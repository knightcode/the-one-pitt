package routing.decisionengine;

import java.util.*;

import core.*;
import routing.*;

public class MEEDDecisionEngine implements RoutingDecisionEngine
{
	
	protected Map<Tuple<DTNHost, DTNHost>, Double> avgWaitingTimes;

	public MEEDDecisionEngine(Settings s)
	{
		
	}
	
	public MEEDDecisionEngine(MEEDDecisionEngine meed)
	{
		
	}
	
	public RoutingDecisionEngine replicate()
	{
		return new MEEDDecisionEngine(this);
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
	// TODO Auto-generated method stub

	}

	public void connectionUp(DTNHost thisHost, DTNHost peer)
	{
	// TODO Auto-generated method stub

	}

	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		// TODO Auto-generated method stub
		
	}

	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		// TODO Auto-generated method stub
		return false;
	}

	public boolean newMessage(Message m)
	{
		// TODO Auto-generated method stub
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

}
