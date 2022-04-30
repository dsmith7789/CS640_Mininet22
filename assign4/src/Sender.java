import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.io.File;

public class Sender {

    // variables we need to track that aren't supplied by the user
    private int sequenceNumber;
    private int totalBytesOfDataSent;
    private int totalPacketsSent;
    private int totalPacketsReceived;
    private int totalDuplicateAcknowledgements;
    private int totalRetransmissions;
    private int lastReceivedAckNumber;
    private int lastReceivedAckOccurrences;
    private ArrayList<TCPSegment> buffer;
    private DatagramSocket socket;
    private long estRTT;
    private long estDev;
    private File file;
    private FileInputStream fileInputStream;
    protected int maxPayload;
    protected int maxTcpSegment;

    // variables the user provides
    protected int port;           // the port at which this sender will run
    protected InetAddress remoteIP;       // IP address of the receiver
    protected int remotePort;     // port at which the remote receiver is running
    protected String filename;    // file we're sending
    protected int mtu;            // max trans unit (in bytes)
    protected int sws;            // sliding window size (number of segments)

    // constructor
    public Sender(int port, String remoteIP, int remotePort, String filename, int mtu, int sws) {
        this.port = port;
        try {
            this.remoteIP = InetAddress.getByName(remoteIP);
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }
        this.remotePort = remotePort;
        this.filename = filename;
        this.file = new File(this.filename);
        try {
        this.fileInputStream = new FileInputStream(this.file);
        } catch (FileNotFoundException e) {
            System.out.println("Couldn't create file.");
            System.exit(1);
        }
        this.mtu = mtu;
        this.maxPayload = this.mtu - 52;    // 52 bytes = IP + UDP + TCP header size in bytes
        this.maxTcpSegment = this.mtu - 28;  // 28 bytes = IP + UDP header size in bytes
        this.sws = sws;
        this.buffer = new ArrayList<TCPSegment>();
        this.sequenceNumber = 0;
    }

    // "GETTER" methods

    /**
     * Retrieves the sequence number of the next segment the receiver will send
     * @return the sequence number for the next segment the receiver will send
     */
    public int getSequenceNumber() {
        return this.sequenceNumber;
    }

    /**
     * Retrieves how many BYTES of data the SENDER sent over this connection.
     * @return the total number of bytes sent by the sender
     */
    public int getTotalBytesOfDataSent() {
        return this.totalBytesOfDataSent;
    }

    /**
     * Retrieves how many PACKETS of data the SENDER sent over this connection.
     * @return the total number of packets sent by the sender
     */
    public int getTotalPacketsSent() {
        return this.totalPacketsSent;
    }

    /**
     * Retrieves how many times we received a duplicate acknowledgement while this connection was active.
     * @return the times we received a duplicate acknowledgement while this connection was active
     */
    public int getTotalDuplicateAcknowledgements() {
        return this.totalDuplicateAcknowledgements;
    }

    /**
     * Retrieves how many times we had to resend a packet while the connection was active.
     * @return the times we had to resend a packet while the connection was active
     */
    public int getTotalRetransmissions() {
        return this.totalRetransmissions;
    }

    /**
     * Retrieves the last ACK number we received from the sender. Helps to track duplicate ACKs
     * @return the last ACK number we received from the sender
     */
    public int getLastReceivedAckNumber() {
        return this.lastReceivedAckNumber;
    }

    /**
     * Retrieves how many times we've received the current last ACK. If this exceeds 2, we can assume we lost a packet and 
     * need to retransmit it.
     * @return how many times we've received the current last ACK
     */
    public int getLastReceivedAckOccurrences() {
        return this.lastReceivedAckOccurrences;
    }

    /**
     * Lets us access the sender's buffer, which is where we will store packets that we've sent, until we get a corresponding 
     * acknowledgement. The buffer is also useful for tracking congestion.
     * @return the times we had to resend a packet while the connection was active
     */
    public ArrayList<TCPSegment> getBuffer() {
        return this.buffer;
    }

    /**
     * Retrieve the socket the sender is sending segments over
     * @return
     */
    public DatagramSocket getSocket() {
        return this.socket;
    }

    public File getFile() {
        return this.file;
    }


    // "SETTER" methods

    /**
     * Sets the sequence number of the next segment the receiver will send
     */
    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * sets how many BYTES of data the SENDER sent over this connection.
     * @return the total number of bytes sent by the sender
     */
    public void setTotalBytesOfDataSent(int newTotal) {
        this.totalBytesOfDataSent = newTotal;
    }

    /**
     * sets how many PACKETS of data the SENDER sent over this connection.
     */
    public void setTotalPacketsSent(int newTotal) {
        this.totalPacketsSent = newTotal;
    }

    /**
     * sets how many times we received a duplicate acknowledgement while this connection was active.
     */
    public void setTotalDuplicateAcknowledgements(int newTotal) {
        this.totalDuplicateAcknowledgements = newTotal;
    }

    /**
     * sets how many times we had to resend a packet while the connection was active.
     */
    public void setTotalRetransmissions(int newTotal) {
        this.totalRetransmissions = newTotal;
    }

    /**
     * sets the last ACK number we received from the sender. Helps to track duplicate ACKs
     */
    public void setLastReceivedAckNumber(int newAckNumber) {
        this.lastReceivedAckNumber = newAckNumber;
    }

    /**
     * sets how many times we've received the current last ACK. If this exceeds 2, we can assume we lost a packet and 
     * need to retransmit it.
     */
    public void setLastReceivedAckOccurrences(int newOccurrences) {
        this.lastReceivedAckOccurrences = newOccurrences;
    }

    // OTHER Methods

    /**
     * Wrapper method to create DatagramSocket (trying to keep TCPend as clean as possible)
     * @throws SocketException
     */
    public void establishSocket() throws SocketException {
        this.socket = new DatagramSocket(this.port);
        this.socket.setSoTimeout(3000);
        this.socket.connect(this.remoteIP, this.remotePort);
    }

    /**
     * Pull bytes from the file and package them into TCPSegments so we can send them
     * @param offset what part of the file we should start reading from
     * @return a TCPSegment with the file segment data in it 
     * @throws IOException
     */
    public TCPSegment gatherData(int offset) throws IOException {
        byte[] buffer = new byte[this.maxPayload];
        this.fileInputStream.read(buffer);
        TCPSegment segment = new TCPSegment();
        segment.setData(buffer);

        for (int i = 0; i < segment.getData().length; i++) {
            System.out.print((char) segment.getData()[i]);
        }

        return segment;
    }

    public void backtrackFileChannel(int newPosition) throws IOException {
        System.out.println("Backtracking to position " + newPosition);
        FileChannel channel = this.fileInputStream.getChannel();
        channel.position((long) newPosition);
    }

    /**
     * Receives in TCPSegment
     * Puts TCPSegment into a DatagramPacket
     * Sends DatagramPacket over DatagramSocket
     * @param packet the TCPSegment
     */
    public void sendPacket(TCPSegment packet) throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(packet.serialize(), packet.serialize().length, this.remoteIP, this.remotePort);
        this.socket.send(datagramPacket);
        this.totalBytesOfDataSent += packet.getLength();
        this.totalPacketsSent++;
        this.printPacketSummary("snd", packet);
    }

    /**
     * Receive in DatagramPacket, give the corresponding TCPSegment back, and print summary of what was received
     * @param packet the incoming DatagramPacket
     * @return the TCPSegment inside the DatagramPacket
     * @throws IOException
     */
    public TCPSegment receivePacket(DatagramPacket packet) throws IOException {
        this.getSocket().receive(packet);
        this.totalPacketsReceived++;
        byte[] segmentData = packet.getData();
        TCPSegment segment = new TCPSegment(segmentData);

        this.printPacketSummary("rcv", segment);
        return segment;
    }
    
    /**
     * Calculate the timeout upon receiving ACK based on formula from assignment
     * @param ackPacket the packet we received
     */
    public void calculateTimeout(TCPSegment ackPacket) {
        if (this.sequenceNumber == 0) {
            this.estRTT = System.nanoTime() - ackPacket.getTimestamp();
            this.estDev = 0;
            int estRttMillis = (int)this.estRTT / 1000000;
            try {
                this.socket.setSoTimeout(2 * estRttMillis);
            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            long segmentRTT = System.nanoTime() - ackPacket.getTimestamp();
            long segmentDev = Math.abs(segmentRTT - this.estRTT);
            //System.out.println("segRTT = " + segmentRTT);
            //System.out.println("segDev = " + segmentDev);
            this.estRTT = (long) ((0.875 * (double) this.estRTT) + ((1 - 0.875) * (double) segmentRTT));
            this.estDev = (long) ((0.75 * (double) this.estDev) + ((1- 0.75) * (double) segmentDev));
            //System.out.println("estRTT = " + estRTT);
            //System.out.println("estDev = " + estDev);
            int estRttMillis = (int) (this.estRTT / 1000000L);
            int estDevMillis = (int) (this.estDev / 1000000L);
            //System.out.println("estRTTMillis = " + estRttMillis);
            //System.out.println("estDevMillis = " + estDevMillis);
            try {
                this.socket.setSoTimeout(estRttMillis + (4 * estDevMillis));
            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }


    /**
     * We need to print the following info for each packet we receive:
     * <snd/rcv> <time> <flag-list> <seq-number> <number of bytes> <ack number>
     * @param mode snd or rcv
     * @param packet the TCP Packet that was sent or received
     */
    public void printPacketSummary(String mode, TCPSegment packet) {
        String synFlag = "-";
        String finFlag = "-";
        String ackFlag = "-";
        String dataFlag = "-";

        if ((packet.getFlags() & 4) == 4) {
            synFlag = "S";
        }
        if ((packet.getFlags() & 2) == 2) {
            finFlag = "F";
        }
        if ((packet.getFlags() & 1) == 1) {
            ackFlag = "A";
        }
        if (packet.getLength() > 0) {
            dataFlag = "D";
        }

        System.out.println(mode + " " + (packet.getTimestamp() / 1000000000) + " " + synFlag +
            " " + ackFlag + " " + finFlag + " " + dataFlag + " " + packet.getSequenceNumber() +
            " " + packet.getLength() + " " + packet.getAcknowledgementNumber());
    }

    /**
     * After the connection is closed, this method will print out information:
     *      Amount of data transferred
     *      Number of packets sent
     *      Number of retransmissions
     *      Number of duplicate acknowledgements
     */
    public void printStats() {
        System.out.println("Data Transferred: " + this.totalBytesOfDataSent + " bytes");
        System.out.println("Packets sent: " + this.totalPacketsSent);
        System.out.println("Retransmissions: " + this.totalRetransmissions);
        System.out.println("Duplicate Acknowledgements: " + this.totalDuplicateAcknowledgements);
    }

}