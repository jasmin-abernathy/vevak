# VeVak 0.3.8 — contrat de localisation unifié

Cette révision remplace l'empilement de correctifs successifs par quelques invariants explicites et testables.

## 1. Une demande automatique ne dépend pas d'une localisation permanente

VeVak ne suit pas le téléphone en continu et ne demande pas que la localisation Android reste activée en permanence.

Quand l'application est réellement ouverte et qu'une source de coordonnées est disponible, VeVak tente ponctuellement d'actualiser sa mémoire locale. Une demande SMS autorisée ultérieure peut donc utiliser cette dernière position même si Android ne permet plus d'en acquérir une nouvelle à cet instant.

L'autorisation `ACCESS_BACKGROUND_LOCATION` n'est plus une condition de validation dans l'interface. Le fonctionnement nominal repose sur une acquisition opportuniste et une mémoire locale, pas sur un suivi périodique.

## 2. Contrat de la phrase-clé

Pour une demande SMS normale et autorisée, le resolver suit cet ordre :

1. point Android local récent ou obtenu ponctuellement si Android le permet au moment de la demande ;
2. lieu de confiance actuellement reconnu, par exemple « Maison » ;
3. estimation réseau/IP fraîche, uniquement si l'utilisateur a explicitement activé cette source ;
4. dernière coordonnée mémorisée, quelle que soit sa méthode d'obtention et quel que soit son âge ;
5. « indisponible » seulement si aucune source exploitable n'a jamais fourni d'information.

Chaque position mémorisée conserve son origine et son ancienneté. Une estimation réseau reste donc présentée comme une zone approximative, même après un redémarrage.

## 3. Deux mémoires pour ne pas casser les fonctions déjà améliorées

VeVak conserve séparément :

- la dernière position issue de n'importe quelle source légitime, utilisée par la phrase-clé automatique ;
- la dernière position réelle/locale, utilisée par le partage manuel et le raccourci d'urgence.

Ainsi, activer l'estimation réseau peut améliorer la réponse automatique sans écraser le dernier vrai point nécessaire aux fonctions explicites introduites précédemment.

Les coordonnées du mode de protection sous contrainte ne sont jamais enregistrées dans ces mémoires et ce mode continue de contourner entièrement le resolver normal.

## 4. Informations choisies par l'utilisateur

Le formatteur respecte de nouveau les options enregistrées :

- niveau/état de batterie seulement si l'option est activée ;
- précision/rayon seulement si l'option est activée ;
- fournisseur de lien cartographique choisi par l'utilisateur ;
- origine approximative et ancienneté toujours indiquées quand elles sont nécessaires pour ne pas présenter une estimation comme un GPS actuel.

## 5. Phrase-clé tolérante à la casse et à la typographie SMS

La comparaison est volontairement insensible aux majuscules/minuscules et indépendante de la langue du téléphone. Elle normalise aussi :

- espaces répétés ;
- espaces insécables courants ;
- apostrophes typographiques courantes ;
- formes Unicode compatibles via NFKC.

Exemples qui doivent être équivalents :

- `position maintenant` / `POSITION MAINTENANT` ;
- `OÙ ES-TU ?` / `où es-tu ?` ;
- espaces ordinaires ou insécables autour des mots.

## 6. Protections qui ne doivent pas régresser

Les modifications de localisation ne doivent pas modifier les invariants suivants :

- limitation anti-suivi des demandes automatiques ;
- autorisations de contacts limitées dans le temps et révocables ;
- partage manuel et urgence fondés sur le dernier point réel, pas sur une estimation IP ;
- mode de protection sous contrainte isolé du resolver normal ;
- aucune journalisation visible de coordonnées ;
- aucune promesse de livraison SMS ou de disponibilité d'un service d'urgence.

Les tests unitaires couvrent désormais explicitement la casse de la phrase-clé, la typographie SMS, les options de batterie/précision et la séparation « toute source » / « point réel » afin qu'un futur correctif ne puisse plus les écraser silencieusement.
