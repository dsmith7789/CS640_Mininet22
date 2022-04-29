import java.io.File;
import java.io.FileNotFoundException;
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
    protected int maxPayload;
    protected int maxTcpSegment;

    // variables the user provides
    protected int port;
    protected int mtu;
    protected int sws;
    protected String filename;

    // constructor
    public Receiver(int port, int mtu, int sws, String filename) throws FileNotFoundException {
        this.port = port;
        this.mtu = mtu;
        this.sws = sws;
        this.filename = filename;
        this.file = new File(filename);
        this.fileOutputStream = new FileOutputStream(this.file);
        this.maxPayload = this.mtu - 52;    // 52 bytes = IP + UDP + TCP header size in bytes
        this.maxTcpSegment = this.mtu - 28;  // 28 bytes = IP + UDP header size in bytes
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
     * @param packet the TCPSegment that we're sending
     */
    public void respondToPacket(DatagramPacket receivedDatagramPacket, TCPSegment packet) throws IOException{
        DatagramPacket datagramPacket = new DatagramPacket(packet.serialize(), 0, this.mtu, receivedDatagramPacket.getAddress(), receivedDatagramPacket.getPort());
        this.socket.send(datagramPacket);

        this.printPacketSummary("rcv", packet);
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
        if (isChecksumCorrect(segment) == false) {
            this.totalIncorrectChecksumPacketsDiscarded++;
        }

        this.totalBytesOfDataReceived += packet.getLength();

        this.printPacketSummary("rcv", segment);
        return segment;
    }













    /**
     * Use this method as a wrapper to check if the checksum on the incoming packet is correct.
     * Most of the logic comes from TCPSegment deserialize method but cleaner to reference the wrapper?
     * @param packet the TCP packet sent
     * @return True if checksum is correct (i.e. we could keep it). False if not (i.e. we'll discard it).
     */
    public boolean isChecksumCorrect(TCPSegment packet) {
        short receivedChecksum = packet.getChecksum();
        short expectedChecksum = packet.calculateChecksum();
        return (receivedChecksum == expectedChecksum);
    }

    /**
     * Use this method to check if we want to discard the packet due to being out of sequence or not
     * @param packet the TCP packet sent 
     * @return True if packet is in sequence (i.e. we could keep it). False if not (i.e. we'll discard it).
     */
    public boolean isInSequence(TCPSegment packet) {
        return this.sequenceNumber <= packet.sequenceNumber;
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
        if (packet.getData() != null) {
            dataFlag = "D";
        }

        System.out.println(mode + " " + (packet.getTimestamp() / 1000000000) + " " + synFlag +
            " " + ackFlag + " " + finFlag + " " + dataFlag + " " + packet.getSequenceNumber() +
            " " + packet.getData().length + " " + packet.getAcknowledgementNumber());
    }

    /**
     * After the connection is closed, this method will print out information:
     *      Amount of data received
     *      Number of packets received
     *      Number of out of sequence packets discarded
     *      Number of packets discarded due to incorrect checksum
     */
    public void printStats() {
        System.out.println("Data Received: " + this.totalBytesOfDataReceived + " bytes");
        System.out.println("Packets Received: " + this.totalPacketsReceived);
        System.out.println("Packets discarded (Out of sequence): " + this.totalOutOfSequencePacketsDiscarded);
        System.out.println("Packets discarded (Wrong checksum): " + this.totalIncorrectChecksumPacketsDiscarded);
    }

}