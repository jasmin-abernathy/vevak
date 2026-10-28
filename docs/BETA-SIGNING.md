# Signatures stables et tests Android — VeVak

## Pourquoi l'ancienne APK n'était pas mise à jour

`assembleFossDebug` utilise une clé Android **debug locale au runner**. Chaque runner GitHub Actions peut produire une nouvelle identité de signature. L'APK de l'ancien build et la nouvelle APK `com.vevak.app.debug` ne peuvent donc pas se remplacer si leurs certificats diffèrent. **Aucune clé privée ne peut être récupérée à partir des APK compilées.**

Ne désinstalle pas VeVak si tu veux préserver ses données : une désinstallation les efface. Cette correction ne peut pas restaurer la clé privée debug utilisée précédemment.

## Nouvelle stratégie de distribution

- `fossDebug` : build technique de CI, signé avec une clé debug potentiellement différente à chaque exécution. **Ne plus partager comme bêta mise à jour.**
- `fossBeta` : vraie édition de test, `com.vevak.app.beta`, signée avec une **clé de test persistante**, indépendante des clés Play Store et F-Droid. Elle peut coexister avec `com.vevak.app.debug`. Les futures `fossBeta` signées avec cette **même** clé peuvent être installées comme mises à jour sur cette édition bêta.
- `fossRelease` : édition officielle F-Droid, sans clé de test.
- `playRelease` : édition Play avec le mécanisme de signature Play indépendant.

**Attention** : si la version debug et `fossBeta` sont toutes deux actives et autorisées, elles peuvent répondre toutes les deux à un SMS. **Révoque les contacts autorisés dans la version que tu n'utilises pas** pendant les tests. Les données de la version debug ne sont pas migrées vers la nouvelle édition bêta.

## Initialiser une seule fois la signature de test

Depuis un PC avec **JDK 17+**, `openssl` et `gh` (GitHub CLI) connecté au compte ayant accès à `jasmin-abernathy/vevak`, sur une copie locale du dépôt :

```bash
bash scripts/setup-beta-signing.sh --upload
```

Le script crée et conserve localement :

- `.private/vevak-beta-signing.p12` — **clé privée de signature** ;
- `.private/vevak-beta-signing.password` — son mot de passe.

**Sauvegarde ces deux fichiers hors ligne et de manière chiffrée.** Ils sont exclus par `.gitignore` et ne doivent jamais être joints à une issue, une PR, un mail ni une conversation. En cas de perte, il ne sera plus possible de mettre à jour les APK déjà installées avec cette signature.

Le script charge cinq secrets chiffrés dans **GitHub → Settings → Secrets and variables → Actions** :

`VEVAK_BETA_KEYSTORE_BASE64`, `VEVAK_BETA_STORE_PASSWORD`, `VEVAK_BETA_KEY_ALIAS`, `VEVAK_BETA_KEY_PASSWORD`, `VEVAK_BETA_CERT_SHA256`.

Le cinquième est l'empreinte publique attendue de la clé. La CI refuse de distribuer un APK dont l'empreinte ne correspond pas. Pour recharger les mêmes secrets sans changer la signature, relance le script avec les deux fichiers locaux intacts. **Ne crée jamais une autre clé pour une édition déjà diffusée**.

## Compiler et récupérer une APK signée

Uniquement sur une **branche de confiance** validée par le propriétaire :

```bash
gh workflow run android-ci.yml -R jasmin-abernathy/vevak \
  --ref feature/cellular-fallback-local-memory \
  -f signed_beta_test=true
```

Le workflow doit être déclenché **manuellement** par `jasmin-abernathy`. Il lance d'abord les tests, la compilation, les lints et les contrôles de confidentialité standards, puis compile `assembleFossBeta` avec la signature de test. Les secrets ne sont **jamais transmis aux PR automatisées**. L'artefact `vevak-foss-beta-signed-<sha>` contient l'APK et son fichier `SHA256SUMS.txt`. La CI échoue si un des secrets manque ou si l'identité du signataire est différente de l'empreinte attendue.

La publication automatique historique de `VeVak-beta.apk` **signé avec une clé debug tournante** a été supprimée du workflow de cette branche. Les anciennes releases restent disponibles, mais ne constituent plus une chaîne de mises à jour fiable.

## Play Protect

Google peut bloquer les APK installées depuis le navigateur ou un gestionnaire de fichiers qui déclarent `RECEIVE_SMS`. **Une signature stable n'annule pas cette protection.** VeVak doit conserver `RECEIVE_SMS` pour les demandes de position par SMS. Pour les tests complets, installer depuis Android Studio/ADB reste une solution de développement, sans désactiver globalement Play Protect. Pour une distribution publique sans friction, examiner la procédure d'appel de Play Protect et les règles applicables à Google Play / F-Droid.

Référence officielle : https://developers.google.com/android/play-protect/warning-dev-guidance
