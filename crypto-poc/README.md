# VeVak crypto POC

Ce dossier est volontairement séparé du code Android et du prototype iOS. Il sert à répondre à une seule question :

Peut-on obtenir un canal VeVak ↔ VeVak asynchrone, chiffré de bout en bout et maintenable, sans inventer notre propre protocole cryptographique ?

## Candidat testé en premier : vodozemac / Olm

vodozemac est l’implémentation Rust moderne d’Olm utilisée dans l’écosystème Matrix. La version testée est 0.11.0, publiée en septembre 2026, sous Apache-2.0.

Le POC teste les propriétés dont VeVak a réellement besoin :

- identité cryptographique locale par appareil ;
- one-time prekeys publiables sans clé privée ;
- création d’une session alors que le destinataire n’est pas simultanément en ligne ;
- échange chiffré Alice ↔ Bob ;
- signature d’un bundle public de prekeys et détection d’altération ;
- sérialisation/restauration du compte et du ratchet ;
- anti-rejeu et expiration au niveau de l’enveloppe VeVak ;
- changement d’identité après réinstallation détectable ;
- compilation Rust pour Android ARM64 et iPhone ARM64.

Le POC ne connecte aucun serveur, APNs, SMS, position réelle ou carnet d’adresses.

## Pourquoi pas matrix-sdk-crypto directement

matrix-sdk-crypto est production-ready et sans I/O réseau, mais c’est une machine de chiffrement Matrix complète. Sa version 0.19.1 dépend elle-même de vodozemac 0.11.0.

Pour VeVak, commencer par le ratchet sous-jacent permet de mesurer si les primitives haut niveau d’Olm couvrent le besoin sans embarquer les types, événements et règles Matrix dont VeVak n’a pas besoin.

Si ce POC oblige à recréer trop de logique de sécurité déjà présente dans matrix-sdk-crypto, cette conclusion sera un motif pour remonter d’un niveau plutôt que pour ajouter de la crypto maison.

## Ce que ce POC ne décide pas encore

Un résultat vert ne signifie pas « crypto terminée ». Il faudra encore décider :

- format exact des invitations ;
- stockage Keychain / Android Keystore ;
- service minimal de distribution des clés publiques ;
- rotation/remplissage des one-time keys ;
- stratégie multi-appareil et récupération ;
- vérification de clé lisible par l’utilisateur ;
- protocole de notifications/APNs ;
- threat model stalking/coercition final ;
- audit avant mise en production.

Aucune clé privée du POC n’est destinée à un serveur.
