# VeVak 0.3.10 — protection par contact

Cette évolution conserve le contrat de non-régression de la 0.3.9 et simplifie la protection avancée sans supprimer la compatibilité avec les bêta précédentes.

## Protection avancée

Le nouveau fonctionnement ne demande plus de créer une seconde phrase.

1. L'utilisateur sélectionne le contact dont il souhaite se protéger.
2. Ce contact conserve exactement la phrase-clé déjà enregistrée dans sa fiche VeVak.
3. Si ce contact envoie cette phrase, VeVak suit uniquement le chemin de protection et répond avec le lieu de repli enregistré.
4. Les autres contacts continuent d'utiliser leur propre phrase et le resolver de localisation normal.

Le routage utilise l'identité du contact résolue à partir du numéro expéditeur puis sa phrase-clé. Deux contacts peuvent donc conserver des phrases identiques sans que la protection soit appliquée au mauvais contact.

L'ancienne configuration reposant sur une seconde phrase reste reconnue en interne afin de ne pas casser une installation bêta existante. La nouvelle interface ne demande plus cette seconde phrase.

## Localisation du mode protection

Le chemin de protection reste isolé du resolver normal :

- aucune lecture de la position réelle ;
- aucune lecture du Wi-Fi de confiance ;
- aucun appel au repli réseau/IP ;
- réponse construite uniquement à partir du lieu de repli localement enregistré.

## Urgence

Le bouton d'urgence est une action locale volontaire. Il n'est pas soumis aux limitations anti-suivi destinées aux demandes automatiques distantes (intervalle minimal et quota sur 24 h).

La CI vérifie désormais que `EmergencyShareReceiver` ne dépend pas de `RuntimeStateRepository`, `RequestRatePolicy` ou `tryAcquire()`. Cela évite qu'une future refactorisation réintroduise accidentellement une limitation de fréquence dans le chemin d'urgence.

## Cartographie

Google Maps est présenté en premier et devient le choix initial d'une nouvelle configuration, car il est le service le plus courant pour le grand public.

Un choix déjà enregistré reste intact : une mise à jour de VeVak ne remplace pas automatiquement CoMaps ou OpenStreetMap chez un utilisateur existant.

## Invariants conservés de la 0.3.9

- phrase-clé insensible à la casse et aux variations typographiques SMS prévues ;
- dernière position automatique issue de la meilleure source légitime disponible ou mémorisée ;
- aucune permission de localisation permanente ;
- séparation mémoire toute-source / dernier point réel ;
- options batterie, précision et fournisseur de carte respectées ;
- mode protection isolé des vraies sources de localisation ;
- urgence basée uniquement sur le dernier point réel/local connu.
