/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.distri.communication.bridge;

import com.distri.communication.multicast.MulticastManager;
import com.distri.communication.multicast.MulticastManagerCallerInterface;
import com.distri.communication.tcp.TCPServiceManager;
import com.distri.communication.tcp.TCPServiceManagerCallerInterface;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author eduar
 */
public class BridgeManager implements TCPServiceManagerCallerInterface, MulticastManagerCallerInterface {
    
    public static int MTU;
    
    TCPServiceManager tcpServiceManager;
    MulticastManager multicastManager;
    
    ArrayList<byte[]> dataToBeSent;
    
    public BridgeManager() {
        try {
            BridgeManager.MTU = Integer.parseInt(
                    (new BufferedReader(new FileReader(
                            Paths.get("src/com/distri/resources/config/MTU.config").toAbsolutePath().toString())
                    )).readLine()
            );
            this.tcpServiceManager = new TCPServiceManager(this);
            this.multicastManager = new MulticastManager("224.0.0.10", 9091, this, BridgeManager.MTU);
            //sendString("C0/"+ BridgeManager.MTU+"/basura");
        }catch (Exception ex) {
            System.err.println(ex);
        }
    }
    
    public static void main(String[] args) {
        new BridgeManager();
    }

    @Override
    public void reSendFileReceivedFromClient(Socket clientSocket) {
        try {
            dataToBeSent = new ArrayList<>();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(clientSocket.getInputStream());
            
            byte[] buffer = new byte[BridgeManager.MTU];

            Object object = objectInputStream.readObject();
            
            String nameFile = "";
            if(object instanceof String) {
                nameFile = object.toString();
            }else {
                errorOnTCPServiceManager(new Exception("Something is wrong"));
            }

            int dataCounter = 0;
            Integer bytesRead = 0;

            do {
                object = objectInputStream.readObject();
                if (!(object instanceof Integer)) {
                    errorOnTCPServiceManager(new Exception("Something is wrong"));
                }
                bytesRead = (Integer) object;
                object = objectInputStream.readObject();
                if (!(object instanceof byte[])) {
                    errorOnTCPServiceManager(new Exception("Something is wrong"));
                }
                buffer = (byte[]) object;
                dataToBeSent.add(Arrays.copyOf(buffer, bytesRead));
                dataCounter++;
            }while (bytesRead == BridgeManager.MTU);
            
            String controlString = "P0/" + nameFile + "/" + dataCounter + "/" + bytesRead + "/";
            multicastManager.sendData(controlString.getBytes());
            
            for (byte[] data : dataToBeSent) {
                multicastManager.sendData(data);
            }
            
            System.out.println("File: " + nameFile + " resent successfully!");
            
            objectInputStream.close();
            objectOutputStream.close();
        }catch (Exception ex) {
            errorOnTCPServiceManager(ex);
        }
    }

    @Override
    public void errorOnTCPServiceManager(Exception ex) {
        System.err.println(ex);
    }

    @Override
    public void dataReceived(String sourceIpAddressOrHost, int sourcePort, byte[] data) {
        String controlString = new String(data);
        String[] controlData = controlString.split("/");
        
        if(controlData[0].equals("HI")) {
            try (BufferedReader br = new BufferedReader(new FileReader("../WebServiceLoadBalancer/hosts.txt"))) {

                String strCurrentLine;
                while ((strCurrentLine = br.readLine()) != null) {
                    if (strCurrentLine.equals(controlData[1])){
                        return;
                    }
                }
            } catch (IOException e) {
                System.err.println(e);
            }
            try(FileWriter fw = new FileWriter("../WebServiceLoadBalancer/hosts.txt", true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
            {
                out.println(controlData[1]);
                out.close();
                fw.close();
                System.out.println(controlData[1] + " added to the hosts.txt file");
            } catch (IOException e) {
                System.err.println(e);
            }
           
        }
    }

    @Override
    public void errorOnMulticastManager(Exception ex) {
        System.err.println(ex);
    }

    @Override
    public void sendString(String data) {
        multicastManager.sendData(data.getBytes());
    }
    
}
