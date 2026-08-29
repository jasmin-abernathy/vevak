# VeVak

**Privacy-first location sharing by SMS, without relying on Internet access.**

🇬🇧 English documentation below  
🇫🇷 [Documentation française](#français)

> [!WARNING]
> **VeVak does not contact emergency services and does not replace 112, 911, your phone's built-in emergency features, or any official safety service.**
>
> VeVak is under active development and real-device testing. Do not rely on it as your only means of communication or safety.

---

## English

### What is VeVak?

VeVak is a privacy-first, open-source Android application that lets **explicitly authorised trusted contacts request location information by SMS** for a limited period.

The core works locally on the phone and uses the **mobile SMS network rather than a mandatory cloud service**. The current prototype supports up to five locally configured trusted contacts, each with their own phone number, request phrase, finite authorisation and local revocation control.

The phone owner can also initiate a one-time manual location share.

### Why VeVak?

Many location-sharing tools depend on permanent Internet access, remote accounts and tracking infrastructure. VeVak explores a deliberately smaller model:

- local-first processing;
- SMS-based communication;
- finite, explicit authorisations;
- no advertising or telemetry;
- no mandatory VeVak account or server;
- no continuous location tracking in the current core;
- open-source development;
- data minimisation and functional sobriety.

### How a normal request works

```text
Authorised contact
      │
      │ SMS containing that contact's configured phrase
      ▼
┌────────────────┐
│     VeVak      │
│ Android phone  │
└────────────────┘
      │
      ├─ verifies sender + phrase
      ├─ verifies finite local authorisation
      ├─ enforces request visibility + anti-tracking limits
      ├─ checks trusted-place / local location fallbacks
      ├─ may request one bounded fresh location
      ▼
SMS response
```

### Location resilience in 0.3.1

Android may redact Wi-Fi identifiers and may clear its own last-location cache when the user switches global **Location** off. VeVak 0.3.1 therefore uses a local resilience ladder instead of treating Android's cache as the only fallback.

For a normal authorised request:

1. a positively recognised trusted Wi-Fi place can return a local label such as `Maison` without waking GPS;
2. if Android later redacts the SSID after Location is switched off, that trusted-place result can continue only for the **same already-verified Android Wi-Fi network session and the same device boot**;
3. VeVak compares Android's cached point with its own app-private remembered last real location;
4. when Android location providers are available, VeVak may attempt one bounded current-location lookup;
5. if current acquisition fails and stale fallback is enabled, VeVak may use the freshest remaining cached point;
6. VeVak's own remembered precise location expires after **24 hours**, and its real age remains visible in the reply.

VeVak does **not** silently re-enable Android Location and does not claim that a third-party app can obtain a genuinely new GPS/network fix after the user has disabled the platform's location services.

The remembered position:

- is stored only in the app-private sandbox;
- is never uploaded;
- is excluded from `.vvk` configuration backups;
- is excluded from redacted diagnostics and logs;
- rejects locations Android marks as mocked;
- expires after 24 hours.

### Trusted Wi-Fi and network permissions

The canonical FOSS variant still has **no `INTERNET` permission**.

VeVak does request `ACCESS_NETWORK_STATE`, a read-only Android permission, so it can determine whether the already-active network session is Wi-Fi and preserve continuity of a network that was positively identified before Android redacted its SSID. This permission does not let VeVak open Internet connections.

A Wi-Fi reconnect, network-session replacement or device reboot invalidates that continuity proof. If Android still hides the SSID afterwards, VeVak fails closed rather than guessing that the phone is still at the trusted place.

### Important limitation: SMS is still a network service

The core does not need Internet access, but it still depends on:

- a working mobile network and SMS service;
- Android SMS permissions;
- notification visibility for automatic requests;
- location permissions and available location information;
- manufacturer/background restrictions;
- SIM/eSIM and operator configuration.

VeVak must not be described as “working without any network”, “always locatable”, or “guaranteed in an emergency”.

### Safety and abuse-prevention baseline

The free/open-source core includes:

- up to five trusted contacts in the current prototype;
- separate finite authorisation for each contact (24 h, 7 days or 30 days);
- immediate local revocation;
- a global anti-tracking quota shared across contacts;
- mandatory local visibility for automatic requests;
- manual one-time sharing initiated on the phone;
- optional duress/safety fallback designed so that a duress request never inspects the real/trusted-Wi-Fi/remembered location path;
- minimal local audit outcomes without coordinates, phone numbers, SMS contents, phrases or Wi-Fi identifiers;
- encrypted `.vvk` configuration export/import that never silently restores active authorisations.

See `ABUSE-PREVENTION.md` and `PRIVACY.md` for the non-negotiable boundaries.

### Current real-device testing priorities

P0 validation currently covers:

- real SMS → validation → reply, including screen-off/app-closed use;
- trusted Wi-Fi registered with Location on, then Location switched off while staying on the same Wi-Fi session;
- remembered last position after global Location is switched off;
- Wi-Fi reconnect and reboot fail-closed behaviour;
- multiple senders and per-contact revocation;
- dual-SIM/eSIM behaviour;
- manufacturer battery/background restrictions;
- encrypted backup portability;
- accessibility and privacy-safe diagnostics.

The public P0 regression checklist is tracked in GitHub issue `#17` without posting real numbers, trigger phrases, Wi-Fi identifiers or coordinates.

### Why not add every possible radio-location technique immediately?

Projects such as GPSLogger, NeoStumbler and local network-location backends demonstrate useful GPS/network/passive/Wi-Fi/cellular techniques. VeVak already asks multiple Android providers where appropriate, and local radio-environment learning remains a P1 research direction.

It is **not** part of the 0.3.1 fix because current Android versions still gate Wi-Fi scans and many radio identifiers behind location-related permissions/settings. Adding more radio collection would increase sensitive state and permission complexity without reliably solving the exact “global Location off” failure.

### Variants

The repository contains two Gradle product flavors sharing the same tested core:

- `foss` — canonical open-source/privacy-first build using Android `LocationManager` and no Google Play Services;
- `play` — isolates Google Fused Location Provider dependencies in the Play source set.

The FOSS boundary is enforced in CI. Proprietary Google location APIs must not leak into `main` or `foss`, and `android.permission.INTERNET` remains forbidden in the canonical core.

### Build and CI

The Android CI validates:

- privacy/ecodesign static boundaries;
- FOSS unit tests;
- FOSS debug build + lint;
- Play unit tests;
- Play debug build + lint.

Successful non-PR runs also publish the exact validated FOSS debug APK as a short-lived GitHub Actions artifact for real-device testing.

See `BUILDING.md` for local build instructions.

### Project status

**Status: active development / prototype / real-device validation in progress**

Before a stable release, VeVak still needs broader multi-device/operator testing, dual-SIM validation, background-behaviour evidence, accessibility review, security/privacy review, documented failure modes and clear release criteria.

### Repository structure

```text
.
├── .github/          # CI and contribution templates
├── app/              # Android app + FOSS/Play source sets
├── docs/             # Technical review notes
├── gradle/           # Gradle wrapper/configuration
├── scripts/          # Privacy/ecodesign validation scripts
├── ABUSE-PREVENTION.md
├── BUILDING.md
├── CHANGELOG.md
├── PRIVACY.md
├── ROADMAP.md
├── SECURITY.md
└── LICENSE
```

### Contributing and responsible disclosure

Contributions are especially useful for Android/SMS testing, dual-SIM, accessibility, manufacturer compatibility, privacy/security review, documentation, F-Droid packaging and reproducible builds.

Before contributing, read `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY.md` and `ROADMAP.md`.

Never publish real phone numbers, SMS contents, request phrases, Wi-Fi identifiers or precise coordinates in public issues, screenshots or logs.

### Licence and origin

VeVak is open source under the licence in `LICENSE` and is developed in France by **Le Potager des Apps**.

---

## Français

### Qu'est-ce que VeVak ?

VeVak est une application Android libre et respectueuse de la vie privée permettant à **plusieurs contacts de confiance explicitement autorisés de demander une information de localisation par SMS** pendant une durée limitée.

Le cœur fonctionne localement sur le téléphone et s'appuie sur le **réseau SMS mobile plutôt que sur un service cloud obligatoire**. Le prototype actuel accepte jusqu'à cinq contacts configurés localement, chacun avec son numéro, sa phrase normale, sa propre autorisation temporaire et sa révocation locale.

Le propriétaire du téléphone peut également déclencher lui-même un partage ponctuel de position.

### Pourquoi VeVak ?

Beaucoup d'outils de partage de position reposent sur une connexion Internet permanente, des comptes distants et une infrastructure de suivi. VeVak explore volontairement un modèle plus réduit :

- traitement local d'abord ;
- communication par SMS ;
- autorisations explicites et limitées dans le temps ;
- aucune publicité ni télémétrie ;
- aucun compte ou serveur VeVak obligatoire ;
- aucun suivi continu dans le cœur actuel ;
- développement open source ;
- minimisation des données et sobriété fonctionnelle.

### Fonctionnement d'une demande normale

```text
Contact autorisé
      │
      │ SMS contenant sa phrase configurée
      ▼
┌───────────────┐
│     VeVak     │
│ Téléphone     │
│ Android       │
└───────────────┘
      │
      ├─ vérifie l'expéditeur et la phrase
      ├─ vérifie l'autorisation locale temporaire
      ├─ applique visibilité + limites anti-suivi
      ├─ cherche un lieu de confiance / repli local
      ├─ peut demander une position fraîche bornée
      ▼
Réponse SMS
```

### Résilience de localisation en 0.3.1

Android peut masquer les identifiants Wi-Fi et effacer son propre cache de dernière position lorsque l'utilisateur coupe le bouton global **Localisation**. VeVak 0.3.1 utilise donc plusieurs replis locaux au lieu de dépendre uniquement du cache Android.

Pour une demande normale autorisée :

1. un Wi-Fi de confiance positivement reconnu peut répondre avec un libellé local tel que `Maison` sans réveiller le GPS ;
2. si Android masque ensuite le SSID après la coupure de Localisation, cette reconnaissance peut continuer uniquement pour **la même session réseau Wi-Fi Android déjà vérifiée et le même démarrage du téléphone** ;
3. VeVak compare le cache Android à sa propre dernière position réelle mémorisée localement ;
4. lorsque les fournisseurs Android sont disponibles, VeVak peut tenter une seule recherche actuelle limitée dans le temps ;
5. si la recherche échoue et que le repli ancien est autorisé, VeVak peut utiliser le meilleur point restant ;
6. la position précise mémorisée par VeVak expire après **24 heures** et son âge réel reste indiqué dans la réponse.

VeVak ne tente **pas** de rallumer silencieusement la Localisation Android et ne prétend pas qu'une application tierce peut produire un nouveau point GPS/réseau après désactivation des services de localisation par l'utilisateur.

La position mémorisée :

- reste dans le stockage privé de l'application ;
- n'est jamais envoyée à un serveur ;
- n'est pas incluse dans les sauvegardes `.vvk` ;
- n'apparaît pas dans les diagnostics ou journaux expurgés ;
- refuse les positions signalées comme simulées par Android ;
- expire après 24 heures.

### Wi-Fi de confiance et permissions réseau

La variante FOSS canonique n'a toujours **pas la permission `INTERNET`**.

VeVak utilise désormais `ACCESS_NETWORK_STATE`, une permission Android de lecture de l'état réseau, uniquement pour savoir si la session déjà active est du Wi-Fi et maintenir la continuité d'un réseau qui avait été positivement identifié avant qu'Android n'en masque le SSID. Cette permission ne permet pas à VeVak d'ouvrir une connexion Internet.

Une reconnexion Wi-Fi, un remplacement de session réseau ou un redémarrage invalide cette preuve de continuité. Si Android masque encore le SSID ensuite, VeVak échoue prudemment plutôt que de deviner que le téléphone est toujours à `Maison`.

### Limite importante : le SMS utilise toujours un réseau

Le cœur ne nécessite pas Internet, mais dépend toujours :

- d'un réseau mobile et d'un service SMS fonctionnels ;
- des permissions SMS Android ;
- de notifications visibles pour les demandes automatiques ;
- des permissions et informations de localisation disponibles ;
- des restrictions d'arrière-plan du constructeur ;
- de la configuration SIM/eSIM et opérateur.

VeVak ne doit pas être présenté comme « fonctionnant sans aucun réseau », « toujours localisable » ou « garanti en cas d'urgence ».

### Socle de sécurité et prévention des abus

Le cœur libre inclut notamment :

- jusqu'à cinq contacts de confiance dans le prototype actuel ;
- une autorisation distincte et limitée pour chacun (24 h, 7 jours ou 30 jours) ;
- la révocation locale immédiate ;
- un quota anti-suivi global partagé entre les contacts ;
- une visibilité locale obligatoire pour les demandes automatiques ;
- le partage ponctuel initié depuis le téléphone ;
- un repli optionnel sous contrainte conçu pour ne jamais consulter le vrai Wi-Fi, la vraie position ou la position réelle mémorisée ;
- un historique local minimal sans coordonnées, numéros, texte SMS, phrases ni identifiants Wi-Fi ;
- un export/import `.vvk` chiffré qui ne réactive jamais silencieusement les autorisations.

Voir `ABUSE-PREVENTION.md` et `PRIVACY.md` pour les règles non négociables.

### Tests terrain prioritaires

La validation P0 couvre actuellement :

- vrai SMS → validation → réponse, écran éteint et application fermée compris ;
- Wi-Fi de confiance enregistré avec Localisation active puis Localisation coupée sans changer de session Wi-Fi ;
- dernière position VeVak après coupure de Localisation ;
- reconnexion Wi-Fi et redémarrage avec échec prudent ;
- plusieurs expéditeurs et révocation par contact ;
- double-SIM/eSIM ;
- restrictions batterie/arrière-plan des constructeurs ;
- portabilité de la sauvegarde chiffrée ;
- accessibilité et diagnostics expurgés.

La checklist P0 correspondante est suivie dans l'issue GitHub `#17`, sans y publier de numéro réel, phrase-clé, identifiant Wi-Fi ou coordonnées.

### Pourquoi ne pas ajouter immédiatement toutes les techniques radio possibles ?

Des projets comme GPSLogger, NeoStumbler et des backends de géolocalisation locale montrent des approches utiles combinant GPS, réseau, passif, Wi-Fi ou cellules. VeVak interroge déjà plusieurs fournisseurs Android lorsque c'est pertinent, et l'apprentissage local de l'environnement radio reste une piste P1.

Ce n'est **pas** dans le correctif 0.3.1 car Android continue de conditionner les scans Wi-Fi et beaucoup d'identifiants radio aux permissions/réglages de localisation. Collecter davantage de données sensibles augmenterait la complexité sans résoudre de manière fiable le cas précis « Localisation globale coupée ».

### Variantes

Le dépôt contient deux flavors Gradle partageant le même cœur testé :

- `foss` — variante canonique libre, basée sur `LocationManager`, sans Google Play Services ;
- `play` — dépendances Google Fused Location Provider isolées dans le source set Play.

La frontière FOSS est vérifiée en CI : les API propriétaires Google ne doivent pas fuiter dans `main` ou `foss`, et `android.permission.INTERNET` reste interdite dans le cœur canonique.

### Build et CI

La CI Android vérifie :

- les frontières statiques de confidentialité et d'écoconception ;
- les tests unitaires FOSS ;
- le build debug FOSS et son lint ;
- les tests unitaires Play ;
- le build debug Play et son lint.

Après une exécution réussie hors pull request, la CI publie également l'APK FOSS debug exacte comme artefact GitHub Actions temporaire afin de tester précisément le build validé.

Voir `BUILDING.md` pour les instructions de compilation locale.

### État du projet

**État : développement actif / prototype / validation sur appareils réels en cours**

Avant une version stable, VeVak nécessite encore davantage de tests multi-appareils et opérateurs, la validation double-SIM, des preuves de comportement en arrière-plan, une revue d'accessibilité, des revues sécurité/confidentialité, une documentation des modes d'échec et des critères de publication clairs.

### Structure du dépôt

```text
.
├── .github/          # CI et modèles de contribution
├── app/              # Application Android + source sets FOSS/Play
├── docs/             # Notes de revue technique
├── gradle/           # Wrapper/configuration Gradle
├── scripts/          # Contrôles confidentialité/écoconception
├── ABUSE-PREVENTION.md
├── BUILDING.md
├── CHANGELOG.md
├── PRIVACY.md
├── ROADMAP.md
├── SECURITY.md
└── LICENSE
```

### Contribuer et signaler

Les contributions sont particulièrement utiles pour les tests Android/SMS, le double-SIM, l'accessibilité, la compatibilité constructeur, les revues de confidentialité/sécurité, la documentation, le packaging F-Droid et les builds reproductibles.

Avant de contribuer, consultez `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY.md` et `ROADMAP.md`.

Ne publiez jamais de véritables numéros de téléphone, contenus SMS, phrases de déclenchement, identifiants Wi-Fi ou coordonnées précises dans une issue, une capture ou un journal public.

### Licence et origine

VeVak est open source selon la licence présente dans `LICENSE` et est développé en France par **Le Potager des Apps**.
