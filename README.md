# VeVak

**Privacy-first location sharing by SMS, without relying on Internet access.**

🇬🇧 English documentation below  
🇫🇷 [Documentation française](#français)

> [!WARNING]
> **VeVak does not contact emergency services and does not replace 112, 911, your phone's built-in emergency features, or any official safety service.**
>
> VeVak is currently under active development and real-world testing. Do not rely on it as your only means of communication or safety.

---

## English

### What is VeVak?

VeVak is a privacy-first, open-source Android application designed to let a **trusted contact request your approximate location by SMS** when an Internet connection is unavailable, unreliable, or intentionally disabled.

The core feature is designed to work locally on the device and to rely on the **mobile SMS network rather than a cloud service**.

A user explicitly chooses:

- the trusted phone number allowed to make a request;
- the exact request phrase;
- the permissions granted to the application.

When VeVak receives a valid SMS request from the authorised contact, it attempts to obtain a recent or current location and sends the result back by SMS.

### Why VeVak?

Many location-sharing tools depend on:

- mobile data or Wi-Fi;
- cloud accounts;
- proprietary services;
- continuous background connections;
- remote tracking infrastructure.

VeVak explores a different approach:

- **local-first processing**;
- **SMS-based communication**;
- **explicitly authorised contact**;
- **no advertising**;
- **no tracking**;
- **no mandatory cloud account**;
- **data minimisation**;
- **open-source development**;
- **digital sustainability and functional sobriety**.

### Important limitation: SMS is still a network service

VeVak does **not** require Internet access for its core location-request feature, but it still depends on:

- an available mobile network;
- working SMS service;
- Android allowing the application to receive and send SMS;
- location availability;
- the permissions granted by the user;
- device and manufacturer background restrictions;
- the user's mobile plan and operator configuration.

For this reason, VeVak should **not** be described as “working without a network” or as “always available”.

### Emergency services

VeVak is **not an emergency service**.

In an emergency, use the official emergency number available in your country and the emergency features built into your phone.

In the European Union, **112** is the common emergency number.

VeVak may help a trusted contact receive a location in some situations, but:

- it does not contact emergency services;
- it does not guarantee that a location can be obtained;
- it does not guarantee that an SMS can be received or sent;
- it must not be your only safety mechanism.

### How it works

```text
Trusted contact
      │
      │ SMS containing the configured request phrase
      ▼
┌───────────────┐
│     VeVak     │
│ Android phone │
└───────────────┘
      │
      ├─ verifies the sender
      ├─ verifies the request phrase
      ├─ applies request rate limits
      ├─ checks for a recent location
      ├─ may request a fresh location
      ▼
SMS response to the trusted contact
```

The intended strategy is:

1. use a sufficiently recent cached location when appropriate;
2. otherwise attempt a bounded one-time location request;
3. if no usable location is available, return an explicit failure result rather than pretending that a location was found.

### Privacy and security principles

VeVak is designed around the following principles:

- **one explicitly authorised contact**;
- **an exact configurable request phrase**;
- **local processing**;
- **minimal persistence**;
- **no analytics or advertising SDKs**;
- **no direct logging of sensitive information**;
- **no automatic cloud backup of sensitive application data**;
- **rate limiting** for repeated requests;
- **clear disclosure of permissions and limitations**;
- **diagnostic information must be redacted**.

Sensitive information such as:

- phone numbers;
- SMS contents;
- request phrases;
- precise coordinates;

must not be included in public bug reports, screenshots, logs, or GitHub issues.

### Phone readiness

Reliable behaviour depends heavily on the phone and mobile network.

Before using VeVak, users should verify that:

- Android is up to date;
- ordinary SMS sending and receiving works;
- the SIM/eSIM is active;
- 4G/5G and VoLTE are correctly configured when relevant;
- location services are enabled when VeVak is expected to obtain a location;
- VeVak has the required permissions;
- manufacturer battery restrictions do not prevent expected background behaviour.

VeVak includes or plans to include guided readiness checks, but these checks **cannot certify that the phone will work in every real-world situation**.

### Current testing priorities

The project is currently testing and documenting behaviour across:

- screen-on and screen-off conditions;
- mobile data disabled;
- Wi-Fi disabled;
- 4G and 5G networks;
- VoLTE configurations;
- different mobile operators;
- single-SIM and dual-SIM phones;
- Android background restrictions;
- battery optimisation modes;
- recent cached location vs fresh location;
- location disabled or unavailable;
- different Android versions;
- accessibility with TalkBack and large text;
- privacy-safe diagnostics.

### Android background constraints

SMS reception is event-driven on Android.

VeVak must keep its SMS processing bounded and avoid long-running work inside a `BroadcastReceiver`. Location acquisition is therefore intentionally time-limited.

Behaviour must be tested on real devices because Android versions and manufacturer-specific battery policies can affect background execution.

### Variants

The project separates a **FOSS-oriented variant** from variants that may depend on proprietary platform components.

The FOSS variant is intended to remain the canonical privacy-first version and should avoid unnecessary network dependencies.

### Distribution

The preferred distribution path is currently:

1. source code on GitHub;
2. closed testing;
3. public pre-release builds;
4. F-Droid-compatible distribution when the project meets the required quality and packaging criteria.

Google Play distribution requires additional review because permissions related to SMS are heavily restricted by Play policy. The project will not weaken its privacy or safety model merely to satisfy a store distribution requirement.

### Project status

**Status: active development / prototype**

VeVak is not yet a stable safety product.

Before a stable release, the project requires:

- successful reproducible builds;
- multi-device testing;
- multi-operator testing;
- dual-SIM validation;
- Android background-behaviour testing;
- accessibility review;
- security review;
- documented failure modes;
- privacy review;
- clear release criteria.

### Repository structure

Typical project structure:

```text
.
├── .github/          # Issue templates, workflows and project automation
├── app/              # Android application
├── brand/            # Brand and visual assets
├── docs/             # Technical and user documentation
├── github-planning/  # Roadmap and planning resources
├── gradle/           # Gradle configuration
├── scripts/          # Validation and maintenance scripts
├── README.md
├── ROADMAP.md
├── SECURITY.md
├── PRIVACY.md
└── LICENSE
```

### Contributing

Contributions are welcome, especially for:

- Android testing;
- SMS behaviour;
- dual-SIM support;
- accessibility;
- privacy and security review;
- device-manufacturer compatibility;
- technical documentation;
- localisation;
- F-Droid packaging;
- reproducible builds.

Before contributing, please read:

- `CONTRIBUTING.md`
- `SECURITY.md`
- `PRIVACY.md`
- `ROADMAP.md`

When reporting a bug, **never include real phone numbers, SMS contents, request phrases, or coordinates**.

### Responsible communication

Please avoid claims such as:

- “always locatable”;
- “works without any network”;
- “guaranteed in an emergency”;
- “replaces emergency services”;
- “anti-violence protection app”.

Preferred wording:

> VeVak can respond to an authorised SMS location request when SMS delivery, Android permissions, device background behaviour and location availability allow it.

### Licence

VeVak is open source.

See the `LICENSE` file in this repository for the applicable licence terms.

### Project origin

VeVak is an open-source project developed in France by **Le Potager des Apps**.

Documentation is maintained in both English and French.

---

## Français

### Qu'est-ce que VeVak ?

VeVak est une application Android libre et respectueuse de la vie privée, conçue pour permettre à **un contact de confiance de demander votre position approximative par SMS**, notamment lorsqu'une connexion Internet est indisponible, instable ou volontairement désactivée.

La fonction principale est conçue pour fonctionner localement sur le téléphone et s'appuyer sur le **réseau SMS mobile plutôt que sur un service cloud**.

L'utilisateur choisit explicitement :

- le numéro de téléphone autorisé à effectuer une demande ;
- la phrase exacte déclenchant la demande ;
- les autorisations accordées à l'application.

Lorsqu'un SMS valide est reçu depuis le contact autorisé, VeVak tente d'obtenir une position récente ou actuelle puis renvoie le résultat par SMS.

### Pourquoi VeVak ?

De nombreux outils de partage de position reposent sur :

- les données mobiles ou le Wi-Fi ;
- un compte cloud ;
- des services propriétaires ;
- des connexions permanentes en arrière-plan ;
- des infrastructures de suivi distantes.

VeVak explore une autre approche :

- **traitement local d'abord** ;
- **communication par SMS** ;
- **contact explicitement autorisé** ;
- **aucune publicité** ;
- **aucun suivi publicitaire** ;
- **aucun compte cloud obligatoire** ;
- **minimisation des données** ;
- **développement open source** ;
- **sobriété fonctionnelle et numérique**.

### Limite importante : le SMS utilise toujours un réseau

VeVak ne nécessite **pas de connexion Internet** pour sa fonction principale de demande de position, mais dépend toujours :

- d'un réseau mobile disponible ;
- du fonctionnement des SMS ;
- de la possibilité pour Android de recevoir et d'envoyer les SMS ;
- de la disponibilité de la localisation ;
- des autorisations accordées par l'utilisateur ;
- des restrictions d'arrière-plan du téléphone ;
- du forfait et de la configuration de l'opérateur.

VeVak ne doit donc pas être présenté comme une application « fonctionnant sans réseau » ou « toujours disponible ».

### Services d'urgence

VeVak **n'est pas un service d'urgence**.

En cas d'urgence, utilisez le numéro d'urgence officiel disponible dans votre pays ainsi que les fonctions d'urgence intégrées au téléphone.

Dans l'Union européenne, **le 112** est le numéro d'urgence commun.

VeVak peut, dans certaines situations, aider un contact de confiance à recevoir une position, mais :

- l'application ne contacte pas les secours ;
- elle ne garantit pas qu'une position pourra être obtenue ;
- elle ne garantit pas qu'un SMS pourra être reçu ou envoyé ;
- elle ne doit jamais constituer l'unique dispositif de sécurité d'une personne.

### Fonctionnement

```text
Contact de confiance
      │
      │ SMS contenant la phrase configurée
      ▼
┌───────────────┐
│     VeVak     │
│ Téléphone     │
│ Android       │
└───────────────┘
      │
      ├─ vérification de l'expéditeur
      ├─ vérification de la phrase
      ├─ limitation des demandes
      ├─ recherche d'une position récente
      ├─ éventuellement recherche d'une nouvelle position
      ▼
Réponse SMS au contact de confiance
```

La stratégie prévue consiste à :

1. utiliser une position récente en cache lorsqu'elle est suffisamment pertinente ;
2. sinon tenter une demande de localisation ponctuelle et limitée dans le temps ;
3. si aucune position exploitable n'est disponible, renvoyer un échec explicite plutôt que de laisser croire qu'une position a été trouvée.

### Principes de confidentialité et de sécurité

VeVak est conçu autour des principes suivants :

- **un contact explicitement autorisé** ;
- **une phrase exacte configurable** ;
- **traitement local** ;
- **stockage minimal** ;
- **aucun SDK publicitaire ou analytique** ;
- **aucun journal direct contenant des données sensibles** ;
- **pas de sauvegarde cloud automatique des données sensibles de l'application** ;
- **limitation de fréquence des demandes** ;
- **présentation claire des autorisations et limites** ;
- **diagnostics expurgés**.

Les informations sensibles telles que :

- les numéros de téléphone ;
- le contenu des SMS ;
- la phrase de déclenchement ;
- les coordonnées précises ;

ne doivent jamais apparaître dans des rapports de bugs publics, captures d'écran, journaux ou issues GitHub.

### Préparation du téléphone

Le comportement réel dépend fortement du téléphone et du réseau mobile.

Avant d'utiliser VeVak, il est recommandé de vérifier que :

- Android est à jour ;
- l'envoi et la réception de SMS ordinaires fonctionnent ;
- la SIM ou eSIM est active ;
- la 4G/5G et la VoLTE sont correctement configurées lorsque cela est pertinent ;
- les services de localisation sont activés lorsqu'une position doit être obtenue ;
- VeVak dispose des autorisations nécessaires ;
- les restrictions de batterie du constructeur n'empêchent pas le fonctionnement attendu en arrière-plan.

VeVak inclut ou prévoit des vérifications guidées, mais celles-ci **ne peuvent pas certifier que le téléphone fonctionnera dans toutes les situations réelles**.

### Tests prioritaires

Le projet teste actuellement notamment :

- l'écran allumé et éteint ;
- les données mobiles désactivées ;
- le Wi-Fi désactivé ;
- les réseaux 4G et 5G ;
- différentes configurations VoLTE ;
- plusieurs opérateurs mobiles ;
- les téléphones simple SIM et double SIM ;
- les restrictions Android en arrière-plan ;
- les modes d'optimisation de batterie ;
- la position récente en cache et la nouvelle localisation ;
- la localisation désactivée ou indisponible ;
- différentes versions d'Android ;
- l'accessibilité avec TalkBack et l'agrandissement du texte ;
- les diagnostics respectueux de la vie privée.

### Contraintes Android en arrière-plan

La réception de SMS est déclenchée par événement sous Android.

VeVak doit maintenir un traitement SMS limité dans le temps et éviter les travaux longs à l'intérieur d'un `BroadcastReceiver`. La recherche de localisation est donc volontairement bornée.

Le fonctionnement doit être testé sur de vrais appareils, car les versions d'Android et les politiques de batterie propres aux constructeurs peuvent modifier le comportement en arrière-plan.

### Variantes

Le projet sépare une **variante orientée FOSS** des variantes pouvant éventuellement dépendre de composants propriétaires.

La variante FOSS est destinée à rester la version canonique respectueuse de la vie privée et doit éviter les dépendances réseau inutiles.

### Distribution

Le parcours de distribution privilégié est actuellement :

1. code source sur GitHub ;
2. tests fermés ;
3. préversions publiques ;
4. distribution compatible F-Droid lorsque le projet répondra aux critères nécessaires.

Une éventuelle distribution sur Google Play nécessite une étude supplémentaire, les autorisations liées aux SMS étant fortement encadrées par les règles du Play Store. Le projet ne modifiera pas son modèle de confidentialité ou de sécurité uniquement pour répondre aux contraintes d'une boutique.

### État du projet

**État : développement actif / prototype**

VeVak n'est pas encore un produit de sécurité stable.

Avant une version stable, le projet nécessite notamment :

- des builds reproductibles réussis ;
- des tests multi-appareils ;
- des tests avec plusieurs opérateurs ;
- une validation du double SIM ;
- des tests du comportement Android en arrière-plan ;
- un audit d'accessibilité ;
- une revue de sécurité ;
- une documentation des modes d'échec ;
- une revue de confidentialité ;
- des critères de publication clairement définis.

### Structure du dépôt

```text
.
├── .github/          # Modèles d'issues, workflows et automatisations
├── app/              # Application Android
├── brand/            # Identité visuelle
├── docs/             # Documentation technique et utilisateur
├── github-planning/  # Roadmap et ressources de pilotage
├── gradle/           # Configuration Gradle
├── scripts/          # Scripts de validation et maintenance
├── README.md
├── ROADMAP.md
├── SECURITY.md
├── PRIVACY.md
└── LICENSE
```

### Contribuer

Les contributions sont particulièrement bienvenues sur :

- les tests Android ;
- le comportement SMS ;
- le support double SIM ;
- l'accessibilité ;
- les revues de confidentialité et sécurité ;
- la compatibilité avec les différents constructeurs ;
- la documentation technique ;
- les traductions ;
- le packaging F-Droid ;
- les builds reproductibles.

Avant de contribuer, consultez :

- `CONTRIBUTING.md`
- `SECURITY.md`
- `PRIVACY.md`
- `ROADMAP.md`

Lors d'un signalement, **ne publiez jamais de véritable numéro de téléphone, contenu de SMS, phrase de déclenchement ou coordonnées géographiques**.

### Communication responsable

Évitez les formulations telles que :

- « toujours localisable » ;
- « fonctionne sans aucun réseau » ;
- « garanti en cas d'urgence » ;
- « remplace les secours » ;
- « application de protection anti-violences ».

Préférez :

> VeVak peut répondre à une demande de localisation autorisée par SMS lorsque l'acheminement du SMS, les autorisations Android, le fonctionnement en arrière-plan du téléphone et la disponibilité de la localisation le permettent.

### Licence

VeVak est un projet open source.

Consultez le fichier `LICENSE` du dépôt pour connaître les conditions de licence applicables.

### Origine du projet

VeVak est un projet open source développé en France par **Le Potager des Apps**.

La documentation est maintenue en anglais et en français.
