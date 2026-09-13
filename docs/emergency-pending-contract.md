# Pending emergency contract — approved 2026-09-13

Jasmin explicitly approved retaining a delayed voluntary emergency, showing it
as pending and allowing another local activation to cancel until receiver claim.
This supersedes the previously open expiry question in the earlier review notes.

## Runtime contract

- IDLE: no valid arm. An activation can start the existing four-second grace.
- CANCEL_WINDOW: arm exists and grace remains. Another activation cancels.
- PENDING_SYSTEM: grace elapsed but receiver has not claimed the arm. No expiry;
  another activation cancels. Tile stays active, labelled "Urgence en attente"
  with "Touchez pour annuler". Pending cancellation is possible even when the
  current SMS/contact configuration would prevent a fresh arm.
- Receiver consumption and cancellation share the existing lock. Whichever
  clears the arm first wins; a stale broadcast cannot consume a replacement id.
- Consumption now requires PENDING_SYSTEM, rejecting an early delivery and a
  known mismatched boot. A successful claim means processing began, not SMS sent
  or delivered; there is no new success notification or delivery claim.
- A tile still displaying a cancellation action never treats that click as a
  new arm if consumption just occurred. It refreshes and reports no pending
  emergency. A later activation on the refreshed idle tile can arm normally.
- The discrete shortcut uses the same toggle. It stays screenless; its setup
  text explains late cancellation and refers to the optional tile for visibility.

## Boundaries unchanged

SharedPreferences.apply remains asynchronous. This lock is not durable exactly-once
delivery across crashes. A crash after claim can still lose all or some recipient
dispatches; no retry was added. The 500 ms location cache budget, no geocoding,
ordinary optional refresh and allow-while-idle emergency fallback are unchanged.
No version, permission, signing, notification or service changes.

Known boot mismatch is idle because Android clears the old alarm at reboot; this
change does not reschedule emergencies across reboot. Legacy/unavailable boot
markers cannot prove boot identity: elapsed state is retained as pending to avoid
silently expiring a valid delayed intention. Test migration explicitly; a stale
legacy record may require an initial cancellation before a new arm.

## Validation

Pure state tests cover exact grace boundaries, day-late pending without expiry,
absent arm, known boot mismatch, unknown marker and invalid deadline. They do not
emulate TileService, AlarmManager, persistence failure or SMS delivery.

Device tests required: delayed receiver after four seconds, pending cancellation,
queued delivery versus cancellation, shortcut/tile cross-cancellation, stale tile
label at claim, process death/recreation, same-boot persisted pending, reboot and
legacy markers, permissions/SIM/contacts changed, locked screen, TalkBack and large
fonts. Only test with informed contacts. Do not use force-stop as process-death
equivalent. PR remains draft until physical validation and explicit merge approval.
