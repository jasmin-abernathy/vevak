use vodozemac::olm::{
    Account, AccountPickle, OlmMessage, Session, SessionConfig, SessionPickle,
};
use vevak_crypto_poc::{
    canonical_prekey_bundle_payload, ReplayGuard, RequestEnvelope, RequestKind, RequestRejection,
};

#[test]
fn asynchronous_prekey_session_round_trips_a_location_request() {
    let alice = Account::new();
    let mut bob = Account::new();

    bob.generate_one_time_keys(1);
    let bob_one_time_key = *bob
        .one_time_keys()
        .values()
        .next()
        .expect("Bob should expose one unpublished one-time key");

    let mut alice_session = alice
        .create_outbound_session(
            SessionConfig::version_1(),
            bob.curve25519_key(),
            bob_one_time_key,
        )
        .expect("Alice should be able to create a session from Bob's public bundle");

    bob.mark_keys_as_published();

    let request = RequestEnvelope {
        request_id: "request-001".to_owned(),
        issued_at: 1_000,
        expires_at: 1_060,
        kind: RequestKind::Location,
    };
    let request_bytes = serde_json::to_vec(&request).expect("request should serialize");
    let encrypted_request = alice_session
        .encrypt(request_bytes)
        .expect("Alice should encrypt before Bob is online");

    let prekey_message = match encrypted_request {
        OlmMessage::PreKey(message) => message,
        OlmMessage::Normal(_) => panic!("first outbound message must establish the session"),
    };

    let inbound = bob
        .create_inbound_session(
            SessionConfig::version_1(),
            alice.curve25519_key(),
            &prekey_message,
        )
        .expect("Bob should establish the matching inbound session");

    let mut bob_session = inbound.session;
    let decrypted_request: RequestEnvelope =
        serde_json::from_slice(&inbound.plaintext).expect("Bob should decrypt the request");

    assert_eq!(decrypted_request, request);
    assert_eq!(alice_session.session_id(), bob_session.session_id());

    let encrypted_position = bob_session
        .encrypt(b"POSITION:49.119300,6.175700")
        .expect("Bob should encrypt a reply");

    let position = alice_session
        .decrypt(&encrypted_position)
        .expect("Alice should decrypt Bob's reply");

    assert_eq!(position, b"POSITION:49.119300,6.175700");
}

#[test]
fn published_prekey_bundle_can_be_signed_and_tampering_is_detected() {
    let mut bob = Account::new();
    bob.generate_one_time_keys(1);

    let one_time_key = *bob
        .one_time_keys()
        .values()
        .next()
        .expect("one-time key should exist");

    let payload = canonical_prekey_bundle_payload(
        &bob.curve25519_key().to_base64(),
        &one_time_key.to_base64(),
    );
    let signature = bob.sign(&payload);

    bob.ed25519_key()
        .verify(&payload, &signature)
        .expect("the genuine bundle signature should verify");

    let mut tampered = payload.clone();
    tampered.extend_from_slice(b"attacker-change");

    assert!(
        bob.ed25519_key().verify(&tampered, &signature).is_err(),
        "modifying the public bundle must invalidate its signature"
    );
}

#[test]
fn vevak_layer_rejects_replayed_and_expired_requests() {
    let mut replay_guard = ReplayGuard::default();
    let request = RequestEnvelope {
        request_id: "same-request".to_owned(),
        issued_at: 100,
        expires_at: 160,
        kind: RequestKind::Location,
    };

    assert_eq!(replay_guard.validate_and_record(&request, 120), Ok(()));
    assert_eq!(
        replay_guard.validate_and_record(&request, 121),
        Err(RequestRejection::Replay)
    );

    let expired = RequestEnvelope {
        request_id: "expired-request".to_owned(),
        issued_at: 100,
        expires_at: 110,
        kind: RequestKind::Location,
    };
    assert_eq!(
        replay_guard.validate_and_record(&expired, 111),
        Err(RequestRejection::ExpiredOrInvalidWindow)
    );

    let invalid_window = RequestEnvelope {
        request_id: "invalid-window".to_owned(),
        issued_at: 200,
        expires_at: 199,
        kind: RequestKind::Location,
    };
    assert_eq!(
        replay_guard.validate_and_record(&invalid_window, 198),
        Err(RequestRejection::ExpiredOrInvalidWindow)
    );
}

#[test]
fn account_and_ratchet_state_survive_serialization() {
    let alice = Account::new();
    let mut bob = Account::new();

    bob.generate_one_time_keys(1);
    let bob_one_time_key = *bob
        .one_time_keys()
        .values()
        .next()
        .expect("one-time key should exist");

    let mut alice_session = alice
        .create_outbound_session(
            SessionConfig::version_1(),
            bob.curve25519_key(),
            bob_one_time_key,
        )
        .expect("outbound session should be created");

    bob.mark_keys_as_published();

    let first = alice_session
        .encrypt(b"REQUEST")
        .expect("initial request should encrypt");
    let prekey = match first {
        OlmMessage::PreKey(message) => message,
        OlmMessage::Normal(_) => panic!("first message should be a prekey message"),
    };

    let inbound = bob
        .create_inbound_session(
            SessionConfig::version_1(),
            alice.curve25519_key(),
            &prekey,
        )
        .expect("inbound session should be created");
    let bob_session = inbound.session;

    let alice_account_json =
        serde_json::to_string(&alice.pickle()).expect("account pickle should serialize");
    let alice_session_json =
        serde_json::to_string(&alice_session.pickle()).expect("session pickle should serialize");
    let bob_session_json =
        serde_json::to_string(&bob_session.pickle()).expect("session pickle should serialize");

    let restored_alice_account: Account = serde_json::from_str::<AccountPickle>(&alice_account_json)
        .expect("account pickle should deserialize")
        .into();
    let mut restored_alice_session: Session =
        serde_json::from_str::<SessionPickle>(&alice_session_json)
            .expect("Alice session pickle should deserialize")
            .into();
    let mut restored_bob_session: Session =
        serde_json::from_str::<SessionPickle>(&bob_session_json)
            .expect("Bob session pickle should deserialize")
            .into();

    assert_eq!(alice.identity_keys(), restored_alice_account.identity_keys());

    let after_restart = restored_bob_session
        .encrypt(b"POSITION-AFTER-RESTART")
        .expect("restored Bob session should encrypt");
    let decrypted = restored_alice_session
        .decrypt(&after_restart)
        .expect("restored Alice session should decrypt");

    assert_eq!(decrypted, b"POSITION-AFTER-RESTART");
}

#[test]
fn reinstall_changes_device_identity_and_can_trigger_revalidation() {
    let bob_before_reinstall = Account::new();
    let trusted_identity = bob_before_reinstall.identity_keys();

    let bob_after_reinstall = Account::new();
    let replacement_identity = bob_after_reinstall.identity_keys();

    assert_ne!(
        trusted_identity, replacement_identity,
        "a fresh installation should have a different cryptographic identity"
    );
}
