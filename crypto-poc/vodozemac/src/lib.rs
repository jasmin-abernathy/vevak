pub mod relay;

use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use vodozemac::olm::{Account, OlmMessage, SessionConfig};

uniffi::setup_scaffolding!();

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

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PublicIdentity {
    pub curve25519: String,
    pub ed25519: String,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RoundTripProof {
    pub plaintext: String,
    pub decrypted: String,
    pub session_ids_match: bool,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CryptoBridgeError {
    #[error("VeVak crypto bridge failed during {operation}")]
    CryptoFailure { operation: String },
}

#[uniffi::export]
pub fn bridge_version() -> String {
    "vevak-crypto-poc/uniffi-0.32/vodozemac-0.11".to_owned()
}

#[uniffi::export]
pub fn generate_public_identity() -> PublicIdentity {
    let account = Account::new();
    PublicIdentity {
        curve25519: account.curve25519_key().to_base64(),
        ed25519: account.ed25519_key().to_base64(),
    }
}

#[uniffi::export]
pub fn bridge_round_trip(plaintext: String) -> Result<RoundTripProof, CryptoBridgeError> {
    let alice = Account::new();
    let mut bob = Account::new();

    bob.generate_one_time_keys(1);
    let bob_one_time_key = *bob
        .one_time_keys()
        .values()
        .next()
        .ok_or_else(|| CryptoBridgeError::CryptoFailure {
            operation: "one-time key generation".to_owned(),
        })?;

    let mut alice_session = alice
        .create_outbound_session(
            SessionConfig::version_1(),
            bob.curve25519_key(),
            bob_one_time_key,
        )
        .map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "outbound session creation".to_owned(),
        })?;

    bob.mark_keys_as_published();

    let first_message = alice_session
        .encrypt(plaintext.as_bytes())
        .map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "first message encryption".to_owned(),
        })?;

    let prekey_message = match first_message {
        OlmMessage::PreKey(message) => message,
        OlmMessage::Normal(_) => {
            return Err(CryptoBridgeError::CryptoFailure {
                operation: "prekey session establishment".to_owned(),
            });
        }
    };

    let inbound = bob
        .create_inbound_session(
            SessionConfig::version_1(),
            alice.curve25519_key(),
            &prekey_message,
        )
        .map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "inbound session creation".to_owned(),
        })?;

    let mut bob_session = inbound.session;
    let decrypted_first =
        String::from_utf8(inbound.plaintext).map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "first message decoding".to_owned(),
        })?;

    let reply = bob_session
        .encrypt(decrypted_first.as_bytes())
        .map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "reply encryption".to_owned(),
        })?;

    let decrypted_reply = alice_session
        .decrypt(&reply)
        .map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "reply decryption".to_owned(),
        })?;
    let decrypted =
        String::from_utf8(decrypted_reply).map_err(|_| CryptoBridgeError::CryptoFailure {
            operation: "reply decoding".to_owned(),
        })?;

    Ok(RoundTripProof {
        plaintext,
        decrypted,
        session_ids_match: alice_session.session_id() == bob_session.session_id(),
    })
}

#[cfg(test)]
mod bridge_tests {
    use super::*;

    #[test]
    fn exported_bridge_api_round_trips_without_exposing_secret_keys() {
        let identity = generate_public_identity();
        assert!(!identity.curve25519.is_empty());
        assert!(!identity.ed25519.is_empty());

        let proof = bridge_round_trip("hello from foreign bindings".to_owned())
            .expect("bridge round trip should succeed");
        assert_eq!(proof.plaintext, proof.decrypted);
        assert!(proof.session_ids_match);
    }
}
