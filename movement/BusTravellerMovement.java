/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package movement;

import java.util.*;

import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.MapRoute;
import movement.map.SimMap;
import core.Coord;
import core.Settings;

/**
 * 
 * This class controls the movement of bus travellers. A bus traveller belongs 
 * to a bus control system. A bus traveller has a destination and a start 
 * location. If the direct path to the destination is longer than the path the 
 * node would have to walk if it would take the bus, the node uses the bus. If 
 * the destination is not provided, the node will pass a random number of stops
 * determined by Markov chains (defined in settings).
 * 
 * @author Frans Ekman
 *
 */
public class BusTravellerMovement extends MapBasedMovement implements 
	SwitchableMovement, TransportMovement {

	public static final String PROBABILITIES_STRING = "probs";
	public static final String PROBABILITY_TAKE_OTHER_BUS = "probTakeOtherBus";
	
	public static final int STATE_WAITING_FOR_BUS = 0;
	public static final int STATE_DECIDED_TO_ENTER_A_BUS = 1;
	public static final int STATE_TRAVELLING_ON_BUS = 2;
	public static final int STATE_WALKING_ELSEWHERE = 3;
	
	public static final int BUSSTOP_INCLUSION_DISTANCE = 500; //meters
	
	private int state;
	private Path nextPath;
	private Coord location;
	private Coord latestBusStop;
	private BusControlSystem controlSystem;
	private int id;
	private ContinueBusTripDecider cbtd;
	private double[] probabilities;
	private double probTakeOtherBus;
	private DijkstraPathFinder pathFinder;
	
	private Coord startBusStop;
	private Coord endBusStop;
	private Coord directionIndicatingStop;
	
	private boolean takeBus;
	private int currentBusID;
	
	private static int nextID = 0;
	
	/**
	 * Creates a BusTravellerModel 
	 * @param settings
	 */
	public BusTravellerMovement(Settings settings) {
		super(settings);
		int bcs = settings.getInt(BusControlSystem.BUS_CONTROL_SYSTEM_NR);
		controlSystem = BusControlSystem.getBusControlSystem(bcs);
		id = nextID++;
		controlSystem.registerTraveller(this);
		nextPath = new Path();
		state = STATE_WALKING_ELSEWHERE;
		if (settings.contains(PROBABILITIES_STRING)) {
			probabilities = settings.getCsvDoubles(PROBABILITIES_STRING);
		}
		if (settings.contains(PROBABILITY_TAKE_OTHER_BUS)) {
			probTakeOtherBus = settings.getDouble(PROBABILITY_TAKE_OTHER_BUS);
		}
		cbtd = new ContinueBusTripDecider(rng, probabilities);
		pathFinder = new DijkstraPathFinder(null);
		takeBus = true;
		currentBusID = -1;
	}
	
	/**
	 * Creates a BusTravellerModel from a prototype
	 * @param proto
	 */
	public BusTravellerMovement(BusTravellerMovement proto) {
		super(proto);
		state = proto.state;
		controlSystem = proto.controlSystem;
		if (proto.location != null) {
			location = proto.location.clone();
		}
		nextPath = proto.nextPath;
		id = nextID++;
		controlSystem.registerTraveller(this);
		probabilities = proto.probabilities;
		cbtd = new ContinueBusTripDecider(rng, probabilities);
		pathFinder = proto.pathFinder;
		this.probTakeOtherBus = proto.probTakeOtherBus;
		takeBus = true;
		currentBusID = -1;
	}
	
	@Override
	public Coord getInitialLocation() {
		
		MapNode[] mapNodes = (MapNode[])getMap().getNodes().
			toArray(new MapNode[0]);
		int index = rng.nextInt(mapNodes.length - 1);
		location = mapNodes[index].getLocation().clone();
		
		List<MapNode> allStops = controlSystem.getBusStops();
		Coord closestToNode = getClosestCoordinate(allStops, location.clone());
		latestBusStop = closestToNode.clone();
		
		return location.clone();
	}

	@Override
	public Path getPath() {
		if (!takeBus) {
			return null;
		}
		if (state == STATE_WAITING_FOR_BUS) {
			return null;
		} else if (state == STATE_DECIDED_TO_ENTER_A_BUS) {
			state = STATE_TRAVELLING_ON_BUS;
			List<Coord> coords = nextPath.getCoords();
			location = (coords.get(coords.size() - 1)).clone();
			return nextPath;
		} else if (state == STATE_WALKING_ELSEWHERE) {
			// Try to find back to the bus stop
			SimMap map = controlSystem.getMap();
			if (map == null) {
				return null;
			}
			MapNode thisNode = map.getNodeByCoord(location);
			MapNode destinationNode = map.getNodeByCoord(latestBusStop);
			List<MapNode> nodes = pathFinder.getShortestPath(thisNode, 
					destinationNode);
			Path path = new Path(generateSpeed());
			for (MapNode node : nodes) {
				path.addWaypoint(node.getLocation());
			}
			location = latestBusStop.clone();
			return path;
		}
			
		return null;
	}

	/**
	 * Switches state between getPath() calls
	 * @return Always 0 
	 */
	protected double generateWaitTime() {
		if (state == STATE_WALKING_ELSEWHERE) {
			if (latestBusStop == null) {
				System.err.println("Disabling bus traveler");
				return Double.POSITIVE_INFINITY;
			}
			if (location.equals(latestBusStop)) {
				state = STATE_WAITING_FOR_BUS;
			}
		}
		if (state == STATE_TRAVELLING_ON_BUS) {
			state = STATE_WAITING_FOR_BUS;
		}
		return 0;
	}
	
	@Override
	public MapBasedMovement replicate() {
		return new BusTravellerMovement(this);
	}

	public int getState() {
		return state;
	}
	
	/**
	 * Get the location where the bus is located when it has moved its path
	 * @return The end point of the last path returned
	 */
	public Coord getLocation() {
		if (location == null) {
			return null;
		}
		return location.clone();
	}
	
	/**
	 * Notifies the node at the bus stop that a bus is there. Nodes inside 
	 * busses are also notified.
	 * @param nextPath The next path the bus is going to take
	 */
	public void enterBus(int busID, Path nextPath) {
		
		if (startBusStop != null && endBusStop != null) {
			if (location.equals(endBusStop)) {
				state = STATE_WALKING_ELSEWHERE;
				latestBusStop = location.clone();
				currentBusID = -1;
			} 
			else if(currentBusID == busID)
			{
				state = STATE_DECIDED_TO_ENTER_A_BUS;
				this.nextPath = nextPath;
			}
			else if(currentBusID == -1)
			{
				List<Coord> mapnodes = nextPath.getCoords();
				Coord nextBusStop = mapnodes.get(mapnodes.size()-1);
				if(nextBusStop.equals(directionIndicatingStop))
				{
					state = STATE_DECIDED_TO_ENTER_A_BUS;
					currentBusID = busID;
					this.nextPath = nextPath;
				}
			}
			return;
		}
		
		if (!cbtd.continueTrip()) {
			state = STATE_WAITING_FOR_BUS;
			this.nextPath = null;
			/* It might decide not to start walking somewhere and wait 
			   for the next bus */
			if (rng.nextDouble() > probTakeOtherBus) {
				state = STATE_WALKING_ELSEWHERE;
				latestBusStop = location.clone();
			}
		} else {
			state = STATE_DECIDED_TO_ENTER_A_BUS;
			this.nextPath = nextPath;
		}
	}
	
	public int getID() {
		return id;
	}
	
	
	/**
	 * Small class to help nodes decide if they should continue the bus trip. 
	 * Keeps the state of nodes, i.e. how many stops they have passed so far. 
	 * Markov chain probabilities for the decisions. 
	 * 
	 * NOT USED BY THE WORKING DAY MOVEMENT MODEL
	 * 
	 * @author Frans Ekman
	 */
	class ContinueBusTripDecider {
		
		private double[] probabilities; // Probability to travel with bus
		private int state;
		private Random rng;
		
		public ContinueBusTripDecider(Random rng, double[] probabilities) {
			this.rng = rng;
			this.probabilities = probabilities;
			state = 0;
		}
		
		/**
		 * 
		 * @return true if node should continue
		 */
		public boolean continueTrip() {
			double rand = rng.nextDouble();
			if (rand < probabilities[state]) {
				incState();
				return true;
			} else {
				resetState();
				return false;
			}
		}
		
		/**
		 * Call when a stop has been passed
		 */
		private void incState() {
			if (state < probabilities.length  - 1) {
				state++;
			}
		}
		
		/**
		 * Call when node has finished it's trip
		 */
		private void resetState() {
			state = 0;
		}	
	}

	/**
	 * Help method to find the closest coordinate from a list of coordinates,
	 * to a specific location
	 * @param allCoords list of coordinates to compare
	 * @param coord destination node
	 * @return closest to the destination
	 */
	private static Coord getClosestCoordinate(List<MapNode> allCoords, 
			Coord coord) {
		Coord closestCoord = null;
		double minDistance = Double.POSITIVE_INFINITY;
		for (MapNode temp : allCoords) {
			double distance = temp.getLocation().distance(coord);
			if (distance < minDistance) {
				minDistance = distance;
				closestCoord = temp.getLocation();
			}
		}
		return closestCoord.clone();
	}
	
	private static Set<MapNode> getClosestCoordinates(List<MapNode> allNodes, 
			Coord location)
	{
		Set<MapNode> closest = new HashSet<MapNode>(8);
		double minDistance = Double.POSITIVE_INFINITY;
		MapNode min = null;
		for(MapNode temp: allNodes)
		{
			Coord loc = temp.getLocation();
			double dist = loc.distance(location);
			if(dist < minDistance)
			{
				for(Iterator<MapNode> i = closest.iterator(); i.hasNext();)
					if(i.next().getLocation().distance(loc) > BUSSTOP_INCLUSION_DISTANCE )
						i.remove();
				closest.add(temp);
				minDistance = dist;
				min = temp;
			}
			else if(min != null && min.getLocation().distance(loc) < BUSSTOP_INCLUSION_DISTANCE)
				closest.add(temp);
		}
		return closest;
	}
	
	private void findShortestBusRoute(List<MapNode> allNodes, Set<MapNode> starts,
			Set<MapNode> stops)
	{
		int searchStart,   
			indexOfStart, count, endIndex, finalStartIndex = 0, minCount = Integer.MAX_VALUE;
		
		// First find the first index of the starting node
		for(indexOfStart = 0; indexOfStart < allNodes.size(); indexOfStart++)
		{
			if(starts.contains(allNodes.get(indexOfStart))) break;
		}
		//System.err.println("Starting search: " + indexOfStart);
		searchStart = indexOfStart;
		do
		{
			/*
			 * Loop in a circular fashion through the list looking for index of a 
			 * stop node, keeping track of how many stops we've gone
			 */
		
			for(endIndex = (indexOfStart+1) % allNodes.size(), count = 1; ; 
						endIndex = (endIndex+1) % allNodes.size(), count++)
			{
				MapNode possibleEnd = allNodes.get(endIndex);
				if(starts.contains(possibleEnd))
				{
					if(endIndex == searchStart)
						break;
					
					indexOfStart = endIndex;
					count = 0;
				}
				if(stops.contains(possibleEnd))
				{
					break;
				}
			}
			
			// We get here either when we found a stop node or the search is over
			// If the search is over, count is meaningless but could be less than
			// minCount
			//System.err.println("Might be done: " + endIndex);
			if(endIndex != searchStart && count < minCount)
			{
				finalStartIndex = indexOfStart;
				minCount = count;
				this.startBusStop = allNodes.get(finalStartIndex).getLocation();
				this.directionIndicatingStop = allNodes.get((finalStartIndex+1)%allNodes.size()).getLocation();
				this.endBusStop = allNodes.get(endIndex).getLocation();
				//System.err.println("Finishing search: " + this.startBusStop);
			}
			
			for(indexOfStart = endIndex; ; indexOfStart = (indexOfStart+1) % allNodes.size()) {
				if(starts.contains(allNodes.get(indexOfStart))) break; 
			}
		}
		while(indexOfStart != searchStart);
		
		
	}
	
	private void findShortestPingPongBusRoute(List<MapNode> allNodes, Set<MapNode> starts,
			Set<MapNode> stops)
	{
		int searchStart,   
			indexOfStart, count, endIndex, minCount = Integer.MAX_VALUE,
			indexLimit = allNodes.size() * 2 - 1;
		
		// First find the first index of the starting node
		for(indexOfStart = 0; indexOfStart < allNodes.size(); indexOfStart++)
		{
			if(starts.contains(allNodes.get(indexOfStart))) break;
		}
		
		searchStart = indexOfStart;
		do
		{
			/*
			 * Loop in a circular fashion through the list looking for index of a 
			 * stop node, keeping track of how many stops we've gone
			 */
			int i;
			for(i = endIndex = (indexOfStart+1) % allNodes.size(), count = 1; ; 
						i = (i+1) % indexLimit, 
						endIndex = i >= allNodes.size() ? indexLimit - i : i, 
						count++)
			{
				MapNode n = allNodes.get(endIndex);
				if(starts.contains(n))
				{
					if(endIndex == searchStart)
						break;
					indexOfStart = endIndex;
					count = 0;
				}
				if(stops.contains(n))
				{
					break;
				}
			}
			
			// We get here either when we found a stop node or the search is over
			// If the search is over, count is meaningless but could be less than
			// minCount
			
			if(endIndex != searchStart && count < minCount)
			{
				minCount = count;
				this.startBusStop = allNodes.get(indexOfStart).getLocation();
				
				if(indexOfStart == allNodes.size() - 1) indexOfStart--;
				else indexOfStart++;
				this.directionIndicatingStop = allNodes.get(indexOfStart).getLocation();
				
				this.endBusStop = allNodes.get(endIndex).getLocation();
			}
			
			for(i = indexOfStart = endIndex; ; 
					i = (i+1) % indexLimit, 
					indexOfStart = i >= allNodes.size() ? indexLimit - i : i) {
				if(starts.contains(allNodes.get(indexOfStart))) break; 
			}
		}
		while(indexOfStart != searchStart);
		
	}
	
	/**
	 * Sets the next route for the traveller, so that it can decide whether it 
	 * should take the bus or not. 
	 * @param nodeLocation
	 * @param nodeDestination
	 */
	public void setNextRoute(Coord nodeLocation, Coord nodeDestination) {
			
		// Find closest stops to current location and destination
		List<MapNode> allStops = controlSystem.getBusStops();
		int routeType = controlSystem.getRouteType();
		
		Set<MapNode> closestToNode = getClosestCoordinates(allStops, nodeLocation);
		Set<MapNode> closestToDestination = getClosestCoordinates(allStops, nodeDestination);
		
		if(routeType == MapRoute.CIRCULAR)
			findShortestBusRoute(allStops, closestToNode, closestToDestination);
		else
			findShortestPingPongBusRoute(allStops, closestToNode, closestToDestination);

		if (this.startBusStop == null) {
			takeBus = false;
			System.err.println("Error: Unable to find bus route: " + id);
			return;
		}

		// Check if it is shorter to walk than take the bus 
		double directDistance = nodeLocation.distance(nodeDestination);
		double busDistance = nodeLocation.distance(this.startBusStop) + 
			nodeDestination.distance(this.endBusStop);

		if (directDistance < busDistance) {
			takeBus = false;
		} else {
			takeBus = true;
		}
		
		this.latestBusStop = startBusStop.clone();
	}
	
	/**
	 * @see SwitchableMovement
	 */
	public Coord getLastLocation() {
		return location.clone();
	}

	/**
	 * @see SwitchableMovement
	 */
	public void setLocation(Coord lastWaypoint) {
		location = lastWaypoint.clone();
	}

	/**
	 * @see SwitchableMovement
	 */
	public boolean isReady() {
		if (state == STATE_WALKING_ELSEWHERE) {
			return true;
		} else {
			return false;
		}
	}
	
	public static void reset() {
		nextID = 0;
	}
	
}
