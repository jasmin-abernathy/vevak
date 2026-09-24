use std::collections::HashSet;
use vevak_e2ee_message_model_v0::{
    AuthenticatedTransportContextV0, InboundValidationContextV0, LocationRequestBodyV0,
    LocationResponseBodyV0, MessageBodyV0, MessageEnvelopeV0, MessageKindV0,
    MessageValidationError, PinnedRelationV0, PROTOCOL_VERSION_V0, ReplayKeyV0,
    ReplayRecordResult, ReplayStoreError, ReplayStoreV0, parse_message_v0,
    serialize_message_v0, validate_inbound_message_v0,
};

const RELATION: &str = "relation-0123456789abcdef0123456789abcdef";
const ALICE_DEVICE: &str = "alice-device-0123456789abcdef";
const BOB_DEVICE: &str = "bob-device-0123456789abcdef";
const ALICE_FP: &str = "ed25519:alice-synthetic-fingerprint";
const BOB_FP: &str = "ed25519:bob-synthetic-fingerprint";
const SESSION: &str = "olm-session-synthetic-001";

#[derive(Default)]
struct TestReplayStore {
    seen: HashSet<ReplayKeyV0>,
}

impl ReplayStoreV0 for TestReplayStore {
    fn record_once(
        &mut self,
        key: &ReplayKeyV0,
        _expires_at: i64,
    ) -> Result<ReplayRecordResult, ReplayStoreError> {
        if self.seen.insert(key.clone()) {
            Ok(ReplayRecordResult::Recorded)
        } else {
            Ok(ReplayRecordResult::AlreadySeen)
        }
    }
}

fn request(message_id: &str, issued_at: i64, expires_at: i64) -> MessageEnvelopeV0 {
    MessageEnvelopeV0 {
        protocol_version: PROTOCOL_VERSION_V0,
        relation_id: RELATION.to_owned(),
        sender_device_id: ALICE_DEVICE.to_owned(),
        recipient_device_id: BOB_DEVICE.to_owned(),
        sender_identity_fingerprint: ALICE_FP.to_owned(),
        recipient_identity_fingerprint: BOB_FP.to_owned(),
        message_id: message_id.to_owned(),
        issued_at,
        expires_at,
        kind: MessageKindV0::LocationRequest,
        body: MessageBodyV0::LocationRequest(LocationRequestBodyV0 {}),
        request_id: None,
    }
}

fn response(message_id: &str, request_id: &str) -> MessageEnvelopeV0 {
    MessageEnvelopeV0 {
        protocol_version: PROTOCOL_VERSION_V0,
        relation_id: RELATION.to_owned(),
        sender_device_id: ALICE_DEVICE.to_owned(),
        recipient_device_id: BOB_DEVICE.to_owned(),
        sender_identity_fingerprint: ALICE_FP.to_owned(),
        recipient_identity_fingerprint: BOB_FP.to_owned(),
        message_id: message_id.to_owned(),
        issued_at: 1_020,
        expires_at: 1_120,
        kind: MessageKindV0::LocationResponse,
        body: MessageBodyV0::LocationResponse(LocationResponseBodyV0 {
            latitude_e7: 0,
            longitude_e7: 0,
            accuracy_m: 25,
        }),
        request_id: Some(request_id.to_owned()),
    }
}

fn context<'a>(pending: &'a HashSet<String>) -> InboundValidationContextV0<'a> {
    InboundValidationContextV0 {
        pinned: PinnedRelationV0 {
            relation_id: RELATION,
            local_device_id: BOB_DEVICE,
            remote_device_id: ALICE_DEVICE,
            local_identity_fingerprint: BOB_FP,
            remote_identity_fingerprint: ALICE_FP,
            olm_session_id: SESSION,
        },
        transport: AuthenticatedTransportContextV0 {
            outer_sender_device_id: ALICE_DEVICE,
            outer_recipient_device_id: BOB_DEVICE,
            mailbox_recipient_device_id: BOB_DEVICE,
            decrypted_session_id: SESSION,
        },
        pending_request_ids: pending,
    }
}

#[test]
fn canonical_v0_round_trip_is_stable_and_valid() {
    let original = request("msg-000000000000000000000000000001", 1_000, 1_120);
    let bytes = serialize_message_v0(&original).expect("v0 message should serialize");
    let parsed = parse_message_v0(&bytes).expect("v0 message should parse");
    assert_eq!(parsed, original);
    assert_eq!(
        serialize_message_v0(&parsed).expect("parsed message should reserialize"),
        bytes,
        "VeVak-generated v0 JSON must use a stable field order"
    );

    let pending = HashSet::new();
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&parsed, 1_010, &context(&pending), &mut replay),
        Ok(())
    );
}

#[test]
fn wrong_pinned_identity_is_rejected() {
    let mut message = request("msg-000000000000000000000000000002", 1_000, 1_120);
    message.sender_identity_fingerprint = "ed25519:attacker-synthetic-fingerprint".to_owned();

    let pending = HashSet::new();
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&message, 1_010, &context(&pending), &mut replay),
        Err(MessageValidationError::SenderIdentityMismatch)
    );
}

#[test]
fn substituted_recipient_relation_and_kind_are_rejected() {
    let pending = HashSet::new();

    let mut wrong_recipient = request("msg-000000000000000000000000000003", 1_000, 1_120);
    wrong_recipient.recipient_device_id = "mallory-device-0123456789abcdef".to_owned();
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(
            &wrong_recipient,
            1_010,
            &context(&pending),
            &mut replay
        ),
        Err(MessageValidationError::RecipientDeviceMismatch)
    );

    let mut wrong_relation = request("msg-000000000000000000000000000004", 1_000, 1_120);
    wrong_relation.relation_id = "relation-fedcba9876543210fedcba9876543210".to_owned();
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&wrong_relation, 1_010, &context(&pending), &mut replay),
        Err(MessageValidationError::RelationMismatch)
    );

    let mut wrong_kind = request("msg-000000000000000000000000000005", 1_000, 1_120);
    wrong_kind.kind = MessageKindV0::LocationResponse;
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&wrong_kind, 1_010, &context(&pending), &mut replay),
        Err(MessageValidationError::KindBodyMismatch)
    );
}

#[test]
fn transport_mailbox_and_session_substitution_are_rejected() {
    let message = request("msg-000000000000000000000000000006", 1_000, 1_120);
    let pending = HashSet::new();

    let base = context(&pending);
    let bad_transport = InboundValidationContextV0 {
        pinned: base.pinned.clone(),
        transport: AuthenticatedTransportContextV0 {
            outer_sender_device_id: "mallory-device-0123456789abcdef",
            ..base.transport.clone()
        },
        pending_request_ids: &pending,
    };
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&message, 1_010, &bad_transport, &mut replay),
        Err(MessageValidationError::TransportSenderMismatch)
    );

    let base = context(&pending);
    let bad_mailbox = InboundValidationContextV0 {
        pinned: base.pinned.clone(),
        transport: AuthenticatedTransportContextV0 {
            mailbox_recipient_device_id: "mallory-device-0123456789abcdef",
            ..base.transport.clone()
        },
        pending_request_ids: &pending,
    };
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&message, 1_010, &bad_mailbox, &mut replay),
        Err(MessageValidationError::MailboxRecipientMismatch)
    );

    let base = context(&pending);
    let bad_session = InboundValidationContextV0 {
        pinned: base.pinned.clone(),
        transport: AuthenticatedTransportContextV0 {
            decrypted_session_id: "olm-session-attacker",
            ..base.transport.clone()
        },
        pending_request_ids: &pending,
    };
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&message, 1_010, &bad_session, &mut replay),
        Err(MessageValidationError::SessionMismatch)
    );
}

#[test]
fn expired_and_excessive_ttl_messages_are_rejected() {
    let pending = HashSet::new();

    let expired = request("msg-000000000000000000000000000007", 1_000, 1_010);
    let mut replay = TestReplayStore::default();
    assert_eq!(
        validate_inbound_message_v0(&expired, 1_011, &context(&pending), &mut replay),
        Err(MessageValidationError::Expired)
    );

    let too_long = request("msg-000000000000000000000000000008", 1_000, 1_301);
    assert_eq!(
        serialize_message_v0(&too_long),
        Err(MessageValidationError::LifetimeTooLong)
    );
}

#[test]
fn duplicate_message_is_rejected_by_injected_replay_contract() {
    let message = request("msg-000000000000000000000000000009", 1_000, 1_120);
    let pending = HashSet::new();
    let mut replay = TestReplayStore::default();

    assert_eq!(
        validate_inbound_message_v0(&message, 1_010, &context(&pending), &mut replay),
        Ok(())
    );
    assert_eq!(
        validate_inbound_message_v0(&message, 1_011, &context(&pending), &mut replay),
        Err(MessageValidationError::Replay)
    );
}

#[test]
fn response_without_matching_pending_request_is_rejected() {
    let message = response(
        "msg-000000000000000000000000000010",
        "msg-request-not-pending-00000000000001",
    );
    let pending = HashSet::new();
    let mut replay = TestReplayStore::default();

    assert_eq!(
        validate_inbound_message_v0(&message, 1_030, &context(&pending), &mut replay),
        Err(MessageValidationError::UnknownRequest)
    );
}

#[test]
fn response_matching_pending_request_is_accepted() {
    let request_id = "msg-request-pending-000000000000000001";
    let message = response("msg-000000000000000000000000000011", request_id);
    let pending = HashSet::from([request_id.to_owned()]);
    let mut replay = TestReplayStore::default();

    assert_eq!(
        validate_inbound_message_v0(&message, 1_030, &context(&pending), &mut replay),
        Ok(())
    );
}

#[test]
fn future_protocol_version_is_rejected_before_v0_interpretation() {
    let message = request("msg-000000000000000000000000000012", 1_000, 1_120);
    let bytes = serialize_message_v0(&message).expect("baseline should serialize");
    let raw = String::from_utf8(bytes)
        .expect("generated JSON is UTF-8")
        .replacen(r#""protocol_version":0"#, r#""protocol_version":1"#, 1);

    assert_eq!(
        parse_message_v0(raw.as_bytes()),
        Err(MessageValidationError::UnsupportedVersion)
    );
}

#[test]
fn missing_duplicate_unknown_and_oversized_fields_fail_closed() {
    let message = request("msg-000000000000000000000000000013", 1_000, 1_120);
    let canonical = String::from_utf8(
        serialize_message_v0(&message).expect("baseline should serialize"),
    )
    .expect("generated JSON is UTF-8");

    let missing_relation = canonical.replacen(
        &format!(r#""relation_id":"{RELATION}","#),
        "",
        1,
    );
    assert_eq!(
        parse_message_v0(missing_relation.as_bytes()),
        Err(MessageValidationError::MalformedJson)
    );

    let duplicate_message_id = canonical.replacen(
        r#""issued_at":"#,
        r#""message_id":"msg-duplicate-00000000000000000001","issued_at":"#,
        1,
    );
    assert_eq!(
        parse_message_v0(duplicate_message_id.as_bytes()),
        Err(MessageValidationError::MalformedJson)
    );

    let unknown_field = canonical.replacen(
        "{",
        r#"{"relay_must_not_trust_me":true,"#,
        1,
    );
    assert_eq!(
        parse_message_v0(unknown_field.as_bytes()),
        Err(MessageValidationError::MalformedJson)
    );

    let oversized = vec![b' '; vevak_e2ee_message_model_v0::MAX_MESSAGE_BYTES_V0 + 1];
    assert_eq!(
        parse_message_v0(&oversized),
        Err(MessageValidationError::MessageTooLarge)
    );
}

#[test]
fn reordered_unexpired_messages_are_independently_accepted() {
    let newer = request("msg-000000000000000000000000000014", 1_020, 1_120);
    let older = request("msg-000000000000000000000000000015", 1_000, 1_100);
    let pending = HashSet::new();
    let mut replay = TestReplayStore::default();

    assert_eq!(
        validate_inbound_message_v0(&newer, 1_030, &context(&pending), &mut replay),
        Ok(())
    );
    assert_eq!(
        validate_inbound_message_v0(&older, 1_031, &context(&pending), &mut replay),
        Ok(()),
        "v0 has no monotonic sequence: valid reordering must not become a false replay"
    );
}
