# Offline cellular fallback research — VeVak 0.3.14

Date: 2026-09-16

## Goal

Evaluate whether VeVak could eventually add a **local-only coarse cellular fallback** between a recognised trusted place and the existing opt-in IP/network estimate, without creating a tracking history or sending cell identifiers to a third party.

No cellular fallback is enabled by this research. The canonical runtime resolver remains unchanged.

Proposed order only if future device tests justify it:

1. Android location;
2. trusted place such as `Maison`;
3. **offline cellular estimate** from an explicitly bundled/downloaded local dataset;
4. existing opt-in IP/network estimate;
5. latest remembered coordinate;
6. unavailable.

The protected-contact path remains separate and must never inspect current cellular/Wi-Fi/location sources.

## What Android gives us

Android's `TelephonyManager.getAllCellInfo()` can expose serving and neighbouring cell records, but:

- it requires `ACCESS_FINE_LOCATION`;
- on Android Q and newer it returns cached cell information and does not force a radio refresh;
- `requestCellInfoUpdate()` can request fresher data, but updates are rate-limited and not guaranteed;
- callers are expected to inspect the `CellInfo` timestamp to understand staleness;
- availability still depends on the device/ROM/radio stack and on Android location state.

Official reference:
- https://developer.android.com/reference/android/telephony/TelephonyManager#getAllCellInfo()
- https://developer.android.com/reference/android/telephony/CellInfo

This is why a real-device ON/OFF experiment is required before adding any lookup engine.

## What an OpenCellID-style local match needs

OpenCellID cell lookups use the full cellular identity, not a Cell ID alone. The relevant dataset fields are:

- radio technology;
- MCC;
- MNC (`net` in downloads);
- LAC/TAC (`area` in downloads);
- cell identity (`cell` in downloads).

The dataset also exposes estimated coordinates, `range`, sample count and `updated` timestamp. These are useful quality signals because a cellular result should be treated as a **zone estimate**, never as GPS accuracy.

OpenCellID supports GSM, UMTS, LTE, NR and CDMA-family data. Country exports and daily changes are available; exports currently contain cells observed in the last 18 months and are generated daily. The France/MCC 208 export is currently roughly 8.5 MB compressed, small enough to make a local proof of concept realistic, but that does **not** mean it should be bundled before device testing and update/licensing design.

Official references:
- https://docs.opencellid.org/docs/downloads/database-format
- https://docs.opencellid.org/docs/downloads/overview
- https://docs.opencellid.org/docs/help/glossary

## Licensing

OpenCellID data is CC BY-SA 4.0. Any shipped or displayed result derived from that dataset needs visible attribution and ShareAlike handling for adapted data.

Official reference:
- https://docs.opencellid.org/docs/attribution

No OpenCellID data is bundled by the current VeVak POC.

## Privacy boundary chosen for the POC

The current experiment is deliberately diagnostic-only:

- no OpenCellID API request;
- no measurement upload;
- no automatic contribution to OpenCellID;
- no raw MCC/MNC/LAC/TAC/Cell ID in the diagnostic report;
- no cellular identity stored in audit/history/backups;
- no new permission;
- no change to `VeVakPositionResolver`.

The diagnostic only reports redacted capability metadata:

- count of visible cell records;
- count of registered/serving records;
- radio technologies seen;
- coarse age of the freshest cached record;
- count of records whose identity fields are complete enough for a future local database match.

A static FOSS boundary should continue to reject direct OpenCellID lookup/upload endpoints in core code. A future attribution link is fine; direct cell-identity transmission is not the design target.

## Device test gate

Before writing a local database lookup, test the exact PR APK and record only the redacted diagnostic values in these states:

1. Android location ON, app foreground;
2. Android location OFF without rebooting;
3. Android location OFF after several minutes;
4. Wi-Fi ON vs mobile data only;
5. screen unlocked vs after screen-off/return;
6. if possible, at least two Android devices / ROMs.

For every state, note:

- `visibleCellRecords`;
- `registeredCellRecords`;
- `offlineCellLookupReadyRecords`;
- technologies;
- freshest cache-age bucket.

### Decision rule

Proceed to a local database proof of concept only if at least one real device still exposes complete, reasonably recent cellular identities in a state where that fallback would actually add resilience over the existing Android/Maison/memory path.

If Android returns zero/very stale/incomplete identities whenever the location switch is OFF, OpenCellID does not solve the important VeVak failure mode and should remain research only.

## If the device test is positive

Next safe step for GPT-6:

1. build an isolated matcher against a **tiny synthetic fixture**, not the full France export;
2. define exact radio normalization and identifier validation, including NR and TD-SCDMA;
3. design a compact local index (for example SQLite or a generated sorted binary index) and measure APK/storage/RAM impact;
4. define dataset freshness/update strategy without turning VeVak into a mandatory network client;
5. design user-visible attribution and CC BY-SA compliance;
6. use OpenCellID `range`, `samples` and `updated` to reject weak/stale matches;
7. keep the result labelled as an approximate cellular zone;
8. add the fallback only behind an explicit product decision and only in the normal canonical resolver, never in the protected-contact path.

Do not add a direct OpenCellID API fallback merely because an API key exists. A local-first lookup is the only direction currently aligned with VeVak's privacy model.
