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
7. **No permanent background-location permission.** `ACCESS_BACKGROUND_LOCATION` is not declared.
8. **No remote sensors.** Remote photo, microphone/audio capture and similar surveillance capabilities are out of scope.
9. **Minimal local audit.** At most 20 recent request outcomes are kept, without coordinates, SMS bodies, contact numbers, Wi-Fi identifiers or request phrases.
10. **No secret leakage in diagnostics.** Phone numbers, phrases, Wi-Fi identifiers, coordinates and whether a request used the protection fallback are excluded from redacted diagnostics.
11. **Manual sharing is local-only.** It is initiated on the phone, requires local confirmation and uses only the last real/local point already known.
12. **Backups cannot silently restore access.** Encrypted configuration backups never restore active authorisation timestamps.
13. **Notification refusal never changes SMS security.** Since 0.3.11 automatic replies do not depend on `POST_NOTIFICATIONS`, request notifications or a permanent status notification.
14. **Emergency is a separate local action.** The voluntary emergency send is not throttled by the remote-request quota, but it also cannot be triggered by a remote SMS command.

## Several trusted contacts

VeVak can hold up to five locally configured contacts. Each has a phone number, a normal request phrase, its own finite authorisation and its own local revoke/reactivate controls.

Duplicate phone numbers are rejected. Adding a contact requires explicit local consent and a finite duration. The global rate limiter is shared by all contacts.

Per-contact remote privilege matrices such as camera, microphone, lock or administration remain out of scope.

## Phrase matching

Phrase matching is case-insensitive and normalises common SMS typography such as non-breaking spaces and typographic apostrophes.

Since 0.3.11 the configured phrase may appear anywhere in an ordinary SMS. Matching still respects word boundaries so a short key is not accidentally found inside an unrelated word.

The wider matching rule does **not** bypass sender identity, contact authorisation or the global anti-tracking quota.

## Notification-free request handling

VeVak 0.3.11 removes request notifications and the permanent `VeVak actif` notification. `POST_NOTIFICATIONS` is no longer declared.

This decision follows beta feedback that routine notifications were intrusive. The safety boundary is therefore based on finite local authorisation, immediate revocation, global rate limiting, minimal audit and the targeted protection mode rather than on notification visibility.

After two successful normal replies for the same contact, VeVak may keep a bounded local counter. On a later voluntary app launch, it can ask whether the owner fears that this contact might use their phrase to obtain the real position against their wishes. The request itself creates no notification or prompt.

## Position memory and optional refresh

Automatic phrase-key replies may use the last coordinate VeVak legitimately obtained, regardless of age, and must state its age. Manual sharing and emergency use a separate last-real/local slot so a network/IP estimate cannot replace their stricter fallback.

The owner may opt in to a best-effort refresh target of 15, 30 or 60 minutes. This feature must remain a **single-slot memory**, not a movement history.

Implementation constraints:

- no `ACCESS_BACKGROUND_LOCATION`;
- no exact/repeating alarm loop;
- no periodic WorkManager loop;
- no foreground location service kept alive merely to force a cadence;
- Android/Doze may defer attempts;
- every attempt rechecks that the option is enabled and an active contact authorisation still exists;
- network/IP estimation is only allowed if the owner separately opted into it.

An optional boot setting may re-schedule the next attempt after the phone restarts. It does not open VeVak or create a permanent notification.

## Manual outgoing position share

The phone owner may voluntarily send the last real/local position already known to a configured trusted contact.

The flow requires local recipient selection and explicit confirmation. It does not start a remote-triggerable share, a tracking loop or an IP fallback. It uses Android's configured default SMS subscription and blocks if no default SMS SIM is available.

The UI distinguishes handing a message to Android for sending from proof of delivery.

## Emergency recipients and discreet shortcut

Emergency recipients are selected in advance: either all currently active trusted contacts or a local subset. The selection is saved immediately, so an emergency trigger does not ask again who should receive the SMS.

The emergency send:

- uses only the last real/local position already known;
- preserves its age;
- does not use network/IP estimation;
- does not add reverse-geocoder address text;
- is not subject to the automatic-request rate limiter;
- remains local-only and cannot be requested remotely.

VeVak may ask Android to pin an additional generic home-screen shortcut. The provided names/icons are original/generic and do not imitate an existing application. The real VeVak launcher entry remains available.

The shortcut uses a local random token and only delegates to the canonical emergency action. It is not a second location resolver.

### Accidental-tap protection

The shortcut uses a four-second grace period:

- first tap arms the emergency send;
- a second tap on the same shortcut during the grace period cancels it;
- otherwise the canonical emergency send is dispatched after the delay;
- after dispatch, the next tap starts a new sequence.

No VeVak notification is shown after shortcut use, because such a notification would defeat the discretion of the shortcut.

## Trusted place / home Wi-Fi

The owner may register the current Wi-Fi network as a trusted place such as `Maison`. VeVak stores a local fingerprint rather than the clear-text SSID when Android exposes enough information.

On a normal request, a recognised trusted place may be returned without acquiring a fresh GPS fix. If the network cannot be recognised reliably, VeVak falls back to the normal resolver rather than guessing.

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

Other authorised contacts keep the normal resolver.

If the fallback coordinates are missing or corrupt, VeVak must not fall through to the real-location path.

Older beta backups with the former separate protection phrase remain migration-compatible, but the new UI does not ask the user to create one.

## Encrypted configuration backup/import

Configuration portability remains a free resilience/privacy feature.

The `.vvk` backup is encrypted/authenticated with AES-GCM using a password-derived PBKDF2-HMAC-SHA256 key, random salt and random IV. VeVak never stores the backup password.

The backup contains configuration only. It excludes request audit history, remembered positions and active authorisation timestamps. After import every restored contact is paused until locally re-authorised.

Refresh preferences may be restored, but because contact authorisations are revoked the scheduler cannot immediately resume location attempts until a contact is locally re-authorised.

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
- missing/corrupt protection fallback never exposing real location;
- manual share confirmation, default-SIM handling and last-real-only behaviour;
- emergency recipient subset and repeated emergency sends;
- emergency shortcut first tap, second-tap cancellation and four-second dispatch;
- encrypted backup round-trip with every contact revoked;
- audit/diagnostics contain no sensitive location/contact/phrase data;
- FOSS and Play tests/build/lint plus static privacy/ecodesign checks;
- real-device screen-off, Doze, launcher, sideload/restricted-settings and dual-SIM behaviour.

## External review

Code review and tests cannot fully model coercive control. Before positioning VeVak as a stable public safety product, the abuse-prevention model should be reviewed with people or organisations experienced in technology-facilitated intimate-partner abuse and stalkerware safety.
