# Vie privée

VeVak n'utilise aucun compte VeVak, aucune publicité, aucun pisteur et aucun serveur applicatif obligatoire.

Les réglages restent dans le stockage privé Android de l'application : numéros autorisés, noms facultatifs, phrases normales, options de réponse, périodes d'autorisation et, si l'utilisateur l'active, phrase sous contrainte et coordonnées du lieu de repli. Chaque contact de confiance dispose de sa propre autorisation locale et de sa propre expiration. Si un lieu de confiance Wi-Fi est enregistré avec l'identification durable, VeVak conserve uniquement une empreinte SHA-256 du nom du réseau ainsi qu'un libellé choisi par l'utilisateur ; le SSID n'est pas stocké en clair. Si l'utilisateur configure Maison sans activer la localisation, VeVak ne lit pas le nom du Wi-Fi : il mémorise uniquement, dans un stockage runtime séparé, l'identifiant opaque de la session réseau Android courante et le numéro de démarrage Android. Les sauvegardes Android et transferts automatiques de l'application restent désactivés.

Pour éviter de dépendre du cache de localisation d'Android — que le système peut effacer lorsque l'utilisateur coupe le bouton global « Localisation » — VeVak conserve également dans son stockage privé **une seule dernière position réelle obtenue avec succès**, avec sa précision et son heure d'acquisition. Cette mémoire locale expire automatiquement après **24 heures**, n'est jamais envoyée à un serveur et n'est pas incluse dans l'export `.vvk`. Une position signalée par Android comme simulée n'est pas mémorisée dans ce cache de résilience.

## Résolution de position et estimation réseau facultative

Depuis la 0.3.3, les demandes normales, le partage manuel et le test de localisation utilisent le même moteur de résolution. Il privilégie un lieu de confiance déjà reconnu, puis les informations de localisation locales récentes (Android, caches et mémoire VeVak). Si ces sources ne suffisent pas, **une estimation réseau approximative peut être utilisée uniquement si l'utilisateur l'a explicitement activée**. Une ancienne position locale peut ensuite servir de dernier recours lorsque cette option est elle aussi autorisée.

L'estimation réseau est **désactivée par défaut**. Lorsqu'elle est activée, VeVak peut envoyer une requête HTTPS IP-only au service public beaconDB (`https://api.beacondb.net/v1/geolocate`). VeVak n'envoie dans cette requête ni SSID, ni BSSID, ni Cell ID, ni numéro de téléphone, ni texte SMS, ni phrase-clé, ni coordonnée locale. Comme pour toute connexion Internet, le serveur distant voit nécessairement l'adresse IP publique utilisée pour la requête ainsi qu'un User-Agent technique indiquant VeVak, sa version et sa variante. Cette adresse IP peut permettre au service de produire une estimation grossière de zone géographique.

Une position obtenue de cette manière est toujours marquée dans le SMS comme **« position approximative via le réseau (adresse IP), pas une position GPS »**, avec l'incertitude retournée lorsqu'elle est disponible. VeVak ne transforme jamais cette estimation en position « précise » et ne la mémorise pas comme dernière position réelle de résilience.

La permission Android `INTERNET` est donc présente dans la variante FOSS à partir de la 0.3.3, mais **aucune requête de géolocalisation réseau n'est effectuée tant que l'utilisateur n'a pas activé cette option**. L'option choisie est enregistrée localement et peut être incluse dans une sauvegarde `.vvk` chiffrée ; elle ne réactive jamais une autorisation de contact.

Le mode sous contrainte est volontairement isolé de ce moteur normal : une commande sous contrainte n'inspecte ni le Wi-Fi courant, ni les caches réels, ni la position réelle, et **n'effectue jamais la requête beaconDB**, même si l'estimation réseau est activée.

## Diagnostics expurgés

Le contenu des SMS, les coordonnées, les numéros de téléphone, les phrases et les identifiants Wi-Fi ne sont pas écrits dans les journaux par le code VeVak. Le diagnostic exportable est volontairement expurgé de ces données.

Le laboratoire de localisation de la 0.3.3 peut indiquer des **comptages et états non identifiants** afin de comparer le comportement d'un téléphone avec le bouton Android Localisation activé puis désactivé : nombre de fournisseurs Android connus/actifs, nombre de fournisseurs disposant d'un cache, nombre d'enregistrements cellulaires que l'API Android rend visibles, identité Wi-Fi lisible ou masquée et type de transport réseau actif. Il ne conserve ni n'affiche les Cell IDs, BSSID, SSID, coordonnées ou identifiants téléphoniques correspondants.

VeVak conserve localement au maximum 20 résultats récents de demandes (horodatage + résultat générique). Cet historique ne contient ni coordonnées, ni contenu SMS, ni numéro, ni phrase, ni Wi-Fi et ne permet pas de distinguer une réponse normale d'une réponse utilisant le lieu de repli. Les partages manuels sortants ne sont pas ajoutés à cet historique de demandes.

Une demande reconnue doit pouvoir rester localement visible avant qu'une position soit envoyée automatiquement. Si les notifications VeVak sont désactivées ou interdites, aucune position automatique n'est envoyée. Le mode discret temporaire utilise un canal silencieux et sans vibration, mais ne supprime ni la notification de demande dans le volet Android ni la notification persistante indiquant que VeVak est actif.

Le propriétaire du téléphone peut aussi déclencher lui-même un partage manuel de position vers n'importe quel contact VeVak configuré. Ce flux exige une sélection locale du destinataire et une confirmation explicite avant la résolution de position et l'envoi du SMS. Il peut être utilisé même si l'autorisation automatique de ce contact est en pause, puisque l'envoi est initié localement par le propriétaire. Le même ordre de résolution que pour une demande normale est utilisé : lieu reconnu, position locale récente, estimation réseau si elle est activée, puis position ancienne autorisée. La nature et l'âge de la donnée sont indiqués dans la réponse lorsque cela s'applique. Ce flux n'appelle aucun service d'urgence et ne peut pas être déclenché à distance.

Le partage manuel n'affiche pas de notification Android supplémentaire : son état de réussite ou d'échec reste dans l'interface ouverte de VeVak. Cela vaut également lorsque le mode discret temporaire est actif. La notification persistante `VeVak est actif`, lorsqu'elle est requise par le fonctionnement des réponses automatiques, reste indépendante de ce partage manuel.

Pour éviter un choix implicite sur les appareils double-SIM/eSIM, le partage manuel utilise uniquement la SIM définie par Android comme SIM SMS par défaut. Si Android n'en expose aucune, VeVak bloque le partage et demande à l'utilisateur d'en choisir une dans les réglages du téléphone.

Lorsqu'un lieu de confiance Wi-Fi est configuré et que le téléphone est connecté au réseau correspondant, une commande normale peut répondre avec le libellé du lieu sans déclencher de nouvelle acquisition GPS. **Activer la localisation n'est pas obligatoire pour enregistrer la connexion Wi-Fi actuelle comme Maison.** Sans localisation, VeVak enregistre uniquement la session réseau Android courante : cette reconnaissance reste valable tant que cette connexion précise reste active, dans le même démarrage du téléphone. Une déconnexion/reconnexion ou un redémarrage invalide volontairement ce raccourci.

Android considère le SSID comme une information liée à la localisation. Lorsque l'utilisateur autorise la localisation précise et active la localisation Android, VeVak peut lire ponctuellement le SSID au moment de la configuration, en conserver uniquement une empreinte et rendre ainsi la reconnaissance de Maison durable après une reconnexion ou un redémarrage. Cette amélioration est facultative : elle n'est jamais nécessaire pour utiliser le mode limité à la session Wi-Fi courante. VeVak ne déduit jamais « Maison » à partir d'une simple adresse IP privée, d'une passerelle courante ou d'autres caractéristiques réseau partagées.

La permission Android `ACCESS_NETWORK_STATE` utilisée pour cette continuité est une permission de lecture de l'état des réseaux. Elle permet notamment de reconnaître le type de connexion active, mais ne révèle pas à elle seule une position. La permission `INTERNET`, distincte, n'est utilisée par le moteur de localisation que pour le repli réseau explicitement activé décrit plus haut.

Cette détection du lieu de confiance n'est jamais utilisée pour une commande sous contrainte. En mode sous contrainte, la réponse est construite à partir des coordonnées de repli enregistrées à l'avance. Le chemin de localisation réelle, la détection du Wi-Fi actuel et le repli réseau ne sont pas appelés pour cette commande. La phrase sous contrainte doit rester distincte de toutes les phrases normales configurées pour les différents contacts.

## Sauvegarde chiffrée locale

VeVak permet d'exporter gratuitement sa configuration vers un fichier `.vvk` choisi via le sélecteur de documents Android. Aucun serveur VeVak n'est utilisé pour cette opération.

Le contenu en clair de cette configuration peut inclure des données sensibles (numéros, phrases, empreinte Wi-Fi durable, coordonnées de repli) ainsi que le choix d'activer ou non l'estimation réseau. Avant écriture, VeVak chiffre et authentifie le contenu avec AES-GCM à l'aide d'une clé dérivée du mot de passe fourni par l'utilisateur via PBKDF2-HMAC-SHA256, avec sel aléatoire et IV aléatoire. Le mot de passe n'est pas enregistré par VeVak. En mode Wi-Fi limité à la session, la sauvegarde peut contenir le marqueur indiquant que ce mode était choisi, mais jamais l'identifiant réel de la session réseau courante.

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

Voir aussi [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) pour les règles destinées à limiter les détournements de VeVak comme outil de surveillance coercitive, et [`docs/location-resolution-0.3.3.md`](docs/location-resolution-0.3.3.md) pour le détail de l'ordre de résolution et du protocole de test ON/OFF.
