# Vie privée

VeVak n'utilise aucun compte VeVak, aucune publicité, aucun pisteur et aucun serveur applicatif obligatoire.

Les réglages restent dans le stockage privé Android de l'application : numéros autorisés, noms facultatifs, phrases normales, options de réponse, périodes d'autorisation et, si l'utilisateur l'active, phrase sous contrainte et coordonnées du lieu de repli. Chaque contact de confiance dispose de sa propre autorisation locale et de sa propre expiration. Si un lieu de confiance Wi-Fi est enregistré avec l'identification durable, VeVak conserve uniquement une empreinte SHA-256 du nom du réseau ainsi qu'un libellé choisi par l'utilisateur ; le SSID n'est pas stocké en clair. Si l'utilisateur configure Maison sans activer la localisation, VeVak ne lit pas le nom du Wi-Fi : il mémorise uniquement, dans un stockage runtime séparé, l'identifiant opaque de la session réseau Android courante et le numéro de démarrage Android. Les sauvegardes Android et transferts automatiques de l'application restent désactivés.

Pour éviter de dépendre du cache de localisation d'Android — que le système peut effacer lorsque l'utilisateur coupe le bouton global « Localisation » — VeVak conserve également dans son stockage privé **une seule dernière position réelle obtenue avec succès**, avec sa précision et son heure d'acquisition. Cette mémoire locale expire automatiquement après **24 heures**, n'est jamais envoyée à un serveur et n'est pas incluse dans l'export `.vvk`. Une position signalée par Android comme simulée n'est pas mémorisée dans ce cache de résilience.

Le contenu des SMS, les coordonnées, les numéros de téléphone, les phrases et les identifiants Wi-Fi ne sont pas écrits dans les journaux par le code VeVak. Le diagnostic exportable est volontairement expurgé de ces données ; il peut seulement indiquer un nombre agrégé de contacts configurés/actifs et si les mécanismes de repli sont disponibles en principe.

VeVak conserve localement au maximum 20 résultats récents de demandes (horodatage + résultat générique). Cet historique ne contient ni coordonnées, ni contenu SMS, ni numéro, ni phrase, ni Wi-Fi et ne permet pas de distinguer une réponse normale d'une réponse utilisant le lieu de repli. Les partages manuels sortants ne sont pas ajoutés à cet historique de demandes.

Une demande reconnue doit pouvoir rester localement visible avant qu'une position soit envoyée automatiquement. Si les notifications VeVak sont désactivées ou interdites, aucune position automatique n'est envoyée. Le mode discret temporaire utilise un canal silencieux et sans vibration, mais ne supprime ni la notification de demande dans le volet Android ni la notification persistante indiquant que VeVak est actif.

Le propriétaire du téléphone peut aussi déclencher lui-même un partage manuel de position vers n'importe quel contact VeVak configuré. Ce flux exige une sélection locale du destinataire et une confirmation explicite avant l'acquisition de position et l'envoi du SMS. Il peut être utilisé même si l'autorisation automatique de ce contact est en pause, puisque l'envoi est initié localement par le propriétaire. Si aucune position actuelle n'est disponible mais qu'une dernière position VeVak encore valide peut être utilisée conformément aux réglages de repli, son âge est explicitement indiqué dans le SMS. Ce flux n'appelle aucun service d'urgence et ne peut pas être déclenché à distance.

Le partage manuel n'affiche pas de notification Android supplémentaire : son état de réussite ou d'échec reste dans l'interface ouverte de VeVak. Cela vaut également lorsque le mode discret temporaire est actif. La notification persistante `VeVak est actif`, lorsqu'elle est requise par le fonctionnement des réponses automatiques, reste indépendante de ce partage manuel.

Pour éviter un choix implicite sur les appareils double-SIM/eSIM, le partage manuel utilise uniquement la SIM définie par Android comme SIM SMS par défaut. Si Android n'en expose aucune, VeVak bloque le partage et demande à l'utilisateur d'en choisir une dans les réglages du téléphone.

Lorsqu'un lieu de confiance Wi-Fi est configuré et que le téléphone est connecté au réseau correspondant, une commande normale peut répondre avec le libellé du lieu sans déclencher de nouvelle acquisition GPS. **Activer la localisation n'est pas obligatoire pour enregistrer la connexion Wi-Fi actuelle comme Maison.** Sans localisation, VeVak enregistre uniquement la session réseau Android courante : cette reconnaissance reste valable tant que cette connexion précise reste active, dans le même démarrage du téléphone. Une déconnexion/reconnexion ou un redémarrage invalide volontairement ce raccourci.

Android considère le SSID comme une information liée à la localisation. Lorsque l'utilisateur autorise la localisation précise et active la localisation Android, VeVak peut lire ponctuellement le SSID au moment de la configuration, en conserver uniquement une empreinte et rendre ainsi la reconnaissance de Maison durable après une reconnexion ou un redémarrage. Cette amélioration est facultative : elle n'est jamais nécessaire pour utiliser le mode limité à la session Wi-Fi courante. VeVak ne déduit jamais « Maison » à partir d'une simple adresse IP privée, d'une passerelle courante ou d'autres caractéristiques réseau partagées.

La permission Android `ACCESS_NETWORK_STATE` utilisée pour cette continuité est une permission de lecture de l'état des réseaux. Elle ne remplace pas et n'ajoute pas la permission `INTERNET`, qui reste absente de la variante FOSS canonique. VeVak n'ouvre aucune connexion réseau pour reconnaître ce lieu de confiance.

Cette détection du lieu de confiance n'est jamais utilisée pour une commande sous contrainte. En mode sous contrainte, la réponse est construite à partir des coordonnées de repli enregistrées à l'avance. Le chemin de localisation réelle et la détection du Wi-Fi actuel ne sont pas appelés pour cette commande. La phrase sous contrainte doit rester distincte de toutes les phrases normales configurées pour les différents contacts.

## Sauvegarde chiffrée locale

VeVak permet d'exporter gratuitement sa configuration vers un fichier `.vvk` choisi via le sélecteur de documents Android. Aucun serveur VeVak n'est utilisé pour cette opération.

Le contenu en clair de cette configuration peut inclure des données sensibles (numéros, phrases, empreinte Wi-Fi durable, coordonnées de repli). Avant écriture, VeVak chiffre et authentifie le contenu avec AES-GCM à l'aide d'une clé dérivée du mot de passe fourni par l'utilisateur via PBKDF2-HMAC-SHA256, avec sel aléatoire et IV aléatoire. Le mot de passe n'est pas enregistré par VeVak. En mode Wi-Fi limité à la session, la sauvegarde peut contenir le marqueur indiquant que ce mode était choisi, mais jamais l'identifiant réel de la session réseau courante.

Ne sont jamais exportés dans cette sauvegarde :

- l'historique local des demandes ;
- les horodatages d'autorisation active des contacts ;
- l'état temporaire du mode discret ;
- la dernière position mémorisée par le mécanisme de résilience ;
- l'identifiant temporaire de session réseau utilisé pour la continuité du Wi-Fi de confiance.

Après import, tous les contacts restaurés restent désautorisés jusqu'à une nouvelle validation locale. Une erreur de mot de passe, un fichier corrompu ou une configuration invalide laisse les réglages actuels inchangés. Si une sauvegarde provenait d'un mode Wi-Fi limité à une session, cette session ne peut pas être restaurée : l'utilisateur doit réenregistrer Maison sur le téléphone concerné.

Le chiffrement protège le contenu du fichier, mais un fichier chiffré peut toujours être copié par une personne disposant d'un accès aux fichiers de l'appareil. Il doit donc être conservé avec les mêmes précautions que toute sauvegarde sensible, et son mot de passe doit être conservé séparément.

Les SMS reçus peuvent être visibles dans l'application de messagerie du téléphone et sont traités par le réseau/opérateur. VeVak envoie ses réponses et partages manuels via les API SMS Android ; il ne faut pas supposer que toutes les applications de messagerie afficheront systématiquement une copie du SMS sortant. Lorsqu'Android accepte un SMS pour envoi, VeVak ne présente pas cette étape comme une preuve que le destinataire l'a effectivement reçu.

La variante `foss` n'utilise pas Google Play Services. La variante `play` utilise le fournisseur de localisation Google ; elle reste isolée dans sa flavor dédiée et n'est pas destinée au dépôt principal F-Droid.

Voir aussi [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) pour les règles destinées à limiter les détournements de VeVak comme outil de surveillance coercitive.
