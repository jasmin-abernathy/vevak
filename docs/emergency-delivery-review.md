# Emergency delivery review — 2026-09-13

Reviewed baseline: `2f69100230680f78d99742a65a17e676ccd02578`, PR #35.
This review does not establish device-test coverage or SMS delivery guarantees.

## Narrow cleanup implemented

Emergency arm cleanup now looks up its unique PendingIntent with FLAG_NO_CREATE,
cancels the alarm and invalidates the token. A missing token is not recreated just
to cancel it. Creation and lookup share the same explicit intent and unique URI.
The current arm-id check remains necessary for broadcasts already dispatched.
PositionRefreshScheduler already uses the same lookup/cancel pattern.

Android documents token lookup and cancellation in its
[PendingIntent reference](https://developer.android.com/reference/android/app/PendingIntent).
No leak has been measured; this change explicitly ends the token's lifetime.

## Persistence: do not mechanically replace apply with commit

The arm, cancellation and consumption use SharedPreferences.apply. Android updates
memory immediately and writes asynchronously, without reporting disk failures.
Normal component lifecycle transitions wait for pending writes; an abrupt crash
or power loss is a distinct scenario. commit reports persistence success but can
block, including waiting for previous apply operations.
[SharedPreferences.Editor](https://developer.android.com/reference/android/content/SharedPreferences.Editor).

Both the shortcut activity and tile invoke the controller from the main thread.
A safe synchronous-persistence design needs serialized off-main operations,
failure results understood by both callers, cancellation while persistence is in
flight, and a receiver that acquires goAsync before doing blocking persistence.
Changing one word is not enough. Keep a minimal record; do not add a database or
blind SMS retries without a concrete failure contract and tests.

## Dispatch semantics retained

Consumption occurs before asynchronous settings/location/SMS work. This reduces
duplicate attempts in a live process but can lose an alert after consumption.
Persisting after SmsManager instead introduces uncertain duplicate attempts.
The multi-recipient loop can partially complete. No exactly-once claim is valid.
Current policy remains unchanged; neither retry nor expiry was added.

## Newly identified blocking work before SMS

The Play provider's lastKnownLocation suspends on client.lastLocation without an
explicit timeout. VeVakLocationRepository.fetchCachedLocation waits for that
provider before reading the remembered real point, then calls enrich. Thus a
stalled provider can prevent even the local fallback from being read.

SystemReverseGeocoder.resolveLegacy uses a synchronous Geocoder call inside
withContext(IO). The surrounding coroutine timeout does not itself prove that a
blocking platform call terminates within the requested interval. EmergencyShareReceiver
has already consumed the arm at this stage. These are code-level risks, not
observed failures on a phone.

Recommended next change: a dedicated emergency cache-only lookup, reading the
remembered real point independently, bounding the asynchronous Play cache lookup,
and avoiding reverse geocoding before urgent dispatch. Preserve source/age and
never substitute an IP estimate or start a new position acquisition. Verify
formatter behaviour without an address. Test with a provider that never completes,
a remembered real point, no remembered point, and cancelled parent coroutine.
Do not wrap the entire repository call in a timeout that discards the remembered
point or assumes blocking legacy geocoding has been interrupted.

## Product decisions still open

After the four-second grace period, an arm can remain pending while the tile
looks idle. Ask whether a delayed emergency should remain eligible or expire;
do not choose an arbitrary expiry. An honest pending state must follow that choice.

Both position refresh and emergency use allow-while-idle alarms. Prefer reviewing
whether optional refresh may wait for maintenance windows, but do not silently
change its freshness contract. Cancelling refresh at emergency-arm time cannot
undo quota already consumed. Standard alarms would not guarantee prompt emergency
delivery either. Test under actual idle with a recent refresh.

## Validation still needed

- Run Android build/test/lint and secret scan for the resulting commit.
- Device: arm/cancel/rearm and stale queued broadcast; after cancellation the
  old token must not be usable and a new arm must remain independent.
- Device: process death, reboot, Doze and recently executed refresh.
- Device: timeout/permission/SIM/contact failures and partial recipient send.
- Keep both UI explanations: one activation prepares, another within four seconds
  cancels; this is not a rapid double tap to send. TalkBack's activation gesture
  is distinct from two VeVak activations.

PR stays draft. No version, signing, permissions, expiration or retry changes.
