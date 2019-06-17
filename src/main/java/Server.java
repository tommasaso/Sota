import jp.vstone.RobotLib.*;
import jp.vstone.sotatalk.MotionAsSotaWish;
import org.apache.commons.io.IOUtils;

import java.awt.*;
import java.io.*;
import java.net.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;

import java.util.Enumeration;

import javax.sound.sampled.*;

public class Server {
    static String vocalInterfaceIP = "";
    static Integer vocalInterfacePort = 9000;//9000;
    static short soundPower = 0;
    static boolean speaking = false;


    public static void main(String args[]) throws IOException {
        /*initDB();
        publicIP();*/
        //init();
        new Thread(Server::init2).start();

        String sotaIP = getIP();
        pucbishIP(sotaIP);
        Long end = System.currentTimeMillis() + 60000;
        String VI;
        do {
            VI = getVocalInterfaceIP();
            if (System.currentTimeMillis() > end) {
                break;
            }
        }while (VI == "");
        vocalInterfaceIP = VI;


        if(args.length != 0){
            vocalInterfaceIP = args[0];
        }

        System.out.println("vocalInterfaceIP= "+vocalInterfaceIP);

        SenderMic senderMic = new SenderMic();
        senderMic.start();
        ListenerResponse listenerResponse = new ListenerResponse();
        listenerResponse.start();

        new Thread(Server::listening).start();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true){
            if(br.readLine().equals("exit")){

            }

        }



    }

    static class SenderMic extends Thread {
        public void run() {
            //Initialize variables needed for microphone acquisition
            TargetDataLine microphone;
            SourceDataLine speakers;

            try {
                // Open the socket connection
                ServerSocket serverSocket = new ServerSocket(8600);//9980);

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

                short max;

                //Until the Microphone connection is open...
                while (targetDataLine.isOpen()) {
                    try {
                        //Acquisition of audio and save it in the buffer
                        Integer numBytesRead = targetDataLine.read(data, 0, data.length);

                        if(numBytesRead>=0){
                            max = (short) (data[0]+ (data[1] << 8));
                            for (int p=2;p<numBytesRead-1;p+=2) {
                                short thisValue = (short) (data[p] + (data[p+1] << 8));
                                if (thisValue>max) max=thisValue;
                            }
                            //System.out.println("Max value is "+max);
                            soundPower = max;
                        }

                        //Send the audio through the socket connection
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
            //Socket socket;
            while (true) {
                try {
                    //System.out.println("VI ip: "+vocalInterfaceIP+"     VI port: "+vocalInterfacePort);
                        //Accept the socket connection
                        Socket socket = new Socket(vocalInterfaceIP, vocalInterfacePort);

                        System.out.println("Socket accepted");
                        //Generate the Input Streaming and the Input Data Stream
                        InputStream socketIn = socket.getInputStream();
                        DataInputStream dis = new DataInputStream(socketIn);

                        //Receive via socket protocol the size of the Audio Synthesized
                        int size = dis.readInt();
                        System.out.println("ArraySize = " + size);

                        //Receive via socket protocol the audio file
                        byte[] bytes = new byte[size];
                        dis.readFully(bytes);

                        boolean understood = dis.readBoolean();

                        //Play audio file with the Sota function
                        speaking = true;
                        play(bytes, understood);

                        socket.close();
                        speaking = false;

                } catch (IOException e) {
                    //e.printStackTrace();
                    //System.out.println("failed");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
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
            System.out.println("Vocal InterfaceIP: "+theString);
            return theString;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";

    }

    private static void play(byte[] bytes,boolean understood){
        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if(mem.Connect()) {

            motion.ServoOn();
            CRobotPose pose = new CRobotPose();
            MotionAsSotaWish move = new MotionAsSotaWish(motion);
            //move.setLEDColorMot(Color.BLACK);
            pose.setLED_Sota(Color.CYAN, Color.CYAN, 255, Color.CYAN);
            motion.play(pose, 500);
            Thread speakThread = new Thread() {
                @Override
                public void run() {
                    super.run();
                    System.out.println("sayFile...");
                    //move.SayFile("ciaoMondo.wav", "ciao");
                    CPlayWave.PlayWave(bytes, true);
                    System.out.println("FInished to play");
                }
            };
            Thread moveThread = new Thread() {
                @Override
                public void run() {
                    super.run();
                    int wait = (int) (bytes.length * 0.03);
                    move.play("scene", wait);
                }
            };
            speakThread.start();
            moveThread.start();

            System.out.println("While started");
            Color mainColor = Color.BLUE;
            if(!understood){
                mainColor = new Color(247,90,0);
            }
            while (speakThread.isAlive()) {
                pose.setLED_Sota(mainColor, mainColor, 255, mainColor);
                motion.play(pose, 500);
                CRobotUtil.wait(500);
                pose.setLED_Sota(Color.WHITE, Color.WHITE, 255, Color.WHITE);
                motion.play(pose, 300);
                CRobotUtil.wait(300);
            }
            pose.setLED_Sota(mainColor, mainColor, 255, mainColor);
            motion.play(pose, 500);
            CRobotUtil.wait(500);
            pose.setLED_Sota(Color.WHITE, Color.WHITE, 255, Color.WHITE);
            motion.play(pose, 300);

        }

    }

    private static void listening() {
        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if(mem.Connect()) {
            CRobotPose pose = new CRobotPose();
            while (true) {
                while (soundPower > 1300 && !speaking) {
                    pose.setLED_Sota(Color.BLACK, Color.BLACK, 255, Color.BLACK);
                    motion.play(pose, 500);
                    CRobotUtil.wait(500);
                    pose.setLED_Sota(Color.WHITE, Color.WHITE, 255, Color.WHITE);
                    motion.play(pose, 300);
                    CRobotUtil.wait(300);
                }
            }
        }
    }

    private static void init2() {
        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if(mem.Connect()) {
            CRobotPose pose = new CRobotPose();
                pose.setLED_Sota(Color.WHITE, Color.WHITE, 255, Color.WHITE);
                motion.play(pose, 300);
                CRobotUtil.wait(300);
        }
    }
    private static void exit() {
        CRobotMem mem = new CRobotMem();
        CSotaMotion motion = new CSotaMotion(mem);
        if(mem.Connect()) {
            CRobotPose pose = new CRobotPose();
            pose.setLED_Sota(Color.BLACK, Color.BLACK, 255, Color.BLACK);
            motion.play(pose, 600);
            CRobotUtil.wait(600);
        }
    }

    private static void init(){
        new Thread(() -> {

        }).start();

    }


}



