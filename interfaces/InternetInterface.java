package interfaces;

import java.util.*;

import core.CBRConnection;
import core.Connection;
import core.NetworkInterface;
import core.Settings;

public class InternetInterface extends NetworkInterface
{

	protected static Collection<NetworkInterface> connectedHosts;
	protected boolean connectionsMade;
	
	public InternetInterface(Settings s)
	{
		super(s);
		connectedHosts = new HashSet<NetworkInterface>();
		connectionsMade = false;
	}

	public InternetInterface(InternetInterface ni)
	{
		super(ni);
		connectedHosts.add(this);
		connectionsMade = ni.connectionsMade;
	}

	@Override
	public NetworkInterface replicate()
	{
		return new InternetInterface(this);
	}

	@Override
	public void connect(NetworkInterface anotherInterface)
	{
		if(this != anotherInterface)
		{
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con,anotherInterface);
		}

	}

	@Override
	public void update()
	{
		if(!connectionsMade)
		{
			for(Iterator<NetworkInterface> i = connectedHosts.iterator(); i.hasNext();)
			{
				NetworkInterface ni = i.next();
				createConnection(ni);
			}
			connectionsMade = true;
		}

	}

	@Override
	public void createConnection(NetworkInterface anotherInterface)
	{
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {    			
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con,anotherInterface);
		}
	}

}
