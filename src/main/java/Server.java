import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteResult;

import jp.vstone.RobotLib.CPlayWave;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.Format;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;

import javax.sound.sampled.*;


public class Server {
    static Firestore firestoreDB = null;
    static String vocalInterfaceIP = "172.20.31.106";
    static Integer vocalInterfacePort = 8450;


    public static void main(String args[]) throws IOException {
        /*initDB();
        publicIP();*/
        SenderMic senderMic = new SenderMic();
        senderMic.start();
        ListenerResponse listenerResponse = new ListenerResponse();
        listenerResponse.start();
    }

    static class SenderMic extends Thread {
        public void run() {
            //Initialize variables needed for microphone acquisition
            TargetDataLine microphone;
            SourceDataLine speakers;

            //Print the Local IP address
            try {
                printIP();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            try {
                // Open the socket connection
                ServerSocket serverSocket = new ServerSocket(9000);

                // Wait until the the connection is established
                Socket server = serverSocket.accept();

                //Generate the Output Streaming
                DataOutputStream socketOut = new DataOutputStream(server.getOutputStream());
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                //Define the audio format - (bigEndian is not supported on Sota)
                AudioFormat audioFormat = new AudioFormat(16000f, 16, 1, true, false);
                //Define the channel to acquire the audio
                DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

                //Check if the microphone connection works
                if (!AudioSystem.isLineSupported(targetInfo)) {
                    System.out.println("Microphone not supported");
                    System.exit(0);
                }

                // Target data line captures the microphone's audio stream
                TargetDataLine targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                targetDataLine.open(audioFormat);
                System.out.println("Start speaking...Press Ctrl-C to stop");
                targetDataLine.start();


               /* // Set the system information to read from the microphone audio stream
                DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
                speakers = (SourceDataLine) AudioSystem.getLine(dataLineInfo);

                // Open the channel with the microphone and start to acquire information from it
                speakers.open(audioFormat);
                speakers.start();
                System.out.println("Speaker started");*/



                //Define a buffer for saving sound waves
                byte[] data = new byte[6400];

                //Until the Microphone connection is open...
                while (targetDataLine.isOpen()) {
                    try {
                        //Acquisition of audio and save it in the buffer
                        Integer numBytesRead = targetDataLine.read(data, 0, data.length);

                        //Send the audio through the socket connection
                        //out.write(data, 0, numBytesRead);
                        //speakers.write(data, 0, numBytesRead);
                        socketOut.write(data, 0, numBytesRead);
                    } catch (Exception e) {
                        System.out.println("Error:" + e);
                        System.exit(-1);
                    }
                }

            } catch (LineUnavailableException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class ListenerResponse extends Thread {
        public void run() {
            Socket socket;
            while (true) {
                try {
                    //Accept the socket connection
                    socket = new Socket(vocalInterfaceIP, vocalInterfacePort);

                    //Generate the Input Streaming and the Input Data Stream
                    InputStream socketIn = socket.getInputStream();
                    DataInputStream dis = new DataInputStream(socketIn);

                    //Receive via socket protocol the size of the Audio Synthesized
                    int size = dis.readInt();

                    //Receive via socket protocol the audio file
                    byte[] bytes = new byte[size];
                    dis.readFully(bytes);

                    //Play audio file with the Sota function
                    CPlayWave.PlayWave(bytes, true);
                    socket.close();

                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }

        }
    }

    static void printIP() throws UnknownHostException {
        /*InetAddress ip = null;
        ip = InetAddress.getLoopbackAddress();
        System.out.println("Local IP: "+ip);*/
        //InetAddress localhost = InetAddress.getLocalHost();
        //System.out.println("System IP Address : " +localhost);
    }

    /*static void publicIP() {
        URL url = null;
        BufferedReader br = null;
        try {
            url = new URL("http://checkip.amazonaws.com/");
            br = new BufferedReader(new InputStreamReader(url.openStream()));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        DocumentReference docRef = firestoreDB.collection("Home").document("Sota");
        ApiFuture<WriteResult> future = null;
        WriteResult result = null;
        try {
            future = docRef.update("hasIP", "pippo");
            result = future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }


    static void initDB() {
        // Fetch the service account key JSON file contents
        FileInputStream serviceAccount = null;
        try {
            serviceAccount = new FileInputStream("/home/root/test/vocalinterface-firebase-adminsdk-3ycvz-8068c39321.json");
            //serviceAccount = new FileInputStream("/Users/tommasaso/Documents/Tesi/IntalliJ/vocalinterface-firebase-adminsdk-3ycvz-8068c39321.json");

            FirestoreOptions optionsFirestore = FirestoreOptions
                    .newBuilder()
                    .setTimestampsInSnapshotsEnabled(true)
                    .build();

            firestoreDB = optionsFirestore.getService();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }*/

}



