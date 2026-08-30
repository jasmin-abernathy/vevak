# VeVak

**Privacy-first, local-first location sharing by SMS. Internet is optional and never required for the core SMS workflow.**

🇬🇧 English documentation below  
🇫🇷 [Documentation française](#français)

> [!WARNING]
> **VeVak does not contact emergency services and does not replace 112, 911, your phone's built-in emergency features, or any official safety service.**
>
> VeVak is under active development and real-device testing. Do not rely on it as your only means of communication or safety.

---

## English

### What is VeVak?

VeVak is a privacy-first, open-source Android application that lets **explicitly authorised trusted contacts request location information by SMS** for a limited period. The current prototype supports up to five locally configured contacts, each with their own phone number, request phrase, finite authorisation and local revocation control.

The owner can also initiate a one-time manual share. No VeVak account, advertising, telemetry or mandatory application server is required.

### VeVak 0.3.3: one multi-source resolver

The 0.3.3 beta fixes an important representation and architecture problem: Android's global **Location OFF** state no longer means “VeVak has no location information”. It only means Android cannot generate a new precise GPS/fused/network point for a normal third-party app.

Normal automatic requests, manual sharing and the built-in location test now use the same resolver, in this order:

1. **recognised trusted place** such as `Maison`, without requesting GPS;
2. **recent Android/cache/VeVak-local location**;
3. **optional coarse network approximation via public IP**, only when explicitly enabled by the phone owner;
4. **older local location**, only when stale fallback is enabled;
5. **unavailable** when no usable source remains.

A network approximation is always labelled as approximate and **never presented as GPS**.

### What happens when Android Location is OFF?

VeVak does not attempt to silently re-enable Android Location. Current Android versions also protect Wi-Fi scans and cellular identifiers that could otherwise be used to reconstruct a precise position, so VeVak does not claim to bypass this platform boundary.

With Location OFF, VeVak may still be able to answer from:

- an already-recognised trusted Wi-Fi place;
- Android's remaining cached location data;
- VeVak's own last real location, retained locally for at most **24 hours**;
- the optional coarse network/IP fallback, if the owner explicitly enabled it.

The home screen now reports these states separately instead of displaying a blanket “Location disabled” failure.

### Optional network approximation

The canonical FOSS build now contains the Android `INTERNET` permission because 0.3.3 adds an **explicitly opt-in**, last-resort coarse network fallback.

It is **OFF by default**. When enabled and local recent sources cannot provide a result, VeVak may send an IP-only HTTPS geolocation request to the public beaconDB service. VeVak does not submit SSIDs, BSSIDs, Cell IDs, SMS contents, phone numbers, trigger phrases or local coordinates in this request. The remote service necessarily sees the connection's public IP address.

The resulting SMS explicitly says that the position is an **approximate network/IP estimate, not a GPS position**, and includes the reported uncertainty when available.

The duress/protection path never calls this online fallback.

See [`PRIVACY.md`](PRIVACY.md) and [`docs/location-resolution-0.3.3.md`](docs/location-resolution-0.3.3.md) for the complete privacy and resolution model.

### Trusted Wi-Fi

VeVak can associate the current Wi-Fi connection with a local label such as `Maison`.

- When Android exposes the SSID, VeVak stores only a SHA-256 fingerprint, not the clear-text network name.
- When Android hides the SSID because Location is off, VeVak can use only the exact current Android network session for the same device boot.
- A reconnect or reboot invalidates this session-only proof rather than guessing that the phone is still at home.

### Privacy-safe ON/OFF location laboratory

0.3.3 adds a real-device diagnostic designed to compare what Android exposes with global Location **ON** and **OFF**. It shows only counts and booleans:

- known and active Android location providers;
- number of providers with a cached fix;
- number of cellular records Android exposes;
- whether the current Wi-Fi identity is readable or redacted;
- active transport type.

It never displays or logs Cell IDs, BSSIDs, SSIDs, coordinates, phone numbers or SMS contents.

Recommended test: record the diagnostic with Location ON, switch Location OFF, return to VeVak, then run **Test all sources** again. This gives device-specific evidence rather than assuming every Android manufacturer behaves identically.

### Safety and abuse-prevention baseline

The free/open-source core includes:

- up to five trusted contacts in the current prototype;
- separate finite authorisation for each contact (24 h, 7 days or 30 days);
- immediate local revocation;
- a global anti-tracking quota shared across contacts;
- mandatory local visibility for automatic requests;
- manual one-time sharing initiated on the phone;
- optional duress/safety fallback isolated from real location, trusted Wi-Fi and online approximation;
- minimal local audit outcomes without coordinates, phone numbers, SMS contents, phrases or Wi-Fi identifiers;
- encrypted `.vvk` configuration export/import that never silently restores active authorisations.

See [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) for the non-negotiable anti-abuse boundaries.

### Why not triangulate 4G/5G towers when Location is OFF?

The modem knows its serving and neighbouring cells, but Android treats the identifiers required for cellular geolocation as location-sensitive information. On current Android versions, a normal third-party app cannot rely on obtaining the necessary Cell IDs while the global Location setting is disabled. Wi-Fi scanning has similar platform restrictions.

VeVak keeps cellular/Wi-Fi visibility in the diagnostic lab so real devices can be measured, but it does not pretend these APIs are a universal bypass.

A future collaborative BLE relay network, conceptually closer to Find Hub / SmartThings Find, is documented as a later research direction rather than being mixed into 0.3.3.

### Variants

The repository contains two Gradle product flavors sharing the same core:

- `foss` — canonical open-source/privacy-first build using Android `LocationManager`, without Google Play Services;
- `play` — Google Fused Location Provider dependency isolated in the Play source set.

The FOSS boundary is checked in CI. Proprietary Google location APIs must not leak into `main` or `foss`. The static privacy check also verifies that the network approximation remains **disabled by default**, gated by the explicit setting and IP-only.

### Build and CI

Android CI validates:

- privacy/ecodesign static boundaries;
- FOSS unit tests;
- FOSS debug build + lint;
- Play unit tests;
- Play debug build + lint.

Successful pushes to `main` publish the exact validated FOSS debug APK as both a GitHub Actions artifact and the rolling `beta` GitHub prerelease, with a SHA-256 checksum.

See [`BUILDING.md`](BUILDING.md) for local build instructions.

### Project status

**Status: active development / prototype / real-device validation in progress**

Before a stable release, VeVak still needs broader multi-device/operator testing, dual-SIM validation, background-behaviour evidence, accessibility review, security/privacy review, documented failure modes and clear release criteria.

### Licence and origin

VeVak is open source under the licence in [`LICENSE`](LICENSE) and is developed in France by **Le Potager des Apps**.

---

## Français

### Qu'est-ce que VeVak ?

VeVak est une application Android libre et respectueuse de la vie privée permettant à **des contacts de confiance explicitement autorisés de demander une information de localisation par SMS** pendant une durée limitée. Le prototype actuel accepte jusqu'à cinq contacts configurés localement, chacun avec son numéro, sa phrase normale, sa propre autorisation temporaire et sa révocation locale.

Le propriétaire du téléphone peut aussi déclencher lui-même un partage ponctuel. Aucun compte VeVak, publicité, télémétrie ou serveur applicatif obligatoire n'est nécessaire.

### VeVak 0.3.3 : un seul moteur de résolution multi-source

La bêta 0.3.3 corrige un problème important d'architecture et de représentation : le fait que le bouton Android **Localisation soit OFF** ne signifie plus « VeVak ne dispose d'aucune information ». Cela signifie seulement qu'Android ne peut pas produire un nouveau point GPS/fused/réseau précis pour une application tierce ordinaire.

Les demandes SMS normales, le partage manuel et le test intégré utilisent désormais le même moteur, dans cet ordre :

1. **lieu de confiance reconnu** tel que `Maison`, sans demander le GPS ;
2. **position Android/cache/mémoire locale VeVak récente** ;
3. **estimation réseau approximative via l'adresse IP**, uniquement si le propriétaire l'a explicitement activée ;
4. **ancienne position locale**, uniquement si le repli ancien est autorisé ;
5. **indisponible** lorsqu'aucune source exploitable ne reste.

Une estimation réseau est toujours indiquée comme approximative et **n'est jamais présentée comme une position GPS**.

### Que se passe-t-il lorsque la Localisation Android est coupée ?

VeVak ne tente pas de rallumer silencieusement la Localisation Android. Les versions actuelles d'Android protègent également les scans Wi-Fi et les identifiants cellulaires qui permettraient de reconstruire une position précise : VeVak ne prétend donc pas contourner cette limite système.

Avec Localisation OFF, VeVak peut encore répondre grâce à :

- un lieu Wi-Fi de confiance déjà reconnu ;
- des caches Android encore disponibles ;
- la dernière position réelle mémorisée par VeVak pendant au maximum **24 heures** ;
- le repli réseau/IP approximatif, si le propriétaire l'a volontairement activé.

L'accueil distingue désormais ces états au lieu d'afficher systématiquement un échec « Localisation désactivée ».

### Estimation réseau facultative

La variante FOSS canonique possède désormais la permission Android `INTERNET` car la 0.3.3 introduit un **repli réseau approximatif, facultatif et explicitement activable**.

Cette option est **désactivée par défaut**. Lorsqu'elle est activée et qu'aucune source locale récente ne suffit, VeVak peut envoyer une requête HTTPS de géolocalisation fondée uniquement sur l'IP au service public beaconDB. VeVak n'envoie dans cette requête ni SSID, ni BSSID, ni Cell ID, ni contenu SMS, ni numéro, ni phrase-clé, ni coordonnée locale. Le service distant voit nécessairement l'adresse IP publique de la connexion.

Le SMS obtenu indique explicitement qu'il s'agit d'une **estimation réseau/IP et non d'une position GPS**, avec l'incertitude fournie lorsqu'elle est disponible.

Le chemin de protection sous contrainte n'appelle jamais ce repli en ligne.

Voir [`PRIVACY.md`](PRIVACY.md) et [`docs/location-resolution-0.3.3.md`](docs/location-resolution-0.3.3.md) pour les détails.

### Wi-Fi de confiance

VeVak peut associer la connexion Wi-Fi courante à un libellé local comme `Maison`.

- Lorsque le SSID est accessible, VeVak n'en conserve qu'une empreinte SHA-256, jamais le nom en clair.
- Lorsque Android masque le SSID parce que Localisation est coupée, VeVak ne peut faire confiance qu'à la session réseau Android exacte et au même démarrage du téléphone.
- Une reconnexion ou un redémarrage invalide cette preuve limitée plutôt que de deviner que le téléphone est toujours à Maison.

### Laboratoire de localisation ON/OFF sans données sensibles

La 0.3.3 ajoute un diagnostic permettant de comparer ce qu'Android expose avec la Localisation globale **ON** puis **OFF**. Il n'affiche que des compteurs et booléens :

- fournisseurs Android connus et actifs ;
- nombre de fournisseurs disposant d'un cache ;
- nombre d'enregistrements cellulaires rendus visibles par Android ;
- identité Wi-Fi lisible ou masquée ;
- type de connexion active.

Il n'affiche et ne journalise jamais les Cell IDs, BSSID, SSID, coordonnées, numéros ou contenus SMS.

Test conseillé : relever le diagnostic Localisation ON, couper la Localisation Android, revenir dans VeVak puis relancer **Tester toutes les sources**. On mesure ainsi le comportement réel du modèle de téléphone au lieu de supposer que tous les constructeurs exposent exactement les mêmes données.

### Socle de sécurité et prévention des abus

Le cœur libre inclut notamment :

- jusqu'à cinq contacts de confiance ;
- une autorisation distincte et limitée pour chacun (24 h, 7 jours ou 30 jours) ;
- la révocation locale immédiate ;
- un quota anti-suivi global partagé entre les contacts ;
- une visibilité locale obligatoire pour les demandes automatiques ;
- le partage ponctuel initié depuis le téléphone ;
- un repli optionnel sous contrainte isolé de la vraie position, du Wi-Fi de confiance et du repli réseau ;
- un historique local minimal sans coordonnées, numéros, texte SMS, phrases ni identifiants Wi-Fi ;
- un export/import `.vvk` chiffré qui ne réactive jamais silencieusement les autorisations.

Voir [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) pour les règles non négociables.

### Pourquoi ne pas trianguler les antennes 4G/5G lorsque Localisation est OFF ?

Le modem connaît les cellules radio auxquelles il est connecté, mais Android considère les identifiants nécessaires à une géolocalisation cellulaire comme des données liées à la localisation. Sur les versions Android actuelles, une application tierce normale ne peut pas compter sur l'accès aux Cell IDs nécessaires lorsque la Localisation globale est désactivée. Les scans Wi-Fi sont soumis à des restrictions comparables.

VeVak conserve ces capacités dans le laboratoire afin de mesurer ce qu'un appareil réel expose, mais ne les présente pas comme un contournement universel.

Un futur réseau collaboratif BLE, conceptuellement plus proche de Find Hub / SmartThings Find, reste une piste de recherche ultérieure et n'est pas mélangé à la 0.3.3.

### Variantes

Le dépôt contient deux flavors Gradle partageant le même cœur :

- `foss` — variante canonique libre basée sur Android `LocationManager`, sans Google Play Services ;
- `play` — Google Fused Location Provider isolé dans le source set Play.

La frontière FOSS est vérifiée en CI. Les API propriétaires Google ne doivent pas fuiter dans `main` ou `foss`. Le contrôle statique vérifie également que l'estimation réseau reste **désactivée par défaut**, derrière l'option explicite et limitée à une requête IP-only.

### Build et CI

La CI Android vérifie :

- les frontières statiques de confidentialité et d'écoconception ;
- les tests unitaires FOSS ;
- le build debug FOSS et son lint ;
- les tests unitaires Play ;
- le build debug Play et son lint.

Chaque push réussi sur `main` publie l'APK FOSS debug validée comme artefact GitHub Actions et dans la préversion GitHub roulante `beta`, accompagnée de son SHA-256.

Voir [`BUILDING.md`](BUILDING.md) pour les instructions de compilation locale.

### État du projet

**État : développement actif / prototype / validation sur appareils réels en cours**

Avant une version stable, VeVak nécessite encore davantage de tests multi-appareils et opérateurs, la validation double-SIM, des preuves de comportement en arrière-plan, une revue d'accessibilité, des revues sécurité/confidentialité, une documentation des modes d'échec et des critères de publication clairs.

### Licence et origine

VeVak est open source selon la licence présente dans [`LICENSE`](LICENSE) et est développé en France par **Le Potager des Apps**.
