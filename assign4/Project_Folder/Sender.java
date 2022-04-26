import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;

public class Sender {

    // variables we need to track that aren't supplied by the user
    private int sequenceNumber;
    private int totalBytesOfDataSent;
    private int totalPacketsSent;
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
        this.sws = sws;
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

    /**
     * Lets us reset the sender's buffer, which is where we will store packets that we've sent, until we get a corresponding 
     * acknowledgement. The buffer is also useful for tracking congestion.
     */
    public void setBuffer(HashMap<Integer, TCPSegment> newBuffer) {
        this.buffer = newBuffer;
    }

    // OTHER Methods

    /**
     * Wrapper method to create DatagramSocket (trying to keep TCPend as clean as possible)
     */
    public void establishSocket() {
        this.socket = new DatagramSocket(this.port);
        this.socket.connect((InetAddress) this.remoteIP, this.remotePort);
    }

    /**
     * Pull bytes from the file and package them into TCPSegments so we can send them
     * @param offset what part of the file we should start reading from
     * @return
     */
    public TCPSegment gatherData(int offset) {
        byte[] buffer = new byte[this.mtu];
        this.fileInputStream.read(buffer, offset, buffer.length);
        TCPSegment segment = new TCPSegment();
        segment.setData(buffer);
        return segment;
    }

    /**
     * Receives in TCPSegment
     * Puts TCPSegment into a DatagramPacket
     * Sends DatagramPacket over DatagramSocket
     * @param packet the TCPSegment
     */
    public void sendPacket(TCPSegment packet) {
        DatagramPacket datagramPacket = new DatagramPacket(packet.serialize(), 0, this.mtu, this.remoteIP, this.remotePort);
        try {
            this.socket.send(datagramPacket);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
    /**
     * Calculate the timeout upon receiving ACK based on formula from assignment
     * @param ackPacket the packet we received
     */
    public void calculateTimeout(TCPSegment ackPacket) {
        if (ackPacket.getSequenceNumber() == 0) {
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
            this.estRTT = (long) ((0.875 * (double) this.estRTT) + ((1 - 0.875) * (double) segmentRTT));
            this.estDev = (long) ((0.75 * (double) this.estDev) + ((1- 0.75) * (double) segmentDev));
            int estRttMillis = (int) this.estRTT / 1000000;
            int estDevMillis = (int) this.estDev / 1000000;
            try {
                this.socket.setSoTimeout(estRttMillis + (4 * estDevMillis));
            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void printPacketSummary(TCPSegment packet) {
        // TODO
    }

    public void printStats() {

    }

}