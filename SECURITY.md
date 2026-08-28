# Sécurité

Signalez une vulnérabilité sans publier de données personnelles ni de scénario exploitable publiquement. Contact : à remplacer par une adresse de sécurité dédiée avant publication.

## Limites connues

- un SMS n'offre pas la confidentialité de bout en bout ;
- l'identité de l'expéditeur repose sur le numéro présenté par le réseau ;
- la réception et la présentation des SMS dépendent aussi d'Android, de l'application de messagerie et de l'opérateur ;
- le mécanisme ne remplace pas un service d'urgence ;
- une personne ayant accès au téléphone déverrouillé peut voir ou modifier une partie de la configuration ;
- aucune application ne peut garantir qu'un consentement a été donné librement lorsqu'une personne subit une coercition hors du téléphone.

## Barrières contre l'usage coercitif

VeVak limite techniquement les demandes automatiques, impose une autorisation locale limitée dans le temps, permet une révocation immédiate, bloque les réponses si la demande ne peut pas être rendue visible localement, conserve un audit minimal sans coordonnées et propose une protection sous contrainte facultative dont le chemin ne consulte jamais la position réelle.

Ces garanties sont détaillées dans [`ABUSE-PREVENTION.md`](ABUSE-PREVENTION.md). Toute fonctionnalité de suivi, récupération d'appareil, capteur distant ou configuration distante doit être réévaluée contre ce document avant intégration.

Voir également `docs/THREAT_MODEL.md`.
