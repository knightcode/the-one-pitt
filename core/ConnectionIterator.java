package core;

import java.util.*;

public class ConnectionIterator implements Iterator<Connection>
{
	protected List<NetworkInterface> net;
	protected List<Connection> conList;
	protected int connectionCount;
	protected int interfaceIndex;
	protected int connectionIndex;
	protected int iterationCount;
	
	ConnectionIterator(DTNHost h)
	{
		net = h.getInterfaces();
		
		this.connectionCount = 0;
		for(NetworkInterface i : net)
			connectionCount += i.connectionCount();
		
		interfaceIndex = connectionIndex = iterationCount = 0;
		
		conList = net.get(interfaceIndex).getConnections();
	}
	
	public boolean hasNext()
	{
		return iterationCount < connectionCount;
	}

	public Connection next()
	{
		// if connection list exhausted, move to next interface
		while(connectionIndex >= conList.size())
		{
			interfaceIndex++;
			if(interfaceIndex >= net.size()) throw new NoSuchElementException();
			
			conList = net.get(interfaceIndex).getConnections();
			connectionIndex = 0;
		}
		
		Connection c = conList.get(connectionIndex++);
		iterationCount++;
		return c;
	}

	public void remove()
	{
			throw new UnsupportedOperationException();
	}
}
