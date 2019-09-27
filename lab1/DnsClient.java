
/**
 * DNS Client
 * 
 * @author Donya Hojabr, Dylan Havelock
 */

import java.io.*;
import java.net.*;
import java.util.Random;

public class DnsClient {

    // Request Parameters
    private short queryId;
    private int timeout;
    private int maxRetries;
    private int retries;
    private int port;
    private String domainName;
    private String server;
    private short qType;
    private String qTypeStr;
    private byte[] ipAddressByte;
    private InetAddress ipDns;
    private double duration;
    
    // Datagram packet objects for sending/receiving
    private DatagramPacket sendPacket = null;
    private DatagramPacket receivePacket = null;

    public DnsClient(String[] args) {
        // Set Defaults
        timeout = 5;
        maxRetries = 3;
        retries = 0;
        port = 53;
        domainName = "";
        server = "";
        qType = 0x0001;
        qTypeStr = "A";
        ipAddressByte = new byte[] { 0, 0, 0, 0 };
        ipDns = null;
        duration = 0;

        this.parseInput(args);
        Random rand = new Random();
        queryId = (short) rand.nextInt(Short.MAX_VALUE + 1);
    }

    public static void main(String args[]) throws Exception {

        // Create dnsClient object based on input arguments
        DnsClient dnsClient = new DnsClient(args);

        // construct the dns request
        byte[] sendData = dnsClient.constructRequest();

        // send dns request
        dnsClient.sendRequest(sendData);

        // process the dns response packet
        dnsClient.processResponsePacket();
    }

    public void parseInput(String[] args) {
        for (int i = 0; i < args.length; i++) {

            // Check if it is an option switch
            if (args[i].substring(0, 1).equals("-")) {

                // timeout
                if (args[i].length() >= 2 && args[i].substring(1, 2).equals("t")) {
                    i++;
                    try {
                        timeout = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR\tIncorrect input format. Use: java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name");
                        System.exit(1);
                    }
                }

                // max retries
                else if (args[i].length() >= 2 && args[i].substring(1, 2).equals("r")) {
                    i++;
                    try {
                        maxRetries = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR\tIncorrect input format. Use: java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name");
                        System.exit(1);
                    }
                }

                // port
                else if (args[i].length() >= 2 && args[i].substring(1, 2).equals("p")) {
                    i++;
                    try {
                        port = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR\tIncorrect input format. Use: java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name");
                        System.exit(1);
                    }
                }

                // type mail server
                else if (args[i].length() >= 3 && args[i].substring(1, 3).equals("mx")) {
                    qType = 0x000f;
                    qTypeStr = "MX";
                }

                // type name server
                else if (args[i].length() >= 3 && args[i].substring(1, 3).equals("ns")) {
                    qType = 0x0002;
                    qTypeStr = "NS";
                } 

                // incorrect input error
                else {
                    System.out.println("ERROR\tIncorrect input format. Use: java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name");
                    System.exit(1);
                }
            }

            // DNS server ip
            else if (args[i].substring(0, 1).equals("@")) {
                args[i] = args[i].substring(1);
                server = args[i];
                String[] ipAddrString = args[i].split("\\.");

                if (ipAddrString.length != 4) {
                    System.out.println("ERROR\tInvalid IP Address");
                    System.exit(1);
                }

                for (int num = 0; num < ipAddrString.length; num++) {
                    int ipByte = Integer.parseInt(ipAddrString[num]);
                    if (ipByte > 255) {
                        System.out.println("ERROR\tInvalid IP Address");
                        System.exit(1);
                    }
                    ipAddressByte[num] = (byte) ipByte;
                }

                try {
                    ipDns = InetAddress.getByAddress(ipAddressByte);
                } catch (UnknownHostException e) {
                    System.out.println("ERROR\tIP Address is of illegal length");
                }
                
                i++;

                domainName = args[i];
            }

            // incorrect input error
            else {
                System.out.println("ERROR\tIncorrect input format. Use: java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name");
                System.exit(1);
            }
        }
    }

    public byte[] constructRequest() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(outputStream);

        // header
        dataOut.writeShort(queryId); // id
        dataOut.writeShort(0x0100); // line 2
        dataOut.writeShort(0x0001); // QDCOUNT
        dataOut.writeShort(0x0000); // ANCOUNT
        dataOut.writeShort(0x0000); // NSCOUNT
        dataOut.writeShort(0x0000); // ARCOUNT

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

        return outputStream.toByteArray();
    }

    public void sendRequest(byte[] sendData) throws IOException {
        // Create a UDP socket
        DatagramSocket clientSocket = new DatagramSocket(1024);
        byte[] receiveData = new byte[1024];

        long startTime = 0;
        long endTime = 0;

        startTime = System.currentTimeMillis();

        // Create and Send Packet
        for (retries = 0; retries < maxRetries; retries++) {

            sendPacket = new DatagramPacket(sendData, sendData.length, ipDns, port);
            clientSocket.send(sendPacket);
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.setSoTimeout(timeout * 1000);

            try {
                clientSocket.receive(receivePacket);
            } catch (Exception e) {
                continue;
            }

            if (receivePacket != null)
                break;
        }

        endTime = System.currentTimeMillis();

        // Close the socket
        clientSocket.close();

        duration = (double) (endTime - startTime)/1000.0;

        System.out.println("DnsClient sending request for " + domainName);
        System.out.println("Server: " + server);
        System.out.println("Request type: " + qTypeStr);

        if (retries == maxRetries) {
            System.out.println("ERROR\tMaximum number of retries " + retries + " exceeded");
            System.exit(1);
        }
    }

    public void processResponsePacket() throws IOException {
        if (receivePacket == null) {
            System.out.println("ERROR\tFailed to connect to DNS Server");
            System.exit(1);
        } else {
            int answerIndex = sendPacket.getLength(); // answer will begin at this index

            ByteArrayInputStream inputStream = new ByteArrayInputStream(receivePacket.getData());
            DataInputStream dataIn = new DataInputStream(inputStream);

            short responseId = dataIn.readShort();

            // Check that response ID matches the query ID
            if (responseId != queryId) {
                System.out.println("ERROR\tInvalid response ID");
                System.exit(1);
            }

            short header2 = dataIn.readShort();
            int aa = (header2 & 0x0400) >> 10;
            int ra = (header2 & 0x0080) >> 7;
            int rcode = header2 & 0x000f;
            dataIn.skipBytes(2);
            short ancount = dataIn.readShort();
            short nscount = dataIn.readShort();
            short arcount = dataIn.readShort();

            inputStream.close();
            dataIn.close();

            if (ra == 0) {
                System.out.println("ERROR\tThe server does not support recursive queries");
                System.exit(1);
            }

            processRCode(rcode);

            System.out.println("Response received after " + duration + " seconds (" + retries + " retries)");
            System.out.println("***Answer Section (" + ancount + " records)***");

            // Parse Answer Records
            for (int record = 0; record < ancount; record++) {
                answerIndex += parseRecord(receivePacket, answerIndex, aa);
            }

            // Skip over authority section
            for (int record = 0; record < nscount; record++) {
                answerIndex += getRecordLength(receivePacket, answerIndex);
            }

            System.out.println("***Additional Section (" + arcount + " records)***");

            // Parse Additional Records
            for (int record = 0; record < arcount; record++) {
                answerIndex += parseRecord(receivePacket, answerIndex, aa);
            }
        }
    }

    private static void processRCode(int rcode) {
        switch (rcode) {
        case 0:
            break;

        case 1:
            System.out.println("ERROR\tThe name server was unable to interpret the query");
            System.exit(1);

        case 2:
            System.out.println(
                    "ERROR\tThe name server was unable to process this query due to a problem with the name server");
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
    }

    private static int parseRecord(DatagramPacket packet, int offset, int aa) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData(), offset,
                packet.getLength() - offset);
        DataInputStream dataIn = new DataInputStream(inputStream);

        short name = dataIn.readShort();
        short type = dataIn.readShort();
        short classIn = dataIn.readShort();
        int ttl = dataIn.readInt();
        short dataLength = dataIn.readShort();

        int recordLength = 12 + dataLength;

        if (classIn != 1) {
            System.out.println("ERROR\tUnexpected CLASS code");
            System.exit(1);
        }

        String auth = aa == 1 ? "auth" : "nonauth";

        switch (type) {
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
            System.out.println("ERROR\tInvalid record type");
        }

        inputStream.close();
        dataIn.close();

        return recordLength;
    }

    private static String getName(DatagramPacket packet, int offset) {
        String name = "";
        byte[] pointerBytes = packet.getData();
        int labelLen = 0; // tracks the remaing length of a label
        int index = 0; // index of a label

        while (pointerBytes[offset + index] != 0) {
            // Check if the label has been read through
            if (labelLen == 0) {
                if (index != 0) {
                    name += ".";
                }
                labelLen = pointerBytes[offset + index];
            } 
            
            // Check if there is a pointer
            else if ((labelLen & 0xc0) == 0xc0) {
                offset = ((labelLen & 0x0000003f) << 8) + (pointerBytes[offset + index] & 0xff);
                labelLen = pointerBytes[offset];
                index = 0;
            } 
            
            // read in the characters of the label
            else {
                name += (char) pointerBytes[offset + index];
                labelLen--;
            }
            index++;
        }

        return name;
    }

    private static int getRecordLength(DatagramPacket packet, int offset) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData(), offset + 10,
                packet.getLength() - offset);
        DataInputStream dataIn = new DataInputStream(inputStream);

        short dataLength = dataIn.readShort();
        int recordLength = 12 + dataLength;

        inputStream.close();
        dataIn.close();

        return recordLength;
    }
}
