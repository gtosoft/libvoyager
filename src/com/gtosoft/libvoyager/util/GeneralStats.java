/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import android.util.Log;

/**
 * This class will be used for stats collection across many classes such as elmbt, and the session layers. 
 * The idea is to have a common data type (this class) which we can funnel up to a higher level (hybridSession).
 * @author brad
 *
 */

public class GeneralStats {

	// Define a hashmap to store stats. 
	HashMap<String,String> mhmStats = new HashMap<String, String>();
	
	
	/**
	 * A single point to get (all stats at once) or set one stat at a time. 
	 * We can synchronize this function if needed. For now, don't, because it is a ton of overhead that's not needed. 
	 * @param key
	 * @param value
	 * @return - returns the global hashmap of stats. If key is blank ("") then we return A COPY OF our local stats. This is a special case used when returning stats to a calling method. 
	 */
	public HashMap<String,String> setStat (String key, String value) {
		// if they're just getting the hashmap as a whole, such as the case when a caller wants our stats. 
		if (key.equals(""))
			return new HashMap<String,String>(mhmStats);

		// little failsafe thing. 
		if (value == null)
			value = "(NULL)";
		
		// Try and set the new value. This should always work, unless something funky is happening. 
		try {
			mhmStats.put(key, value);
		} catch (Exception e) {
			msg ("ERROR setting stat K=" + key + " V=" + value + " E=" + e.getMessage());
			return mhmStats;
		}
		
		return mhmStats;
	}

	/**
	 * Convenience method to set an integer stat. 
	 * @param key - stat key, think hierarchy. 
	 * @param value - integer value to set that stat to. It will still be stored as a string. 
	 * @return - returns current genStats hashmap. 
	 */
	public HashMap<String,String> setStat (String key, int value) {
		return setStat(key, "" + value);
	}

	/**
	 * Convenience method. 
	 * @param key
	 * @param value
	 * @return
	 */
	public HashMap<String,String> setStat (String key, double value) {
		return setStat(key, "" + value);
	}
	
	/**
	 * Convenience method to set a long stat. 
	 * @param key - stat key, think hierarchy. 
	 * @param value - integer value to set that stat to. It will still be stored as a string. 
	 * @return - returns current genStats hashmap. 
	 */
	public HashMap<String,String> setStat (String key, long value) {
		return setStat(key, "" + value);
	}

	
	/**
	 * Returns a new (thread-safe) hashmap containing all of our stats.  
	 * @return - returns a new hashmap containing all our stats. 
	 */
	public HashMap<String,String> getStatsAsHashmap () {
		return setStat("", "");
	}
	
	/**
	 * Returns a new hashmap which contains a snapshot of the contents of our stats hashmap. 
	 * we return a copy of our stats hashmap so that the caller can do as they please with the information and not worry about concurrent modifications. 
	 * @return
	 */
	public HashMap<String,String> getStats() {
		return setStat("", "");
	}

	private void msg (String messg) {
		Log.d("GS:",messg);
	}
	
	/**
	 * Use this method to merge other GeneralStats data into your instance. 
	 * @param tag - a unique tag to identify the stats you're merging
	 * @param gs - a generalstats instance which is to be merged. 
	 * @return - true on success, false otherwise. 
	 */
	public boolean merge (String tag, GeneralStats gs) {
		HashMap<String,String> theirStats = gs.getStats();
		
		Set<String>s = theirStats.keySet();
		Iterator<String> i = s.iterator();
		
		String thisKey = "";
		String thisValue = "";
		
		// Loop through all the stats in their generalstats instance. For each one, merge it into our local store!
		while (i.hasNext()) {
			try {
				thisKey = i.next();
			} catch (Exception e) {
				msg ("ERROR: HM COLLISION while merging stats. K=" + thisKey + " E=" + e.getMessage());
				return false;
			}
			thisValue = theirStats.get(thisKey);
			setStat(tag + "." + thisKey, thisValue);
		}
		
		return true;
	}

	/**
	 * Used to obtain a single stat from our hashmap. 
	 * @return - returns "" if the stat is not found, otherwise the exact value of the stat from the hashmap. 
	 */
	public String getStat (String statKey) {
		String statVal = mhmStats.get(statKey);
		
		if (statVal != null) 
			return statVal;
		else
			return ""; // instead of returning null, return a blank string. 
		
	}
	
	
	/**
	 * Produce a big string (CRLF line termination) which contains all stats contatined herein.
	 * @return - returns a big string with CRLF line delimiters. 
	 */
	public String getAllStats () {
		String allStats = "";
		
		// Create a new treeset (sorted key set) based on the hashmap of key values. 
		TreeSet<String> tsKeys = new TreeSet<String>(mhmStats.keySet());
		Iterator<String> i = tsKeys.iterator();
		
		String thisKey = "";
		String thisValue = "";
		while (i.hasNext()) {
			thisKey = i.next();

			// get the value. 
			thisValue = mhmStats.get(thisKey);

			// append this stat. 
			allStats = allStats + thisKey + "=" + thisValue + "\n";
		}// end of while. 
		
		return allStats;
	}// end of getAllStats. 

	
	/**
	 * Takes the given key and sets it to a new value which is one greater than what it was last. 
	 * In the case that the stat hasn't been set before, we create it and set it to 1. 
	 * @param key
	 */
	public void incrementStat(String key) {
		int newVal = 1;
		
		if (mhmStats.containsKey(key)) {
			try {
				newVal = Integer.valueOf(mhmStats.get(key)) + 1;
			} catch (Exception e) {
				msg ("UNEXPECTEDD NUMBER PROBLEM (this shouldn't happen). Old Value " + mhmStats.get(key) + " E=" + e.getMessage());
			}
		} 

		setStat(key, newVal);
	}
	
	public String getAllStatsAsString () {
		String allStats = "";
		
		HashMap<String,String> hmStats = mhmStats;
		
		// use a treeSet for auto sorting... 
		Set<String> s = new TreeSet<String>(hmStats.keySet());
		Iterator<String> i = s.iterator();
		
		String key = "";
		String value = "";
		while (i.hasNext()) {
			key = i.next();
			value = hmStats.get(key);
			allStats = allStats + key + "=" + value + "\n";
		}// end of while

		return allStats;
	}// end of getAllStatsaSString

	
}

