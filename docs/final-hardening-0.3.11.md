# VeVak 0.3.11 — final hardening candidate

Cette version part directement de la 0.3.10 et conserve ses invariants : contacts multiples, autorisations finies, quota anti-suivi global, résolution de position toute-source, mémoire séparée du dernier point réel pour le partage manuel/l'urgence, protection ciblée par contact, Google Maps proposé en premier, estimation réseau opt-in, sauvegarde chiffrée et variantes FOSS/Play.

## Phrase-clé dans une phrase normale

La phrase-clé n'a plus besoin d'être l'intégralité du SMS. Après normalisation de la casse, des espaces insécables et des apostrophes typographiques, VeVak cherche la phrase configurée **à l'intérieur** du message.

Exemple : si la phrase-clé est `position maintenant`, `Salut, position maintenant s'il te plaît` est reconnue. Les limites de mots évitent qu'une clé courte comme `ok` corresponde par accident à l'intérieur de `booking`.

Le numéro expéditeur, l'autorisation du contact et le quota anti-suivi restent obligatoires.

## Réponses SMS sans notifications

À partir de 0.3.11 :

- aucune notification n'est affichée pour une demande reçue ;
- aucune notification permanente « VeVak actif » n'est utilisée ;
- `POST_NOTIFICATIONS` n'est plus déclaré dans le manifeste ;
- refuser ou désactiver les notifications ne peut jamais bloquer une réponse SMS ;
- le traitement SMS ne dépend d'aucun canal ou gestionnaire de notification ;
- après deux réponses normales réussies du même contact, VeVak conserve seulement un compteur local borné et peut proposer la protection lors d'une prochaine ouverture de l'application.

La CI vérifie explicitement que la permission et les dépendances de notification ne reviennent pas dans le cœur SMS.

## Autorisations / paramètres restreints

L'étape d'autorisations demande uniquement les SMS et la localisation ponctuelle.

Avant d'ouvrir manuellement la fiche Android de VeVak, une boîte de dialogue rappelle le parcours des APK installées hors store : ouvrir la fiche Android, utiliser le menu `⋮` puis `Autoriser les paramètres restreints` lorsque cette option existe, accorder les autorisations puis revenir simplement dans VeVak.

Au retour (`ON_RESUME`), VeVak relit les autorisations. L'étape s'avance automatiquement dès que les accès nécessaires sont accordés.

## Mémoire périodique d'une seule position

L'utilisateur peut activer explicitement `Essayer de garder une dernière position récente`.

- choix de fréquence cible : 15, 30 ou 60 minutes ;
- 30 minutes par défaut ;
- une seule position mémorisée : chaque nouveau point remplace le précédent ;
- aucun historique, trajet ou breadcrumb ;
- aucune alarme répétitive exacte, aucun WorkManager périodique et aucun service de localisation permanent ;
- Android/Doze peut retarder les tentatives ;
- l'estimation réseau/IP n'est utilisée que si l'utilisateur a déjà activé cette option séparée ;
- `ACCESS_BACKGROUND_LOCATION` reste absent.

Le planificateur crée un seul prochain déclenchement à la fois. Le receiver revalide l'onboarding, l'option et l'existence d'au moins une autorisation active avant chaque tentative.

## Reprise après redémarrage

Une option distincte `Relancer VeVak au démarrage du téléphone` permet de reprogrammer la mémoire périodique après `BOOT_COMPLETED`.

Elle n'ouvre pas l'interface, ne lance pas de notification permanente et ne contourne pas les restrictions de localisation Android.

## Raccourci discret d'urgence

VeVak peut demander au launcher Android d'épingler un raccourci d'écran d'accueil avec l'un des noms/icônes génériques suivants : Notes, Liste, Horaires, Dossier, Outils ou Mémos. Les visuels sont des ressources originales du projet et n'imitent pas une marque existante.

Les destinataires sont choisis à l'avance parmi les contacts VeVak autorisés : tous les contacts actifs ou un sous-ensemble. Aucun choix de destinataire ni écran de confirmation n'apparaît lors du déclenchement.

Le raccourci possède un jeton aléatoire local afin qu'une Activity exportée ne puisse pas être utilisée simplement par une autre application pour déclencher l'urgence.

### Temporisation annulable

- premier appui : l'urgence est armée pendant 4 secondes ;
- deuxième appui sur le même raccourci pendant cette fenêtre : annulation ;
- sans deuxième appui : déclenchement de l'action d'urgence canonique ;
- après envoi, un nouvel appui commence une nouvelle séquence ;
- le raccourci lui-même ne résout jamais la position.

L'action finale reste `EmergencyShareReceiver` : dernier point **réel/local** uniquement, ancienneté conservée, pas d'estimation réseau/IP, pas d'adresse géocodée et aucun quota anti-suivi appliqué à l'action locale d'urgence.

Le déclenchement et son résultat restent silencieux pour ne pas révéler VeVak après l'utilisation d'un raccourci discret.

## Protection ciblée par contact

Le fonctionnement 0.3.10 est conservé : l'utilisateur choisit le contact dont il veut se protéger. Ce contact continue à envoyer sa phrase-clé habituelle. Pour ce contact seulement, une phrase reconnue utilise exclusivement le lieu de repli et n'entre jamais dans le resolver réel, le Wi-Fi Maison ou l'estimation réseau.

Les anciennes sauvegardes possédant une seconde phrase de protection restent lisibles pour migration.

## Invariants de non-régression à tester

Avant de déclarer cette branche stable :

1. phrase-clé seule et phrase-clé au milieu d'un SMS ;
2. casse, espaces insécables et apostrophes typographiques ;
3. demande avec Wi-Fi, puis uniquement 4G/5G, localisation Android ON/OFF ;
4. fallback vers la dernière coordonnée mémorisée ;
5. refus total des notifications sans impact sur le SMS ;
6. quota global 15 min / 4 réponses sur 24 h en production ;
7. contact protégé vs autre contact ;
8. partage manuel : dernier point réel seulement + confirmation locale ;
9. urgence : destinataires prédéfinis, absence de confirmation et bypass du quota ;
10. raccourci : premier appui, annulation au second appui, envoi après 4 s ;
11. rafraîchissement 15/30/60 min et redémarrage ;
12. restauration `.vvk` : préférences conservées, autorisations révoquées ;
13. FOSS et Play : tests, build et lint ;
14. vérifications statiques confidentialité/écoconception.

Les tests automatisés ne remplacent pas les essais sur téléphone réel, notamment pour les restrictions constructeur, le double-SIM, Doze, les paramètres restreints et le comportement du launcher.
