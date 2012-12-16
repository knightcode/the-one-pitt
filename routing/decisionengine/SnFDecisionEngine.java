/*
 * @(#)SnFDecisionEngine.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.decisionengine;

import java.util.*;

import core.*;
import routing.*;

/**
 * An implementation of the Spray and Focus Routing protocol using the
 * Decision Engine framework.
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class SnFDecisionEngine implements RoutingDecisionEngine
{
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "SprayAndFocus.copies";
	/** identifier for the difference in timer values needed to forward on a message copy */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	
	protected static final double DEFAULT_TIMEDIFF = 300;
	protected static final double defaultTransitivityThreshold = 1.0;
	
	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;
	
	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, Double> recentEncounters;
	
	public SnFDecisionEngine(Settings s)
	{
		initialNrofCopies = s.getInt(NROF_COPIES_S);
		
		if(s.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = s.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;
		
		recentEncounters = new HashMap<DTNHost, Double>();
	}
	
	public SnFDecisionEngine(SnFDecisionEngine snf)
	{
		this.initialNrofCopies = snf.initialNrofCopies;
		this.transitivityTimerThreshold = snf.transitivityTimerThreshold;
		recentEncounters = new HashMap<DTNHost, Double>();
	}
	
	public RoutingDecisionEngine replicate()
	{
		return new SnFDecisionEngine(this);
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer){}

	public void connectionUp(DTNHost thisHost, DTNHost peer){}

	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		SnFDecisionEngine de = this.getOtherSnFDecisionEngine(peer);
		DTNHost myHost = con.getOtherNode(peer);
		double distBwt = myHost.getLocation().distance(peer.getLocation());
		double mySpeed = myHost.getPath() == null ? 0 : myHost.getPath().getSpeed(),
			peerSpeed = peer.getPath() == null ? 0 : peer.getPath().getSpeed(),
			myTimediff, peerTimediff;
		
		if(mySpeed == 0.0)
			myTimediff = DEFAULT_TIMEDIFF;
		else
			myTimediff = distBwt/mySpeed;
		
		if(peerSpeed == 0.0)
			peerTimediff = DEFAULT_TIMEDIFF;
		else
			peerTimediff = distBwt/peerSpeed;
		
		//Add references to other tables
		recentEncounters.put(peer, SimClock.getTime());
		de.recentEncounters.put(myHost, SimClock.getTime());
		
		Set<DTNHost> hostSet = new HashSet<DTNHost>(this.recentEncounters.size() 
				+ de.recentEncounters.size());
		hostSet.addAll(this.recentEncounters.keySet());
		hostSet.addAll(de.recentEncounters.keySet());
		
		for(DTNHost h : hostSet)
		{
			double myTime, peerTime;
			
			if(this.recentEncounters.containsKey(h)) 
				myTime = this.recentEncounters.get(h);
			else
				myTime = -1.0;
			if(de.recentEncounters.containsKey(h))
				peerTime = de.recentEncounters.get(h);
			else
				peerTime = -1.0;
			
			//update my table for host h
			if(myTime < 0.0 || myTime + myTimediff < peerTime)
				recentEncounters.put(h, peerTime - myTimediff);
			
			//update peer table for host h
			if(peerTime < 0.0 || peerTime + peerTimediff < myTime)
				de.recentEncounters.put(h, myTime - peerTimediff);
		}
	}

	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
		nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		
		return m.getTo() == aHost;
	}

	public boolean newMessage(Message m)
	{
		m.addProperty(MSG_COUNT_PROP, initialNrofCopies);
		return true;
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		return m.getTo() == hostReportingOld;
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

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		return m.getTo() != thisHost;
	}

	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
	{
		if(m.getTo() == otherHost) return true;
		
		int nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
		if(nrofCopies > 1) return true;
		
		DTNHost dest = m.getTo();
		
		SnFDecisionEngine de = this.getOtherSnFDecisionEngine(otherHost);
		
		//If otherHost has no entry, don't bother
		if(!de.recentEncounters.containsKey(dest))
			return false;
		
		// If otherHost has an entry and I don't, send
		if(!this.recentEncounters.containsKey(dest))
			return true;
		
		if(de.recentEncounters.get(dest) > this.recentEncounters.get(dest))
			return true;
		
		return false;
	}

	private SnFDecisionEngine getOtherSnFDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (SnFDecisionEngine) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
	}
}
