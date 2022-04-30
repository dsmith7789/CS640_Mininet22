import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

public class TCPend {
    
    public static void main (String[] args) throws IOException {
        if (args[2].equals("-s")) { // run as sender
            
            // parse command line arguments
            int port = Integer.parseInt(args[1]);
            String remoteIP = args[3];
            int remotePort = Integer.parseInt(args[5]);
            String filename = args[7];
            int mtu = Integer.parseInt(args[9]);
            int sws = Integer.parseInt(args[11]);
            Sender sender = new Sender(port, remoteIP, remotePort, filename, mtu, sws);
            sender.establishSocket();
            senderEstablishConnection(sender);
            
            // arrange the data exchange
            senderDataTransfer(sender);

            senderTerminateConnection(sender);
            sender.getSocket().close();
            sender.printStats();
            
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
            receiverDataTransfer(receiver);

            receiverTerminateConnection(receiver);
            receiver.getSocket().close();
            receiver.printStats();
        }
    }

    /**
     * Sender side of 3 way handshake
     * @param sender
     * @throws IOException
     */
    public static void senderEstablishConnection(Sender sender) throws IOException {
        // put a SYN packet together and send it to the receiver
        // wait for a corresponding SYN + ACK
        // if we haven't gotten the SYN + ACK within socket's timeout,
        // send the SYN again (up to 16 times)
        TCPSegment synPacket = new TCPSegment();
        synPacket.setFlags(4);
        boolean receivedSynAck = false;
        int attempts = 0;
        byte[] buf = new byte[sender.maxTcpSegment];
        DatagramPacket potentialSynAckPacket = new DatagramPacket(buf, buf.length);
        while (receivedSynAck == false && attempts < 16) {
            
            try {
                sender.sendPacket(synPacket);
                TCPSegment receivedSegment = sender.receivePacket(potentialSynAckPacket);
                int flags = receivedSegment.getFlags();
                if ((flags & 0x5) != 0x5) {
                    continue;   // wasn't a SYN + ACK, so need to keep waiting
                } else {
                    receivedSynAck = true;
                } 
            } catch (SocketTimeoutException e) {
                attempts++;
                System.out.println("Resending SYN to receiver.");
            }
        }
        if (attempts == 16) {
            System.out.println("Failed to establish connection. Didn't receive SYN + ACK back from receiver. Exiting.");
            System.exit(1);
        }

        // send the final ACK
        // the receiver is waiting for this ACK and will attempt to resend the SYN + ACK  if it doesn't get this ACK successfully
        // therefore we can wait for a significant enough period of time and if we don't get a SYN + ACK in that time,
        // our connection is established.
        TCPSegment ackPacket = new TCPSegment();
        ackPacket.setAcknowledgementNumber(1);
        ackPacket.setFlags(1);

        boolean ackPacketWasReceived = false;
        attempts = 0;
        byte[] duplicateSynAckBuffer = new byte[sender.maxPayload];
        DatagramPacket duplicateSynAckDatagramPacket = new DatagramPacket(duplicateSynAckBuffer, duplicateSynAckBuffer.length);
        while ((ackPacketWasReceived == false) && (attempts < 16)) {
            try {
                sender.sendPacket(ackPacket);
                TCPSegment duplicateSynAckSegment = sender.receivePacket(duplicateSynAckDatagramPacket);
            } catch (SocketTimeoutException e) {
                ackPacketWasReceived = true;
            }
        }

        if (attempts == 16) {
            System.out.println("Error in establishing connection. Receiver never got ACK from Sender. Exiting.");
        }

        System.out.println("SUCCESSFULLY ESTABLISHED CONNECTION.");
    }

    /**
     * Receiver side of 3 way handshake
     * @param receiver
     * @throws IOException
     */
    public static void receiverEstablishConnection(Receiver receiver) throws IOException {
        // read the SYN packet from the sender
        boolean receivedSyn= false;
        TCPSegment receivedSegment = new TCPSegment();
        byte[] buf = new byte[receiver.maxTcpSegment];
        DatagramPacket synDatagramPacket = new DatagramPacket(buf, buf.length);
        while (receivedSyn == false) {
            receivedSegment = receiver.receivePacket(synDatagramPacket);
            int flags = receivedSegment.getFlags();
            if ((flags & 0x4) != 0x4) {
                continue;   // wasn't a SYN, so need to keep waiting
            } else {
                receivedSyn = true;
            }
        }

        // put together a SYN + ACK and send back to the sender
        // and wait for the final ACK to come back from sender
        int attempts = 0;
        boolean receivedAck = false;
        receiver.getSocket().setSoTimeout(5000);    // to use "timeout loop" structure, we temporarily need a timeout on the receiver socket
        while (receivedAck == false && attempts < 16) {
            try {

                // respond to the SYN we received
                TCPSegment synAckPacket = new TCPSegment();
                synAckPacket.setFlags(5);
                synAckPacket.setAcknowledgementNumber(receiver.getSequenceNumber());
                synAckPacket.setTimestamp(receivedSegment.getTimestamp());
                receiver.respondToPacket(synDatagramPacket, synAckPacket); // send a SYN + ACK in response

                // receive in what should be the final ACK from sender to establish the connection
                byte[] buffer = new byte[receiver.maxTcpSegment];
                DatagramPacket finalAckDatagramPacket = new DatagramPacket(buffer, buffer.length);
                TCPSegment finalAckTcpSegment = receiver.receivePacket(finalAckDatagramPacket);

                // check to make sure this is an ACK we got back
                int flags = finalAckTcpSegment.getFlags();
                if ((flags & 0x1) != 0x1) {
                    continue;   // wasn't a ACK, so need to keep waiting
                } else {
                    receivedAck = true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Resending SYN + ACK to sender.");
                attempts++;
            } catch (IOException e) {
                System.out.println("IO Exception");
            }
        }

        if (attempts == 16) {
            System.out.println("Error establishing connection. Did not receive final ACK from sender. Exiting.");
            System.exit(1);
        }
        receiver.getSocket().setSoTimeout(0);    // reset receiver to not have a timeout for the rest of the program
       
        System.out.println("SUCCESSFULLY ESTABLISHED CONNECTION.");
    }

    /**
     * Sender side of 3 way termination
     * @param sender
     * @throws IOException
     */
    public static void senderTerminateConnection(Sender sender) throws IOException {
        System.out.println("ATTEMPTING TO TERMINATE CONNECTION");
        // put a FIN packet together and send it to the receiver. Wait for ACK to come back
        boolean receivedAck = false;
        TCPSegment finPacket = new TCPSegment();
        finPacket.setSequenceNumber(sender.getSequenceNumber());
        finPacket.setAcknowledgementNumber(1);  // all ACKs from receiver have sequence number 0
        finPacket.setFlags(3);

        int attempts = 0;
        while ((receivedAck == false) && (attempts < 16)) {
            try {
                sender.sendPacket(finPacket);
                byte[] buf = new byte[sender.maxTcpSegment];
                DatagramPacket ackPacket = new DatagramPacket(buf, buf.length);
                TCPSegment receivedSegment = sender.receivePacket(ackPacket);
                int flags = receivedSegment.getFlags();
                if ((flags & 0x1) != 0x1) {
                    continue;   // wasn't an ACK, so need to keep waiting
                } else {
                    receivedAck = true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Resending FIN to receiver.");
                attempts++;
            }
        }

        if (attempts == 16) {
            System.out.println("Error terminating connection. Did not receive ACK from receiver on FIN message. Exiting.");
            System.exit(1);
        }
        
        // now we need to wait for the FIN to come in from the receiver
        boolean receivedFin = false;
        TCPSegment receivedSegment = new TCPSegment();
        byte[] buf = new byte[sender.maxTcpSegment];
        DatagramPacket finDatagramPacket = new DatagramPacket(buf, buf.length);
        
        int oldTimeout = sender.getSocket().getSoTimeout();
        sender.getSocket().setSoTimeout(0);

        while (receivedFin == false) {
            try {
                receivedSegment = sender.receivePacket(finDatagramPacket);
                int flags = receivedSegment.getFlags();
                if ((flags & 0x2) != 0x2) {
                    continue;   // wasn't a FIN, so need to keep waiting
                } else {
                    receivedFin = true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Shouldn't happen, this is an error. Exiting.");
            }
        }

        // send the final ACK
        // we need to wait and make sure we don't get another FIN back from the receiver
        // if we get another FIN from the receiver, then that means it never received our ACK
        // in that case the receiver still thinks the connection is open
        // Per Piazza @588, waiting 16 * timeout will work.
        sender.getSocket().setSoTimeout(oldTimeout * 16);
        attempts = 0;
        boolean receiverGotFinalAck = false;
        byte[] duplicateFinBuf = new byte[sender.maxTcpSegment];
        DatagramPacket duplicateFinDatagramPacket = new DatagramPacket(duplicateFinBuf, duplicateFinBuf.length);
        while ((attempts < 16) && (receiverGotFinalAck == false)) {
            try {
                attempts++;
                TCPSegment ackPacket = new TCPSegment();
                ackPacket.setFlags(1);
                sender.sendPacket(ackPacket);
                TCPSegment duplicateFinSegment = sender.receivePacket(duplicateFinDatagramPacket);
            } catch (SocketTimeoutException e) {
                receiverGotFinalAck = true; // timeout is good in this case!
            }
        }
        if (attempts == 16) {
            System.out.println("Error in terminating connection. Receiver never got the final ACK from sender. Exiting.");
            System.exit(1);
        }
        
        System.out.println("SUCCESSFULLY TERMINATED CONNECTION.");
    }

    /**
     * Receiver side of 3 way termination
     * @param receiver
     * @throws IOException
     */
    public static void receiverTerminateConnection(Receiver receiver) throws IOException {
        System.out.println("ATTEMPTING TO TERMINATE CONNECTION");
        // read the FIN packet from the sender
        boolean receivedFin = false;
        TCPSegment receivedSegment = new TCPSegment();
        byte[] buf = new byte[receiver.maxTcpSegment];
        DatagramPacket finDatagramPacket = new DatagramPacket(buf, buf.length);
        while (receivedFin == false) {
            receivedSegment = receiver.receivePacket(finDatagramPacket);
            int flags = receivedSegment.getFlags();
            if ((flags & 0x3) != 0x3) {
                continue;   // wasn't a FIN + ACK, so need to keep waiting
            } else {
                receivedFin = true;
            }
        }

        // now that we got in the FIN, we need to send the ACK 
        // and make sure it was received (i.e we don't keep geting FINs, meaning our ACK was never received)
        boolean ackSentSuccessfully = false;
        int attempts = 0;
        TCPSegment ackSegment = new TCPSegment();
        ackSegment.setAcknowledgementNumber(receiver.getSequenceNumber() + 1);
        ackSegment.setTimestamp(receivedSegment.getTimestamp());
        ackSegment.setFlags(1);
        byte[] duplicateFinBuf = new byte[receiver.maxTcpSegment];
        DatagramPacket duplicateFinDatagramPacket = new DatagramPacket(duplicateFinBuf, duplicateFinBuf.length);
        receiver.getSocket().setSoTimeout(5000);    // need to temporarily set the receiver socket timeout again
        while ((ackSentSuccessfully == false) && (attempts < 16)) {
            try {
                attempts++;
                receiver.respondToPacket(finDatagramPacket, ackSegment);
                TCPSegment duplicateFinSegment = receiver.receivePacket(duplicateFinDatagramPacket);
            } catch (SocketTimeoutException e) {
                ackSentSuccessfully = true;
            }
        }
        if (attempts == 16) {
            System.out.println("Error in terminating connection. Receiver could not ACK the sender's FIN message. Exiting.");
            System.exit(1);
        }

        // put together a FIN, send to sender, and wait for the ACK to come back
        TCPSegment finPacket = new TCPSegment();
        finPacket.setSequenceNumber(1);
        finPacket.setAcknowledgementNumber(receiver.getSequenceNumber() + 1);
        finPacket.setTimestamp(receivedSegment.getTimestamp());
        finPacket.setFlags(3);

        byte[] finalAckBuf = new byte[receiver.maxTcpSegment];
        DatagramPacket finalAckDatagramPacket = new DatagramPacket(finalAckBuf, finalAckBuf.length);
        boolean finalAckReceived = false;
        attempts = 0;
        while ((finalAckReceived == false) && (attempts < 16)) {
            try {
                receiver.respondToPacket(finDatagramPacket, finPacket);
                TCPSegment finalAckSegment = receiver.receivePacket(finalAckDatagramPacket);
                finalAckReceived = true;
            } catch (SocketTimeoutException e) {
                attempts++;
                System.out.println("Resending final FIN from receiver.");
            }
        }

        if (attempts == 16) {
            System.out.println("Error in terminating connection. Never received ACK from sender on final FIN message. Exiting.");
            System.exit(1);
        }
        
        System.out.println("SUCCESSFULLY TERMINATED CONNECTION.");
    }

    /**
     * Sender portion of the data transfer phase
     * Continuously fill buffer, then wait for a response. Handle the received packet
     * and update the time out if we got an ACK back.
     * @param sender
     * @throws IOException
     */
    public static void senderDataTransfer(Sender sender) throws IOException {
        while (sender.getSequenceNumber() < (int) sender.getFile().length()) {
            //System.out.println("Sender Sequence Number: " + sender.getSequenceNumber());
            //System.out.println("File Length: " + sender.getFile().length());

            // fill buffer
            while (sender.getBuffer().size() < sender.sws) {
                if (sender.getSequenceNumber() >= (int) sender.getFile().length()) {
                    break;
                }
                // package data from file into a segment, format segment header, and send the segment
                TCPSegment sendSegment = sender.gatherData(sender.getSequenceNumber());
                
                sendSegment.setSequenceNumber(sender.getSequenceNumber());
                sendSegment.setAcknowledgementNumber(1);        // should be 1 because we aren't receiving data from receiver
                sendSegment.setLength(sendSegment.getData().length);
                sendSegment.setFlags(1);    // all packets should have ACK flag set
                sender.sendPacket(sendSegment);
                sender.getBuffer().add(sendSegment);
                sender.setSequenceNumber(sender.getSequenceNumber() + sendSegment.getLength());
            }
            TCPSegment ackSegment = senderReceiveMethod(sender);
            
            if (ackSegment.equals(null)) {
                System.out.println("Connection lost error. Quitting.");
                System.exit(1);
            }
            int ack = ackSegment.getAcknowledgementNumber();
            sender.calculateTimeout(ackSegment);
            //System.out.println("Sender timeout = " + sender.getSocket().getSoTimeout());

            // check for duplicate acknowledgements
            if (ack == sender.getLastReceivedAckNumber()) {
                sender.setLastReceivedAckNumber(sender.getLastReceivedAckNumber() + 1);
                sender.setTotalDuplicateAcknowledgements(sender.getTotalDuplicateAcknowledgements() + 1);
                if (sender.getLastReceivedAckOccurrences() > 2) {
                    sender.setTotalRetransmissions(sender.getTotalRetransmissions() + sender.getBuffer().size());
                    sender.getBuffer().clear();
                    sender.backtrackFileChannel(sender.getLastReceivedAckNumber());
                    sender.setSequenceNumber(sender.getLastReceivedAckNumber());
                }
            } else if (ack > sender.getLastReceivedAckNumber()) {
                sender.setSequenceNumber(ack);
                sender.setLastReceivedAckNumber(ack);
                sender.setLastReceivedAckOccurrences(0);
                // drop all  the packets from the buffer that we can

                for (int i = (sender.getBuffer().size() - 1); i >= 0; i--) {
                    TCPSegment segment = sender.getBuffer().get(i);
                    if (segment.getSequenceNumber() < ack) {
                        //System.out.println("Removed segment from buffer.");
                        sender.getBuffer().remove(i);
                    }
                }
            }
        }
    }

    /**
     * Handles waiting for ACKs to come back in from the receiver
     * @param sender
     * @return null if we retransmitted 16 times without getting an ACK back (error), otherwise returns an ACK packet the receiver sent
     * @throws IOException
     */
    public static TCPSegment senderReceiveMethod(Sender sender) throws IOException {
        int attempts = 0;
        while (attempts < 16) {
            try {
                byte[] buf = new byte[sender.maxTcpSegment];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                //System.out.println("Waiting for an ACK; Sender timeout = " + sender.getSocket().getSoTimeout());
                TCPSegment segment = sender.receivePacket(packet);
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
     * While the receiver thinks the connection is active (haven't gotten FIN),
     * wait for a packet to come in. Then put the packet in the receive buffer.
     * then if we can write anything from the buffer to the file, do that,
     * send an ACK packet back to the sender, and take the packet out of the buffer.
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

            //System.out.println("Testing - verifying checksum");
            // discard packet if the checksum is wrong
            if (receiver.isChecksumCorrect(receivedSegment) == false) {
                receiver.setTotalIncorrectChecksumPacketsDiscarded(receiver.getTotalIncorrectChecksumPacketsDiscarded() + 1);
                continue;
            }

            //System.out.println("Testing - checking if outside window");
            // discard packet if it's outside the window
            if (receiver.isInSequence(receivedSegment) == false) {
                receiver.setTotalOutOfSequencePacketsDiscarded(receiver.getTotalOutOfSequencePacketsDiscarded() + 1);
                continue;
            }

            if ((receivedSegment.getFlags() & 0x2) == 2) {
                finReceived = true;
                continue;
            }

            int seq = receivedSegment.getSequenceNumber();
            // check if we already have in buffer, then add to buffer
            //System.out.println("Testing - checking if packet already in buffer");

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

            //System.out.println("Testing - checking if the packet is the next one in the sequence");
            //System.out.println("got in sequence = " + seq);
            //System.out.println("Receiver sequence = " + receiver.getSequenceNumber());
            if (seq == receiver.getSequenceNumber()) {
                // write everything we can to the file and remove from buffer
                for (int i = (receiver.getBuffer().size() - 1); i >= 0; i--) {
                    TCPSegment segment = receiver.getBuffer().get(i);
                    if (segment.getSequenceNumber() <= seq) {
                        System.out.println("Writing Data for packet with seq " + segment.getSequenceNumber());
                        receiver.writeData(segment);
                        receiver.getBuffer().remove(i);
                    }
                }
            } 
            
            //System.out.println("Testing - creating ACK packet");
            // send an ACK back to the sender
            TCPSegment ackPacket = new TCPSegment();
            ackPacket.setAcknowledgementNumber(receiver.getSequenceNumber());
            ackPacket.setTimestamp(receivedSegment.getTimestamp());
            ackPacket.setFlags(1);
            receiver.respondToPacket(receivedPacket, ackPacket);
        }
    }

    /**
     * Handles waiting for the packets to come in from the sender
     * @param receiver
     * @return
     */
    public static DatagramPacket receiverReceiveMethod(Receiver receiver) {
        try {
            byte[] buf = new byte[receiver.maxTcpSegment];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            TCPSegment segment = receiver.receivePacket(packet);
            return packet;
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout exception.");
        } catch (IOException e) {
            System.out.println("IO Exception");
        }
        return null;
    }

}
