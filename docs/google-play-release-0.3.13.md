# Google Play hand-off — VeVak 0.3.13

## Artifact identity

- Application id: `com.vevak.app.play`
- Version name: `0.3.13`
- Version code: `16`
- Required upload: signed Android App Bundle from `bundlePlayRelease`
- Privacy policy: `https://vevak.lepotager.org/confidentialite/`
- Ads: no
- Account required: no
- Target audience: adults; the app is not designed for children

## Build and upload signing

The release build reads the upload keystore only from environment variables and never from a
tracked file:

```text
VEVAK_UPLOAD_STORE_FILE=/absolute/path/to/vevak-upload.jks
VEVAK_UPLOAD_STORE_PASSWORD=...
VEVAK_UPLOAD_KEY_ALIAS=...
VEVAK_UPLOAD_KEY_PASSWORD=...
```

Then run `./gradlew clean bundlePlayRelease`. Before upload, verify the certificate and bundle with
`jarsigner -verify -verbose -certs app/build/outputs/bundle/playRelease/app-play-release.aab`.

Alternatively, add the four values above plus `VEVAK_UPLOAD_KEYSTORE_BASE64` as GitHub Actions
secrets, then run the manual **Google Play release bundle** workflow. It validates the Play flavor
and returns a signed AAB, checksum and de-obfuscation mapping without publishing them publicly.

## SMS permission declaration

VeVak requests `RECEIVE_SMS` and `SEND_SMS` for its critical core feature: a local device automation
configured by the phone owner. A valid incoming SMS from one of the locally authorised numbers,
containing that contact's locally configured phrase, triggers a rate-limited location response.
There is no alternative Android API that can receive arbitrary carrier SMS from an ordinary trusted
contact while the app is closed. The feature is prominently described in the store listing and in
onboarding. Access stops being useful as soon as contacts are revoked or expired.

Select the **Device automation** eligible use in the Play Console SMS/Call Log declaration. Also
describe the separate **physical safety/emergency alert** use of `SEND_SMS`. Approval remains a
Google Play review decision; do not misclassify VeVak as a default SMS handler.

## Background location declaration

Declare one feature only: **periodic refresh of one last location for later authorised safety
requests**. It is off by default, enabled from the Safety screen, and preceded by a prominent
disclosure explaining that VeVak may access location while closed. Each point replaces the previous
one; there is no route, position history, telemetry, advertising or VeVak server upload. The feature
provides physical-safety value when Android cannot obtain a fresh point at SMS-receipt time.

Record a short review video showing: Safety → enable one-slot refresh → prominent disclosure →
accept → Android's `Always allow` setting → disable again. The public privacy policy and the in-app
link must be live before submission.

## Data safety answers

- Location: processed for app functionality; precise or approximate depending on Android/user
  choices; not sold; not used for advertising; not sent to a VeVak server.
- Phone/SMS data: processed locally for app functionality and fraud/abuse prevention; outgoing SMS
  is provided to the selected mobile carrier; no SMS body is retained by VeVak.
- App activity: at most 20 generic request outcomes are stored locally and can be erased in-app.
- Diagnostics: local and redacted; no telemetry collection.
- Optional beaconDB fallback: the third-party service receives the public IP address only after
  explicit opt-in; no phone number, SMS, Wi-Fi identifier or local coordinate is submitted.
- Data deletion: local reset and Android uninstall; no VeVak account or server-side profile exists.
- Encryption in transit: carrier SMS is not end-to-end encrypted; HTTPS is used for optional
  beaconDB access. The store form must not claim that all transmitted data is encrypted.

## Console checklist

1. Enrol in Play App Signing and preserve the upload key securely.
2. Upload the signed `app-play-release.aab` to internal testing first.
3. Complete SMS/Call Log and background-location declaration forms.
4. Publish the privacy-policy page before review.
5. Add phone screenshots captured from the real 0.3.13 Play build; do not use mock screens.
6. Add the French and English listing copy from `fastlane/metadata/android`.
7. Complete content rating, target audience, ads, app access and Data safety forms.
8. Install the Play-delivered build from internal testing and rerun the real SMS/location matrix.

## Remaining external inputs

The repository intentionally does not contain the upload keystore/password, Play Console account,
review video or screenshots. These must come from the owner and a real device.
