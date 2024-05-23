package fr.idmc.raizo;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class Worker {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean pretExecutionTache;
    private String etat;
    private int difficulty;
    private int start;
    private int increment;
    private byte[] hash;
    private Thread c;
    private String temphash;



    public Worker(Socket socket) {
        this.socket = socket;
        this.pretExecutionTache = false;
        this.etat = "En attente";
    }

    public void run() throws IOException, NoSuchAlgorithmException{

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        String line;


        //Boucle de lecture de la console
        while (true) {
            line = in.readLine();

            if (line.equals("WHO_ARE_YOU_?")) {
                out.println("IT'S ME");
                out.flush();
            }

            if (line.equals("GIMME_PASSWORD")) {
                Scanner sc = new Scanner(System.in);
                System.out.print("$ Veuillez saisir le MDP :");

                String password = sc.nextLine();
                out.println("PASSWD " + password);
                out.flush();
            }

            if (line.equals("YOU_DONT_FOOL_ME")) {
                System.out.println("Mauvais mot de passe ! La connexion est fermee.");
                socket.close();
            }

            if (line.equals("HELLO_YOU")) {
                System.out.println("Vous etes connecte au serveur !");

                out.println("READY");
                out.flush();
            }

            if (line.equals("OK")) {
                System.out.println("OK, pret a travailler !");
                this.pretExecutionTache = true;
            }

            //Si le client est pret à executer une tache
            if (this.pretExecutionTache) {
                if (line.startsWith("NONCE")) {
                    System.out.println("Commande NONCE recue !");
                    this.start = Integer.parseInt(line.substring(6, 7));

                    this.increment = Integer.parseInt(line.substring(8));

                    System.out.println("Start : " + this.start + " Increment : " + this.increment);
                }

                if (line.startsWith("PAYLOAD")) {
                    System.out.println("Commande PAYLOAD recue !");
                    this.temphash = line.substring(8);
                    System.out.println("Hash : " + this.temphash);
                    this.hash = temphash.getBytes();


                }

                if (line.startsWith("SOLVE")) {
                    System.out.println("Commande SOLVE recue !");
                    this.difficulty = Integer.parseInt(line.substring(6));

                    System.out.println("Nouvelle tache commencee avec une difficulte de " + this.difficulty + " ! avec le hash : "
                            + this.temphash + " et le start : " + this.start + " et l'increment : " + this.increment);

                    this.etat = "En cours";
                    this.pretExecutionTache = false;

                    AtomicBoolean solutionTrouvee = new AtomicBoolean(false);

                    c = new Thread(() -> {
                        try {
                            if (in.readLine().startsWith("CANCELLED")) {
                                solutionTrouvee.set(true);
                                c.interrupt();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    c.start();

                    int IntNonce = this.start;
                    String dataPlusNonce = "";
                    String HashRes = "";
                    boolean trouvee = false;

                            MessageDigest md = MessageDigest.getInstance("SHA-256");
                            String targetPrefix = "0".repeat(this.difficulty);
                            while (true) {

                                byte[] data = concatenateByteArrays(this.hash, intToByteArray(IntNonce));
                                byte[] digest = md.digest(data);
                                StringBuilder sb = new StringBuilder();

                                for (byte b : digest) {
                                    sb.append(String.format("%02x", b));
                                }
                                String hashString = sb.toString();

                                if (solutionTrouvee.get()) {
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
                        out.println("FOUND " + HashRes + " " + hexNonce);
                        out.flush();

                    } else {
                        System.out.println("Un client a trouve un solution avant !");
                    }

                    this.etat = "En attente";
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



    // Concatène deux tableaux de bytes
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
