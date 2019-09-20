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
        DataOutputStream dataOut = new DataOutputStream(outputStream);

        // default arguments
        int timeout = 5;
        int maxRetries = 3;
        int port = 53; 
        String domainName = "";
        String server = "";
        short qType = 0x0001;
        String qTypeStr = "A";
		
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
                    qTypeStr = "MX";
                }

                // type name server
                else if (args[i].substring(1, 3).equals("ns")) {
                    qType = 0x0002;
                    qTypeStr = "NS";
                }
            } 
            
            // DNS server ip
            else if (args[i].substring(0, 1).equals("@")) {
                args[i] = args[i].substring(1);
                server = args[i];
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
        dataOut.writeShort(queryId); // id
        dataOut.writeShort(0x0100);  // line 2
        dataOut.writeShort(0x0001);  // QDCOUNT
        dataOut.writeShort(0x0000);  // ANCOUNT
        dataOut.writeShort(0x0000);  // NSCOUNT
        dataOut.writeShort(0x0000);  // ARCOUNT

        // QNAME

        String[] labels = domainName.split("\\."); // assuming max 10 labels
        for (String label : labels) {
            dataOut.writeByte(label.length());
            for (int i = 0; i < label.length(); i++) {
                dataOut.writeByte(label.charAt(i));
            }
        }
        dataOut.writeByte(0); // terminating character

        // QTYPE
        dataOut.writeShort(qType);

        // QCLASS
        dataOut.writeShort(0x0001);

        dataOut.flush();
        dataOut.close();
        outputStream.close();

        sendData = outputStream.toByteArray();

        System.out.println("DnsClient sending request for " + domainName);
        System.out.println("Server: " + server);
        System.out.println("Request type: " + qTypeStr);

        // Create a UDP socket
		DatagramSocket clientSocket = new DatagramSocket(1024);
        int i;
        long startTime = 0; 
        long endTime = 0;

         for (i = 0; i < maxRetries; i++) {
        
            sendPacket = new DatagramPacket(sendData, sendData.length, ipDns, port);
            clientSocket.send(sendPacket);
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.setSoTimeout(timeout*1000);
            
            try {
                startTime = System.currentTimeMillis();
                clientSocket.receive(receivePacket);
                endTime = System.currentTimeMillis();
            } catch (Exception e) {
                continue;
            }    
            
           if (receivePacket != null) break;
        }

        if(i == maxRetries){
            System.out.println("ERROR   Maximum number of retries " + i + " exceeded");
            System.exit(1);;
        } 

        if (receivePacket == null) {
            System.out.println("Failed to connect to DNS Server");
        } else {
            int answerIndex = sendPacket.getLength(); // answer will begin at this index

            ByteArrayInputStream inputStream = new ByteArrayInputStream(receivePacket.getData());
            DataInputStream dataIn = new DataInputStream(inputStream);

            dataIn.skipBytes(2);
            short header2 = dataIn.readShort();
            int aa = (header2 & 0x0100) >> 8;
            int ra = (header2 & 0x0080) >> 7;
            int rcode = header2 & 0x000f;
            dataIn.skipBytes(2);
            short ancount = dataIn.readShort();
            short nscount = dataIn.readShort();
            short arcount = dataIn.readShort();

            // dataIn.skipBytes(answerIndex-12);
            
            // short name = dataIn.readShort();
            // short type = dataIn.readShort();
            // short classIn = dataIn.readShort();
            // int ttl = dataIn.readInt();
            // short dataLength = dataIn.readShort();
            // int data0 = dataIn.readByte() & 0xff;
            // int data1 = dataIn.readByte() & 0xff;
            // int data2 = dataIn.readByte() & 0xff;
            // int data3 = dataIn.readByte() & 0xff;

            inputStream.close();
            dataIn.close();

            double duration = (endTime - startTime)/1000.0;

            System.out.println("Response receieved after " + duration + " seconds (" + i +" retries)");
            System.out.println("*** Answer Section (" + ancount + " records)***");
            // String auth = aa == 1 ? "auth" : "nonauth";
            // System.out.println("IP  " + data0 + "." + data1 + "." + data2 + "." + data3 + "\t " + ttl + "\t" + auth);

            // switch(type) {
            //     case 0x0001: // A
            //         System.out.println("IP\t " + data0 + "." + data1 + "." + data2 + "." + data3 + "\t " + ttl + "\t" + auth);
            //         break;
            //     case 0x0002: // NS
            //         System.out.println("NS\t");
            //         break;
            //     case 0x000f: // MX
            //         break;
            //     case 0x0005: //CNAME
            //         break;

            // }

            parseRecord(receivePacket, answerIndex, aa);

            System.out.println("AA: " + aa);
            System.out.println("RA: " + ra);
            System.out.println("RCODE: " + rcode);
            System.out.println("ANCOUNT: " + ancount);
            System.out.println("NSCOUNT: " + nscount);
            System.out.println("ARCOUNT: " + arcount);
        }

		// Close the socket
		clientSocket.close();
		
    }
    
    public static void parseRecord(DatagramPacket packet, int offset, int aa) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData(), offset, packet.getLength()-offset);
        DataInputStream dataIn = new DataInputStream(inputStream);

        short name = dataIn.readShort();
        short type = dataIn.readShort();
        short classIn = dataIn.readShort();
        int ttl = dataIn.readInt();
        short dataLength = dataIn.readShort();
        int data0 = dataIn.readByte() & 0xff;
        int data1 = dataIn.readByte() & 0xff;
        int data2 = dataIn.readByte() & 0xff;
        int data3 = dataIn.readByte() & 0xff;

        String auth = aa == 1 ? "auth" : "nonauth";

        switch(type) {
            case 0x0001: // A
                System.out.println("IP\t " + data0 + "." + data1 + "." + data2 + "." + data3 + "\t " + ttl + "\t" + auth);
                break;
            case 0x0002: // NS
                System.out.println("NS\t");
                break;
            case 0x000f: // MX
                break;
            case 0x0005: //CNAME
                break;
        }

        inputStream.close();
        dataIn.close();
    }
}
