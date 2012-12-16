/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package movement;

import java.util.LinkedList;
import java.util.Queue;

import core.Settings;
import core.SettingsError;
import core.SimClock;

/**
 * Object of this class tell the movement models when a node belonging
 * to a certain group is active and when not.
 */
public class ActivenessHandler {
	/** 
	 * Active times -setting id ({@value}).<BR>
	 * Syntax: <CODE>start, end</CODE><BR>
	 * Multiple times can be concatenated by repeating the sequence. Time 
	 * limits should be in order and should not overlap. 
	 */
	public static final String ACTIVE_TIMES_S = "activeTimes";
	
	private Queue<TimeRange> activeTimes;
	private TimeRange curRange = null;
	
	public ActivenessHandler(Settings s) {
		this.activeTimes = parseActiveTimes(s);

		if (activeTimes != null) {
			this.curRange = activeTimes.poll();
		}			
	}
	
	private Queue<TimeRange> parseActiveTimes(Settings s) {
		double [] times;
		String sName = s.getFullPropertyName(ACTIVE_TIMES_S);
		
		if (s.contains(ACTIVE_TIMES_S)) {
			times = s.getCsvDoubles(ACTIVE_TIMES_S);
			if (times.length % 2 != 0) {
				throw new SettingsError("Invalid amount of values (" + 
						times.length + ") for setting " + sName + ". Must " + 
						"be divisable by 2");
			}
		}
		else {
			return null; // no setting -> always active
		}

		Queue<TimeRange> timesList = new LinkedList<TimeRange>(); 
		
		for (int i=0; i<times.length; i+= 2) {
			double start = times[i];
			double end = times[i+1];
			
			if (start > end) {
				throw new SettingsError("Start time (" + start + ") is " + 
						" bigger than end time (" + end + ") in setting " + 
						sName);
			}
			
			timesList.add(new TimeRange(start, end));
		}
		
		return timesList;
	}
	
	/**
	 * Returns true if node should be active at the moment
	 * @return true if node should be active at the moment
	 */
	public boolean isActive() {
		if (this.activeTimes == null) {
			return true; // no inactive times 
		}
		
		if (curRange == null) {
			return false; // out of active times
		}
		
		double time = SimClock.getTime();
		
		if (this.curRange.isOut(time)) { // time for the next time range
			this.curRange = activeTimes.poll();
			if (curRange == null) {
				return false; // out of active times
			}
		}
		
		return curRange.isInRange(time);
	}

	/**
	 * Class for handling time ranges
	 */
	private class TimeRange {
		private double start;
		private double end;
		
		/**
		 * Constructor.
		 * @param start The start time
		 * @param end The end time
		 */
		public TimeRange(double start, double end) {
			this.start = start;
			this.end = end;
		}
		
		/**
		 * Returns true if the given time is within start and end time 
		 * (inclusive).
		 * @param time The time to check
		 * @return true if the time is within limits
		 */
		public boolean isInRange(double time) {
			if (time < start || time > end ) {
				return false; // out of range
			}
			return true;			
		}
		
		/**
		 * Returns true if given time is bigger than end the end time
		 * @param time The time to check
		 * @return true if given time is bigger than end 
		 */
		public boolean isOut(double time) {
			return time > end;
		}
	}
}
