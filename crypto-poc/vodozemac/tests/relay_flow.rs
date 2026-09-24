use vodozemac::{
    Curve25519PublicKey, Ed25519PublicKey, Ed25519Signature,
    olm::{Account, OlmMessage, SessionConfig},
};
use vevak_crypto_poc::{
    ReplayGuard, RequestEnvelope, RequestKind, RequestRejection,
    canonical_prekey_bundle_payload,
    relay::{InMemoryRelay, PublicPrekeyBundle, RelayEnvelope, RelayRejection},
};

fn signed_prekey_bundle(account: &mut Account, device_id: &str) -> PublicPrekeyBundle {
    account.generate_one_time_keys(1);
    let one_time_keys = account.one_time_keys();
    let (key_id, key) = one_time_keys
        .iter()
        .next()
        .expect("one-time key should exist");

    let curve25519_identity = account.curve25519_key().to_base64();
    let one_time_key = key.to_base64();
    let payload = canonical_prekey_bundle_payload(&curve25519_identity, &one_time_key);
    let signature = account.sign(&payload).to_base64();

    PublicPrekeyBundle {
        device_id: device_id.to_owned(),
        curve25519_identity,
        ed25519_identity: account.ed25519_key().to_base64(),
        one_time_key_id: key_id.to_base64(),
        one_time_key,
        signature,
    }
}

#[test]
fn relay_carries_only_public_keys_metadata_and_ciphertext() {
    let alice_device_id = "alice-device";
    let bob_device_id = "bob-device";

    let alice = Account::new();
    let mut bob = Account::new();
    let bob_bundle = signed_prekey_bundle(&mut bob, bob_device_id);

    let mut relay = InMemoryRelay::default();
    relay.publish_prekey(bob_bundle);

    let published = relay
        .take_prekey(bob_device_id)
        .expect("Bob's one-time prekey should be consumable exactly once");
    assert!(
        relay.take_prekey(bob_device_id).is_none(),
        "one-time prekey must not be handed out twice"
    );

    let payload = canonical_prekey_bundle_payload(
        &published.curve25519_identity,
        &published.one_time_key,
    );
    let signing_key = Ed25519PublicKey::from_base64(&published.ed25519_identity)
        .expect("published signing key should decode");
    let signature = Ed25519Signature::from_base64(&published.signature)
        .expect("published signature should decode");
    signing_key
        .verify(&payload, &signature)
        .expect("public prekey bundle signature should verify");

    let bob_identity = Curve25519PublicKey::from_base64(&published.curve25519_identity)
        .expect("published identity key should decode");
    let bob_one_time_key = Curve25519PublicKey::from_base64(&published.one_time_key)
        .expect("published one-time key should decode");

    let mut alice_session = alice
        .create_outbound_session(
            SessionConfig::version_1(),
            bob_identity,
            bob_one_time_key,
        )
        .expect("Alice should create an outbound session from relay public data");

    bob.mark_keys_as_published();

    let request = RequestEnvelope {
        request_id: "location-request-001".to_owned(),
        issued_at: 1_000,
        expires_at: 1_060,
        kind: RequestKind::Location,
    };
    let request_plaintext = serde_json::to_vec(&request).expect("request should serialize");
    let encrypted_request = alice_session
        .encrypt(request_plaintext)
        .expect("request should encrypt");

    relay
        .enqueue(
            RelayEnvelope {
                envelope_id: "relay-envelope-request-001".to_owned(),
                sender_device_id: alice_device_id.to_owned(),
                recipient_device_id: bob_device_id.to_owned(),
                issued_at: 1_000,
                expires_at: 1_060,
                olm_message_json: serde_json::to_string(&encrypted_request)
                    .expect("Olm message should serialize"),
            },
            1_001,
        )
        .expect("valid request envelope should queue");

    let server_view = relay.snapshot_json();
    assert!(!server_view.contains("Location"));
    assert!(!server_view.contains("location-request-001"));

    let delivered = relay.fetch_for(bob_device_id, 1_002);
    assert_eq!(delivered.len(), 1);
    let encrypted: OlmMessage = serde_json::from_str(&delivered[0].olm_message_json)
        .expect("relay ciphertext should decode as Olm message");

    let prekey = match encrypted {
        OlmMessage::PreKey(message) => message,
        OlmMessage::Normal(_) => panic!("first request should establish an Olm session"),
    };
    let inbound = bob
        .create_inbound_session(
            SessionConfig::version_1(),
            alice.curve25519_key(),
            &prekey,
        )
        .expect("Bob should establish the inbound session");

    let mut bob_session = inbound.session;
    let decrypted_request: RequestEnvelope = serde_json::from_slice(&inbound.plaintext)
        .expect("Bob should decrypt the request payload");
    assert_eq!(decrypted_request, request);

    let mut replay_guard = ReplayGuard::default();
    assert_eq!(
        replay_guard.validate_and_record(&decrypted_request, 1_003),
        Ok(())
    );
    assert_eq!(
        replay_guard.validate_and_record(&decrypted_request, 1_004),
        Err(RequestRejection::Replay)
    );

    let position_plaintext = br#"{"latitude":"49.119300","longitude":"6.175700","accuracy_m":15}"#;
    let encrypted_position = bob_session
        .encrypt(position_plaintext)
        .expect("position response should encrypt");

    relay
        .enqueue(
            RelayEnvelope {
                envelope_id: "relay-envelope-position-001".to_owned(),
                sender_device_id: bob_device_id.to_owned(),
                recipient_device_id: alice_device_id.to_owned(),
                issued_at: 1_005,
                expires_at: 1_065,
                olm_message_json: serde_json::to_string(&encrypted_position)
                    .expect("encrypted response should serialize"),
            },
            1_005,
        )
        .expect("encrypted position should queue");

    let server_view = relay.snapshot_json();
    assert!(!server_view.contains("49.119300"));
    assert!(!server_view.contains("6.175700"));
    assert!(!server_view.contains("accuracy_m"));

    let delivered = relay.fetch_for(alice_device_id, 1_006);
    assert_eq!(delivered.len(), 1);
    let encrypted: OlmMessage = serde_json::from_str(&delivered[0].olm_message_json)
        .expect("encrypted response should decode");
    let decrypted_position = alice_session
        .decrypt(&encrypted)
        .expect("Alice should decrypt Bob's response");
    assert_eq!(decrypted_position, position_plaintext);
}

#[test]
fn relay_rejects_duplicate_and_expired_envelopes() {
    let mut relay = InMemoryRelay::default();
    let envelope = RelayEnvelope {
        envelope_id: "same-envelope".to_owned(),
        sender_device_id: "alice".to_owned(),
        recipient_device_id: "bob".to_owned(),
        issued_at: 100,
        expires_at: 160,
        olm_message_json: "opaque-ciphertext".to_owned(),
    };

    assert_eq!(relay.enqueue(envelope.clone(), 120), Ok(()));
    assert_eq!(
        relay.enqueue(envelope, 121),
        Err(RelayRejection::DuplicateEnvelope)
    );

    let expired = RelayEnvelope {
        envelope_id: "expired-envelope".to_owned(),
        sender_device_id: "alice".to_owned(),
        recipient_device_id: "bob".to_owned(),
        issued_at: 100,
        expires_at: 110,
        olm_message_json: "opaque-ciphertext".to_owned(),
    };
    assert_eq!(
        relay.enqueue(expired, 111),
        Err(RelayRejection::InvalidLifetime)
    );
}

#[test]
fn a_reinstalled_device_has_a_new_identity_for_local_revalidation() {
    let old_bob = Account::new();
    let new_bob = Account::new();

    assert_ne!(
        old_bob.ed25519_key(),
        new_bob.ed25519_key(),
        "reinstall must be detectable as a different device identity"
    );
    assert_ne!(
        old_bob.curve25519_key(),
        new_bob.curve25519_key(),
        "reinstall must not silently inherit the previous Olm identity"
    );
}
