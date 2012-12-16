package routing;

import java.util.*;
//import java.awt.*;
//import java.awt.geom.*;
//import java.text.*;
import core.*;
//import gui.playfield.*;

/**
 * Implementation of the Virtual Repository routing protocol. VR routing makes
 * forwarding decisions based on the geographic location of nodes, meaning it
 * needs to get the geographic location of each node. This is done in two ways.
 * The router maintains a list of nodes to which it is currently connected and
 * their locations. For all other nodes, it uses the node's ID as input to two
 * hash functions that determine x and y coordinates for the node, which we call
 * the virtual repository for the node. Thus, in all cases, the router has a set
 * of coordinates to which to send the given message. 
 * 
 * @author PJ Dillon
 *
 */
public class GeoRouter extends ActiveRouter
{
	public static final String GEOROUTER_NS = "GeoRouter";
	public static final String CHECKIN_NAME = "checkInInterval";
	public static final String RADIUS_NAME = "vrRadius";
	public static final String NEIGHBOREXP_NAME = "neighborExpireInterval";
	public static final String MAX_VR_AREA_S = "maxVRArea";
	public static final String VR_ORIGIN_S = "vrOrigin";
	
	public static final String MESSAGE_CHECKIN_SEQNUM_P = "GeoRouter.checkInID";
	public static final String MESSAGE_FOCUS_P = "GeoRouter.focus";
	public static final String MESSAGE_IS_CHECKIN_P = "GeoRouter.isCheckIn";
	public static final String MESSAGE_HOME_LOC_P = "GeoRouter.homeLocation";
	public static final String MESSAGE_LOOK_P = "GeoRouter.look";
	public static final String MESSAGE_LOOK_TIMEOUT_P = "GeoRouter.lookTimeout";
	
	public static final int DENIED_ALREADY_IN_VR = -6;
	public static final int DENIED_CHECKIN = -7;
	
	public static final int defaultCheckInInterval = 300;  //seconds
	public static final double defaultVrRadius = 50.0; //meters
	public static final int defaultExpirationInterval = 300; //seconds
	
	private static final double A = (Math.sqrt(5.0) - 1)/2;
	private static final String CHECKIN_ID_PREFIX = "checkIN";
	private static int checkInID = 0;
	
	protected static int maxVrX;
	protected static int maxVrY;
	protected static int vrOriginX;
	protected static int vrOriginY;
	protected static int checkInInterval;
	protected static int checkInTTL;
	protected static double vrRadius;
	protected static int neighborExpirationInterval;
	
	/*
	 * For now, the hashing scheme assumes we're using a rectangular movement
	 * area.
	 */
	static
	{	
		Settings s = new Settings(GEOROUTER_NS);
		
		int[] vrAreaSize = s.getCsvInts(MAX_VR_AREA_S);
		maxVrX = vrAreaSize[0];
		maxVrY = vrAreaSize[1];
		
		int[] vrAreaOrigin = s.getCsvInts(VR_ORIGIN_S);
		vrOriginX = vrAreaOrigin[0];
		vrOriginY = vrAreaOrigin[1];
		
		if(s.contains(CHECKIN_NAME)) {
			checkInInterval = s.getInt(CHECKIN_NAME);
		}
		else {
			checkInInterval = defaultCheckInInterval;
		}
		
		if(s.contains(RADIUS_NAME)) {
			vrRadius = s.getDouble(RADIUS_NAME);
		}
		else {
			vrRadius = defaultVrRadius;
		}
		
		if(s.contains(NEIGHBOREXP_NAME)) {
			neighborExpirationInterval = s.getInt(NEIGHBOREXP_NAME);
		}
		else {
			neighborExpirationInterval = defaultExpirationInterval;
		}
	}
	
	/*
	 * hash functions for the router; takes a node's address and returns a Coord
	 * object indicating the focus of the node's VR.
	 */
	public static Coord hash(DTNHost node)
	{
		double x = hashX(node), 
		 	y = hashY(node, x);
		return new Coord(x, y);
	}
	
	private static double hashX(DTNHost node)
	{
		int addr = node.getAddress();
//		return node.hashCode() / ((double)Integer.MAX_VALUE) * worldSizeX;
		return maxVrX * (A* addr - Math.floor(A * addr)) + vrOriginX;
	}
	
	private static double hashY(DTNHost node, double x)
	{
		int addr = node.getAddress();
//		return node.hashCode() / ((double)Integer.MAX_VALUE) * worldSizeY;
		return maxVrY * (A*(x+1)*addr - Math.floor(A *(x+1)* addr)) + vrOriginY;
	}
	
	/**
	 * Helper to drawn the VRs in the GUI.
	 * @return
	 */
	public static double getVRRadius()
	{
		return vrRadius;
	}
	
	/**
	 * Returns true if the given host is inside the Virtual Repository area of the
	 * node who is the destination of the given message. This generally means that
	 * the host is responsible for holding on to the message.
	 * 
	 * @param host
	 * @param m
	 * @return
	 */
	private static boolean isHostInMsgVR(DTNHost host, Message m)
	{
		Coord hostLoc = host.getLocation();
		Coord vrHome = hash(m.getTo());
		
		double hx = hostLoc.getX(), hy = hostLoc.getY(), 
		 //vector lengths for 'here' to 'dest' vector
		 vx = vrHome.getX() - hx, vy = vrHome.getY() - hy, 
		 vmag = Math.hypot(vx, vy); //distance bwt here and dest
		
		return vmag < vrRadius;
	}
	
	/**
	 * Stores the location of all currently connected neighbors. If any entry 
	 * exists here, then the actual location of a destination is known and any
	 * data messages should be forwarded towards this location. Check-in messages
	 * should, however, always be directed towards the destination's VR.
	 */
	protected Map<DTNHost, NeighborEntry> neighborhood;
	
	/**
	 * Stores the IDs of messages that have been delivered to their final 
	 * destination, which is not necessarily this host. This info is used to
	 * inform nodes of a message's completion. Thus, if a node is carrying one of 
	 * these messages and it tries to send to a neighbor, it will be informed that
	 * the message has already been delivered and it can be deleted from the
	 * buffer.
	 */
	protected Set<String> finishedMessages;
	
	/**
	 * The time at which another check-in message should be generated. 
	 */
	protected double nextCheckInTime;
	
	protected long checkInSeqNum;
	
	/**
	 * Creates an instance of the Virtual Repository Router
	 * @param s Settings for the router
	 */
	public GeoRouter(Settings s)
	{
		super(s);
		init();
	}
	
	/**
	 * Copy Constructor
	 * @param vr the prototype from where the Settings are copied
	 */
	public GeoRouter(GeoRouter vr)
	{
		super(vr);
		init();
	}
	
	protected void init()
	{
		neighborhood = new HashMap<DTNHost, NeighborEntry>();
		finishedMessages = new HashSet<String>();
		nextCheckInTime = SimClock.getTime() + (double)(checkInInterval * 2) * Math.random();
		checkInSeqNum = 0;
	}
	
/*	@Override
	public void draw(Graphics2D g2)
	{
		Coord home = VRRouter.hash(getHost());
		double rad = VRRouter.getVRRadius();
		
		Ellipse2D.Double repArea = new Ellipse2D.Double(PlayFieldGraphic.scale(home.getX()-rad),
				PlayFieldGraphic.scale(home.getY()-rad), PlayFieldGraphic.scale(2*rad), PlayFieldGraphic.scale(2*rad));
		g2.setColor(Color.blue);
		g2.draw3DRect(PlayFieldGraphic.scale(home.getX()-1), PlayFieldGraphic.scale(home.getY()-1), 
				PlayFieldGraphic.scale(3), PlayFieldGraphic.scale(3), true);
		
		g2.draw(repArea);
		g2.setColor(Color.red);
		g2.drawString("h"+getHost().toString(), PlayFieldGraphic.scale(home.getX()), 
				PlayFieldGraphic.scale(home.getY()));
	}*/
	
	@Override
	public boolean createNewMessage(Message m)
	{
		DTNHost to = m.getTo();
		
		makeRoomForNewMessage(m.getSize());
		
		if(neighborhood.containsKey(to)) {
			NeighborEntry entry = neighborhood.get(to);
			doLook(m, entry.getLocation(), entry.getLocationTimestamp());
		}
		else {
			m.addProperty(MESSAGE_LOOK_P, false);
			m.addProperty(MESSAGE_FOCUS_P, hash(to));
			m.addProperty(MESSAGE_LOOK_TIMEOUT_P, null);
		}
				
		addToMessages(m, true);
		
		return true;
	}
	
	/**
	 * Adds a new check-in message to this host's buffer. The message is directed
	 * towards the VR for this host. The destination for this message is also set
	 * to this host, and the checkReceiving() method ensures that no host attempts
	 * to return the message to this host.
	 */
	protected void createCheckInMessage()
	{
		DTNHost thisHost = getHost();
		Message m = new Message(thisHost, thisHost, CHECKIN_ID_PREFIX + checkInID++, 64);

		m.setResponseSize(0);
//		m.setTtl(checkInTTL);
		
		m.addProperty(MESSAGE_CHECKIN_SEQNUM_P, checkInSeqNum++);
		m.addProperty(MESSAGE_LOOK_P, false);
		m.addProperty(MESSAGE_FOCUS_P, hash(thisHost));
		m.addProperty(MESSAGE_IS_CHECKIN_P, m);
		m.addProperty(MESSAGE_HOME_LOC_P, thisHost.getLocation());
		m.updateProperty(MESSAGE_LOOK_TIMEOUT_P, null);
		
		removeOldCheckInMsgs(m);
		addToMessages(m, true);
	}

/*	@Override
	protected int checkReceiving(Message m)
	{
		VRMessage vrm = (VRMessage) m;
		if( vrm.isCheckInMessage()) {
			if (vrm.getTo().equals(getHost())) {
				return DENIED_OLD;
			}
		}
		
		if(finishedMessages.contains(vrm.getId()))
			return DENIED_FINISHED;
		
		return super.checkReceiving(m);
	}*/

	/*
	 * Called when another host, from, wants to send the given message to this
	 * host.
	 */
	@Override
	public int receiveMessage(Message m, DTNHost from)
	{
		if(hasMessage(m.getId()) && isHostInMsgVR(getHost(), m) && !isHostInMsgVR(from, m))
			return DENIED_ALREADY_IN_VR;
		if(isCheckInMessage(m) && m.getTo().equals(getHost()))
			return DENIED_CHECKIN;
		if(finishedMessages.contains(m.getId()))
			return DENIED_DELIVERED;
		
		return super.receiveMessage(m, from);
	}

	/**
	 * Called when this router is trying to send a message over the given 
	 * connection to its peer.
	 */
	@Override
	protected int startTransfer(Message m, Connection con)
	{
		/*
		 * We need to know if the peer to which we're trying to send knows that the
		 * message is already 
		 */
		int retVal = super.startTransfer(m, con);
		
		if(retVal == DENIED_DELIVERED)
		{
//			System.out.println("Peer says msg " + m.getId() + " is finished");
			finishedMessages.add(m.getId());
			if(hasMessage(m.getId()))
				deleteMessage(m.getId(), false);
		}
		else if(retVal == DENIED_ALREADY_IN_VR && hasMessage(m.getId()))
		{
			deleteMessage(m.getId(), false);
		}
		/*System.out.print("Start Transfer " + getHost().getAddress() + " to " + 
				con.getOtherNode(getHost()).getAddress() + ": ");
		switch(retVal)
		{
		case DENIED_FINISHED: System.out.println("DENIED_FINISHED"); break;
		case DENIED_ALREADY_IN_VR: System.out.println("DENIED_ALREADY_IN_VR"); break;
		case DENIED_OLD: System.out.println("DENIED_OLD"); break;
		case RCV_OK: System.out.println("RCV_OK"); break;
		}*/
		return retVal;
	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message vrm = super.messageTransferred(id, from);
//		System.out.println(vrm.toString() + " transfered to host: " + getHost().getAddress());
		
		/*
		 * This is a hack. messagedTransferred is called at the receiving end of
		 * a connection over which the message was just transferred. To simulate an
		 * ACK, which is trivial in this scenario, we're reaching into the sending
		 * node and deleted the message.
		 * 
		 * This is a custody transfer.
		 */
		((ActiveRouter)from.getRouter()).ackMessage(vrm, getHost());
		
		/*
		 * If this is a check-in message, then we need to add the location contained
		 * within it along with the source host of the message to our neighborhood.
		 */
		if(isCheckInMessage(vrm) && removeOldCheckInMsgs(vrm))
		{	
			Coord homeloc = getCheckInLocation(vrm);
			neighborhood.put(vrm.getFrom(), 
					new NeighborEntry(homeloc, vrm.getCreationTime()));
			
			if(isHostInMsgVR(getHost(), vrm))
			{
				for(Message m : this.getMessageCollection())
				{
					if(!isCheckInMessage(m) && m.getTo() == vrm.getFrom())
					{
						doLook(m, homeloc, vrm.getCreationTime());
					}
				}
			}
		}
		else if(neighborhood.containsKey(vrm.getTo()) && !isLooking(vrm))
		{
//			System.out.println("Host:" + getHost().getAddress() + " starting Look " +
//					vrm.getId());
			NeighborEntry entry = neighborhood.get(vrm.getTo());
			doLook(vrm, entry.getLocation(), entry.getLocationTimestamp());
		}
		
		return vrm;
	}

	/**
	 * Called by a peer node when a message is sent from here to the node. It acts
	 * as an acknowledgment for the message. The peer passes in a reference to itself
	 * as the 'from' argument. If the peer is the final destination of the message,
	 * we can add the message to our list of finished messages. In all cases, when 
	 * the peer has received the message, we can delete it from our buffer.
	 * 
	 * @param vrm Message being acknowledged
	 * @param from Peer acknowledging the message
	 */
	public void ackMessage(Message vrm, DTNHost from)
	{
		if(from == vrm.getTo())
		{
			finishedMessages.add(vrm.getId());
			if(hasMessage(vrm.getId()))
				deleteMessage(vrm.getId(), false);
			return;
		}
		
		if((!isHostInMsgVR(getHost(), vrm) || isLooking(vrm)) && hasMessage(vrm.getId()))
		{
//			System.out.println("Host " + getHost().getAddress() + " deleting " + 
//					vrm.getId() + ".. outside its VR");
			deleteMessage(vrm.getId(), false);
		}
	}
	
	protected boolean removeOldCheckInMsgs(Message newCheckin)
	{
		for(Message m : getMessageCollection())
		{
			// delete any older checkIn messages (or this message if it's older)
			if(m != newCheckin && 
				isCheckInMessage(m) && 
				m.getTo() == newCheckin.getTo())
			{
				if(getCheckInSequenceNumber(newCheckin) > getCheckInSequenceNumber(m))
				{
					deleteMessage(m.getId(), false);
				}
				else
				{
					deleteMessage(newCheckin.getId(), false);
					return false;
				}
				break;
			}
		}
		return true;
	}
	
	protected Coord getFocus(Message m)
	{
		return (Coord) m.getProperty(MESSAGE_FOCUS_P);
	}
	
	protected boolean isCheckInMessage(Message m)
	{
		return m.getProperty(MESSAGE_IS_CHECKIN_P) != null;
	}
	
	protected Coord getCheckInLocation(Message m)
	{
		return (Coord) m.getProperty(MESSAGE_HOME_LOC_P);
	}
	
	protected boolean isLooking(Message m)
	{
		return ((Boolean)m.getProperty(MESSAGE_LOOK_P)).booleanValue();
	}
	
	protected long getCheckInSequenceNumber(Message m)
	{
		return ((Long) m.getProperty(MESSAGE_CHECKIN_SEQNUM_P)).longValue();
	}
	
	protected void doLook(Message m, Coord toLoc, double locationTime)
	{
		m.updateProperty(MESSAGE_LOOK_P, true);
		m.updateProperty(MESSAGE_FOCUS_P, toLoc);
		m.updateProperty(MESSAGE_LOOK_TIMEOUT_P, 2*SimClock.getTime()-locationTime+600);
//		System.out.println("Starting look: " + m.getId() + ' '+toLoc);
	}
	
	protected void expireLook(Message m, double timeNow)
	{
		if(!isLooking(m))return;
		
		double timeout = ((Double)m.getProperty(GeoRouter.MESSAGE_LOOK_TIMEOUT_P)).doubleValue();
		if(timeout < timeNow)
		{
			m.updateProperty(MESSAGE_LOOK_P, false);
			m.updateProperty(MESSAGE_FOCUS_P, hash(m.getTo()));
		}
	}
	
	@Override
	public void update()
	{
		super.update();
		
		/*
		 * At regular intervals, each node needs to generate a message to its
		 * Virtual Repository to get messages.
		 */
		{
			double time = SimClock.getTime();
			if(time > nextCheckInTime /*&& this.getHost().getAddress() == 18*/) {
				createCheckInMessage();
				//nextCheckInTime = Integer.MAX_VALUE;
				nextCheckInTime = time + GeoRouter.checkInInterval;
			}
			
			updateNeighborhood(time);
			updateMessageCollection(time);
		}
		
		if(!this.canStartTransfer() || this.isTransferring())
			return; // nothing to transfer or is currently transferring 
		
		/*
		 * try messages that could be delivered to final recipient, i.e. all 
		 * messages whose final destination is a peer to which we're a currently 
		 * connected.
		 */ 
		{
			if (exchangeDeliverableMessages() != null)
			{
				return;
			}
		}
		Collection<Message> msgCollection = getMessageCollection();
		java.util.List<Tuple<Message, Connection>> messages = 
							new LinkedList<Tuple<Message, Connection>>();
		Coord here = this.getHost().getLocation(); //host knows its own location
		
		/*
		 * For each message, we need to determine several things: 
		 *   1. Do we know the actual location of the destination or do we have to
		 *      use its hashed VR location contained in the message.
		 *   2. Are we inside the VR for this message or not.
		 *   
		 */
		for(Message m : msgCollection)
		{
			DTNHost to = m.getTo(); //Note: the (peer) DTNHost knows its coordinate
									//location in the simulation, but an actual
									//implementation may not have this info for its
									//peer, so we don't use it here.
								
			Coord msgDest;
			boolean isDestOutsideNeighborhood =
				!neighborhood.containsKey(to) || isCheckInMessage(m);
			if(isDestOutsideNeighborhood)
			{
				/*
				 * this host doesn't know the actual location or this is a check-in msg 
				 * and the actual location should be ignored, use hashed location stored
				 * in message.
				 */
				msgDest = getFocus(m);
			}
			else {
				msgDest = neighborhood.get(to).getLocation();
			}
			
			/*
			 * Compute information for this host. 
			 */
			double distToMsgDest = Math.hypot(msgDest.getX() - here.getX(), 
											msgDest.getY() - here.getY());
			boolean isThisHostInVR = distToMsgDest < vrRadius;
			
//			double myDirectionAngle = myDest.equals(here) ? Double.NaN : 
//				Math.atan2(myDest.getY() - here.getY(), myDest.getX() - here.getX());
			
//			double msgDirectionFromHere = Math.atan2(msgDest.getY() - here.getY(), 
//					msgDest.getX() - here.getX());
			
//			boolean myMovingTowards = myDirectionAngle != Double.NaN &&
//				Math.abs(myDirectionAngle - msgDirectionFromHere) < Math.PI / 4;
			
//			double mySpeed = getHost().getSpeed(),
//						 mySpeedX = mySpeed * Math.cos(myDirectionAngle),
//						 mySpeedY = mySpeed * Math.sin(myDirectionAngle);
		
			for(Connection c : getHost())
			//for(Connection c : getConnections())
			{
				DTNHost peer = c.getOtherNode(getHost());
				if(((ActiveRouter)peer.getRouter()).isTransferring())
					continue;
				
				Coord peerLoc = peer.getLocation();
				
				/*
				 * If both this host and the peer are in the VR for the msg and we don't
				 * have the actual location of the destination node, then we need to
				 * transfer the msg to the peer. This essentially broadcasts the message 
				 * to all peers inside the VR.
				 */
				if(isDestOutsideNeighborhood && isHostInMsgVR(peer, m))
				{
					messages.add(new Tuple<Message, Connection>(m, c));
					continue;
				}
				else if(isThisHostInVR && isDestOutsideNeighborhood)
					continue;
				
				double peerDistToMsgDest = Math.hypot(msgDest.getX() - peerLoc.getX(), 
						msgDest.getY() - peerLoc.getY());
				
//				double peerDirectionAngle = peerLoc.equals(peerDest) ? Double.NaN :
//					Math.atan2(peerDest.getY() - peerLoc.getY(), peerDest.getX() - 
//							peerLoc.getX());
				
//				double msgDirFromPeer =	Math.atan2(msgDest.getY() - peerLoc.getY(), 
//						msgDest.getX() - peerLoc.getX());
				
//				boolean peerMovingTowards = peerDirectionAngle != Double.NaN &&
//					Math.abs(peerDirectionAngle - msgDirFromPeer) < Math.PI / 4;
				
//				double peerSpeed = peer.getSpeed(),
//							 peerRelSpeedX = peerSpeed * Math.cos(peerDirectionAngle) - mySpeedX,
//							 peerRelSpeedY = peerSpeed * Math.sin(peerDirectionAngle) - mySpeedY,
//							 peerRelDirAngle = peerSpeed == 0.0 && mySpeed == 0.0 ? Double.NaN :
//								 Math.atan2(peerRelSpeedY, peerRelSpeedX);
				
//				boolean peerRelMovingTowards = peerRelDirAngle != Double.NaN &&
//					Math.abs(peerRelDirAngle - msgDirFromPeer) < Math.PI / 4;
				
				if(distToMsgDest > peerDistToMsgDest)
				{
					/*if(myMovingTowards && peerMovingTowards && peerDistToMsgDest < 
							distToMsgDest)
					{
						System.out.println("Host " + getHost().getAddress() + 
								" trying send "+ vrm.getId() +" to " + peer.getAddress() + " bc peerdist:" +
								peerDistToMsgDest + " < hostdist:" + distToMsgDest);
					}
					if(!myMovingTowards && !peerMovingTowards && peerDistToMsgDest <
							distToMsgDest)
						System.out.println("Host " + getHost().getAddress() + 
								" trying send "+vrm.getId()+" to " + peer.getAddress() + 
								" bc both moving away and peerdist:" +
								peerDistToMsgDest + " < hostdist:" + distToMsgDest);
					if(!myMovingTowards && peerMovingTowards)
						System.out.println("Host " + getHost().getAddress() + 
								" trying send "+vrm.getId()+" to " + peer.getAddress() + 
								" bc peer is moving towards and " + getHost().getAddress() + 
								" is not");
					if(peerRelMovingTowards || peerDistToMsgDest < distToMsgDest && !myMovingTowards)
						System.out.println("Relative velocity indicates transfer");*/
					/*if(peerRelMovingTowards)
						System.out.println("Host " + getHost().getAddress() + 
								" trying send "+ vrm.getId() +" to " + peer.getAddress() + 
								" bc relative velocity");
					if(peerDistToMsgDest < distToMsgDest && !myMovingTowards)
						System.out.println("Host " + getHost().getAddress() + 
								" trying send "+vrm.getId()+" to " + peer.getAddress() + 
								" bc " + getHost().getAddress() + " moving away and peerdist:" +
								peerDistToMsgDest + " < hostdist:" + distToMsgDest);*/
					/*if(!vrm.isCheckInMessage())
					{
						System.out.println("Host: " + getHost().getAddress() + " msg: " + vrm.getId()
								+ " look: " + vrm.isLooking() + " msgDest: " + msgDest.toString() + 
								"peer closer" + " knowDest: " + !isDestOutsideNeighborhood);
					}*/
					messages.add(new Tuple<Message, Connection>(m, c));
//					msgForwardNotFound = true;
				}
			}
		}
		
		{
			Collections.shuffle(messages);
//			Tuple<Message, Connection> t;
			if((tryMessagesForConnected(messages)) != null)
			{
//				System.out.println("Sending " +t.getKey().toString() + " from " + 
//					getHost().getAddress() + " to " + t.getValue().getOtherNode(getHost()).getAddress());
			
			}
		}
	}
	
	private void updateNeighborhood(double time)
	{
		for(Connection conn : getHost())
		//for(Connection conn : getConnections())
		{
			DTNHost otherHost = conn.getOtherNode(getHost());
			neighborhood.put(otherHost, new NeighborEntry(otherHost.getLocation(), time));
		}
		
		for(Iterator<Map.Entry<DTNHost, NeighborEntry>> i =
						neighborhood.entrySet().iterator(); i.hasNext();)
		{
			Map.Entry<DTNHost, NeighborEntry> e = i.next();
			if(e.getValue().isExpired(time)) {
				i.remove();
			}
		}
	}
	
	private void updateMessageCollection(double time)
	{
		for(Message m : this.getMessageCollection())
		{
			expireLook(m, time);
		}
	}

	@Override
	public MessageRouter replicate()
	{
		return new GeoRouter(this);
	}
	
	/**
	 * Entry for the neighborhood map. Each entry contains a stored location for
	 * an associated DTNHost (the key of the map) and a time when this entry 
	 * should expire and be removed from the map. 
	 * 
	 * @author PJ Dillon
	 */
	protected class NeighborEntry
	{
		private Coord knownLocation;
		private double locationTimestamp; //time at which the neighbor was at knownlocation
		private int expirationTime;
		
		NeighborEntry(Coord location, double timestampOfLocation)
		{
			knownLocation = location;
			locationTimestamp = timestampOfLocation;
			setExpiration();
		}
		
		public Coord getLocation()
		{
			return knownLocation;
		}
		
		public double getLocationTimestamp()
		{
			return locationTimestamp;
		}
		
		public boolean isExpired(double timeNow)
		{
			return expirationTime < timeNow;
		}
		
		public void refresh()
		{
			setExpiration();
		}
		
		private void setExpiration()
		{
			expirationTime = SimClock.getIntTime() + 
			GeoRouter.neighborExpirationInterval;
		}
	}
}
