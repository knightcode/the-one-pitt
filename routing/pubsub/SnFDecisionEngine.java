package routing.pubsub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import routing.*;
import core.*;

public class SnFDecisionEngine implements RoutingDecisionEngine
{
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocus.copies";
	/** identifier for the difference in timer values needed to forward on a message copy */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	
	public static final int CREATE_MSG = 1;
	public static final int SUBSCRIBE_MSG = 2;
	public static final int UNSUBSCRIBE_MSG = 3;
	public static final int DATA_MSG = 4;
	
	public static final String PUBNAME_PROP = "PubSub-pubname";
	public static final String MSGTYPE_PROP = "PubSub-msgType";
	
	protected static final double DEFAULT_TIMEDIFF = 300;
	protected static final double defaultTransitivityThreshold = 3.0;
	
	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;
	protected Map<String, Double> forwardingDecisionTable;
	
	protected Set<String> mySubscriptions;
	
	public SnFDecisionEngine(Settings s)
	{
		this.initialNrofCopies = s.getInt(NROF_COPIES_S);
		
		if(s.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = s.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;
		
		forwardingDecisionTable = new HashMap<String, Double>();
		mySubscriptions = new HashSet<String>(2);
	}
	
	public SnFDecisionEngine(SnFDecisionEngine snf)
	{
		this.initialNrofCopies = snf.initialNrofCopies;
		this.transitivityTimerThreshold = snf.transitivityTimerThreshold;
		forwardingDecisionTable = new HashMap<String, Double>();
		mySubscriptions = new HashSet<String>(2);
	}
	
	public RoutingDecisionEngine replicate()
	{
		return new SnFDecisionEngine(this);
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
						neighborvalue + " for pub: " + pub);
				this.forwardingDecisionTable.put(pub, neighborvalue - timediff);
			}
		}
		
	}
	
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		/*SnFDecisionEngine de = getOtherSnFDecisionEngine(peer);
		double distTo = con.getOtherNode(peer).getLocation().distance(peer.getLocation());
		double speed = peer.getPath() == null ? 0 : peer.getPath().getSpeed();
		double timediff;
		
		if(speed == 0.0)
			timediff = DEFAULT_TIMEDIFF;
		else
			timediff = distTo/speed;
		
		Set<String> pubSet = new HashSet<String>(this.forwardingDecisionTable.size() 
				+ de.forwardingDecisionTable.size());
		pubSet.addAll(this.forwardingDecisionTable.keySet());
		pubSet.addAll(de.forwardingDecisionTable.keySet());
		
		for(String pub : pubSet)
		{
			double myValue = getValueForPublication(pub),
						 peerValue = de.getValueForPublication(pub);
		}*/
	}

	public boolean newMessage(Message m)
	{
		String pubname = (String) m.getProperty(PUBNAME_PROP);
		int type = (Integer) m.getProperty(MSGTYPE_PROP);
		
		if(pubname == null)
			throw new SimError("No Publication Name specified in new Message");
		
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
			/*System.out.println("Neighbor: " + otherHost.getAddress() + " of Host: " +
					"has no info on pub " + pubname);*/
			return false;
		}

		// We get here when the other host has seen the pub, if we haven't, send
		if(!this.forwardingDecisionTable.containsKey(pubname)) 
		{
			/*System.out.println("Neighbor: " + otherHost.getAddress() + " of Host: " + 
				 " knows pub " + pubname);*/
			return true;
		}
		
		/*System.out.print("Host with value: " + 
				getValueForPublication(pubname) + " has neighbor: " + otherHost.getAddress() + 
				" with value: " + de.getValueForPublication(pubname) + " for pub: " + 
				pubname + ' ');
		if(getValueForPublication(pubname) < de.getValueForPublication(pubname) -
				this.transitivityTimerThreshold)
			System.out.println("sending");
		else
			System.out.println();*/
		return getValueForPublication(pubname) < 
			de.getValueForPublication(pubname) - transitivityTimerThreshold;
	}
	
	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
		nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		
		String pubname = (String) m.getProperty(PUBNAME_PROP);
		assert pubname != null;
		return isSubscriber(pubname);
	}

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
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

	private SnFDecisionEngine getOtherSnFDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (SnFDecisionEngine) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
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
}
