/**
 * DNS Client
 * 
 * @author Donya Hojabr, Dylan Havelock
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
            System.out.println("ERROR\tMaximum number of retries " + i + " exceeded");
            System.exit(1);;
        } 

        if (receivePacket == null) {
            System.out.println("ERROR\tFailed to connect to DNS Server");
        } else {
            int answerIndex = sendPacket.getLength(); // answer will begin at this index

            ByteArrayInputStream inputStream = new ByteArrayInputStream(receivePacket.getData());
            DataInputStream dataIn = new DataInputStream(inputStream);

            dataIn.skipBytes(2);
            short header2 = dataIn.readShort();
            int aa = (header2 & 0x0400) >> 10;
            int ra = (header2 & 0x0080) >> 7;
            int rcode = header2 & 0x000f;
            dataIn.skipBytes(2);
            short ancount = dataIn.readShort();
            short nscount = dataIn.readShort();
            short arcount = dataIn.readShort();

            if(ra == 0) {
                System.out.println("ERROR\tThe server does not support recursive queries");
            }
            
            switch(rcode){
                case 0: 
                    break;
                
                case 1:
                    System.out.println("ERROR\tThe name server was unable to interpret the query");
                    System.exit(1);

                case 2: 
                    System.out.println("ERROR\tThe name server was unable to process this query due to a problem with the name server");
                    System.exit(2);

                case 3:
                    System.out.println("NOTFOUND");
                    System.exit(3);

                case 4:
                    System.out.println("ERROR\tThe name server does not support the requested kind of query");
                    System.exit(4);

                case 5:
                    System.out.println("ERROR\tThe name server refuses to perform the requested operation for policy reasons");
                    System.exit(5);
            }
            
            inputStream.close();
            dataIn.close();

            double duration = (endTime - startTime)/1000.0;

            System.out.println("Response receieved after " + duration + " seconds (" + i +" retries)");
            System.out.println("***Answer Section (" + ancount + " records)***");

            for (int record = 0; record < ancount; record++) {
                answerIndex += parseRecord(receivePacket, answerIndex, aa);
            }

            for (int record = 0; record < nscount; record++) {
                answerIndex += getRecordLength(receivePacket, answerIndex);
            }

            // Check for authority
            System.out.println("***Additional Section (" + arcount + " records)***");

            for (int record = 0; record < arcount; record++) {
                answerIndex += parseRecord(receivePacket, answerIndex, aa);
            }
        }

		// Close the socket
		clientSocket.close();
    }

    public static int getRecordLength(DatagramPacket packet, int offset) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData(), offset + 10, packet.getLength()-offset);
        DataInputStream dataIn = new DataInputStream(inputStream);

        short dataLength = dataIn.readShort();
        int recordLength = 12 + dataLength;

        inputStream.close();
        dataIn.close();

        return recordLength;
    }
    
    public static int parseRecord(DatagramPacket packet, int offset, int aa) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData(), offset, packet.getLength()-offset);
        DataInputStream dataIn = new DataInputStream(inputStream);

        short name = dataIn.readShort();
        short type = dataIn.readShort();
        short classIn = dataIn.readShort();
        int ttl = dataIn.readInt();
        short dataLength = dataIn.readShort();
        
        int recordLength = 12 + dataLength;

        if(classIn != 1){
            System.out.println("ERROR\tUnexpected CLASS code");
            System.exit(1);
        }

        String auth = aa == 1 ? "auth" : "nonauth";

        switch(type) {
            case 0x0001: // A
                int data0 = dataIn.readByte() & 0xff;
                int data1 = dataIn.readByte() & 0xff;
                int data2 = dataIn.readByte() & 0xff;
                int data3 = dataIn.readByte() & 0xff;
                System.out.println("IP\t" + data0 + "." + data1 + "." + data2 + "." + data3 + "\t " + ttl + "\t" + auth);
                break;

            case 0x0002: // NS
                offset += 12;
                String nameNs = getName(packet, offset);
                System.out.println("NS\t" + nameNs + "\t" + ttl + "\t" + auth);
                break;

            case 0x000f: // MX
                short preference = dataIn.readShort();
                offset += 14;
                String nameMx = getName(packet, offset);
                System.out.println("MX\t" + nameMx + "\t" + preference + "\t" + ttl + "\t" + auth);
                break;

            case 0x0005: // CNAME
                offset += 12;
                String nameCname = getName(packet, offset);
                System.out.println("CNAME\t" + nameCname + "\t" + ttl + "\t" + auth);
                break;

            default:
                System.out.println("ERROR");
        }

        inputStream.close();
        dataIn.close();

        return recordLength;
    }

    public static String getName(DatagramPacket packet, int offset) {
        String name = "";
        byte[] pointerBytes = packet.getData();
        int num = 0;
        int index = 0;
        while (pointerBytes[offset + index] != 0) {
            if (num == 0) {
                if (index != 0) {
                    name += ".";
                }
                num = pointerBytes[offset + index];
            } else if ((num & 0xc0) == 0xc0) { // check if pointer
                offset = ((num & 0x0000003f) << 8) + (pointerBytes[offset + index] & 0xff );
                num = pointerBytes[offset];
                index = 0;
            } else {
                name += (char)pointerBytes[offset + index];
                num--;
            }
            index++;
        }

        return name;
    }
}
