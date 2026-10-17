# VeVak

**Privacy-first, local-first location sharing by SMS. Internet is optional and never required for the core SMS workflow.**

🇫🇷 Français ci-dessous  
🇬🇧 [English](#english)

> [!WARNING]
> VeVak ne contacte pas les services de secours et ne remplace pas le 112/911 ni les fonctions d'urgence natives du téléphone. Le projet est encore en bêta et doit être validé sur davantage d'appareils réels.

## VeVak 0.3.14 — candidate de validation et de publication Play

VeVak permet à des contacts explicitement autorisés de demander une information de localisation par SMS pendant une durée limitée. Jusqu'à cinq contacts peuvent être configurés localement, chacun avec son numéro, sa phrase-clé, son autorisation finie et sa révocation locale.

Aucun compte VeVak, publicité, télémétrie ou serveur applicatif obligatoire n'est nécessaire.

### Phrase-clé dans un SMS normal

La phrase-clé est insensible à la casse et peut apparaître **au milieu d'un message plus long**. La comparaison se fait par suite de mots plutôt que par sous-chaîne typographique exacte : les variations courantes de ponctuation, d'apostrophe, de tiret et d'espacement ne bloquent plus le déclenchement.

Exemple : la clé `position maintenant` reconnaît aussi `Salut, position maintenant s'il te plaît`.

Le numéro expéditeur doit toujours être celui d'un contact autorisé et les limites anti-suivi restent appliquées.

### Resolver canonique partagé

Pour une demande normale et autorisée, un partage manuel ou une urgence locale, VeVak utilise le même contrat de résolution :

1. une position Android récente/actuelle si Android peut en fournir une ;
2. un lieu de confiance reconnu comme `Maison` ;
3. une estimation réseau/IP fraîche uniquement si l'utilisateur l'a activée ;
4. la dernière coordonnée mémorisée issue d'une source légitime, quel que soit son âge ;
5. `position indisponible` seulement si aucune source exploitable n'a jamais fourni d'information.

Chaque coordonnée conserve son ancienneté. Une estimation réseau reste explicitement présentée comme approximative. Les coordonnées du lieu de repli de la protection ciblée restent séparées de ce resolver et ne sont utilisées que pour le contact concerné.

### Mémoire périodique optionnelle, sans trajet

L'utilisateur peut activer un rafraîchissement best-effort de **la seule dernière position** : 15, 30 ou 60 minutes, avec 30 minutes par défaut.

Chaque nouveau point remplace le précédent. VeVak ne conserve aucun trajet, historique ou breadcrumb. Android/Doze peut retarder les tentatives.

VeVak ne demande jamais la localisation en arrière-plan pour son fonctionnement SMS normal. Cette autorisation Android devient facultative uniquement si le propriétaire active la mise à jour périodique d'un seul point. VeVak n'utilise ni service de localisation permanent, ni alarme répétitive exacte, ni boucle WorkManager périodique. Une option distincte peut reprogrammer la mémoire après le redémarrage du téléphone.

### Notifications : demandes silencieuses, retour d'urgence facultatif

Les demandes SMS automatiques restent silencieuses : aucune notification à chaque demande reçue et aucune notification permanente `VeVak actif`.

`POST_NOTIFICATIONS` est déclaré uniquement pour permettre le **retour d'urgence facultatif** choisi par le propriétaire. Le silence reste le défaut ; l'utilisateur peut aussi choisir une vibration courte ou une notification temporaire sans son. Refuser les notifications ne bloque jamais une réponse SMS ni l'urgence elle-même.

La notification d'urgence éventuelle ne contient ni position, ni destinataire, ni contenu SMS. Elle ne prouve jamais la livraison et ne déclenche aucun retry automatique.

### Paramètres supplémentaires protégés

Des options personnelles facultatives sont disponibles dans « Paramètres supplémentaires », derrière un mot de passe local. Leur état n'est pas affiché dans l'accueil ni dans le diagnostic standard.

Lors de la première création du mot de passe, VeVak demande une confirmation du verrouillage Android. Le mot de passe lui-même n'est jamais stocké : seul un vérificateur PBKDF2 salé reste dans le stockage privé de l'application et n'entre pas dans les sauvegardes.

Une fois défini, ce mot de passe est également demandé avant l'export, la restauration ou la réinitialisation de la configuration depuis VeVak.

L'historique local expurgé reste limité à vingt résultats génériques datés et ne contient ni coordonnées, ni numéro, ni phrase-clé, ni indication qu'un lieu de repli a été utilisé.

### Protection ciblée par contact

L'utilisateur peut sélectionner le contact dont il craint un usage abusif de la phrase-clé. Ce contact continue à envoyer **sa phrase habituelle**.

Pour ce contact seulement, VeVak utilise exclusivement un lieu de repli préenregistré et n'inspecte jamais la vraie position, le Wi-Fi Maison ou l'estimation réseau. Les autres contacts continuent à utiliser le resolver canonique.

Cette fonction est désactivée par défaut, n'est jamais proposée automatiquement après un nombre de SMS et se configure uniquement à la demande dans les paramètres supplémentaires protégés.

Les anciennes sauvegardes contenant la seconde phrase de protection des premières bêtas restent compatibles pour migration.

### Urgence locale et raccourci SMS

L'urgence est désactivée par défaut. Pendant l'onboarding ou dans l'écran Sécurité, l'utilisateur choisit explicitement les destinataires ; aucun contact n'est précoché.

L'urgence utilise le resolver canonique partagé. Elle n'est pas soumise au quota anti-suivi des demandes distantes et ne peut pas être déclenchée par un SMS distant.

VeVak peut demander à Android d'épingler un raccourci **SMS urgence**, avec une bulle de message Streamline. Une tuile Réglages rapides facultative peut également être ajoutée. Retirez les anciennes icônes génériques du launcher et ajoutez le raccourci SMS pour actualiser leur apparence.

Un appui sur le raccourci **SMS urgence** arme l’envoi pendant **4 secondes**. Les appuis répétés sont ignorés. Annulez explicitement depuis la tuile « Annuler » / « Urgence en attente » ou la notification facultative, jusqu’à la prise en charge. Une alarme système inexacte sert de repli. Armement, annulation et claim sont écrits durablement hors UI. Aucun retry automatique après claim : un crash peut empêcher certains envois et la remise à Android ne prouve pas la livraison.

Selon le choix local, cette action reste silencieuse, vibre brièvement ou affiche une notification temporaire sans son. Aucun de ces retours ne constitue une preuve de livraison du SMS.

### Paramètres restreints Android

Pour les APK installées manuellement, l'écran d'autorisations explique le parcours avant d'ouvrir les paramètres Android : menu `⋮` → `Autoriser les paramètres restreints` lorsque l'option existe, puis retour simple dans VeVak.

Au retour, l'application relit les autorisations et avance automatiquement lorsque les accès nécessaires sont accordés. Les notifications ne font partie des accès nécessaires que si l'utilisateur choisit explicitement le retour d'urgence par notification.

### Réponses et confidentialité

La réponse peut inclure, selon les choix du propriétaire :

- batterie ;
- précision/rayon ;
- lien cartographique ;
- ancienneté du point ;
- `Adresse approx.` pour certaines coordonnées réelles, via le géocodeur système Android.

L'estimation réseau est désactivée par défaut. Si elle est activée, VeVak peut interroger beaconDB via l'adresse IP ; aucune phrase, numéro, SMS, SSID/BSSID ou coordonnée locale n'est envoyé dans cette requête.

### Quota anti-suivi

En production, les réponses automatiques sont limitées globalement à :

- au moins **15 minutes** entre deux réponses ;
- **4 réponses maximum sur 24 heures** pour l'ensemble des contacts.

L'urgence locale est volontairement séparée de ce quota.

### Sauvegarde chiffrée

La configuration peut être exportée dans un fichier `.vvk` chiffré/authentifié. Les positions mémorisées et l'historique des demandes ne sont jamais exportés. Après restauration, tous les contacts sont révoqués et doivent être réautorisés localement.

Si un mot de passe de paramètres supplémentaires a été défini, il doit aussi être confirmé avant un export, une restauration ou une réinitialisation. Ce mot de passe de contrôle local est distinct du mot de passe de chiffrement du fichier `.vvk`.

### Variantes et CI

Deux flavors Gradle partagent le même cœur :

- `foss` — variante canonique libre basée sur Android `LocationManager`, sans Google Play Services ;
- `play` — Google Fused Location Provider isolé dans le source set Play.

La CI vérifie les frontières de confidentialité/écoconception, les tests unitaires FOSS et Play, les builds debug et le lint. Les pushes réussis sur `main` publient l'APK FOSS debug validée dans la bêta roulante.

Documentation détaillée : [`PRIVACY.md`](PRIVACY.md), [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md), [`docs/final-hardening-0.3.11.md`](docs/final-hardening-0.3.11.md) et [`BUILDING.md`](BUILDING.md).

---

## English

VeVak is an open-source Android application for limited, explicitly authorised location requests by SMS. No VeVak account, advertising, telemetry or mandatory application server is required.

### 0.3.14 highlights

- A configured phrase may appear inside a longer SMS; matching remains case-insensitive and typography-normalised.
- Normal requests, manual sharing and local emergency use the same canonical resolver: Android location → trusted place → opt-in network/IP estimate → latest remembered coordinate → unavailable.
- Automatic SMS replies remain notification-free. `POST_NOTIFICATIONS` exists only for the owner's optional temporary emergency-feedback notification; refusing it never blocks SMS replies or emergency dispatch.
- The owner may opt into a single-slot last-position refresh target of 15/30/60 minutes (30 by default), with optional re-scheduling after boot. Optional background-location access is requested only for this off-screen refresh; it is never required for SMS replies. There is no route/history.
- Optional additional settings are protected by a local password. The password itself is never stored; only a salted PBKDF2 verifier remains in app-private storage. Once configured, it is also required before configuration export, restore or reset.
- Protection is targeted at a selected trusted contact while keeping that contact's existing phrase. The protected path only uses the pre-recorded fallback and never consults real location, trusted Wi-Fi or network approximation. It is disabled by default and is never auto-suggested after SMS activity.
- Emergency recipients are selected locally in advance. A generic pinned home-screen shortcut and optional Quick Settings tile can arm the emergency SMS for four seconds; a second activation cancels it. Android may delay dispatch; cancellation remains possible until receiver claim. Delivery is never claimed.
- Emergency feedback is optional: silence by default, or a short vibration / temporary silent notification if explicitly selected.
- Emergency is unconfigured by default and preselects no recipient during onboarding.
- The sideload/restricted-settings flow explains Android's `Allow restricted settings` step before opening app settings and rechecks permissions automatically on return.

Production automatic replies keep a device-wide minimum interval of 15 minutes and a maximum of four replies per 24 hours.

The canonical `foss` flavor contains no Google Play Services; the optional `play` flavor isolates Google Fused Location Provider in its own source set.

See [`PRIVACY.md`](PRIVACY.md), [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md), [`docs/final-hardening-0.3.11.md`](docs/final-hardening-0.3.11.md) and [`BUILDING.md`](BUILDING.md).

**Status: beta / real-device validation still required before a stable public release.**
