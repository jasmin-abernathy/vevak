# Manifeste du kit Android VeVak v4

## Code

- application Kotlin / Compose / Material 3 ;
- moteur SMS local ;
- contrôle du contact autorisé ;
- limitation des demandes ;
- localisation FOSS avec `LocationManager` ;
- localisation Play facultative avec Fused Location Provider ;
- cache récent avant position unique ;
- tests unitaires existants.

## Écoconception

- aucune permission Internet ;
- aucun compte ou serveur requis ;
- aucune publicité ou télémétrie ;
- aucune localisation périodique ;
- WorkManager absent du chemin critique ;
- API 26 minimum ;
- R8 et `shrinkResources` en release ;
- budgets JSON ;
- scripts de vérification et mesure ;
- documentation et rapport type.

## GitHub

- roadmap ;
- labels et milestones ;
- Project recommandé ;
- formulaires d'issues ;
- modèle de pull request ;
- 18 issues préparées ;
- scripts GitHub CLI.

## Limite de validation

Le kit a été validé statiquement et les archives ont été contrôlées. Un build
Android complet doit encore être exécuté dans Android Studio avec le SDK et le
Wrapper officiels.
