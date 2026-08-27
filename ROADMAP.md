# Feuille de route VeVak

Cette feuille de route présente la direction du projet. Elle ne constitue pas une promesse de dates : les priorités peuvent évoluer après des tests, une revue de sécurité, une mesure de ressources ou une évolution d'Android.

## Principes permanents

- la variante `foss` reste canonique ;
- le cœur reste utilisable sans Internet, compte ou serveur VeVak ;
- aucune publicité, aucun pisteur et aucune télémétrie ;
- aucune localisation périodique dans le cœur actuel ;
- le cache est consulté avant l'activation d'un capteur ;
- le chemin critique SMS/localisation ne dépend ni de WorkManager ni d'un serveur ;
- aucune nouvelle dépendance sans justification de fonction, licence et poids ;
- aucune IA sans besoin utilisateur démontré ;
- aucun usage furtif ou non consenti ;
- aucune promesse de remplacement des services d'urgence.

## Topologie des dépôts Android

La direction retenue en août 2026 est de séparer clairement les variantes afin de préserver la frontière FOSS :

- `VeVaK-android-FOSS` — version canonique libre, sans dépendance propriétaire inutile ;
- `VeVaK-android-PlayStore` — variante de distribution Play, avec composants propriétaires uniquement lorsqu'ils sont nécessaires et explicitement isolés ;
- `VeVaK-android-Custom` — intégrations ou besoins spécifiques, séparés du cœur canonique.

Le dépôt public `jasmin-abernathy/vevak` reste la référence de l'état actuellement implémenté tant que cette migration n'est pas terminée.

## 0.3.x - Stabilisation

Le moteur SMS et la localisation sont déjà implémentés. Les priorités sont :

- exécuter et documenter un build Android complet installable ;
- corriger les écarts de compilation réels ;
- exécuter les tests unitaires des variantes ;
- ajouter des tests instrumentés du `SmsReceiver` ;
- tester la réception et la réponse sur plusieurs constructeurs ;
- tester les appareils multi-SIM / eSIM ;
- rendre le comportement de réponse multi-SIM déterministe et explicite ;
- vérifier le comportement écran éteint et sous économie d'énergie ;
- vérifier le build reproductible de la variante `foss` ;
- mesurer les budgets d'écoconception de référence ;
- compléter les métadonnées de publication F-Droid.

## 0.4 - Fiabilité et accessibilité

- ajouter un test guidé de bout en bout : SMS réel → validation → localisation → réponse ;
- améliorer les diagnostics et les messages d'erreur ;
- identifier les restrictions constructeur / batterie et proposer des actions correctives compréhensibles ;
- auditer TalkBack, contraste, taille de texte et zones tactiles ;
- documenter les restrictions propres aux constructeurs ;
- confirmer le fonctionnement sur un appareil ancien ou d'entrée de gamme ;
- ajouter un historique local minimal uniquement si les tests montrent qu'il est nécessaire, désactivable et sans corps de SMS ni coordonnées ;
- étudier un import/export chiffré uniquement si le besoin est confirmé.

## Après stabilisation 0.4 - extensions de sécurité personnelle

### SOS sortant manuel

Étudier puis prototyper un flux explicite permettant à l'utilisateur d'envoyer lui-même une alerte à son contact de confiance.

Contraintes :

- déclenchement volontaire ;
- confirmation ou compte à rebours annulable ;
- aucun appel automatique aux services d'urgence ;
- message honnête sur l'absence de garantie de livraison ;
- réutilisation du moteur local/SMS existant lorsque possible ;
- aucune télémétrie ni stockage centralisé requis.

Les raccourcis, tuiles rapides ou appels complémentaires ne seront ajoutés qu'après validation séparée de leur utilité et du risque de faux déclenchement.

## 0.5+ - sécurité et usages avancés

- étudier une authentification de requête plus robuste que numéro + phrase seule tout en restant compatible avec SMS et usage sans Internet ;
- étudier plusieurs contacts autorisés avec droits explicites et limités ;
- valider les besoins réels d'import/export chiffré et d'historique local minimal ;
- poursuivre les améliorations multi-SIM sur la base de tests réels.

## Module de récupération d'appareil - étude séparée

Des fonctions utiles inspirées de Find Hub / Find Device peuvent être étudiées **dans VeVak plutôt que dans une application séparée**, à condition de conserver le modèle local-first et le consentement explicite.

Candidats à étudier :

- faire sonner l'appareil ;
- obtenir un état de batterie / disponibilité ;
- afficher un message explicite sur l'écran verrouillé lorsque la plateforme le permet ;
- récupérer la dernière position connue ;
- exposer des informations de préparation réseau/localisation utiles au diagnostic ;
- éventuellement utiliser un relais chiffré et facultatif lorsque le SMS direct est insuffisant.

Garde-fous obligatoires :

- aucune infrastructure centrale obligatoire pour le cœur VeVak ;
- aucune localisation permanente ou cachée ;
- fonctions distantes explicitement activées, révocables et auditées ;
- verrouillage distant ou autres actions sensibles uniquement après threat model spécifique et validation des limites Android ;
- consommation batterie et dépendances mesurées avant intégration ;
- séparation architecturale nette entre cœur FOSS, composants Play éventuels et extensions Custom.

## Fonctions non engagées dans le cœur actuel

Les éléments suivants restent exploratoires et ne doivent pas être présentés comme livrés ou promis sans nouvelle décision explicite :

- suivi temporaire périodique ;
- déclenchement par secousse ou capteurs permanents ;
- sirène/flash automatiques ;
- photo ou audio liés à un événement ;
- appel automatique aux secours ;
- cloud obligatoire ;
- IA dans le chemin critique.

## 1.0 - Première version stable

La version stable exige :

- tests unitaires, instrumentés et sur appareils réels ;
- revue du modèle de menace ;
- audit des permissions ;
- mesures d'écoconception publiées ;
- build FOSS reproductible ;
- politique de confidentialité conforme au comportement réel ;
- signature et procédure de publication documentées ;
- canal privé de signalement des vulnérabilités ;
- limites et modes d'échec visibles avant activation.

## Contribuer

Les tâches concrètes sont suivies dans les Issues :

- `help wanted` : contribution extérieure recherchée ;
- `good first issue` : tâche délimitée et non critique ;
- `status: needs discussion` : décision requise avant développement ;
- `status: ready` : définition suffisante pour commencer ;
- `needs measurement` : mesure avant/après obligatoire.

Commentez une issue avant de commencer et attendez la confirmation d'un mainteneur.
