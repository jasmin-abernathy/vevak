# Vie privée

VeVak n'utilise aucun compte VeVak, aucune publicité, aucun pisteur et aucun serveur applicatif obligatoire.

Les réglages restent dans le stockage privé Android de l'application : numéro autorisé, nom facultatif, phrase normale, options de réponse, période d'autorisation et, si l'utilisateur l'active, phrase sous contrainte et coordonnées du lieu de repli. Si un lieu de confiance Wi-Fi est enregistré, VeVak conserve uniquement une empreinte SHA-256 du nom du réseau ainsi qu'un libellé choisi par l'utilisateur ; le SSID n'est pas stocké en clair. Les sauvegardes Android et transferts automatiques sont désactivés.

Le contenu des SMS, les coordonnées, les numéros de téléphone, les phrases et les identifiants Wi-Fi ne sont pas écrits dans les journaux par le code VeVak. Le diagnostic exportable est volontairement expurgé de ces données.

VeVak conserve localement au maximum 20 résultats récents de demandes (horodatage + résultat générique). Cet historique ne contient ni coordonnées, ni contenu SMS, ni numéro, ni phrase, ni Wi-Fi et ne permet pas de distinguer une réponse normale d'une réponse utilisant le lieu de repli. Les partages manuels sortants ne sont pas ajoutés à cet historique de demandes.

Une demande reconnue doit pouvoir rester localement visible avant qu'une position soit envoyée automatiquement. Si les notifications VeVak sont désactivées ou interdites, aucune position automatique n'est envoyée. Le mode discret temporaire utilise un canal silencieux et sans vibration, mais ne supprime ni la notification de demande dans le volet Android ni la notification persistante indiquant que VeVak est actif.

Le propriétaire du téléphone peut aussi déclencher lui-même un partage manuel de position depuis l'interface de VeVak. Ce flux exige une confirmation locale explicite avant l'acquisition de position et l'envoi du SMS. Si aucune position n'est obtenue, aucun SMS de partage n'est envoyé. Ce flux n'appelle aucun service d'urgence et ne peut pas être déclenché à distance.

Le partage manuel n'affiche pas de notification Android supplémentaire : son état de réussite ou d'échec reste dans l'interface ouverte de VeVak. Cela vaut également lorsque le mode discret temporaire est actif. La notification persistante `VeVak est actif`, lorsqu'elle est requise par le fonctionnement des réponses automatiques, reste indépendante de ce partage manuel.

Pour éviter un choix implicite sur les appareils double-SIM/eSIM, le partage manuel utilise uniquement la SIM définie par Android comme SIM SMS par défaut. Si Android n'en expose aucune, VeVak bloque le partage et demande à l'utilisateur d'en choisir une dans les réglages du téléphone.

Lorsqu'un lieu de confiance Wi-Fi est configuré et que le téléphone est connecté au réseau correspondant, une commande normale peut répondre avec le libellé du lieu sans déclencher de nouvelle acquisition GPS. Si Android ne permet pas de lire le réseau actuel, VeVak revient au chemin de localisation normal. Cette détection n'est jamais utilisée pour une commande sous contrainte.

En mode sous contrainte, la réponse est construite à partir des coordonnées de repli enregistrées à l'avance. Le chemin de localisation réelle et la détection du Wi-Fi actuel ne sont pas appelés pour cette commande.

Les SMS reçus peuvent être visibles dans l'application de messagerie du téléphone et sont traités par le réseau/opérateur. VeVak envoie ses réponses et partages manuels via les API SMS Android ; il ne faut pas supposer que toutes les applications de messagerie afficheront systématiquement une copie du SMS sortant. Lorsqu'Android accepte un SMS pour envoi, VeVak ne présente pas cette étape comme une preuve que le destinataire l'a effectivement reçu.

La variante `foss` n'utilise pas Google Play Services. La variante `play` utilise le fournisseur de localisation Google ; elle reste isolée dans sa flavor dédiée et n'est pas destinée au dépôt principal F-Droid.

Voir aussi [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) pour les règles destinées à limiter les détournements de VeVak comme outil de surveillance coercitive.