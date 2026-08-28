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

## Architecture Android retenue

VeVak reste dans **un seul dépôt Android** (`jasmin-abernathy/vevak`) avec des variantes Gradle clairement isolées.

Le projet contient actuellement :

- `foss` — variante canonique, basée sur les API Android et sans Google Play Services ;
- `play` — variante facultative utilisant Google Play Services uniquement dans son source set / ses dépendances dédiées.

Ce choix évite de dupliquer le cœur SMS, la sécurité, les tests et la documentation dans plusieurs dépôts. Une éventuelle variante ou intégration `custom` ne justifiera un dépôt séparé que lorsqu'un besoin concret, durable et incompatible avec le cœur commun apparaîtra.

## Modèle économique et fonctions payantes

VeVak peut proposer plus tard des fonctions payantes sans fermer le code client. Le modèle retenu est documenté dans [`MONETIZATION.md`](MONETIZATION.md).

Fondations désormais prévues dans l'architecture :

- le dépôt Android reste public et sous GPL-3.0-or-later ;
- le cœur de sécurité reste gratuit : demande/réponse SMS de base, partage manuel, révocation, expiration du consentement, limites anti-suivi, visibilité locale, protections sous contrainte supportées et diagnostics indispensables ;
- les fonctions payantes doivent surtout correspondre à de la commodité, de la configuration avancée ou à un service optionnel ayant un coût réel ;
- le cœur connaît uniquement une abstraction d'`entitlement` publique ;
- `foss` fournit un provider sans dépendance propriétaire et reste la variante canonique ;
- `play` dispose d'un point d'intégration séparé pour un futur Google Play Billing, sans dépendance de paiement tant que cette intégration n'est pas décidée ;
- un éventuel backend payant, par exemple un relais chiffré, ne sera créé que si le service est réellement utile et restera facultatif pour le fonctionnement SMS de base ;
- aucun achat ne doit être présenté avec des dark patterns, de la peur ou une fausse urgence.

Candidats possibles, non promis : plusieurs contacts de confiance, export/import chiffré, personnalisation avancée, relais facultatif ou commodités multi-appareils. Chaque proposition doit passer par [`docs/PAID_FEATURE_REVIEW.md`](docs/PAID_FEATURE_REVIEW.md), une revue d'écoconception et, lorsqu'elle touche à la sécurité, une revue d'abus/coercition.

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

### Partage sortant manuel — prototype implémenté, validation réelle requise

Un flux permet désormais à l'utilisateur d'envoyer lui-même une position unique à son contact de confiance, sans attendre une demande entrante. Ce prototype doit encore être validé sur appareils réels avant d'être considéré comme stabilisé.

Contraintes déjà intégrées :

- déclenchement uniquement depuis l'interface locale de VeVak ;
- confirmation explicite avant toute acquisition de position et tout envoi ;
- annulation possible avant confirmation ;
- aucun appel automatique aux services d'urgence ;
- aucune position envoyée si VeVak ne parvient pas à en obtenir une ;
- message honnête : une remise à l'API SMS Android n'est jamais présentée comme une preuve de livraison ;
- réutilisation du moteur local de localisation et d'envoi SMS ;
- aucune télémétrie ni stockage centralisé ;
- aucune notification Android supplémentaire créée par le partage manuel ; en mode discret temporaire, le résultat reste uniquement dans l'interface de VeVak ;
- la notification persistante `VeVak est actif`, lorsqu'elle est requise par le modèle général, reste indépendante ;
- utilisation uniquement de la SIM définie comme SIM SMS par défaut dans Android ; si aucune SIM par défaut n'est définie, l'envoi est bloqué plutôt que de sélectionner une SIM arbitrairement ;
- aucun déclenchement à distance et aucun envoi périodique.

À valider sur téléphone réel : obtention de position, SMS réellement reçu, erreur réseau, mode discret, SIM unique, double SIM/eSIM, absence de SIM par défaut et comportement constructeur.

Les raccourcis, tuiles rapides ou appels complémentaires ne seront ajoutés qu'après validation séparée de leur utilité et du risque de faux déclenchement.

## 0.4 - Fiabilité et accessibilité

- ajouter un test guidé de bout en bout : SMS réel → validation → localisation → réponse ;
- améliorer les diagnostics et les messages d'erreur ;
- identifier les restrictions constructeur / batterie et proposer des actions correctives compréhensibles ;
- auditer TalkBack, contraste, taille de texte et zones tactiles ;
- documenter les restrictions propres aux constructeurs ;
- confirmer le fonctionnement sur un appareil ancien ou d'entrée de gamme ;
- ajouter un historique local minimal uniquement si les tests montrent qu'il est nécessaire, désactivable et sans corps de SMS ni coordonnées ;
- étudier un import/export chiffré uniquement si le besoin est confirmé.

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
- toute dépendance propriétaire reste confinée à la variante `play` ;
- une extension spécifique reste dans le monorepo tant qu'elle peut être isolée proprement ; un dépôt séparé n'est créé qu'en cas de besoin réel.

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