package routing.pubsub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

public class ExpectedDelayDecisionEngine implements RoutingDecisionEngine
{
	
	public static final String PUBNAME_PROP = "PubSub-pubname";
	public static final String MSGTYPE_PROP = "PubSub-msgType";
	
	protected Map<String, RouteData> forwardingDecisionTable;
	
	protected Set<String> mySubscriptions;
	
	static protected RouteData subscriberMetric;
	
	public ExpectedDelayDecisionEngine(Settings s)
	{
		forwardingDecisionTable = new HashMap<String, RouteData>();
		mySubscriptions = new HashSet<String>(2);
		
		subscriberMetric = new RouteData();
		subscriberMetric.avgDelay = 0.0;
	}
	
	public ExpectedDelayDecisionEngine(ExpectedDelayDecisionEngine de)
	{
		forwardingDecisionTable = new HashMap<String, RouteData>();
		mySubscriptions = new HashSet<String>(2);
	}

	public RoutingDecisionEngine replicate()
	{
		return new ExpectedDelayDecisionEngine(this);
	}
	
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		// TODO Auto-generated method stub
		
	}

	public void connectionDown(DTNHost thisHost, DTNHost peer){}

	public void connectionUp(DTNHost thisHost, DTNHost peer)
	{
		ExpectedDelayDecisionEngine peerDe = getOtherSnFDecisionEngine(peer);
		
		for(Map.Entry<String, RouteData> entry : peerDe.forwardingDecisionTable.entrySet())
		{
			String pub = entry.getKey();
			RouteData neighborData = peerDe.getValueForPub(pub, entry.getValue()),
				myDelay = this.getValueForPub(pub, null);
			
			if(myDelay == null )
			{
				myDelay = new RouteData();
				// TODO: compute avgDelay and timestamp
				this.forwardingDecisionTable.put(pub, myDelay);
			}
		}
	}

	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		String pubname = (String) m.getProperty(PUBNAME_PROP);
		assert pubname != null;
		return isSubscriber(pubname);
	}

	public boolean newMessage(Message m)
	{
		return false;
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		// TODO Auto-generated method stub
		return false;
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		// TODO Auto-generated method stub
		return false;
	}

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		// TODO Auto-generated method stub
		return false;
	}

	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
	{
		// TODO Auto-generated method stub
		return false;
	}

	private boolean isSubscriber(String pubname)
	{
		return mySubscriptions.contains(pubname);
	}
	
	private ExpectedDelayDecisionEngine getOtherSnFDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (ExpectedDelayDecisionEngine) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
	}
	
	private RouteData getValueForPub(String pubname, RouteData entryValue)
	{
		if(mySubscriptions.contains(pubname)) return subscriberMetric;
		if(entryValue == null) return this.forwardingDecisionTable.get(pubname);
		return entryValue;
	}
	
	protected class RouteData
	{
		double avgDelay;
		double timeOfLastEncounter;
	}
}
