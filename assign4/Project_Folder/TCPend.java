import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class TCPend {
    
    public static void main (String[] args) {
        if (args[2].equals("s")) { // run as sender
            
            // parse command line arguments
            int port = Integer.parseInt(args[1]);
            int remoteIP = Integer.parseInt(args[3]);
            int remotePort = Integer.parseInt(args[5]);
            String filename = args[7];
            int mtu = Integer.parseInt(args[9]);
            int sws = Integer.parseInt(args[11]);
            Sender sender = new Sender(port, remoteIP, remotePort, filename, mtu, sws);
            sender.establishSocket();
            senderEstablishConnection(sender);
            sender.getSocket().disconnect();
            
            // arrange the data exchange

            senderTerminateConnection(sender);


        } else { // run as receiver

            // parse command line arguments
            int port = Integer.parseInt(args[1]);
            int mtu = Integer.parseInt(args[3]);
            int sws = Integer.parseInt(args[5]); 
            String filename = args[7];
            Receiver receiver = new Receiver(port, mtu, sws, filename);
            receiver.establishSocket();
            receiverEstablishConnection(receiver);

            // arrange the data exchange

            receiverTerminateConnection(receiver);
            receiver.getSocket().disconnect();
        }
    }

    /**
     * Sender side of 3 way handshake
     * @param sender
     */
    public void senderEstablishConnection(Sender sender) {
        // put a SYN packet together and send it to the receiver
        TCPSegment synPacket = new TCPSegment();
        synPacket.setFlags(4);
        sender.getSocket().send(synPacket.serialize());

        // read in the SYN + ACK
        boolean receivedSynAck = false;
        while (receivedSynAck == false) {
            
            byte[] payload;
            DatagramPacket potentialSynAckPacket = new DatagramPacket(payload, sender.mtu);
            sender.getSocket().receive(potentialSynAckPacket);
            byte[] buffer = potentialSynAckPacket.getData();
            TCPSegment receivedSegment = new TCPSegment(buffer, 0, buffer.length);
            int flags = receivedSegment.getFlags();
            if ((flags & 0x5) != 0x5) {
                continue;   // wasn't a SYN + ACK, so need to keep waiting
            } else {
                receivedSynAck = true;
            }
        }

        // send the final ack
        TCPSegment ackPacket = new TCPSegment();
        synPacket.setFlags(1);
        sender.getSocket().send(ackPacket.serialize());
    }

    /**
     * Receiver side of 3 way handshake
     * @param receiver
     */
    public void receiverEstablishConnection(Receiver receiver) {
        // read the SYN packet from the sender
        boolean receivedSyn= false;
        while (receivedSyn == false) {
            
            byte[] payload;
            DatagramPacket potentialSynPacket = new DatagramPacket(payload, receiver.mtu);
            receiver.getSocket().receive(potentialSynPacket);
            byte[] buffer = potentialSynPacket.getData();
            TCPSegment receivedSegment = new TCPSegment(buffer, 0, buffer.length);
            int flags = receivedSegment.getFlags();
            if ((flags & 0x4) != 0x4) {
                continue;   // wasn't a SYN, so need to keep waiting
            } else {
                receivedSyn = true;
            }
        }

        // put together a SYN + ACK and send back to the sender
        TCPSegment synAckPacket = new TCPSegment();
        synAckPacket.setFlags(5);
        receiver.getSocket().send(synAckPacket.serialize());

    }

    /**
     * Sender side of 3 way termination
     * @param sender
     */
    public void senderTerminateConnection(Sender sender) {
        // put a FIN packet together and send it to the receiver
        TCPSegment finPacket = new TCPSegment();
        finPacket.setSequenceNumber(sender.getSequenceNumber());
        finPacket.setAcknowledgementNumber(1);  // all ACKs from receiver have length 0
        finPacket.setTimestamp(System.nanoTime());
        finPacket.setLength(0);
        finPacket.setFlags(3);
        sender.getSocket().send(finPacket.serialize());

        // read in the FIN + ACK
        boolean receivedFinAck = false;
        while (receivedFinAck == false) {
            
            byte[] payload;
            DatagramPacket potentialFinAckPacket = new DatagramPacket(payload, sender.mtu);
            sender.getSocket().receive(potentialFinAckPacket);
            byte[] buffer = potentialFinAckPacket.getData();
            TCPSegment receivedSegment = new TCPSegment(buffer, 0, buffer.length);
            int flags = receivedSegment.getFlags();
            if ((flags & 0x1) != 0x1) {
                continue;   // wasn't an ACK, so need to keep waiting
            } else {
                receivedFinAck = true;
            }
        }

        // send the final ACK
        TCPSegment ackPacket = new TCPSegment();
        ackPacket.setFlags(1);
        sender.getSocket().send(ackPacket.serialize());
    }

    /**
     * Receiver side of 3 way termination
     * @param receiver
     */
    public void receiverTerminateConnection(Receiver receiver) {
        // read the FIN packet from the sender
        boolean receivedFin = false;
        while (receivedFin == false) {
            
            byte[] payload;
            DatagramPacket potentialFinPacket = new DatagramPacket(payload, receiver.mtu);
            receiver.getSocket().receive(potentialFinPacket);
            byte[] buffer = potentialFinPacket.getData();
            TCPSegment receivedSegment = new TCPSegment(buffer, 0, buffer.length);
            int flags = receivedSegment.getFlags();
            if ((flags & 0x3) != 0x3) {
                continue;   // wasn't a SYN, so need to keep waiting
            } else {
                receivedFin = true;
            }
        }

        // put together a FIN + ACK and send back to the sender
        TCPSegment finAckPacket = new TCPSegment();
        finAckPacket.setSequenceNumber(1);
        finAckPacket.setAcknowledgementNumber(receiver.getSequenceNumber() + 1);
        finAckPacket.setTimestamp(receivedSegment.getTimestamp());
        finAckPacket.setLength(0);
        finAckPacket.setFlags(3);
        receiver.getSocket().send(finAckPacket.serialize());

    }
}
