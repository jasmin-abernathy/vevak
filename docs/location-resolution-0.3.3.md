# VeVak 0.3.3 — résolution de position multi-source

## Pourquoi cette révision

VeVak 0.3.2 affichait « Localisation désactivée » dès que le bouton système Android était coupé. Cet état était trompeur : il décrivait uniquement la capacité d'Android à produire un **nouveau point précis**, pas la capacité globale de VeVak à répondre.

La 0.3.3 remplace cette logique par un resolver unique partagé par les réponses SMS normales, le partage manuel et le test intégré.

## Ordre de résolution

1. **Lieu de confiance déjà reconnu** (ex. Maison) — aucune acquisition de position.
2. **Position Android / cache / mémoire VeVak suffisamment récente**.
3. **Estimation réseau IP** via beaconDB, uniquement si l'utilisateur l'a explicitement activée.
4. **Ancienne position locale**, uniquement si l'utilisateur autorise le repli ancien.
5. **Indisponible** si aucune source n'est utilisable.

Une estimation IP est toujours marquée comme approximative et n'est jamais présentée comme une position GPS.

## Limites Android vérifiées

- Le bouton système Localisation OFF empêche une application Android ordinaire d'obtenir un nouveau point via GPS/fused/network provider.
- Les identifiants de cellules LTE/5G nécessaires à une géolocalisation par antennes sont protégés par les règles de localisation Android ; ils ne constituent pas un contournement général lorsque Localisation est OFF.
- Les scans Wi-Fi exploitables pour la géolocalisation sont eux aussi liés aux règles de localisation Android.
- Les réseaux de type Find Hub / SmartThings Find disposent d'une infrastructure collaborative et/ou d'intégrations système qu'une application Android ordinaire ne possède pas.

## Estimation IP et confidentialité

Le fallback réseau est **désactivé par défaut**. Lorsqu'il est activé et que les sources locales récentes échouent, VeVak envoie une requête IP-only à :

`https://api.beacondb.net/v1/geolocate`

Aucun SSID, BSSID, Cell ID, numéro de téléphone, contenu SMS ou coordonnée locale n'est envoyé par VeVak dans cette requête. Le serveur voit nécessairement l'adresse IP publique de la connexion. La réponse peut être très approximative (ville/région, parfois plusieurs kilomètres).

## Laboratoire localisation ON/OFF

Le diagnostic 0.3.3 expose uniquement des compteurs et booléens non sensibles :

- état ON/OFF de la localisation Android ;
- nombre de providers connus / actifs ;
- nombre de providers possédant un cache ;
- nombre d'enregistrements cellulaires visibles par l'API Android ;
- identité Wi-Fi lisible ou masquée ;
- type de connexion active.

Il n'affiche et ne journalise **jamais** les coordonnées, Cell IDs, BSSID, SSID ou identifiants téléphoniques.

### Test appareil réel recommandé

1. Accorder les permissions VeVak et activer la localisation Android.
2. Ouvrir Réglages → Diagnostic et noter les compteurs du laboratoire.
3. Lancer « Tester toutes les sources ».
4. Désactiver volontairement la localisation Android.
5. Revenir dans VeVak : le diagnostic est rafraîchi à la reprise.
6. Comparer les compteurs et relancer « Tester toutes les sources ».
7. Refaire le test avec l'estimation réseau désactivée puis activée.
8. Tester enfin une demande SMS réelle avec l'application au second plan.

Ce protocole permet de mesurer ce que **le modèle de téléphone testé** expose réellement plutôt que d'inférer le comportement depuis la documentation Android.

## Pistes futures évaluées

### Cellules 4G/5G

À conserver comme diagnostic/optimisation lorsqu'Android expose effectivement des `CellInfo`, mais pas comme garantie quand Localisation est OFF.

### Wi-Fi environnant

Utile lorsque les scans sont légalement/API-accessibles, mais pas comme contournement fiable du bouton système.

### Réseau collaboratif BLE

Piste 0.4+ : identifiants BLE tournants détectés par d'autres appareils VeVak, sur le modèle conceptuel des réseaux Find Hub/SmartThings Find. Cette piste nécessite une conception cryptographique, une infrastructure et une masse critique ; elle ne fait pas partie de la 0.3.3.

## Invariants de sécurité

- Le mode de protection sous contrainte ne passe jamais par le resolver normal et n'effectue jamais le fallback réseau.
- L'estimation réseau est désactivée par défaut et contrôlée par une option explicite.
- Les réponses approximatives l'indiquent clairement au destinataire.
- Aucun suivi continu n'est ajouté.
- Les limites anti-demandes restent globales au téléphone.
