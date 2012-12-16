/*
 * @(#)CommunityDetection.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;

import java.util.*;

import core.*;

/**
 * <p>An abstraction of community detection algorithms that track and assemble a 
 * set of nodes defined to the local community of this object. Each of the 
 * algorithms must update their information whenever a new connection is formed
 * or one is lost, and they are responsible for determining if a given host is
 * a member of the community or not.</p>
 * 
 * <p> These algorithms are needed by social-based routing protocols, such as 
 * {@link routing.community.DistributedBubbleRap} and 
 * {@link routing.community.LABELDecisionEngine} to determine appropriate 
 * forwarding paths based on community membership. </p>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public interface CommunityDetection
{
	/**
	 * Called to inform the object that a new connection was made. 
	 * 
	 * @param myHost Host to which this CommunityDetection object belongs
	 * @param peer Host that connected to this host
	 * @param peerCD Instance of CommunityDetection residing at the new peer 
	 */
	public void newConnection(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD);
	
	/**
	 * Called to inform the object that a connection was lost.
	 * 
	 * @param myHost Host to which this CommunityDetection object belongs
	 * @param peer Host that is now disconnected from this object
	 * @param peerCD Instance of CommunityDetection residing at the lost peer
	 * @param connHistory Entire connection history between this host and the peer
	 */
	public void connectionLost(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD, List<Duration> connHistory);
	
	/**
	 * Determines if the given host is a member of the local community of this 
	 * object. 
	 * 
	 * @param h Host to consider
	 * @return true if h is a member of the community, false otherwise
	 */
	public boolean isHostInCommunity(DTNHost h);
	
	/**
	 * Returns a set of hosts that are members of the local community of this 
	 * object. This method is really only provided for {@link 
	 * report.CommunityDetectionReport} to use.
	 * 
	 * @return the Set representation of the local community
	 */
	public Set<DTNHost> getLocalCommunity();
	
	/**
	 * Duplicates this CommunityDetection object.
	 * 
	 * @return A semantically equal copy of this CommunityDetection object
	 */
	public CommunityDetection replicate();
}
