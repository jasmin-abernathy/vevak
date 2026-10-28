# VeVak — édition diagnostic de géolocalisation (sans SMS)

Cette édition est destinée aux téléphones qui bloquent l'installation par navigateur d'une APK demandant `RECEIVE_SMS`. Elle a son propre identifiant d'application `com.vevak.app.diagnostic.debug`, indépendant de la VeVak complète. **Ce n'est pas un remplacement de VeVak : elle ne reçoit pas les SMS, ne répond pas aux demandes et ne peut pas envoyer d'urgence.**

Le manifeste de cette variante retire explicitement `RECEIVE_SMS`, `SEND_SMS`, l'accès à la localisation en arrière-plan et les récepteurs SMS/boot/urgence. La CI vérifie le manifeste de l'APK générée pour éviter toute régression.

## Protocole de test

1. Installe `app-diagnostic-debug.apk` depuis l'artefact `vevak-diagnostic-nosms-<sha>` d'un workflow Android CI réussi.
2. Ouvre l'édition diagnostic, autorise sa localisation précise, active temporairement Android Localisation et appuie sur **Tester toutes les sources autorisées**. Note la source et la précision.
3. Désactive Android Localisation, puis appuie sur **Lire uniquement la mémoire (sans GPS ni réseau)**. Le dernier point réellement obtenu doit être restitué avec son ancienneté.
4. Avec Android Localisation ON et une connexion Internet, tu peux cocher séparément **Estimation par antenne cellulaire** et **Estimation IP**. Attention : l'estimation cellulaire envoie le MCC/MNC et l'identifiant de l'antenne enregistrée ainsi que l'IP à beaconDB ; l'estimation IP transmet seulement l'IP. Les réponses sont imprécises et peuvent échouer.
5. Quand Android Localisation est OFF, une estimation cellulaire fraîche n'est pas possible, par décision de confidentialité du système et de VeVak. Ne pas la présenter comme une localisation autonome hors ligne.

Aucune donnée de la version complète ne passe dans cette édition : les deux applications ont des stockages privés Android distincts. La mémoire de l'édition diagnostic est conservée entre ses lancements tant que ses données ne sont pas effacées.

**Attention aux mises à jour de l'APK diagnostic debug :** cette variante reste signée avec une clé debug de CI qui peut changer. Pour la distribution pérenne de l'édition complète, utiliser la variante `fossBeta` signée comme expliqué dans [BETA-SIGNING.md](BETA-SIGNING.md).

La suppression de `RECEIVE_SMS` réduit l'un des déclencheurs documentés de Play Protect mais ne garantit pas à 100 % l'installation, puisque d'autres protections peuvent intervenir.
