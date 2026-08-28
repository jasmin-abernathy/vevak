# Vie privée

VeVak n'utilise aucun compte VeVak, aucune publicité, aucun pisteur et aucun serveur applicatif obligatoire.

Les réglages restent dans le stockage privé Android de l'application : numéro autorisé, nom facultatif, phrase normale, options de réponse, période d'autorisation et, si l'utilisateur l'active, phrase sous contrainte et coordonnées du lieu de repli. Les sauvegardes Android et transferts automatiques sont désactivés.

Le contenu des SMS, les coordonnées, les numéros de téléphone et les phrases ne sont pas écrits dans les journaux par le code VeVak. Le diagnostic exportable est volontairement expurgé de ces données.

VeVak conserve localement au maximum 20 résultats récents de demandes (horodatage + résultat générique). Cet historique ne contient ni coordonnées, ni contenu SMS, ni numéro, ni phrase et ne permet pas de distinguer une réponse normale d'une réponse utilisant le lieu de repli.

Une demande reconnue doit pouvoir produire une notification locale avant qu'une position soit envoyée automatiquement. Si les notifications VeVak sont désactivées ou interdites, aucune position automatique n'est envoyée.

En mode sous contrainte, la réponse est construite à partir des coordonnées de repli enregistrées à l'avance. Le chemin de localisation réelle n'est pas appelé pour cette commande.

Les SMS reçus peuvent être visibles dans l'application de messagerie du téléphone et sont traités par le réseau/opérateur. VeVak envoie ses réponses via les API SMS Android ; il ne faut pas supposer que toutes les applications de messagerie afficheront systématiquement une copie du SMS sortant.

La variante `foss` n'utilise pas Google Play Services. La variante `play` utilise le fournisseur de localisation Google ; elle reste isolée dans sa flavor dédiée et n'est pas destinée au dépôt principal F-Droid.

Voir aussi [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md) pour les règles destinées à limiter les détournements de VeVak comme outil de surveillance coercitive.
