# Raccourcis discrets — icônes neutres

Les six raccourcis d’urgence utilisent maintenant des **VectorDrawable originaux VeVak**, volontairement simples et génériques. Le but n’est pas d’imiter une application existante mais de ressembler à de petits utilitaires Android ordinaires.

## Direction visuelle actuelle

Chaque icône suit la même recette :

- fond carré arrondi uni ;
- une seule métaphore visuelle ;
- pictogramme épais et très lisible ;
- beaucoup d’air autour du symbole pour résister aux masques des launchers ;
- aucune ombre, badge rouge, détail décoratif ou mini-interface ;
- aucune marque ou identité visuelle d’une application tierce ;
- aucune marque VeVak ajoutée par l’application.

Les presets restent : Notes, Liste, Horaires, Dossier, Outils et Mémos.

Les fichiers runtime sont dans `app/src/main/res/drawable/ic_shortcut_*.xml`.

## Pourquoi Papirus n’est plus utilisé au runtime

Une première passe avait adapté six ressources de **Papirus Icon Theme**. Le test sur téléphone a montré que ces icônes de bureau restaient trop détaillées une fois réduites et retraitées par un launcher Android.

Les sources Papirus et leur licence restent dans `third_party/papirus/` pour conserver la provenance historique du prototype, mais les six ressources utilisées par Android n’en dérivent plus.

`scripts/build-shortcut-vectors.py` ne reconvertit donc plus Papirus : il valide simplement que les six VectorDrawable originaux attendus sont présents et gardent leur viewport/signalement de provenance.

## Contraintes à préserver

1. Libellés neutres uniquement (`Notes`, `Liste`, `Horaires`, `Dossier`, `Outils`, `Mémos`).
2. Ne pas copier le logo, le nom ou la silhouette reconnaissable d’une application existante.
3. Ne pas ajouter de badge VeVak. Certains launchers peuvent toutefois ajouter eux-mêmes un badge de l’application d’origine ; ne pas tenter de contourner ce comportement système.
4. Tester le rendu réel : aperçu Compose ≠ rendu final du launcher.
5. Conserver une zone sûre généreuse : les launchers peuvent recadrer, masquer ou redimensionner.
6. Aucun téléchargement d’icône au runtime.

## Validation restante

Le nouveau rendu doit être vérifié sur le launcher du téléphone de bêta qui a montré le mauvais rendu précédent et, si possible, sur un launcher Android plus standard, avec les six presets réellement épinglés.

Le raccourci reste une façade locale optionnelle : VeVak lui-même n’est ni caché ni renommé.
