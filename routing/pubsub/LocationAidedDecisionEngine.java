package routing.pubsub;

import core.Connection;
import core.DTNHost;
import core.Message;
import routing.RoutingDecisionEngine;

public class LocationAidedDecisionEngine implements RoutingDecisionEngine
{

	public RoutingDecisionEngine replicate()
	{
		return null;
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
	// TODO Auto-generated method stub

	}

	public void connectionUp(DTNHost thisHost, DTNHost peer)
	{

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
	
	private class EncounterInfo
	{
		double locationTimestamp;
		double x;
		double y;
	}

}
