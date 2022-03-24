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
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) { 
                if ((ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) || (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP)) {
                    sendIcmpPacket(etherPacket, inIface, 3, 3);
                } else if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP) {
                    ICMP echoIcmp = (ICMP) ipPacket.getPayload();
                    if (echoIcmp.getIcmpType() == (byte) 8) {   // type 8 = echo request
                        Ethernet ether = new Ethernet();
            
                        // set up type
                        ether.setEtherType(Ethernet.TYPE_IPv4);

                        // set the source MAC
                        System.out.println("inIface is: "+inIface.toString());
                        MACAddress sourceMac = inIface.getMacAddress();
                        System.out.println("sourceMac is "+sourceMac);
                        String sourceMacString = sourceMac.toString();
                        ether.setSourceMACAddress(sourceMacString);

                        // look up destination MAC from ARP Cache using ipPacket's destination IP address and set it
                        int ipDestAddress = ipPacket.getDestinationAddress();
                        System.out.println("original IP packet ip destination address = " + ipDestAddress);
                        RouteEntry routeEntry = this.routeTable.lookup(ipDestAddress);
                        System.out.println("routeEntry = " + routeEntry);
                        int routeEntryIpDestAddress = routeEntry.getDestinationAddress();
                        System.out.println("Route Entry Dest IP Address = " + routeEntryIpDestAddress);
                        //ArpEntry arpEntry = this.arpCache.lookup(routeEntryIpDestAddress);
                        ArpEntry arpEntry = this.arpCache.lookup(ipDestAddress);
                        System.out.println("arpEntry is: "+arpEntry.toString());
                        MACAddress arpEntryMacAddr = arpEntry.getMac();
                        String arpEntryMacAddrString = arpEntryMacAddr.toString();
                        ether.setDestinationMACAddress(arpEntryMacAddrString);

                        // set up the header for the ICMP IP Packet
                        IPv4 ip = new IPv4();
                        ip.setTtl((byte) 64);
                        ip.setProtocol(IPv4.PROTOCOL_ICMP);
                        ip.setSourceAddress(ipDestAddress);
                        ip.setDestinationAddress(ipPacket.getSourceAddress());

                        // set up the ICMP header
                        ICMP icmp = new ICMP();
                        icmp.setIcmpType((byte) 0);
                        icmp.setIcmpCode((byte) 0);

                        // prepare a byte array for ICMP data
                        byte ipHeaderLength = ipPacket.getHeaderLength();
                        int arraySize = ipHeaderLength + 4 + 8; // 4 byes of padding and the 8 bytes of the original IP Packet
                        byte[] icmpHeaderInfo = new byte[arraySize];
                        /*
                        // set up the padding for ICMP data
                        for (int i = 0; i < 4; i++) {
                            icmpHeaderInfo[i] = 0;
                        } */
                        byte[] serializeArray = ipPacket.serialize();

                        // take the entire echo payload from the serialize and put into ICMP info
                        for (int i = 0; i < (serializeArray.length); i++) {
                            icmpHeaderInfo[i] = serializeArray[i];
                        }

                        Data data = new Data();
                        data.setData(icmpHeaderInfo);
                        
                        icmp.setPayload(data);
                        ip.setPayload(icmp);
                        ether.setPayload(ip);

                        this.forwardIpPacket(ether, inIface); 
                    }
                }
                return; 
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

		// If no entry matched, drop packet and send ICMP message
		if (null == bestMatch)
		{ 
            sendIcmpPacket(etherPacket, inIface, 3, 0);
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
            sendIcmpPacket(etherPacket, inIface, 3, 1);
            return;
        }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}

    /**
    * Handle cases where we need to send an ICMP message.
	* @param etherPacket the original Ethernet packet we are trying to send.
    * @param inIface the interface on the router that the Ethernet packet came in on.
    * @param type the type of ICMP message to send
    * @param code the code of ICMP message to send
    */
    private void sendIcmpPacket(Ethernet etherPacket, Iface inIface, int type, int code) {
        Ethernet ether = new Ethernet();
        IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        // set up type
        ether.setEtherType(Ethernet.TYPE_IPv4);

        // set the source MAC (the router's interface)
        System.out.println("inIface is: "+inIface.toString());
        MACAddress sourceMac = inIface.getMacAddress();
        System.out.println("sourceMac is "+sourceMac);
        String sourceMacString = sourceMac.toString();
        ether.setSourceMACAddress(sourceMacString);

        // set the destination MAC (the next hop from the router)
        // 1. look up the source IP address from the original IP Packet in the route table
        // 2. Find the gateway in the RouteEntry
        // 3. If the gateway is 0, just look up the source IP address from original IP packet in ARP cache 
            // and set destination MAC to corresponding MAC
        // If gateway is not 0, look up the gateway IP address (gateway = the next router) in the ARP cache
            // and set destination MAC to the corresponding MAC

        RouteEntry routeEntry = this.routeTable.lookup(ipPacket.getSourceAddress());
        int gatewayAddress = routeEntry.getGatewayAddress();
        if (gatewayAddress == 0) {
            ArpEntry arpEntry = this.arpCache.lookup(ipPacket.getSourceAddress());
            MACAddress destinationMacAddress = arpEntry.getMac();
            String arpEntryMacAddrString = destinationMacAddress.toString();
            ether.setDestinationMACAddress(arpEntryMacAddrString);
        } else {
            ArpEntry arpEntry = this.arpCache.lookup(gatewayAddress);
            MACAddress destinationMacAddress = arpEntry.getMac();
            String arpEntryMacAddrString = destinationMacAddress.toString();
            ether.setDestinationMACAddress(arpEntryMacAddrString);
        }      

        // set up the header for the IP Packet (for ICMP message)
        IPv4 ip = new IPv4();
        ip.setTtl((byte) 64);
        ip.setProtocol(IPv4.PROTOCOL_ICMP);
        ip.setSourceAddress(inIface.getIpAddress());
        ip.setDestinationAddress(ipPacket.getSourceAddress());
        ip.resetChecksum();

        // set up the ICMP header
        ICMP icmp = new ICMP();
        icmp.setIcmpType((byte) type);
        icmp.setIcmpCode((byte) code);
        icmp.resetChecksum();

        // prepare a byte array for ICMP data
        byte ipHeaderLength = ipPacket.getHeaderLength();
        int arraySize = (4 * ipHeaderLength) + 4 + 8; // 4 byes of padding and the 8 bytes of the original IP Packet
        byte[] icmpHeaderInfo = new byte[arraySize];
        
        // set up the padding for ICMP data
        for (int i = 0; i < 4; i++) {
            icmpHeaderInfo[i] = 0;
        }
        byte[] serializeArray = ipPacket.serialize();

        // take the first (ipHeaderLength + 8) bytes of the serialize and put into ICMP info
        for (int i = 0; i < ((4 * ipHeaderLength) + 8); i++) {
            icmpHeaderInfo[i + 4] = serializeArray[i];
        }

        Data data = new Data();
        data.setData(icmpHeaderInfo);
        
        ether.setPayload(ip);
        ip.setPayload(icmp);
        icmp.setPayload(data);
        //ip.setPayload(icmp);
        //ether.setPayload(ip);

        this.forwardIpPacket(ether, inIface);
        return;
    }
}