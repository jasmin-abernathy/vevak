# VeVak — retours à intégrer avant la version finale

Cette note complète l'issue #22 et doit être relue avant la PR finale afin d'éviter toute régression.

## Phrase-clé dans un SMS naturel

La phrase-clé d'un contact doit déclencher VeVak même si le SMS contient d'autres mots avant ou après.

Comportement attendu :
- correspondance de type « contient » et non égalité stricte / début de chaîne ;
- comparaison toujours insensible à la casse ;
- conservation de la normalisation actuelle des espaces insécables et apostrophes typographiques ;
- fonctionnement identique pour une demande normale et pour le contact ciblé par le mode protection ;
- éviter les faux positifs à l'intérieur d'un autre mot (ex. une clé « ok » ne doit pas matcher « booking ») ;
- ne rien changer à l'autorisation de l'expéditeur, aux quotas anti-suivi ni au choix de la méthode de localisation.

Exemples acceptés :
- clé : `position maintenant` ; SMS : `Salut, position maintenant si tu peux, merci.`
- clé : `où es-tu ?` ; SMS : `Coucou, OÙ ES-TU ? merci`

Tests de non-régression ajoutés sur le parseur, le routage normal et le routage du contact protégé.
