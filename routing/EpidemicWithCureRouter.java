package routing;

import java.util.*;

import core.*;

public class EpidemicWithCureRouter extends ActiveRouter
{
	/**
	 * Stores the IDs of messages that have been delivered to their final 
	 * destination, which is not necessarily this host. This info is used to
	 * inform nodes of a message's completion. Thus, if a node is carrying one of 
	 * these messages and it tries to send to a neighbor, it will be informed that
	 * the message has already been delivered and it can be deleted from the
	 * buffer.
	 */
	protected Set<String> finishedMessages;
	
	public EpidemicWithCureRouter(Settings s)
	{
		super(s);
		finishedMessages = new HashSet<String>();
	}

	public EpidemicWithCureRouter(EpidemicWithCureRouter r)
	{
		super(r);
		finishedMessages = new HashSet<String>(r.finishedMessages);
	}

	//@Override
	public MessageRouter replicate()
	{
		return new EpidemicWithCureRouter(this);
	}

	@Override
	public int receiveMessage(Message m, DTNHost from)
	{
		if(finishedMessages.contains(m.getId()))
			return DENIED_DELIVERED;
		return super.receiveMessage(m, from);
	}

	@Override
	protected int startTransfer(Message m, Connection con)
	{
		int retVal = super.startTransfer(m, con);
		if(retVal == DENIED_DELIVERED)
		{
			String id = m.getId();
			finishedMessages.add(id);
			if(hasMessage(id))
				deleteMessage(id, false);
		}
		return retVal;
	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message m = super.messageTransferred(id, from);
		if(isDeliveredMessage(m))
			finishedMessages.add(m.getId());
		return m;
	}

	@Override
	public void update() 
	{
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
	}

}
