# Changelog

## Unreleased - trusted place, discreet alerts and manual sharing (2026-08-28)

### Added

- optional trusted-place shortcut using the currently connected Wi-Fi network;
- only a SHA-256 fingerprint of the Wi-Fi SSID is stored locally, never the network name in clear text;
- normal requests can reply with a local label such as `Maison` without waking GPS when the trusted Wi-Fi matches;
- trusted-place detection is never consulted for a duress request;
- temporary discreet mode for 1 h, 8 h or 24 h, capped by the remaining authorisation period;
- dedicated low-importance request-notification channel for discreet mode, silent and without vibration by default;
- home-screen controls to register/remove the trusted Wi-Fi, rename its label, enable/disable discreet mode and see its expiry;
- manual outgoing position share from the home screen, requiring explicit confirmation before location acquisition and SMS sending;
- manual share uses the configured trusted contact and only Android's default SMS SIM; if no default exists, the action is blocked rather than choosing a SIM arbitrarily;
- manual share never creates its own Android notification, including while temporary discreet mode is active; success/failure stays in the VeVak UI;
- manual-share result wording distinguishes an SMS accepted by Android for sending from confirmed delivery;
- unit coverage for finite discreet mode, deterministic SSID hashing and the manual-share SMS format.

### Safety constraints

- discreet mode does **not** create a covert automatic-request mode: request notifications remain visible in Android and the ongoing `VeVak est actif` notification remains present;
- disabling notifications globally or disabling the active request channel still blocks automatic replies;
- manual sharing is a foreground action initiated and confirmed locally by the phone owner, so it does not depend on request-notification visibility;
- no manual share is sent when a position cannot be obtained;
- manual sharing does not call emergency services, does not run periodically and cannot be triggered remotely;
- the trusted Wi-Fi shortcut is a convenience/privacy optimisation, not proof of physical presence;
- if Android cannot identify the current Wi-Fi network, VeVak falls back to the normal location path.

## Unreleased - abuse-prevention safeguards (2026-08-28)

### Added

- explicit, time-limited local authorisation (24 h, 7 days or 30 days) with no permanent option;
- immediate local revocation from the home screen;
- request visibility as a hard safety boundary: automatic location replies are blocked when VeVak cannot post request notifications;
- ongoing active-status notification identifying the authorised contact and expiry;
- hard anti-tracking limits of at least 15 minutes between automatic replies and at most four replies per 24-hour window;
- minimal local request audit (maximum 20 generic outcomes, no coordinates, SMS text, phone number, phrase or duress marker);
- optional safety-fallback / duress phrase with a pre-recorded fallback location;
- fail-safe duress routing: a duress command never calls the real location repository, and phrase collisions prefer the safety path;
- `ABUSE-PREVENTION.md` documenting coercive-control threat assumptions and non-negotiable product invariants;
- unit tests for authorisation expiry, rate limits, phrase separation and fail-safe request routing.

### Changed

- legacy installs with no explicit authorisation timestamps are paused after upgrade and require local re-authorisation;
- legacy request intervals below 15 minutes are clamped to the anti-tracking floor;
- Android 13+ notification permission is now requested because request visibility is required for automatic replies;
- privacy/security documentation no longer assumes that every outgoing SMS is necessarily inserted into the user's messaging-app history.

## Unreleased - product/repository sync (2026-08-27)

Documentation and planning have been synchronised with the VeVak discussions held after the v4 ecodesign kit.

### Clarified

- the FOSS build remains canonical;
- Android variants stay in the single `jasmin-abernathy/vevak` repository using Gradle product flavors (`foss` and `play`) rather than being duplicated into separate repositories;
- a dedicated custom repository will only be considered if a concrete long-lived integration cannot be isolated cleanly in the current project;
- P0 reliability work now explicitly includes the complete guided SMS → validation → location → reply test, actionable manufacturer/battery diagnostics and deterministic dual-SIM/eSIM behaviour;
- an outgoing manual SOS was initially planned as a post-stabilisation extension and is now implemented as an experimental manual position-share prototype pending real-device validation;
- selected device-recovery ideas inspired by Find Hub / Find Device are an exploratory module, not an implemented feature;
- no mandatory central VeVak server, covert tracking or permanent location is introduced by this planning update.

### Not implemented by this entry

This older planning entry did **not** itself implement SOS, remote ringing, remote lock-screen messaging, encrypted relay, remote locking or periodic tracking. The later 2026-08-28 entry above documents the subsequently implemented manual position-share prototype.

## 0.3.0 - kit v4 d'écoconception et contribution

### Conservé du kit v3

- moteur SMS ;
- contrôle du contact et de la phrase ;
- limitation des demandes ;
- cache puis position unique limitée à 8 secondes ;
- variante FOSS canonique ;
- variante Play facultative ;
- aucune permission Internet ;
- aucune publicité, télémétrie ou pisteur ;
- métadonnées F-Droid.

### Ajouté dans le kit v4

- roadmap alignée sur l'état réel du moteur ;
- budgets d'écoconception lisibles par machine ;
- protocole de mesure ;
- modèle de revue de fonctionnalité ;
- vérification automatisée des limites ;
- mesure de taille APK ;
- formulaires GitHub sobres et orientés preuve ;
- 18 issues initiales de stabilisation et de mesure ;
- scripts de création des labels, milestones et issues ;
- CI limitée aux changements techniques et annulant les exécutions obsolètes ;
- documentation explicite de l'absence de serveur et de WorkManager dans le chemin critique.