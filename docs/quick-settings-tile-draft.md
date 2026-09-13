# Tuile Urgence VeVak — lot en revue, tests physiques requis

Ce lot prolonge le commit `437db70` de la PR #35. La CI verte de ce commit précédent ne valide pas ce nouvel ajout : vérifier les checks de son propre commit avant toute fusion.

Revue complémentaire : un second appui pendant le chargement annule la coroutine ET tout armement déjà créé, sans appeler toggle. Un contrôle ensureActive interdit d'armer après annulation même si runCatching a capturé CancellationException. Les mises à jour de qsTile sont limitées à l'écoute ; la destruction invalide les callbacks de déverrouillage. Les erreurs Android demande déjà ouverte / app hors premier plan sont distinguées. L'explication du double appui est présente avant l'ajout de la tuile et du raccourci.

## Parcours préparé

- Tuile optionnelle et identifiable « Urgence VeVak », ajout demandé depuis Sécurité.
- Android 13+ : dialogue système ; versions précédentes : instructions pour le bouton Modifier.
- Aucun destinataire implicite : les destinataires actifs et l'installation terminée sont revérifiés avant l'armement, ainsi que SEND_SMS et la SIM par défaut.
- Téléphone verrouillé : déverrouillage avant l'armement. Annuler reste possible sans nouvelle authentification.
- Premier appui : armement partagé avec le raccourci existant. La tuile affiche « Annuler · N s » pendant quatre secondes ; deuxième appui : annulation.
- Pas de notification ou service permanent. Mise à jour de l'affichage uniquement pendant l'écoute du panneau ; pas de position ou de contact affiché dans le volet.
- Aucun état « SMS livré » inventé : l'acceptation par Android ne vaut pas confirmation de réception.

## Demande utilisateur à terminer impérativement

**Expliquer clairement le « double tap » dans l'interface.** Il ne s'agit pas d'un double appui rapide pour envoyer :

> Un appui prépare l'envoi. Un deuxième appui dans les quatre secondes l'annule. Sans deuxième appui, l'envoi est déclenché après ce délai.

Cette explication doit apparaître à la configuration du raccourci ET de la tuile, avant leur ajout. Le compte à rebours doit rappeler l'action Annuler. Vérifier la compréhension avec TalkBack, une grande police et sur petit écran ; éviter le terme technique « double tap » seul. L'utilisateur a demandé d'inscrire explicitement ce point dans le prompt de reprise.

## Revue restant à faire avant push

1. Compiler FOSS/Play et lint : cette préparation n'a pas été compilée localement faute de SDK Android.
2. Vérifier le callback d'ajout avec un refus, une tuile déjà ajoutée et une demande répétée.
3. Tester les appuis rapprochés pendant la lecture de configuration, deux callbacks de déverrouillage, une annulation à la limite des quatre secondes et un ancien libellé Annuler.
4. Tester écran verrouillé, changement de SIM, permission révoquée, aucun contact actif, retrait d'un destinataire, processus recréé et redémarrage.
5. Vérifier que fermer le volet ne supprime pas l'armement déjà volontairement lancé et n'empêche pas de l'annuler après réouverture.
6. Contrôler la discrétion : nom volontairement visible dans les réglages rapides, aucun numéro ou lieu ; les raccourcis neutres restent séparés.

Le contrôleur expose une lecture de l'échéance et une annulation atomique qui ne peut pas réarmer. Il ne modifie pas le mécanisme job/alarme/réception existant. Un délai sauvegardé supérieur à quatre secondes n'est pas présenté comme un armement actuel (notamment après un changement d'origine du temps écoulé).

Sources Android :

- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/reference/android/app/StatusBarManager

Ne pas fusionner cette préparation sans revue, CI puis tests physiques. La PR doit rester en brouillon.
