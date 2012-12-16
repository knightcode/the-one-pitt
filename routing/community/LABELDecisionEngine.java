/*
 * @(#)LABELDecisionEngine.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.*;

import core.*;
import routing.*;

/**
 * <p>Implements the LABEL routing protocol described in <em>How Small Labels 
 * create Big Improvements</em> by Pan Hui and Jon Crowcroft (2007). The authors
 * found that, where nodes self describe their community with some text label, 
 * they are more likely to contact nodes with in their community. While the 
 * paper doesn't discuss a protocol that uses programmatic community detection 
 * techniques, other papers of the authors reference the idea (though do not
 * specifically present it). Thus, this class is a bit of an extrapolation of
 * their various works on the matter.</p>
 * 
 * <p>The protocol has each node only forward message to members of the 
 * destination's community, i.e. at any given time, if a connected peer declares
 * the destination of a message to be in its local community, then a node
 * decides to transfer the message to that peer.</p>
 * 
 * <pre>
 * \@inproceedings{4144796,
 *   Author = {Hui, Pan and Crowcroft, Jon},
 *   Doi = {10.1109/PERCOMW.2007.55},
 *   Journal = {Pervasive Computing and Communications Workshops, 2007. PerCom 
 *      Workshops '07. Fifth Annual IEEE International Conference on},
 *   Month = {March},
 *   Pages = {65 -70},
 *   Title = {How Small Labels Create Big Improvements},
 *   Year = {2007}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class LABELDecisionEngine 
				implements RoutingDecisionEngine, CommunityDetectionEngine
{
	/** Name corresponding to the community detection class to use in this router 
	 */
	public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg";
	
	protected CommunityDetection community;
	
	/** 
	 * Records the times at which each open connection started. Used to compute
	 * the duration of each connection (needed by the community detection algs).
	 */
	protected Map<DTNHost, Double> startTimestamps;
	
	/**
	 * A record of the entire connection history of this node for the whole 
	 * simulation. As each connection goes down, a new entry is added into the 
	 * list for the peer that just disconnected. 
	 */
	protected Map<DTNHost, List<Duration>> connHistory;
	
	/**
	 * Initializes the decision engine using the given Settings object, extracting
	 * the class of community detection algorithm to use. This constructor does
	 * NOT create instances of the member fields since this constructor is 
	 * generally only used to create a "prototype" object that each actual 
	 * instance replicates.
	 * 
	 * @param s Settings from which to get class name 
	 */
	public LABELDecisionEngine(Settings s)
	{
		if(s.contains(COMMUNITY_ALG_SETTING))
			this.community = (CommunityDetection) 
				s.createIntializedObject(s.getSetting(COMMUNITY_ALG_SETTING));
		else
			this.community = new SimpleCommunityDetection(s);
	}
	
	/**
	 * Initializes the LABEL decision engine to be a copy of the parameter 
	 * decision engine.
	 * 
	 * @param proto LABELDecisionEngine to copy
	 */
	public LABELDecisionEngine(LABELDecisionEngine proto)
	{
		this.community = proto.community.replicate();
		
		startTimestamps = new HashMap<DTNHost, Double>();
		connHistory = new HashMap<DTNHost, List<Duration>>();
	}

	public void connectionUp(DTNHost thisHost, DTNHost peer){}

	/**
	 * Records the duration of the lost connection and informs the Community
	 * Detection object that the connection was last.
	 */
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		
		// Find or create the connection history list
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
		}
		else
			history = connHistory.get(peer);
		
		// add the new connection to the history
		if(etime - time > 0)
			history.add(new Duration(time, etime));
		
		// Inform the community detection object
		CommunityDetection peerCD = this.getOtherDecisionEngine(peer).community;
		community.connectionLost(thisHost, peer, peerCD, history);
		
		startTimestamps.remove(peer);
	}

	/**
	 * Starts timing the duration of the new connection and informs the community
	 * detection object of the new connection.
	 */
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		DTNHost myHost = con.getOtherNode(peer);
		LABELDecisionEngine de = this.getOtherDecisionEngine(peer);
		
		this.community.newConnection(myHost, peer, de.community);

		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
	}

	public boolean newMessage(Message m) {return true;}

	public boolean isFinalDest(Message m, DTNHost aHost)
		{return m.getTo() == aHost;} // Unicast Routings

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
		{return m.getTo() != thisHost;}

	/**
	 * LABEL Routing works such that we only send the message to hosts in the same
	 * local community as the message's destination.
	 */
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
	{
		if(m.getTo() == otherHost) return true;
		
		DTNHost dest = m.getTo();
		LABELDecisionEngine de = getOtherDecisionEngine(otherHost);
		
		return de.commumesWithHost(dest);
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		return !this.commumesWithHost(m.getTo());
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		return true;
	}

	public RoutingDecisionEngine replicate()
	{
		return new LABELDecisionEngine(this);
	}
	
	protected boolean commumesWithHost(DTNHost h)
	{
		return community.isHostInCommunity(h);
	}
	
	private LABELDecisionEngine getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : 
			"This router only works with other routers of same type";
		
		return (LABELDecisionEngine) ((DecisionEngineRouter)otherRouter)
						.getDecisionEngine();
	}

	public Set<DTNHost> getLocalCommunity()
	{
		return this.community.getLocalCommunity();
	}
}
