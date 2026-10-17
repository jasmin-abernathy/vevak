# Emergency durability and remaining validation

## Implemented

- arm/cancel/claim are serialized under the process lock, on Dispatchers.IO. Critical writes use commit inside a non-cancellable section; closing UI cannot interrupt a transition halfway through.
- Register the one-shot inexact alarm before persisting a new arm. An uncommitted stale alarm cannot claim its id. No exact alarm, foreground service or continuous tracking.
- Persist removal before cancelling alarm tokens, or before any SMS attempt. A disk failure or exception blocks further mutations/claims in that process because SharedPreferences memory may already differ from disk. Do not report successful cancellation after failure.
- Both receivers acquire goAsync before durable work and finish in finally.
- One launcher/tile activation requests an explicit emergency broadcast immediately after durable intent persistence, with no intentional delay or countdown. An inexact allow-while-idle alarm at the same instant remains a process-death fallback. Android may still defer its own processing.
- Repeated shortcut activation cannot duplicate a pending send. Cancellation is only opportunistic before receiver claim, through the tile or optional notification.

## Crash/multiple recipients decision

At most one claim per arm, no automatic replay after claim. A crash between durable claim and SmsManager can lose the whole dispatch; a crash between recipients can leave a partial dispatch. Keep this compromise rather than retrying all recipients and risking duplicate emergency messages. Successful SmsManager calls are only requests handed to Android, never proof of delivery. No recipient journal or sensitive history is introduced.

## Corrupt private password verifier decision

Keep fail-closed behavior: an existing malformed verifier must never become an unconfigured password. Do not add a phone-PIN fallback for an existing local password: someone knowing the phone PIN could bypass it. No automatic recovery is implemented or promised. First-time setup still requires Android credentials. Android app-data deletion/uninstall remains outside the in-app lock.

## Device checks still required

- One launcher tap requests an immediate dispatch, without a four-second wait. Repeated taps before claim never duplicate or extend it.
- Explicit tile/notification cancellation; stale alarm after cancellation; competing job/alarm deliveries.
- Process death before/after claim, disk failure, multiple recipients, Doze, reboot (old-boot arms are rejected).
- Private-password creation/change/relock; abandoned private draft; backup/import/reset authorization.
- Real launcher icon, dark mode and enlarged fonts.

Automated tests cover failure poisoning of the persistence guard and existing phase/boot policies. They do not simulate Android disk crashes or validate OEM alarm delivery.
