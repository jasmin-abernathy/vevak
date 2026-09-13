# Raccourcis discrets — piste d’icônes libres

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

La source et cinq candidats génériques sont validés au niveau licence/provenance. Le remplacement des ressources Android reste à faire après sélection d’un sixième candidat générique pour `Liste` et contrôle visuel de l’ensemble à taille de launcher.
