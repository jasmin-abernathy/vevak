# VeVak → VeVak : appairage distant et demandes chiffrées

## Idée produit

Sur iPhone, VeVak ne peut pas surveiller les SMS comme sur Android. La voie privilégiée pour une demande de position distante devient donc **VeVak → VeVak** : les deux personnes installent VeVak, s’autorisent explicitement, puis les demandes passent par un canal chiffré de bout en bout.

Le SMS reste utile comme partage manuel et fallback d’interopérabilité. Il ne doit pas être présenté comme le mécanisme automatique principal sur iOS.

## Inspiration Signal, sans réimplémenter Signal au hasard

Signal résout l’établissement de session asynchrone avec des clés d’identité et des **prekeys** publiques publiées à l’avance sur un serveur. Un correspondant peut récupérer un bundle public et commencer une session sans que l’autre téléphone soit simultanément en ligne.

Références :
- https://signal.org/docs/specifications/pqxdh/
- https://signal.org/docs/specifications/doubleratchet/
- https://support.signal.org/hc/fr/articles/10223569377562-V%C3%A9rification-automatique-des-cl%C3%A9s
- https://support.signal.org/hc/fr/articles/360007060632-Qu-est-ce-qu-un-num%C3%A9ro-de-s%C3%A9curit%C3%A9-et-pourquoi-peut-il-changer

Le dépôt officiel libsignal expose des APIs Java et Swift, mais Signal indique que l’usage hors de ses propres produits n’est pas supporté et que son intégration Swift Package n’est pas un chemin tiers supporté. VeVak doit donc reprendre **les propriétés de sécurité et l’ergonomie**, pas copier une dépendance instable sans revue.

Aucun protocole cryptographique VeVak ne doit être codé avant choix d’une bibliothèque/primitive standard, auditée et maintenable sur Android + iOS.

## Parcours utilisateur cible

1. À l’installation, VeVak crée localement une identité cryptographique.
2. Le téléphone publie uniquement le matériel **public** nécessaire à l’établissement asynchrone d’une session.
3. Alice ajoute Bob et crée une invitation VeVak à usage unique.
4. L’invitation peut être envoyée par SMS, Signal, mail, QR ou lien universel. Elle ne contient aucune clé privée.
5. Bob ouvre VeVak et **accepte localement** l’invitation.
6. Les deux apps récupèrent le matériel public nécessaire via le relais et établissent leur relation chiffrée automatiquement.
7. VeVak affiche « Chiffrement établi ». Un QR/empreinte de sécurité reste disponible pour une vérification renforcée.
8. Une demande de position future est chiffrée pour l’autre appareil ; le relais et APNs ne voient pas la position en clair.

Aucune invitation reçue à distance ne doit activer une autorisation sans action locale sur le téléphone protégé.

## Deux niveaux d’autorisation

### Demande confirmée

Par défaut, Bob demande une position et Alice reçoit une notification VeVak :
- « Bob demande ta position »
- Partager
- Refuser

Le partage n’a lieu qu’après confirmation locale.

### Autorisation automatique temporaire

Alice peut choisir explicitement d’autoriser Bob à demander sa position sans confirmation à chaque fois pendant une durée finie.

Ce mode :
- est désactivé par défaut ;
- a une expiration explicite ;
- reste soumis au quota anti-suivi ;
- peut être révoqué immédiatement ;
- ne peut jamais être prolongé à distance ;
- doit être suspendu si l’identité cryptographique de Bob change ;
- utilisera, sur iOS, la Location Push Service Extension seulement lorsque les capacités Apple Developer/APNs sont disponibles.

## Changement de téléphone ou réinstallation

Un changement inattendu de clé est un événement de sécurité.

VeVak doit :
1. avertir clairement que l’identité cryptographique du contact a changé ;
2. conserver le contact mais suspendre toute autorisation automatique ;
3. exiger une revalidation locale avant de restaurer les privilèges distants ;
4. proposer une nouvelle vérification QR/empreinte si l’utilisateur le souhaite.

Le relais ne doit jamais décider seul qu’une nouvelle clé remplace l’ancienne pour une autorisation sensible.

## Rôle du relais

Le relais VeVak doit rester minimal et opaque.

Il peut conserver temporairement :
- identifiant pseudonyme d’appareil ;
- clés publiques/prekeys ;
- jeton APNs ou équivalent de routage ;
- enveloppes chiffrées en attente ;
- nonce, expiration et état anti-rejeu.

Il ne doit pas recevoir :
- clé privée ;
- coordonnées en clair ;
- historique de trajet ;
- carnet d’adresses ;
- phrase ou secret permettant d’usurper une autorisation.

Les enveloppes expirées doivent être supprimées rapidement.

## Identité et découverte

VeVak ne doit pas rendre les utilisateurs globalement recherchables par numéro de téléphone par défaut.

Le modèle recommandé est **invitation explicite** :
- lien/QR à usage unique ;
- token aléatoire à forte entropie ;
- expiration courte ;
- acceptation locale ;
- rattachement à la clé d’identité de l’invitant.

Une éventuelle découverte par numéro serait une fonction séparée, opt-in, nécessitant une analyse de confidentialité spécifique.

## Protocole de requête : invariants

Chaque demande distante doit comporter au minimum :
- identifiant de relation ;
- nonce imprévisible ;
- date d’émission ;
- expiration courte ;
- type de demande ;
- authentification cryptographique du demandeur.

Le destinataire doit rejeter :
- nonce déjà vu ;
- requête expirée ;
- signature/authentification invalide ;
- contact révoqué ;
- autorisation automatique expirée ;
- dépassement de quota.

## Compatibilité Android

À terme, VeVak → VeVak peut devenir le chemin commun Android/iOS.

Android peut conserver en plus son mécanisme SMS local pour :
- contacts sans VeVak ;
- absence de données ;
- interopérabilité historique.

L’installation de VeVak chez les deux personnes débloque alors :
- demandes chiffrées ;
- notifications riches ;
- révocation synchronisée ;
- vérification de clé ;
- meilleure expérience iPhone.

## Avant implémentation cryptographique

À décider et documenter :
1. bibliothèque/protocole audité réellement maintenable sur Swift et Kotlin ;
2. stockage des clés privées (Keychain / Android Keystore) ;
3. format d’enveloppe commun ;
4. rotation des prekeys ;
5. récupération après réinstallation ;
6. protection contre rejeu et rollback ;
7. métadonnées visibles au relais ;
8. stratégie de transparence/vérification de clés ;
9. threat model spécifique coercition/stalking.

Aucun « crypto maison » ne doit être fusionné pour accélérer ce jalon.
