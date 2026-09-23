# VeVak iOS — prototype natif

Ce dossier est un **prototype isolé** du port iPhone. Il vit temporairement sur une branche dédiée du dépôt Android parce que l’outil GitHub utilisé pour ce chantier ne peut pas créer un nouveau dépôt. Il a vocation à être déplacé vers `vevak-ios` sans modifier l’architecture Android.

## Ce qui fonctionne dans ce premier jalon

- interface SwiftUI iOS 16+ ;
- jusqu’à cinq contacts de confiance ;
- stockage local JSON protégé par Data Protection et exclu de la sauvegarde de l’app ;
- acquisition **ponctuelle** de la position via Core Location après confirmation ;
- lien OpenStreetMap cohérent avec la variante libre Android ;
- préparation d’un SMS de partage via `MFMessageComposeViewController` ;
- destinataires d’urgence explicitement sélectionnés ;
- préparation de **SMS d’urgence séparés** : aucun SMS de groupe ne révèle les numéros des autres contacts ;
- aucune télémétrie, aucun compte, aucun serveur requis pour ce socle ;
- tests unitaires sur les invariants simples et build/test simulateur en CI ;
- paquet ARM64 non signé généré en CI pour re-signature sur les iPhone publics Sauce Labs.

## Différence volontaire avec Android

iOS ne fournit pas d’API publique générale équivalente au `BroadcastReceiver` SMS d’Android. `MessageUI` permet de **présenter** un SMS prérempli, mais la personne garde la main pour envoyer ou annuler. Le prototype ne prétend donc jamais écouter une phrase-clé SMS ni envoyer une réponse automatiquement.

Le simulateur peut compiler et tester la logique, mais `MFMessageComposeViewController.canSendText()` est faux sur simulateur : le flux SMS doit être validé sur un véritable iPhone.

## Construire localement sur macOS

Prérequis : Xcode 16+ et XcodeGen 2.46+.

```bash
brew install xcodegen
cd ios-prototype
xcodegen generate
open VeVak.xcodeproj
```

Aucun `TEAM_ID` n’est versionné. La CI compile avec `CODE_SIGNING_ALLOWED=NO`.

## Tester sur un vrai iPhone via Sauce Labs

Le workflow de cette branche construit aussi `VeVak-SauceLabs.ipa` pour `Any iOS Device (arm64)` sans signature Apple locale. L’artefact GitHub est conservé sept jours. Il est destiné à être téléversé dans le stockage d’apps Sauce Labs, où la re-signature iOS est activée sur les appareils publics.

Ce paquet sert au test, pas à la distribution App Store/TestFlight. Le composeur SMS, Core Location, l’orientation et l’accessibilité doivent encore être vérifiés sur l’iPhone distant.

## Demande distante : VeVak → VeVak

Pour retrouver une expérience de demande distante propre sur iPhone, le chemin privilégié est que le contact de confiance installe aussi VeVak. L’appairage et l’établissement de clés doivent être automatisés après **acceptation locale**, sur un modèle inspiré des prekeys Signal, sans échange manuel de secret.

Le design détaillé et les invariants de sécurité sont dans `VEVAK-TO-VEVAK.md`.

## Location Push

Apple propose une `Location Push Service Extension` pour les applications où une personne partage sa position avec des personnes qu’elle a explicitement approuvées. Ce chemin nécessite APNs, l’autorisation de localisation `Always`, l’entitlement Apple associé et un serveur qui émet les location pushes. Il n’est **pas** nécessaire au partage manuel ci-dessus et n’est pas activé dans ce prototype.

Voir `REMOTE-LOCATION-PUSH.md` avant toute implémentation : la confidentialité et l’anti-coercition priment sur la parité fonctionnelle.
