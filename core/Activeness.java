/*
 * @(#)Activeness.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package core;

/**
 * Abstracts the concept of a node being active through out the course of a 
 * simulation. Implementing classes can implement various alogithms or 
 * selections from various distributions to model node (or some other entity's)
 * activeness.
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public interface Activeness
{
	/**
	 * Returns true if the associated entity is active at the current moment in
	 * simulation time. 
	 * 
	 * @return true if active, false otherwise.
	 */
	public boolean isActive();
	
	/**
	 * Creates a duplicate copy of the current Activeness object.
	 * 
	 * @return Copy of this Activeness object
	 */
	public Activeness replicate();
}
