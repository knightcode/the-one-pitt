/*
 * @(#)FixedMovement.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package movement;

import input.WKTReader;

import java.io.File;
import java.io.IOException;
import java.util.*;

import movement.map.SimMap;

import core.*;

/**
 * A simple stationary movement model where the node positions are specified in 
 * a WKT file. The nodes can be assigned positions in the order they appear in 
 * the file or select their position randomly from the list. Also, no two nodes 
 * take the same position. If the file contains too few locations, a 
 * SettingsError is thrown since it's expected two nodes of the same group 
 * wouldn't need to rest on top of each other for the whole simulation.
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public class FixedMovement extends MapBasedMovement
{
	/** Path to a file containing locations -setting id {@value} */
	public static final String LOCATIONS_FILE_SETTING = "locationsFile";
	/** Number of locations files -setting id {@value} */
	public static final String FILE_COUNT_SETTING = "nrOfLocationFiles";
	/** Boolean indicating whether to place nodes according to the order the
	 * locations appear in the file (false) or selecting locations at random
	 * (true) -setting id {@value} */
	public static final String ORDER_SETTING = "randomPlacement";
	
	protected static List<Coord> locations = null;
	protected static int locIndex = 0;
	protected boolean randomPlacement;
	
	private Coord location;
	
	public FixedMovement(Settings settings)
	{
		super(settings);
		
		String[] locationsFiles = null;
		if(settings.contains(ORDER_SETTING))
			this.randomPlacement = settings.getBoolean(ORDER_SETTING);
		else
			this.randomPlacement = false;
		
		if(settings.contains(LOCATIONS_FILE_SETTING))
		{
			locationsFiles = new String[] 
			                       {settings.getSetting(LOCATIONS_FILE_SETTING)};
		}
		else if(settings.contains(FILE_COUNT_SETTING))
		{
			locationsFiles = new String[settings.getInt(FILE_COUNT_SETTING)];
			for(int i = 0; i < locationsFiles.length ; i++)
				locationsFiles[i] = settings.getSetting(LOCATIONS_FILE_SETTING + (i+1));
			
		}
		
		if(locationsFiles != null)
		{
			locations = new ArrayList<Coord>(16);
			for(int i = 0 ; i < locationsFiles.length; i++)
			{
				try {
					File f = new File(locationsFiles[i]);
					if(f.exists())
					{
						List<Coord> coordsRead = 
							(new WKTReader()).readPoints(f);
						
						for (Coord coord : coordsRead) 
						{
							SimMap map = getMap();
							Coord offset = map.getOffset();
							// mirror points if map data is mirrored
							if (map.isMirrored()) { 
								coord.setLocation(coord.getX(), -coord.getY()); 
							}
							coord.translate(offset.getX(), offset.getY());
							locations.add(coord);
						}
					}
					else
						throw new SettingsError("FixedMovement: locations file not found: " 
								+ locationsFiles[i]);
				}
				catch(IOException ioe){ioe.printStackTrace();}
			}
		}
		
		/*
		 * Being just the prototype object, we don't need to set this objects
		 * location
		 */
	}

	public FixedMovement(FixedMovement proto)
	{
		super(proto);
		this.randomPlacement = proto.randomPlacement;
		
		if(locations != null)
		{
			if(randomPlacement)
			{
				if(locations.size() == 0)
					throw new SettingsError(
							"FixedMovement: Out of locations to place nodes");
				locIndex = rng.nextInt(locations.size());
				this.location = locations.get(locIndex);
				locations.remove(locIndex);
			}
			else
			{
				this.location = locations.get(locIndex);
				locIndex = (locIndex + 1) % locations.size();
			}
		}
		else
		{
			this.location = new Coord(rng.nextInt(getMaxX()), rng.nextInt(getMaxY()));
		}
	}

	@Override
	public Path getPath()
	{
		Path p = new Path(0);
		p.addWaypoint(location);
		return p;
	}

	@Override
	public Coord getInitialLocation()
	{
		return location;
	}

	@Override
	public double nextPathAvailable()
	{
		return Double.MAX_VALUE;	// no new paths available
	}
	
	@Override
	public MapBasedMovement replicate()
	{
		return new FixedMovement(this);
	}

}
