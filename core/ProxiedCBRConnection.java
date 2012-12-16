package core;

public class ProxiedCBRConnection extends CBRConnection
{
	protected NetworkInterface fromIntermediary;
	protected NetworkInterface toIntermediary;
	
	public ProxiedCBRConnection(DTNHost fromNode, NetworkInterface fromInterface, 
			NetworkInterface fromIntermediary, 
			DTNHost toNode,	NetworkInterface toInterface, 
			NetworkInterface toIntermediary,
			int connectionSpeed)
	{
		super(fromNode, fromInterface, toNode, toInterface, connectionSpeed);
		this.fromIntermediary = fromIntermediary;
		this.toIntermediary = toIntermediary;
	}
	
	/**
	 * Returns the interface adjacent to the parameter interface 
	 * @param i The interface in this end of the connection
	 * @return The requested interface
	 */
	public NetworkInterface getOtherInterface(NetworkInterface i) {
		if (i == this.fromInterface) {
			return this.fromIntermediary;
		}
		else {
			return this.toIntermediary;
		}
	}
	
	/**
	 * Returns the interface at the opposite end of the connection, the one 
	 * involved in the actual communication
	 * @param i
	 * @return
	 */
	public NetworkInterface getEndpointInterface(NetworkInterface i)
	{
		if(i == this.fromInterface) return this.toInterface;
		else return this.fromInterface;
	}
	
	/*public void disconnect(NetworkInterface initiator)
	{
		setUpState(false);
		
		NetworkInterface other = initiator == fromInterface ? toInterface : fromInterface;
		
		initiator.disconnect(other);
		
		fromInterface.removeConnection(this, null);
		toInterface.removeConnection(this, null);
		
		toNode.connectionDown(this);
		fromNode.connectionDown(this);
	}*/
	
	public String toString() { return "Proxied " + super.toString();}
}
