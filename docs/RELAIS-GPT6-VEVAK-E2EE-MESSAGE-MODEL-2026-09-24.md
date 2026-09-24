# Relais GPT-6 — VeVak E2EE message model v0 — 2026-09-24

## Branche

- Base immuable : work/crypto-relay-poc au SHA b8a732985fa317af257f3e0f7ec20a287020cf54.
- Branche isolée : work/e2ee-message-model-v0.
- Aucun changement Android/iOS, APNs, SMS, Core Location ou serveur de production.

## Lot préparé

Le lot ajoute un mini-crate Rust indépendant crypto-poc/message-model-v0 afin de ne pas raccorder prématurément le protocole aux POC mobiles.

Le modèle v0 contient dans le plaintext destiné à Olm : version, relation, appareils émetteur/destinataire, empreintes d'identité, message_id, dates, kind, body et request_id pour les réponses. Le parseur est borné à 16 Kio et rejette version inconnue, JSON malformé, champs manquants/dupliqués/inconnus, identifiants invalides, fenêtre temporelle invalide, TTL > 5 min et corps incohérent avec le type.

La validation entrante confronte ensuite le message déchiffré à la relation locale épinglée, aux métadonnées externes émetteur/destinataire, à la boîte locale et à l'identifiant de session Olm réellement utilisé avant tout effet métier.

L'anti-rejeu est un contrat injecté ReplayStoreV0::record_once. Sa documentation impose une persistance à travers les redémarrages et une insertion atomique. Le seul store mémoire vit dans les tests ; il n'est pas proposé comme stockage de production.

## Tests synthétiques

Le corpus couvre : round-trip stable, mauvaise identité, substitution destinataire/relation/type, substitution des métadonnées de transport/boîte/session, expiration et TTL excessif, doublon, réponse sans requête en attente, réponse corrélée valide, version future, champ manquant/dupliqué/inconnu, taille excessive et messages valides livrés dans un ordre différent.

Les coordonnées de test sont synthétiques (0,0) et aucun secret, numéro, contact ou emplacement réel n'est inclus.

## Choix à revoir par GPT-6

1. Le corps de réponse utilise provisoirement des entiers fixes latitude_e7 / longitude_e7 et accuracy_m pour éviter les ambiguïtés de flottants JSON. Confirmer ou modifier avant intégration.
2. La tolérance d'horloge future est provisoirement de 120 s ; le TTL maximal reste 300 s conformément au threat model v0.
3. Le parseur peut vérifier format/longueur mais ne peut pas prouver l'entropie aléatoire d'un relation_id ou message_id : le générateur devra garantir au moins 128 bits.
4. decrypted_session_id est fourni par l'adaptateur appelant et comparé à la session épinglée. Vérifier dans vodozemac 0.11.0 que l'identifiant retenu est stable après pickle/restauration et adapté comme binding applicatif avant raccordement.
5. Le lot ne décide pas encore la persistance concrète de l'anti-rejeu, la consommation concurrente des prekeys, ni les transitions de consentement. Ces points restent derrière le gate GPT-6 prévu.

## Validation attendue

Le workflow dédié exécute les tests host puis vérifie la compilation Rust Android ARM64 et iPhone ARM64 sur le même SHA. Ne considérer le lot validé qu'après réussite des runs correspondant au SHA final.
