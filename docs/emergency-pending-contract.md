# Pending emergency contract — approved 2026-09-13

Jasmin explicitly approved retaining a delayed voluntary emergency, showing it as pending and allowing another local activation to cancel until receiver claim. This supersedes the previously open expiry question in the earlier review notes.

## Runtime contract

- IDLE: no valid arm. An activation can start the existing four-second grace.
- CANCEL_WINDOW: arm exists and grace remains. Another activation cancels.
- PENDING_SYSTEM: grace elapsed but receiver has not claimed the arm. No expiry; another activation cancels. Tile stays active, labelled "Urgence en attente" with "Touchez pour annuler".
- Receiver consumption and cancellation share the existing lock. Whichever clears the arm first wins; a stale broadcast cannot consume a replacement id.
- Consumption requires PENDING_SYSTEM. A successful claim means processing began, not SMS sent or delivered.

## Location contract after real-device validation

The emergency receiver now uses the same canonical `VeVakPositionResolver` as an authorised phrase-key request. This replaces the former emergency-only last-real/cache lookup, which produced a misleading "no position" result on a real device while the normal SMS path correctly recognised the trusted place.

Therefore an emergency can resolve, in the same order as the normal authorised SMS path: Android position when available; configured trusted place such as `Maison`; optional network/IP approximation only when the owner enabled it; newest remembered coordinate allowed by the canonical resolver; unavailable only when none can answer.

The emergency SMS remains explicitly labelled `URGENCE VeVak`. A network approximation must still be presented as approximate, never as an exact fix. This change does not make the emergency remotely triggerable and does not add a tracking loop, location history, permission, service or notification.

Manual sharing may keep its separate explicit-confirmation behaviour; the correction here is that the emergency action no longer maintains a second position-resolution policy that can disagree with an authorised SMS.

## Boundaries unchanged

`SharedPreferences.apply()` remains asynchronous. The process-local lock is not durable exactly-once delivery across crashes. A crash after claim can still lose all or some recipient dispatches; no retry was added. Ordinary optional refresh and the allow-while-idle emergency fallback are unchanged.

Known boot mismatch is idle because Android clears the old alarm at reboot. Legacy/unavailable boot markers cannot prove boot identity: elapsed state is retained as pending to avoid silently expiring a valid delayed intention.

## Validation

Pure state tests cover the pending policy. Formatter tests now cover emergency trusted-place and network-approximation presentation. They do not emulate TileService, AlarmManager, persistence failure or SMS delivery.

Device tests required: canonical location parity between keyword and emergency, delayed receiver after four seconds, pending cancellation, queued delivery versus cancellation, shortcut/tile cross-cancellation, stale tile label at claim, process death/recreation, same-boot persisted pending, reboot and legacy markers, permissions/SIM/contacts changed, locked screen, TalkBack and large fonts. Only test with informed contacts. Do not use force-stop as process-death equivalent. PR remains draft until physical validation and explicit merge approval.
