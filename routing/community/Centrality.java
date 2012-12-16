/*
 * @(#)Centrality.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.*;

import core.*;

/**
 * <p>
 * Abstracts the concept of a centrality computation algorithm (where Centrality
 * is defined in the context of a social network). For the purposes of routing
 * protocols like Distributed Bubble Rap, centrality must be computed globally,
 * using the history of all previous contacts, and locally, using only the
 * contact history of those hosts in some local community, where the community
 * is defined by some community detection algorithm.
 * </p>
 * 
 * <p>
 * In this way, the Centrality interface semantically requires any class
 * employing one of its subclasses to keep track of the connection history of
 * the node at which these instancces are stored. To use the local centrality
 * computation, the using object would also have to create and use a
 * CommunityDetection instance. As of right now,
 * {@link routing.community.DistributedBubbleRap} is the only class that does
 * this.
 * </p>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public interface Centrality
{
	/**
	 * Returns the computed global centrality based on the connection history
	 * passed as an argument.  
	 * 
	 * @param connHistory Contact History on which to compute centrality
	 * @return Value corresponding to the global centrality
	 */
	public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory);
	
	/**
	 * Returns the computed local centrality based on the connection history and
	 * community detection objects passed as parameters.
	 * 
	 * @param connHistory Contact history on which to compute centrality
	 * @param cd CommunityDetection object that knows the local community
	 * @return Value corresponding to the local centrality
	 */
	public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory, 
			CommunityDetection cd);
	
	/**
	 * Duplicates a Centrality object. This is a convention of the ONE to easily
	 * create multiple instances of objects based on defined settings. 
	 * 
	 * @return A duplicate Centrality instance
	 */
	public Centrality replicate();
}
