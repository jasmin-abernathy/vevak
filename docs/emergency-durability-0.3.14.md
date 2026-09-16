# Emergency durability and remaining validation

## Implemented

- arm/cancel/claim are serialized under the process lock, on Dispatchers.IO. Critical writes use commit inside a non-cancellable section; closing UI cannot interrupt a transition halfway through.
- Register the one-shot inexact alarm before persisting a new arm. An uncommitted stale alarm cannot claim its id. No exact alarm, foreground service or continuous tracking.
- Persist removal before cancelling alarm tokens, or before any SMS attempt. A disk failure or exception blocks further mutations/claims in that process because SharedPreferences memory may already differ from disk. Do not report successful cancellation after failure.
- Both receivers acquire goAsync before durable work and finish in finally.
- A pending launcher activation is ignored. Cancellation uses explicit tile/notification controls.

## Crash/multiple recipients decision

At most one claim per arm, no automatic replay after claim. A crash between durable claim and SmsManager can lose the whole dispatch; a crash between recipients can leave a partial dispatch. Keep this compromise rather than retrying all recipients and risking duplicate emergency messages. Successful SmsManager calls are only requests handed to Android, never proof of delivery. No recipient journal or sensitive history is introduced.

## Corrupt private password verifier decision

Keep fail-closed behavior: an existing malformed verifier must never become an unconfigured password. Do not add a phone-PIN fallback for an existing local password: someone knowing the phone PIN could bypass it. No automatic recovery is implemented or promised. First-time setup still requires Android credentials. Android app-data deletion/uninstall remains outside the in-app lock.

## Device checks still required

- One launcher tap, repeated taps during the grace window and system delay: no cancellation or deadline reset.
- Explicit tile/notification cancellation; stale alarm after cancellation; competing job/alarm deliveries.
- Process death before/after claim, disk failure, multiple recipients, Doze, reboot (old-boot arms are rejected).
- Private-password creation/change/relock; abandoned private draft; backup/import/reset authorization.
- Real launcher icon, dark mode and enlarged fonts.

Automated tests cover failure poisoning of the persistence guard and existing phase/boot policies. They do not simulate Android disk crashes or validate OEM alarm delivery.
