# Vie privée

VeVak n'utilise aucun compte VeVak, aucune publicité, aucun pisteur et aucun serveur applicatif obligatoire.

Les réglages restent dans le stockage privé Android de l'application : numéros autorisés, noms facultatifs, phrases normales, options de réponse, périodes d'autorisation et, si l'utilisateur l'active, phrase sous contrainte et coordonnées du lieu de repli. Chaque contact de confiance dispose de sa propre autorisation locale et de sa propre expiration. Les sauvegardes Android et transferts automatiques de l'application restent désactivés.

## Lieu de confiance Wi-Fi

VeVak peut reconnaître un lieu comme « Maison » sans lancer une nouvelle acquisition GPS. Il privilégie la preuve locale la plus forte qu'Android accepte d'exposer :

- lorsque le SSID connecté est lisible, VeVak conserve uniquement son empreinte SHA-256 ; le nom du Wi-Fi n'est jamais enregistré en clair ;
- lorsque le SSID est masqué mais qu'Android expose un préfixe IPv6 global et une passerelle IPv6 locale suffisamment discriminants via `LinkProperties`, VeVak peut calculer une empreinte locale persistante de ces éléments ; les préfixes et adresses bruts ne sont jamais enregistrés ;
- si aucun signal durable suffisamment discriminant n'est disponible, VeVak revient à une reconnaissance limitée à la session réseau Android courante et au démarrage courant du téléphone.

VeVak refuse volontairement de considérer des caractéristiques IPv4 banales telles que `192.168.1.1` comme preuve de « Maison », car elles sont partagées par trop de réseaux et pourraient provoquer un faux positif.

Une reconnexion ou un changement de préfixe IPv6 peut provoquer un faux négatif : dans ce cas VeVak préfère ne pas certifier « Maison » plutôt que d'inventer un lieu.

La détection du lieu de confiance n'est jamais utilisée pour une commande sous contrainte.

## Dernière position locale mémorisée

Pour éviter de dépendre uniquement du cache Android — que le système peut vider lorsque l'utilisateur coupe le bouton global « Localisation » — VeVak conserve dans son stockage privé **une seule dernière position réelle obtenue avec succès**, avec sa précision et son heure d'acquisition.

Cette mémoire locale expire automatiquement après **24 heures**, n'est jamais envoyée à un serveur et n'est pas incluse dans l'export `.vvk`. Une position signalée par Android comme simulée n'est pas mémorisée dans ce cache de résilience.

## Résolution de position et estimation réseau facultative

Les demandes normales, le partage manuel et le test de localisation utilisent le même moteur de résolution. Depuis la 0.3.5, l'ordre de confiance est :

1. lieu de confiance déjà reconnu ;
2. position Android ou cache local récent ;
3. dernière position réelle locale plus ancienne, si ce repli est autorisé ;
4. estimation réseau/IP facultative en dernier recours.

Cette hiérarchie est volontaire : une vraie position locale datée et assortie de sa précision peut être plus utile qu'un centre d'adresse IP « actuel » avec une incertitude de plusieurs dizaines de kilomètres.

L'estimation réseau est **désactivée par défaut**. Lorsqu'elle est activée, VeVak peut envoyer une requête HTTPS IP-only au service public beaconDB (`https://api.beacondb.net/v1/geolocate`). VeVak n'envoie dans cette requête ni SSID, ni BSSID, ni Cell ID, ni numéro de téléphone, ni texte SMS, ni phrase-clé, ni coordonnée locale. Comme pour toute connexion Internet, le serveur distant voit nécessairement l'adresse IP publique utilisée pour la requête ainsi qu'un User-Agent technique indiquant VeVak, sa version et sa variante.

Une estimation obtenue par IP n'est jamais présentée comme une « dernière position connue ». VeVak la présente comme **une zone estimée via le réseau**. Le lien cartographique est centré et dézoomé selon le rayon annoncé par le fournisseur au lieu d'afficher un pin précis. Au-delà de 10 km de rayon, le SMS signale explicitement que la précision est faible.

VeVak ne réduit jamais artificiellement le rayon annoncé par le fournisseur et ne transforme pas cette estimation en position « précise ». Une estimation réseau n'est pas mémorisée comme dernière position réelle.

La permission Android `INTERNET` est présente dans la variante FOSS à partir de la 0.3.3, mais aucune requête de géolocalisation réseau n'est effectuée tant que l'utilisateur n'a pas activé cette option.

## Commande sous contrainte

Le mode sous contrainte est volontairement isolé du moteur normal. Une commande sous contrainte n'inspecte ni le Wi-Fi courant, ni les caches réels, ni la position réelle, et n'effectue jamais la requête beaconDB, même si l'estimation réseau est activée.

La réponse sous contrainte est construite uniquement à partir du lieu de repli préenregistré. La phrase sous contrainte doit rester distincte de toutes les phrases normales configurées.

## Diagnostics expurgés

Le contenu des SMS, les coordonnées, les numéros de téléphone, les phrases et les identifiants Wi-Fi ne sont pas écrits dans les journaux par le code VeVak. Le diagnostic exportable est volontairement expurgé de ces données.

Le laboratoire de localisation peut indiquer des **comptages et états non identifiants** afin de comparer le comportement d'un téléphone avec le bouton Android Localisation activé puis désactivé : nombre de fournisseurs Android connus/actifs, nombre de fournisseurs disposant d'un cache, nombre d'enregistrements cellulaires rendus visibles par Android, identité Wi-Fi lisible ou masquée et type de transport réseau actif. Il ne conserve ni n'affiche les Cell IDs, BSSID, SSID, coordonnées ou identifiants téléphoniques correspondants.

VeVak conserve localement au maximum 20 résultats récents de demandes (horodatage + résultat générique). Cet historique ne contient ni coordonnées, ni contenu SMS, ni numéro, ni phrase, ni Wi-Fi et ne permet pas de distinguer une réponse normale d'une réponse utilisant le lieu de repli. Les partages manuels sortants ne sont pas ajoutés à cet historique de demandes.

## Visibilité des demandes

Une demande reconnue doit pouvoir rester localement visible avant qu'une position soit envoyée automatiquement. Si les notifications VeVak sont désactivées ou interdites, aucune position automatique n'est envoyée. Le mode discret temporaire utilise un canal silencieux et sans vibration, mais ne supprime ni la notification de demande dans le volet Android ni la notification persistante indiquant que VeVak est actif.

Le propriétaire du téléphone peut aussi déclencher lui-même un partage manuel vers un contact VeVak configuré. Ce flux exige une sélection locale du destinataire et une confirmation explicite avant la résolution et l'envoi du SMS.

Pour éviter un choix implicite sur les appareils double-SIM/eSIM, le partage manuel utilise uniquement la SIM définie par Android comme SIM SMS par défaut. Si Android n'en expose aucune, VeVak bloque le partage et demande à l'utilisateur d'en choisir une dans les réglages du téléphone.

## Sauvegarde chiffrée locale

VeVak permet d'exporter gratuitement sa configuration vers un fichier `.vvk` choisi via le sélecteur de documents Android. Aucun serveur VeVak n'est utilisé pour cette opération.

Le contenu en clair peut inclure des données sensibles : numéros, phrases, empreinte de réseau de confiance, coordonnées de repli et choix d'activer ou non l'estimation réseau. Avant écriture, VeVak chiffre et authentifie ce contenu avec AES-GCM à l'aide d'une clé dérivée du mot de passe fourni par l'utilisateur via PBKDF2-HMAC-SHA256, avec sel et IV aléatoires. Le mot de passe n'est pas enregistré par VeVak.

Ne sont jamais exportés :

- l'historique local des demandes ;
- les horodatages d'autorisation active des contacts ;
- l'état temporaire du mode discret ;
- la dernière position mémorisée par le mécanisme de résilience ;
- l'identifiant temporaire de session réseau utilisé pour la continuité du Wi-Fi de confiance.

Après import, tous les contacts restaurés restent désautorisés jusqu'à une nouvelle validation locale.

Les SMS reçus peuvent être visibles dans l'application de messagerie du téléphone et sont traités par le réseau/opérateur. VeVak envoie ses réponses via les API SMS Android ; l'acceptation de l'envoi par Android n'est pas présentée comme une preuve de livraison au destinataire.

La variante `foss` n'utilise pas Google Play Services. La variante `play` utilise le fournisseur de localisation Google et reste isolée dans sa flavor dédiée.

Voir aussi [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) et [`docs/location-resolution-0.3.3.md`](docs/location-resolution-0.3.3.md).
