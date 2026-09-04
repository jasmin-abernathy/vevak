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

VeVak is an open-source Android application that lets explicitly authorised trusted contacts request location information by SMS for a limited period. The current beta supports up to five locally configured contacts, each with their own phone number, request phrase, finite authorisation and local revocation control.

The phone owner can also initiate a manual one-time share. No VeVak account, advertising, telemetry or mandatory application server is required.

### VeVak 0.3.9: one stable location contract

The location path is built around one rule: **a valid phrase-key request should return the most useful latest information VeVak can legitimately obtain or has already remembered, without requiring permanent location access.**

For a normal authorised automatic request, the current order is:

1. a recent/current Android location when Android can provide one at that moment;
2. a recognised trusted place such as `Home`;
3. a fresh coarse network/IP estimate, only when the owner explicitly enabled that optional fallback;
4. the latest remembered coordinate from any legitimate source, regardless of its age;
5. unavailable only when no usable source has ever provided information.

Every remembered coordinate keeps its age. A network/IP estimate also keeps its source and is always described as an approximate area, never as an exact GPS fix.

### No permanent background location requirement

VeVak does not declare `ACCESS_BACKGROUND_LOCATION` and does not run periodic location tracking.

When the app becomes operational while normal Android location access is available, VeVak can opportunistically acquire and remember a point. A later SMS request can therefore use that remembered point even if the Android Location switch is later turned off or a new fix is unavailable.

This is resilience, not continuous tracking: there is no periodic WorkManager job, repeating alarm or background polling loop for location.

### Two separate last-position memories

To avoid one improvement breaking another, VeVak intentionally keeps two local concepts:

- **latest coordinate from any legitimate source** for automatic phrase-key replies;
- **latest real/local point** for manual sharing and the emergency shortcut.

An optional IP estimate may improve an automatic reply, but it never overwrites the stricter real/local point used by explicit manual and emergency actions. Duress/safety fallback coordinates are excluded from both normal memories.

### Phrase-key matching

Phrase matching is deliberately case-insensitive and locale-independent. Common SMS keyboard transformations are normalised as well, including repeated whitespace, non-breaking spaces and common typographic apostrophes.

For example, a configured `position maintenant` matches `POSITION MAINTENANT`. The sender still has to match an authorised trusted contact and all authorisation/rate-limit checks still apply.

### Reply information chosen by the owner

VeVak keeps the location itself as the core information and respects the configured reply options:

- battery status/level only when enabled;
- accuracy/radius only when enabled;
- map-link provider selected by the owner;
- age always retained when needed so an old point is not represented as current;
- network estimates always labelled as estimates.

For sufficiently precise real coordinates, Android's system geocoder may add an `Approx. address` line to normal and manual shares. Geocoding is best-effort and never blocks the coordinate reply. The emergency shortcut stays compact and does not add reverse-geocoder text.

### Optional network approximation

The FOSS build contains the Android `INTERNET` permission solely because VeVak offers an explicitly opt-in coarse network fallback. It is **off by default**.

When enabled, VeVak may send an IP-only HTTPS geolocation request to the public beaconDB service. VeVak does not submit SSIDs, BSSIDs, Cell IDs, SMS contents, phone numbers, trigger phrases or local coordinates in this request. The remote service necessarily sees the connection's public IP address.

The duress/protection path never calls this online fallback.

### Trusted Wi-Fi

VeVak can associate the current Wi-Fi connection with a local label such as `Home`.

- When Android exposes the SSID, VeVak stores only a SHA-256 fingerprint, not the clear-text network name.
- When stronger local network signals are available, VeVak may derive a local fingerprint without storing the raw values.
- When Android exposes only weak/session-level evidence, VeVak prefers a false negative over guessing that the phone is still at the trusted place.

Trusted-place detection is never consulted for a duress request.

### Safety and abuse-prevention baseline

The free/open-source core includes:

- up to five trusted contacts in the current beta;
- separate finite authorisation for each contact (24 h, 7 days or 30 days);
- immediate local revocation;
- a global anti-tracking quota shared across contacts;
- mandatory local visibility for automatic requests;
- manual one-time sharing initiated and confirmed on the phone;
- an emergency shortcut using only the last real/local point;
- optional duress/safety fallback isolated from real location, trusted Wi-Fi and online approximation;
- minimal local audit outcomes without coordinates, phone numbers, SMS contents, phrases or Wi-Fi identifiers;
- encrypted `.vvk` configuration export/import that never silently restores active authorisations.

See [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) for the non-negotiable anti-abuse boundaries and [`PRIVACY.md`](PRIVACY.md) for the complete data model.

### Variants and CI

The repository contains two Gradle product flavors sharing the same core:

- `foss` — canonical open-source/privacy-first build using Android `LocationManager`, without Google Play Services;
- `play` — Google Fused Location Provider dependency isolated in the Play source set.

Android CI validates static privacy/ecodesign boundaries, FOSS and Play unit tests, debug builds and lint. Successful pushes to `main` publish the validated FOSS debug APK as a GitHub Actions artifact and rolling beta prerelease.

See [`BUILDING.md`](BUILDING.md) and [`docs/location-resolution-0.3.8.md`](docs/location-resolution-0.3.8.md).

### Project status

**Status: active development / beta / real-device validation in progress**

Before a stable release, VeVak still needs broader multi-device/operator testing, dual-SIM validation, accessibility review, security/privacy review, documented failure modes and clear release criteria.

---

## Français

### Qu'est-ce que VeVak ?

VeVak est une application Android libre et respectueuse de la vie privée permettant à des contacts de confiance explicitement autorisés de demander une information de localisation par SMS pendant une durée limitée. La bêta actuelle accepte jusqu'à cinq contacts configurés localement, chacun avec son numéro, sa phrase-clé, sa propre autorisation temporaire et sa révocation locale.

Le propriétaire du téléphone peut aussi déclencher lui-même un partage ponctuel. Aucun compte VeVak, publicité, télémétrie ou serveur applicatif obligatoire n'est nécessaire.

### VeVak 0.3.9 : un contrat de localisation unique

Le fonctionnement repose désormais sur une règle stable : **une demande valide par phrase-clé doit renvoyer la meilleure dernière information que VeVak peut légitimement obtenir ou qu'il a déjà mémorisée, sans exiger une localisation permanente.**

Pour une demande automatique normale et autorisée, l'ordre est :

1. une position Android récente/actuelle si Android peut en fournir une à ce moment ;
2. un lieu de confiance reconnu, par exemple `Maison` ;
3. une estimation réseau/IP fraîche, uniquement si le propriétaire a explicitement activé ce repli facultatif ;
4. la dernière coordonnée mémorisée issue de n'importe quelle source légitime, quel que soit son âge ;
5. « indisponible » uniquement si aucune source exploitable n'a jamais fourni d'information.

Chaque coordonnée mémorisée conserve son ancienneté. Une estimation réseau/IP conserve aussi son origine et reste toujours décrite comme une zone approximative, jamais comme un point GPS exact.

### Pas de localisation permanente obligatoire

VeVak ne déclare plus `ACCESS_BACKGROUND_LOCATION` et ne lance aucun suivi périodique de localisation.

Lorsque l'application entre en fonctionnement alors que l'accès Android normal à la localisation est disponible, VeVak peut obtenir ponctuellement un point et le mémoriser. Une demande SMS ultérieure peut donc réutiliser cette dernière position même si le bouton Localisation Android a été coupé entre-temps ou qu'aucun nouveau point n'est disponible.

Il s'agit d'un mécanisme de résilience, pas d'un suivi continu : aucun job WorkManager périodique, alarme répétitive ou boucle de polling de localisation n'est ajouté.

### Deux mémoires distinctes pour éviter les régressions

VeVak conserve volontairement deux notions séparées :

- **dernière coordonnée issue de toute source légitime** pour les réponses automatiques à la phrase-clé ;
- **dernier point réel/local** pour le partage manuel et le raccourci d'urgence.

Une estimation IP facultative peut donc améliorer une réponse automatique sans écraser le dernier vrai point nécessaire aux actions explicites. Les coordonnées de repli du mode sous contrainte sont exclues de ces deux mémoires normales.

### Phrase-clé

La comparaison est insensible aux majuscules/minuscules et indépendante de la langue du téléphone. VeVak normalise également des transformations courantes des SMS : espaces répétés, espaces insécables et apostrophes typographiques usuelles.

Ainsi, une phrase configurée `position maintenant` reconnaît aussi `POSITION MAINTENANT`. Le numéro expéditeur doit toujours correspondre à un contact autorisé et les contrôles d'autorisation et anti-suivi restent inchangés.

### Informations choisies par l'utilisateur

La localisation reste l'information centrale et VeVak respecte les options de réponse enregistrées :

- état/niveau de batterie seulement si l'option est activée ;
- précision/rayon seulement si l'option est activée ;
- fournisseur du lien cartographique choisi par l'utilisateur ;
- ancienneté conservée lorsque nécessaire pour ne jamais présenter un vieux point comme actuel ;
- estimation réseau toujours identifiée comme telle.

Pour une coordonnée réelle suffisamment précise, le géocodeur système Android peut ajouter une ligne `Adresse approx.` aux réponses normales et aux partages manuels. Cet enrichissement est facultatif et son échec ne bloque jamais les coordonnées ni le SMS. Le raccourci d'urgence reste volontairement compact et n'ajoute pas ce texte d'adresse.

### Estimation réseau facultative

La variante FOSS possède la permission Android `INTERNET` uniquement parce que VeVak propose un repli réseau approximatif explicitement activable. Cette option est **désactivée par défaut**.

Lorsqu'elle est activée, VeVak peut envoyer une requête HTTPS de géolocalisation fondée uniquement sur l'IP au service public beaconDB. VeVak n'envoie dans cette requête ni SSID, ni BSSID, ni Cell ID, ni contenu SMS, ni numéro, ni phrase-clé, ni coordonnée locale. Le service distant voit nécessairement l'adresse IP publique de la connexion.

Le chemin de protection sous contrainte n'appelle jamais ce repli en ligne.

### Wi-Fi de confiance

VeVak peut associer la connexion Wi-Fi courante à un libellé local comme `Maison`.

- Lorsque le SSID est accessible, VeVak n'en conserve qu'une empreinte SHA-256, jamais le nom en clair.
- Lorsque des signaux réseau locaux plus robustes sont disponibles, VeVak peut produire une empreinte locale sans conserver les valeurs brutes.
- Lorsque seules des preuves faibles ou limitées à une session sont disponibles, VeVak préfère un faux négatif plutôt que de deviner que le téléphone est toujours au lieu de confiance.

La détection du lieu de confiance n'est jamais utilisée pour une commande sous contrainte.

### Socle de sécurité et prévention des abus

Le cœur libre inclut notamment :

- jusqu'à cinq contacts de confiance ;
- une autorisation distincte et limitée pour chacun (24 h, 7 jours ou 30 jours) ;
- la révocation locale immédiate ;
- un quota anti-suivi global partagé entre les contacts ;
- une visibilité locale obligatoire pour les demandes automatiques ;
- le partage ponctuel initié et confirmé depuis le téléphone ;
- un raccourci d'urgence fondé uniquement sur le dernier point réel/local ;
- un repli optionnel sous contrainte isolé de la vraie position, du Wi-Fi de confiance et du repli réseau ;
- un historique local minimal sans coordonnées, numéros, texte SMS, phrases ni identifiants Wi-Fi ;
- un export/import `.vvk` chiffré qui ne réactive jamais silencieusement les autorisations.

Voir [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) pour les règles non négociables et [`PRIVACY.md`](PRIVACY.md) pour le modèle complet des données.

### Variantes et CI

Le dépôt contient deux flavors Gradle partageant le même cœur :

- `foss` — variante canonique libre basée sur Android `LocationManager`, sans Google Play Services ;
- `play` — Google Fused Location Provider isolé dans le source set Play.

La CI Android vérifie les frontières statiques de confidentialité et d'écoconception, les tests unitaires FOSS et Play, les builds debug et le lint. Chaque push réussi sur `main` publie l'APK FOSS debug validée comme artefact GitHub Actions et dans la préversion bêta roulante.

Voir [`BUILDING.md`](BUILDING.md) et [`docs/location-resolution-0.3.8.md`](docs/location-resolution-0.3.8.md).

### État du projet

**État : développement actif / bêta / validation sur appareils réels en cours**

Avant une version stable, VeVak nécessite encore davantage de tests multi-appareils et opérateurs, la validation double-SIM, une revue d'accessibilité, des revues sécurité/confidentialité, une documentation des modes d'échec et des critères de publication clairs.
