package fr.idmc.raizo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serveur de minage
 */
public class Server {

    /**
     * Liste des clients NON connectés (avant mdp)
     */
    private final Set<Socket> clients = new HashSet<>();

    /**
     * Liste des clients qui SONT connectés (apres mdp)
     */
    private final Set<Socket> clientsConnectes = new HashSet<>();

    /**
     * Port d'écoute du serveur
     */
    final int port = 1337;

    /**
     * Mot de passe généré aléatoirement
     */
    private final StringBuilder password = new StringBuilder();

    /**
     * Difficulté en cours
     */
    private int difficulteEnCours;

    /**
     * Etat clients
     */
    private String etat;

    /**
     * Lancement du serveur
     * @throws Exception
     */
    public void run() throws Exception {
        //ouvrir le port avec l'api socket sur le port 1337 avec un Thread pour parraléliser
        // l'invite de commande et l'écoute du serveur
            new Thread(() ->{
            try (ServerSocket serverSocket = new ServerSocket(port)){

                //Initialisation de l'état
                etat = "En attente";

                //Génération d'un mot de passe aléatoire
                Random rand = new Random();
                for (int i = 0; i < 8; i++) {
                    char c = (char) (rand.nextInt(94) + 33);
                    password.append(c);
                }

                //Annonce de l'écoute du serveur + mdp
                System.out.println("Server is listening on port " + port);
                System.out.println("$ Server Password is : " + password);
                System.out.print("$ ");

                //Boucle d'attente de connexion
                while(true){
                    Socket client = serverSocket.accept();
                    System.out.println("New client connected, port: " + client.getPort() + ", IP: " + client.getInetAddress().getHostAddress());
                    System.out.print("$ ");
                    clients.add(client);
                }

            } catch(IOException e){
                System.err.println("Could not listen on port " + port);
            }
        }).start();

            boolean keepGoing = true;
            final Console console = System.console();

            //Boucle de lecture de la console
            while (keepGoing) {
                if (console == null) {
                    System.out.println("La console n'est pas disponible. Veuillez utiliser une autre méthode pour lire l'entrée utilisateur.");
                    break;
                } else {
                    final String commande = console.readLine("$ ");
                    if (commande == null) break;
                    try {
                        keepGoing = processCommand(commande.trim());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
    }


    //Traitement des commandes
    private boolean processCommand(String cmd) throws Exception {
        if (("QUIT").equals(cmd)) {
            System.out.println("Bye bye!");
            return false;
        }

        //Commande pour annuler le travail en cours
        if (("CANCELLED").equals(cmd)) {
            for (Socket client : clientsConnectes) {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                out.println("CANCELLED");
                out.flush();

                etat = "En attente";
            }


        } else if (("STATUS").equals(cmd)) {

            for (Socket client : clientsConnectes) {
                System.out.println("Client: " + client.getInetAddress().getHostAddress() + ":" + client.getPort() + " : " + etat);
            }
        } else if (cmd.startsWith("WHO_ARE_YOU_?")) {
            for (Socket client : clients) {

                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                out.println("WHO_ARE_YOU_?");
                out.flush();

                String response = in.readLine();
                System.out.println("-=-=(=)=-=-");
                System.out.println(response + " from " + client.getInetAddress().getHostAddress() + ":" + client.getPort());

                //Demande de mdp au client après avoir reçu ITS_ME
                if (response.startsWith("IT'S ME")) {

                    //envoie GIMME_PASSWORD au client
                    out.println("GIMME_PASSWORD");
                    out.flush();

                    //attente de PASSWD du client
                    String passwordWorker = in.readLine();

                    if (passwordWorker.startsWith("PASSWD")) {
                        //Récupération du mot de passe du client
                        String passwordWorkerValue = passwordWorker.substring(7);
                        if (passwordWorkerValue.contentEquals(password)) {

                            //Ajout du client à la liste des clients connectés
                            System.out.println("Client connecte : " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
                            clientsConnectes.add(client);

                            //envoie HELLO_YOU au client
                            out.println("HELLO_YOU");
                            out.flush();

                            //attente de READY du client
                            String READY = in.readLine();
                            if (READY.startsWith("READY")) {
                                System.out.println("Client pret : " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
                                out.println("OK");
                                out.flush();
                            }

                        } else {
                            System.out.println("Mauvais mot de passe pour le client : " + client.getInetAddress().getHostAddress() + ":" + client.getPort());

                            //envoie YOU_DONT_FOOL_ME au client
                            out.println("YOU_DONT_FOOL_ME");
                            out.flush();

                        }
                    }
                }
            }

            //Vider la liste des clients non connectés
            clients.clear();
        } else if (cmd.startsWith("NONCE")) {
            //Commande NONCE

            int nbClient = clientsConnectes.size();
            int incrClient = 0;

            //Si aucun client connecté
            if (nbClient == 0) {
                System.out.println("Aucun client apte : utilisez WHO_ARE_YOU_? pour connecter des clients.");
                return true;
            }

            System.out.println("Nombre de clients connectes : " + nbClient);
            System.out.println("Nous allons initialiser les valeurs de start et increment pour chaque client.");

            //Envoi de la commande NONCE à chaque client
            for (Socket client : clientsConnectes) {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                System.out.println("-=-=(=)=-=-");

                int start = incrClient;
                int increment = nbClient;

                //Envoi de la commande NONCE
                out.println("NONCE " + start + " " + increment);
                out.flush();
                System.out.println("Envoi de la commande NONCE au client : " + client.getInetAddress().getHostAddress() + ":" + client.getPort() + " avec start = " + start + " et increment = " + increment);

                //Incrémentation du client pour changer le start en fonction du nombre de connectés
                incrClient++;
            }

            System.out.println("Donner le hash avec la commande PAYLOAD");
            Scanner sc = new Scanner(System.in);
            System.out.print("$ ");
            String payload = sc.nextLine();

            //Attente de la commande PAYLOAD
            while (!payload.startsWith("PAYLOAD")) {
                System.out.println("Veuillez donner le hash avec la commande PAYLOAD");
                payload = sc.nextLine();
            }

            //Envoi de la commande PAYLOAD à chaque client
            for (Socket client : clientsConnectes) {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                System.out.println("-=-=(=)=-=-");
                System.out.println("Envoi de la commande PAYLOAD au client : " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
                String paylo = payload.substring(8);
                out.println("PAYLOAD " + paylo);
                out.flush();
            }

            //On récupère la difficulté ici
            System.out.println("Donner la difficulte avec la commande SOLVE");
            System.out.print("$ ");
            String cmdSolve = sc.nextLine();
            while (!cmdSolve.startsWith("SOLVE")) {
                System.out.println("Veuillez donner la commande SOLVE");
                cmdSolve = sc.nextLine();
            }

            //Envoi de la commande SOLVE à chaque client
            for (Socket client : clientsConnectes) {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                System.out.println("-=-=(=)=-=-");
                System.out.println("Envoi de la commande SOLVE au client : " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
                String diff = cmdSolve.substring(6);

                difficulteEnCours = Integer.parseInt(diff);

                out.println("SOLVE " + diff);
                out.flush();

                etat = "En cours de recherche...";
            }




                System.out.println("-=-=(=)=-=-");
                System.out.println("En attente de la solution...");
                AtomicReference<String> solution = new AtomicReference<>("");

                //Réception de la solution (on crée un thread pour parraléliser la réception de chaque client)
                for (Socket client : clientsConnectes) {
                    Thread reception = new Thread(() -> {
                        try {
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                            solution.set(in.readLine());
                            if (solution.get().startsWith("FOUND")) {
                                //Envoi de la commande CANCELLED à chaque client qui va permettre l'arrêt de tous les tâches en cours

                                clientsConnectes.forEach(socket -> {
                                    try {
                                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                                        out.println("CANCELLED");
                                        out.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });

                                etat = "En attente";

                                    //Affichage de la solution trouvée
                                    System.out.println("Solution trouvee par un client");

                                    String hash = solution.get().substring(6, 70);
                                    System.out.println("HTTPHash: " + hash);
                                    String nonce = solution.get().substring(71);
                                    System.out.println("HTTPNonce: " + nonce);


                                    //Envoi de la solution à la webApp.
                                    String url = "https://projet-raizo-idmc.netlify.app/.netlify/functions/validate_work";
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("Content-Type", "application/json");
                                    //On met le token de la webApp
                                    headers.put("Authorization", "Bearer rec2IzLwbwoTUBN3T");

                                    //On envoie la solution avec les valeurs trouvées
                                    String body = "{\"d\": \"" + difficulteEnCours + "\",\"n\": \"" + hash + "\", \"h\": \"" + nonce + "\"}";
                                    postRequest(url, headers, body);
                                    System.out.print("$ ");

                            }
                        } catch (IOException e) {
                            e.printStackTrace();

                            System.out.print("$ ");
                        }
                    });
                    reception.start();
                }
                //Commande Help pour afficher les commandes disponibles
        } else if(("HELP").equals(cmd.trim())) {
            System.out.println("Commandes disponibles :");
            System.out.println(" - WHO_ARE_YOU_? - demander aux clients de s'identifier");
            System.out.println(" - STATUS - afficher l'état des clients connectés");
            System.out.println(" - NONCE | PAYLOAD {h} | SOLVE {d} DANS L'ORDRE lance le minage");
            System.out.println(" - HELP - afficher les commandes disponibles");
            System.out.println(" - CANCELLED - arrêter le travail en cours de tous les clients connectés");
            System.out.println(" - QUIT - fermer la console");

        } else {
            //Commande inconnue
            System.out.println("Commande inconnue. Utilisez 'HELP' pour afficher les commandes disponibles.");
        }

        return true;
    }

    //Méthode pour envoyer une requête POST
    private void postRequest(String url, Map<String, String> headers, String body) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
        conn.setRequestMethod("POST");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        // On envoie la requête
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // On recupère la réponse
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());
        }

        conn.disconnect();
    }

    public static void main(String[] args) throws Exception {
        new Server().run();
    }
}
