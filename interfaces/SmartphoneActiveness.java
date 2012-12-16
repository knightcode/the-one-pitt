/*
 * @(#)SmartphoneActiveness.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package interfaces;

import java.util.*;

import org.uncommons.maths.random.*;
import core.*;

/**
 * <p>An activeness implementation that conforms to the finding presented in:
 * </p>
 * 
 * <p>Diversity in Smartphone Usage</p>
 * 
 * <p>By: Falaki, Hossein and Mahajan, Ratul and Kandula, Srikanth and 
 *    Lymberopoulos, Dimitrios and Govindan, Ramesh and Estrin, Deborah</p>
 * 
 * <p>http://doi.acm.org/10.1145/1814433.1814453</p>
 * 
 * <p>The work presents an activeness model where the duration of active 
 * sessions is a composition of an exponential and pareto distribution. The 
 * off-time durations conform to a Weibull distribution. They further find that 
 * usage varies greatly from user to user but demonstrate that this can be 
 * modelled by varying the parameters of the exponential, pareto, and weibull 
 * distribution functions. Acceptable ranges for each of these parameters are 
 * also presented.<p>
 * 
 * <p>By default this class will select these parameters from their acceptable 
 * ranges at random so that the hosts in the simulation vary in their usage
 * habits across the range of habits found in the study. Alternatively, the
 * populationUniformity setting can make the population conform to the same
 * distribution parameters.</p>
 * 
 * <p>A main method is included within this class that can be used to test a 
 * set of parameters.</p>
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class SmartphoneActiveness implements Activeness
{
	/** Minimum value of Pareto Xm parameter, default 60s -setting id {@value} */
	public static final String PARETO_XM_SETTING = "paretoXm";
	/** Alpha parameter of the Pareto Distribution -setting id {@value} */
	public static final String PARETO_ALPHA_SETTING = "paretoAlpha";
	/** Lambda parameter for the Exponential distribution -setting id {@value} */
	public static final String EXP_LAMBDA_SETTING = "expLambda";
	/** Probability of using Exp dist over Pareto -setting id {@value} */
	public static final String R_VALUE_SETTING = "R";
	/** Weibull scale parameter -setting id {@value} */
	public static final String WEIBULL_ALPHA_SETTING = "weibullAlpha";
	/** Weibull shape parameter -setting id {@value} */
	public static final String WEIBULL_BETA_SETTING = "weibullBeta";
	/** A parameter to bound the active intervals (Pareto dist) -setting id 
	 * {@value} */
	public static final String MAX_SESSION_LENGTH_SETTING = "maxSessionLength";
	/** Determines if all nodes in the group should conform to the same 
	 * distribution parameters -setting id {@value} */
	public static final String UNIFORMITY_SETTING = "populationUniformity";
	
	public static final double defaultXm = 60.0; //seconds
	public static final double defaultMaxSessionLength = 12000; //seconds
	
	public static boolean populationUniformity = false;
	
	protected double r;
	protected double weiAlpha;
	protected double weiBeta;
	protected double maxSessionLength;
	
	protected ParetoRNG prng;
	protected ExponentialGenerator erng;
	protected static Random rng;
	
	/** Defines the start time of the next Active interval */
	protected double curStartTime;
	/** Defines the end time of the next Active interval */
	protected double curEndTime;
	
	public SmartphoneActiveness(Settings s)
	{
		if(rng == null)
			rng = new Random();
		/*
		 * From the paper, the rng distribution parameters vary over a population
		 * of users. In most cases, the parameters vary uniformly, except for the
		 * pareto dist's alpha parameter. The acceptable ranges for each parameter 
		 * are:
		 * xm: 15-65 sec 
		 *    (60 is so popular, though, that we just stick with it. It models the
		 *    idle timeout of the display screen)
		 *    
		 * alpha (pareto): [-0.8, 0.9], clusters around 0.2
		 * lambda (exp):   (0.0, 0.3]
		 * r:              [0.25, 0.9]
		 * alpha (weibull):[10, 600]
		 * beta (weibull): [0.3, 0.5]
		 */
		
		double xm, alpha, lambda;
		
		if(s.contains(UNIFORMITY_SETTING))
			populationUniformity = s.getBoolean(UNIFORMITY_SETTING);
		
		if(s.contains(PARETO_XM_SETTING)) 
			xm = s.getDouble(PARETO_XM_SETTING);
		else
			xm = defaultXm;
		
		if(s.contains(PARETO_ALPHA_SETTING))
			alpha = s.getDouble(PARETO_ALPHA_SETTING);
		else
			alpha = rng.nextGaussian() * 0.4 + 0.2;
		
		if(s.contains(EXP_LAMBDA_SETTING))
			lambda = s.getDouble(EXP_LAMBDA_SETTING);
		else
			lambda = rng.nextDouble() * 0.2999 + 0.0001;
		
		if(s.contains(R_VALUE_SETTING))
			this.r = s.getDouble(R_VALUE_SETTING);
		else
			this.r = rng.nextDouble() * 0.65 + 0.25;
		
		if(s.contains(WEIBULL_ALPHA_SETTING))
			this.weiAlpha = s.getDouble(WEIBULL_ALPHA_SETTING);
		else
			this.weiAlpha = rng.nextDouble() * 590.0 + 10;
		
		if(s.contains(WEIBULL_BETA_SETTING))
			this.weiBeta = s.getDouble(WEIBULL_BETA_SETTING);
		else
			this.weiBeta = rng.nextDouble() * 0.2 + 0.3;
		
		if(s.contains(MAX_SESSION_LENGTH_SETTING))
			this.maxSessionLength = s.getDouble(MAX_SESSION_LENGTH_SETTING);
		else
			this.maxSessionLength = defaultMaxSessionLength;
		
		
		this.prng = new ParetoRNG(rng, alpha, xm, this.maxSessionLength);
		this.erng = new ExponentialGenerator(lambda, rng);
		
		curStartTime = this.nextStartTime();
		curEndTime = curStartTime + this.nextDuration();
	}
	
	public SmartphoneActiveness(SmartphoneActiveness proto)
	{
		this.maxSessionLength = proto.maxSessionLength;
		
		/*
		 * If the settings specified that each object should behave the same, copy
		 * the prototype settings. Otherwise, generate new parameters for this new
		 * object.
		 * 
		 * TODO: If one or more parameters are specified in the settings, those
		 * should be uniform across the population, but the others should be chosen
		 * from their appropriate ranges. 
		 */
		if(populationUniformity)
		{
			this.r = proto.r;
			this.weiAlpha = proto.weiAlpha;
			this.weiBeta = proto.weiBeta;
			this.prng = proto.prng;
			this.erng = proto.erng;
		}
		else
		{
			double xm, alpha, lambda;
			xm = defaultXm;
			alpha = rng.nextGaussian() * 0.45;
			lambda = rng.nextDouble() * 0.2999 + 0.0001;
			this.r = rng.nextDouble() * 0.65 + 0.25;
			this.weiAlpha = rng.nextDouble() * 590.0 + 10;
			this.weiBeta = rng.nextDouble() * 0.2 + 0.3;
			
			this.prng = new ParetoRNG(rng, alpha, xm, this.maxSessionLength);
			this.erng = new ExponentialGenerator(lambda, rng);
		}
		
		
		curStartTime = this.nextStartTime();
		curEndTime = curStartTime + this.nextDuration();
	}
	
	public boolean isActive() {
		
		double time = SimClock.getTime();
		
		if(time > curEndTime)
		{
			double duration;
			//pick new start time and duration
			curStartTime = time + this.nextStartTime();
			duration = this.nextDuration();
			curEndTime = curStartTime + duration;
		}
		
		if(time < curStartTime) return false;
		
		return true;
	}
	
	public SmartphoneActiveness replicate()
	{
		return new SmartphoneActiveness(this);
	}
	
	protected double nextStartTime()
	{
		//Select from a Weibull Distribution
		return this.weiAlpha * Math.pow(- Math.log(rng.nextDouble()), 1 / this.weiBeta);
	}
	
	protected double nextDuration()
	{
		double pickDist = rng.nextDouble();
		if(pickDist < r) return erng.nextValue();
		else return prng.getDouble();
	}
	
	public static void main(String[] args)
	{
		int bucketSize = 5, trials = 100000;
		rng = new MersenneTwisterRNG();
		
		SmartphoneActiveness sa = new SmartphoneActiveness(new Settings());
		
		if(args.length > 0)
		{
			Map<Integer, Integer> lengthFreq = new TreeMap<Integer, Integer>();
			for(int i = 0; i < trials ; i++)
			{
				double length = sa.nextDuration();
				int index = (int)length/bucketSize, value = 0;
				if(lengthFreq.containsKey(index)) value = lengthFreq.get(index);
				lengthFreq.put(index, ++value);
			}
			
			for(Map.Entry<Integer, Integer> entry : lengthFreq.entrySet())
			{
				System.out.print(entry.getKey()*bucketSize);
				System.out.print(' ');
				System.out.println(((double)entry.getValue())/trials);
			}
		}
		else
		{
			List<Double> inter = new ArrayList<Double>();
			for(int i = 0; i < trials ; i++)
			{
				inter.add(sa.nextStartTime());
			}
			Collections.sort(inter);
			double cumProb = 0.0;
			for(Double d : inter)
			{
				cumProb += 1.0/trials;
				System.out.print(d);
				System.out.print(' ');
				System.out.println(cumProb);
			}
		}
	}
}
