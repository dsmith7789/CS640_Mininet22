import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

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
    public static void senderEstablishConnection(Sender sender) {
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
    public static void receiverEstablishConnection(Receiver receiver) {
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
    public static void senderTerminateConnection(Sender sender) {
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
    public static void receiverTerminateConnection(Receiver receiver) {
        // read the FIN packet from the sender
        boolean receivedFin = false;
        while (receivedFin == false) {
            
            byte[] payload;
            DatagramPacket potentialFinPacket = new DatagramPacket(payload, receiver.mtu);
            receiver.getSocket().receive(potentialFinPacket);
            byte[] buffer = potentialFinPacket.getData();
            TCPSegment receivedSegment = new TCPSegment(buffer);
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

    /**
     * Sender portion of the data transfer phase
     * @param sender
     */
    public static void senderDataTransfer(Sender sender) {
        while (sender.getSequenceNumber() < (int) sender.getFile().length()) {
            // fill buffer
            while (sender.getBuffer().size() < sender.sws) {
                TCPSegment sendSegment = sender.gatherData(sender.getSequenceNumber());
                sender.sendPacket(sendSegment);
                sender.setSequenceNumber(sender.getSequenceNumber() + sender.mtu);
            }
            TCPSegment ackSegment = senderReceiveMethod(sender);
            
            if (ackSegment.equals(null)) {
                System.out.println("Connection lost error. Quitting.");
                System.exit(1);
            }
            int ack = ackSegment.getAcknowledgementNumber();

            if (ack == sender.getLastReceivedAckNumber()) {
                sender.setLastReceivedAckNumber(sender.getLastReceivedAckNumber() + 1);
                sender.setTotalDuplicateAcknowledgements(sender.getTotalDuplicateAcknowledgements() + 1);
                if (sender.getLastReceivedAckOccurrences() > 2) {
                    sender.getBuffer().clear();
                    sender.setSequenceNumber(sender.getLastReceivedAckNumber());
                }
            } else if (ack > sender.getLastReceivedAckNumber()) {
                sender.setLastReceivedAckNumber(ack);
                sender.setLastReceivedAckOccurrences(0);
                // drop all  the packets from the buffer that we can
                for (TCPSegment segment : sender.getBuffer()) {
                    if (segment.getSequenceNumber() < ack) {
                        sender.getBuffer().remove(segment);
                    }
                }
                sender.calculateTimeout(ackSegment);
            }
        }
    }

    /**
     * Handles waiting for ACKs to come back in from the receiver
     * @param sender
     * @return -1 if we retransmitted 16 times without getting an ACK back (error), otherwise returns the ACK
     */
    public static TCPSegment senderReceiveMethod(Sender sender) {
        int attempts = 0;
        while (attempts < 16) {
            try {
                byte[] buffer = new byte[sender.mtu];
                DatagramPacket packet = new DatagramPacket(buffer, sender.mtu);
                sender.getSocket().receive(packet);
                byte[] segmentData = packet.getData();
                TCPSegment segment = new TCPSegment(segmentData);
                return segment;
            } catch (SocketTimeoutException e) {
                System.out.println("Retransmitting packets.");
                for (TCPSegment segment : sender.getBuffer()) {
                    sender.sendPacket(segment);
                }
                attempts++;
            } catch (IOException e) {
                System.out.println("IO Exception");
            }
        }

        return null;   
    }

    /**
     * The receiver side of the data transfer phase
     * @throws IOException
     */
    public static void receiverDataTransfer(Receiver receiver) throws IOException {
        boolean finReceived = false;
        while (finReceived == false) {
            DatagramPacket receivedPacket = receiverReceiveMethod(receiver);
            byte[] packetData = receivedPacket.getData();
            TCPSegment receivedSegment = new TCPSegment(packetData);

            if (receivedPacket.equals(null)) {
                System.out.println("This shouldn't happen. Quitting.");
                System.exit(1);
            }
            int seq = receivedSegment.getSequenceNumber();
            // check if we already have in buffer, then add to buffer
            boolean alreadyInBuffer = false;
            for (TCPSegment segment : receiver.getBuffer()) {
                if (segment.getSequenceNumber() == seq) {
                    alreadyInBuffer = true;
                    break;
                }
            }
            if (alreadyInBuffer == false) {
                receiver.getBuffer().add(receivedSegment);
            }

            int bytesWritten = 0;
            if (seq == receiver.getSequenceNumber()) {
                // write everything we can to the file and remove from file
                for (int i = (receiver.getBuffer().size() - 1); i >= 0; i--) {
                    TCPSegment segment = receiver.getBuffer().get(i);
                    if (segment.getSequenceNumber() <= seq) {
                        receiver.writeData(segment);
                        bytesWritten = bytesWritten + segment.getData().length;
                        receiver.getBuffer().remove(i);
                    }
                }
            } 
            
            TCPSegment ackPacket = new TCPSegment();
            ackPacket.setAcknowledgementNumber(receiver.getSequenceNumber());
            ackPacket.setTimestamp(receivedSegment.getTimestamp());
            ackPacket.setFlags(1);
            DatagramPacket datagramPacket = new DatagramPacket(ackPacket.serialize(), 0, receiver.mtu, receivedPacket.getAddress(), receivedPacket.getPort());
            receiver.setSequenceNumber(receiver.getSequenceNumber() + bytesWritten + 1);
            receiver.getSocket().send(datagramPacket);
        }
    }

    /**
     * Handles waiting for the 
     * @param receiver
     * @return
     */
    public static DatagramPacket receiverReceiveMethod(Receiver receiver) {
        try {
            byte[] buffer = new byte[receiver.mtu];
            DatagramPacket packet = new DatagramPacket(buffer, receiver.mtu);
            receiver.getSocket().receive(packet);
            return packet;
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout exception.");
        } catch (IOException e) {
            System.out.println("IO Exception");
        }
        return null;
    }

}
