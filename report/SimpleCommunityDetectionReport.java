/*
 * @(#)SimpleCommunityDetectionReport.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package report;

import java.util.*;

import core.*;

/**
 * <p>Reports the community structure of the simulation scenario using the
 * SIMPLE distributed community detection algorithm described in <em>Distributed 
 * Community Detection in Delay Tolerant Networks</em> by Pan Hui et al. (bibtex 
 * record is included below for convenience). A node using
 * SIMPLE keeps a record of all the nodes it has met and the cummulative contact
 * duration it has had with each. Once this total contact duration for one of 
 * these nodes exceeds a configurable parameter, the node is added to the host's
 * familiar set and local community. When two peers meet, they compair their 
 * familiar sets and local communities for commonalities and may decide to merge
 * their local communities, which intuitively means that they've decided that 
 * they each tend to meet the same nodes often, suggesting they both are and 
 * should be part of the same local community. </p> 
 * 
 * <pre>
 * \@inproceedings{1366929,
 * Address = {New York, NY, USA},
 * Author = {Hui, Pan and Yoneki, Eiko and Chan, Shu Yan and Crowcroft, Jon},
 * Booktitle = {MobiArch '07: Proceedings of 2nd ACM/IEEE international workshop
 *  on Mobility in the evolving internet architecture},
 * Doi = {http://doi.acm.org/10.1145/1366919.1366929},
 * Isbn = {978-1-59593-784-8},
 * Location = {Kyoto, Japan},
 * Pages = {1--8},
 * Publisher = {ACM},
 * Title = {Distributed community detection in delay tolerant networks},
 * Year = {2007}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public class SimpleCommunityDetectionReport extends Report implements
		ConnectionListener
{
	/** Threshold value for adding a host to the local community -setting id 
	 * {@value} 
	 */
	public static final String LAMBDA_SETTING = "lambda";
	
	/** Threshold value for merging the local community with a peer -setting id 
	 * {@value} 
	 */
	public static final String GAMMA_SETTING = "gamma";
	
	/** Total contact time threshold for adding a node to the familiar set 
	 * -setting id {@value} 
	 */
	public static final String FAMILIAR_SETTING = "familiarThreshold";
	
	protected Map<DTNHost, Set<DTNHost>> familiars;
	protected Map<DTNHost, Set<DTNHost>> localCommunities;
	
	protected Map<Pair, Double> startTimestamps;
	protected Map<Pair, List<Duration>> connHistories;
	
	protected double lambda;
	protected double gamma;
	protected double familiarThreshold;
	
	public SimpleCommunityDetectionReport()
	{
		this.familiars = new HashMap<DTNHost, Set<DTNHost>>();
		this.localCommunities = new HashMap<DTNHost, Set<DTNHost>>();
		this.startTimestamps = new HashMap<Pair, Double>();
		this.connHistories = new HashMap<Pair, List<Duration>>();
		
		Settings s = getSettings();
		this.lambda = s.getDouble(LAMBDA_SETTING);
		this.gamma = s.getDouble(GAMMA_SETTING);
		this.familiarThreshold = s.getDouble(FAMILIAR_SETTING);
	}

	public void hostsConnected(DTNHost host1, DTNHost host2)
	{
		boolean addH2ToH1Community=false, addH1ToH2Community=false;
		
		Set<DTNHost> host1familiarSet; 
		Set<DTNHost> host2familiarSet;
		Set<DTNHost> h1lc;
		Set<DTNHost> h2lc;
		
		// get or create familiar and community sets
		if(this.familiars.containsKey(host1))
			host1familiarSet = this.familiars.get(host1);
		else
		{
			host1familiarSet = new HashSet<DTNHost>();
			familiars.put(host1, host1familiarSet);
		}
		
		if(this.familiars.containsKey(host2))
			host2familiarSet = this.familiars.get(host2);
		else
		{
			host2familiarSet = new HashSet<DTNHost>();
			this.familiars.put(host2, host2familiarSet);
		}
		
		if(this.localCommunities.containsKey(host1))
			h1lc = this.localCommunities.get(host1);
		else
		{
			h1lc = new HashSet<DTNHost>();
			h1lc.add(host1);
			this.localCommunities.put(host1, h1lc);
		}
		if(this.localCommunities.containsKey(host2))
			h2lc = this.localCommunities.get(host2);
		else
		{
			h2lc = new HashSet<DTNHost>();
			h2lc.add(host2);
			this.localCommunities.put(host2, h2lc);
		}
		
		// Step 4 of alg: if not in familar set, begin recording contact duration
		if(!host1familiarSet.contains(host2) || !host2familiarSet.contains(host1))
		{
			startTimestamps.put(new Pair(host1, host2), SimClock.getTime());
		}
		
		// Add peer to my local community if needed
		if(!h1lc.contains(host2))
		{
			/*
			 * The algorithm calls for computing the size of  the intersection of 
			 * peer's familiarSet and this host's localCommunity. We divide that by
			 * the size of the peer's familiar set
			 */
			
			int count=0, peerFsize = host2familiarSet.size();
			for(DTNHost h : host2familiarSet)
				if(h1lc.contains(h))
					count++;
			if(addH2ToH1Community = ((double)count)/peerFsize > lambda)
			{
				h1lc.add(host2);
			}
		}
		
		// Repeat for the other host
		if(!h2lc.contains(host1))
		{
			int count = 0, myFsize = host1familiarSet.size();
			for(DTNHost h : host1familiarSet)
				if(h2lc.contains(h))
					count++;
			if(addH1ToH2Community = ((double)count)/myFsize > lambda)
			{
				h2lc.add(host1);
			}
		}
		
		if(addH2ToH1Community || addH1ToH2Community)
		{
			// Decide if the communities have enough in common to merge them
			Set<DTNHost> commUnion = new HashSet<DTNHost>(h1lc.size() +
					h2lc.size() + 2);
			commUnion.addAll(h1lc);
			commUnion.addAll(h2lc);
			commUnion.add(host1); // Just to make sure
			commUnion.add(host2);
			
			int count = 0;
			for(DTNHost h : h1lc)
				if(h2lc.contains(h))
					count++;
			
			if(addH2ToH1Community && count > this.gamma * commUnion.size())
			{
				h1lc.addAll(h2lc);
			}
			if(addH1ToH2Community && count > gamma * commUnion.size())
			{
				h2lc.addAll(h1lc);
			}
		}

	}

	public void hostsDisconnected(DTNHost host1, DTNHost host2)
	{
		double time, etime;
		
		if(familiars.containsKey(host1) && familiars.get(host1).contains(host2) &&
			 familiars.containsKey(host2) && familiars.get(host2).contains(host1))
			return;
		
		Pair p = new Pair(host1, host2);
		
		// record connection length in connection history
		
		time = startTimestamps.get(p);
		etime = SimClock.getTime();
		
		List<Duration> history;
		if(!connHistories.containsKey(p))
		{
			history = new LinkedList<Duration>();
			connHistories.put(p, history);
		}
		else
			history = connHistories.get(p);

		if(etime - time > 0)
			history.add(new Duration(time, etime));

		Iterator<Duration> i = history.iterator();
		time = 0;
		while(i.hasNext())
		{
			Duration d = i.next();
			time += d.end - d.start;
		}
	
		// if the peers' total connection history crossed the threshold, add
		// as familiars
		if(time > this.familiarThreshold)
		{
			familiars.get(host1).add(host1);
			familiars.get(host2).add(host1);
			localCommunities.get(host1).add(host2);
			localCommunities.get(host2).add(host1);
		}
		
		startTimestamps.remove(p);

	}
	
	@Override
	public void done()
	{
		// Find only the unique communities 
		// (some hosts may record the same community)
		List<Set<DTNHost>> communities = new LinkedList<Set<DTNHost>>();
		for(Map.Entry<DTNHost, Set<DTNHost>> entry : 
			this.localCommunities.entrySet())
		{
			Set<DTNHost> comm = entry.getValue();
			boolean alreadyHaveCommunity = false;
			for(Set<DTNHost> c : communities)
			{
				if(c.containsAll(comm) && comm.containsAll(c))
				{
					alreadyHaveCommunity = true;
				}	
			}
			if(!alreadyHaveCommunity && comm.size() > 0)
				communities.add(comm);
		}
		
		// print the size and content of each community
		for(Set<DTNHost> c : communities)
			write("" + c.size() + ' ' + c);
		super.done();
	}

	/**
	 * A helper class for the connection histories map that stores a pair of 
	 * DTNHosts and ensures an appropriate contract of equals. 
	 * 
	 * @author PJ Dillon, University of Pittsburgh
	 *
	 */
	private class Pair
	{
		DTNHost h1;
		DTNHost h2;
		
		public Pair(DTNHost host1, DTNHost host2) {h1 = host1; h2 = host2;}

		@Override
		public int hashCode()
		{
			return h1.hashCode() + h2.hashCode();
		}

		@Override
		public boolean equals(Object obj)
		{
			if(obj == null) return false;
			if(obj instanceof Pair)
			{
				Pair p = (Pair)obj;
				return (p.h1 == this.h1 && p.h2 ==this.h2) ||
					(p.h1 == this.h2 && p.h2 == this.h1);
			}
			return false;
		}
		
	}
	
	/**
	 * Helper class to store start and end timestamps for a connection.
	 * 
	 * @author PJ Dillon, University of Pittsburgh
	 *
	 */
	private class Duration
	{
		double start;
		double end;
		
		public Duration(double s, double e) {start = s; end = e;}
	}

}
