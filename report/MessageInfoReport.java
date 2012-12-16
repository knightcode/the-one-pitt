package report;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import core.*;
import core.Message;
import core.MessageListener;

public class MessageInfoReport extends Report implements MessageListener
{
	private Map<String, Double> creation;
	private Map<String, Double> delays;
//	private Map<String, Integer> hopCounts;
//	private Map<String, Double> dropped;
	
	public MessageInfoReport()
	{
		init();
	}
	
	@Override
	protected void init()
	{
		super.init();
		this.creation = new HashMap<String, Double>();
		this.delays = new HashMap<String, Double>();
		//this.hopCounts = new HashMap<String, Integer>();
		
		write("Scenario: " + getScenarioName());
		write("ID CreateTime Delay HopCount");
	}

	public void newMessage(Message m)
	{
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}
		
		this.creation.put(m.getId(), getSimTime());
	}


	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean firstDelivery)
	{
		if (isWarmupID(m.getId())) {return;}
		
		if(firstDelivery)
		{
			String id = m.getId();
			double createtime = creation.get(id),
				delay = getSimTime() - createtime;
			//this.hopCounts.put(id, m.getHops().size() - 1);
			this.delays.put(id, delay );
			
			write(id + ' ' + createtime + ' ' + delay + ' ' + 
					(m.getHops().size() - 1));
		}

	}
	
	public void messageDeleted(Message m, DTNHost where, boolean dropped){}

	public void messageTransferStarted(Message m, DTNHost from, DTNHost to){}
	
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to){}

	@Override
	public void done()
	{
		Set<String> undelivered = new HashSet<String>(creation.keySet());
		undelivered.removeAll(delays.keySet());
		
		for(String id : undelivered)
		{
			write(id + ' ' + creation.get(id) + ' ' + Double.POSITIVE_INFINITY + " -1");
		}
		super.done();
	}

	
}
