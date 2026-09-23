use serde::{Deserialize, Serialize};
use std::collections::HashSet;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum RequestKind {
    Location,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RequestEnvelope {
    pub request_id: String,
    pub issued_at: i64,
    pub expires_at: i64,
    pub kind: RequestKind,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RequestRejection {
    ExpiredOrInvalidWindow,
    Replay,
}

#[derive(Debug, Default)]
pub struct ReplayGuard {
    seen_request_ids: HashSet<String>,
}

impl ReplayGuard {
    pub fn validate_and_record(
        &mut self,
        request: &RequestEnvelope,
        now: i64,
    ) -> Result<(), RequestRejection> {
        if request.expires_at < request.issued_at || now > request.expires_at {
            return Err(RequestRejection::ExpiredOrInvalidWindow);
        }

        if !self.seen_request_ids.insert(request.request_id.clone()) {
            return Err(RequestRejection::Replay);
        }

        Ok(())
    }
}

pub fn canonical_prekey_bundle_payload(
    curve25519_identity: &str,
    one_time_key: &str,
) -> Vec<u8> {
    format!(
        "vevak-prekey-v1\ncurve25519-identity:{curve25519_identity}\none-time-key:{one_time_key}\n"
    )
    .into_bytes()
}
