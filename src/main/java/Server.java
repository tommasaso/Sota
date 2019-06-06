

import jp.vstone.RobotLib.CPlayWave;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.Format;
import java.net.NetworkInterface;

import java.util.Enumeration;
import java.util.concurrent.ExecutionException;

import javax.sound.sampled.*;


public class Server {
    static String vocalInterfaceIP = "172.20.31.106";
    static Integer vocalInterfacePort = 8450;


    public static void main(String args[]) throws IOException {
        /*initDB();
        publicIP();*/

        String sotaIP = getIP();
        pucbishIP(sotaIP);
        Long end = System.currentTimeMillis() + 10000;
        String VI;
        while((VI = getVocalInterfaceIP()).equals("")){
            vocalInterfaceIP = VI;
            if(System.currentTimeMillis() > end) {
                break;
            }
        }

        if(args.length != 0){
            vocalInterfaceIP = args[0];
        }

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
                targetDataLine.close();
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

    static String getIP(){
        String ip;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // filters out 127.0.0.1 and inactive interfaces
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    ip = addr.getHostAddress();
                    System.out.println(iface.getDisplayName() + " " + ip);
                    return ip;
                }
            }
            return "failed ip acquisition";
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

    }

    static void pucbishIP(String ip){
        URL url2 = null;
        try {
            url2 = new URL("https://vocalinterface.firebaseio.com/installation_test_name/VocalInterface.json");
            HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();

            //conn2.requestMethod = "PATCH"
            conn2.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conn2.setDoOutput(true);
            conn2.setRequestProperty("Content-Type", "application/json");
            conn2.setRequestProperty("Accept", "application/json");
            String input = "{\"SotaHasIP\": \""+ip +"\"}";

            OutputStream os = conn2.getOutputStream();
            os.write(input.getBytes());
            os.flush();

            if (conn2.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn2.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn2.getInputStream())));

            String output;
            System.out.println("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                //System.out.println(output);
                System.out.println("SotaIP published: "+ ip);
            }
            conn2.disconnect();


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static String getVocalInterfaceIP(){

        HttpURLConnection conn = null;
        try {
            StringBuilder result = new StringBuilder();
            URL url = new URL("https://vocalinterface.firebaseio.com/installation_test_name/VocalInterface/ViHasIP.json");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            StringWriter writer = new StringWriter();
            IOUtils.copy(conn.getInputStream(), writer);
            String theString = writer.toString().replace("\"", "");
            System.out.println("Vocal InterfaceIP"+theString);
            return theString;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";

    }


}



