# Paid feature review

Copy this file into an issue or design note for every capability proposed behind a paid entitlement.

## Feature

- Name:
- Owner / issue:
- Intended platforms:
- Proposed entitlement: one-time / subscription / service-backed / undecided

## User need

- What concrete user problem does this solve?
- What evidence shows the feature is useful?
- What is the simpler non-feature alternative?

## Why paid?

- Why is charging appropriate for this capability?
- Why is it not part of VeVak's free safety baseline?
- What real maintenance, convenience or infrastructure value justifies payment?

## Free-tier safety check

Confirm that a user without entitlement can still:

- receive/respond to the basic authorised SMS request;
- use the supported manual share flow;
- revoke authorisation locally;
- benefit from anti-tracking limits and request visibility;
- use supported duress/safety safeguards;
- access safety-critical diagnostics;
- delete local data.

If any answer is no, stop and redesign the proposal.

## Entitlement behaviour

- Which `PremiumCapability` gates the feature?
- What happens when entitlement is unknown?
- What happens when it expires?
- What happens after refund/revocation?
- What works offline after a valid purchase?
- How is purchase restoration handled?
- Is the result understandable without dark patterns?

## Privacy and security

- New permissions:
- New data leaving the device:
- New account requirement:
- New SDK/dependency:
- New server/service:
- Retention period:
- Encryption model:
- Abuse/coercion risks:
- Why the feature cannot create covert tracking:

No billing or relay metadata may contain phone numbers, SMS bodies, request phrases, coordinates, trusted Wi-Fi identifiers or duress state.

## FOSS / flavor boundary

- Does `foss` still compile without proprietary store dependencies?
- Is any proprietary billing dependency restricted to the relevant flavor?
- Does the public GPL client remain buildable from source?
- Is a private repository actually necessary, or would it merely hide client code?

## Ecodesign

- APK size delta:
- Network activity delta:
- Background activity delta:
- CPU/memory impact:
- Battery impact:
- Dependency weight:
- Removal strategy:

Also complete `docs/FEATURE_ECO_REVIEW.md` when implementation begins.

## UX / accessibility

- Purchase language:
- Cancellation/refund language:
- Accessibility considerations:
- Does any purchase prompt appear in a safety-critical moment? (It should not.)

## Decision

- [ ] approved for prototype
- [ ] needs product discussion
- [ ] needs security/abuse review
- [ ] needs legal/licensing review
- [ ] rejected / keep free

Decision notes:
