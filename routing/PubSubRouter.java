package routing;

import java.util.*;

import core.*;

public class PubSubRouter extends ActiveRouter
{
	public static final String PUBSUB_NS = "PubSubRouter";
	public static final String ENGINE_SETTING = "decisionEngine";
	
	public static final int CREATE_MSG = 1;
	public static final int SUBSCRIBE_MSG = 2;
	public static final int UNSUBSCRIBE_MSG = 3;
	public static final int DATA_MSG = 4;
	
	public static final String PUBNAME_PROP = "PubSub-pubname";
	public static final String MSGTYPE_PROP = "PubSub-msgType";
	
	protected RoutingDecisionEngine decider;
	
	protected List<Tuple<Message, Connection>> outgoingMessages;
	
	public PubSubRouter(Settings s)
	{
		super(s);
		
		Settings routeSettings = new Settings(PUBSUB_NS);
		
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject(
				"routing." + routeSettings.getSetting(ENGINE_SETTING));
	}
	
	public PubSubRouter(PubSubRouter r)
	{
		super(r);
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		
		decider = r.decider.replicate();
	}
	
	/*protected void init()
	{
		Settings routeSettings = new Settings(PUBSUB_NS);
		
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject(
				"routing." + 
				routeSettings.getSetting(ENGINE_SETTING));
	}*/
	
	@Override
	public MessageRouter replicate()
	{
		return new PubSubRouter(this);
	}
	

	/*
	 * Called when a message comes down from the Application on this node
	 * @see routing.ActiveRouter#createNewMessage(core.Message)
	 */
	@Override
	public boolean createNewMessage(Message m)
	{
		if(decider.newMessage(m))
		{
			makeRoomForNewMessage(m.getSize());
			addToMessages(m, true);
			return true;
		}
		return false;
	}
	
	@Override
	public void changedConnection(Connection con)
	{
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		if(con.isUp())
		{
			decider.connectionUp(myHost, otherNode);
			
			Collection<Message> msgs = getMessageCollection();
			for(Message m : msgs)
			{
				if(decider.shouldSendMessageToHost(m, otherNode))
					outgoingMessages.add(new Tuple<Message,Connection>(m, con));
			}
		}
		else
		{
			decider.connectionDown(myHost, otherNode);
			
			for(Iterator<Tuple<Message,Connection>> i = outgoingMessages.iterator(); 
					i.hasNext();)
			{
				Tuple<Message, Connection> t = i.next();
				if(t.getValue() == con)
					i.remove();
			}
		}
	}

	@Override
	protected int startTransfer(Message m, Connection con)
	{
		int retVal;
		
		if (!con.isReadyForTransfer())
		{
			return TRY_LATER_BUSY;
		}
		
		retVal = con.startTransfer(getHost(), m);
		if (retVal == RCV_OK) { // started transfer
			addToSendingConnections(con);
		}
		else if (deleteDelivered && retVal == DENIED_OLD && 
				decider.shouldDeleteOldMessage(m, con.getOtherNode(getHost())))
		{
			/* final recipient has already received the msg -> delete it */
			System.out.println("Host: " + getHost().getAddress() + " deleting msg: " +
					m.getId() + " bc host: " + con.getOtherNode(getHost()).getAddress() + 
					" returned DENIED_OLD");
			
//			removeMsgAndConFromOutgoingQueue(m, con);
			this.deleteMessage(m.getId(), false);
		}
		
		return retVal;
	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message incoming = removeFromIncomingBuffer(id, from);
//		String pubname = (String) incoming.getProperty(PUBNAME_PROP);
		
		//System.out.println("Host: " + getHost().getAddress() + 
//				" received message " + incoming.getId() + " for pub: " + pubname);
		
		if (incoming == null) {
			throw new SimError("No message with ID " + id + " in the incoming "+
					"buffer of " + getHost());
		}
		
		incoming.setReceiveTime(SimClock.getTime());
		
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, getHost());
			if (outgoing == null) break; // Some app wanted to drop the message
		}
		
		Message aMessage = (outgoing==null)?(incoming):(outgoing);
		
		
		//boolean isSubscriber = mySubscriptions.contains(pubname);
		boolean isFirstDelivery = decider.isFinalDest(aMessage, getHost()) && 
			!isDeliveredMessage(aMessage);
		
		if (outgoing!=null && decider.shouldSaveReceivedMessage(aMessage, getHost())) 
		{
			// not the final recipient and app doesn't want to drop the message
			// -> put to buffer
			addToMessages(aMessage, false);
			
			for(Connection c : getHost())
			//for(Connection c : getConnections())
			{
				DTNHost other = c.getOtherNode(getHost());
				if(decider.shouldSendMessageToHost(aMessage, other))
					outgoingMessages.add(new Tuple<Message, Connection>(aMessage, c));
			}
		}
		else if (isFirstDelivery) {
			this.deliveredMessages.put(id, aMessage);
		}
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, getHost(),
					isFirstDelivery);
		}
		
		return aMessage;
	}

	@Override
	protected void transferDone(Connection con)
	{
		Message transferred = con.getMessage();
		
		removeMsgAndConFromOutgoingQueue(transferred, con);
		
		if(decider.shouldDeleteSentMessage(transferred, con.getOtherNode(getHost())))
		{
			System.out.println("Host:" + getHost().getAddress() + " deleting msg: " + 
					transferred.getId());
			this.deleteMessage(transferred.getId(), false);
		}
	}
	
	@Override
	public void update()
	{
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		tryMessagesForConnected(outgoingMessages);
		
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(!this.hasMessage(t.getKey().getId()))
			{
				i.remove();
			}
		}
	}
	
	public RoutingDecisionEngine getDecisionEngine()
	{
		return this.decider;
	}
	
	protected void removeMsgAndConFromOutgoingQueue(Message m, Connection con)
	{
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(t.getKey().equals(m) && 
					t.getValue().equals(con))
			{
				i.remove();
				break;
			}
		}
	}
	
	/**
	 * A PRoPHET-based routing algorithm for publish/subscribe routing
	 * @author knightcode
	 *
	 */
	/*class ProphetDecisionEngine implements RoutingDecisionEngine
	{
		protected final static String BETA_SETTING = "beta";
		protected final static String P_INIT_SETTING = "initial_p";
		protected final static String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
		/** 
		 * Maps each name to a forwarding decision value.
		 * The hope here is to create this class in such a way that the actual 
		 * decision can we made by another class.
		 *
		 *
		protected Map<String, Double> forwardingDecisionTable;
		
		protected Set<String> mySubscriptions;
		
		protected static final double P_SUBSCRIBER = 1.00;
		protected static final double DEFAULT_P_INIT = 0.75;
		protected static final double GAMMA = 0.98;
		protected static final double DEFAULT_BETA = 0.75;
		protected static final int    DEFAULT_UNIT = 60;
		
		protected double beta;
		protected double pinit;
		protected double lastAgeUpdate; 
		protected int 	 secondsInTimeUnit;
		
		public ProphetDecisionEngine(Settings s)
		{
			//Settings s = new Settings();
			if(s.contains(BETA_SETTING))
				beta = s.getDouble(BETA_SETTING);
			else
				beta = DEFAULT_BETA;
			
			if(s.contains(P_INIT_SETTING))
				pinit = s.getDouble(P_INIT_SETTING);
			else
				pinit = DEFAULT_P_INIT;
			
			if(s.contains(SECONDS_IN_UNIT_S))
				secondsInTimeUnit = s.getInt(SECONDS_IN_UNIT_S);
			else
				secondsInTimeUnit = DEFAULT_UNIT;
			
			this.forwardingDecisionTable = new HashMap<String, Double>();
			mySubscriptions = new HashSet<String>(2);
			this.lastAgeUpdate = 0.0;
		}
		
		
		public void connectionUp(DTNHost host)
		{
			ProphetDecisionEngine pde = getOtherProphetDecisionEngine(host);
			
			this.ageValues();
			pde.ageValues();
			
			for(Map.Entry<String, Double> e : pde.forwardingDecisionTable.entrySet())
			{
				String publication = e.getKey();
				double neighborValue = e.getValue(), myValue;
				
				if(mySubscriptions.contains(publication)) continue;
				
				if(!forwardingDecisionTable.containsKey(publication))
				{
					if(neighborValue == P_SUBSCRIBER)
						myValue = pinit;
					else
						myValue = neighborValue * beta;
					System.out.println("Host: " + getHost().getAddress() + " setting value: " + myValue
							+ " for pub: " + publication);
					forwardingDecisionTable.put(publication, myValue);
				}
				else
				{
					myValue = forwardingDecisionTable.get(publication);
					//if(neighborValue > myValue)
					{
						System.out.println("Host: " + getHost().getAddress() + " setting value: " + 
								(myValue + (1 - myValue) * neighborValue * beta) +
								" for pub: " + publication);
						forwardingDecisionTable.put(publication, myValue + (1 - myValue) * neighborValue * beta);
					}
				}
			}
		}
		
		public void connectionDown(DTNHost host)
		{
			
		}
		
		public boolean newMessage(Message m)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			int type = (Integer) m.getProperty(MSGTYPE_PROP);
			
			switch(type)
			{
				case CREATE_MSG:
				{
					
					return false;
				}
				case UNSUBSCRIBE_MSG:
				{
					mySubscriptions.remove(pubname);
					forwardingDecisionTable.put(pubname, 0.00);
					return false;
				}
				case SUBSCRIBE_MSG:
				{
					mySubscriptions.add(pubname);
					forwardingDecisionTable.put(pubname, P_SUBSCRIBER);
					return false;
				}
				
				case DATA_MSG:
				{
					//System.out.println("Got DATA Msg: " + m.getId());
					return true;
				}
				default:
					System.out.println("Crap");
			}
			return false;
		}

		public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
		{
			ProphetDecisionEngine de = getOtherProphetDecisionEngine(otherHost);
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			
			ageValues();
			de.ageValues();
			
			if(!de.forwardingDecisionTable.containsKey(pubname)) return false;
			if(!forwardingDecisionTable.containsKey(pubname)) return true;
			
			return this.forwardingDecisionTable.get(pubname) < de.forwardingDecisionTable.get(pubname);
		}
		
		public boolean shouldSaveReceivedMessage(Message m)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			return !isSubscriber(pubname);
		}

		public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost){return false;}

		public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			
			ProphetDecisionEngine de = this.getOtherProphetDecisionEngine(hostReportingOld);
			
			return de.isSubscriber(pubname);
		}
		
		public boolean isFinalDest(Message m)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			return isSubscriber(pubname);
		}
		
		private ProphetDecisionEngine getOtherProphetDecisionEngine(DTNHost host)
		{
			MessageRouter otherRouter = host.getRouter();
			assert otherRouter instanceof SubjPubSubRouter : "This router only works " + 
			" with other routers of same type";
			
			return (ProphetDecisionEngine) ((SubjPubSubRouter)otherRouter).getDecisionEngine();
		}
		
		private void ageValues()
		{
			double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
			if (timeDiff == 0) {
				return;
			}
		
			double mult = Math.pow(GAMMA, timeDiff);
			for (Map.Entry<String, Double> e : forwardingDecisionTable.entrySet())
			{
				if(e.getValue() == P_SUBSCRIBER) continue;
				System.out.println("Host: " + getHost().getAddress() + " aging pub: " +
						e.getKey() + " old value: " + e.getValue() + " mult: " + mult +
						" new val: " + (e.getValue()*mult));
				e.setValue(e.getValue()*mult);
			}
		
			this.lastAgeUpdate = SimClock.getTime();
		}
		private boolean isSubscriber(String pubname)
		{
			return mySubscriptions.contains(pubname);
		}
	}*/
	
	/**
	 * A Spray and Focus routing algorithm for subject based publish/subscribe 
	 * routing
	 * @author PJ Dillon
	 *
	 */
	/*class SnFDecisionEngine implements RoutingDecisionEngine
	{
		/** identifier for the initial number of copies setting ({@value})*
		public static final String NROF_COPIES_S = "nrofCopies";
		/** Message property key for the remaining available copies of a message *
		public static final String MSG_COUNT_PROP = "SprayAndFocus.copies";
		/** identifier for the difference in timer values needed to forward on a message copy *
		public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
		
		protected static final double DEFAULT_TIMEDIFF = 300;
		protected static final double defaultTransitivityThreshold = 1.0;
		
		protected int initialNrofCopies;
		protected double transitivityTimerThreshold;
		protected Map<String, Double> forwardingDecisionTable;
		
		protected Set<String> mySubscriptions;
		
		public SnFDecisionEngine(Settings s)
		{
			this.initialNrofCopies = s.getInt(NROF_COPIES_S);
			
			forwardingDecisionTable = new HashMap<String, Double>();
			mySubscriptions = new HashSet<String>(2);
		}
		
		public void connectionDown(DTNHost thisHost, DTNHost peer){}

		public void connectionUp(DTNHost thisHost, DTNHost peer)
		{
			SnFDecisionEngine de = getOtherSnFDecisionEngine(peer);
			double distTo = thisHost.getLocation().distance(peer.getLocation());
			double speed = peer.getPath() == null ? 0 : peer.getPath().getSpeed();
			double timediff;
			
			if(speed == 0.0)
				timediff = DEFAULT_TIMEDIFF;
			else
				timediff = distTo/speed;
			
			for(Map.Entry<String, Double> entry : de.forwardingDecisionTable.entrySet())
			{
				String pub = entry.getKey();
				double neighborvalue = de.getValueForPublication(pub),
							 myValue = 0.0;
				
				if(this.forwardingDecisionTable.containsKey(pub))
					myValue = this.getValueForPublication(pub);
				
				if(myValue + timediff < neighborvalue)
				{
					System.out.println("Host: " + thisHost.getAddress() + " adding transitive value " +
							(neighborvalue - timediff) + " from host " + peer.getAddress() + " with value: " +
							neighborvalue);
					this.forwardingDecisionTable.put(pub, neighborvalue - timediff);
				}
			}
			
		}

		public boolean newMessage(Message m)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			int type = (Integer) m.getProperty(MSGTYPE_PROP);
			
			switch(type)
			{
				case CREATE_MSG:
				{
					
					return false;
				}
				case UNSUBSCRIBE_MSG:
				{
					mySubscriptions.remove(pubname);
					return false;
				}
				case SUBSCRIBE_MSG:
				{
					mySubscriptions.add(pubname);
					forwardingDecisionTable.put(pubname, SimClock.getTime());
					return false;
				}
				
				case DATA_MSG:
				{
					//System.out.println("Got DATA Msg: " + m.getId());
					m.addProperty(MSG_COUNT_PROP, initialNrofCopies);
					return true;
				}
				default:
					System.out.println("Crap");
			}
			return false;
		}

		public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
		{
			int nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
			if(nrofCopies > 1) return true;
			
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			
			SnFDecisionEngine de = this.getOtherSnFDecisionEngine(otherHost);
			
			// If the other host has never seen the publication, don't send
			if(!de.forwardingDecisionTable.containsKey(pubname))
			{
				System.out.println("Neighbor: " + otherHost.getAddress() + " of Host: " + 
						getHost().getAddress() + " has no info on pub " + pubname);
				return false;
			}

			// We get here when the other host has seen the pub, if we haven't, send
			if(!this.forwardingDecisionTable.containsKey(pubname)) 
			{
				System.out.println("Neighbor: " + otherHost.getAddress() + " of Host: " + 
						getHost().getAddress() + " knows pub " + pubname);
				return true;
			}
			
			System.out.print("Host: " + getHost().getAddress()+ " with value: " + 
					getValueForPublication(pubname) + " has neighbor: " + otherHost.getAddress() + 
					" with value: " + de.getValueForPublication(pubname) + " for pub: " + 
					pubname + ' ');
			if(getValueForPublication(pubname) < de.getValueForPublication(pubname) -
					this.transitivityTimerThreshold)
				System.out.println("sending");
			else
				System.out.println();
			return getValueForPublication(pubname) < 
				de.getValueForPublication(pubname) - transitivityTimerThreshold;
		}
		

		public boolean shouldSaveReceivedMessage(Message m)
		{
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
			
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
			
			m.updateProperty(MSG_COUNT_PROP, nrofCopies);
			
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			
			return !isSubscriber(pubname);
		}

		public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
		{
			int nrofCopies;
			
			nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
			if(nrofCopies > 1)
				nrofCopies /= 2;
			else
				return true;
			
			m.updateProperty(MSG_COUNT_PROP, nrofCopies);
			
			return false;
		}
		
		public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			
			SnFDecisionEngine de = this.getOtherSnFDecisionEngine(hostReportingOld);
			
			return de.isSubscriber(pubname);
		}
		
		public boolean isFinalDest(Message m)
		{
			String pubname = (String) m.getProperty(PUBNAME_PROP);
			assert pubname != null;
			return isSubscriber(pubname);
		}
		
		private SnFDecisionEngine getOtherSnFDecisionEngine(DTNHost h)
		{
			MessageRouter otherRouter = h.getRouter();
			assert otherRouter instanceof SubjPubSubRouter : "This router only works " + 
			" with other routers of same type";
			
			return (SnFDecisionEngine) ((SubjPubSubRouter)otherRouter).getDecisionEngine();
		}
		
		private double getValueForPublication(String pubname)
		{
			if(mySubscriptions.contains(pubname)) return SimClock.getTime();
			else return forwardingDecisionTable.get(pubname);
		}
		
		private boolean isSubscriber(String pubname)
		{
			return mySubscriptions.contains(pubname);
		}
		
		public void setHost(DTNHost h) {}
	}*/
}
