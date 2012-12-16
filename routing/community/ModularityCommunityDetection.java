package routing.community;

import java.util.*;

//import routing.communitydetection.DiBuBB.Duration;

import core.*;

public class ModularityCommunityDetection implements CommunityDetection
{
	//public static final String LAMBDA_SETTING = "lambda";
	public static final String GAMMA_SETTING = "gamma";
	public static final String FAMILIAR_SETTING = "familiarThreshold";
	
	protected Set<DTNHost> familiarSet;
	protected Set<DTNHost> localCommunity;
	protected Map<DTNHost, Set<DTNHost>> familiarsOfMyCommunity;
	
	protected double k;
	//protected double gamma;
	protected double familiarThreshold;
	
	public ModularityCommunityDetection(Settings s)
	{
		//this.lambda = s.getDouble(LAMBDA_SETTING);
		//this.gamma = s.getDouble(GAMMA_SETTING);
		this.familiarThreshold = s.getDouble(FAMILIAR_SETTING);
	}
	
	public ModularityCommunityDetection(ModularityCommunityDetection proto)
	{
		//this.lambda = proto.lambda;
//		this.gamma = proto.gamma;
		this.familiarThreshold = proto.familiarThreshold;
		familiarSet = new HashSet<DTNHost>();
		localCommunity = new HashSet<DTNHost>();
		this.familiarsOfMyCommunity = new HashMap<DTNHost, Set<DTNHost>>();
	}
	
	public void newConnection(DTNHost myHost, DTNHost peer, CommunityDetection peerCD)
	{
//		boolean addPeerToMyLocal=false, addMeToPeerLocal=false;
		ModularityCommunityDetection scd = (ModularityCommunityDetection)peerCD;
		
		this.localCommunity.add(myHost);
		scd.localCommunity.add(peer);
		
		// Update local approximation of the familiar sets of my community from peer info
		for(Map.Entry<DTNHost, Set<DTNHost>> entry : this.familiarsOfMyCommunity.entrySet())
		{
			DTNHost hostInMyCommunity = entry.getKey();
			Set<DTNHost> approxFamiliarSet = entry.getValue();
			if(scd.familiarsOfMyCommunity.containsKey(hostInMyCommunity))
				approxFamiliarSet.addAll(scd.familiarsOfMyCommunity.get(hostInMyCommunity));
		}
		
		// Add peer to my local community if needed
		if(!this.localCommunity.contains(peer))
		{
			/*
			 * 
			 */
			int count=0;
			for(DTNHost h : scd.familiarSet)
				if(this.localCommunity.contains(h))
					count++;
			//if(count > 0)
				//System.out.println(myHost.toString() + " count: " + count + " peerSize: " + peerFsize);
			if(count >= this.k - 1)
			{
				//System.out.println(myHost.toString() + " adding " + peer + " to Local Community");
				this.localCommunity.add(peer);
				
				for(DTNHost h : scd.localCommunity)
				{
					count = 0;
					for(DTNHost i : scd.familiarsOfMyCommunity.get(h))
						if(this.localCommunity.contains(i))
							count++;
					if(count >= this.k - 1)
					{
						this.localCommunity.add(h);
						this.familiarsOfMyCommunity.put(h, scd.familiarsOfMyCommunity.get(h));
					}
				}
			}
		}
		
		if(!scd.localCommunity.contains(myHost))
		{
			int count = 0;
			for(DTNHost h : this.familiarSet)
				if(scd.localCommunity.contains(h))
					count++;
			if(count >= scd.k - 1)
			{
				//System.out.println(peer.toString() + " adding " + myHost + " to Local Community");
				scd.localCommunity.add(myHost);
				
				for(DTNHost h : this.localCommunity)
				{
					count = 0;
					for(DTNHost i : this.familiarsOfMyCommunity.get(h))
						if(scd.localCommunity.contains(i))
							count++;
					if(count >= scd.k - 1)
					{
						scd.localCommunity.add(h);
						scd.familiarsOfMyCommunity.put(h, this.familiarsOfMyCommunity.get(h));
					}
				}
			}
		}
	}
	
	public void connectionLost(DTNHost myHost, DTNHost peer, 
			CommunityDetection peerCD, List<Duration> history)
	{
		if(this.familiarSet.contains(peer)) return;
		
		Iterator<Duration> i = history.iterator();
		double time = 0;
		while(i.hasNext())
		{
			Duration d = i.next();
			time += d.end - d.start;
		}
		
		if(time > this.familiarThreshold)
		{
			System.out.println(myHost + " adding " + peer + " as Familiar");
			this.familiarSet.add(peer);
			this.localCommunity.add(peer);
		}
	}

	public boolean isHostInCommunity(DTNHost h)
	{
		return this.localCommunity.contains(h);
	}

	public CommunityDetection replicate()
	{
		return new ModularityCommunityDetection(this);
	}

	public Set<DTNHost> getLocalCommunity()
	{
		return this.localCommunity;
	}

	public Set<DTNHost> getFamiliarSet()
	{
		return this.familiarSet;
	}
	
}
