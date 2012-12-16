package routing;

import core.*;

/**
 * Defines the interface between DecisionEngineRouter and its decision making
 * object. 
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public interface RoutingDecisionEngine
{
	/**
	 * Called when a connection goes up between this host and a peer. Note that,
	 * doExchangeForNewConnection() may be called first.
	 * 
	 * @param thisHost
	 * @param peer
	 */
	public void connectionUp(DTNHost thisHost, DTNHost peer);
	
	/**
	 * Called when a connection goes down between this host and a peer.
	 * 
	 * @param thisHost
	 * @param peer
	 */
	public void connectionDown(DTNHost thisHost, DTNHost peer);
	
	/**
	 * Called once for each connection that comes up to give two decision engine
	 * objects on either end of the connection to exchange and update information
	 * in a simultaneous fashion. This call is provided so that one end of the 
	 * connection does not perform an update based on newly updated information 
	 * from the opposite end of the connection (real life would reflect an update
	 * based on the old peer information). 
	 * 
	 * @param con
	 * @param peer
	 */
	public void doExchangeForNewConnection(Connection con, DTNHost peer);
	
	/**
	 * Allows the decision engine to gather information from the given message and
	 * determine if it should be forwarded on or discarded. This method is only 
	 * called when a message originates at the current host (not when received 
	 * from a peer). In this way, applications can use a Message to communicate
	 * information to this routing layer.
	 * 
	 * @param m the new Message to consider routing
	 * @return True if the message should be forwarded on. False if the message 
	 * should be discarded.  
	 */
	public boolean newMessage(Message m);
	
	/**
	 * Determines if the given host is an intended recipient of the given Message.
	 * This method is expected to be called when a new Message is received at a
	 * given router. 
	 * 
	 * @param m Message just received
	 * @param aHost Host to check
	 * @return true if the given host is a recipient of this given message. False
	 * otherwise.
	 */
	public boolean isFinalDest(Message m, DTNHost aHost);
	
	/**
	 * Called to determine if a new message received from a peer should be saved
	 * to the host's message store and further forwarded on.
	 * 
	 * @param m Message just received
	 * @param thisHost The requesting host
	 * @return true if the message should be saved and further routed. 
	 * False otherwise.
	 */
	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost);
	
	/**
	 * Called to determine if the given Message should be sent to the given host.
	 * This method will often be called multiple times in succession as the
	 * DecisionEngineRouter loops through its respective Message or Connection
	 * Collections.  
	 * 
	 * @param m Message to possibly sent
	 * @param otherHost peer to potentially send the message to.
	 * @return true if the message should be sent. False otherwise.
	 */
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost);
	
	/**
	 * Called after a message is sent to some other peer to ask if it should now
	 * be deleted from the message store. 
	 * 
	 * @param m Sent message
	 * @param otherHost Host who received the message
	 * @return true if the message should be deleted. False otherwise.
	 */
	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost);
	
	/**
	 * Called if an attempt was unsuccessfully made to transfer a message to a 
	 * peer and the return code indicates the message is old or already delivered,
	 * in which case it might be appropriate to delete the message. 
	 * 
	 * @param m Old Message
	 * @param hostReportingOld Peer claiming the message is old
	 * @return true if the message should be deleted. False otherwise.
	 */
	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld);
	
	/**
	 * Duplicates this decision engine.
	 * 
	 * @return
	 */
	public RoutingDecisionEngine replicate();
}
