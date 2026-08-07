# Feuille de route VeVak

Cette feuille de route présente la direction du projet. Elle ne constitue pas
une promesse de dates : les priorités peuvent évoluer après des tests, une revue
de sécurité, une mesure de ressources ou une évolution d'Android.

## Principes permanents

- la variante `foss` reste canonique ;
- le cœur reste utilisable sans Internet, compte ou serveur VeVak ;
- aucune publicité, aucun pisteur et aucune télémétrie ;
- aucune localisation périodique ;
- le cache est consulté avant l'activation d'un capteur ;
- le chemin critique SMS/localisation ne dépend ni de WorkManager ni d'un serveur ;
- aucune nouvelle dépendance sans justification de fonction, licence et poids ;
- aucune IA sans besoin utilisateur démontré.

## 0.3.x - Stabilisation

Le moteur SMS et la localisation sont déjà implémentés. Les priorités sont :

- exécuter le premier build Android complet ;
- corriger les écarts de compilation réels ;
- exécuter les tests unitaires des deux variantes ;
- ajouter des tests instrumentés du `SmsReceiver` ;
- tester la réception et la réponse sur plusieurs constructeurs ;
- tester les appareils multi-SIM ;
- vérifier le comportement écran éteint et sous économie d'énergie ;
- vérifier le build reproductible de la variante `foss` ;
- mesurer les budgets d'écoconception de référence ;
- compléter les métadonnées de publication F-Droid.

## 0.4 - Fiabilité et accessibilité

- ajouter un test guidé de la protection ;
- améliorer les diagnostics et les messages d'erreur ;
- auditer TalkBack, contraste, taille de texte et zones tactiles ;
- ajouter un historique local minimal uniquement s'il est nécessaire,
  désactivable et sans corps de SMS ni coordonnées ;
- étudier un import/export chiffré uniquement si le besoin est confirmé ;
- documenter les restrictions propres aux constructeurs ;
- confirmer le fonctionnement sur un appareil ancien ou d'entrée de gamme.

## 1.0 - Première version stable

La version stable exige :

- tests unitaires, instrumentés et sur appareils réels ;
- revue du modèle de menace ;
- audit des permissions ;
- mesures d'écoconception publiées ;
- build FOSS reproductible ;
- politique de confidentialité conforme au comportement réel ;
- signature et procédure de publication documentées ;
- canal privé de signalement des vulnérabilités.

## Plus tard, après étude

- plusieurs contacts autorisés ;
- protocole d'authentification plus robuste ;
- import/export chiffré ;
- historique local minimal ;
- améliorations multi-SIM ;
- aucune fonction cloud ou IA sans besoin démontré, séparation architecturale,
  revue de confidentialité et revue d'écoconception.

## Contribuer

Les tâches concrètes sont suivies dans les Issues :

- `help wanted` : contribution extérieure recherchée ;
- `good first issue` : tâche délimitée et non critique ;
- `status: needs discussion` : décision requise avant développement ;
- `status: ready` : définition suffisante pour commencer ;
- `needs measurement` : mesure avant/après obligatoire.

Commentez une issue avant de commencer et attendez la confirmation d'un
mainteneur.
