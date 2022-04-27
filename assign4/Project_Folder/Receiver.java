import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;

public class Receiver {

    // variables we need to track that aren't supplied by the user
    private int sequenceNumber;
    private ArrayList<TCPSegment> buffer;
    private int totalBytesOfDataReceived;
    private int totalPacketsReceived;
    private int totalOutOfSequencePacketsDiscarded;
    private int totalIncorrectChecksumPacketsDiscarded;
    private DatagramSocket socket;
    private File file;
    private FileOutputStream fileOutputStream;

    // variables the user provides
    protected int port;
    protected int mtu;
    protected int sws;
    protected String filename;

    // constructor
    public Receiver(int port, int mtu, int sws, String filename) {
        this.port = port;
        this.mtu = mtu;
        this.sws = sws;
        this.filename = filename;
        this.file = new File(filename);
        this.fileOutputStream = new FileOutputStream(this.file);
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
     * Retrieves how many BYTES of data the receiver was sent over this connection.
     * @return the total number of bytes sent to this receiver
     */
    public int getTotalBytesOfDataReceived() {
        return this.totalBytesOfDataReceived;
    }

    /**
     * Retrieves how many PACKETS of data the receiver was sent over this connection.
     * @return the total number of packets sent to this receiver
     */
    public int getTotalPacketsReceived() {
        return this.totalPacketsReceived;
    }


    /**
     * Retrieves how many packets the receiver discarded because it received them OUT OF SEQUENCE.
     * @return the total number of discarded packets due to OOS error.
     */
    public int getTotalOutOfSequencePacketsDiscarded() {
        return this.totalOutOfSequencePacketsDiscarded;
    }

    /**
     * Retrieves how many packets the receiver discarded because the PACKET CHECKSUM INCORRECT.
     * @return the total number of discarded packets due to PCI error.
     */
    public int getTotalIncorrectChecksumPacketsDiscarded() {
        return this.totalIncorrectChecksumPacketsDiscarded;
    }
    
    /**
     * Retrieve the socket the receiver is receiving segments over
     * @return
     */
    public DatagramSocket getSocket() {
        return this.socket;
    }

    public ArrayList<TCPSegment> getBuffer() {
        return this.buffer;
    }

    // "SETTER" methods

    /**
    * Wrapper method to create DatagramSocket (trying to keep TCPend as clean as possible)
     * @throws SocketException
    */
    public void establishSocket() throws SocketException {
        this.socket = new DatagramSocket(this.port);
    }

    /**
     * Set the sequence number of the next segment the receiver will send
     */
    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Sets the specified number of BYTES to the total this receiver was sent over this connection.
     */
    public void setTotalBytesOfDataReceived(int newTotal) {
        this.totalBytesOfDataReceived = newTotal;
    }

    /**
     * Sets the specified number of PACKETS to the total this receiver was sent over this connection.
     */
    public void setTotalPacketsReceived(int newTotal) {
        this.totalPacketsReceived = newTotal;
    }


    /**
     * Sets how many packets the receiver discarded because it received them OUT OF SEQUENCE.
     */
    public void setTotalOutOfSequencePacketsDiscarded(int newTotal) {
        this.totalOutOfSequencePacketsDiscarded = newTotal;
    }

    /**
     * Sets how many packets the receiver discarded because the PACKET CHECKSUM INCORRECT.
     */
    public void setTotalIncorrectChecksumPacketsDiscarded(int newTotal) {
        this.totalIncorrectChecksumPacketsDiscarded = newTotal;
    }

    // OTHER methods

    /**
     * Write data to the file on receiver
     * @param segment
     * @throws IOException
     */
    public void writeData(TCPSegment segment) throws IOException {
        byte[] buffer = segment.getData();
        this.fileOutputStream.write(buffer, segment.getSequenceNumber() - 1, segment.getLength());
    }

    /**
     * Puts TCPSegment into a DatagramPacket
     * Sends DatagramPacket over DatagramSocket
     * @param receivedDatagramPacket the DatagramPacket we are responding to
     * @param packet the TCPSegment
     */
    public void respondToPacket(DatagramPacket receivedDatagramPacket, TCPSegment packet) {
        DatagramPacket datagramPacket = new DatagramPacket(packet.serialize(), 0, this.mtu, receivedDatagramPacket.getAddress(), receivedDatagramPacket.getPort());
        try {
            this.socket.send(datagramPacket);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }















    /**
     * Use this method as a wrapper to check if the checksum on the incoming packet is correct.
     * Most of the logic comes from TCPSegment deserialize method but cleaner to reference the wrapper?
     * @param packet the TCP packet sent
     * @return True if checksum is correct (i.e. we could keep it). False if not (i.e. we'll discard it).
     */
    public boolean isChecksumCorrect(TCPSegment packet) {
        // TODO
    }

    /**
     * Use this method to check if we want to discard the packet due to being out of sequence or not
     * @param packet the TCP packet sent 
     * @return True if packet is in sequence (i.e. we could keep it). False if not (i.e. we'll discard it).
     */
    public boolean isInSequence(TCPSegment packet) {
        // TODO
    }

    /**
     * We need to print the following info for each packet we receive:
     * <rcv> <time> <flag-list> <seq-number> <number of bytes> <ack number>
     * @param packet the TCP Packet that was sent
     */
    public void printPacketSummary(TCPSegment packet) {
        // TODO
    }

    /**
     * After the connection is closed, this method will print out information:
     *      Amount of data received
     *      Number of packets received
     *      Number of out of sequence packets discarded
     *      Number of packets discarded due to incorrect checksum
     */
    public void printStats() {
        // TODO
    }

}