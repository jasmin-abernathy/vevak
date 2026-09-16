# Revue ciblée PR #35 — 13 septembre 2026

Base de cette passe : `8564c4cbe3f4aa5e4640b904cc297a7cecae496d` ; base main : `cda8556e4ad33ef03405b55fc172799f181838bc`. La CI initiale est entièrement verte, y compris tests/builds/lints FOSS et Play et bundle Play (run 34736293032).

## Changements

- La protection renforcée n'apparaît plus dans l'accueil ni dans les réglages ordinaires. Elle se trouve derrière l'entrée neutre « Paramètres supplémentaires », un mot de passe local et une activation explicite. La phrase associée au contact reste inchangée.
- La proposition automatique après plusieurs SMS a été supprimée. La protection renforcée se configure uniquement à la demande dans « Paramètres supplémentaires », derrière un mot de passe local.
- Six icônes Papirus locales GPL-3.0, dont Liste dérivée d'une feuille générique. Sources et conversion reproductible conservées.
- Aperçus limités à 48 dp dans Sécurité et colonne de texte pondérée pour éviter que l'icône ne prenne la place des libellés.
- La coche de permission prête utilise `onSecondaryContainer`, adapté au fond, plutôt que l'accent secondaire. Le titre « À vérifier » utilise `onSurface` : le texte reste explicite sans dépendre d'une couleur orange peu contrastée en mode clair.

## Revue du code UI

La Surface racine de la PR est conservée. Aucun noir/gris/blanc fixe utilisé directement par les écrans Compose hors thème n'a été trouvé. Accueil, installation, réglages, historique, cartes, dialogues, Sécurité et Maison utilisent les rôles Material. La palette globale n'est pas refaite.

Les conteneurs principaux sont défilables avec insets système/clavier. Les actions s'empilent sous 360 dp ou au-delà de fontScale 1,3 ; la navigation compacte s'applique sous 390 dp ou avec grande police. Les textes ne sont pas réduits pour tenir. Ce constat est une revue de code, pas une validation visuelle Android.

## Vérifications et limites

Contrôles locaux : frontières FOSS, écoconception et logs sensibles ; conversion SVG stricte ; diff sans erreur d'espacement. La CI du nouveau commit doit être verte avant toute fusion.

Tests physiques indispensables : 320/360/390 dp, grandes polices, clavier ouvert, clair/sombre ; ajout de chacun des raccourcis sur le launcher de la bêta et un launcher standard ; premier appui puis annulation en quatre secondes ; SMS avec phrase incluse dans une phrase ; refus de localisation hors écran puis fin d'installation ; quota global et contact révoqué.

Le bundle de validation CI n'est pas une livraison AAB signée. Aucun secret, package, applicationId, versionCode ou paramètre de signature n'est modifié. Les jobs de PR n'exportent pas d'APK ; aucun nouveau téléchargement bêta n'est publié par ce lot.

## Suite

La tuile facultative « Urgence VeVak » du panneau Android reste au backlog : elle n'est pas implémentée dans ce lot ciblé. Elle nécessite ses propres vérifications de cycle de vie, verrouillage et annulation. Le fallback UI historique est conservé pour éviter une refonte avant release. PR maintenue en brouillon, sans fusion.
