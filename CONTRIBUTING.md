# Contribuer à VeVak

VeVak traite des SMS, des contacts autorisés, de la localisation et de la
sécurité personnelle. Une contribution doit donc être sobre, vérifiable et
prudente.

## Choisir une contribution

1. Consultez [`ROADMAP.md`](ROADMAP.md).
2. Recherchez une issue existante.
3. Privilégiez `help wanted`.
4. Utilisez `good first issue` pour une première contribution.
5. Commentez l'issue avant de commencer.
6. Attendez la confirmation d'un mainteneur.

Les tâches critiques liées à l'expéditeur SMS, aux permissions, à la
localisation en arrière-plan ou à l'authentification ne sont généralement pas
classées `good first issue`.

## Contribuer à une version iOS

Une version iOS est désormais ouverte à l'étude. Avant toute contribution Apple/Swift, lisez [`IOS-PORT.md`](IOS-PORT.md) et l'issue dédiée.

Le but n'est pas de recopier naïvement le fonctionnement Android : iOS impose un modèle différent pour la réception et l'envoi des SMS. Le premier prototype utile doit donc viser un **partage manuel de position natif**, avec confirmation locale, Core Location et l'interface système d'envoi de message.

Ne déposez pas encore de projet Xcode au milieu de la structure Android. Une fois une architecture iOS validée et un contributeur prêt à développer réellement le prototype, un dépôt `vevak-ios` séparé pourra être créé.

Les règles de confidentialité, d'anti-coercition, de sobriété et d'absence de suivi furtif restent communes aux deux plateformes.

## Règles d'architecture

- préserver la variante `foss` sans Google Play Services ;
- ne pas ajouter de permission Internet au cœur de VeVak ;
- ne pas introduire de compte ou serveur obligatoire ;
- ne pas utiliser WorkManager dans le chemin critique SMS/localisation ;
- ne pas ajouter de polling ou de localisation périodique ;
- maintenir le flux `cache -> position unique -> secours ancien facultatif` ;
- garder Compose hors du domaine métier ;
- privilégier les API Android natives et les dépendances déjà présentes.

## Fonctions payantes et monétisation

Avant toute contribution liée à une fonction payante, lisez [`MONETIZATION.md`](MONETIZATION.md) et remplissez [`docs/PAID_FEATURE_REVIEW.md`](docs/PAID_FEATURE_REVIEW.md).

Règles obligatoires :

- le cœur de sécurité reste utilisable sans achat ;
- aucune publicité, aucun tracker et aucune télémétrie ne sont ajoutés comme alternative au paiement ;
- les contrôles de révocation, consentement, anti-suivi, confidentialité et suppression locale ne sont jamais derrière un paywall ;
- la variante `foss` doit continuer à compiler sans SDK propriétaire de paiement ;
- les dépendances de boutique restent dans le source set / la configuration Gradle de la plateforme concernée ;
- le code client premium peut rester public : l'obscurité du code n'est pas une frontière de sécurité ;
- un dépôt privé n'est justifié que pour un service réellement séparé, par exemple un futur relais hébergé, pas pour cacher du code client GPL ;
- toute fonction payante sensible nécessite la même revue d'abus/coercition qu'une fonction gratuite.

## Revue d'écoconception

Toute fonction nouvelle doit documenter :

- le besoin utilisateur ;
- l'alternative sans nouvelle fonction ;
- les permissions ;
- l'activité réseau et en arrière-plan ;
- le poids ajouté à l'APK ;
- l'impact CPU, mémoire et batterie ;
- la compatibilité API 26 et appareils anciens ;
- la présence dans la variante `foss` ;
- la stratégie de retrait.

Utilisez [`docs/FEATURE_ECO_REVIEW.md`](docs/FEATURE_ECO_REVIEW.md).

## Données sensibles

Ne jamais journaliser ou publier :

- numéro de téléphone ;
- nom d'un contact ;
- phrase de déclenchement ;
- contenu d'un SMS ;
- coordonnées ;
- nom d'un réseau de confiance ;
- diagnostic non expurgé.

## Tests

Selon le changement :

```bash
./gradlew testFossDebugUnitTest
./gradlew assembleFossDebug
./gradlew testPlayDebugUnitTest
./gradlew assemblePlayDebug
python3 scripts/verify-foss-boundary.py
python3 scripts/verify-ecodesign-boundaries.py
python3 scripts/check-sensitive-logs.py
```

## Commits

Exemples :

```text
fix(sms): preserve receiving subscription id
test(location): cover stale fallback
docs(ecodesign): record baseline measurements
perf(apk): remove unused dependency
```

## Licence

Les contributions acceptées sont distribuées sous `GPL-3.0-or-later`.
Chaque contributeur conserve ses droits d'auteur.