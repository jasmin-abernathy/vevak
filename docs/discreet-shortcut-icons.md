# Raccourcis discrets — icônes libres

Cette note prépare le remplacement des pictogrammes monochromes actuels des raccourcis d’urgence par des icônes qui ressemblent davantage à de petits utilitaires Android, sans copier le nom ni la marque d’une application existante.

## Source retenue pour l’exploration

**Papirus Icon Theme** — https://github.com/PapirusDevelopmentTeam/papirus-icon-theme

- format source : SVG ;
- licence du thème : **GNU GPL v3** ;
- licence compatible avec VeVak, lui-même sous GPL-3.0-or-later ;
- privilégier uniquement les icônes **génériques** du thème et éviter les logos d’applications/marques, même lorsqu’ils sont présents dans Papirus ;
- toute ressource finalement intégrée devra garder dans le dépôt la référence au fichier source et à sa licence.

Candidats génériques déjà vérifiés dans le dépôt Papirus :

| Preset VeVak | SVG Papirus candidat | Rôle visuel |
| --- | --- | --- |
| Notes | `Papirus/48x48/apps/accessories-text-editor.svg` | feuille / éditeur avec crayon |
| Horaires | `Papirus/48x48/apps/preferences-system-time.svg` | horloge colorée |
| Dossier | `Papirus/48x48/apps/system-file-manager.svg` | gestionnaire de fichiers générique |
| Outils | `Papirus/48x48/apps/applications-utilities.svg` | boîte à outils générique |
| Mémos | `Papirus/48x48/apps/accessories-dictionary.svg` | carnet / livre générique |

Le preset **Liste** reste à sélectionner : ne pas reprendre l’icône exacte d’une application connue (Todoist, GNOME To Do, etc.) uniquement parce qu’elle ressemble à une checklist. Préférer une ressource générique ou une composition dérivée d’un pictogramme générique libre.

## Contraintes d’intégration

1. Le raccourci doit conserver un libellé local neutre (`Notes`, `Liste`, `Horaires`, etc.).
2. VeVak ne doit **pas** ajouter son propre logo ou badge sur l’icône du raccourci.
3. Certains launchers peuvent ajouter eux-mêmes un badge de l’application d’origine ; VeVak ne doit pas tenter de contourner artificiellement ce comportement système/launcher.
4. Le rendu doit être testé au minimum sur le launcher utilisé pendant la bêta ainsi que sur un launcher Android plus classique.
5. Les SVG ne doivent pas être chargés depuis le réseau au runtime : les ressources retenues doivent être embarquées localement.
6. Si un SVG doit être converti en `VectorDrawable` ou en PNG de ressource Android, conserver ici la provenance du SVG original.
7. Éviter les icônes reproduisant volontairement l’identité visuelle d’une application tierce : l’objectif est un utilitaire neutre et plausible, pas l’usurpation d’une application existante.

## État

Les six ressources Android sont maintenant remplacées. Les cinq candidats ci-dessus sont intégrés ; Liste est une adaptation locale de la feuille `accessories-text-editor.svg` : le badge crayon et les lignes sont remplacés par trois coches et trois lignes génériques. Aucune icône Todoist ou GNOME To Do n'est utilisée.

## Provenance des fichiers intégrés (13 septembre 2026)

Les sources originales sont conservées dans `third_party/papirus/`, avec la licence GPL-3.0 complète (`LICENSE`). Copyright : contributeurs Papirus. L'adaptation Liste et la conversion Android sont réalisées par les contributeurs VeVak le 13 septembre 2026, sous GPL-3.0.

| Source sous `Papirus/48x48/apps/` | SHA du blob source |
| --- | --- |
| accessories-text-editor.svg | d24fa101ea015447f0bd7cd48e16e1c6d704a076 |
| preferences-system-time.svg | c7475704daf5f2ed12c3647657ddf111aaa4efdf |
| system-file-manager.svg | e57cf245a0b49bf8e00eff1fe3502e98d59ba700 |
| applications-utilities.svg | e779e2d10498345e910084a16f2301bb31a62cd0 |
| accessories-dictionary.svg | 219c7bc22fcd446f550ae7b9b4534fdeea96394f |

Source : https://github.com/PapirusDevelopmentTeam/papirus-icon-theme/tree/master/Papirus/48x48/apps

Relancer `python3 scripts/build-shortcut-vectors.py` pour régénérer les six VectorDrawable. Le convertisseur préserve chemins, rectangles arrondis, cercles, opacités, traits et la transformation du carnet. Il refuse les formes/attributs non pris en charge. Aucune bibliothèque SVG ni connexion réseau n'est ajoutée à Android.

Les aperçus du centre de sécurité sont limités à 48 dp et le texte reçoit l'espace restant. Le contrat d'armement/annulation et le jeton des raccourcis ne changent pas. Le rendu final et les badges éventuels restent à vérifier sur les launchers réels avant fusion.
