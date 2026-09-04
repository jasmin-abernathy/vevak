# Vie privée

VeVak n'utilise aucun compte VeVak, aucune publicité, aucun pisteur et aucun serveur applicatif obligatoire. Les réglages et mémoires de position restent dans le stockage privé Android de l'application. Les sauvegardes Android automatiques de l'application restent désactivées.

## Contacts et SMS

Chaque contact de confiance possède localement son numéro, sa phrase-clé et une autorisation limitée dans le temps. Un accès peut être révoqué immédiatement depuis le téléphone.

La phrase-clé est comparée sans tenir compte de la casse et après normalisation des espaces insécables et apostrophes typographiques courants. Depuis 0.3.11, la phrase-clé peut apparaître au milieu d'un SMS plus long : `Salut, position maintenant s'il te plaît` peut donc reconnaître la clé `position maintenant`. Le numéro expéditeur, l'autorisation active et les limites anti-suivi restent obligatoires.

## Notifications

Les demandes automatiques ne dépendent plus des notifications Android. VeVak 0.3.11 ne déclare pas `POST_NOTIFICATIONS`, n'affiche pas de notification à chaque demande et n'utilise pas de notification permanente `VeVak actif`.

Après deux réponses normales réussies du même contact, VeVak peut mémoriser uniquement un compteur local borné pour proposer la protection lors d'une prochaine ouverture volontaire de l'application. Ce compteur ne contient ni texte SMS, ni numéro, ni position.

## Dernières positions

VeVak conserve deux mémoires séparées :

- la dernière coordonnée issue de toute source légitime, utilisée par les réponses automatiques ;
- le dernier point réel/local, réservé au partage manuel et à l'urgence.

Une estimation réseau/IP activée volontairement peut alimenter la première mémoire mais ne remplace jamais le dernier point réel. Les coordonnées du lieu de repli de la protection sont exclues de ces deux mémoires.

Chaque nouveau point remplace le précédent. VeVak ne conserve pas de liste de positions, de trajet ou de breadcrumbs. L'heure d'acquisition est gardée afin que le SMS puisse indiquer l'ancienneté du point.

Les positions signalées comme simulées par Android sont refusées. Les positions mémorisées ne sont pas exportées dans les sauvegardes `.vvk`.

## Résolution normale

Pour une demande SMS normale et autorisée, VeVak essaie dans cet ordre :

1. une position Android récente ou actuelle si Android peut en fournir une ;
2. un lieu de confiance reconnu comme `Maison` ;
3. une estimation réseau/IP, uniquement si l'utilisateur l'a activée ;
4. la dernière coordonnée mémorisée, quelle que soit son ancienneté ;
5. `position indisponible` seulement si aucune source exploitable n'a jamais fourni d'information.

VeVak ne déclare pas `ACCESS_BACKGROUND_LOCATION`.

## Rafraîchissement périodique optionnel

L'utilisateur peut activer `Essayer de garder une dernière position récente` avec une fréquence cible de 15, 30 ou 60 minutes (30 minutes par défaut).

Cette fonction remplace toujours un seul point local : elle ne crée aucun historique. Elle programme un prochain passage ponctuel à la fois et n'utilise ni WorkManager périodique, ni alarme répétitive exacte, ni service de localisation permanent. Android et Doze peuvent retarder une tentative.

À chaque passage, VeVak vérifie que l'option est toujours active et qu'au moins un contact dispose encore d'une autorisation active. L'estimation réseau/IP n'est utilisée que si elle a été activée séparément.

Une option distincte permet de reprogrammer ce fonctionnement après le redémarrage du téléphone. Elle n'ouvre pas l'interface et ne crée pas de notification permanente.

## Estimation réseau facultative

L'estimation réseau est désactivée par défaut. Lorsqu'elle est activée, VeVak peut envoyer une requête HTTPS fondée uniquement sur l'adresse IP au service public beaconDB. VeVak n'envoie dans cette requête ni SSID, ni BSSID, ni Cell ID, ni numéro de téléphone, ni contenu SMS, ni phrase-clé, ni coordonnée locale.

Le service distant voit nécessairement l'adresse IP publique de la connexion. Le résultat reste présenté comme une zone approximative et ne devient jamais un faux point GPS précis.

## Wi-Fi Maison

VeVak peut associer la connexion Wi-Fi courante à un libellé local comme `Maison`. Lorsque le SSID est accessible, VeVak n'en conserve qu'une empreinte SHA-256, jamais le nom en clair. Lorsque les signaux disponibles sont insuffisants, VeVak préfère ne pas reconnaître Maison plutôt que risquer un faux positif.

Le Wi-Fi Maison n'est jamais consulté pour une demande relevant de la protection ciblée.

## Protection ciblée par contact

L'utilisateur choisit le contact dont il craint un usage abusif de la phrase-clé. Ce contact continue à envoyer sa phrase habituelle. Pour ce contact seulement, la réponse utilise exclusivement le lieu de repli préenregistré.

Dans ce chemin, VeVak ne consulte ni vraie position, ni Wi-Fi Maison, ni estimation réseau. Les autres contacts conservent le comportement normal.

Les anciennes sauvegardes contenant une seconde phrase de protection restent lisibles pour migration, mais l'interface actuelle ne demande plus de créer une seconde phrase.

## Partage manuel et urgence

Le partage manuel exige une sélection locale du destinataire et une confirmation explicite. Il utilise uniquement le dernier point réel déjà connu et la SIM définie par Android comme SIM SMS par défaut.

Les destinataires de l'urgence sont choisis à l'avance parmi les contacts autorisés. L'urgence locale n'est pas soumise au quota anti-suivi des demandes distantes et utilise uniquement le dernier point réel/local, sans estimation IP ni adresse géocodée.

VeVak peut créer un raccourci d'écran d'accueil avec un nom et une icône génériques. Le premier appui arme l'envoi pendant quatre secondes ; un second appui pendant ce délai annule l'action. Le raccourci et son résultat ne génèrent pas de notification VeVak. Le raccourci ne masque ni ne renomme l'application VeVak elle-même.

## Informations des réponses

Le SMS peut contenir le lien cartographique choisi, l'ancienneté du point, la batterie si cette option est activée et la précision/rayon si cette option est activée. Pour une coordonnée réelle, le géocodeur système Android peut ajouter `Adresse approx.` aux réponses normales et au partage manuel. L'échec du géocodeur ne bloque jamais l'envoi des coordonnées.

L'urgence reste plus compacte et n'ajoute pas cette adresse.

## Audit et diagnostics

VeVak conserve au maximum 20 résultats génériques récents de demandes. Cet audit ne contient ni coordonnées, ni texte SMS, ni numéro, ni phrase-clé, ni identifiant Wi-Fi et ne révèle pas si le lieu de repli a été utilisé.

Les diagnostics sont expurgés : ils peuvent afficher des comptages et états techniques, mais pas les coordonnées, numéros, phrases, SSID/BSSID ou identifiants cellulaires bruts.

## Sauvegarde chiffrée

L'export `.vvk` est chiffré et authentifié avec AES-GCM à partir d'une clé dérivée du mot de passe utilisateur par PBKDF2-HMAC-SHA256. Le mot de passe n'est pas enregistré par VeVak.

La sauvegarde peut conserver les préférences de rafraîchissement périodique, fréquence et reprise après redémarrage, mais jamais les positions mémorisées ni l'historique des demandes. Après restauration, toutes les autorisations de contacts restent révoquées jusqu'à une nouvelle validation locale.

## Réseau SMS et variantes

Les SMS passent par le réseau et l'application de messagerie Android. VeVak utilise les API SMS Android ; l'acceptation d'un envoi par Android n'est pas une preuve de livraison au destinataire.

La variante `foss` n'utilise pas Google Play Services. La variante `play` utilise le fournisseur de localisation Google, isolé dans sa flavor dédiée.

Voir aussi [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) et [`docs/final-hardening-0.3.11.md`](docs/final-hardening-0.3.11.md).
