/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package movement;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import core.Coord;
import core.DTNSim;
import core.SimError;

/**
 * This class controls the group mobility of the people meeting their friends in
 * the evening
 * 
 * @author Frans Ekman
 */
public class EveningActivityControlSystem {

	private HashMap<Integer, EveningActivityMovement> eveningActivityNodes;
	private List<Coord> meetingSpots;
	private EveningTrip[] nextTrips;
	
	private Random rng;
	
	private static HashMap<Integer, EveningActivityControlSystem> 
		controlSystems;
	
	static {
		DTNSim.registerForReset(EveningActivityControlSystem.class.
				getCanonicalName());
		reset();
	}
	
	/**
	 * Creates a new instance of EveningActivityControlSystem without any nodes
	 * or meeting spots, with the ID given as parameter
	 * @param id
	 */
	private EveningActivityControlSystem(int id) {
		eveningActivityNodes = new HashMap<Integer, EveningActivityMovement>();
	}

	public static void reset() {
		controlSystems = new HashMap<Integer, EveningActivityControlSystem>();
	}
	
	/**
	 * Register a evening activity node with the system
	 * @param eveningMovement activity movement
	 */
	public void addEveningActivityNode(EveningActivityMovement eveningMovement) {
		eveningActivityNodes.put(new Integer(eveningMovement.getID()), 
				eveningMovement);
//		if(eveningMovement.getID() != 0)
			this.possibleTripGoers++;
//		else
//			System.out.println("Prototype added to Control System");
	}
	
	/**
	 * Sets the meeting locations the nodes can choose among
	 * @param meetingSpots
	 */
	public void setMeetingSpots(List<Coord> meetingSpots) {
		this.meetingSpots = meetingSpots;
		this.nextTrips = new EveningTrip[meetingSpots.size()];
	}
	
	/**
	 * This method gets the instruction for a node, i.e. When/where and with 
	 * whom to go.  
	 * @param eveningActivityNodeID unique ID of the node
	 * @return Instructions object
	 */
	private int[] index = null;
	private int slotcount = 0; // Slots allocated to trips
	private int nodesAlreadyDecided = 0; // count of nodes in some slot or skipping
	private int possibleTripGoers = 0;
	
	/*
	 * We define an epoch as the period in time in which all the nodes of this
	 * control system decide to go shopping or not once. 
	 * 
	 * possibleTripGoers is a count of the number of nodes going shopping plus 
	 * those that haven't decided yet. We optimistically assume they'll all go 
	 * shopping, and, as nodes decides otherwise, this value gets decremented.
	 * 
	 * nodesAlreadyDecided is a count of how many nodes have already decided. Once
	 * this value reaches the total number of nodes for this control system, we
	 * start a new epoch.
	 * 
	 * slotcount sums the trip sizes as each trip is created. If trips have space
	 * that can't be filled, the nodes in them will wait indefinitely, which is 
	 * bad. So we prevent allocating more slots than could possibly be filled by
	 * ensuring slotcount < possibleTripGoers. 
	 */
	
	public EveningTrip getEveningInstructions(int eveningActivityNodeID) {
		EveningActivityMovement eveningMovement = eveningActivityNodes.get(
				new Integer(eveningActivityNodeID));
		if (eveningMovement != null) {
			
			if(this.index == null)
			{
//				this.possibleTripGoers = eveningActivityNodes.size() - 1;
				int avgNumTrips = Math.round(eveningActivityNodes.size() / 
						((eveningMovement.getMaxGroupSize() + 
					eveningMovement.getMinGroupSize()) / 2));
				if(avgNumTrips == 0) avgNumTrips++;
				this.index = new int[avgNumTrips];
				for(int i = 0; i < avgNumTrips; i++)
					index[i] = rng.nextInt(meetingSpots.size());
			}
			
			//int index = eveningActivityNodeID % meetingSpots.size();
			int i = eveningActivityNodeID % this.index.length;
			
			if (nextTrips[index[i]] == null && slotcount < this.possibleTripGoers) {
				
				int nrOfEveningMovementNodes = (int)(eveningMovement.
						getMinGroupSize() + 
						(double)(eveningMovement.getMaxGroupSize() - 
								eveningMovement.getMinGroupSize()) * 
								rng.nextDouble());
				
				if(slotcount + nrOfEveningMovementNodes > this.possibleTripGoers) //account for prototype model
					nrOfEveningMovementNodes = this.possibleTripGoers - slotcount;
				
				/*System.out.println("Creating Trip of size: " + nrOfEveningMovementNodes +
						" total nodes: " + (eveningActivityNodes.size()) + 
						" loc: " + meetingSpots.get(index[i]));*/
				
				slotcount += nrOfEveningMovementNodes;
				Coord loc = meetingSpots.get(index[i]).clone();
				nextTrips[index[i]] = new EveningTrip(nrOfEveningMovementNodes, loc);
			}
			else if(slotcount >= this.possibleTripGoers)
			{
				boolean cantFindTrip = true;
				for(int j = 0; j < index.length; j++)
					if(nextTrips[index[j]] != null) {i = j; cantFindTrip = false;}
				if(cantFindTrip)
					throw new SimError("No EveningTrip in which to include a shopper. " +
							"This is a logic error with EveningActivityControlSystem");
					//System.err.println("Crap, no trips to assign mode to, id: " + eveningActivityNodeID);
			}
			
			/*System.out.println("Node: " + eveningActivityNodeID + " joining trip to: " +
					meetingSpots.get(index[i])+
					" reported: " + (nodesAlreadyDecided+1) +
					" possible: " + this.possibleTripGoers +
					" slots: " + this.slotcount);*/
			
			nextTrips[index[i]].addNode(eveningMovement);
			
			EveningTrip toReturn = nextTrips[index[i]];
			if (nextTrips[index[i]].isFull())
				nextTrips[index[i]] = null;
			
			nodesAlreadyDecided++;
			this.checkForEndOfEveningEpochAndReset();
			return toReturn;
			
		}
		return null;
	}
	
	/**
	 * 
	 */
	public void skipEveningActivity(int eveningActivityNodeID)
	{
		EveningActivityMovement eveningMovement = eveningActivityNodes.get(
				new Integer(eveningActivityNodeID));
		
		this.possibleTripGoers--;
		EveningTrip tonotify = null;
		int i = 0;
		
		if(eveningMovement != null)
		{
			if(index != null)
			{
				i = eveningActivityNodeID % this.index.length;;
				if(nextTrips[index[i]] == null)
				{
					/*System.out.println("Node: " + eveningActivityNodeID + " skipping someone else's trip,"+
							" reported: " + (nodesAlreadyDecided+1) + " possible: " + this.possibleTripGoers+
							" slots: " + this.slotcount);*/
					for(int j = 0; j < index.length; j++)
						if(nextTrips[index[j]] != null)
						{
							tonotify = nextTrips[index[j]];
							i = j;
							break;
						}
				}
				else
				{
					/*System.out.println("Node: " + eveningActivityNodeID + " skipping trip,"+
							" reported: " + (nodesAlreadyDecided+1) + " possible: " + this.possibleTripGoers+
							" slots: " + this.slotcount);*/
					tonotify = nextTrips[index[i]];
				}
			}
			/*else
				System.out.println("Node: " + eveningActivityNodeID + " skipping with no trips yet,"+
						" reported: " + (nodesAlreadyDecided+1) + " possible: " + this.possibleTripGoers+
						" slots: " + this.slotcount);*/
		}
		
		if(tonotify != null && slotcount > this.possibleTripGoers)
		{
//			System.out.println("Reducing Trip size for trip to: " + tonotify.getLocation());
			tonotify.nodeIsSkippingTrip(); // reduces trip size
			slotcount--;
			if(tonotify.isFull()) nextTrips[index[i]] = null;
		}
		
		this.nodesAlreadyDecided++;
		this.checkForEndOfEveningEpochAndReset();
	}
	
	private void checkForEndOfEveningEpochAndReset()
	{
		if(this.nodesAlreadyDecided >= this.eveningActivityNodes.size())
		{
			/*System.out.println("EACS: Epoch over: " + slotcount + " possible: " + this.possibleTripGoers
					+ ". Resetting");*/
			for(EveningTrip e : nextTrips)
				if(e != null)
					throw new SimError("An Evening Trip is likely waiting for another " +
							"participant that won't come");
//						System.err.println("Trip not null at end of epoch: " + e.getLocation());
			index = null;
			slotcount = 0;
			this.nodesAlreadyDecided = 0;
			possibleTripGoers = eveningActivityNodes.size();
		}
	}
	
	/**
	 * Get the meeting spot for the node
	 * @param id
	 * @return Coordinates of the spot
	 */
	public Coord getMeetingSpotForID(int id) {
		int index = id % meetingSpots.size();
		Coord loc = meetingSpots.get(index).clone();
		return loc;
	}
	
	
	/**
	 * Sets the random number generator to be used 
	 * @param rand
	 */
	public void setRandomNumberGenerator(Random rand) {
		this.rng = rand;
	}
	
	/**
	 * Returns a reference to a EveningActivityControlSystem with ID provided as
	 * parameter. If a system does not already exist with the requested ID, a 
	 * new one is created. 
	 * @param id unique ID of the EveningActivityControlSystem
	 * @return The EveningActivityControlSystem with the provided ID
	 */
	public static EveningActivityControlSystem getEveningActivityControlSystem(
			int id) {
		if (controlSystems.containsKey(new Integer(id))) {
			return controlSystems.get(new Integer(id));
		} else {
			EveningActivityControlSystem scs = 
				new EveningActivityControlSystem(id);
			controlSystems.put(new Integer(id), scs);
			return scs;
		}
	}
	
}
