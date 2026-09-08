# VeVak 0.3.9 — contrat de non-régression

> Note historique : l'absence totale de `ACCESS_BACKGROUND_LOCATION` a rendu la mise à jour hors écran non fonctionnelle sur Android récent. La 0.3.12 la remplace par une autorisation facultative, strictement réservée au rafraîchissement d'un point explicitement activé. Elle reste inutile au traitement SMS normal.

Cette révision complète la refonte 0.3.8 en transformant plusieurs améliorations historiques en invariants vérifiés par les tests et la CI.

## Phrase-clé

Une phrase normale ou sous contrainte est comparée après normalisation Unicode, harmonisation des apostrophes/espaces SMS et passage en minuscules indépendant de la locale.

Invariant : une phrase configurée en minuscules doit fonctionner si le SMS reçu contient des majuscules, et inversement.

## Dernière position automatique

Une demande automatique valide doit utiliser la meilleure information disponible sans exiger de localisation permanente :

1. position Android récente/actuelle si accessible ;
2. lieu de confiance reconnu ;
3. estimation réseau/IP fraîche si l'option est activée ;
4. dernière coordonnée mémorisée issue de toute source légitime, avec son ancienneté ;
5. indisponible uniquement si aucune information n'existe.

La mémoire ne doit jamais reculer dans le temps lorsqu'un cache Android plus ancien est relu.

## Partage manuel et urgence

Le partage manuel et l'urgence gardent une mémoire séparée du dernier point réel/local. Une estimation IP ne peut pas remplacer ce point. Les coordonnées du mode sous contrainte n'entrent dans aucune mémoire normale.

## Informations de réponse

Les choix utilisateur restent contractuels :

- batterie uniquement si activée ;
- précision/rayon uniquement si activé ;
- fournisseur cartographique sélectionné ;
- ancienneté conservée pour les positions mémorisées ;
- une estimation réseau reste explicitement identifiée comme estimation.

Le reverse geocoding ajouté historiquement est restauré : lorsqu'une adresse approximative est disponible, les réponses normales et les partages manuels peuvent inclure `Adresse approx.`. Son absence ne bloque jamais l'envoi. Le raccourci d'urgence reste compact et n'ajoute pas cette adresse.

## Permissions et suivi

Dans la 0.3.9, `ACCESS_BACKGROUND_LOCATION` ne faisait pas partie du contrat VeVak. Depuis la 0.3.12, elle reste absente du parcours SMS normal mais peut être demandée séparément pour rendre fonctionnel le rafraîchissement facultatif hors écran.

VeVak peut mémoriser opportunément une position lorsque l'application est utilisable et que la localisation Android est accessible. Aucun scheduler périodique, WorkManager récurrent, alarme répétitive ou suivi permanent n'est ajouté.

## Contrôles CI

Les tests verrouillent notamment :

- majuscules/minuscules au niveau du parseur et du routage de commande ;
- typographie SMS courante ;
- options batterie/précision ;
- distinction zone réseau / point réel ;
- restauration de l'adresse approximative ;
- urgence sans adresse ni estimation IP ;
- séparation des mémoires toute-source / réelle ;
- ordre temporel de la mémoire ;
- absence de permission de localisation en arrière-plan.
