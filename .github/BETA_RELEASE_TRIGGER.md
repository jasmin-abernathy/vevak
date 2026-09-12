# Deliberate beta release trigger

Current requested beta: `0.3.14` (`versionCode 17`) — improved permission onboarding, compact-screen
and large-text layout fixes, tightened security checks and the current Google Play hand-off candidate.

Updating this file on `main` deliberately launches the full FOSS + Play validation workflow. The
rolling GitHub release `beta` is replaced only after every test, build, lint, privacy and ecodesign
check succeeds.

The validated FOSS APK is distributed as a GitHub Release asset with the stable filename
`VeVak-beta.apk`. The public website links directly to that asset from its home page; the legacy
private `/test/` page is no longer part of the normal distribution flow.
