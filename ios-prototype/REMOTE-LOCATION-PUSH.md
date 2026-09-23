# VeVak iOS — piste Location Push Service Extension

## Pourquoi cette piste existe

Apple documente depuis iOS 15 une **Location Push Service Extension (LPSE)** conçue notamment pour les apps où des personnes partagent leur position avec d’autres personnes qu’elles ont explicitement approuvées. Le système peut réveiller l’extension sur un \`location\` push APNs même lorsque l’app principale n’est pas ouverte.

Cette API est beaucoup plus proche de l’intention de VeVak qu’un détournement du filtrage SMS. Elle ne doit toutefois pas être présentée comme un clone du mécanisme Android : le déclencheur passe par APNs et un serveur, pas par la lecture d’un SMS local.

Références Apple :

- https://developer.apple.com/documentation/corelocation/creating-a-location-push-service-extension
- https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.location.push
- https://developer.apple.com/documentation/corelocation/cllocationpushserviceextension

## Conditions Apple connues

- entitlement \`com.apple.developer.location.push\` ;
- Push Notifications ;
- autorisation Core Location **Always** ;
- token LPSE obtenu sur l’iPhone puis enregistré côté service ;
- un serveur émet le push de type \`location\` via APNs ;
- Apple impose des limites d’usage ; sa documentation évoque environ 360 événements de location push par période de 24 h, avec reconstitution progressive du quota ;
- Apple demande une protection forte de la vie privée et recommande le chiffrement de bout en bout lorsque la position quitte le téléphone.

## Contrat VeVak proposé avant code

Cette fonction doit rester **facultative**. Le socle manuel de VeVak doit continuer à fonctionner sans compte ni serveur.

Pour chaque personne autorisée à demander la position :

1. consentement local explicite sur l’iPhone protégé ;
2. durée d’autorisation finie et révocable ;
3. aucune création ou extension d’autorisation à distance ;
4. limite anti-suivi VeVak nettement inférieure au quota Apple ;
5. requête authentifiée et non rejouable ;
6. position chiffrée de bout en bout pour le destinataire ;
7. serveur incapable de lire ou conserver la position en clair ;
8. pas d’historique de trajet ;
9. journal local minimal et expurgé ;
10. la protection ciblée/anti-coercition doit être re-menacée spécifiquement avant d’autoriser ce chemin.

## Architecture minimale envisagée

\`\`\`text
App du contact autorisé
       |
       | requête authentifiée, nonce + expiration
       v
Relais VeVak minimal
       |
       | APNs location push
       v
LPSE sur l’iPhone protégé
       |
       | Core Location -> chiffrement vers clé du contact
       v
Relais opaque -> contact
\`\`\`

Le relais ne doit pas devenir un compte VeVak central : identifiants pseudonymes, métadonnées minimales, suppression rapide et absence de coordonnées en clair.

## Pourquoi ce n’est pas dans le premier commit

Le prototype actuel peut être compilé sans signature Apple. LPSE et APNs nécessitent les capacités/signatures du programme Apple Developer et un test sur appareil réel. Ajouter un faux target non testable maintenant augmenterait le risque sans valider le comportement.

La prochaine passe doit commencer par un threat model et un protocole cryptographique documenté, puis seulement créer le target LPSE.
