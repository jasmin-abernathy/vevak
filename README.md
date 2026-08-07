# VeVak

VeVak est une application Android native qui répond par SMS à une demande de
localisation envoyée par un contact autorisé.

## Positionnement

- Kotlin, Jetpack Compose et Material 3 ;
- fonctionnement local, sans compte ni serveur VeVak ;
- aucune permission Internet ;
- aucune publicité, aucun pisteur et aucune télémétrie ;
- variante `foss` canonique, destinée à la distribution libre et à F-Droid ;
- variante `play` facultative avec Fused Location Provider ;
- position en cache utilisée avant toute nouvelle activation des capteurs ;
- position unique limitée à 8 secondes lorsque le cache ne suffit pas ;
- aucune boucle périodique de localisation ;
- aucune dépendance à WorkManager ou à un serveur dans le chemin critique.

## État du code

Le dépôt contient déjà :

- le moteur de réception et de réponse SMS ;
- la vérification du contact autorisé ;
- la comparaison normalisée de la phrase de déclenchement ;
- la limitation des demandes ;
- la stratégie `cache récent -> position unique -> secours ancien facultatif` ;
- les variantes de localisation `foss` et `play` ;
- l'onboarding ;
- le diagnostic expurgé ;
- des tests unitaires de logique pure.

Le travail prioritaire est désormais la **stabilisation sur un vrai environnement
Android**, les tests instrumentés, les mesures d'écoconception et la préparation
d'un build reproductible.

## Builds

La variante libre est la référence :

```bash
./gradlew testFossDebugUnitTest
./gradlew assembleFossDebug
./gradlew assembleFossRelease
```

La variante Google reste facultative :

```bash
./gradlew testPlayDebugUnitTest
./gradlew assemblePlayDebug
```

Le Gradle Wrapper officiel doit être généré une fois comme expliqué dans
[`docs/GRADLE_WRAPPER_SETUP.md`](docs/GRADLE_WRAPPER_SETUP.md).

## Écoconception

Les limites techniques sont documentées dans :

- [`docs/ECODESIGN.md`](docs/ECODESIGN.md)
- [`docs/ECODESIGN_MEASUREMENT.md`](docs/ECODESIGN_MEASUREMENT.md)
- [`docs/FEATURE_ECO_REVIEW.md`](docs/FEATURE_ECO_REVIEW.md)
- [`ECODESIGN_BUDGETS.json`](ECODESIGN_BUDGETS.json)

## Roadmap et contributions

- feuille de route : [`ROADMAP.md`](ROADMAP.md)
- guide de contribution : [`CONTRIBUTING.md`](CONTRIBUTING.md)
- installation des labels, milestones et issues :
  [`docs/GITHUB_ROADMAP_SETUP.md`](docs/GITHUB_ROADMAP_SETUP.md)

Les issues `help wanted` sont ouvertes aux contributions. Les issues
`good first issue` sont volontairement limitées à des tâches non critiques.

## Avertissement

VeVak n'est pas un service d'urgence. La livraison d'un SMS, les permissions,
le modem, Android ou les restrictions du constructeur peuvent empêcher une
réponse. L'application doit être testée régulièrement et ne doit jamais être
présentée comme une garantie de sécurité.

## Licence

Code : `GPL-3.0-or-later`.

Documentation : sauf mention contraire, `CC-BY-SA-4.0` dans le dépôt
`vevak-docs`.
