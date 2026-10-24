# Deliberate beta release trigger

Current requested beta: `0.3.15` (`versionCode 18`) — FOSS build for real-device tests and F-Droid readiness, including the current SMS/location privacy, safety and permission behaviour.

This trigger records the explicit request to provide the FOSS APK corresponding to the currently validated source.

Updating this file on `main` deliberately launches the full FOSS + Play validation workflow. The
rolling GitHub release `beta` is replaced only after every test, build, lint, privacy and ecodesign
check succeeds.

The validated FOSS APK is distributed as a GitHub Release asset with the stable filename
`VeVak-beta.apk`. The public website links directly to that asset from its home page; the legacy
private `/test/` page is no longer part of the normal distribution flow.
