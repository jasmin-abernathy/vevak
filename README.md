# VeVak

**Privacy-first, local-first location sharing by SMS. Internet is optional and never required for the core SMS workflow.**

🇫🇷 Français ci-dessous  
🇬🇧 [English](#english)

> [!WARNING]
> VeVak ne contacte pas les services de secours et ne remplace pas le 112/911 ni les fonctions d'urgence natives du téléphone. Le projet est encore en bêta et doit être validé sur davantage d'appareils réels.

## VeVak 0.3.13 — candidate de validation et de publication Play

VeVak permet à des contacts explicitement autorisés de demander une information de localisation par SMS pendant une durée limitée. Jusqu'à cinq contacts peuvent être configurés localement, chacun avec son numéro, sa phrase-clé, son autorisation finie et sa révocation locale.

Aucun compte VeVak, publicité, télémétrie ou serveur applicatif obligatoire n'est nécessaire.

### Phrase-clé dans un SMS normal

La phrase-clé est insensible à la casse et peut apparaître **au milieu d'un message plus long**. La 0.3.12 compare une suite de mots plutôt qu'une sous-chaîne typographique exacte : les variations courantes de ponctuation, d'apostrophe, de tiret et d'espacement ne bloquent plus le déclenchement.

Exemple : la clé `position maintenant` reconnaît aussi `Salut, position maintenant s'il te plaît`.

Le numéro expéditeur doit toujours être celui d'un contact autorisé et les limites anti-suivi restent appliquées.

### Contrat de résolution normal

Pour une demande normale et autorisée, VeVak cherche dans cet ordre :

1. une position Android récente/actuelle si Android peut en fournir une ;
2. un lieu de confiance reconnu comme `Maison` ;
3. une estimation réseau/IP fraîche uniquement si l'utilisateur l'a activée ;
4. la dernière coordonnée mémorisée issue de toute source légitime, quel que soit son âge ;
5. `position indisponible` seulement si aucune source exploitable n'a jamais fourni d'information.

Chaque position conserve son ancienneté. Une estimation réseau reste explicitement présentée comme approximative.

### Deux mémoires de position séparées

VeVak garde volontairement :

- une **dernière coordonnée toute-source** pour les réponses automatiques ;
- un **dernier point réel/local** pour le partage manuel et l'urgence.

Une estimation IP peut améliorer une réponse automatique mais ne remplace jamais le dernier point réel utilisé par les actions explicites. Les coordonnées du lieu de repli de la protection sont exclues de ces mémoires.

### Mémoire périodique optionnelle, sans trajet

L'utilisateur peut activer un rafraîchissement best-effort de **la seule dernière position** : 15, 30 ou 60 minutes, avec 30 minutes par défaut.

Chaque nouveau point remplace le précédent. VeVak ne conserve aucun trajet, historique ou breadcrumb. Android/Doze peut retarder les tentatives.

VeVak ne demande jamais la localisation en arrière-plan pour son fonctionnement SMS normal. Cette autorisation Android devient facultative uniquement si le propriétaire active la mise à jour périodique d'un seul point. VeVak n'utilise ni service de localisation permanent, ni alarme répétitive exacte, ni boucle WorkManager périodique. Une option distincte peut reprogrammer la mémoire après le redémarrage du téléphone.

### Pas de notifications pour les demandes

Depuis 0.3.11 :

- aucune notification à chaque demande reçue ;
- aucune notification permanente `VeVak actif` ;
- `POST_NOTIFICATIONS` n'est plus déclaré ;
- refuser les notifications ne bloque jamais une réponse SMS.

Après deux SMS normaux valides du même contact, VeVak peut proposer la protection lors d'une prochaine ouverture volontaire de l'application, sans notification déclenchée par la demande elle-même.

Le compteur est séparé pour chaque contact : deux personnes ayant envoyé un message chacune ne
déclenchent jamais cette proposition. L'historique local expurgé est désormais accessible dans un
onglet dédié, reste limité à vingt résultats génériques datés et contient un test guidé du parcours
SMS réel.

### Protection ciblée par contact

L'utilisateur peut sélectionner le contact dont il craint un usage abusif de la phrase-clé. Ce contact continue à envoyer **sa phrase habituelle**.

Pour ce contact seulement, VeVak utilise exclusivement un lieu de repli préenregistré et n'inspecte jamais la vraie position, le Wi-Fi Maison ou l'estimation réseau. Les autres contacts continuent à utiliser le resolver normal.

Les anciennes sauvegardes contenant la seconde phrase de protection des premières bêtas restent compatibles pour migration.

### Urgence locale et raccourci discret

L'urgence est désactivée par défaut. Pendant l'onboarding ou dans l'écran Sécurité, l'utilisateur
choisit explicitement les destinataires ; aucun contact n'est précoché.

L'urgence utilise uniquement le dernier point réel/local et son ancienneté, sans estimation réseau/IP ni adresse géocodée. Elle n'est pas soumise au quota anti-suivi des demandes distantes.

VeVak peut demander à Android d'épingler un raccourci d'écran d'accueil avec un nom et une icône génériques/originaux (`Notes`, `Liste`, `Horaires`, `Dossier`, `Outils`, `Mémos`). Cela ne masque ni ne renomme l'application VeVak elle-même.

Premier appui : envoi armé pendant **4 secondes**. Second appui pendant ce délai : annulation. Sans second appui : le SMS d'urgence part vers les destinataires prédéfinis. Une alarme système ponctuelle sert de repli si Android arrête le processus pendant ce délai ; le raccourci reste silencieux.

### Paramètres restreints Android

Pour les APK installées manuellement, l'écran d'autorisations explique désormais le parcours avant d'ouvrir les paramètres Android : menu `⋮` → `Autoriser les paramètres restreints` lorsque l'option existe, puis retour simple dans VeVak.

Au retour, l'application relit les autorisations et avance automatiquement lorsque les accès nécessaires sont accordés. Les notifications ne font pas partie de ces accès nécessaires.

### Réponses et confidentialité

La réponse peut inclure, selon les choix du propriétaire :

- batterie ;
- précision/rayon ;
- lien cartographique (Google Maps proposé en premier, avec autres choix disponibles) ;
- ancienneté du point ;
- `Adresse approx.` pour certaines coordonnées réelles, via le géocodeur système Android.

L'estimation réseau est désactivée par défaut. Si elle est activée, VeVak peut interroger beaconDB via l'adresse IP ; aucune phrase, numéro, SMS, SSID/BSSID ou coordonnée locale n'est envoyée dans cette requête.

### Quota anti-suivi

En production, les réponses automatiques sont limitées globalement à :

- au moins **15 minutes** entre deux réponses ;
- **4 réponses maximum sur 24 heures** pour l'ensemble des contacts.

L'urgence locale est volontairement séparée de ce quota.

### Sauvegarde chiffrée

La configuration peut être exportée dans un fichier `.vvk` chiffré/authentifié. Les positions mémorisées et l'historique des demandes ne sont jamais exportés. Après restauration, tous les contacts sont révoqués et doivent être réautorisés localement.

### Variantes et CI

Deux flavors Gradle partagent le même cœur :

- `foss` — variante canonique libre basée sur Android `LocationManager`, sans Google Play Services ;
- `play` — Google Fused Location Provider isolé dans le source set Play.

La CI vérifie les frontières de confidentialité/écoconception, les tests unitaires FOSS et Play, les builds debug et le lint. Les pushes réussis sur `main` publient l'APK FOSS debug validée dans la bêta roulante.

Documentation détaillée : [`PRIVACY.md`](PRIVACY.md), [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md), [`docs/final-hardening-0.3.11.md`](docs/final-hardening-0.3.11.md) et [`BUILDING.md`](BUILDING.md).

---

## English

VeVak is an open-source Android application for limited, explicitly authorised location requests by SMS. No VeVak account, advertising, telemetry or mandatory application server is required.

### 0.3.13 highlights

- A configured phrase may appear inside a longer SMS; matching remains case-insensitive and typography-normalised.
- Automatic SMS replies no longer depend on Android notifications. There are no per-request or permanent status notifications and `POST_NOTIFICATIONS` is not declared.
- The app exposes its redacted twenty-entry local request history and offers targeted protection on
  a later opening after two valid SMS messages from one specific contact; contacts are never pooled.
- Normal resolution remains: Android location → trusted place → opt-in network/IP estimate → latest remembered coordinate → unavailable.
- VeVak keeps separate any-source memory for automatic requests and last-real/local memory for manual/emergency actions.
- The owner may opt into a single-slot last-position refresh target of 15/30/60 minutes (30 by default), with optional re-scheduling after boot. Optional background-location access is requested only for this off-screen refresh; it is never required for SMS replies. There is no route/history.
- Protection is targeted at a selected trusted contact while keeping that contact's existing phrase. The protected path only uses the pre-recorded fallback and never consults real location, trusted Wi-Fi or network approximation.
- Emergency recipients are selected locally in advance. A generic pinned home-screen shortcut can arm the emergency SMS for four seconds; a second tap cancels it. Emergency uses last-real/local position only and bypasses the remote-request quota.
- Emergency is unconfigured by default, preselects no recipient during onboarding and shows the real
  generic shortcut icon previews before pinning.
- The sideload/restricted-settings flow explains Android's `Allow restricted settings` step before opening app settings and rechecks permissions automatically on return.

Production automatic replies keep a device-wide minimum interval of 15 minutes and a maximum of four replies per 24 hours.

The canonical `foss` flavor contains no Google Play Services; the optional `play` flavor isolates Google Fused Location Provider in its own source set.

See [`PRIVACY.md`](PRIVACY.md), [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md), [`docs/final-hardening-0.3.11.md`](docs/final-hardening-0.3.11.md) and [`BUILDING.md`](BUILDING.md).

**Status: beta / real-device validation still required before a stable public release.**
