/**
 * This class maintains attributes about the connection. It doesn't actually set up the connection.
 * The connection is established by the sender & receiver sending packets to each other 
 * Per Piazza question 485, our sliding window is static in size so don't need Slow Start/Congestion Control
 * Just use Fast Retransmit
 */

public class Connection {
    // variables
    private boolean connectionEstablished;
    private boolean congested;
    private int congestionWindow;
    private long estRTT;
    private long estDev;

    // constructor
    public Connection() {
        this.connectionEstablished = false;
        this.congested = false;
        this.congestionWindow
    }

    // "GETTER" methods

    /**
     * A flag to indicate if the connection between sender and receiver was successful. If this is false, we either
     * haven't tried connecting the two hosts yet, or the connection attempt failed.
     * @return True, if the sender and receiver are connected. False if not.
     */
    public boolean isEstablished() {
        return this.connectionEstablished;
    }

    /**
     * Hopefully simplifies the way we look up if a connection is congested or not, instead of having to do math every time.
     * @return True if the connection is congested, False if not.
     */
    public boolean isCongested() {
        return this.congested;
    }

    /**
     * Corresponds to the number of spots available on the buffer for us to send segments across the connection.
     * Unacknowledged segments that are waiting on the sender/receiver buffer will decrease the congestion window.
     * @return the congestion window for this connection
     */
    public int getCongestionWindow() {
        return this.congestionWindow;
    }

    /**
     * Do we actually need the acknowledgement window? TODO re-read assignment for this
     * @return
     */
    public int getAcknowledgementWindow() {
        return this.acknowledgementWindow;
    }

    /**
     * Last congestion point is the size of the congestion window the last time the connection experienced congestion 
     * (and had to go back to Slow Start phase).
     * @return the size of the congestion window the last time we experienced congestion
     */
    public int getLastCongestionPoint() {
        return this.lastCongestionPoint;
    }

    /**
     * The estimated RTT of the connection will update every time we successfully acknowledge a packet. We store the current RTT estimate
     * in the estRTT variable.
     * @return the current estimated RTT
     */
    public long getEstRTT() {
        return this.estRTT;
    }

    /**
     * The estimated deviation inRTT of the connection will update every time we successfully acknowledge a packet. 
     * We store the current RTT deviation estimate in the estDev variable.
     * @return the current estimated RTT dev
     */
    public long getEstDev() {
        return this.estDev;
    }

    // "SETTER" Methods

    /**
     * A flag to indicate if the connection between sender and receiver was successful. If this is false, we either
     * haven't tried connecting the two hosts yet, or the connection attempt failed.
     */
    public void setEstablished(boolean establishFlag) {
        this.connectionEstablished = establishFlag;
    }

    /**
     * Hopefully simplifies the way we look up if a connection is congested or not, instead of having to do math every time.
     */
    public void setCongested(boolean congestFlag) {
        this.congested = congestFlag;
    }

    /**
     * Corresponds to the number of spots available on the buffer for us to send segments across the connection.
     * Unacknowledged segments that are waiting on the sender/receiver buffer will decrease the congestion window.
     * Size of congestion window increments with each successful acknowledgement.
     */
    public void setCongestionWindow(int newWindow) {
        this.congestionWindow = newWindow;
    }

    /**
     * Do we actually need the acknowledgement window? TODO re-read assignment for this
     * @return
     */
    public void setAcknowledgementWindow(int newWindow) {
        this.acknowledgementWindow = newWindow;
    }

    /**
     * Last congestion point is the size of the congestion window the last time the connection experienced congestion 
     * (and had to go back to Slow Start phase).
     */
    public void setLastCongestionPoint(int pointOfCongestion) {
        this.lastCongestionPoint = pointOfCongestion;
    }

    /**
     * The estimated RTT of the connection will update every time we successfully acknowledge a packet. We store the current RTT estimate
     * in the estRTT variable.
     * Note that there's a calculateEstRTT method, setEstRTT is just providing a consistent way of updating the value
     */
    public void setEstRTT(long newEstRTT) {
        this.estRTT = newEstRTT;
    }

    /**
     * The estimated deviation inRTT of the connection will update every time we successfully acknowledge a packet. 
     * We store the current RTT deviation estimate in the estDev variable.
     * Note that there's a calculateEstDev method, setEstDev is just providing a consistent way of updating the value
     */
    public void setEstDev(long newEstDev) {
        this.estDev = newEstDev;
    }

    // OTHER Methods

    /**
     * The 3 way handshake. We'll set the connectionEstablished variable in here.
     * We actually already have the DatagramSockets created, but need to do the SYN / SYN + ACK / ACK
     */
    public void connectionStart(String mode) {
        
    }

    /**
     * When we're finished with the connection, main method will call this to handle teardown.
     * Sending of the FIN packets
     */
    public void connectionEnd() {

    }

    /**
     * In a successful connection, this will handle the transferring of data from sender to receiver
     */
    public void dataTransferPhase() {
        // condition check, call slowStart here

        // other condition check, call congestionAvoidance here
    }

    public void slowStart() {

    }

    public void congestionAvoidance() {

    }
}
