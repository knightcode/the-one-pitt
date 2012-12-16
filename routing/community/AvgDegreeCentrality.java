/*
 * @(#)AvgDegreeCentrality.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.*;

import core.*;

/**
 * <p>Computes the global and local centrality of a node using the average 
 * degree centrality algorithm described in <em>BUBBLE Rap: Social-based 
 * Forwarding in Delay Tolerant Networks</em> by Pan Hui et al. (2008) (the 
 * bibtex record is included below for convenience). The discussion
 * around this algorithm was a bit vague in that it describes a protocol for 
 * estimating the node's centrality by maintaining the node's degree (unique
 * past encounters). They go on to say that this value, computed for all time,
 * does not correlate to the node's centrality, but the node degree over the 
 * past six hours does. They further claim that computing this value for all
 * time is difficult and state that computing a average for multiple six hour 
 * periods in the past would be a better measure, which they do in two other 
 * algorithms, {@link CWindowCentrality} and {@link SWindowCentrality}. A third
 * algorithm called DEGREE is also presented as a computation of the node degree
 * for all time, but it's unclear whether this refers to an average over all
 * prior six-hour time windows or simply the total number of prior unique 
 * encounters. As such, both possible algorithms are implemented. 
 * AvgDegreeCentrality computes the average node degree by breaking the entire 
 * past connection history into a series of time windows, computing the node 
 * degree in each window, and computing average.<p> 
 * 
 * <p>This computation is done at regular intervals instead of every time the 
 * global and local centrality measures are requested.</p> 
 * 
 * <p>This class looks for two settings:
 * <ul>
 * <li><strong>timeWindow</strong> &ndash; the duration of each time interval 
 * (epoch) to consider. Default: 6 hours</li>
 * <li><strong>computeInterval</strong> &ndash; the amount of simulation time 
 * between updates to the centrality values. A longer interval reduces 
 * simulation time at the expense of accuracy. Default: 10 minutes</li>
 * </ul>
 * <p>
 * <pre>
 * \@inproceedings{1374652,
 *	Address = {New York, NY, USA},
 *	Author = {Hui, Pan and Crowcroft, Jon and Yoneki, Eiko},
 *	Booktitle = {MobiHoc '08: Proceedings of the 9th ACM international symposium 
 *		on Mobile ad hoc networking and computing},
 *	Doi = {http://doi.acm.org/10.1145/1374618.1374652},
 *	Isbn = {978-1-60558-073-9},
 *	Location = {Hong Kong, Hong Kong, China},
 *	Pages = {241--250},
 *	Publisher = {ACM},
 *	Title = {BUBBLE Rap: Social-based Forwarding in Delay Tolerant Networks},
 *	Url = {http://portal.acm.org/ft_gateway.cfm?id=1374652&type=pdf&coll=GUIDE&dl=GUIDE&CFID=55195392&CFTOKEN=93998863},
 *	Year = {2008}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 * @see Centrality
 * @see DegreeCentrality
 */
public class AvgDegreeCentrality implements Centrality
{
	/** Width of time window into which to group past history -setting id 
	   {@value} */
	public static final String CENTRALITY_WINDOW_SETTING = "timeWindow";
	/** Interval between successive updates to centrality values -setting id 
	   {@value} */
	public static final String COMPUTATION_INTERVAL_SETTING = "computeInterval";
	
	/** Time to wait before recomputing centrality values (node degree) */
	protected static int COMPUTE_INTERVAL = 600; // seconds, i.e. 10 minutes
	/** Width of each time interval in which to count the node's degree */
	protected static int CENTRALITY_TIME_WINDOW = 21600; // 6 hrs, from literature
	
	/** Saved global centrality from last computation */
	protected double globalCentrality;
	/** Saved local centrality from last computation */
	protected double localCentrality;
	
	/** timestamp of last global centrality computation */
	protected int lastGlobalComputationTime;
	/** timestamp of last local centrality computation */ 
	protected int lastLocalComputationTime;
	
	public AvgDegreeCentrality(Settings s) 
	{
		if(s.contains(CENTRALITY_WINDOW_SETTING))
			CENTRALITY_TIME_WINDOW = s.getInt(CENTRALITY_WINDOW_SETTING);
		
		if(s.contains(COMPUTATION_INTERVAL_SETTING))
			COMPUTE_INTERVAL = s.getInt(COMPUTATION_INTERVAL_SETTING);
	}
	
	public AvgDegreeCentrality(AvgDegreeCentrality proto)
	{
		// set these back in time (negative values) to do one computation at the 
		// start of the sim
		this.lastGlobalComputationTime = this.lastLocalComputationTime = 
			-COMPUTE_INTERVAL;
	}
	
	public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory)
	{
		if(SimClock.getIntTime() - this.lastGlobalComputationTime < COMPUTE_INTERVAL)
			return globalCentrality;
		
		// initialize
		int epochCount = SimClock.getIntTime() / CENTRALITY_TIME_WINDOW;
		int[] centralities = new int[epochCount];
		int epoch, timeNow = SimClock.getIntTime();
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		for(int i = 0; i < epochCount; i++)
			nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
		
		/*
		 * For each node, loop through connection history until we crossed all
		 * the epochs we need to cover
		 */
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			DTNHost h = entry.getKey();
			for(Duration d : entry.getValue())
			{
				int timePassed = (int)(timeNow - d.end);
				
				// if we reached the end of the last epoch, we're done with this node
				if(timePassed > CENTRALITY_TIME_WINDOW * epochCount)
					break;
				
				// compute the epoch this contact belongs to
				epoch = timePassed / CENTRALITY_TIME_WINDOW;
				
				// Only consider each node once per epoch
				Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
				if(nodesAlreadyCounted.contains(h))
					continue;
				
				// increment the degree for the given epoch
				centralities[epoch]++;
				nodesAlreadyCounted.add(h);
			}
		}
		
		// compute and return average node degree
		int sum = 0;
		for(int i = 0; i < epochCount; i++) 
			sum += centralities[i];
		this.globalCentrality = ((double)sum) / epochCount;
		
		this.lastGlobalComputationTime = SimClock.getIntTime();
		
		return this.globalCentrality;
	}

	public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory,
			CommunityDetection cd)
	{
		if(SimClock.getIntTime() - this.lastLocalComputationTime < COMPUTE_INTERVAL)
			return localCentrality;
		
		// centralities will hold the count of unique encounters in each epoch
		int epochCount = SimClock.getIntTime() / CENTRALITY_TIME_WINDOW;
		int[] centralities = new int[epochCount];
		int epoch, timeNow = SimClock.getIntTime();
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		for(int i = 0; i < epochCount; i++)
			nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
		
		// local centrality only considers nodes in the local community
		Set<DTNHost> community = cd.getLocalCommunity();
		
		/*
		 * For each node, loop through connection history until we crossed all
		 * the epochs we need to cover
		 */
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			DTNHost h = entry.getKey();
			
			// if the host isn't in the local community, we don't consider it
			if(!community.contains(h))
				continue;
			
			for(Duration d : entry.getValue())
			{
				int timePassed = (int)(timeNow - d.end);
				
				// if we reached the end of the last epoch, we're done with this node
				if(timePassed > CENTRALITY_TIME_WINDOW * epochCount)
					break;
				
				// compute the epoch this contact belongs to
				epoch = timePassed / CENTRALITY_TIME_WINDOW;
				
				// Only consider each node once per epoch
				Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
				if(nodesAlreadyCounted.contains(h))
					continue;
				
				// increment the degree for the given epoch
				centralities[epoch]++;
				nodesAlreadyCounted.add(h);
			}
		}
		
		// compute and return average node degree
		int sum = 0;
		for(int i = 0; i < epochCount; i++) 
			sum += centralities[i];
		this.localCentrality = ((double)sum) / epochCount; 
		
		this.lastLocalComputationTime = SimClock.getIntTime();
		
		return this.localCentrality;
	}

	public Centrality replicate()
	{
		return new AvgDegreeCentrality(this);
	}

}
