package routing.contentpubsub;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import core.Connection;
import core.Message;
import core.Settings;
import core.Tuple;
import routing.ActiveRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

public class ContentPubSubRouter extends ActiveRouter
{
	public static final String PUBSUB_NS = "ContentPubSubRouter";
	public static final String ENGINE_SETTING = "decisionEngine";
	
	public static final int CREATE_MSG = 1;
	public static final int SUBSCRIBE_MSG = 2;
	public static final int UNSUBSCRIBE_MSG = 3;
	public static final int DATA_MSG = 4;
	
	public static final String PUBNAME_PROP = "PubSub-pubname";
	public static final String MSGTYPE_PROP = "PubSub-msgType";
	
	protected RoutingDecisionEngine decider;
	
	protected List<Tuple<Message, Connection>> outgoingMessages;
	
	public ContentPubSubRouter(Settings s)
	{
		super(s);
		init();
	}

	public ContentPubSubRouter(ActiveRouter r)
	{
		super(r);
		init();
	}

	@Override
	public MessageRouter replicate() {return new ContentPubSubRouter(this);}
	
	protected void init()
	{
		Settings routeSettings = new Settings(PUBSUB_NS);
		
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject(
				"routing.pubsub." + routeSettings.getSetting(ENGINE_SETTING));
	}
	
	@Override
	public void update()
	{
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		tryMessagesForConnected(outgoingMessages);
		
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(!this.hasMessage(t.getKey().getId()))
			{
				i.remove();
			}
		}
	}
	
}
