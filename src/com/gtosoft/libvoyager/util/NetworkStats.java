/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.gtosoft.libvoyager.db.DashDB;

import android.os.Environment;
import android.util.Log;



public class NetworkStats {

	GeneralStats mgStats = new GeneralStats();
	
	// counts the number of messages we have accepted and processed.
	long mMessagesProcessed = 0;
	long mMessagesThisInterval = 0;
	long mIntervalStart = 0;
	int mPidChangeLogThreshold = 500; // set this super high by default to log
	// everything. Calling class can set it
	// lower if desired.

	
	long mTimeOfLastNewHeader = 0;


	DashDB dashDB = null;
	
	String mMonitorSessionID = "";
	
	EasyTime metime = new EasyTime();
	
	// Stores PIDs and last known data.
	HashMap<String, String> mhmPIDData = null;

	// Keeps track of timestamp when we last saw this PID.
	HashMap<String, Long> mhmPIDLastSeen = null;
	
	// Keeps track of number of times this header has changed data.
	HashMap<String, Long> mhmPIDChanges = null;
	
	// counts number of times each PID/header has been seen. 
	HashMap<String, Integer> mhmPIDSeenCount = null;

	// If not null, then logging is enabled and we should write every packet to file!
	FileWriter mLogFile = null;
	final String mLogFileNameX = "replaylog";
	
	
	public NetworkStats(DashDB ddb) {
		dashDB = ddb;
		resetStats();
	}
	
	/**
	 * Create the specified directory (the last part of the path only, not the
	 * whole tree).
	 * 
	 * @param directory
	 *            - directory, for example: /sdcard/Dash
	 * @return - returns true on success, false otherwise.
	 */
	public boolean mkdir(String directory) {

		try {
			new File(directory).mkdir();
		} catch (Exception e) {
			msg("mkdir(): Error creating directory: " + directory + " msg=" + e.getMessage());
			return false;
		}

		return true;
	}

	/**
	 * controls whether we'll be logging packets to file or not. 
	 * @param trueOrFalse - set to true if you want to log packets to the sdcard. 
	 * @return
	 */
	public boolean setLogging (boolean trueOrFalse) {


		
		// true, so activate logging. 
		if (trueOrFalse == true) {
			try {
				setLogging(false);// close logfile in case already open.
				// Make sure the directory exists. 
				String backupDirectory = Environment.getExternalStorageDirectory()+ "/Dash";
				mkdir (backupDirectory);
				mLogFile = new FileWriter(backupDirectory + "/" + mLogFileNameX,true);
			} catch (Exception e1) {
				msg ("Error setting up logger: " + e1.getMessage());
				return false;
			}

			
			// Append a timestamp to the file so we know where we're starting. 
			try {
				mLogFile.append("\n# LOG STARTED " + EasyTime.currentDateAndTimeStamp() + "\n");
			} catch (IOException e) {
				msg ("Error: unable to write timestamp to logfile. deactivating logging.");
				mLogFile = null;
			}
			
		}// end of "if true, do all this"
		else {
			// request for logging to be shut off. Try and close the file gracefully and set log handle to null.
			try {
				mLogFile.close();
				mLogFile = null;
			} catch (Exception e) {
				msg ("Error while closing logfile: " + e.getMessage());
			}
		}

		return true;
	}

	/**
	 * Writes (appends) the given packet to the log file, if logging is enabled. 
	 * @param packet - the exact packaet you want appended. We'll add CRLF. 
	 */
	private void logOnePacket (String HDR, String data) {
		if (mLogFile != null) {
			try {
				mLogFile.append(HDR + " " + data + "\n");
			} catch (IOException e) {
				msg ("Error appending data to logfile. E=" + e.getMessage() + "Dash: Closing logfile.");
				setLogging(false);
			}
		}
	}

	
	/**
	 * THIS METHOD IS THE ENTRYPOINT FOR DATA INTO THE NETWORKSTATS CLASS.
	 * 
	 * Sets the current data point for a given PID and also updates variables
	 * for stats. This method is called by the entrypoint method as soon as the
	 * header and data parts are separated.
	 * 
	 * @param pid
	 *            - PID of the data point
	 * @param data
	 *            - Data payload for the given PID.
	 */
	public void setPIDDataByHeader(String header, String newData) {
		// TODO: Instead of decoding every PID as it comes in, let our parent
		// define a list of headers that we should watch out for. When we see
		// data with one of those headers, then we execute the callback.

		// Did the data change?
		boolean isNew = false;
		boolean isDataChanged = false;

		// pass the packet to the logger, if logging is not on, it ignores the call. 
		logOnePacket(header,newData);
		
		String oldData = "";
		if (mhmPIDData.containsKey(header)) {
			oldData = mhmPIDData.get(header);
		} else {

		}

		// determine if the data is new, if it's changed, etc. Fire off
		// necessary event handlers.
		if (mhmPIDData.containsKey(header)) {
			if (dataComparison("SIMPLE", header, oldData, newData)) {
				dataChanged(header, oldData, newData);
				incrementChangedCount(header); // stats.
				isDataChanged = true;
			}
		} else {
			// The header doesn't exist in the hashmap yet. Call a hook.
			newHeaderPresent(header, newData);
			isNew = true;
		}

//		// Kick off callback, if one is registered.
//		if (mNewDataHandler != null)
//			mNewDataHandler.onNewDataArrived("", header, oldData, newData,
//					isNew, isDataChanged);

		// After we're done getting "old data" by getting it from the hashmap,
		// it's safe to put the new data into the hashmap.
		mhmPIDData.put(header, newData);

		incrementSeenCount(header); // stats

		mMessagesProcessed++; // stats
		mMessagesThisInterval++; // stats

	}

	private void incrementSeenCount(String header) {

		// record the timestamp
		mhmPIDLastSeen.put(header, metime.getUptimeSeconds());

		// Increment the seen-count stats hashmap for this PID.
		if (mhmPIDSeenCount.containsKey(header)) {
			// increment existing
			mhmPIDSeenCount.put(header, mhmPIDSeenCount.get(header) + 1);
		} else {
			// add the pid.
			mhmPIDSeenCount.put(header, 1);
		}
	}

	private void incrementChangedCount(String header) {
		if (mhmPIDChanges.containsKey(header) == true) {
			// get the old value, increment it by one, put it back in.
			mhmPIDChanges.put(header, mhmPIDChanges.get(header) + 1);
		} else {
			// Key does not yet exist.
			mhmPIDChanges.put(header, 1L);
		}

	}
	
	/**
	 * Returns TRUE if dat changed
	 * 
	 * @param compareMethod
	 * @param header
	 * @param oldData
	 * @param newData
	 * @return - TRUE if data changed, using the specified comparison method
	 *         string.
	 */
	private boolean dataComparison(String compareMethod, String header,
			String oldData, String newData) {
		if (compareMethod.equals("SIMPLE")) {
			if (oldData.equals(newData))
				return false;
			else
				return true;
		} else
			msg("Unknown data comparison module: " + compareMethod);

		// TODO: Add verious comparison methods here. For example, look at a
		// single byte in the data. Or "length changed".

		return false;
	}


	/**
	 * This gets called when setPIDDataByHeader() registers data for a PID which
	 * previously didn't exist.
	 * 
	 * @param header
	 * @param data
	 */
	private void newHeaderPresent(String header, String data) {
		// TODO: Anything we want, related to new data being present.

		mTimeOfLastNewHeader = EasyTime.getUnixTime();

		// If we're within a monitor session (if sessionID is defined) then
		// write the new header info to the DB.
		if (mMonitorSessionID.length() > 0)
			dashDB.addMonitorRecord(mMonitorSessionID, "" + EasyTime.getUnixTime(), header, "", data, 1, 1, 1, 1);

		if (header.startsWith("0F FF E0 ") && header.length() >= 11) {
			String nodeAddress = header.substring(9, 11);
			String nodeType = getNodeTypeByAddress(nodeAddress);
			msg("NEW NODE DETECTED: " + nodeAddress + " TYPE: " + nodeType);
		}

	}

	/**
	 * @return - returns the number of seconds since a new PID was seen. 
	 */
	public long getTimeSinceLastNewPID () {
		
		// Special case: no new pids have arrived yet. 
		if (mTimeOfLastNewHeader == 0)
			return 0;
		
		return (EasyTime.getUnixTime() - mTimeOfLastNewHeader);
	}

	/**
	 * This gets executed any time a setPIDDataByHeader() registers data for a
	 * PID and that data doesn't match the last known data for the PID.
	 * 
	 * @param header
	 * @param oldData
	 * @param newData
	 */
	private void dataChanged(String header, String oldData, String newData) {
		// TODO: anything we want, related to data changing. For example, search
		// a list of headers that the parent method wishes us to "decode" and if
		// this one's in the list, decode it.

		long changes = 0;
		if (mhmPIDChanges.containsKey(header))
			changes = mhmPIDChanges.get(header);

		long seenCount = 0;
		if (mhmPIDSeenCount.containsKey(header))
			seenCount = mhmPIDSeenCount.get(header);

		// used for calculating whether to log this pid change or not. Also
		// logged as another piece of information with each change.
		long changeRate = getPIDChangeRate(header);

		// If we're within a monitor session (if sessionID is defined) then
		// write the change info to the DB.
		if (mMonitorSessionID.length() > 0
				&& changeRate < mPidChangeLogThreshold)
			dashDB.addMonitorRecord(mMonitorSessionID, ""
					+ EasyTime.getUnixTime(), header, oldData, newData, changes,
					changeRate, getPIDTransmitRate(header), seenCount);

	}

	
	/**
	 * Returns the number of seconds that have elapsed since the given
	 * PIDwithSigs was seen.
	 * 
	 * @param PIDWithSigs
	 *            - the PID and any significant data bytes as defined in the
	 *            dataPoint table of the Dash DB.
	 * @return - returns number of seconds.
	 */
	public long getPIDAge(String PIDWithSigs) {
		Long age = 0L;

		if (mhmPIDLastSeen.containsKey(PIDWithSigs))
			age = metime.getUptimeSeconds() - mhmPIDLastSeen.get(PIDWithSigs);

		return age;
	}

	public long getIntervalDuration() {
		long duration = getTimeStamp() - mIntervalStart;

		if (duration == 0)
			return 1L; // instead of returning 0... prevents situation for
						// divide by zero.
		else
			return duration;
	}


	/**
	 * Returns the number of PIDs being processed per minute.
	 * 
	 * @return - number PIDs we're processing per minute, according to the
	 *         current stats.
	 */
	public long getPIDsPerMinute() {
		long intervalDuration = getIntervalDuration(); // getTimeStamp() -
														// mIntervalStart;

		if (intervalDuration == 0)
			intervalDuration = 1;

		return ((60 * mMessagesThisInterval) / intervalDuration);

	}

	
	/**
	 * Goes to the internal store of PIDs (collected during passive mode) and
	 * extracts the set of nodes that are transmitting "COMM ACTIVE" signals.
	 * 
	 * @return - returns a set<String> wich each entry containing a single
	 *         string representation of a node's address.
	 */
	public Set<String> getNodeAddressList() {

		Set<String> s = new HashSet<String>();

		Set<String> headers = mhmPIDData.keySet();

		Iterator<String> i = headers.iterator();

		String thisHeader = "";
		while (i.hasNext()) {
			thisHeader = i.next();

			// Throw out headers of insufficient length.
			if (thisHeader.length() < 11)
				continue;

			// check and see if it's a comm-active broadcast.
			if (thisHeader.startsWith("0F FF E0 ")) {
				s.add(thisHeader.substring(9, 11));
			}

		}// end of while

		return s;
	}// end of getNodeAddressList()

	
	public String getStatsString() {
		long intervalDuration = getIntervalDuration();
		if (intervalDuration <= 0)
			intervalDuration = 1;

		String stats = "";

		stats = stats + "STATS: Total PIDS: " + getPIDCount() + "\n";
		stats = stats + "STATS: PIDs processed per second: "
				+ getPIDsPerMinute() / 60 + "\n";
		stats = stats + "STATS: Interval: " + intervalDuration + " seconds\n";

		// Iterate through all the PIDs and append each one and its stats to the
		// stats string.
		String key = "";
		long rate = 0;
		// we chose the pidseencount hashmap randomly. We could have chosen any
		// of the hashmaps which contains pid keys.
		Set<String> s = mhmPIDSeenCount.keySet();
		Iterator<String> i = s.iterator();

		int pidnum = 0;
		while (i.hasNext()) {
			try {
				key = i.next();
			} catch (Exception e) {
				msg("Bang! ");
				stats = stats + "ERROR: THREAD COLLISION. Try Again.";
				return stats;
			}
			rate = (60 * mhmPIDSeenCount.get(key)) / intervalDuration;
			stats = stats + "(" + pidnum + ") STATS: PID=[" + key + "] TXRate="
					+ rate + "/Min" + " Age=" + getPIDAge(key) + "s" + "\n";
			stats = stats + "    Last DATA: [" + mhmPIDData.get(key) + "]\n";

			pidnum++;
		}

		// if (pidnum != mhmPIDSeenCount.size())
		msg("STATS: shown=" + pidnum + " seen-map size="
				+ mhmPIDSeenCount.size() + " data-map size="
				+ mhmPIDData.size());

		resetStats();
		return stats;
	}

	
	/**
	 * Returns the rate of change (changes per minute) for the given header.
	 * 
	 * @param header
	 * @return - returns the number of changes per minute.
	 */
	public long getPIDChangeRate(String header) {
		long elapsedTime;

		// never seen it before?
		if (mhmPIDChanges.containsKey(header) != true)
			return 0;

		elapsedTime = getIntervalDuration(); // getTimeStamp() - mIntervalStart;

		if (elapsedTime == 0)
			elapsedTime = 1;

		return ((60L * mhmPIDChanges.get(header)) / elapsedTime);
	}


	
	/**
	 * 
	 * @param header
	 * @return - returns the rate (in transmits per minute) at which this PID is
	 *         transmitted, assuming no packet loss.
	 */
	public long getPIDTransmitRate(String header) {
		long elapsedTime;

		if (mhmPIDSeenCount.containsKey(header) != true)
			return 0;

		elapsedTime = getIntervalDuration(); // getTimeStamp() - mIntervalStart;

		if (elapsedTime == 0)
			elapsedTime = 1;

		return ((60L * mhmPIDSeenCount.get(header)) / elapsedTime);
	}


	/**
	 * Returns the number of PIDs which we've managed to set data for.
	 * 
	 * @return
	 */
	public int getPIDCount() {

		if (mhmPIDData == null)
			return 0;

		return mhmPIDData.size();
	}

	public long getNumMessagesProcessed() {
		return mMessagesProcessed;
	}

	/**
	 * Reset stats counters and such.
	 */
	private void resetStats() {
		mhmPIDSeenCount = new HashMap<String, Integer>();
		mhmPIDChanges = new HashMap<String, Long>();
		mhmPIDData = new HashMap<String, String>();
		mhmPIDLastSeen = new HashMap<String, Long>();
		mhmPIDChanges = new HashMap<String, Long>();
		mMessagesProcessed = 0;
	}

	private void msg (String message) {
		Log.d("NStats",message);
	}

	/**
	 * Return a timestamp, in seconds, which increments linearly throughout the
	 * course of this application's lifecycle. May be a Unix timestamp or
	 * seconds of uptime (most likely the latter).
	 * 
	 * @return - returns number of seconds, possibly a huge number, or maybe a
	 *         smaller number such as in the case of uptime.
	 */
	private long getTimeStamp() {
		return metime.getUptimeSeconds();
	}


	/**
	 * Sleeps for the specified interval, or shorter if interrupted. Exceptions
	 * are caught so you don't have to.
	 * 
	 * @param millis
	 */
	private void safeSleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			return;
		}

	}

	/**
	 * @param secondsToSettleIn
	 * @return - returns the newly created monitor session ID associated with
	 *         all packets captured.
	 */
	public String startMonitorSession() {

		// Stop any already-running monitor sessions.
		stopMonitorSession();

		// Create a new monitor session entry to correlate with all the monitor
		// data we're about to collect.
		final String monitorSessionID = dashDB.getNextMonitorSessionID(""
				+ EasyTime.getUnixTime());

		// reset all stored data and stats.
		resetStats();

		// Immediately start logging data to the DB. Data is logged to DB as
		// long as the monitorSessionID is not blank.
		mMonitorSessionID = monitorSessionID;

		// new Thread() {
		// public void run () {
		// // Sleep for a bit so we don't get a flood of stuff at the start.
		// try {Thread.sleep (secondsToSettleIn * 1000L); } catch
		// (InterruptedException e) { }
		//
		// // By defining the global monitor session ID variable, we will
		// automatically begin writing data changes to the database.
		// mMonitorSessionID = monitorSessionID;
		// }// end of run
		// }.start();

		return monitorSessionID;
	}

	/**
	 * stops logging monitor session data to the DB by setting a global variable
	 * that the event handler sees and stops using.
	 * 
	 * @return - returns the number of messages processed during the monitor
	 *         interval.
	 */
	public long stopMonitorSession() {

		// We may have been called upon even if there wasn't an active session.
		if (mMonitorSessionID.length() < 1)
			return 0;

		// Update the monitorsession in the DB to reflect the end time.
		dashDB.setMonitorSessionStopTime(mMonitorSessionID, ""
				+ EasyTime.getUnixTime());

		// write an info message.
		msg("Monitor session ID " + mMonitorSessionID
				+ " terminated. Processed " + mMessagesThisInterval
				+ " packets, ");

		// Setting this to "" results in other logic in this class ceasing to
		// write PID changes to the DB.
		mMonitorSessionID = "";

		// return the number of packets captured during this session.
		return mMessagesThisInterval;
	}



	public static String getNodeTypeByAddress(String nodeAddress) {
		String nodeType = "UNKNOWN(" + nodeAddress + ")";

		int iNodeAddress = 0;

		try {
			iNodeAddress = Integer.valueOf(nodeAddress, 16);
		} catch (Exception e) {
			// msg ("Error converting node " + nodeAddress + " to integer. ERR="
			// + e.getMessage());
			return "ERROR: " + e.getMessage();
		}

		if (iNodeAddress >= 0x00 && iNodeAddress <= 0x0f)
			nodeType = "Powertrain Expansion";

		if (iNodeAddress >= 0x10 && iNodeAddress <= 0x17)
			nodeType = "Powertrain Engine Controller";

		if (iNodeAddress >= 0x18 && iNodeAddress <= 0x1F)
			nodeType = "Powertrain Transmission Controller";

		if (iNodeAddress >= 0x20 && iNodeAddress <= 0x3F)
			nodeType = "Chassis Controller";

		if (iNodeAddress >= 0x20 && iNodeAddress <= 0x27)
			nodeType = "Chassis Expansion";

		if (iNodeAddress >= 0x28 && iNodeAddress <= 0x2F)
			nodeType = "Chassis Brake Controller";

		if (iNodeAddress >= 0x30 && iNodeAddress <= 0x37)
			nodeType = "Chassis Steering Controller";

		if (iNodeAddress >= 0x38 && iNodeAddress <= 0x3F)
			nodeType = "Chassis Suspension Controller";

		if (iNodeAddress >= 0x40 && iNodeAddress <= 0xC7)
			nodeType = "Body Controller";

		if (iNodeAddress >= 0x40 && iNodeAddress <= 0x57)
			nodeType = "Body Expansion Controller";

		if (iNodeAddress >= 0x58 && iNodeAddress <= 0x5F)
			nodeType = "Body Restraint Controller";

		if (iNodeAddress >= 0x60 && iNodeAddress <= 0x6F)
			nodeType = "Body Driver Information/Display Controller";

		if (iNodeAddress >= 0x70 && iNodeAddress <= 0x7F)
			nodeType = "Body Lighting Controller";

		if (iNodeAddress >= 0x80 && iNodeAddress <= 0x8F)
			nodeType = "Body Entertainment/Audio Controller";

		if (iNodeAddress >= 0x90 && iNodeAddress <= 0x97)
			nodeType = "Body Personal Communications Controller";

		if (iNodeAddress >= 0x98 && iNodeAddress <= 0x9F)
			nodeType = "Body Climate Control (HVAC) Controller";

		if (iNodeAddress >= 0xA0 && iNodeAddress <= 0xBF)
			nodeType = "Body Convenience (Doors/Seats/Windows/etc.) Controller";

		if (iNodeAddress >= 0xC0 && iNodeAddress <= 0xC7)
			nodeType = "Body Security Controller";

		if (iNodeAddress == 0xC8)
			nodeType = "Electric Vehicle Utility Connection Controller";

		if (iNodeAddress == 0xC9)
			nodeType = "Electric Vehicle AC to AC Conversion Controller";

		if (iNodeAddress == 0xCA)
			nodeType = "Electric Vehicle AC to DC Conversion Controller";

		if (iNodeAddress == 0xCB)
			nodeType = "Electric Vehicle Energy Storage Controller";

		return nodeType;
	}

	public void setPidChangeLogThreshold(int logIfLessThanThisNumChangesPerMin) {
		mPidChangeLogThreshold = logIfLessThanThisNumChangesPerMin;
	}


	/**
	 * Returns the most recent data bytes stored for the given header If no data
	 * is being held for the given header, we return a blank string NOT null.
	 * 
	 * @param header
	 * @return - returns up to 8 hex data bytes as a string.
	 */
	public String getDataByHeader(String header) {
		String data = "";

		if (mhmPIDData.containsKey(header) == true)
			data = mhmPIDData.get(header);
		else
			data = "";

		return data;
	}


//	private boolean startStatsThread() {
//	if (mtStatsThread != null)
//		return false;
//
//	mtStatsThread = new Thread() {
//		public void run() {
//
//			msg("Stats thread starting...");
//			while (mThreadsOn == true) {
//
//				// calculateStats();
//
//				try {
//					Thread.sleep(5000);
//				} catch (InterruptedException e) {
//					// if sleep interrupted, break out of the thread loop.
//					// We must be shutting down.
//					break;
//				}// end of sleep try-catch
//			}// end of main while loop within stats thread.
//
//			msg("Stats Thread finished.");
//		}// end of run()
//
//	};// end of thread class definition.
//
//	mtStatsThread.start();
//
//	return true;
//}

//	long mMessagesProcessed = 0;
//	long mMessagesThisInterval = 0;
//	long mIntervalStart = 0;
//	int mPidChangeLogThreshold = 500; // set this super high by default to log
//	// everything. Calling class can set it
//	// lower if desired.
//
//
//	DashDB dashDB = null;
//	
//	String mMonitorSessionID = "";
	
	
	public GeneralStats getStats () {
		mgStats.setStat("messagesProcessed", "" + mMessagesProcessed);
		mgStats.setStat("messagesThisInterval", "" + mMessagesThisInterval);
		mgStats.setStat("monitorSessionID", mMonitorSessionID);
		mgStats.setStat("pidsPerMinute", "" + getPIDsPerMinute());
		mgStats.setStat("intervalSecs", "" + getIntervalDuration());
		mgStats.setStat("pidCount", "" + getPIDCount());
		
		// NEW: use a Treeset for two reasons: 
		// 1. we create a new instance of a SET so we avoid concurrent modification of the other set
		// 2. a treeset is inherently sorted, so we get a nicely sorted list of stats. Well the sorting isn't too too necessary because we sort it in a higher layer anyways...
		Set<String> s = new TreeSet<String>(mhmPIDData.keySet());
		Iterator<String> i = s.iterator();
		
		String thisKey = "";
		String thisStat = "";
		// Build a stat string for every PID! put it in our general stats store as pid.X where X is the PID header. 
		while (i.hasNext()) {
			thisKey = i.next();
			thisStat = "ChgRate=" + getPIDChangeRate(thisKey);
			thisStat += " TXRate=" + getPIDTransmitRate(thisKey);
			thisStat += " D=" + mhmPIDData.get(thisKey);
			mgStats.setStat("pid." + thisKey, thisStat);
		}
		
		
		return mgStats;
	}

	/**
	 * @return - returns a list of PIDs, preferribly sorted from most frequently transmitted, to least frequent. 
	 */
	public String getIDList() {
		String pidlist = "";
		
		// key = CAN header
		// value = frequency of transmissions. 
		TreeMap<String, Double> hmTransmitFrequencies = new TreeMap<String,Double> ();

		Set <String> s = getIDSet();
		Iterator<String> i = s.iterator();
		String thisKey = "";
		double thisVal = 0;
		// produce a hashmap containing PIDs and their transmit frequencies.

		while (i.hasNext()) {
			thisKey = i.next();
			thisVal = getPIDTransmitRate(thisKey);
			hmTransmitFrequencies.put(thisKey, thisVal);
		}

//		// instantiate the hashmap that's going to hold the frequencies, sorted. 
//		HashMap<String,Double> hmSortedByFrequency = sortHashMapByValuesD(hmTransmitFrequencies);
		
		// Loop through the sorted hashmap to produce a string of pids. 
		for (String thisHeader: hmTransmitFrequencies.keySet()){
			if (thisHeader.length()>0)
				pidlist += thisHeader + "(" + getPIDTransmitRate(thisHeader) + "/m)" + "|";
		}
		
		// chop off trailing delimiter
		if (pidlist.length() > 1) {
			pidlist = pidlist.substring(0,pidlist.length() - 1);
		}
		
		return pidlist;
	}

	/**
	 * @return - returns a (newly allocated) Set of CAN IDs which we have observed thus far.
	 * We allocate a new set for you so that there's no chance of a concurrent read/modification exception while you're accsesing the set.  
	 */
	public Set<String> getIDSet () {
		Set<String> s = new HashSet<String>(mhmPIDData.keySet());
		return s;
	}
	
	// Credit: http://www.lampos.net/how-to-sort-hashmap
	public LinkedHashMap<String,Double> sortHashMapByValuesD(HashMap<String,Double> passedMap) {
	    List mapKeys = new ArrayList(passedMap.keySet());
	    List mapValues = new ArrayList(passedMap.values());
	    Collections.sort(mapValues);
	    Collections.sort(mapKeys);
	        
	    LinkedHashMap <String,Double>sortedMap = new LinkedHashMap<String,Double>();
	    
	    Iterator valueIt = mapValues.iterator();
	    while (valueIt.hasNext()) {
	        Object val = valueIt.next();
	        Iterator keyIt = mapKeys.iterator();
	        
	        while (keyIt.hasNext()) {
	            Object key = keyIt.next();
	            String comp1 = passedMap.get(key).toString();
	            String comp2 = val.toString();
	            
	            if (comp1.equals(comp2)){
	                passedMap.remove(key);
	                mapKeys.remove(key);
	                sortedMap.put((String)key, (Double)val);
	                break;
	            }

	        }

	    }
	    return sortedMap;
	}
	//	/**
//	 * Sorts the given hashmap by its values. Returns a linkedhashmap sorted in ascending order. 
//	 */
//	private HashMap<String, Double> sortHashMap(HashMap<String, Double> input){
//	    Map<String, Double> tempMap = new HashMap<String, Double>();
//	    for (String wsState : input.keySet()){
//	        tempMap.put(wsState,input.get(wsState));
//	    }
//
//	    List<String> mapKeys = new ArrayList<String>(tempMap.keySet());
//	    List<Double> mapValues = new ArrayList<Double>(tempMap.values());
//	    HashMap<String, Double> sortedMap = new LinkedHashMap<String, Double>();
//	    TreeSet<Double> sortedSet = new TreeSet<Double>(mapValues);
//	    Object[] sortedArray = sortedSet.toArray();
//	    int size = sortedArray.length;
//	    for (int i=0; i<size; i++){
//	        sortedMap.put(mapKeys.get(mapValues.indexOf(sortedArray[i])), 
//	                      (Double)sortedArray[i]);
//	    }
//	    return sortedMap;
//	}
	

}
