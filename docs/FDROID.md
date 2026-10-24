# Préparation de VeVak pour le dépôt F-Droid officiel

**État : dossier candidat, pas une soumission ni une publication.** Ne pas annoncer VeVak « disponible sur F-Droid » avant la présence effective dans le catalogue officiel.

## Périmètre

- Source : https://github.com/jasmin-abernathy/vevak
- Licence déclarée : GPL-3.0-or-later (`LICENSE`).
- Variante dédiée : `fossRelease`, sans Google Play Services (`assembleFossRelease`).
- Application ID FOSS : `com.vevak.app`.
- Application ID Play : `vevak.lepotager.org` — **distinct**. Les deux installations ne partagent pas une migration automatique ni une clé de signature. Ne pas modifier les IDs pour faciliter F-Droid : la variante Play est déjà engagée dans le circuit Google Play.
- Candidat actuel : `0.3.15`, `versionCode 18`. C'est une **bêta**, pas une version stable attestée.
- Recette candidate : `packaging/fdroid/com.vevak.app.yml`, destinée au dépôt séparé `fdroid/fdroiddata` sur GitLab.

## Déjà en place

1. Code public et licence GPL.
2. Localisation Android native dans `foss` ; dépendance `play-services-location` limitée à `playImplementation`.
3. Descriptions Fastlane en `fr-FR` et `en-US` et journal de changements.
4. Contrôles de frontières FOSS, confidentialité, tests et lint dans la CI Android.

## Vérifications indispensables avant toute soumission

1. **Fonctionnement réel** : tester sur téléphone physique la réception du SMS, la validation de l'expéditeur, les refus/révocations, la réponse et son quota, les autorisations Android, l'absence de services Google dans la version FOSS, le raccourci d'urgence et son annulation, le redémarrage et le double-SIM si possible. Ne pas présenter un test automatisé comme une preuve de livraison SMS.
2. **Compilation de publication** : lancer la CI `F-Droid preflight`, puis construire `./gradlew --no-daemon testFossDebugUnitTest lintFossDebug assembleFossRelease` sans secrets de signature. Confirmer un APK FOSS release et son `applicationId` `com.vevak.app`.
3. **Contrôle F-Droid** : sur un fork de `fdroiddata`, copier `packaging/fdroid/com.vevak.app.yml` en `metadata/com.vevak.app.yml` ; exécuter `fdroid readmeta`, `fdroid lint com.vevak.app`, puis `fdroid build com.vevak.app` dans l'environnement F-Droid. Corriger les problèmes de dépendances, artefacts précompilés et éventuelles anti-fonctionnalités avant demande de fusion.
4. **Licence des ressources** : vérifier les licences des icônes, graphismes, polices et ressources tierces (notamment les pictogrammes Streamline et leurs attributions). Une ressource libre en CC BY peut exiger une attribution même lorsqu'elle est distribuable.
5. **Fiche réelle** : ajouter un `fastlane/metadata/android/fr-FR/images/icon.png` et un équivalent `en-US` ainsi que des captures d'écran **réelles** de l'interface sous `images/phoneScreenshots/`. Ne pas fabriquer de fausses captures de fonctionnalités.
6. **Description et données** : confirmer la cohérence entre l'application testée, la fiche Fastlane et la politique de confidentialité. Documenter explicitement l'estimation IP via beaconDB (optionnelle), les permissions SMS, la localisation en arrière-plan optionnelle et les limites de VeVak comme outil d'urgence.
7. **Maturité** : les critères publiés de F-Droid demandent généralement trois mois de développement actif, avec des exceptions possibles notamment si l'application a déjà des utilisateurs. Préparer des éléments factuels sur les bêta-testeurs/retours au besoin.
8. **Tag immuable** : après validation, créer un tag `v0.3.15` sur le commit **exact** de la version validée (ou réviser versionName/versionCode/recette pour une nouvelle version). Le tag mouvant `beta` ne convient pas : il est réassigné par la CI des bêtas.
9. **Signatures** : la voie normale F-Droid compile et signe son APK elle-même ; une installation issue du circuit Play ne sera pas mise à jour en place par cet APK. Le mode de publication avec signature du développeur exige un processus reproductible distinct, non activé ici.

## Soumission : étape humaine GitLab

1. Ouvrir https://gitlab.com/fdroid/fdroiddata et créer un fork GitLab avec ton compte.
2. Ajouter la recette validée sous `metadata/com.vevak.app.yml` dans une branche dédiée (avec commit correspondant au tag réellement publié).
3. Ouvrir une **merge request** vers `fdroid/fdroiddata`, titre suggéré : `New App: VeVak (com.vevak.app)`.
4. Joindre les résultats de `fdroid lint`, `fdroid build`, l'identifiant du tag, le lien vers le code et la précision « FOSS flavor, no Google Play Services ». Répondre aux vérifications et éventuelles remarques sur les permissions SMS et les risques de surveillance contrainte.

Guide officiel : https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/

Politique d'inclusion : https://f-droid.org/en/docs/Inclusion_Policy/

## Vigilances sur le packaging

- La recette est **une candidate non testée par fdroidserver** tant que ses validations n'ont pas été effectuées.
- Ne pas introduire `Binaries`/`AllowedAPKSigningKeys` sans disposer d'un véritable processus reproductible et d'un APK FOSS release signé avec la bonne clé.
- Ne pas modifier à la légère `applicationId`, `versionCode`, provenance des dépendances ou signature après la première publication : les mises à jour Android en dépendent.
- Une fois la première version acceptée, incrémenter les codes et publier de nouveaux tags fixes pour la mise à jour automatique.
