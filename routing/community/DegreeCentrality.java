/*
 * @(#)DegreeCentrality.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.List;
import java.util.Map;

import core.*;

/**
 * <p>Computes the global and local centrality of a node using a version of the 
 * DEGREE centrality computation algorithm described in <em>BUBBLE Rap: 
 * Social-based Forwarding in Delay Tolerant Networks</em> by Pan Hui et al. 
 * (2008) (the bibtex record is included below for convenience). The discussion
 * around this algorithm was a bit vague. This class does not compute the node's
 * degree using any kind of averaging over a set of past time windows as is done
 * in {@link CWindowCentrality}, {@link SWindowCentrality}, or 
 * {@link AvgDegreeCentrality}. Instead, the node's centrality is defined here
 * as the total number of unique contacts (degree) the node has had over the 
 * entire simulation. 
 * </p>
 * 
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
 * @see AvgDegreeCentrality
 * @see Centrality
 */
public class DegreeCentrality implements Centrality
{
	public DegreeCentrality(Settings s){}
	public DegreeCentrality(DegreeCentrality proto){}
	
	public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory)
	{
		return connHistory.size();
	}

	public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory,
			CommunityDetection cd)
	{
		int centrality = 0;
		for(DTNHost h : connHistory.keySet())
		{
			if(cd.isHostInCommunity(h))
				centrality++;
		}
		return centrality;
	}

	public Centrality replicate()
	{
		return new DegreeCentrality(this);
	}

}
