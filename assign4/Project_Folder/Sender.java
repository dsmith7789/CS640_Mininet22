import java.io.FileInputStream;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;

public class Sender {

    // variables we need to track that aren't supplied by the user
    private int sequenceNumber;
    private int totalBytesOfDataSent;
    private int totalPacketsSent;
    private int totalDuplicateAcknowledgements;
    private int totalRetransmissions;
    private int lastReceivedAckNumber;
    private int lastReceivedAckOccurrences;
    private HashMap<Integer, TCPSegment> buffer;
    private DatagramSocket socket;
    private long estRTT;
    private long estDev;
    private File file;
    private FileInputStream fileInputStream;

    // variables the user provides
    protected int port;           // the port at which this sender will run
    protected int remoteIP;       // IP address of the receiver
    protected int remotePort;     // port at which the remote receiver is running
    protected String filename;    // file we're sending
    protected int mtu;            // max trans unit (in bytes)
    protected int sws;            // sliding window size (number of segments)

    // constructor
    public Sender(int port, int remoteIP, int remotePort, String filename, int mtu, int sws) {
        this.port = port;
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.filename = filename;
        this.file = new File(this.filename);
        this.fileInputStream = new FileInputStream(this.file);
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
        return this.totalIncorrectChecksumPacketsDiscarded;
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
    public HashMap getBuffer() {
        return this.buffer;
    }

    /**
     * Retrieve the socket the sender is sending segments over
     * @return
     */
    public DatagramSocket getSocket() {
        return this.socket;
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
        this.totalIncorrectChecksumPacketsDiscarded = newTotal;
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
    public void LastReceivedAckOccurrences(int newOccurrences) {
        this.lastReceivedAckOccurrences = newOccurrences;
    }

    /**
     * Lets us reset the sender's buffer, which is where we will store packets that we've sent, until we get a corresponding 
     * acknowledgement. The buffer is also useful for tracking congestion.
     */
    public void setBuffer(HashMap<Integer, TCPSegment> newBuffer) {
        this.totalIncorrectChecksumPacketsDiscarded = newBuffer;
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

    public void sendPacket(TCPSegment packet) {
        // 1. Send the datagram packet
        // 2. Add the packet to the buffer
        // 3. Start the timeout
        
        while (packet.getRetrasmitAttempts() < 16) {
            try {
                this.socket.send(packet.serialize());
                this.buffer.put(packet.getSequenceNumber(), packet);
                packet.setRetransmitAttempts(packet.getRetrasmitAttempts() + 1);
                boolean receivedAck = false;
                while (receivedAck == false) {
                    byte[] payload;
                    DatagramPacket receivedPacket = new DatagramPacket(payload, sender.mtu);
                    this.getSocket().receive(receivedPacket);
                    byte[] buffer = receivedPacket.getData();
                    TCPSegment receivedSegment = new TCPSegment(buffer, 0, buffer.length);
                    int flags = receivedSegment.getFlags();
                    int receivedAckNumber = receivedSegment.getAcknowledgementNumber();
                    if ((flags & 0x1) != 0x1) {
                        continue;   // wasn't an ACK, so need to keep waiting
                    } else if (receivedAckNumber > packet.getSequenceNumber()) {
                        receivedAck = true;
                    }
                }
                if (receivedAck == true) {
                    this.buffer.remove(packet.getSequenceNumber());
                    break;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Retransmitting packet.");
            } 
        }
        if (packet.getRetrasmitAttempts() == 16) {
            System.out.println("Maximum number of retransmissions failure. Exiting program.");
            System.exit(1);
        }
    }

    public void calculateTimeout(Connection connection, TCPSegment ackPacket) {
        if (ackPacket.getSequenceNumber == 0) {
            this.estRTT = System.nanoTime() - ackPacket.getTimestamp();
            this.estDev = 0;
            this.socket.setSoTimeout(2 * estRTT);
        } else {
            long segmentRTT = System.nanoTime() - ackPacket.getTimestamp();
            long segmentDev = Math.abs(segmentRTT - this.estRTT);
            this.estRTT = (0.875 * this.estRTT) + ((1 - 0.875) * segmentRTT);
            this.estDev = (0.75 * this.estDev) + ((1- 0.75) * segmentDev);
            this.socket.setSoTimeout(this.estRTT + (4 * estDev));
        }
    }

    /**
     * Track the timeout and number of retransmissions
     * @param packet
     */
    public void retransmitPacket(TCPSegment packet) {
        // TODO
    }

    public void printPacketSummary(TCPSegment packet) {
        // TODO
    }

    public void printStats() {

    }

}