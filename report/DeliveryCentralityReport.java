/*
 * @(#)DeliveryCentralityReport.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package report;

import java.util.*;

import core.*;

/**
 * <p>Computes the Delivery Centrality of each node in the simulation where 
 * Delivery Centrality is defined as an integer counting the number of times a
 * given node lies on the shortest path between two other communicating nodes.
 * For each delivered message (on its first delivery), a count is incremented 
 * for each nodes listed in the message's list of hops.</p>
 * 
 * <p>This report is really intended to be used with Epidemic Routing since it 
 * (usually) finds the shortest path from a sender to a destination.</p>
 * 
 * <p>The report generated list each node's name on its own line followed by the
 * count of how many times it acted as a relay on the path of a delivered 
 * message.</p>
 * 
 * <p>This report follows the idea of Betweenness Centrality mentioned in 
 * <em>BUBBLE Rap: Social-based Forwarding in Delay Tolerant Networks<em> by
 * Pan Hui et al. (MobiHoc '08).</p> 
 * 
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public class DeliveryCentralityReport extends Report implements MessageListener
{
	/** Count of times each node lies on shortest path of a delivered message */
	protected Map<DTNHost, Integer> relayCounts;
	
	public DeliveryCentralityReport()
	{
		init();
	}

	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean firstDelivery)
	{
		if(firstDelivery)
		{
			List<DTNHost> hopNodes = m.getHops();
			
			//We want to skip the source and the destination node.
			for(int i = 1; i < hopNodes.size() - 1; i++)
			{
				DTNHost h = hopNodes.get(i);
				if(relayCounts.containsKey(h))
					relayCounts.put(h, relayCounts.get(h) + 1) ;
				else
					relayCounts.put(h, 1);
			}
		}
		
	}

	@Override
	protected void init()
	{
		super.init();
		relayCounts = new HashMap<DTNHost, Integer>();
	}

	@Override
	public void done()
	{
		for(Map.Entry<DTNHost, Integer> entry : relayCounts.entrySet())
		{
			write("" + entry.getKey() + ' ' + entry.getValue());
		}
		super.done();
	}

	public void newMessage(Message m){}
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to){}
	public void messageDeleted(Message m, DTNHost where, boolean dropped){}
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to){}
}
