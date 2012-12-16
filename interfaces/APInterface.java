/*
 * @(#)APInterface.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package interfaces;

import java.util.*;

import core.*;

/**
 * <p>A NetworkInterface that acts as an access point such that it bridges two
 * mobile hosts across a wired network. The collection of all APInterface 
 * instances collectively manage a collection of hosts within range of any
 * AP. When a new host comes in range of one of them, instead of creating a new
 * connection between the AP node and the mobile node, multiple connections are
 * created to all the other mobile hosts in range of any AP.</p> 
 * 
 * <p>Each AP can listen on multiple interface types, configurable with the 
 * <code>interfaceCount</code> and <code>interface{i}</code> settings, where {i}
 * is replaced by a number. If these settings are not supplied, the instances
 * will search for an attach to all the interface types defined for other node
 * groups. In either case, then, the APs act as a bridge not only across a 
 * distance but also across different interfaces.</p>
 * 
 * 
 * <p>Facilitating this paradigm required some modification to the 
 * NetworkInterface superclass and the other known subclasses of it. It is 
 * necessary to prevent the NetworkInterface instances residing on other nodes
 * from successfully creating a Connection object with any APInterface instance
 * as the other endpoint in the connection, yet instances of APInterface still
 * had to be active as defined by isActive() in order for the simulator to 
 * execute their update() functions.<p> 
 * 
 * @author PJ Dillon, Unversity of Pittsburgh
 *
 */
public class APInterface extends NetworkInterface
{
	/** Number of interfaces to which to add these APs-setting id {@value} */
	public static final String INTERFACE_COUNT_S = "interfaceCount";
	/** Settings to define each interface to which to add these APs -setting id
	 * {@value} */
	public static final String INTERFACE_S_PREFIX = "interface";
	
	protected static String[] interfaceTypes;
	
	/**
	 * Collection of all hosts in range of an AP.
	 */
	protected static Map<NetworkInterface, 
                       Collection<NetworkInterface>> connectedHosts;
	
	protected ConnectivityOptimizer[] optimizers = null;
	
	public APInterface(Settings settings)
	{
		super(settings);
		if(settings.contains(INTERFACE_COUNT_S))
		{
			/*
			 * Read each of type of interface the APs should service
			 */
			int gCount = settings.getInt(INTERFACE_COUNT_S);
			interfaceTypes = new String[gCount];
			for(int i = 1; i <= gCount; i++)
			{
				interfaceTypes[i] = settings.getSetting(INTERFACE_S_PREFIX + i);
			}
		}
		else
		{
			/*
			 * If the interfaces to which we're suppose to attach the APs isn't 
			 * explicitly declared, we search through all host groups looking for 
			 * declared instances of network interfaces.
			 */
			Settings s = new Settings();
			int nrOfHostGroups = s.getInt("Scenario.nrofHostGroups");
			Set<String> interfaces = new HashSet<String>();
			for(int i = 1; i <= nrOfHostGroups; i++)
			{
				s.setNameSpace(core.SimScenario.GROUP_NS+i);
				s.setSecondaryNamespace(core.SimScenario.GROUP_NS);
				int nrofInterfaces = s.getInt(core.SimScenario.NROF_INTERF_S);
				for(int j = 1; j <= nrofInterfaces; j++)
				{
					interfaces.add(s.getSetting(core.SimScenario.INTERFACENAME_S+j));
				}
			}
			interfaceTypes = new String[interfaces.size()];
			int i = 0;
			for(String str : interfaces)
				interfaceTypes[i++] = str;
		}
		
		if(connectedHosts == null)
			connectedHosts = new HashMap<NetworkInterface, 
												Collection<NetworkInterface>>();
		
		/*
		 *  Note that we specifically don't add this instance of APInterface to the
		 *  connectedHosts map here because this instance is a prototype instance 
		 *  that is not actually used during simulation.
		 */
		
	}

	public APInterface(APInterface ni)
	{
		super(ni);
		this.optimizers = new ConnectivityOptimizer[interfaceTypes.length];
		connectedHosts.put(this, new HashSet<NetworkInterface>());
	}

	@Override
	public NetworkInterface replicate()
	{
		return new APInterface(this);
	}

	@Override
	public void setHost(DTNHost host)
	{
		this.host = host;
		ModuleCommunicationBus comBus = host.getComBus();
		comBus.subscribe(SCAN_INTERVAL_ID, this);
		comBus.subscribe(RANGE_ID, this);
		comBus.subscribe(SPEED_ID, this);
		
		// Instead of creating an new interface type, we add the instance to some
		// of or all of the other declared interface types
		for(int i = 0; i < interfaceTypes.length; i++)
		{
			optimizers[i] = ConnectivityGrid.ConnectivityGridFactory(
				interfaceTypes[i].hashCode(), transmitRange);
			optimizers[i].addInterface(this);
		}
	}

	@Override
	public void connect(NetworkInterface anotherInterface)
	{
		Collection<NetworkInterface> myCollection = connectedHosts.get(this);
		
		if(anotherInterface.isActive() 
				&& isWithinRange(anotherInterface) 
				&& !myCollection.contains(anotherInterface)
				&& !(anotherInterface instanceof APInterface))
		{
			for(Map.Entry<NetworkInterface, Collection<NetworkInterface>> entry : 
							connectedHosts.entrySet())
			{
				NetworkInterface farAP = entry.getKey();
				if(farAP == this) continue;
				for(NetworkInterface ni : entry.getValue())
				{
					int conSpeed = anotherInterface.getTransmitSpeed();
					if (conSpeed > this.transmitSpeed) {
						conSpeed = this.transmitSpeed; 
					}
					if(conSpeed > ni.getTransmitSpeed()) {
						conSpeed = ni.getTransmitSpeed();
					}
					
					DTNHost nearEndpoint = anotherInterface.getHost();
					DTNHost farEndpoint = ni.getHost();
		
					/*
					 * Connection Diagram:
					 * 
					 * Host:          nearEndpoint                 farEndpoint
					 *                    \                          /
					 * Intermediary:    anotherInterface            ni
					 *                      \                      /
					 * AP:                  this---------------farAP
					 */
					Connection con = new ProxiedCBRConnection(farEndpoint, ni, farAP, 
							nearEndpoint, anotherInterface, this, conSpeed);
					
					
					ni.notifyConnectionListeners(CON_UP, anotherInterface.getHost());
					
					// add con to end point connection lists
					anotherInterface.getConnections().add(con);
					ni.getConnections().add(con);
					
					// inform host routers about connection
					nearEndpoint.connectionUp(con);
					farEndpoint.connectionUp(con);
					
				}
			}
			myCollection.add(anotherInterface);
		}

	}

	@Override
	public void update()
	{
	
		for(int i = 0; i < optimizers.length; i++){
			optimizers[i].updateLocation(this);
		}
		
		// First break the old ones
		for (Iterator<NetworkInterface> i =  connectedHosts.get(this).iterator(); 
					i.hasNext();) 
		{
			NetworkInterface anotherInterface = i.next();

			if (!isWithinRange(anotherInterface)) {
				i.remove();
			}
		}
		
		// Then find new possible connections
		for(int j = 0; j < optimizers.length; j++){
			Collection<NetworkInterface> interfaces =
				optimizers[j].getNearInterfaces(this);
			for (NetworkInterface i : interfaces) {
				connect(i);
			}
		}
	}

	@Override
	public void createConnection(NetworkInterface anotherInterface)
	{
		Collection<NetworkInterface> myCollection = connectedHosts.get(this);
		if (!isConnected(anotherInterface) && 
				!myCollection.contains(anotherInterface)) {  
			for(NetworkInterface ni : myCollection)
			{
				// connection speed is the lower one of the two speeds 
				int conSpeed = anotherInterface.getTransmitSpeed();
				if (conSpeed > this.transmitSpeed) {
					conSpeed = this.transmitSpeed; 
				}
								
				Connection con = new CBRConnection( 
						anotherInterface.getHost(), anotherInterface, 
						ni.getHost(), this, 
							conSpeed);
				connect(con,anotherInterface);
			}
		}

	}

	@Override
	public boolean isActive()
	{
		return true;
	}

	@Override
	public boolean acceptingConnections()
	{
		return false;
	}
}
