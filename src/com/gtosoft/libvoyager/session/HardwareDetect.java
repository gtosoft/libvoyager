/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import android.util.Log;

import com.gtosoft.libvoyager.android.ELMBT;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.PIDDecoder;

/**
 * Plug the ELM device into the car and start the car. Then run this method to get specific information:   
 * 	1. Whether the device is SWCAN or not.
 * 	2. Whether or not OBD2 is supported
 *  3. Whether or not sniffing (moni) is possible
 *  4. Whether or not the interface supports commands (commandSession, controlling volume and things).  
 * formerly available only within HybridSession, the logic related to detecting the hardware and vehicle capabilities are being moved to this class.
 * 
 *  * We will persist to storage, any conclusive results we find. 
 *  * We will retrieve from storage, any relevant detection results that might speed up the process of detecting hardware.
 *  
 *  * This code is not re-entrant
 *  
 */
public class HardwareDetect {
	boolean mThreadsOn = true;
	private static final boolean DEBUG = true;
	
	HashMap<String,String> mhmCapabilities = new HashMap<String,String> ();

	MonitorSession sess_monitor	= null;
	OBD2Session    sess_obd2   	= null;
	CommandSession sess_cmd 	= null;
	
	PIDDecoder 		pd			= null;
	DashDB 			ddb			= null;
	ELMBT			ebt			= null;
	
	GeneralStats  	mgStats		= new GeneralStats();

	// Key values which correspond to mhmCapabilities Keys AS WELL AS database tables. hopefully. 
	public static final String KEY_BLUETOOOTH_MAC 	= "btaddr";
	public static final String KEY_SUPPORTS_OBD 	= "supportsOBD";
	public static final String KEY_SUPPORTS_MONI 	= "supportsMoni";
	public static final String KEY_SUPPORTS_CMD 	= "supportsCMD";
	public static final String KEY_HW_IS_SWCAN	 	= "hardwareIsSWCAN";
	public static final String KEY_OBD_RESPONSE	 	= "responseOBD";
	public static final String KEY_MONI_RESPONSE	= "responseMONI";
	
	// For saving and restoring info. 
	public static final String PROFILE_TYPE_NAME	= "HardwareDetect";
	
	
	
	// Allows the caller to obtain references to our stuff. Hopefully they run a session detect first. 
	public MonitorSession getMonitorSession () {return sess_monitor;}
	public OBD2Session getOBD2Session () { return sess_obd2;}
	public CommandSession getCommandSession () { return sess_cmd;}
	
	
	/**
	 * This is the main entryPoint for this method. 
	 * This runs a session-detection thing. IF nothing is cached, then it can take up to 30 seconds to finish (because it will go out and get any missing information).
	 * @return - returns a Capabilities hashmap. For key values, see the public static String section of the HardwareDetect class. 
	 */
	public HashMap<String,String> getCapabilities () {

		lookupCapabilities();
		
		
		// if OBD2 support is unknown then go figure it out. Otherwise we're good. 
		if (isOBD2Supported().equals("unknown")) {
			msg ("OBD2 capabilities are unknown asof yet. Please wait while we find out if device + vehicle supports OBD2.");
			findOutIfOBDIsSupported();
			// if OBD2 is supported, this leaves the question "is moni supported too?" - we'll answer that shortly. 
		}

		// After OBD2 has been checked, we may have an OBD detect string, which can be used to pull up a full profile of supported info. 
		lookupCapabilities();
		
		// If we don't know if the hardware is SWCAN then we must have tried OBD2 and it failed, so that means it could be SWCAN, or the car is off. 
		if (isHardwareSWCAN().equals("unknown")) {
			msg ("SWCAN capabilities are unknown asof yet. Please wait while we find out if device is a SWCAN adapter");
			findOutIfHardwareIsSWCAN();
		}

		// last thing - if OBD2 is supported, then we would like to know if moni is supported too!
		if (isOBD2Supported().equals("true")) {
			findOutIfOBD2SupportsMoni();
		}

		// Make sure the data in the Capabilities hash is good before we pass it to the calling class. 
		fillInUnknownCapabilities();
		
		// Make sure all supported sessions have been instantiated before telling the calling method what is supported. Their next step will be to obtain a pointer to each of the supported sessions. 
		instantiateAllAvailableSessions();

		// And finally, since we just ran a detection script, it seems fitting to persist those capabilities to storage. 
		persistCapabilities();

		msg ("Backing up DB...");
		ddb.backupDB("HardwareDetect");
		
		return mhmCapabilities;
	}

	/**
	 * Consults the local answer cache to answer the question "Can I talk OBD2 with this hardware + Vehicle combination?"
	 * @return - a String containing one of true/false/unknown.
	 */
	public String isOBD2Supported() {
		return consultAnswerCache(KEY_SUPPORTS_OBD);
	}

	/**
	 * Consults the local answer cache to answer the question "Is this bluetooth device a SWCAN adapter?"
	 * @return - a String containing one of true/false/unknown.
	 */
	public String isHardwareSWCAN() {
		return consultAnswerCache(KEY_HW_IS_SWCAN);
	}

	/**
	 * Consults the local answer cache to answer the question "Can I sniff packets on this network?"
	 * @return - a String containing one of true/false/unknown.
	 */
	public String isMoniSupported () {
		return consultAnswerCache(KEY_SUPPORTS_MONI);
	}

	/**
	 * Consults the answer cache and returns the value if it exists or "unknown" otherwise. 
	 * This is used by our local methods to look up information. 
	 * @param whichKey
	 */
	private String consultAnswerCache (String whichKey) {
		// Consult the cache. If the key exists, it's probably either yes or no. 
		if (mhmCapabilities.containsKey(whichKey)) {
			if (mhmCapabilities.get(whichKey) == null || mhmCapabilities.get(whichKey).length() < 1) {
				// special case - invalid data in the cache. 
				if (DEBUG) msg ("Removed and replaced invalid data in the cache for key " + whichKey);
				mhmCapabilities.remove(whichKey);
				return "unknown";
			}
			
			return mhmCapabilities.get(whichKey);
		} else {
			// default case - no cached answer is available, try running session detection then try again.
			return "unknown";
		}
	}
	
	
	
	public GeneralStats getStats () {
		// Do any last minute stat calculation here. 
		return mgStats;
	}
	
	public void shutdown () {
		
		if (DEBUG) msg ("Dereferencing all member variabes for GC.");

		mThreadsOn = false;

//		// Dereference all member variables so they can be gc'd. 
//		mgStats 	= null;
//
//		ebt 		= null;
//		ddb 		= null;
//		pd 			= null;
//		
//		sess_cmd 	= null;
//		sess_obd2 	= null;
//		sess_monitor= null;
		
//		mhmCapabilities = null;
		
	}
	
	private void msg (String m) {
		Log.d("HardwareDetect",m);
	}
	
	/**
	 * Default constructor. Ensures that we get certain pieces of data necessary to do our work. 
	 */
	public HardwareDetect (ELMBT e, DashDB d, PIDDecoder p) {
		pd  = p;
		ebt = e;
		ddb = d;

		// write the peer's MAC to the capabilities hashmap. 
		mhmCapabilities.put(KEY_BLUETOOOTH_MAC, ebt.getPeerMAC());
		
		// Look up the capabilities of the current device. 
		lookupCapabilities();
	}

	
	
	
	
	/**
	 * Determine whether or not the monitor session is "working" (collecting
	 * data). We'll do this by waiting a short period and checking to see if the
	 * number of PIDs/blocks collected changes.
	 * 
	 * @return - true if the monitorsession is up and running, false otherwise.
	 */
	private String tryMoniSession(boolean isSWCAN) {
		int loopCount = 0;
		final int sleepDuration = 200;
		final int maxLoops = 5 * 8;

		// Instantiate monitorsession or if necessary re-instantiate it.
		if (DEBUG) msg ("instantiating monitor session.");
		instantiateMonitorSession(isSWCAN);
		if (DEBUG) msg ("AFTER instantiating monitor session.");

		// Loop for up to X seconds, waiting for data to trickle in.
		// new: make sure monitor session is in state 40 too. If it never reaches 40 then there was a problem so don't check pd stats. 
		// New: we're more interested in the sess state rather than # pids. Because sess needs to be allowed to reach state 40 before we check # pids.  
		while (mThreadsOn == true && sess_monitor != null && sess_monitor.getCurrentState() != 40) {
			if (DEBUG) msg ("Moni Session Loop. count=" + loopCount);
			// check and see if a timeout has elapsed. In this case we are done
			// trying, moni failed.
			if (loopCount > maxLoops) {
				mgStats.setStat("timeToDetectMoni", "FAIL (" + (loopCount * sleepDuration) + "s)" );
				// Failure!

				// Return blank string to let the calling method know that we failed.
				if (DEBUG) msg ("Moni: Timeout while detecting moni. Bailing out. ");
				return "";
			}
			if (!EasyTime.safeSleep(sleepDuration))
				break;
			mgStats.setStat("timeToDetectMoni", ">" + (loopCount * 200) + "ms");
			loopCount++;
		}

		if (DEBUG) msg ("Moni broke out of loop. state=" + sess_monitor.getCurrentState() + " pids=" + pd.getNetworkStats().getPIDCount());
		
		mgStats.setStat("timeToDetectMoni", "" + (loopCount * 200) + "ms");

		// Weird case, seems to happen as we are being shutdown/suspended.
		if (sess_monitor == null)
			return "";

		
		// Mode 40 won't be attained uneless everything's ok, such as no CAN errors. 
		// We don't want to call this moni method a success if there are CAN errors for example. Leave those tests up to the mode switch logic in sess_moni, and just check the final result here.  
		if (sess_monitor.getCurrentState() != 40)
			return "";

		// If we reached this far, then it seems the moni session is picking up
		// DATA!

		return "" + pd.getNetworkStats().getPIDCount() + " IDs in "
				+ (loopCount * sleepDuration) + "ms. moni state=" + sess_monitor.getCurrentState();
	}

	/**
	 * Attempt to open the OBD2 session against the bluetooth connection. If we
	 * are successful we will return the OBD protocol that was detected. we'll
	 * be leaving the connection resumed, so suspend it yourself if necessary.
	 * What if we're connected via SWCAN interface? Then this method should
	 * return ""; return - a blank string on FAILURE, otherwise a string
	 * describing the OBD protocol detected.
	 * * WE MUST return a string that includes the protocol string IF the obd2 session was successfully detected.  
	 */
	private String tryOBD2Session() {
		int loopCount = 0;
		final int sleepDuration = 200;
		final int maxLoops = 5 * 10; // max wait time to try and establish an OBD2 connection.

		// Instantiate if necessary.
		instantiateOBDSession();
		

		while (mThreadsOn == true && sess_obd2 != null && sess_obd2.getCurrentState() < OBD2Session.STATE_OBDCONNECTED && loopCount < maxLoops) {
			if (!EasyTime.safeSleep(sleepDuration))
				break;
			loopCount++;
		}

		if (sess_obd2 == null)
			return "";

		
		if (DEBUG) {
			msg ("TryOBD2Session Loop ended with following stats: Threadson=" + mThreadsOn + " obdstate=" + sess_obd2.getCurrentState() + " loopcount=" + loopCount + " maxloops=" + maxLoops);
		}
		
		if (sess_obd2.getCurrentState() == OBD2Session.STATE_OBDCONNECTED) {
			String ret = "";
			mgStats.setStat("timeToDetectOBD", "" + (loopCount * sleepDuration) + "ms");
			
			// Put together a message string that is unique to this vehicle. Include the protocol description from the ELM (ebt.getprotocolstring).  
			ret = "CONNECT! Proto=" + ebt.getProtocolString() + " PIDs=" + pd.getDataViaOBD("PIDS_01_0120") + " VIN=" + pd.getDataViaOBD("VIN");
			
			return ret;
		}

		mgStats.setStat("timeToDetectOBD", "FAIL(" + (loopCount * sleepDuration) + "ms)");
		return "";
	}

	/**
	 * Instantiates a nes OBD2 session. If an existing session is open, we close it (suspend, shutdown) first. 
	 * @return - true if all goes well. 
	 */
	private boolean instantiateOBDSession () {
		if (sess_obd2 != null) {
			msg ("OBD2: closing existing OBD session to open a new one");
			sess_obd2._suspend();
			sess_obd2.shutdown();
			sess_obd2 = null;
		}
		
		sess_obd2 = new OBD2Session(ebt, pd, "", ddb);
		
		pd.reset();
		
		return true;
	}

	
	private boolean instantiateMonitorSession(boolean isSWCAN) {

		// TODO: If there's an existing monisession with same SWCAN status then
		// don't re-instantiate.

		if (sess_monitor != null) {
			msg("Moni: Shutting down old instance of Monisession before instantiating new.");
			sess_monitor._suspend();
			sess_monitor.shutdown();
			sess_monitor = null;
		}

		if (isSWCAN == true) {
			sess_monitor = new MonitorSession(ebt,MonitorSession.INTERFACE_SWCAN, pd);
		} else {
			sess_monitor = new MonitorSession(ebt,MonitorSession.INTERFACE_STANDARD, pd);
		}

		pd.reset();

		return true;
	}

	/**
	 * talks to device to find out if OBD2 is supported. 
	 * Saves result into mhmCapabilities hashmap, which makes the information available to "isBLAH" public methods.  
	 */
	private void findOutIfOBDIsSupported () {
		// find out whether OBD2 is supported. 
		String obd_support_string = tryOBD2Session();
		// make sure to suspend the session after using it. 
		if (sess_obd2 != null) sess_obd2._suspend();

		if (obd_support_string.length() == 0) {
			// OBD support is inconclusive. Don't say no here because the car might be off. 
			mhmCapabilities.put(KEY_SUPPORTS_OBD, "unknown");
		} else {
			// Valid OBD2 response observed. make a note of all conclusions we can conclusively draw. 
			mhmCapabilities.put(KEY_OBD_RESPONSE, obd_support_string);
			mhmCapabilities.put(KEY_SUPPORTS_OBD,"true");
			mhmCapabilities.put(KEY_SUPPORTS_CMD,"false");
			mhmCapabilities.put(KEY_HW_IS_SWCAN,"false");
		}
	}

	private void findOutIfHardwareIsSWCAN () {
		
		// tryMoniSession takes one arg, if it's set to "true" then moni is set up in low-speed CAN mode. if false, it sets up for high speed monitor. 
		String ret = tryMoniSession(true);

		// Make sure and suspend it afterwards. 
		if (DEBUG) msg ("Moni support is: " + ret + " suspending now...");
		sess_monitor._suspend();
		if (DEBUG) msg ("Moni suspend - complete.");
		
		if (ret.length() == 0) {
			// SWCAN Moni not supported... Either device not SWCAN or vehicle not connected. INCONCLUSIVE. 
			mgStats.setStat("support.swcan.reason","no 33k packets observed");
		} else {
			mgStats.setStat("support.swcan.reason","33k packets observed: " + ret);
			// Valid moni response received, which tells us the device IS SWCAN! Log all affirmations below: 
			mhmCapabilities.put(KEY_MONI_RESPONSE, ret);
			mhmCapabilities.put(KEY_SUPPORTS_OBD,"false");
			mhmCapabilities.put(KEY_SUPPORTS_CMD,"true");
			mhmCapabilities.put(KEY_HW_IS_SWCAN,"true");
			mhmCapabilities.put(KEY_SUPPORTS_MONI,"true");
		}
		
	}

	
	
	/**
	 * This method gets called in the case where OBD2 support is affirmed and we want to know if sniffing is also possible. 
	 */
	private void findOutIfOBD2SupportsMoni() {

		// Sanity check/s. 
		if (!mhmCapabilities.containsKey(KEY_OBD_RESPONSE)) {
			msg ("ERROR: unable to find out if OBD2 supports moni because obd response is null/missing from hm");
			mgStats.setStat("support.obd2.reason", "no capabilities entry");
			return;
		}
		
		if (mhmCapabilities.get(KEY_OBD_RESPONSE).contains("CAN")) {
			mhmCapabilities.put(KEY_SUPPORTS_MONI,"true");
			mgStats.setStat("support.obd2.reason", "OBD Response contains \"CAN\"" + ": " + mhmCapabilities.get(KEY_OBD_RESPONSE));
		} else {
			// based on the obd2 protocol string we're going to say sniffing isn't possible with this device.
			String obdChoiceReason = "Based on OBD2 response string, we don't think sniffing is possible. response string was: " + mhmCapabilities.get(KEY_OBD_RESPONSE);
			if (DEBUG) msg (obdChoiceReason);
			mhmCapabilities.put(KEY_SUPPORTS_MONI,"false");
			mgStats.setStat("support.obd2.reason",obdChoiceReason);
		}
		
	}
	

	/**
	 * make sure all keys exist in the hashmap so that there are no keys which are empty.
	 * New: We also check that the values are valid. if not, we set them to be valid.  
	 */
	private void fillInUnknownCapabilities () {
	
		String keys [] = {
				KEY_HW_IS_SWCAN,
				KEY_SUPPORTS_CMD,
				KEY_SUPPORTS_MONI,
				KEY_SUPPORTS_OBD
		};
		
		for (int i=0;i<keys.length;i++) {
			if (!mhmCapabilities.containsKey(keys[i]) || isSupportValueValid(keys[i])) {
				if (!isSupportValueValid(keys[i])) 
					if (DEBUG) msg ("WARNING: Cleaning up invalid supported value of " + keys[i]);
				mhmCapabilities.put(keys[i], "unknown");
			}
			
		}

		
	}

	/**
	 * @param supportValue
	 * @return - returns true if supportValue is true/false/unknown, false otherwise. 
	 */
	private boolean isSupportValueValid (String supportValue) {
		
		if (supportValue == null) return false;
		
		if (supportValue.equals("true")) return true;
		if (supportValue.equals("false")) return true;
		if (supportValue.equals("unknown")) return true;
		
		return false;
	}
	
	/**
	 * 	for each session type, if support is "true" (not unknown or no), then make sure the session is 
	 *	instantiated. When we look up hardware support from the DB it allows us to skip the instantiation 
	 *	during detection phase, so this method will let us instantiate those sessions for the parent class. 
	 */
	private void instantiateAllAvailableSessions() {
		
		// OBD2 Session
		if (sess_obd2 == null && isOBD2Supported().equals("true")) {
			sess_obd2 = new OBD2Session(ebt, pd, "", ddb);
		}
		

		// Command Session
		if (sess_cmd == null && isMoniSupported().equals("true") && isHardwareSWCAN().equals("true")) {
			sess_cmd = new CommandSession(ebt, ddb);
			// suspend it so it doesn't interfere with other sessions. 
			sess_cmd.suspend();
		}

		// MonitorSession
		if (sess_monitor == null && isMoniSupported().equals("true")) {
			
			// instantiate a moni session either HSCAN(standard) or SWCAN(GMLAN single-wire). 
			if (isHardwareSWCAN().equals("true"))
				sess_monitor = new MonitorSession(ebt, MonitorSession.INTERFACE_SWCAN, pd);
			else
				sess_monitor = new MonitorSession(ebt, MonitorSession.INTERFACE_STANDARD, pd);
			
		}
		
		// leave stuff in suspended state before returning results. 
		if (sess_cmd != null)		sess_cmd.suspend();
		if (sess_obd2 != null) 		sess_obd2._suspend();
		if (sess_monitor != null)	sess_monitor._suspend();
		
		
//		// leave one of these two unsuspended based on what's supported. 
//		if (!isOBD2Supported().equals("true") && sess_obd2 	  != null) sess_obd2._suspend();
//		if (!isMoniSupported().equals("true") && sess_monitor != null) sess_monitor._suspend(); 
		

		
	}

	/**
	 * Saves local capabilities hashmap data to persistent storage. 
	 */
	private void persistCapabilities() {
		saveSWCANTypeToDDB();
//		saveDetailedCapabilities();
	}

	/**
	 * Pulls capabilities from persistent storage, into our hashmap.
	 * Can be used in two instances: 
	 * 1. When just the peer btaddr is known, we may be able to tell if hardware is SWCAN or not. 
	 * 2. After OBD2 is detected, we can use the detected string to identify the vehicle and hardware and get full capabilities from persistent storage.   
	 */
	private void lookupCapabilities() {
		retrieveSWCANTypeFromDDB();
//		retrieveDetailedCapabilities();

		// make sure the hashmap is clean. 
		fillInUnknownCapabilities();

		// Make sure we didn't blow away important information
		mhmCapabilities.put(KEY_BLUETOOOTH_MAC, ebt.getPeerMAC());
	}
	
//	/**
//	 * Called internally.
//	 */
//	private void saveDetailedCapabilities() {
//		// Save capabilities to persistant storage. 
//		ddb.saveHashmapToStorage("HardwareCapabilities", "" + mhmCapabilities.get(KEY_OBD_RESPONSE), mhmCapabilities);
//	}
	
//	/**
//	 * Called internally.
//	 */
//	private void retrieveDetailedCapabilities() {
//		HashMap<String,String> hmCaps = new HashMap<String,String> ();
//		
//		// If hardware is OBD2 and valid response, then use that response to pull in details hardware information into our hashmap. 
//		if (isOBD2Supported().equals("true")) {
//			// Retrieve hardware capabilities as related to the current vehicle. 
//			hmCaps = ddb.restoreHashmapFromStorage("HardwareCapabilities", "" + mhmCapabilities.get(KEY_OBD_RESPONSE));
//		
//if (DEBUG) msg ("About to pull in capabilities details from storage. mhmcapabilities=" + getHashMapString (mhmCapabilities) + " fromProfiles=" + getHashMapString (hmCaps));
//			hmCaps = mhmCapabilities;
//			
//			// Since OBD2 is supported, then SWCAN must NOT be supported. 
//			mhmCapabilities.put(KEY_HW_IS_SWCAN, "false");
//		}
//	}

	public static String getHashMapString (HashMap<String,String> hm) {
		String ret = "";
		
		Set<String> s = new TreeSet<String> (hm.keySet());
		Iterator<String> i = s.iterator();
		
		String thisKey = "";
		String thisVal = "";
		while (i.hasNext()) {
			thisKey = i.next();
			thisVal = hm.get(thisKey);
			ret = ret + thisKey + "=" + thisVal + " ";
		}
		
		
		return ret;
	}
	



	private void saveSWCANTypeToDDB() {
		ddb.setProfileValue(
				"HardwareCapabilities", 
				"HardwareSWCAN", 
				mhmCapabilities.get(KEY_BLUETOOOTH_MAC), 
				"" + mhmCapabilities.get(KEY_HW_IS_SWCAN)
				);
	}
	
	/**
	 * called internally.
	 */
	private void retrieveSWCANTypeFromDDB() {
		String hardwareType = "";

		// append it, in case it's null. 
		hardwareType += ddb.getProfileValue(
				"HardwareCapabilities", 
				"HardwareSWCAN", 
				mhmCapabilities.get(KEY_BLUETOOOTH_MAC)
				);

		if (DEBUG) msg ("Retrieved SWCAN support string from database, SWCANSupport=" + hardwareType); 

		
		// Take the value we just read from profile and put it in the capabilities hashmap. 
		if (hardwareType.length() > 0) {
			// use value obtained from profile record. 
			mhmCapabilities.put(KEY_HW_IS_SWCAN, hardwareType);
		}
		
		// If it's swcan, then it's sniffable and commandable. 
		if (isHardwareSWCAN().equals("true")) {
			mhmCapabilities.put(KEY_SUPPORTS_MONI, "true");
			mhmCapabilities.put(KEY_SUPPORTS_CMD, "true");
		}
		
	}
	
	/**
	 * @return - returns true if hardware detection info is valid. false otherwise. 
	 */
	public boolean isDetectionValid() {

		if (isHardwareSWCAN().equals("true") || isOBD2Supported().equals("true"))
			return true;
		
		return false;
	}
	
	
}
