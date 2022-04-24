import java.nio.ByteBuffer;

public class TCPSegment {

    protected int sequenceNumber;
    protected int acknowledgementNumber;
    protected long timestamp;
    protected int length;
    protected short checksum;
    protected byte[] data;
    protected byte dataOffset;
    protected int retransmitAttempts;

    /**
     * Constructor - anytime we create a TCPSegment we should use
     * SET methods to make sure fields are appropriately set
     * @param data the array that we want to store as the payload
     */
    public TCPSegment(byte[] data, int offset, int length) {
        this.sequenceNumber = 0;
        this.acknowledgementNumber = 0;
        this.timestamp = 0;
        this.length = 0;
        this.checksum = 0;
        this.data = data;
        this.retransmitAttempts = 0;        
    }

    // GET Methods
    public int getSequenceNumber() {
        return this.sequenceNumber;
    }

    public int getAcknowledgementNumber() {
        return this.acknowledgementNumber;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public int getLength() {
        return (this.length >> 3);  // only the first 29 bits are the length
    }
    
    public int getFlags() {
        return (this.length & 0x7); // strip everything except the last 3 bits (111 in binary is 7 in decimal)
    }

    public short getChecksum() {
        return this.checksum;
    }

    public byte[] getData() {
        return this.data;
    }

    public int getRetrasmitAttempts() {
        return this.retransmitAttempts;
    }
    
    // SET methods
    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setAcknowledgementNumber(int acknowledgementNumber) {
        this.acknowledgementNumber = acknowledgementNumber;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setLength(int length) {
        this.length = (length << 3);
    }

    public void setFlags(int flags) {
        this.length = this.length & 0xFFFFFFF8; // clear the last 3 digits
        this.length = this.length | flags;
    }

    public void setChecksum(short checksum) {
        this.checksum = checksum;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setRetransmitAttempts(int attempts) {
        this.retransmitAttempts = attempts;
    }
    
    // OTHER methods

    public void resetChecksum() {
        this.checksum = 0;
    }

    /**
     * Convert a TCP segment to a byte[]
     * @return byte [] representation of TCP segment
     */
    public byte[] serialize() {
        int length;
        if (dataOffset == 0)
            dataOffset = 5;  // default header length
        length = dataOffset << 2;

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.putInt(this.sequenceNumber);
        bb.putInt(this.acknowledgementNumber);
        bb.putLong(this.timestamp);
        bb.putInt(this.length);
        bb.putShort(0);
        bb.putShort(this.checksum);
        bb.put(this.data);

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int accumulation = 0;

            for (int i = 0; i < length / 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            // pad to an even number of shorts
            if (length % 2 > 0) {
                accumulation += (bb.get() & 0xff) << 8;
            }

            accumulation = ((accumulation >> 16) & 0xffff) + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(22, this.checksum); // the checksum is 22 bytes offset from header start
        }
        return data;
    }

    public TCPPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        this.sequenceNumber = bb.getInt();
        this.acknowledgementNumber = bb.getInt();
        this.timestamp = bb.getLong();
        this.length = bb.getInt();
        bb.getShort();
        this.checksum = bb.getShort();
        bb.get(this.data); // transfer the rest of the array into the data array?
        return this;
    }
}
