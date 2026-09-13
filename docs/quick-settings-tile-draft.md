# Tuile Urgence VeVak — intégrée, validation physique requise

Cette note suit le chantier de la PR #35. La tuile n'est plus un patch local : elle est intégrée à la branche `work/ux-polish-darkmode-onboarding-0.3.14`. Toujours vérifier le head et ses checks avant toute fusion.

## Contrat utilisateur

- Tuile optionnelle et identifiable « Urgence VeVak », ajout demandé depuis Sécurité.
- Android 13+ : dialogue système d'ajout ; versions précédentes : instructions pour le bouton Modifier.
- Aucun destinataire implicite : les destinataires actifs et la configuration sont revérifiés, ainsi que `SEND_SMS` et la SIM par défaut.
- Téléphone verrouillé : déverrouillage avant l'armement.
- Un appui prépare l'envoi. Un deuxième appui dans les quatre secondes l'annule. Sans deuxième appui, VeVak déclenche la prise en charge après ce délai.
- **Ce n'est pas un double appui rapide pour envoyer.**
- Le compte à rebours affiche explicitement l'action `Annuler · N s`.
- Pas de notification ou service permanent. Aucun numéro, contact ou lieu n'est affiché dans le volet.
- Aucun état « SMS livré » n'est inventé : l'acceptation par Android ne vaut pas confirmation de réception.

## Durcissements intégrés

- Un second appui pendant la lecture asynchrone annule le job et tout armement déjà créé ; il n'appelle jamais un second `toggle()` pour annuler.
- `ensureActive()` empêche une coroutine annulée de continuer vers l'armement après un `runCatching`.
- Le rendu du `Tile` est limité à la phase d'écoute du panneau.
- Les callbacks de déverrouillage sont invalidés à la destruction du service.
- Le clic relit l'échéance réelle du contrôleur au lieu de se fier à un ancien état visuel.
- Le compteur de boot Android est mémorisé avec chaque nouvel armement lorsqu'il est disponible, afin de distinguer deux fenêtres d'`elapsedRealtime()` qui coïncideraient après redémarrage.
- Chaque armement utilise une identité `PendingIntent` distincte via son URI de données. Les extras seuls ne suffisent pas à distinguer deux `PendingIntent` Android.
- Le fallback `AlarmManager` est conservé jusqu'à consommation par le receiver : la coroutine ne l'annule plus juste avant son propre broadcast.
- Le fallback utilise `setAndAllowWhileIdle()` : il peut fonctionner en Doze sans permission d'alarme exacte, mais reste inexact et soumis aux limitations/quotas Android.

## Important : délai de quatre secondes et fallback système

Les quatre secondes décrivent la fenêtre d'annulation du chemin normal tant que le processus VeVak reste vivant. Le fallback système est volontairement une alarme inexacte. Android peut la retarder, notamment en Doze ou à cause des quotas `allowWhileIdle`.

Le choix produit concernant une urgence très retardée n'est pas encore tranché : actuellement, un armement non annulé peut encore être consommé lorsqu'un fallback retardé arrive. Ne pas introduire arbitrairement une expiration sans décision explicite.

## Validation automatisée / CI

Les derniers runs observés sur le head précédent `3d66326` étaient en `startup_failure` avant création d'un job, pour Android CI comme pour Secret scan. Ce symptôme n'est pas une preuve d'échec de compilation.

Après tout nouveau commit :

1. vérifier Android CI et Secret scan séparément ;
2. ne qualifier le head de « vert » que si de vrais jobs ont tourné ;
3. ne pas désactiver les contrôles, modifier les workflows ou créer un commit vide uniquement pour contourner un `startup_failure`.

## Tests physiques restant obligatoires

1. Ajout de tuile accepté, refusé, déjà ajouté et demande répétée.
2. Premier appui normal puis annulation vers 1 s et juste avant 4 s.
3. Second appui pendant la lecture de configuration.
4. Fermeture/réouverture du volet pendant l'armement ; ancien libellé `Annuler` après expiration.
5. Écran verrouillé : authentification réussie, annulée et callbacks tardifs.
6. Mort réelle du processus après armement sans `force-stop`, pour exercer le fallback système.
7. Doze forcé via ADB ; vérifier que le test entre réellement en idle avant de conclure.
8. Redémarrage : le premier appui après reboot doit armer normalement, y compris lorsque les valeurs d'uptime avant/après pourraient se ressembler.
9. Réarmement rapide après annulation : vérifier qu'un ancien événement système ne consomme jamais le nouvel armement.
10. `SEND_SMS` révoqué, SIM absente/changée, destinataire révoqué/expiré/retiré.
11. 320/360/390 dp, grande police, TalkBack, paysage, clair/sombre.
12. Régressions existantes : phrase-clé dans une phrase, quotas globaux, protection d'un contact, lieu de repli, onboarding et mémoire mono-point.

## Tests ADB utiles

Ne jamais utiliser un numéro d'urgence public pour tester.

- Mort du processus : `adb shell am kill PACKAGE`, puis confirmer avec `adb shell pidof PACKAGE`.
- Préparation Doze : `adb shell dumpsys battery unplug`.
- Entrée Doze : `adb shell dumpsys deviceidle force-idle` puis contrôler `adb shell dumpsys deviceidle`.
- Sortie Doze : `adb shell dumpsys deviceidle unforce`.
- Nettoyage batterie : `adb shell dumpsys battery reset`.

Rallumer l'écran peut sortir l'appareil de Doze ; ne pas ouvrir le volet simplement pour « regarder » la tuile pendant une mesure Doze.

## Garde-fous

Ne pas ajouter sans décision explicite : permission d'alarme exacte, exemption batterie demandée à l'utilisateur, notification persistante, foreground service, nouvelle acquisition de position, suivi continu ou historique de positions.

Ne pas fusionner la PR #35 tant que les checks réels et les essais physiques nécessaires ne sont pas terminés et que le propriétaire n'a pas donné son accord.

Sources Android :

- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/reference/android/app/StatusBarManager
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/reference/android/provider/Settings.Global#BOOT_COUNT
- https://developer.android.com/reference/android/content/Intent#filterEquals(android.content.Intent)
