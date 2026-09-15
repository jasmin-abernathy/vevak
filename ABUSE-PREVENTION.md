# Abuse prevention and coercion safety

VeVak is a personal-safety tool, not a partner-monitoring or employee-tracking product. This document defines the product invariants intended to make coercive surveillance materially harder.

## Threat model

A relevant abuse case is a controlling partner, family member, employer or other person who pressures the phone owner into configuring VeVak and then uses an authorised SMS phrase to reconstruct their movements.

No application can prove that consent was freely given. VeVak therefore combines finite authorisations, local revocation, a hard device-wide rate limit, deliberately limited data retention and an optional protection path for a specific authorised contact.

## Non-negotiable invariants

1. **VeVak itself is not hidden or remotely disguised.** The normal launcher entry remains VeVak. The optional emergency home-screen shortcut may use a generic local name/icon, but it does not rename, replace or conceal the VeVak application.
2. **No remote configuration.** Contacts, phrases, authorisation periods, trusted places, emergency recipients, refresh preferences and protection settings are changed only on the phone being located.
3. **No permanent authorisation.** The current build offers 24 hours, 7 days or 30 days. Expired contacts stop receiving automatic replies until locally re-authorised.
4. **Immediate local revocation per contact.** One contact can be stopped without deleting VeVak or affecting another contact.
5. **Hard anti-tracking limits remain global.** Automatic replies are separated by at least 15 minutes and capped at four replies per 24-hour window for the entire device. Adding contacts never multiplies this cap.
6. **No route/history.** VeVak may keep one latest coordinate for resilience, but it does not retain a sequence of positions, journeys or breadcrumbs.
7. **No continuous background tracking.** Background-location access is optional, requested only for the owner's single-slot refresh setting, and never required for incoming SMS replies. Disabling the setting cancels its next alarm.
8. **No remote sensors.** Remote photo, microphone/audio capture and similar surveillance capabilities are out of scope.
9. **Minimal local audit.** At most 20 recent request outcomes are kept, without coordinates, SMS bodies, contact numbers, Wi-Fi identifiers or request phrases.
10. **No secret leakage in diagnostics.** Phone numbers, phrases, Wi-Fi identifiers, coordinates and whether a request used the protection fallback are excluded from redacted diagnostics.
11. **Manual sharing is local-only.** It is initiated on the phone, requires local recipient selection and confirmation, and uses the same canonical resolver as normal requests rather than starting a special tracking path.
12. **Backups cannot silently restore access.** Encrypted configuration backups never restore active authorisation timestamps.
13. **Notification refusal never changes SMS security.** Automatic replies do not depend on `POST_NOTIFICATIONS`, request notifications or a permanent status notification. The permission exists only for optional emergency feedback.
14. **Emergency is a separate local action.** The voluntary emergency send is not throttled by the remote-request quota, but it also cannot be triggered by a remote SMS command.
15. **Protected-contact settings are local and deliberately opt-in.** They are never auto-suggested from SMS activity and are edited only in a password-gated additional-settings area.

## Several trusted contacts

VeVak can hold up to five locally configured contacts. Each has a phone number, a normal request phrase, its own finite authorisation and its own local revoke/reactivate controls.

Duplicate phone numbers are rejected. Adding a contact requires explicit local consent and a finite duration. The global rate limiter is shared by all contacts.

Per-contact remote privilege matrices such as camera, microphone, lock or administration remain out of scope.

## Phrase matching

Phrase matching is case-insensitive and normalises common SMS typography such as non-breaking spaces and typographic apostrophes.

The configured phrase may appear anywhere in an ordinary SMS. Matching still respects word boundaries so a short key is not accidentally found inside an unrelated word.

The wider matching rule does **not** bypass sender identity, contact authorisation or the global anti-tracking quota.

## Notification behavior

Routine incoming-request handling is silent. VeVak does not show a notification for each authorised SMS request and does not keep a permanent `VeVak actif` notification.

`POST_NOTIFICATIONS` is declared only because the owner may explicitly choose a temporary emergency-feedback notification. Refusing that permission never changes automatic SMS replies, contact authorisation, rate limiting or emergency dispatch.

The protection model is therefore based on finite local authorisation, immediate revocation, global rate limiting, minimal audit and the targeted protection mode rather than on routine notification visibility.

## Position memory and optional refresh

Normal SMS requests, manual sharing and local emergency use one canonical resolution contract: Android location, trusted place, explicitly opted-in network/IP estimate, latest remembered coordinate, then unavailable.

The implementation may retain a separate last-real/local slot for bounded internal purposes, but manual sharing must not silently diverge into an older last-real-only policy. The user-facing resolver contract remains shared.

The owner may opt in to a best-effort refresh target of 15, 30 or 60 minutes. This feature must remain a **single-slot memory**, not a movement history.

Implementation constraints:

- background-location access is optional and dedicated to this user-enabled feature;
- no exact/repeating alarm loop;
- no periodic WorkManager loop;
- no foreground location service kept alive merely to force a cadence;
- Android/Doze may defer attempts;
- every attempt rechecks that the option is enabled and an active contact authorisation still exists;
- network/IP estimation is only allowed if the owner separately opted into it.

An optional boot setting may re-schedule the next attempt after the phone restarts. It does not open VeVak or create a permanent notification.

## Manual outgoing position share

The phone owner may voluntarily send a position or recognised place to a configured trusted contact.

The flow requires local recipient selection and explicit confirmation. It uses the same canonical resolver as normal requests and emergency: Android position, trusted place, network/IP estimate only if already enabled, then remembered coordinate. It does not start a tracking loop or enable network estimation by itself.

It uses Android's configured default SMS subscription and blocks if no default SMS SIM is available. The UI distinguishes handing a message to Android for sending from proof of delivery.

## Emergency recipients and discreet shortcut

Emergency recipients are selected in advance: either all currently active trusted contacts or a local subset. The selection is saved locally, so an emergency trigger does not ask again who should receive the SMS.

Emergency is unconfigured by default. Onboarding and Safety settings preselect nobody; the owner must make an explicit choice before a shortcut can send anything.

The emergency send:

- uses the same canonical resolver as authorised normal requests and manual share, including trusted places;
- preserves the source and age of coordinate-bearing results;
- uses network/IP estimation only with the existing explicit opt-in and an inexact-position warning;
- does not add reverse-geocoder address text;
- is not subject to the automatic-request rate limiter;
- remains local-only and cannot be requested remotely.

VeVak may ask Android to pin an additional generic home-screen shortcut. The provided names are generic; the object icons come from Streamline Ultimate Color (CC BY 4.0, attribution included) and do not imitate an existing application. The real VeVak launcher entry remains available.

An optional Quick Settings tile may expose the same local emergency action. Neither shortcut nor tile is a second location resolver.

### Accidental-tap protection

The shortcut/tile action uses a four-second grace period:

- first activation arms the emergency send;
- a second activation during the grace period cancels it;
- otherwise dispatch is requested after the delay; Android may postpone it without arbitrary expiry;
- a later activation can cancel a pending request until receiver claim;
- after dispatch, the next activation starts a new sequence.

The owner chooses the local feedback mode: silence by default, short vibration, or a temporary silent notification. The notification is optional, contains no position/contact/SMS content, is hidden on the lock screen, and never proves delivery. It must not introduce a permanent service, persistent notification or retry loop.

## Trusted place / home Wi-Fi

The owner may register the current Wi-Fi network as a trusted place such as `Maison`. VeVak stores a local fingerprint rather than the clear-text SSID when Android exposes enough information.

On a normal request, manual share or local emergency, a recognised trusted place may be returned without acquiring a fresh GPS fix. If the network cannot be recognised reliably, VeVak falls back to the canonical resolver rather than guessing.

Trusted-place detection is never consulted for a protected-contact request.

## Protection targeted at one authorised contact

New configurations do not require a second safety phrase. The owner selects the authorised contact whose use of the normal phrase they fear.

That contact continues using its existing phrase. When a matching request comes from the protected contact:

- only the pre-recorded fallback coordinates are used;
- the real location repository is never called;
- current Wi-Fi / trusted-place state is never read;
- no current GPS acquisition is attempted;
- no network/IP fallback is requested;
- the local audit stores only the generic outcome and does not identify the protection path.

Other authorised contacts keep the canonical resolver.

If the fallback coordinates are missing or corrupt, VeVak must not fall through to the real-location path.

The feature is disabled by default, is never proposed automatically after one or more SMS requests, and is configured only on demand in `Paramètres supplémentaires` behind a local password. The normal home screen and standard diagnostics must not reveal whether it is configured.

The password itself is never stored. Only a salted PBKDF2 verifier remains in app-private storage. First-time password creation requires confirmation of the Android device credential. The private area locks when the user leaves it.

Older beta backups with the former separate protection phrase remain migration-compatible, but the new UI does not ask the user to create one.

## Encrypted configuration backup/import

Configuration portability remains a free resilience/privacy feature.

The `.vvk` backup is encrypted/authenticated with AES-GCM using a password-derived PBKDF2-HMAC-SHA256 key, random salt and random IV. VeVak never stores the backup password.

The backup contains configuration only. It excludes request audit history, remembered positions and active authorisation timestamps. After import every restored contact is paused until locally re-authorised.

Refresh preferences may be restored, but because contact authorisations are revoked the scheduler cannot immediately resume location attempts until a contact is locally re-authorised.

The verifier for the additional-settings password is never exported. If that local password already exists on the device, it must be confirmed before export, import or in-app reset. This local gate password is distinct from the `.vvk` encryption password.

## Revocation behaviour

Revocation is local and silent. VeVak does not send a special SMS announcing that access was revoked. A matching request from an expired/revoked contact produces no location reply.

Revoking one contact does not reset the device-wide anti-tracking quota or silently revoke another contact.

The requester should not receive a protocol-level distinction between revoked access, expired access, an unavailable phone or VeVak being disabled/removed.

## Features requiring a separate abuse review

Before implementing any of the following, the threat model must be revisited:

- continuous or historical tracking;
- remote device locking;
- remote configuration changes;
- remote camera or microphone access;
- hiding the VeVak application itself;
- automatic forwarding to unrelated third parties;
- different remote administrative privileges per contact;
- cloud relay/account recovery able to alter authorisation remotely.

## Release checklist for abuse resistance

Before a stable public release, test at minimum:

- normal request from each authorised contact;
- expired/revoked contact while another remains active;
- global 15-minute floor / four-per-24h cap across several contacts;
- phrase-key alone and phrase-key inside a longer SMS;
- notifications refused/absent while automatic SMS still works;
- Wi-Fi, 4G/5G-only and location Android ON/OFF fallback behaviour;
- last remembered coordinate with correct age;
- optional refresh at 15/30/60 minutes without history creation;
- restart/boot scheduling option;
- targeted protection contact vs another normal contact;
- protection disabled → the same contact follows the normal resolver;
- missing/corrupt protection fallback never exposing real location;
- private settings relock on leaving the screen and do not appear in standard diagnostics;
- manual share confirmation, default-SIM handling and canonical resolver behavior;
- emergency recipient subset and repeated emergency sends;
- emergency shortcut/tile first activation, second-activation cancellation and dispatch requested after four seconds (system delays possible);
- each optional emergency-feedback mode, including notification refusal;
- encrypted backup round-trip with every contact revoked;
- private-settings password required for configuration export/import/reset when configured;
- audit/diagnostics contain no sensitive location/contact/phrase/protection data;
- FOSS and Play tests/build/lint plus static privacy/ecodesign checks;
- real-device screen-off, Doze, launcher, sideload/restricted-settings and dual-SIM behaviour.

## External review

Code review and tests cannot fully model coercive control. Before positioning VeVak as a stable public safety product, the abuse-prevention model should be reviewed with people or organisations experienced in technology-facilitated intimate-partner abuse and stalkerware safety.
