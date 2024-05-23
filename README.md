# Distributed-Mining

Déroulé pour démarrer une simulation : 

-> Tous d'abord démarrez le serveur avec la commande Server.java
-> Celui-ci devrait se lancer et fournir un mot de passe généré aléatoirement pour la connexion des clients.

-> Démarrez le nombre de clients que vous souhaitez sur différents terminaux.

Le serveur doit détecter ces connexions et l'afficher sur l'invite de commande.

-> Ensuite taper la commande WHO_ARE_YOU_? sur le serveur et appuyer sur ENTRER
-> Une fois ceci fait insérer le mot de passe pour chacun des clients (attention, les clients ne demandent pas les mots de passe en même temps, il faut remplir au fur et à mesure.).

-> Une fois tous les mots de passe insérer, vous pouvez débuter la configuration pour une recherche de hash.

Dans l'ordre :

-> Tapez la commande NONCE sur la console du serveur, celle-ci aura pour effet de calculer et d'envoyer le start et l'incrémentation de chaque client.
-> Tapez la commande PAYLOAD {data} pour envoyer la série pour déchiffrer.
-> Tapez la commande SOLVE {difficulté} pour lancer la recherche avec une difficulté donnée.

Les clients vont tourner et s'arrêter jusqu'à ce qu'une solution ait trouvé. 

Vous disposez aussi de la commande QUIT pour quitter la console, STATUS pour afficher les différents clients connectés (seul ceux qui ont insérer le MDP) et la commande HELP pour afficher les commandes disponibles.
