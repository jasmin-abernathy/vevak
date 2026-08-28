# VeVak on iOS — contributor brief

VeVak is currently implemented for Android. This document defines what an iOS contributor should preserve, what can be prototyped first, and which Android behaviours must **not** be assumed to exist on iOS.

## Goal

Explore a native iOS version of VeVak that follows the same product values:

- privacy-first;
- local-first whenever possible;
- no mandatory VeVak account or central server for the core experience;
- no advertising, trackers or telemetry;
- explicit user control;
- no covert tracking;
- no claim that VeVak replaces emergency services;
- honest handling of failure and delivery uncertainty.

The iOS implementation should be treated as a platform adaptation, **not** as a literal Android port.

## Important platform difference

The current Android core can react to an authorised incoming SMS and send a reply automatically when Android permissions and background execution allow it.

A normal iOS application must not assume it can reproduce that exact flow.

Apple's public Message Filter APIs are designed for filtering SMS/MMS from unknown senders and do not provide a general-purpose replacement for Android SMS receivers. Apple's documentation also states that IdentityLookup message filtering does not work for messages from senders in Contacts or for iMessage messages.

Reference:
- https://developer.apple.com/documentation/identitylookup/sms-and-mms-message-filtering

For outgoing SMS, Apple's standard `MFMessageComposeViewController` presents a system compose interface. The app may prefill the recipient and message, but the person can edit, cancel or send it. Presenting the composer does not prove delivery.

Reference:
- https://developer.apple.com/documentation/messageui/mfmessagecomposeviewcontroller

Therefore, contributors must **not** claim that iOS currently supports VeVak's Android-style automatic SMS request/reply path unless a compliant public Apple API is identified and documented.

## First realistic iOS milestone

The first prototype should focus on **manual outgoing position sharing**.

Suggested native flow:

1. User opens VeVak.
2. User taps an explicit “Send my position” action.
3. VeVak shows a confirmation step before acquiring location.
4. VeVak obtains one bounded location result using Core Location.
5. VeVak prepares the SMS content for the configured trusted contact.
6. VeVak presents Apple's standard message composer.
7. The user explicitly sends or cancels.
8. VeVak reports only what it can actually know; it must not present the result as guaranteed delivery.

This corresponds closely to the manual outgoing position-share feature already implemented on Android while respecting Apple's interaction model.

## iOS features worth investigating after the first prototype

Contributors may investigate platform-native integrations such as:

- App Intents;
- Shortcuts;
- Siri / Spotlight exposure where appropriate;
- Action Button shortcuts where supported;
- widgets or lock-screen entry points only if they remain explicit and safe from accidental triggering.

Apple App Intents documentation:
- https://developer.apple.com/documentation/appintents

Any shortcut capable of sharing location must preserve the project's consent and anti-coercion model. Convenience must not become a hidden or remotely triggerable tracking path.

## Shared product rules that must remain unchanged

An iOS implementation must preserve these invariants:

- no hidden app mode;
- no remote configuration of contacts, phrases or safety settings;
- no periodic location history in the core;
- no background breadcrumb trail;
- no remote camera or microphone feature;
- no automatic call to emergency services;
- no mandatory cloud account for the core experience;
- no sensitive data in public logs, diagnostics or issue reports;
- location must never be described as guaranteed;
- SMS/message delivery must never be described as guaranteed;
- features related to coercion, stalking or partner surveillance require explicit abuse review.

Read before implementing:

- `ABUSE-PREVENTION.md`
- `PRIVACY.md`
- `SECURITY.md`
- `ROADMAP.md`

## Duress / safety fallback

Do not copy the Android duress path blindly.

If an iOS safety-fallback feature is explored, it must preserve the same core invariant: a safety/duress action must never accidentally expose the current real location.

The exact interaction should be threat-modelled specifically for iOS before implementation.

## Notifications and discreet mode

The Android implementation has platform-specific notification rules. iOS contributors should reproduce the **product intent**, not Android APIs:

- automatic or externally triggered location sharing must never become invisible to the phone owner;
- manual sharing is an explicit foreground action and should avoid unnecessary extra alerts;
- any discreet-mode equivalent must be finite, locally enabled and must not create a covert tracking mode.

## Repository strategy

Do **not** add Xcode-generated project files to the Android app structure merely to make the repository appear cross-platform.

Recommended approach:

1. Use this repository for product rules, feasibility discussion and the initial iOS issue.
2. Once an iOS contributor is ready to build a real Swift/Xcode prototype, create a dedicated `vevak-ios` repository.
3. Keep product invariants and interoperability decisions synchronised between the Android and iOS projects.

The Android `foss` and `play` flavors remain in this repository and should not be reorganised for the iOS effort.

## Interoperability goal

A trusted contact may already use an iPhone to send an ordinary SMS command to a VeVak-enabled Android phone. No iOS VeVak app is required for that role.

The iOS project is needed when the **phone being protected / located is itself an iPhone**.

Where possible, message wording and user concepts should remain compatible across platforms, but platform safety and OS constraints take priority over protocol symmetry.

## Definition of a useful first contribution

A strong first iOS contribution is not “recreate the whole Android app”. It is one of the following:

- document current public iOS API feasibility with links to Apple documentation;
- propose a native Swift architecture for the manual-share prototype;
- implement a minimal Core Location + MessageUI proof of concept in a dedicated iOS repository after maintainers approve the architecture;
- review the proposed iOS flow for accessibility, privacy and coercion risks.

## What not to do

Please do not:

- use private Apple APIs;
- rely on App Store policy bypasses;
- introduce a mandatory server solely to simulate Android SMS behaviour;
- claim that an iOS background SMS trigger works without a reproducible public-API demonstration;
- weaken VeVak's safety model for feature parity.

The correct iOS version may intentionally have fewer automatic features than Android.