# Abuse prevention and coercion safety

VeVak is a personal-safety tool, not a partner-monitoring or employee-tracking tool. This document defines product invariants intended to make coercive surveillance materially harder.

## Threat model

A relevant abuse case is a controlling partner, family member, employer or other person who pressures the phone owner into installing/configuring VeVak and then uses an authorised SMS command to reconstruct their movements.

No application can prove that consent was freely given when another person has physical, social or financial control over the owner. VeVak therefore combines explicit consent with technical limits that remain in force even when an authorised contact knows a normal command phrase.

## Non-negotiable invariants

1. **No covert mode.** VeVak does not hide its launcher icon, disguise itself, suppress Android permission indicators or provide a remote way to conceal its presence.
2. **No remote configuration.** Contacts, phrases, authorisation periods, trusted places, discreet-mode duration and the safety fallback are changed only on the phone that is being located.
3. **No permanent authorisation.** The current build offers 24 hours, 7 days or 30 days. After expiry the configuration remains, but automatic location replies stop until the owner explicitly re-authorises that contact locally.
4. **Immediate local revocation per contact.** The owner can stop one contact's automatic replies from the home screen without deleting the application, affecting another contact or notifying the requester.
5. **Visibility is a precondition.** If VeVak cannot post a request notification, it refuses to send an automatic location response. A locally enabled discreet period may make that request notification silent and low-importance, but it never removes local visibility. On Android 13+ this makes `POST_NOTIFICATIONS` part of the safety boundary rather than an optional cosmetic permission.
6. **Hard anti-tracking limits remain global.** Automatic replies are separated by at least 15 minutes and capped at four replies per 24-hour window for the entire device. Adding contacts must never multiply this cap.
7. **No periodic location.** VeVak does not maintain breadcrumbs, journeys, location history or a background polling loop.
8. **No remote sensors.** Remote photo, microphone/audio capture and similar surveillance capabilities are out of scope.
9. **Minimal local audit.** The phone keeps at most 20 recent request outcomes. It never stores coordinates, SMS bodies, contact numbers, Wi-Fi identifiers or request phrases in this audit trail.
10. **No secret leakage in diagnostics.** Phone numbers, phrases, Wi-Fi identifiers, coordinates and whether a request used the safety fallback are excluded from redacted diagnostics. Diagnostics may expose only aggregate contact counts.
11. **Manual sharing is local-only.** An outgoing manual position share can only be initiated in the VeVak interface and requires explicit local confirmation before location acquisition and SMS sending. It cannot be remotely triggered or scheduled.
12. **Backups cannot silently restore access.** Encrypted configuration backup/import never exports active authorisation timestamps. A restored contact is paused until the phone owner explicitly re-authorises it locally.

## Several trusted contacts

VeVak can hold several locally configured contacts without making contact count a paid safety feature.

Each contact has:

- a locally entered phone number;
- a normal request phrase;
- its own authorisation grant time and expiry;
- its own local revoke/reactivate controls.

The current implementation deliberately gives trusted contacts the same narrow capability: request the normal VeVak response while their local authorisation is active. Per-contact privilege matrices such as `locate`, `ring`, `lock`, sensor access or administrative roles remain out of scope pending a separate abuse review.

Adding a contact requires an explicit local consent checkbox and a finite authorisation period. Duplicate phone numbers are rejected. The global rate limiter remains shared by all contacts, so five configured contacts do not become five independent tracking quotas.

## Request visibility and temporary discreet mode

While at least one authorisation is active, VeVak posts an ongoing local status notification when notification policy permits it. A matching request also produces a local notification.

If the relevant request-notification channel is disabled, notifications are globally disabled, or Android 13+ notification permission is missing, automatic replies are blocked. A location must never be sent invisibly merely because SMS/location permissions remain granted.

The owner may locally enable a **temporary discreet mode** for 1 hour, 8 hours or 24 hours, capped by the latest currently active contact authorisation. In this mode:

- request notifications use a dedicated low-importance channel;
- they are silent and do not vibrate by default;
- they remain visible in the Android notification shade;
- the ongoing `VeVak est actif` notification remains visible and indicates that discreet mode is active;
- disabling Android notifications entirely still blocks automatic replies.

For one active contact, the ongoing notification may identify that contact and its expiry. With several active contacts it may show only an aggregate count and the latest expiry. It never exposes request phrases, trusted Wi-Fi identifiers or fallback coordinates.

## Manual outgoing position share

The phone owner may voluntarily send one current position to any configured trusted contact from the VeVak home screen, even when that contact's automatic-request authorisation is paused. This is safe because the action is locally initiated and locally confirmed rather than remotely triggered.

This flow is intentionally different from an automatic incoming request:

- choosing the contact only prepares a confirmation; it does not acquire location or send anything;
- a second explicit local confirmation is required;
- cancellation before confirmation performs no location acquisition and no SMS send;
- if no position can be obtained, no location SMS is sent;
- no emergency service is called;
- no periodic/background manual-share loop exists;
- no requester or remote command can trigger it;
- it uses only Android's configured default SMS subscription; if none exists, VeVak blocks the action instead of choosing a SIM arbitrarily;
- the result shown in the app distinguishes an SMS handed to Android for sending from proof of delivery.

A manual share creates **no additional Android notification of its own**, in either normal or discreet mode. In temporary discreet mode, the result remains in the already-open VeVak interface. This exception does not weaken the automatic-request visibility rule because the owner is physically interacting with and confirming the action. The independent ongoing `VeVak est actif` notification remains governed by the normal authorisation model.

## Encrypted configuration backup/import

Configuration portability is treated as a free resilience/privacy feature rather than a premium gate.

The backup is created locally through Android's document picker. Its plaintext may contain sensitive configuration such as contact phone numbers, request phrases, trusted-Wi-Fi fingerprints and fallback coordinates, so it is wrapped in a password-derived authenticated-encryption container before being written.

Current safeguards:

- AES-GCM authenticated encryption;
- a PBKDF2-HMAC-SHA256 password-derived key with a random salt;
- a fresh random GCM IV per export;
- bounded input sizes and validated crypto parameters before decryption;
- no server upload and no VeVak account;
- no audit history in the backup;
- no active authorisation timestamps in the backup;
- no temporary discreet-mode state in the backup;
- a wrong password, corruption or invalid configuration leaves current settings unchanged;
- after successful import every restored contact is paused and must be re-authorised locally;
- VeVak never stores or recovers the backup password.

Because an encrypted file can still be copied by someone with device/file access, the backup is not a substitute for normal device lock-screen security. A future device-credential prompt before export may be considered as additional hardening, but adding it must not make encrypted portability dependent on a proprietary SDK.

## Trusted place / home Wi-Fi shortcut

The owner may optionally register the currently connected Wi-Fi network as a trusted place and give it a local label such as `Maison`.

VeVak stores only a SHA-256 fingerprint of the SSID rather than the SSID in clear text. On a **normal** request, if Android exposes the current Wi-Fi network and its fingerprint matches the saved trusted place, VeVak may answer with the chosen label instead of acquiring a fresh GPS position. This is a battery/privacy shortcut, not a proof of physical presence; if the network cannot be identified, VeVak falls back to the normal location path.

The trusted-place shortcut must never weaken duress safety. A duress request does not inspect the current Wi-Fi network at all.

## Safety fallback / duress mode

The owner may optionally configure a second exact phrase and save a fallback location locally.

When that phrase is received from an authorised phone number:

- the fallback coordinates are used;
- **the real location repository is never called**;
- the current Wi-Fi/trusted-place state is never read;
- no GPS/current-location acquisition is attempted;
- the reply uses the same public SMS format as a normal location reply;
- the local audit records only the generic request outcome and does not mark the event as a duress request;
- the notification is generic and does not reveal that duress mode was used.

This is deliberately fail-safe. The safety phrase is checked before the selected contact's normal phrase. If any configured normal phrase collides with the safety phrase through corrupted/legacy settings, the safety path wins. If fallback coordinates are invalid, VeVak returns no real location rather than falling through to the normal GPS path.

The safety phrase must be clearly distinct from **every** configured normal contact phrase. VeVak rejects equal phrases, phrases contained inside one another and pairs with too-small an edit distance.

### Why the audit does not identify duress requests

A detailed audit would be useful for debugging but could expose the existence/use of the safety fallback to a person inspecting the phone. VeVak therefore records only whether a request was replied to, unavailable, rate-limited, blocked because visibility was disabled, blocked because authorisation was inactive, or failed to send.

## Revocation behaviour

Revocation is local and silent. VeVak does not send a special SMS saying that access has been revoked. A matching request from an expired/revoked contact produces no location reply.

Revoking one contact does not silently revoke another contact, and does not reset the device-wide anti-tracking quota in a way that could increase the number of allowed replies.

The requester must not be given a protocol-level distinction between:

- revoked access;
- expired access;
- an unavailable phone;
- a phone with VeVak removed or disabled.

This reduces the chance that revocation itself becomes a trigger for escalation.

## Migration safety

Older VeVak settings do not automatically become a permanent authorisation. The original single contact remains the primary contact after migration and keeps only the finite timestamps already stored locally.

Builds that predate time-limited authorisation have no `authorization_granted_at` / `authorization_expires_at` values, so they enter the paused state after upgrade and require a new local confirmation.

Additional-contact storage is empty by default on upgrade. No second contact is inferred from the address book, SMS history or any remote source.

Legacy request intervals below the anti-tracking floor are clamped to at least 15 minutes.

New trusted-place and discreet-mode settings are disabled by default for existing installations. No Wi-Fi identifier is captured automatically during migration.

## Features that require a separate abuse review

Before implementing any of the following, the threat model must be revisited and the feature must not land merely because it is technically possible:

- temporary/continuous tracking;
- remote device locking;
- remote configuration changes;
- remote camera or microphone access;
- hidden notifications or hidden app state;
- automatic forwarding to third parties;
- **different remote privileges or administrative roles per contact**;
- cloud relay or account recovery that can alter authorisation remotely.

A Find-Hub-inspired device-recovery feature must remain clearly separated from interpersonal tracking and must not weaken the rules above.

## Release checklist for abuse resistance

Before a public release, test at minimum:

- normal request from each configured contact while that contact is authorised;
- request from an expired/revoked contact while another contact remains active (must send no location and must not remove the valid active-status notification);
- adding a duplicate phone number is rejected;
- adding a contact requires explicit local consent and a finite duration;
- device-wide rate-limit floor/daily cap remains global across requests from different contacts;
- normal request while notification permission/channel is disabled (must send no location);
- discreet mode for 1 h / 8 h / 24 h: request notification must remain visible but silent, and full notification disable must still block replies;
- manual outgoing share requires explicit contact selection + confirmation and cancellation sends nothing;
- manual outgoing share in discreet mode creates no additional Android notification and reports the result only in-app;
- manual outgoing share with unavailable location sends no location SMS;
- manual outgoing share uses the configured default SMS SIM and blocks when none is configured;
- per-contact authorisation expiry, revocation and re-authorisation;
- trusted Wi-Fi match: normal request must return the local label without real-location acquisition;
- trusted Wi-Fi unavailable/non-match: normal request must fall back to the normal location path;
- safety phrase remains distinct from every normal contact phrase;
- safety phrase with valid fallback (must send fallback and never invoke real-location or trusted-network acquisition);
- safety phrase with missing/corrupt fallback (must not fall through to real GPS);
- phrase-collision fail-safe behaviour;
- encrypted backup round-trip restores configuration but no active authorisation or discreet mode;
- wrong backup password/corrupt backup leaves current configuration unchanged;
- request audit contains no coordinates, SMS text, phone number, phrase, Wi-Fi identifier or duress marker;
- upgrade from legacy settings preserves no permanent authorisation;
- screen-off/background behaviour on real devices;
- dual-SIM/eSIM behaviour.

## External review

Code review and tests cannot fully model coercive control. Before positioning VeVak as a public safety product, the abuse-prevention model should be reviewed with people or organisations experienced in technology-facilitated intimate-partner abuse / stalkerware safety.
