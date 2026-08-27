# Changelog

## Unreleased - product/repository sync (2026-08-27)

Documentation and planning have been synchronised with the VeVak discussions held after the v4 ecodesign kit.

### Clarified

- the FOSS build remains canonical;
- Android variants stay in the single `jasmin-abernathy/vevak` repository using Gradle product flavors (`foss` and `play`) rather than being duplicated into separate repositories;
- a dedicated custom repository will only be considered if a concrete long-lived integration cannot be isolated cleanly in the current project;
- P0 reliability work now explicitly includes the complete guided SMS → validation → location → reply test, actionable manufacturer/battery diagnostics and deterministic dual-SIM/eSIM behaviour;
- an outgoing manual SOS is a post-stabilisation extension, not a current core feature;
- selected device-recovery ideas inspired by Find Hub / Find Device are an exploratory module, not an implemented feature;
- no mandatory central VeVak server, covert tracking or permanent location is introduced by this planning update.

### Not implemented by this entry

This changelog entry documents product direction only. It does **not** claim implementation of SOS, remote ringing, remote lock-screen messaging, encrypted relay, remote locking or periodic tracking.

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
- documentation explicite de l'absence de serveur et de WorkManager dans le
  chemin critique.
