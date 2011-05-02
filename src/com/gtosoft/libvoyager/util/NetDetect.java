/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.util.Iterator;
import java.util.Set;

import com.gtosoft.libvoyager.db.DashDB;

import android.util.Log;

/**
 * The purpose of this class is to perform passive CAN network detection. 
 * A separate class will collect network data (i.e. CAN IDs) and we will look at that collected data and form a "best guess" at which datapoint Network ID to use. 
 * Each entry in the dataPoint table in the Voyager Database contains a field "Network ID" which must be set in order to decode passive network traffic.
 * This class will provide the link (the Network ID) between the collection of [unknown] network data, and the processes which decode that data.     
 * @author brad
 */


public class NetDetect {
	final boolean DEBUG = false;

	// This will store the base set of CAN IDs procided to us. These will be the basis of our comparisons. 
	Set<String> mBaseSet;

	// we may want to collect some general stats. 
	GeneralStats mgStats = new GeneralStats();

	// A reference to the caller's instance of DashDB. 
	DashDB ddb;
	


	/**
	 * Default constructor. 
	 */
	public NetDetect(DashDB d) {
		ddb = d;
//		msg ("Initialization complete.");
	}
	

	/**
	 * Given a base set (set of observed CAN IDs), we will compare against all available DPNs in the DB. We'll then return the DPN Network ID corresponding to the closest match.
	 * @return - if no base set elements were provided then we will return a default value. Currently that is a blank string.
	 * If you get a "" from this method, then that means you should try again later when we have more PIDs, or it could mean your network is not known. 
	 */
	public synchronized String getBestGuessNetworkID (Set<String> newBaseSet) {
		final String defaultNetworkID 	= "";
		final int minTimeBetweenTries	= 10;
		String bestGuessNetworkID 		= "";
		int highestMatches 				= 0;
		
		Set<String> allNetworkIDs;

		// Collect a stat. 
		if (newBaseSet != null)
			mgStats.setStat("baseSetSize", "" + newBaseSet.size());
		else
			mgStats.setStat("baseSetSize", "NULL");

		
		// Sanity check
		mBaseSet = newBaseSet; // make note of the caller's new base set. 
		if (mBaseSet == null || mBaseSet.size() == 0) {
			if (DEBUG) msg ("WARNING: Empty base set. Unable to perform detection.");
			return defaultNetworkID;
		}
		
		// Get a list of all of the Network IDs for which we have decoders for. 
		allNetworkIDs = ddb.getAllDPNNetIDs();
		if (DEBUG) msg ("Got Network IDs list, there are " + allNetworkIDs.size() + " Entries.");

		// This shouldn't happen, but if there are no decoders whatsoever, return the default. 
		if (allNetworkIDs.size() == 0) {
			msg ("ERROR: Zero decoders found. This shouldn't happen (DB Error)");
			return defaultNetworkID;
		}
		
		// There must be elements present, so process them...
		

		
		Iterator<String> iNetID = allNetworkIDs.iterator();
		Set<String> thisCANIDSet;
		String thisNetID = "";
		int numMatches = 0;
		// Loop through all the network IDs. For each one, get a set of CAN IDs. Then compare that set of CAN IDs with our base set. Get num intersections. 
		// We're ultimately trying to find the Network ID with the most CAN ID intersections with our base set. 
		while (iNetID.hasNext()) {
			thisNetID = iNetID.next();
			thisCANIDSet = ddb.getDPNCANIDsForNetworkID(thisNetID);
			numMatches = getNumIntersections(mBaseSet, thisCANIDSet);
			// Collect a stat!
			mgStats.setStat("ID" + thisNetID + "Matches", "" + numMatches);
			
			// If the current Net ID has more matches than the last highest number of matches then we have a NEW match!
			if (numMatches > highestMatches) {
				// hooray! 
				msg ("New best guess: ID=" + thisNetID + " matches=" + numMatches + " PriorMatches=" + highestMatches);

				// collect a couple useful stats.
				mgStats.setStat("bestGuessID", thisNetID);
				mgStats.setStat("bestGuessMatches", "" + numMatches);

				// Now jot down this new match!
				bestGuessNetworkID = thisNetID;
				highestMatches = numMatches;
			}
		}

		return bestGuessNetworkID;
	}
	
	/**
	 * This method counts the number of elements which are present in both sets. It returns that number.
	 * @return - returns the number of elements which are common to both sets. 
	 */
	private int getNumIntersections (Set<String> s1, Set<String> s2) {
		int numIntersects;

		// Sanity check - weed out nulls. 
		if (s1 == null || s2 == null) {
			if (DEBUG) msg ("Intersections is 0 because size1 or size2 is NULL.");
			return 0;
		}
		
		// Sanity check - weed out empty sets.  
		if (s1.size() == 0 || s2.size() == 0) {
			if (DEBUG) msg ("Intersections is 0 because size1 or size2 is 0.");
			return 0;
		}

		Iterator<String> i = s1.iterator();
		numIntersects = 0;
		String thisKey = "";
		// Loop through all elements of set 1. For each element, search set 2 for that element. For each match, increment the intersections variable. 
		if (DEBUG) msg ("Comparing baseset=" + s1.size() + " secondary=" + s2.size());
		while (i.hasNext()) {
			thisKey = i.next();
			// Does this element exist in set#2? If so, it's an intersection! 
			if (s2.contains(thisKey))
				numIntersects++;
		}
		
		
		return numIntersects;
	}


	/**
	 * Local logging shortcut. 
	 * @param m
	 */
	private void msg (String m) {
		Log.d("VoyagerND",m);
	}

	/**
	 * returns a reference to our instance of the general stats class. 
	 * @return - a reference to our instance of the general stats class.
	 */
	public GeneralStats getStats() {
				
		return mgStats;
	}

	
}
