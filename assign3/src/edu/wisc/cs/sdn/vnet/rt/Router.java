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

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
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
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{ 
            sendIcmpPacket(etherPacket, inIface, 11, 0);
            return; 
        }

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{ return; }
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
		{ return; }

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
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}


    /**
    * Handle cases where we need to send an ICMP message.
	* @param ethernetOriginalPacket the original Ethernet packet we are trying to send.
    * @param inIface the interface on the router that the Ethernet packet came in on.
    * @param type the type of ICMP message to send
    * @param code the code of ICMP message to send
    */
    private void sendIcmpPacket(Ethernet ethernetOriginalPacket, Iface inIface, int type, int code) 
    {
    /*********** BEGIN SETUP ETHERNET HEADER ************/

        System.out.println("The received ethernet packet's Source MAC is: " + ethernetOriginalPacket.getSourceMAC().toString());
        System.out.println("The received ethernet packet's Destination MAC is: " + ethernetOriginalPacket.getDestinationMAC().toString());
        Ethernet ethernetNewPacket = new Ethernet();
        IPv4 ipOriginalPacket = (IPv4)ethernetOriginalPacket.getPayload();
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
    
    /*********** END SETUP ETHERNET HEADER ************/  
    
    /*********** BEGIN SETUP IPv4 HEADER ************/

        IPv4 ipNewPacket = new IPv4();
        ipNewPacket.setTtl((byte) 64);
        ipNewPacket.setProtocol(IPv4.PROTOCOL_ICMP);
        ipNewPacket.setSourceAddress(inIface.getIpAddress());
        System.out.println("We set the ICMP IP Packet's Source IP Address to: " + ipNewPacket.fromIPv4Address(ipNewPacket.getSourceAddress()));
        ipNewPacket.setDestinationAddress(ipOriginalPacket.getSourceAddress());
        System.out.println("We set the ICMP IP Packet's Destination IP Address to: " + ipNewPacket.fromIPv4Address(ipNewPacket.getDestinationAddress()));
        ipNewPacket.resetChecksum();
    
    /*********** END SETUP IPv4 HEADER ************/

    /*********** BEGIN SETUP ICMP HEADER ************/

        ICMP icmp = new ICMP();
        icmp.setIcmpType((byte) type);
        icmp.setIcmpCode((byte) code);
        icmp.resetChecksum();

    /*********** END SETUP ICMP HEADER ************/
    
    /*********** BEGIN SETUP ICMP PAYLOAD ************/

        // TA suggestion: try this format to copy your data in the ICMP packet.
        // System.arraycopy(oldPacketContent, 0, newPayload, 4, newPayload.length - 4);
        // format = arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
        byte[] originalIpPacketContent = ipOriginalPacket.serialize();
        byte ipOriginalPacketHeaderLength = ipOriginalPacket.getHeaderLength();   // convert from 4 byte words to bytes
        System.out.println("Header length in bytes = " +(4 * ipOriginalPacketHeaderLength));
        int arraySize = (4 * ipOriginalPacketHeaderLength) + 4 + 8; // 4 byes of padding and the 8 bytes of the original IP Packet
        System.out.println("new payload array size = " + arraySize);
        byte[] newPayload = new byte[arraySize];
        System.arraycopy(originalIpPacketContent, 0, newPayload, 4, newPayload.length - 4);
    
    /*********** END SETUP ICMP PAYLOAD ************/


    /*********** BEGIN NESTING PACKETS ************/

        Data data = new Data();
        data.setData(newPayload);
        icmp.setPayload(data);
        ipNewPacket.setPayload(icmp);
        ethernetNewPacket.setPayload(ipNewPacket);
                
    /*********** END NESTING PACKETS ************/

        System.out.println("Now we're calling the sendPacket method for the Ethernet packet we created for the ICMP message.");
        this.sendPacket(ethernetNewPacket, inIface);
        return;
    }
}