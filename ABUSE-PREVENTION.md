# Abuse prevention and coercion safety

VeVak is a personal-safety tool, not a partner-monitoring or employee-tracking tool. This document defines product invariants intended to make coercive surveillance materially harder.

## Threat model

A relevant abuse case is a controlling partner, family member, employer or other person who pressures the phone owner into installing/configuring VeVak and then uses the authorised SMS command to reconstruct their movements.

No application can prove that consent was freely given when another person has physical, social or financial control over the owner. VeVak therefore combines explicit consent with technical limits that remain in force even when the authorised contact knows the normal command phrase.

## Non-negotiable invariants

1. **No covert mode.** VeVak does not hide its launcher icon, disguise itself, suppress Android permission indicators or provide a remote way to conceal its presence.
2. **No remote configuration.** Contacts, phrases, authorisation periods, trusted places, discreet-mode duration and the safety fallback are changed only on the phone that is being located.
3. **No permanent authorisation.** The current build offers 24 hours, 7 days or 30 days. After expiry the configuration remains, but automatic location replies stop until the owner explicitly re-authorises them locally.
4. **Immediate local revocation.** The owner can stop automatic replies from the home screen without deleting the application or notifying the requesting contact.
5. **Visibility is a precondition.** If VeVak cannot post a request notification, it refuses to send an automatic location response. A locally enabled discreet period may make that request notification silent and low-importance, but it never removes local visibility. On Android 13+ this makes `POST_NOTIFICATIONS` part of the safety boundary rather than an optional cosmetic permission.
6. **Hard anti-tracking limits.** Automatic replies are separated by at least 15 minutes and capped at four replies per 24-hour window. These limits cannot be increased from the UI.
7. **No periodic location.** VeVak does not maintain breadcrumbs, journeys, location history or a background polling loop.
8. **No remote sensors.** Remote photo, microphone/audio capture and similar surveillance capabilities are out of scope.
9. **Minimal local audit.** The phone keeps at most 20 recent request outcomes. It never stores coordinates, SMS bodies, Wi-Fi identifiers or request phrases in this audit trail.
10. **No secret leakage in diagnostics.** Phone numbers, phrases, Wi-Fi identifiers, coordinates and whether a request used the safety fallback are excluded from redacted diagnostics.

## Request visibility and temporary discreet mode

While an authorisation is active, VeVak posts an ongoing local status notification when notification policy permits it. A matching request also produces a local notification.

If the relevant request-notification channel is disabled, notifications are globally disabled, or Android 13+ notification permission is missing, automatic replies are blocked. A location must never be sent invisibly merely because SMS/location permissions remain granted.

The owner may locally enable a **temporary discreet mode** for 1 hour, 8 hours or 24 hours, capped by the remaining authorisation period. In this mode:

- request notifications use a dedicated low-importance channel;
- they are silent and do not vibrate by default;
- they remain visible in the Android notification shade;
- the ongoing `VeVak est actif` notification remains visible and indicates that discreet mode is active;
- disabling Android notifications entirely still blocks automatic replies.

The ongoing notification identifies the authorised contact and the authorisation expiry. It does not expose the normal phrase, the safety phrase, the trusted Wi-Fi identifier or the fallback coordinates.

## Trusted place / home Wi-Fi shortcut

The owner may optionally register the currently connected Wi-Fi network as a trusted place and give it a local label such as `Maison`.

VeVak stores only a SHA-256 fingerprint of the SSID rather than the SSID in clear text. On a **normal** request, if Android exposes the current Wi-Fi network and its fingerprint matches the saved trusted place, VeVak may answer with the chosen label instead of acquiring a fresh GPS position. This is a battery/privacy shortcut, not a proof of physical presence; if the network cannot be identified, VeVak falls back to the normal location path.

The trusted-place shortcut must never weaken duress safety. A duress request does not inspect the current Wi-Fi network at all.

## Safety fallback / duress mode

The owner may optionally configure a second exact phrase and save a fallback location locally.

When that phrase is received from the same authorised phone number:

- the fallback coordinates are used;
- **the real location repository is never called**;
- the current Wi-Fi/trusted-place state is never read;
- no GPS/current-location acquisition is attempted;
- the reply uses the same public SMS format as a normal location reply;
- the local audit records only the generic request outcome and does not mark the event as a duress request;
- the notification is generic and does not reveal that duress mode was used.

This is deliberately fail-safe. If legacy/corrupted settings ever make the normal and safety phrases collide, the safety phrase wins. If fallback coordinates are invalid, VeVak returns no real location rather than falling through to the normal GPS path.

The two phrases must be clearly distinct. VeVak rejects equal phrases, phrases contained inside one another and pairs with too-small an edit distance.

### Why the audit does not identify duress requests

A detailed audit would be useful for debugging but could expose the existence/use of the safety fallback to a person inspecting the phone. VeVak therefore records only whether a request was replied to, unavailable, rate-limited, blocked because visibility was disabled, blocked because authorisation was inactive, or failed to send.

## Revocation behaviour

Revocation is local and silent. VeVak does not send a special SMS saying that access has been revoked. A matching request after expiry/revocation produces no location reply.

The requester must not be given a protocol-level distinction between:

- revoked access;
- expired access;
- an unavailable phone;
- a phone with VeVak removed or disabled.

This reduces the chance that revocation itself becomes a trigger for escalation.

## Migration safety

Older VeVak settings do not automatically become a permanent authorisation. Builds that predate time-limited authorisation have no `authorization_granted_at` / `authorization_expires_at` values, so they enter the paused state after upgrade and require a new local confirmation.

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
- multiple contacts with different privileges;
- cloud relay or account recovery that can alter authorisation remotely.

A Find-Hub-inspired device-recovery feature must remain clearly separated from interpersonal tracking and must not weaken the rules above.

## Release checklist for abuse resistance

Before a public release, test at minimum:

- normal request while notifications are enabled;
- normal request while notification permission/channel is disabled (must send no location);
- discreet mode for 1 h / 8 h / 24 h: request notification must remain visible but silent, and full notification disable must still block replies;
- authorisation expiry and local revocation (must send no location);
- rate-limit floor and daily cap;
- trusted Wi-Fi match: normal request must return the local label without real-location acquisition;
- trusted Wi-Fi unavailable/non-match: normal request must fall back to the normal location path;
- safety phrase with valid fallback (must send fallback and never invoke real-location or trusted-network acquisition);
- safety phrase with missing/corrupt fallback (must not fall through to real GPS);
- phrase-collision fail-safe behaviour;
- request audit contains no coordinates, SMS text, phrase, Wi-Fi identifier or duress marker;
- upgrade from legacy settings pauses authorisation;
- screen-off/background behaviour on real devices;
- dual-SIM/eSIM behaviour.

## External review

Code review and tests cannot fully model coercive control. Before positioning VeVak as a public safety product, the abuse-prevention model should be reviewed with people or organisations experienced in technology-facilitated intimate-partner abuse / stalkerware safety.
