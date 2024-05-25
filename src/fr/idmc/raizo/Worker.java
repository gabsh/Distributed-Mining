package fr.idmc.raizo;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Classe Worker
 */
public class Worker {
    /**
     * Socket
     */
    private final Socket socket;
    /**
     * BufferedReader
     */
    private BufferedReader in;
    /**
     * PrintWriter
     */
    private PrintWriter out;
    /**
     * Boolean pour verifier si le client est pret à executer une tache
     */
    private boolean pretExecutionTache;
    /**
     * Int pour la difficulte
     */
    private int difficulty;
    /**
     * Int pour le start
     */
    private int start;
    /**
     * Int pour l'increment
     */
    private int increment;
    /**
     * Tableau de bytes pour le hash
     */
    private byte[] hash;
    /**
     * Thread pour verifier si une solution est trouvee par un autre client
     */
    private Thread c;
    /**
     * String pour le hash temporaire en gros on s'en sert pour un affichage proprement quelque fois car hash est un tableau de bytes
     */
    private String temphash;

    /**
     * Constructeur de la classe Worker
     * @param socket
     */
    public Worker(Socket socket) {
        this.socket = socket;
        this.pretExecutionTache = false;
    }

    /**
     * Methode run
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void run() throws IOException, NoSuchAlgorithmException{

        //Initialisation du BufferedReader et du PrintWriter
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        //Initialisation du BufferedReader et du PrintWriter
        out = new PrintWriter(socket.getOutputStream(), true);

        String line;

        //Boucle de lecture de la console
        while (true) {
            line = in.readLine();

            //Si le client recoit WHO_ARE_YOU_?
            if (line.equals("WHO_ARE_YOU_?")) {
                out.println("IT'S ME");
                out.flush();
            }

            //Si le client recoit GIMME_PASSWORD
            if (line.equals("GIMME_PASSWORD")) {
                Scanner sc = new Scanner(System.in);
                System.out.print("$ Veuillez saisir le MDP :");

                String password = sc.nextLine();
                out.println("PASSWD " + password);
                out.flush();
            }

            //Si le client recoit YOU_DONT_FOOL_ME
            if (line.equals("YOU_DONT_FOOL_ME")) {
                System.out.println("Mauvais mot de passe ! La connexion est fermee.");
                socket.close();
            }

            //Si le client recoit HELLO_YOU
            if (line.equals("HELLO_YOU")) {
                System.out.println("Vous etes connecte au serveur !");

                out.println("READY");
                out.flush();
            }

            //Si le client recoit OK
            if (line.equals("OK")) {
                System.out.println("OK, pret a travailler !");
                //On met le client en etat de pret à executer une tache
                this.pretExecutionTache = true;
            }

            if(line.equals("CANCELLED")) {
                System.out.println("Je n'ai pas de tache en cours !");
                break;
            }

            //Si le client est pret à executer une tache
            if (this.pretExecutionTache) {
                if (line.startsWith("NONCE")) {
                    System.out.println("Commande NONCE recue !");
                    this.start = Integer.parseInt(line.substring(6, 7));

                    this.increment = Integer.parseInt(line.substring(8));

                    System.out.println("Start : " + this.start + " Increment : " + this.increment);
                }

                //Si le client recoit une commande PAYLOAD
                if (line.startsWith("PAYLOAD")) {
                    System.out.println("Commande PAYLOAD recue !");
                    this.temphash = line.substring(8);
                    System.out.println("Hash : " + this.temphash);
                    this.hash = temphash.getBytes();


                }

                //Si le client recoit une commande SOLVE
                if (line.startsWith("SOLVE")) {
                    System.out.println("Commande SOLVE recue !");
                    this.difficulty = Integer.parseInt(line.substring(6));

                    System.out.println("Nouvelle tache commencee avec une difficulte de " + this.difficulty + " ! avec le hash : "
                            + this.temphash + " et le start : " + this.start + " et l'increment : " + this.increment);

                    //On met le client en etat de non pret à executer une tache
                    this.pretExecutionTache = false;

                    //AtomicBoolean pour verifier si une solution est trouvee par un autre client ou quant le serveur envoie le demande manuellement
                    AtomicBoolean stop = new AtomicBoolean(false);

                    //Thread pour verifier si une solution est trouvee par un autre client (sous forme de thread pour ne pas bloquer le programme
                    //et pouvoir continuer à chercher une solution en attendant)
                    c = new Thread(() -> {
                        try {
                            //Lecture de la console
                            String s = in.readLine();
                            if (s.startsWith("CANCELLED")) {
                                //Si une solution est trouvee ou le serveur demande on arrete la tâche en cours
                                stop.set(true);
                                c.interrupt();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    c.start();

                    //int pour le nonce
                    int IntNonce = this.start;
                    //String pour le data + nonce
                    String dataPlusNonce = "";
                    //String pour le hash
                    String HashRes = "";
                    //Boolean pour verifier si une solution est trouvee (à la différence de la variable AtomicBoolean qui est pour arrêter la boucle
                    //de recherche de solution celui-ci est pour envoyer la solution au serveur pour le if(trouvee) plus bas)
                    boolean trouvee = false;

                            //MessageDigest pour hasher le data + nonce
                            MessageDigest md = MessageDigest.getInstance("SHA-256");
                            String targetPrefix = "0".repeat(this.difficulty);

                            //Boucle de recherche de solution
                            while (true) {

                                byte[] data = concatenateByteArrays(this.hash, intToByteArray(IntNonce));
                                byte[] digest = md.digest(data);
                                StringBuilder sb = new StringBuilder();

                                //Convertir le hash en string
                                for (byte b : digest) {
                                    sb.append(String.format("%02x", b));
                                }
                                String hashString = sb.toString();

                                //Si une solution est trouvee par un autre client on arrete la recherche (VOIR THREAD CREE PLUS HAUT)
                                if (stop.get()) {
                                    break;
                                }

                                if (hashString.startsWith(targetPrefix)) {
                                    dataPlusNonce = new String(data);
                                    HashRes = hashString;
                                    trouvee = true;
                                    break;
                                }
                                IntNonce += this.increment;
                            }

                    //Si une solution est trouvee on envoi FOUND au serveur
                    if (trouvee) {
                        System.out.println("Solution trouvee : " + dataPlusNonce + " avec le nonce : " + IntNonce + " et le hash : " + HashRes);
                        String hexNonce = Integer.toHexString(IntNonce);
                        //Envoi de la solution au serveur
                        out.println("FOUND " + HashRes + " " + hexNonce);
                        out.flush();

                    } else {
                        System.out.println("Arret de la recherche de solution.");
                    }

                    //On met le client en etat de pret à executer une tache
                    this.pretExecutionTache = true;
                }
            }
        }
    }

    /**
     * Convertit un entier en tableau de bytes
     * @param number
     * @return
     */
    public static byte[] intToByteArray(int number) {
        byte[] result = new byte[4];
        result[0] = (byte) (number >> 24);
        result[1] = (byte) (number >> 16);
        result[2] = (byte) (number >> 8);
        result[3] = (byte) number;

        /**
         * Supprimer les bytes inutiles
         */
        int firstNonZeroIndex = 0;
        for (int i = 0; i < result.length; i++) {
            if (result[i] != 0) {
                firstNonZeroIndex = i;
                break;
            }
        }

        // Créer un nouveau tableau en copiant les bytes à partir du premier byte non nul
        byte[] trimmedArray = new byte[result.length - firstNonZeroIndex];
        System.arraycopy(result, firstNonZeroIndex, trimmedArray, 0, trimmedArray.length);

        return trimmedArray;
    }


    /**
     * Concatene deux tableaux de bytes
     * @param a
     * @param b
     * @return
     */
    public static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    /**
     * Main lance les worker dans un thread
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        Socket socket = new Socket("localhost", 1337);
        new Thread(() -> {
            try {
                Worker w = new Worker(socket);
                w.run();
            } catch (IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
