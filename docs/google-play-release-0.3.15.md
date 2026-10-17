# Google Play candidate — VeVak 0.3.15 (versionCode 18)

The previous signed AAB was made from `main` at version 0.3.14/code 17, before PR #35 landed. A matching versionName is **not** proof of feature parity. Do not upload another AAB with versionCode 17.

## Release gate

1. Complete code review and merge PR #35 after the owner approves it. **Do not launch the signed workflow from the stale main.**
2. Confirm the release commit contains the single `SMS urgence` shortcut without an icon picker, and immediate emergency broadcast dispatch without a four-second timer.
3. Run FOSS and Play unit tests, lint and builds on that same SHA. Validate real-device SMS recipient/permission/SIM failure cases and absence of double sends.
4. Invoke the manual signed `play-release.yml` on `main`, requiring signing secrets, and retrieve the SHA-suffixed AAB + checksum + `build-info.txt`.
5. Compare the `sourceRevision`, `versionCode=18`, `versionName=0.3.15`, and Play package id against the commit reviewed and the bundle inspected, then upload to Play Console internal testing.
6. Install the Google Play-delivered app (not just a FOSS debug APK), verify the current icon setup and emergency behavior, and check the versionCode. The carrier SMS attempt may still take longer due to platform/location operations; no app-imposed four-second wait should remain.

The workflow builds an AAB but never uploads it to Google Play automatically. It is not a store release or an APK handoff.
