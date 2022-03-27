package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
/*
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.ICMP; */
import net.floodlightcontroller.packet.*;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets                                             */

		System.out.println("The type of the packet is " + etherPacket.getEtherType());
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			// check if receiving RIP request
			IPv4 ipPacket = (IPv4) etherPacket.getPayload();
			if ((ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) && (((UDP)ipPacket).getDestinationPort() == UDP.RIP_PORT)) {
				processRipPacket(ipPacket, inIface);
			} else {
				this.handleIpPacket(etherPacket, inIface);
			}
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void processRipPacket(IPv4 ipPacket, Iface inIface) {
		RIPv2 receivedRipPacket = (RIPv2) ipPacket.getPayload();
		if (receivedRipPacket.getCommand() == RIPv2.COMMAND_REQUEST) {
			// create new objects
			Ethernet newEthernetPacket = new Ethernet();
			UDP newUdpPacket = new UDP();
			RIPv2 newRipPacket = new RIPv2();

			

			// nest packets
			newEthernetPacket.setPayload(newUdpPacket);
			newUdpPacket.setPayload(newRipPacket);
			sendRipResponse(newEthernetPacket, inIface);
		}
	}

	private void sendRipResponse(Ethernet ethernetPacket, Iface inIface) {

	}

	private void sendRipRequest(IPv4 ipPacket, Iface inIface) {
		RIPv2 ripPacket = (RIPv2) ipPacket.getPayload();
	}
	

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("Do we get here?");
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		System.out.println("The packet's TTL is " + ipPacket.getTtl());
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{ 
            sendIcmpPacket(etherPacket, inIface, 11, 0, false);
            return; 
        }

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{ 
				if ((ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) || (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP)) {
                    sendIcmpPacket(etherPacket, inIface, 3, 3, false);
                } else if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP) {
					sendIcmpPacket(etherPacket, inIface, 0, 0, true);
				}
			}
		}

		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry 
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if (null == bestMatch)
		{ 
			sendIcmpPacket(etherPacket, inIface, 3, 0, false);
			return; 
		}

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ 
			sendIcmpPacket(etherPacket, inIface, 3, 1, false);
			return; 
		}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}


    /**
    * Handle cases where we need to send an ICMP message.
	* @param ethernetOriginalPacket the original Ethernet packet we are trying to send.
    * @param inIface the interface on the router that the Ethernet packet came in on.
    * @param type the type of ICMP message to send
    * @param code the code of ICMP message to send
	* @param echoMode to handle if we're sending a reply to an ICMP echo request
    */
    private void sendIcmpPacket(Ethernet ethernetOriginalPacket, Iface inIface, int type, int code, boolean echoMode) 
    {
		// set up the new Ethernet packet
		Ethernet ethernetNewPacket = constructEthernetHeader(ethernetOriginalPacket, inIface);
		IPv4 ipOriginalPacket = (IPv4)ethernetOriginalPacket.getPayload();

		// set up the new IP Packet
        IPv4 ipNewPacket = constructIPv4Header(ipOriginalPacket, inIface, echoMode);

		// set up the new ICMP Packet
        ICMP icmp = new ICMP();
        icmp.setIcmpType((byte) type);
        icmp.setIcmpCode((byte) code);
        icmp.resetChecksum();
    
		// retrieve the ICMP Payload
		if (echoMode == false) {
			byte[] newPayload = copyIpPacketPayload(ipOriginalPacket, 8);
			nestPackets(newPayload, icmp, ipNewPacket, ethernetNewPacket);
		} else {
			//byte[] newPayload = copyIpPacketPayload(ipOriginalPacket, -1);
			//nestPackets(newPayload, icmp, ipNewPacket, ethernetNewPacket);
			ICMP icmpPayload = new ICMP();
			icmpPayload = (ICMP) ipOriginalPacket.getPayload();
			icmp.setPayload(icmpPayload.getPayload());
			ipNewPacket.setPayload(icmp);
			ethernetNewPacket.setPayload(ipNewPacket);
		}
        
		// send the new Ethernet packet to the proper destination
        System.out.println("Now we're calling the sendPacket method for the Ethernet packet we created for the ICMP message.");
        this.sendPacket(ethernetNewPacket, inIface);
        return;
    }

	/**
	* Places payload inside ICMP, ICMP into IP packet, IP packet into Ethernet.
	* @param payload object that contains byte[] payload
	* @param icmp icmp packet we're nesting the data in
	* @param ipPacket ipPacket we're nesting the icmp in
	* @param ethernet ethernet we're nesting the ipPacket in
	 */
	private void nestPackets(byte[] payload, ICMP icmp, IPv4 ipPacket, Ethernet ethernet) {
		Data data = new Data();
        data.setData(payload);
        icmp.setPayload(data);
        ipPacket.setPayload(icmp);
        ethernet.setPayload(ipPacket);
	}

	/**
	* Given an IP Packet, this will copy the payload up to the requested number of bytes, and return the copy as a byte array.
	* @param originalIpPacket this is the packet we're copying the payload from
	* @param bytesToCopy how many bytes of the payload we want to copy. If copying the entire payload, set this to -1
	 */
	private byte[] copyIpPacketPayload(IPv4 originalIpPacket, int bytesToCopy) {
		if (bytesToCopy == -1) {
			// copying the whole payload of the original IP Packet (echo reply)
			byte[] originalIpPacketContent = originalIpPacket.serialize();
    		byte ipOriginalPacketHeaderLength = originalIpPacket.getHeaderLength();   // convert from 4 byte words to bytes
			System.out.println("Header length in bytes = " +(4 * ipOriginalPacketHeaderLength));
			int arraySize = (4 * ipOriginalPacketHeaderLength) + 4 + originalIpPacketContent.length; // 4 byes of padding and the 8 bytes of the original IP Packet
        	byte[] newPayload = new byte[arraySize];
			System.out.println("new payload array size = " + arraySize);
        	System.arraycopy(originalIpPacketContent, 0, newPayload, 4, newPayload.length - 4);
			return newPayload;
		} else {
			// copying only a specific number of bytes from the payload	
			byte[] originalIpPacketContent = originalIpPacket.serialize();
    		byte ipOriginalPacketHeaderLength = originalIpPacket.getHeaderLength();   // convert from 4 byte words to bytes
			System.out.println("Header length in bytes = " +(4 * ipOriginalPacketHeaderLength));
			int arraySize = (4 * ipOriginalPacketHeaderLength) + 4 + bytesToCopy; // 4 byes of padding and the 8 bytes of the original IP Packet
        	byte[] newPayload = new byte[arraySize];
			System.out.println("new payload array size = " + arraySize);
        	System.arraycopy(originalIpPacketContent, 0, newPayload, 4, newPayload.length - 4);
			return newPayload;
		}
	}

	/**
	* Given an Ethernet Packet, this will create a new Ethernet packet and set up the header such that:
	*	Source MAC Address = the MAC Address of the interface we'll be sending the packet out of
	*	Destin MAC Address = found by doing a lookup in the route table to find the proper gateway IP address (next router), then finding the
	*		corresponding MAC address using the ARP cache (if sending to another router)
	* @param ethernetOriginalPacket the Ethernet packet that the router received
	* @param inIface which interface on the router the Ethernet packet came in
	 */
	private Ethernet constructEthernetHeader(Ethernet originalEthernetPacket, Iface inIface) {
		System.out.println("The received ethernet packet's Source MAC is: " + originalEthernetPacket.getSourceMAC().toString());
        System.out.println("The received ethernet packet's Destination MAC is: " + originalEthernetPacket.getDestinationMAC().toString());
        Ethernet ethernetNewPacket = new Ethernet();
        IPv4 ipOriginalPacket = (IPv4)originalEthernetPacket.getPayload();
        // set up type
        ethernetNewPacket.setEtherType(Ethernet.TYPE_IPv4);

        // set the source MAC (the router's interface)
        System.out.println("We received the packet on interface: "+inIface.toString());
        MACAddress sourceMac = inIface.getMacAddress();
        System.out.println("So the ICMP Ethernet Packet's Source MAC Address should be: " + sourceMac.toString());
        String sourceMacString = sourceMac.toString();
        ethernetNewPacket.setSourceMACAddress(sourceMacString);

        // set the destination MAC (the next hop from the router)

        // 1. look up the source IP address from the original IP Packet in the route table
        RouteEntry routeEntry = this.routeTable.lookup(ipOriginalPacket.getSourceAddress());
        if (routeEntry == null) {
            System.out.println("The route entry was null.");
        } else {
                System.out.println("Found Route Entry " + routeEntry.toString());
        }
        // 2. Find the gateway in the RouteEntry
        int gatewayAddress = routeEntry.getGatewayAddress();
        if (gatewayAddress == 0) {
            // 3. If the gateway is 0, just look up the source IP address from original IP packet in ARP cache 
            // and set destination MAC to corresponding MAC
            ArpEntry arpEntry = this.arpCache.lookup(ipOriginalPacket.getSourceAddress());
            if (arpEntry == null) {
                System.out.println("The ARP Entry was null.");
            } else {
                System.out.println("Found ARP Entry " + arpEntry.toString());
            }
            MACAddress destinationMacAddress = arpEntry.getMac();
            String arpEntryMacAddrString = destinationMacAddress.toString();
            ethernetNewPacket.setDestinationMACAddress(arpEntryMacAddrString);
        } else {
            // If gateway is not 0, look up the gateway IP address (gateway = the next router) in the ARP cache
            // and set destination MAC to the corresponding MAC
            ArpEntry arpEntry = this.arpCache.lookup(gatewayAddress);
            if (arpEntry == null) {
                System.out.println("The ARP Entry was null.");
            } else {
                System.out.println("Found ARP Entry " + arpEntry.toString());
            }
            MACAddress destinationMacAddress = arpEntry.getMac();
            String arpEntryMacAddrString = destinationMacAddress.toString();
            ethernetNewPacket.setDestinationMACAddress(arpEntryMacAddrString);
        }      
        System.out.println("We set the ICMP Ethernet Packet's Destination MAC Address to: " + ethernetNewPacket.getDestinationMAC().toString());
		
		return ethernetNewPacket;
	}

	/**
	* Given an IPv4 Packet, this will create a new IPv4 packet and set up the header such that:
	*	Source IP Address = if not echo mode, this will be the address of the interface that the original packet arrived on. 
	*		If in echo mode, this will be the destination IP from the IP header in the echo request.
	*	Destin IP Address = the source IP address of the original IP packet
	* @param originalIpPacket the IP packet that was the payload of the Ethernet packet that the router received
	* @param inIface which interface on the router the Ethernet packet came in
	* @param echoMode determines how we'll set the Source IP Address on the new IP Packet
	 */
	private IPv4 constructIPv4Header(IPv4 originalIpPacket, Iface inIface, boolean echoMode) {
		IPv4 ipNewPacket = new IPv4();
        ipNewPacket.setTtl((byte) 64);
        ipNewPacket.setProtocol(IPv4.PROTOCOL_ICMP);
        
        if (echoMode == true) {
			// set source IP on new packet as the destination IP from the IP header in the echo request.
			ipNewPacket.setSourceAddress(originalIpPacket.getDestinationAddress());
		} else {
			// set source IP on new packet as the IP address of the interface that the original packet arrived on.
			ipNewPacket.setSourceAddress(inIface.getIpAddress());
		}
		System.out.println("We set the ICMP IP Packet's Source IP Address to: " + ipNewPacket.fromIPv4Address(ipNewPacket.getSourceAddress()));
		ipNewPacket.setDestinationAddress(originalIpPacket.getSourceAddress());
        System.out.println("We set the ICMP IP Packet's Destination IP Address to: " + ipNewPacket.fromIPv4Address(ipNewPacket.getDestinationAddress()));
        ipNewPacket.resetChecksum();
		return ipNewPacket;
	}
}