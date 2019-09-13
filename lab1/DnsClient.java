/**
 * DNS Client
 * 
 * @author michaelrabbat, adapted from code provided by Jun Ye Yu
 */

import java.io.*;
import java.net.*;
import java.util.Random;

public class DnsClient {
	public static void main(String args[]) throws Exception
	{
        // Random number generator
        Random rand = new Random();
        short queryId = (short) rand.nextInt(Short.MAX_VALUE + 1);

        // Stream to contruct data to send to DNS
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(outputStream);

        // default arguments
        int timeout = 5;
        int maxRetries = 3;
        int port = 53;
        String domainName = "";
        short qType = 0x0001;

		// Create a UDP socket
		DatagramSocket clientSocket = new DatagramSocket(port);
		
		// Create byte array for IP address
        byte[] ipAddressByte = new byte[]{0, 0, 0, 0};

        // Byte arrays for send and receive data
        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];

        // Datagram packet objects for sending/receiving
        DatagramPacket sendPacket = null;
        DatagramPacket receivePacket = null;

        // Parse input
        for (int i = 0; i < args.length; i++) {

            // Check if it is an option switch
            if (args[i].substring(0, 1).equals("-")) {

                // timeout
                if (args[i].substring(1, 2).equals("t")) {
                    i++;
                    timeout = Integer.parseInt(args[i]);
                }

                // max retries
                else if (args[i].substring(1, 2).equals("r")) {
                    i++;
                    timeout = Integer.parseInt(args[i]);
                }

                // port
                else if (args[i].substring(1, 2).equals("p")) {
                    i++;
                    timeout = Integer.parseInt(args[i]);
                }

                // type mail server
                else if (args[i].substring(1, 3).equals("mx")) {
                    qType = 0x000f;
                }

                // type name server
                else if (args[i].substring(1, 3).equals("ns")) {
                    qType = 0x0002;
                }
            } 
            
            // DNS server ip
            else if (args[i].substring(0, 1).equals("@")) {
                args[i] = args[i].substring(1);
                String[] ipAddrString = args[i].split("\\.");

                for (int num = 0; num < ipAddrString.length; num++) {
                    ipAddressByte[num] = (byte) Integer.parseInt(ipAddrString[num]);
                }

                i++;

                domainName = args[i];
            }
        }

        InetAddress ipDns = InetAddress.getByAddress(ipAddressByte);

        // header
        data.writeShort(queryId); // id
        data.writeShort(0x0100);  // line 2
        data.writeShort(0x0001);  // QDCOUNT
        data.writeShort(0x0000);  // ANCOUNT
        data.writeShort(0x0000);  // NSCOUNT
        data.writeShort(0x0000);  // ARCOUNT

        // QNAME

        String[] labels = domainName.split("\\."); // assuming max 10 labels
        for (String label : labels) {
            data.writeByte(label.length());
            for (int i = 0; i < label.length(); i++) {
                data.writeByte(label.charAt(i));
            }
        }
        data.writeByte(0); // terminating character

        // QTYPE
        data.writeShort(qType);

        // QCLASS
        data.writeShort(0x0001);

        data.flush();
        data.close();

        sendData = outputStream.toByteArray();

        for (int i = 0; i < maxRetries; i++) {

            sendPacket = new DatagramPacket(sendData, sendData.length, ipDns, port);
            clientSocket.send(sendPacket);

            long currentTime = System.currentTimeMillis();
            long timeoutTime = currentTime + timeout*1000;

            while (timeoutTime - currentTime > 0 && receivePacket == null) {
                currentTime = System.currentTimeMillis();
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);
            }

            if (receivePacket != null) break;
        }

        if (receivePacket == null) {
            System.out.println("Failed to connect to DNS Server");
        } else {
            String modifiedSentence = new String(receivePacket.getData());
		    System.out.println("From Server: " + modifiedSentence);
        }

		// Close the socket
		clientSocket.close();
		
	}
}
