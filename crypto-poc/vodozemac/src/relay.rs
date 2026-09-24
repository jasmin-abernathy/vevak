use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet, VecDeque};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PublicPrekeyBundle {
    pub device_id: String,
    pub curve25519_identity: String,
    pub ed25519_identity: String,
    pub one_time_key_id: String,
    pub one_time_key: String,
    pub signature: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RelayEnvelope {
    pub envelope_id: String,
    pub sender_device_id: String,
    pub recipient_device_id: String,
    pub issued_at: i64,
    pub expires_at: i64,
    pub olm_message_json: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RelayRejection {
    InvalidLifetime,
    DuplicateEnvelope,
}

#[derive(Debug, Default, Serialize)]
pub struct InMemoryRelay {
    prekeys: HashMap<String, VecDeque<PublicPrekeyBundle>>,
    envelopes: Vec<RelayEnvelope>,
    seen_envelope_ids: HashSet<String>,
}

impl InMemoryRelay {
    pub fn publish_prekey(&mut self, bundle: PublicPrekeyBundle) {
        self.prekeys
            .entry(bundle.device_id.clone())
            .or_default()
            .push_back(bundle);
    }

    pub fn take_prekey(&mut self, device_id: &str) -> Option<PublicPrekeyBundle> {
        let queue = self.prekeys.get_mut(device_id)?;
        let bundle = queue.pop_front();
        if queue.is_empty() {
            self.prekeys.remove(device_id);
        }
        bundle
    }

    pub fn enqueue(
        &mut self,
        envelope: RelayEnvelope,
        now: i64,
    ) -> Result<(), RelayRejection> {
        if envelope.expires_at < envelope.issued_at || envelope.expires_at < now {
            return Err(RelayRejection::InvalidLifetime);
        }
        if !self
            .seen_envelope_ids
            .insert(envelope.envelope_id.clone())
        {
            return Err(RelayRejection::DuplicateEnvelope);
        }
        self.envelopes.push(envelope);
        Ok(())
    }

    pub fn fetch_for(&mut self, device_id: &str, now: i64) -> Vec<RelayEnvelope> {
        let mut delivered = Vec::new();
        self.envelopes.retain(|envelope| {
            if envelope.expires_at < now {
                return false;
            }
            if envelope.recipient_device_id == device_id {
                delivered.push(envelope.clone());
                return false;
            }
            true
        });
        delivered
    }

    pub fn snapshot_json(&self) -> String {
        serde_json::to_string(self).expect("in-memory relay snapshot should serialize")
    }
}
