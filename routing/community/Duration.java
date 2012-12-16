package routing.community;

/**
 * A helper class for the community package that stores a start and end value
 * for some abstract duration. Generally, in this package, the duration being
 * stored is a time duration.
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class Duration
{
	/** The start value */
	double start;
	
	/** The end value */
	double end;
	
	/**
	 * Standard constructor that assigns s to start and e to end.
	 * 
	 * @param s Initial start value
	 * @param e Initial end value
	 */
	public Duration(double s, double e) {start = s; end = e;}
}
