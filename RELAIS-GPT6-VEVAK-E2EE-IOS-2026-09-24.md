# RELAIS GPT-6 — VeVak iOS / E2EE / relais aveugle — 2026-09-24

## But

Reprendre VeVak **à partir de l’état validé**, sans refaire les POC déjà conclus, afin de transformer le canal expérimental VeVak ↔ VeVak en architecture de production auditable.

La priorité n’est pas d’ajouter des fonctions : elle est de **figer correctement le protocole de sécurité avant intégration dans les apps Android/iOS**.

## Contraintes produit

- VeVak est privacy-first, local-first et anti-surveillance.
- Sur Android, le mécanisme SMS historique peut rester comme fallback/interoperabilité.
- Sur iOS, ne pas chercher à reproduire artificiellement le BroadcastReceiver SMS Android.
- Le chemin privilégié iPhone est VeVak ↔ VeVak : les deux personnes installent l’app, s’appairent explicitement, puis les demandes passent par un canal E2EE.
- Une invitation reçue à distance ne doit jamais autoriser à elle seule un suivi ou une demande automatique.
- Les autorisations automatiques de localisation doivent être désactivées par défaut, temporaires, révocables localement et suspendues après changement de clé/appareil.
- Aucun historique de trajet centralisé.
- Le relais ne doit jamais voir les coordonnées en clair.
- Pas de crypto maison si une primitive/protocole standard et maintenable couvre le besoin.

## État iOS VeVak

Branche prototype : `work/ios-native-prototype`  
HEAD validé : `86403c2d29ac3d03297c1699e7381b766f799b76`

Le prototype SwiftUI contient notamment :

- jusqu’à cinq contacts de confiance ;
- stockage local protégé ;
- acquisition ponctuelle Core Location après confirmation ;
- lien OpenStreetMap ;
- composeur SMS Apple ;
- SMS d’urgence **séparés par destinataire** pour éviter de révéler les numéros entre contacts ;
- documentation VeVak → VeVak et Location Push ;
- build/test simulateur ;
- IPA ARM64 de test pour device farm.

Un smoke-test Sauce Labs sur un vrai iPhone 15 a confirmé seulement : installation, lancement et écran d’accueil. Ne pas transformer cela en validation fonctionnelle de la localisation, SMS, navigation ou notifications.

## POC crypto de base — validé

Branche : `work/crypto-vodozemac-poc`  
SHA : `c9e5aa65aa756c0e8f59106842951d492eed6797`  
Run : `35835103002`

Choix testé : **vodozemac 0.11.0 / Olm**, Rust 1.89.

Validé :

- identité cryptographique locale par appareil ;
- one-time prekeys ;
- session asynchrone Alice ↔ Bob sans présence simultanée ;
- échange chiffré bidirectionnel ;
- signature Ed25519 d’un bundle public de prekeys ;
- détection d’altération du bundle ;
- anti-rejeu et expiration au niveau de l’enveloppe VeVak ;
- sérialisation/restauration Account + Session puis poursuite du ratchet ;
- réinstallation => nouvelle identité détectable ;
- compilation Android ARM64 ;
- compilation iPhone ARM64.

Ne pas remplacer vodozemac par libsignal par réflexe : Signal précise que son usage hors de ses propres produits n’est pas supporté.

## POC UniFFI Swift/Kotlin — validé

Branche : `work/crypto-uniffi-poc`  
SHA exact vert : `20ae2e5b87340310599e0248d446aeb2e57f147a`  
Run : `35974808713`

Choix : UniFFI 0.32.0 épinglé.

Validé réellement :

- génération bindings Kotlin ;
- compilation d’un appelant Kotlin ;
- compilation Rust Android ARM64 ;
- génération bindings Swift ;
- compilation du module Swift généré ;
- **exécution réelle d’un programme Swift appelant le cœur Rust/vodozemac via UniFFI** ;
- type-check Swift iPhone ARM64 ;
- compilation Rust iPhone ARM64.

Conclusion : la frontière Rust → Kotlin/Swift est viable. Il n’est pas nécessaire de basculer vers matrix-sdk-crypto uniquement pour obtenir une FFI mobile.

## POC relais aveugle — validé

Branche : `work/crypto-relay-poc`  
HEAD vert : `e6f09190762c726af06c40c08b2549aaee66873f`  
Run : `35975671795`

Le relais de test ne manipule que :

- identifiants pseudonymes d’appareils ;
- clés publiques/prekeys ;
- signatures publiques ;
- métadonnées de routage et expiration ;
- ciphertext Olm.

Le test bout-en-bout vérifie :

1. Bob publie un bundle de prekey signé.
2. Le relais délivre une one-time prekey une seule fois.
3. Alice vérifie la signature avec la clé publique de Bob.
4. Alice crée sa session uniquement depuis les données publiques du relais.
5. Alice chiffre une demande de position.
6. Le JSON complet du relais est inspecté et ne contient pas le type `Location` ni l’identifiant métier de la demande.
7. Bob déchiffre et l’anti-rejeu accepte une fois puis refuse la répétition.
8. Bob chiffre une position fictive.
9. Le JSON complet du relais est inspecté et ne contient ni latitude, ni longitude, ni champ de précision en clair.
10. Alice récupère et déchiffre exactement la réponse.
11. Le relais refuse doublons et enveloppes expirées.
12. Une réinstallation génère une nouvelle identité Curve25519 + Ed25519.

La branche reste un POC, pas un serveur de production.

## Ce qu’il NE faut PAS refaire

- Ne pas réécrire le prototype iOS depuis zéro.
- Ne pas remplacer vodozemac sans motif technique documenté.
- Ne pas introduire de protocole crypto ad hoc.
- Ne pas fusionner les branches POC dans Android/main avant revue de sécurité.
- Ne pas ajouter APNs/Location Push avant de figer consentement, révocation, quotas et threat model.
- Ne pas inventer de TEAM_ID Apple.
- Ne pas considérer Sauce Labs comme validation fonctionnelle complète.
- Ne pas réorganiser la PR Android #35 ou casser les travaux Android existants.

## Revue demandée à GPT-6

Produire d’abord une **revue d’architecture et threat model**, puis seulement du code si les décisions sont suffisamment sûres.

### 1. Protocole applicatif exact

Définir ce qui doit être authentifié/signé autour d’Olm :

- identifiant de relation ;
- sender/recipient device IDs ;
- nonce ;
- issued_at / expires_at ;
- type de message ;
- version de protocole ;
- éventuel compteur/séquence ;
- liaison avec l’identité vérifiée du contact.

Décider quels champs sont dans le plaintext E2EE, lesquels peuvent rester métadonnées visibles au relais, et lesquels doivent être liés cryptographiquement au ciphertext pour empêcher substitution/routage malveillant.

### 2. Prekeys

Définir :

- taille du stock ;
- seuil de réapprovisionnement ;
- rotation ;
- comportement si stock épuisé ;
- prévention de réutilisation ;
- suppression côté relais après consommation ;
- éventuelle signed prekey à durée plus longue si nécessaire.

### 3. Multi-appareil / réinstallation / récupération

Décider explicitement :

- une identité = personne ou appareil ?
- peut-on avoir plusieurs appareils par contact ?
- comment distinguer remplacement légitime et attaque ?
- que sauvegarder, où, et avec quel chiffrement ?
- faut-il permettre une récupération cryptographique ou préférer ré-appairer ?
- quelles autorisations sont révoquées après nouvelle identité ?

Le comportement actuel souhaité est conservateur : **nouvelle identité => suspension des autorisations automatiques jusqu’à revalidation locale**.

### 4. Vérification des identités

Définir une UX simple :

- appairage ordinaire automatisé ;
- empreinte/QR facultatif pour vérification renforcée ;
- avertissement clair sur changement de clé ;
- aucune formulation qui pousse l’utilisateur à accepter aveuglément un changement inattendu.

### 5. Threat model stalking/coercition

Étudier au minimum :

- contact auparavant approuvé devenu hostile ;
- téléphone du demandeur compromis ;
- téléphone du propriétaire brièvement accessible à un tiers ;
- relais compromis ;
- rejeu/réordonnancement ;
- corrélation de métadonnées ;
- création massive de demandes ;
- autorisation automatique prolongée abusivement ;
- pression/coercition pour autoriser un contact ;
- suppression/revocation discrète et sûre.

### 6. Autorisation de localisation

Définir au moins deux niveaux :

- demande confirmée localement à chaque fois ;
- autorisation automatique facultative et temporaire.

Pour le mode automatique proposer :

- durée maximale ;
- fréquence/quota VeVak nettement inférieur aux limites Apple ;
- cooldown ;
- expiration non renouvelable à distance ;
- révocation locale immédiate ;
- journal local minimal ;
- comportement lors d’un changement de clé.

### 7. Relais

Partir du POC aveugle et proposer une API minimale :

- publier/renouveler des prekeys ;
- consommer une prekey ;
- déposer une enveloppe ;
- récupérer les enveloppes ;
- accusé technique/suppression ;
- expiration serveur ;
- anti-abus sans carnet d’adresses central.

Étudier les métadonnées encore visibles et réduire ce qui peut l’être.

### 8. Stockage local mobile

Proposer une frontière claire :

- Rust sérialise/chiffre son état ;
- iOS stocke la clé locale dans Keychain ;
- Android protège la clé via Android Keystore ;
- pas de secret brut dans UserDefaults/SharedPreferences ;
- politique de sauvegarde/restauration explicitement décidée.

### 9. Packaging de production

À partir de la FFI validée, définir le chemin le plus simple pour :

- XCFramework iOS ;
- bibliothèque native/AAR Android ;
- génération reproductible des bindings ;
- versionnage du protocole et du cœur crypto ;
- tests croisés Swift ↔ Kotlin.

## Attendu de la réponse GPT-6

1. Audit des choix actuels.
2. Liste des risques réels, classés par gravité mais **sans remplacer les parties déjà validées sans raison**.
3. Décisions recommandées sur les neuf points ci-dessus.
4. Plan d’intégration par petits lots testables.
5. Ce que GPT-5.6 peut ensuite implémenter en sécurité.
6. Ce que GPT-6 doit garder pour lui-même.
7. Un nouveau relais MD seulement après modifications substantielles.

## Discipline repo

Avant toute modification Git, lire :

- `jasmin-abernathy/repo-factory/README.md`
- `AGENTS.md`
- `COMPATIBILITY-AND-DEPRECATION-PLAYBOOK.md`
- `KMP-IOS-PORTING-PLAYBOOK.md`

Règles : edit many → validate once → build once ; exact-SHA validation ; re-fetch après mutation échouée ; ne pas reconstruire un fichier complet depuis une lecture partielle ; documenter les enseignements techniques dans repo-factory.
