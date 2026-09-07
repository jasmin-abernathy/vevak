# CI / GitHub Actions budget policy

These rules apply to automated work in this repository.

- Work in batches: edit many, validate once, build once.
- Do not trigger a heavy build after every small commit.
- README, documentation and this policy should not trigger CI.
- APK/ZIP/package artifacts should be built manually or on deliberate release tags unless continuous packaging is explicitly required.
- Keep lightweight syntax, security and deployment-safety checks automatic for relevant code paths.
- Use path filters, timeouts, caches and `concurrency` with `cancel-in-progress: true` for CI when safe.
- Do not cancel a production deployment mid-run unless the deployment is explicitly designed to be safely interruptible.
- Group related automated edits into as few pushes as practical.
- Prefer self-hosted runners for heavy builds when one is available.
