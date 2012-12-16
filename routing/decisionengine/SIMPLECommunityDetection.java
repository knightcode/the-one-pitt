package routing.decisionengine;

import java.util.*;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.community.CommunityDetectionEngine;

/**
 * Implements the SIMPLE Community Detection Algorithm from Yoneki et al. 2007
 * (Bibtex record included for convenience). 
 * 
 * @inproceedings{1298166,
 *	Title = {A socio-aware overlay for publish/subscribe communication in 
 *		delay tolerant networks},
 *  Address = {New York, NY, USA},
 *	Author = {Yoneki, Eiko and Hui, Pan and Chan, ShuYan and Crowcroft, Jon},
 *	Booktitle = {MSWiM '07: Proceedings of the 10th ACM Symposium on Modeling, 
 *		analysis, and simulation of wireless and mobile systems},
 *	Doi = {http://doi.acm.org/10.1145/1298126.1298166},
 *	Isbn = {978-1-59593-851-0},
 *	Location = {Chania, Crete Island, Greece},
 *	Pages = {225--234},
 *	Publisher = {ACM},
 *	Url = {http://portal.acm.org/ft_gateway.cfm?
 *		id=1298166&type=pdf&coll=GUIDE&dl=GUIDE&CFID=55409507&CFTOKEN=69953247},
 *	Year = {2007},
 * }	
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public class SIMPLECommunityDetection implements RoutingDecisionEngine, CommunityDetectionEngine
{
	public static final String LAMBDA_SETTING = "lambda";
	public static final String GAMMA_SETTING = "gamma";
	public static final String FAMILIAR_SETTING = "familiarThreshold";
	
	protected Set<DTNHost> familiarSet;
	protected Set<DTNHost> localCommunity;
	
	protected Map<DTNHost, Double> startTimestamps;
	protected Map<DTNHost, List<Duration>> connHistory;
	
	protected DTNHost myHost;
	
	protected double lambda;
	protected double gamma;
	protected double familiarThreshold;
	
	public SIMPLECommunityDetection(Settings s)
	{
		this.lambda = s.getDouble(LAMBDA_SETTING);
		this.gamma = s.getDouble(GAMMA_SETTING);
		this.familiarThreshold = s.getDouble(FAMILIAR_SETTING);
	}
	
	public SIMPLECommunityDetection(SIMPLECommunityDetection proto)
	{
		this.lambda = proto.lambda;
		this.gamma = proto.gamma;
		this.familiarThreshold = proto.familiarThreshold;
		
		familiarSet = new HashSet<DTNHost>();
		localCommunity = new HashSet<DTNHost>();
		startTimestamps = new HashMap<DTNHost, Double>();
		connHistory = new HashMap<DTNHost, List<Duration>>();
	}

	public void connectionUp(DTNHost thisHost, DTNHost peer)
		{localCommunity.add(thisHost);}

	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		if(this.familiarSet.contains(peer)) return;
		
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
		}
		else
			history = connHistory.get(peer);
		
		if(etime - time > 0)
			history.add(new Duration(time, etime));
		
		Iterator<Duration> i = history.iterator();
		time = 0;
		while(i.hasNext())
		{
			Duration d = i.next();
			time += d.end - d.start;
		}
		
		if(time > this.familiarThreshold)
		{
			//System.out.println(thisHost.toString() + " adding " + peer + " as Familiar");
			this.familiarSet.add(peer);
			this.localCommunity.add(peer);
		}
		
		startTimestamps.remove(peer);
	}

	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		DTNHost myHost = con.getOtherNode(peer);
		SIMPLECommunityDetection de = this.getOtherDecisionEngine(peer);
		boolean addPeerToMyLocal=false, addMeToPeerLocal=false;
		
		// Step 4 of alg: if not in familar set, begin recording contact duration
		if(!this.familiarSet.contains(peer))
			this.startTimestamps.put(peer, SimClock.getTime());
		if(!de.familiarSet.contains(myHost))
			de.startTimestamps.put(myHost, SimClock.getTime());
		
		// Add peer to my local community if needed
		if(!this.localCommunity.contains(peer))
		{
			/*
			 * The algorithm calls for computing the size of  the intersection of 
			 * peer's familiarSet and this host's localCommunity. We divide that by
			 * the size of the peer's familiar set
			 */
			
			int count=0, peerFsize = de.familiarSet.size();
			for(DTNHost h : de.familiarSet)
				if(this.localCommunity.contains(h))
					count++;
			//if(count > 0)
				//System.out.println(myHost.toString() + " count: " + count + " peerSize: " + peerFsize);
			if(addPeerToMyLocal = ((double)count)/peerFsize > this.lambda)
			{
				//System.out.println(myHost.toString() + " adding " + peer + " to Local Community");
				this.localCommunity.add(peer);
			}
		}
		
		if(!de.localCommunity.contains(myHost))
		{
			int count = 0, myFsize = this.familiarSet.size();
			for(DTNHost h : this.familiarSet)
				if(de.localCommunity.contains(h))
					count++;
			if(addMeToPeerLocal = ((double)count)/myFsize > de.lambda)
			{
				//System.out.println(peer.toString() + " adding " + myHost + " to Local Community");
				de.localCommunity.add(myHost);
			}
		}
		
		if(addPeerToMyLocal || addMeToPeerLocal)
		{
			Set<DTNHost> commUnion = new HashSet<DTNHost>(this.localCommunity.size() +
					de.localCommunity.size() + 2);
			commUnion.addAll(this.localCommunity);
			commUnion.addAll(de.localCommunity);
			commUnion.add(peer); // Just to make sure
			commUnion.add(myHost);
			
			//boolean mergedMine = false, mergedPeer = false;
			int count = 0;
			for(DTNHost h : this.localCommunity)
				if(de.localCommunity.contains(h))
					count++;
			
			if(addPeerToMyLocal && count > this.gamma * commUnion.size())
			{
				//System.out.println(myHost.toString() + " merging community with " + peer);
				this.localCommunity.addAll(de.localCommunity);
				//mergedMine = true;
			}
			if(addMeToPeerLocal && count > de.gamma * commUnion.size())
			{
				//System.out.println(peer.toString() + " merging community with " + myHost);
				de.localCommunity.addAll(this.localCommunity);
				//mergedPeer = true;
			}
			//if(mergedMine && !mergedPeer || !mergedMine && mergedPeer)
				//System.out.println("Asymmetric community merging detected");
		}
	}

	public boolean newMessage(Message m)
	{
		return true;
	}

	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		return m.getTo() == aHost;
	}

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		return m.getTo() != thisHost;
	}

	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
	{
//		if(m.getTo() == otherHost) return true;
		
		return true;
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		return false;
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		return false;
	}

	public RoutingDecisionEngine replicate()
	{
		return new SIMPLECommunityDetection(this);
	}

	private SIMPLECommunityDetection getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (SIMPLECommunityDetection) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
	}
	
	private class Duration
	{
		double start;
		double end;
		
		public Duration(double s, double e) {start = s; end = e;}
	}

	public Set<DTNHost> getFamiliars() {return this.familiarSet;}
	public Set<DTNHost> getLocalCommunity() {return this.localCommunity;}
}
