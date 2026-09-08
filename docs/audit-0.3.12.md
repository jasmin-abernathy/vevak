# Audit VeVak 0.3.12 — correctifs issus des tests réels

Date : 8 septembre 2026. Base auditée : `main` à `aba9f229bfdb8eaeb49ce5d73953cbf6ec5ef0a9`.

## Conclusion

La 0.3.11 compilait et ses règles étaient largement couvertes par des tests unitaires, mais trois comportements annoncés ne résistaient pas au test sur téléphone. Deux causes étaient directement visibles dans le code ; la troisième était insuffisamment tolérante aux transformations courantes d'un SMS.

| Attente | État 0.3.11 | Cause | Correction 0.3.12 |
|---|---|---|---|
| Rafraîchir régulièrement une seule dernière position | Réglage et alarmes présents, acquisition hors écran non fiable | Le manifeste interdisait précisément l'autorisation Android nécessaire à une nouvelle acquisition lorsque l'application n'est pas visible | Permission facultative dédiée, écran explicatif, contrôle dans le planificateur et le receiver ; état suspendu affiché si elle manque |
| Reconnaître la phrase-clé au milieu d'un SMS naturel | Tests verts pour quelques chaînes exactes | La recherche restait sensible à certains espacements de ponctuation, apostrophes ou tirets produits par les claviers | Comparaison par suite contiguë de mots Unicode, toujours bornée pour éviter `ok` dans `booking` |
| Donner une zone approximative quand la localisation Android est coupée | Chemin beaconDB présent mais fragile | Délai court, requête sans longueur fixe et rejet d'une réponse valide si le champ facultatif `fallback` manquait | Requête HTTPS IP-only plus robuste, délai de 6 s et validation sur les coordonnées plutôt que sur ce champ indicatif |

## Contrats conservés

- pas de compte, publicité, pisteur, télémétrie ou serveur VeVak obligatoire ;
- FOSS canonique et Google Play Services limité à la flavor Play ;
- aucune notification par demande reçue et aucune notification permanente ;
- phrase, expéditeur autorisé, expiration et quota global 15 min / 4 réponses par 24 h toujours obligatoires ;
- estimation réseau désactivée par défaut et explicitement décrite comme une zone ;
- une seule position périodique remplaçable, aucun historique ou trajet ;
- partage manuel et urgence limités au dernier point réel/local ;
- protection ciblée par contact isolée de la vraie position, du Wi-Fi Maison et de l'estimation réseau.

## Limites Android à afficher honnêtement

L'autorisation en arrière-plan rend possible l'acquisition d'un nouveau point Android hors écran mais ne transforme pas la fréquence choisie en minuterie exacte. Doze et les restrictions constructeur peuvent retarder ou regrouper les passages. Sans l'accès Android « Toujours autoriser », VeVak limite les passages à la zone réseau/IP si cette option distincte est active ; sans aucune de ces deux capacités, il suspend le planificateur au lieu de laisser croire que la fonction tourne.

## Validation avant commit puis bêta

1. Exécuter les vérifications statiques, les tests, les builds et le lint FOSS + Play.
2. Installer l'APK indiquant `0.3.12` / versionCode `15`.
3. Autoriser le rafraîchissement hors écran, choisir « Toujours autoriser », fermer VeVak et contrôler l'âge du dernier point après 30 à 90 minutes.
4. Tester une phrase seule puis incluse dans un message avec ponctuation différente, apostrophe typographique et tiret.
5. Couper la localisation Android, désactiver le Wi-Fi Maison ou quitter ce réseau, activer l'estimation réseau, puis lancer « Tester toutes les sources » et un SMS réel en 4G/5G.
6. Rejouer écran verrouillé, économie de batterie et redémarrage.

Ne publier aucune coordonnée, phrase, numéro ou identité Wi-Fi dans une issue publique.
