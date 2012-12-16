package routing.pubsub;

import java.util.*;
import routing.*;
import core.*;

public class ProphetDecisionEngine implements RoutingDecisionEngine
{
	protected final static String BETA_SETTING = "beta";
	protected final static String P_INIT_SETTING = "initial_p";
	protected final static String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
	
	public static final int CREATE_MSG = 1;
	public static final int SUBSCRIBE_MSG = 2;
	public static final int UNSUBSCRIBE_MSG = 3;
	public static final int DATA_MSG = 4;
	
	public static final String PUBNAME_PROP = "PubSub-pubname";
	public static final String MSGTYPE_PROP = "PubSub-msgType";
	
	/** 
	 * Maps each name to a forwarding decision value.
	 * The hope here is to create this class in such a way that the actual 
	 * decision can we made by another class.
	 *
	 */
	protected Map<String, Double> forwardingDecisionTable;
	
	protected Set<String> mySubscriptions;
	
	protected static final double P_SUBSCRIBER = 1.00;
	protected static final double DEFAULT_P_INIT = 0.75;
	protected static final double GAMMA = 0.92;
	protected static final double DEFAULT_BETA = 0.45;
	protected static final int    DEFAULT_UNIT = 30;
	
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
	
	public ProphetDecisionEngine(ProphetDecisionEngine de)
	{
		beta = de.beta;
		pinit = de.pinit;
		secondsInTimeUnit = de.secondsInTimeUnit;
		this.forwardingDecisionTable = new HashMap<String, Double>();
		this.mySubscriptions = new HashSet<String>(2);
		this.lastAgeUpdate = de.lastAgeUpdate;
	}
	
	public RoutingDecisionEngine replicate()
	{
		return new ProphetDecisionEngine(this);
	}
	
	public void connectionUp(DTNHost thisHost, DTNHost peer)
	{
		/*ProphetDecisionEngine pde = getOtherProphetDecisionEngine(peer);
		
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
				System.out.println("Host: " + thisHost.getAddress() + " setting value: " + myValue
						+ " for pub: " + publication);
				forwardingDecisionTable.put(publication, myValue);
			}
			else
			{
				myValue = forwardingDecisionTable.get(publication);
//				if(neighborValue > myValue)
				{
					System.out.println("Host: " + thisHost.getAddress() + " setting value: " + 
							(myValue + (1 - myValue) * neighborValue * beta) +
							" for pub: " + publication);
					forwardingDecisionTable.put(publication, myValue + (1 - myValue) * neighborValue * beta);
				}
			}
		}*/
	}
	
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		ProphetDecisionEngine de = getOtherProphetDecisionEngine(peer);
		Set<String> pubSet = new HashSet<String>(this.forwardingDecisionTable.size() 
				+ de.forwardingDecisionTable.size());
		pubSet.addAll(this.forwardingDecisionTable.keySet());
		pubSet.addAll(de.forwardingDecisionTable.keySet());
		
		this.ageValues();
		de.ageValues();
		
		for(String pub : pubSet)
		{
			double myOldValue = 0.0, peerOldValue = 0.0;
			
			if(this.forwardingDecisionTable.containsKey(pub))
				myOldValue = this.forwardingDecisionTable.get(pub);
			if(de.forwardingDecisionTable.containsKey(pub))
				peerOldValue = de.forwardingDecisionTable.get(pub);
			
			if(myOldValue != P_SUBSCRIBER)
			{
				double newValue;
				if(peerOldValue == P_SUBSCRIBER && myOldValue == 0.0)
					newValue = pinit;
				else
					newValue = myOldValue + (1 - myOldValue) * peerOldValue * beta;
				this.forwardingDecisionTable.put(pub, newValue);
				System.out.println("Host: " + con.getOtherNode(peer).getAddress() + 
						" setting value: " + newValue + " for pub: " + pub + " old value: "
						+ myOldValue);
			}
			
			if(peerOldValue != P_SUBSCRIBER)
			{
				double newValue;
				if(myOldValue == P_SUBSCRIBER && peerOldValue == 0.0)
					newValue = pinit;
				else
					newValue = peerOldValue + (1 - peerOldValue) * myOldValue * beta;
				de.forwardingDecisionTable.put(pub, newValue);
				System.out.println("Host: " + peer.getAddress() + 
						" setting value: " + newValue + " for pub: " + pub + " old value: "
						+ peerOldValue);
			}
		}
	}

	public void connectionDown(DTNHost thisHost, DTNHost peer){}
	
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
		
		double myValue = forwardingDecisionTable.get(pubname),
					peerValue = de.forwardingDecisionTable.get(pubname); 
		
		/*if(myValue > peerValue)
			System.out.print("Not sending msg: ");
		else
			System.out.print("Sending msg: ");
		System.out.println(m.getId() + " to host: " + 
					otherHost.getAddress() + " myP: " + myValue + " peerP: " + peerValue);
		*/
		return myValue < peerValue;
	}
	
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		String pubname = (String) m.getProperty(PUBNAME_PROP);
		assert pubname != null;
		return !isSubscriber(pubname);
	}
	
	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		String pubname = (String) m.getProperty(PUBNAME_PROP);
		assert pubname != null;
		return isSubscriber(pubname);
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		return shouldDeleteOldMessage(m, otherHost);
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		String pubname = (String) m.getProperty(PUBNAME_PROP);
		assert pubname != null;
		
		ProphetDecisionEngine de = this.getOtherProphetDecisionEngine(hostReportingOld);
		
		return de.isSubscriber(pubname);
	}
	
	private ProphetDecisionEngine getOtherProphetDecisionEngine(DTNHost host)
	{
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (ProphetDecisionEngine) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
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
			/*System.out.println("Host: " + myHost.getAddress() + " aging pub: " +
					e.getKey() + " old value: " + e.getValue() + " mult: " + mult +
					" new val: " + (e.getValue()*mult));*/
			e.setValue(e.getValue()*mult);
		}
	
		this.lastAgeUpdate = SimClock.getTime();
	}
	private boolean isSubscriber(String pubname)
	{
		return mySubscriptions.contains(pubname);
	}

}
