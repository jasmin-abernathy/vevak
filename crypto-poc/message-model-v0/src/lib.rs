use serde::{Deserialize, Serialize};
use std::collections::HashSet;

pub const PROTOCOL_VERSION_V0: u16 = 0;
pub const MAX_MESSAGE_BYTES_V0: usize = 16 * 1024;
pub const MAX_MESSAGE_TTL_SECONDS_V0: i64 = 5 * 60;
pub const MAX_FUTURE_CLOCK_SKEW_SECONDS_V0: i64 = 2 * 60;
const MAX_IDENTIFIER_BYTES_V0: usize = 256;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum MessageKindV0 {
    LocationRequest,
    LocationResponse,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LocationRequestBodyV0 {}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LocationResponseBodyV0 {
    /// Signed fixed-point latitude in degrees × 10^7.
    pub latitude_e7: i32,
    /// Signed fixed-point longitude in degrees × 10^7.
    pub longitude_e7: i32,
    pub accuracy_m: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(untagged)]
pub enum MessageBodyV0 {
    LocationRequest(LocationRequestBodyV0),
    LocationResponse(LocationResponseBodyV0),
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct MessageEnvelopeV0 {
    pub protocol_version: u16,
    pub relation_id: String,
    pub sender_device_id: String,
    pub recipient_device_id: String,
    pub sender_identity_fingerprint: String,
    pub recipient_identity_fingerprint: String,
    pub message_id: String,
    pub issued_at: i64,
    pub expires_at: i64,
    pub kind: MessageKindV0,
    pub body: MessageBodyV0,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub request_id: Option<String>,
}

#[derive(Debug, Deserialize)]
struct VersionProbe {
    protocol_version: u16,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PinnedRelationV0<'a> {
    pub relation_id: &'a str,
    pub local_device_id: &'a str,
    pub remote_device_id: &'a str,
    pub local_identity_fingerprint: &'a str,
    pub remote_identity_fingerprint: &'a str,
    pub olm_session_id: &'a str,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AuthenticatedTransportContextV0<'a> {
    /// Outer sender metadata supplied by the relay transport.
    pub outer_sender_device_id: &'a str,
    /// Outer recipient metadata supplied by the relay transport.
    pub outer_recipient_device_id: &'a str,
    /// Local mailbox owner used for this fetch operation.
    pub mailbox_recipient_device_id: &'a str,
    /// Session identifier of the Olm session that actually decrypted the message.
    pub decrypted_session_id: &'a str,
}

#[derive(Debug)]
pub struct InboundValidationContextV0<'a> {
    pub pinned: PinnedRelationV0<'a>,
    pub transport: AuthenticatedTransportContextV0<'a>,
    /// Message IDs for requests that are still awaiting a response locally.
    pub pending_request_ids: &'a HashSet<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ReplayKeyV0 {
    pub relation_id: String,
    pub sender_device_id: String,
    pub message_id: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReplayRecordResult {
    Recorded,
    AlreadySeen,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReplayStoreError {
    Unavailable,
}

/// Persistence contract for anti-replay state.
///
/// Production implementations MUST persist across process restarts and perform
/// record_once atomically: two concurrent calls for the same key cannot both
/// return Recorded. An in-memory implementation is suitable for tests only.
pub trait ReplayStoreV0 {
    fn record_once(
        &mut self,
        key: &ReplayKeyV0,
        expires_at: i64,
    ) -> Result<ReplayRecordResult, ReplayStoreError>;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MessageValidationError {
    MessageTooLarge,
    MalformedJson,
    UnsupportedVersion,
    InvalidIdentifier,
    InvalidTimestamp,
    InvalidLifetime,
    LifetimeTooLong,
    IssuedTooFarInFuture,
    Expired,
    InvalidLocationBody,
    RelationMismatch,
    SenderDeviceMismatch,
    RecipientDeviceMismatch,
    SenderIdentityMismatch,
    RecipientIdentityMismatch,
    TransportSenderMismatch,
    TransportRecipientMismatch,
    MailboxRecipientMismatch,
    SessionMismatch,
    KindBodyMismatch,
    MissingRequestId,
    UnexpectedRequestId,
    UnknownRequest,
    Replay,
    ReplayStoreUnavailable,
}

pub fn serialize_message_v0(
    message: &MessageEnvelopeV0,
) -> Result<Vec<u8>, MessageValidationError> {
    validate_intrinsic_shape(message)?;
    let bytes = serde_json::to_vec(message).map_err(|_| MessageValidationError::MalformedJson)?;
    if bytes.len() > MAX_MESSAGE_BYTES_V0 {
        return Err(MessageValidationError::MessageTooLarge);
    }
    Ok(bytes)
}

pub fn parse_message_v0(bytes: &[u8]) -> Result<MessageEnvelopeV0, MessageValidationError> {
    if bytes.len() > MAX_MESSAGE_BYTES_V0 {
        return Err(MessageValidationError::MessageTooLarge);
    }

    // Probe the version before interpreting the v0 schema. This makes a future
    // schema fail closed as an unsupported protocol instead of being partially
    // accepted as v0.
    let version = serde_json::from_slice::<VersionProbe>(bytes)
        .map_err(|_| MessageValidationError::MalformedJson)?
        .protocol_version;
    if version != PROTOCOL_VERSION_V0 {
        return Err(MessageValidationError::UnsupportedVersion);
    }

    let message = serde_json::from_slice::<MessageEnvelopeV0>(bytes)
        .map_err(|_| MessageValidationError::MalformedJson)?;
    validate_intrinsic_shape(&message)?;
    Ok(message)
}

pub fn validate_inbound_message_v0(
    message: &MessageEnvelopeV0,
    now: i64,
    context: &InboundValidationContextV0<'_>,
    replay_store: &mut impl ReplayStoreV0,
) -> Result<(), MessageValidationError> {
    validate_intrinsic_shape(message)?;

    if now < 0 {
        return Err(MessageValidationError::InvalidTimestamp);
    }
    if message.issued_at > now.saturating_add(MAX_FUTURE_CLOCK_SKEW_SECONDS_V0) {
        return Err(MessageValidationError::IssuedTooFarInFuture);
    }
    if now > message.expires_at {
        return Err(MessageValidationError::Expired);
    }

    if message.relation_id != context.pinned.relation_id {
        return Err(MessageValidationError::RelationMismatch);
    }
    if message.sender_device_id != context.pinned.remote_device_id {
        return Err(MessageValidationError::SenderDeviceMismatch);
    }
    if message.recipient_device_id != context.pinned.local_device_id {
        return Err(MessageValidationError::RecipientDeviceMismatch);
    }
    if message.sender_identity_fingerprint != context.pinned.remote_identity_fingerprint {
        return Err(MessageValidationError::SenderIdentityMismatch);
    }
    if message.recipient_identity_fingerprint != context.pinned.local_identity_fingerprint {
        return Err(MessageValidationError::RecipientIdentityMismatch);
    }

    if context.transport.outer_sender_device_id != message.sender_device_id {
        return Err(MessageValidationError::TransportSenderMismatch);
    }
    if context.transport.outer_recipient_device_id != message.recipient_device_id {
        return Err(MessageValidationError::TransportRecipientMismatch);
    }
    if context.transport.mailbox_recipient_device_id != context.pinned.local_device_id {
        return Err(MessageValidationError::MailboxRecipientMismatch);
    }
    if context.transport.decrypted_session_id != context.pinned.olm_session_id {
        return Err(MessageValidationError::SessionMismatch);
    }

    match message.kind {
        MessageKindV0::LocationRequest => {
            if message.request_id.is_some() {
                return Err(MessageValidationError::UnexpectedRequestId);
            }
        }
        MessageKindV0::LocationResponse => {
            let request_id = message
                .request_id
                .as_ref()
                .ok_or(MessageValidationError::MissingRequestId)?;
            if !context.pending_request_ids.contains(request_id) {
                return Err(MessageValidationError::UnknownRequest);
            }
        }
    }

    let replay_key = ReplayKeyV0 {
        relation_id: message.relation_id.clone(),
        sender_device_id: message.sender_device_id.clone(),
        message_id: message.message_id.clone(),
    };

    match replay_store.record_once(&replay_key, message.expires_at) {
        Ok(ReplayRecordResult::Recorded) => Ok(()),
        Ok(ReplayRecordResult::AlreadySeen) => Err(MessageValidationError::Replay),
        Err(ReplayStoreError::Unavailable) => Err(MessageValidationError::ReplayStoreUnavailable),
    }
}

fn validate_intrinsic_shape(message: &MessageEnvelopeV0) -> Result<(), MessageValidationError> {
    if message.protocol_version != PROTOCOL_VERSION_V0 {
        return Err(MessageValidationError::UnsupportedVersion);
    }

    for value in [
        message.relation_id.as_str(),
        message.sender_device_id.as_str(),
        message.recipient_device_id.as_str(),
        message.sender_identity_fingerprint.as_str(),
        message.recipient_identity_fingerprint.as_str(),
        message.message_id.as_str(),
    ] {
        validate_identifier(value)?;
    }
    if let Some(request_id) = message.request_id.as_deref() {
        validate_identifier(request_id)?;
    }

    if message.issued_at < 0 || message.expires_at < 0 {
        return Err(MessageValidationError::InvalidTimestamp);
    }
    if message.expires_at < message.issued_at {
        return Err(MessageValidationError::InvalidLifetime);
    }
    if message.expires_at - message.issued_at > MAX_MESSAGE_TTL_SECONDS_V0 {
        return Err(MessageValidationError::LifetimeTooLong);
    }

    match (&message.kind, &message.body) {
        (MessageKindV0::LocationRequest, MessageBodyV0::LocationRequest(_)) => {}
        (MessageKindV0::LocationResponse, MessageBodyV0::LocationResponse(body)) => {
            if !(-900_000_000..=900_000_000).contains(&body.latitude_e7)
                || !(-1_800_000_000..=1_800_000_000).contains(&body.longitude_e7)
                || body.accuracy_m > 100_000
            {
                return Err(MessageValidationError::InvalidLocationBody);
            }
        }
        _ => return Err(MessageValidationError::KindBodyMismatch),
    }

    Ok(())
}

fn validate_identifier(value: &str) -> Result<(), MessageValidationError> {
    if value.is_empty()
        || value.len() > MAX_IDENTIFIER_BYTES_V0
        || value.chars().any(char::is_control)
    {
        return Err(MessageValidationError::InvalidIdentifier);
    }
    Ok(())
}
