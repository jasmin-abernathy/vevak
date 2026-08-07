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

## Règles d'architecture

- préserver la variante `foss` sans Google Play Services ;
- ne pas ajouter de permission Internet au cœur de VeVak ;
- ne pas introduire de compte ou serveur obligatoire ;
- ne pas utiliser WorkManager dans le chemin critique SMS/localisation ;
- ne pas ajouter de polling ou de localisation périodique ;
- maintenir le flux `cache -> position unique -> secours ancien facultatif` ;
- garder Compose hors du domaine métier ;
- privilégier les API Android natives et les dépendances déjà présentes.

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
