import java.io.*;
import java.net.*;
import java.util.Random;

public class dnsclient {
    
    public static void main (String argv[]) {
        
        try {
        InetAddress ip;
        BufferedReader br = new BufferedReader(
                                new FileReader("/etc/resolv.conf"));
        
        if(argv.length == 0) {
            br.readLine();
            String[] tokens = br.readLine().split(" ");
            ip = InetAddress.getByName(tokens[1]);
        } else{
            ip = InetAddress.getByName(argv[0]);
        }
        
        DatagramSocket sockfd = new DatagramSocket();
        sockfd.setSoTimeout(2000);
        
        clientRequest request = new clientRequest(sockfd, ip);
        request.createRequest();
        request.sendRequest();
        
        serversResponse sResponse = new serversResponse(sockfd);
        sResponse.recvServersResponse();
        System.out.println(sResponse.toString());
        
        } catch (Exception e) {
            System.out.println("Something went wrong.\n");
            e.printStackTrace();
        }
    }
}

class clientRequest {
    
    public static final int DNS_PORT = 53;
    public static final short CLASS_IN = 1;
    public static final short TYPE_A = 1;
    
    private DatagramSocket socket;
    private InetAddress address;
    private String domainName;
    private DatagramPacket sendPacket;
    
    public clientRequest(DatagramSocket socket, InetAddress address) {
    
        this.socket = socket;
        this.address = address;
        
        domainName = getDomainName();
    }
    
    private String getDomainName() {
        
        BufferedReader inFromUser =
        new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter a domain name: ");
        String domain = "";
        try {
            domain = inFromUser.readLine();
        } catch (IOException e) {
            System.out.println("Could not get domain name.\n");
            e.printStackTrace();
        }
        return domain;
    }
    
    private short generateRandID() {
        Random r  = new Random();
        return (short) r.nextInt();
    }
    
    public void createRequest() throws IOException {
        
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        
        short id = generateRandID();
        short flags = 0;
        flags |= (1 << 8);
        short qcount = 1;
        short ancount = 0;
        short authcount = 0;
        short addcount = 0;
        
        d.writeShort(id);
        d.writeShort(flags);
        d.writeShort(qcount);
        d.writeShort(ancount);
        d.writeShort(authcount);
        d.writeShort(addcount);
        
        String[] labels = domainName.split("\\.");
        
        for(String label : labels){
            d.writeByte(label.length());
            d.writeBytes(label);
        }
        
        d.writeByte(0);
        d.writeShort(TYPE_A);
        d.writeShort(CLASS_IN);
        d.flush();
        
        byte[] sendData = b.toByteArray();
        
    
        sendPacket = new DatagramPacket(
                    sendData,sendData.length,address,DNS_PORT);
    }
    
    public void sendRequest() throws IOException {
        
        try {
            socket.send(sendPacket);
            System.out.println("Sent the query.\n");
        } catch (IOException e) {
            System.out.println("Could not send request. Please make sure IP address is valid.\n");
            System.exit(0);
        }
    }
}

class serversResponse {
    
    private DatagramSocket socket;
    private DatagramPacket recvPacket;
    private byte[] sResponse;
    
    /** current pointer to sResponse */
    private int offset;
    
    private short id;
    private short flags;
    private short qcount;
    private short ancount;
    private short authcount;
    private short addcount;

    
    public serversResponse(DatagramSocket socket) {
        this.socket = socket;
        offset = 0;
    }
    
    public void recvServersResponse() {
        try {
            byte[] recvData = new byte[512];
            recvPacket =
                    new DatagramPacket(recvData, recvData.length);
            socket.receive(recvPacket);
            System.out.println("Received a sResponse");
            sResponse = recvPacket.getData();
        } catch (SocketTimeoutException e) {
            System.out.println("Socket timed out.\n");
            System.exit(0);
        } catch (Exception e) {
            System.out.println("Could not receive packet.\n");
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void parseHeader() {
        id = getShort();
        flags = getShort();
        qcount = getShort();
        ancount = getShort();
        authcount = getShort();
        addcount = getShort();
    }
    
    private short getShort() {
        return (short) (sResponse[offset++] << 8 | (sResponse[offset++] & 0xFF));
    }
    
    private int getInt() {
        return (sResponse[offset++] << 24 |
                (sResponse[offset++] & 0xFF) << 16 |
                (sResponse[offset++] & 0xFF) << 8 |
                (sResponse[offset++] & 0xFF));
    }
    
    private String parseDomainName() {
        
        StringBuilder buffer = new StringBuilder();
        byte next;
        int length = 0;
        
        while(offset < sResponse.length && (next = sResponse[offset++]) != 0) {
            
            length = (next & 0xFF);
            
            if(length > 63) { //get domain name from pointer
                buffer.append(parseDomainName((length & 0x3F) << 8 | sResponse[offset++] & 0xFF));
                break;
            } else {
                for(int i = 0; i < length; i++) {
                    buffer.append((char)sResponse[offset++]);
                }
                buffer.append(".");
            }
        }
        if(buffer.length() > 0)
            buffer.deleteCharAt(buffer.length()-1); // remove last period
        
        return buffer.toString();
    }
    
    private String parseDomainName(int pointer) {
        
        StringBuilder buffer = new StringBuilder();
        byte next;
        int length = 0;
        
        while(pointer < sResponse.length && (next = sResponse[pointer++]) != 0) {
            
            length = (next & 0xFF);
            
            if(length > 63) { 
                buffer.append(parseDomainName((length & 0x3F) << 8 | sResponse[pointer++] & 0xFF));
                break;
            } else {
                for(int i = 0; i < length; i++) {
                    buffer.append((char)sResponse[pointer++]);
                }
                buffer.append(".");
            }
        }
        return buffer.toString();
    }
    
    private String getIPAddress(short n) {
        
        StringBuilder buffer = new StringBuilder();
        
        for(int i = 0; i < n; i++) {
            buffer.append(sResponse[offset++] & 0xFF);
            buffer.append(".");
        }
        if(buffer.length() > 0)
            buffer.deleteCharAt(buffer.length()-1);
        
        return buffer.toString();
    }
    
    private String getQuestions(short n) {
        
        StringBuilder buffer = new StringBuilder();
        buffer.append("\n\nQuestions ("+n+"):\n");
                      
        for(int i = 0; i < n; i++) {
            buffer.append(parseDomainName());
            short s = getShort();
            short s2 = getShort();
            if ( s == 1)
             	buffer.append("\tA");
            else {
            	buffer.append("\t"+ s);
            }
            if (s2 == 1)
            	buffer.append("\tIN\n");
            else{
            	buffer.append("\t"+s2+"\n");
            }
        }
        return buffer.toString();
    }
    
    private String recvServersResponseRecord(short n) {
        
        StringBuilder buffer = new StringBuilder();
        
        for(int i = 0; i < n; i++) {
            buffer.append(parseDomainName()); //name
            short type = getShort();
            buffer.append("\t"+ (type & 0xFFFF)); //class
            buffer.append("\t"+getShort()); //type
            buffer.append("\t"+getInt()); //ttl
            short len = getShort(); 
            buffer.append("\t"+len); // length
            buffer.append("\t"+getIPAddress(len)+"\n"); //data
        }
        return buffer.toString();
    }
    private String getAnswers(short n) {
        String record = "";
        if(n > 0) {
            record = "\n\nAnswers ("+n+"):\n";
            record += recvServersResponseRecord(n);
        }
        return record;
    }
    
    private String getAuthority(short n) {
        String record = "";
        if(n > 0) {
            record = "Authorities ("+n+"): \n";
            record += recvServersResponseRecord(n);
        }
        return record;
    }
    
    private String getAdd(short n) {
        String record = "";
        if(n > 0) {
            record = "Additional ("+n+"): \n";
            record += recvServersResponseRecord(n);
        }
        return record;
    }
    
    public String toString() {
        
        StringBuilder buffer = new StringBuilder();
        parseHeader();
        
        buffer.append("ID: 0x" + Integer.toHexString((id & 0xFFFF)) +"\n");
    
        buffer.append("Flag binary string: "+Integer.toBinaryString(flags & 0xFFFF) + "\n");
        buffer.append("QR: "+ (flags >>> 15 & 1));
        buffer.append("\tOP: "+(flags >>> 11 & 0b1111));
        buffer.append("\tAA: "+(flags >>> 10 & 1));
        buffer.append("\tTC: "+(flags >>> 9 & 1));
        buffer.append("\tRD: "+(flags >>> 8 & 1));
        buffer.append("\tRA: "+(flags >>> 7 & 1));
        buffer.append("\tZ: "+(flags >>> 4 & 0b111));
        buffer.append("\tRCode: "+(flags & 0b1111)+"\n");
        
        buffer.append("\nQuestions: "+qcount);
        buffer.append("\tAnswers: "+ancount);
        buffer.append("\tAuthorities: "+authcount);
        buffer.append("\tAdditional: "+addcount);
        buffer.append("\n");
        
        buffer.append(getQuestions(qcount));
        buffer.append(getAnswers(ancount));
        buffer.append(getAuthority(authcount));
        buffer.append(getAdd(addcount));
        buffer.append("\n");
        
        return buffer.toString();
    }
}

