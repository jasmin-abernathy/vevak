# VeVak monetisation and paid-feature boundaries

VeVak may offer paid features in the future, but monetisation must not weaken the project's privacy, FOSS, accessibility or abuse-prevention principles.

This document defines the architecture and product rules before billing is implemented.

## Short version

- The Android source remains public and GPL-3.0-or-later.
- The `foss` flavor remains canonical and does not depend on proprietary billing SDKs.
- Paid access is represented through a small entitlement abstraction in the public codebase.
- Core safety, consent and resilience behaviour stays available without a paid entitlement.
- **Several trusted contacts and encrypted configuration backup/import are free baseline features.**
- Paid features should primarily be convenience, advanced configuration or optional service-backed features.
- No ads, tracking or sale of personal data are introduced as a monetisation fallback.
- A separate private backend may be considered only for a genuinely separate optional paid service, such as a future relay, and must never become mandatory for VeVak's core SMS mode.

## What must remain free

The following capabilities are part of VeVak's safety, consent or resilience baseline and must not be disabled because the user has no paid entitlement:

- receiving and validating authorised SMS requests;
- the core one-shot location/reply path;
- **several locally configured trusted contacts, with separate local authorisation/expiry/revocation**;
- manual outgoing position sharing to a configured contact;
- local revocation and authorisation expiry;
- anti-tracking rate limits, including a global device-wide cap that is not multiplied by adding contacts;
- request visibility safeguards;
- the duress/safety-fallback protections once they are part of the supported build;
- safety-critical diagnostics needed to understand whether VeVak can operate;
- privacy controls and local data deletion;
- **password-protected encrypted export/import of the user's own VeVak configuration**.

A paid feature must never make the free safety path deliberately unreliable or harder to revoke.

### Backup-specific baseline rules

The encrypted backup feature is intentionally free because it is a portability and resilience control, not a luxury feature.

- backups are created locally through Android's document picker;
- no VeVak server is required;
- the backup contains configuration, including sensitive contact/phrase/fallback settings, only inside an authenticated encrypted container;
- request audit history is not exported;
- active authorisation timestamps and temporary discreet-mode state are not exported;
- restoring a backup never silently re-authorises a contact;
- after import, contacts must be explicitly re-authorised locally on the destination phone;
- VeVak never stores or recovers the user's backup password.

## Suitable paid-feature candidates

Examples that may be suitable for a paid tier after separate product, security and ecodesign review include:

- several trusted **places** instead of the free single-place shortcut;
- advanced response profiles and per-context convenience rules;
- advanced personalisation that does not affect the reliability of the free safety path;
- future cross-device, desktop, watch or web conveniences that are not required for the core SMS path;
- an optional relay service that incurs real infrastructure costs.

These are candidates, not promises. A feature must not be implemented merely because it can be monetised.

## Public-code model

The official source may contain the implementation of paid capabilities even though official distributed builds require an entitlement to expose them.

This is intentional. VeVak does not treat obscurity of client code as a security boundary.

People compiling their own GPL build may modify the entitlement checks. That is compatible with the project's open-source model. Official binaries, store distribution, support, updates and any optional hosted service can still form the commercial offering.

## Android architecture

The public core defines:

- `EntitlementState` — free/premium state, source and optional expiry;
- `PremiumCapability` — explicit optional capabilities that may be gated;
- `EntitlementProvider` — store/platform boundary;
- `EntitlementRepository` — stable core-facing access point;
- `PremiumAccessPolicy` — fail-closed decision for paid capabilities.

The concrete provider lives in the active Gradle flavor:

- `foss`: no proprietary store SDK, currently returns the free tier;
- `play`: currently returns the free tier and is the future integration point for Google Play Billing.

This keeps Google billing libraries out of the canonical FOSS build.

`PremiumCapability` deliberately does **not** contain trusted-contact management or encrypted configuration backup. Those features must not call `PremiumAccessPolicy`.

## Rules for a future Play Billing implementation

When billing is added to the `play` flavor:

1. Billing dependencies must remain isolated to `app/src/play` / `playImplementation`.
2. Purchase and restore flows must use current public Google Play Billing APIs.
3. VeVak must distinguish purchase acknowledgement from ongoing entitlement state.
4. Entitlement failures must fail closed for paid conveniences, never disable core safety or resilience functions.
5. Where practical, already-entitled local functionality should keep working during temporary network loss.
6. No phone number, SMS text, coordinates, duress phrase, trusted-place data or backup contents may be sent to the billing provider as custom metadata.
7. Billing telemetry must not be repurposed as VeVak analytics.
8. Subscription, one-time purchase and refund semantics must be documented before release.

## Optional private service / relay

A future service such as `VeVak Relay` may justify separate server infrastructure if it provides a real service that the SMS-only core cannot provide.

The design must preserve these rules:

- the server is optional;
- the existing local/SMS mode remains usable without a VeVak account;
- server failure cannot silently alter local consent or anti-abuse settings;
- the relay must not become a covert tracking channel;
- data minimisation and end-to-end encryption should be the default design goal;
- retention must be explicitly bounded;
- the public client must clearly disclose when a request uses the relay rather than SMS;
- a dedicated threat model and privacy review are required before implementation.

A separate backend repository may be private if there is a genuine independently operated service to protect and maintain. Do not create a private repository merely to hide client-side premium code.

GPL licensing and commercial distribution can have legal consequences; this document is an engineering/product rule, not legal advice. Obtain appropriate legal review before a commercial release or a new licensing arrangement.

## iOS

A future iOS implementation should use the same product-level entitlement concepts, but the store implementation should be native to Apple platforms (for example StoreKit) and live in the future iOS repository.

Purchases should not require Android/Google infrastructure, and platform-specific store receipts must not leak VeVak's sensitive location/SMS data.

The same baseline principle applies on iOS: contact authorisation and encrypted portability of the user's own configuration should not become premium-only merely because the platform implementation differs.

## No dark patterns

VeVak must not use:

- countdown pressure to purchase;
- fake emergency urgency;
- ads as punishment for remaining on the free tier;
- deliberately degraded location accuracy for free users;
- hidden recurring billing;
- confusing cancellation language;
- consent screens that bundle safety authorisation with a purchase;
- artificial contact or backup restrictions designed only to push users into a paid tier.

Paid prompts should be calm, optional and clearly separated from emergency/safety messaging.

## Review checklist for every paid capability

Before merging a paid feature, answer:

- What user need does it solve?
- Why is it appropriate to charge for this instead of making it part of the safety/resilience baseline?
- What happens when entitlement is unavailable or expires?
- Does the free safety path remain fully usable?
- Does it add a network, account, SDK or background dependency?
- Is the dependency confined to the appropriate flavor?
- What sensitive data can leave the device?
- What is the CPU/battery/APK-size impact?
- What is the abuse/coercion risk?
- Can the feature be removed cleanly later?
- Are purchase, restore, refund and offline states understandable to the user?

Use `docs/PAID_FEATURE_REVIEW.md` for concrete proposals.
