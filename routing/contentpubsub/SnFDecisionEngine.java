package routing.contentpubsub;

import java.util.*;

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
	
	public static final String FILTER_PROP = "PubSub-filter";
	public static final String MSGTYPE_PROP = "PubSub-msgType";
	
	protected static final double DEFAULT_TIMEDIFF = 300;
	protected static final double defaultTransitivityThreshold = 1.0;
	
	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;
	protected Map<ContentFilter, Double> forwardingDecisionTable;
	
	protected Set<ContentFilter> mySubscriptions;
	
	public SnFDecisionEngine(Settings s)
	{
		this.initialNrofCopies = s.getInt(NROF_COPIES_S);
		
		forwardingDecisionTable = new HashMap<ContentFilter, Double>();
		mySubscriptions = new HashSet<ContentFilter>(2);
	}
	
	public SnFDecisionEngine(SnFDecisionEngine snf)
	{
		this.forwardingDecisionTable = new HashMap<ContentFilter, Double>();
		this.mySubscriptions = new HashSet<ContentFilter>(2);
	}
	
	public RoutingDecisionEngine replicate()
	{
		return new SnFDecisionEngine(this);
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer){}

	public void connectionUp(DTNHost thisHost, DTNHost peer)
	{
		SnFDecisionEngine de = getOtherCSnFDecisionEngine(peer);
		double distTo = thisHost.getLocation().distance(peer.getLocation());
		double speed = peer.getPath() == null ? 0 : peer.getPath().getSpeed();
		double timediff;
		
		if(speed == 0.0)
			timediff = DEFAULT_TIMEDIFF;
		else
			timediff = distTo/speed;
		
		for(Map.Entry<ContentFilter, Double> entry : de.forwardingDecisionTable.entrySet())
		{
			ContentFilter filter = entry.getKey();
			double neighborvalue = de.getValueForFilter(filter),
						 myValue = 0.0;
			
			if(this.forwardingDecisionTable.containsKey(filter))
				myValue = this.getValueForFilter(filter);
			
			if(myValue + timediff < neighborvalue)
			{
				System.out.println("Host: " + thisHost.getAddress() + " adding transitive value " +
						(neighborvalue - timediff) + " from host " + peer.getAddress() + " with value: " +
						neighborvalue);
				this.forwardingDecisionTable.put(filter, neighborvalue - timediff);
			}
		}
		
	}

	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		// TODO Auto-generated method stub
		
	}

	public boolean newMessage(Message m)
	{
		int type = (Integer) m.getProperty(MSGTYPE_PROP);
		
		switch(type)
		{
			case CREATE_MSG:
			{
				
				return false;
			}
			case UNSUBSCRIBE_MSG:
			{
				ContentFilter filter = (ContentFilter) m.getProperty(FILTER_PROP);
				mySubscriptions.remove(filter);
				return false;
			}
			case SUBSCRIBE_MSG:
			{
				ContentFilter filter = (ContentFilter) m.getProperty(FILTER_PROP);
				mySubscriptions.add(filter);
				forwardingDecisionTable.put(filter, SimClock.getTime());
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
		
		//ContentMetadata cm = (ContentMetadata) m.getProperty(METADATA_PROP);
//		assert cm != null;
		
		SnFDecisionEngine de = this.getOtherCSnFDecisionEngine(otherHost);
		
		//double maxValue = 0.0;
		
		for(Map.Entry<ContentFilter, Double> entry : de.forwardingDecisionTable.entrySet())
		{
			ContentFilter filter = entry.getKey();
			double	neighborValue = de.getValueForFilter(filter),
							myValue = this.getValueForFilter(filter);
			if(filter.match(m) &&  neighborValue - transitivityTimerThreshold > myValue)
			{
				return true;
			}
		}
		return false;
		// If the other host has never seen the publication, don't send
		/*if(!de.forwardingDecisionTable.containsKey(pubname))
		{
			return false;
		}*/

		// We get here when the other host has seen the pub, if we haven't, send
		/*if(!this.forwardingDecisionTable.containsKey(pubname)) 
		{
			return true;
		}*/
		
		/*System.out.print("Host with value: " + 
				getValueForPublication(pubname) + " has neighbor: " + otherHost.getAddress() + 
				" with value: " + de.getValueForPublication(pubname) + " for pub: " + 
				pubname + ' ');
		if(getValueForPublication(pubname) < de.getValueForPublication(pubname) -
				this.transitivityTimerThreshold)
			System.out.println("sending");
		else
			System.out.println();*/
		/*return getValueForFilter(pubname) < 
			de.getValueForFilter(pubname) - transitivityTimerThreshold;*/
	}
	

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
		
		nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		
		return !isFinalDest(m, null);
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
		SnFDecisionEngine de = this.getOtherCSnFDecisionEngine(hostReportingOld);
		
		return de.isFinalDest(m, null);
	}
	
	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		for(ContentFilter f : mySubscriptions)
			if(f.match(m))
				return true;
		return false;
	}
	
	private SnFDecisionEngine getOtherCSnFDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof PubSubRouter : "This router only works " + 
		" with other routers of same type";
		
		return (SnFDecisionEngine) ((PubSubRouter)otherRouter).getDecisionEngine();
	}
	
	private double getValueForFilter(ContentFilter filter)
	{
		if(mySubscriptions.contains(filter)) return SimClock.getTime();
		else if(forwardingDecisionTable.containsKey(filter))
			return forwardingDecisionTable.get(filter);
		else
			return 0.0;
	}
	
	/*private boolean isSubscriber(ContentFilter f)
	{
		return mySubscriptions.contains(f);
	}*/
}
