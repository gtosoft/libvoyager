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

import android.bluetooth.BluetoothAdapter;
import android.util.Log;

import com.gtosoft.libvoyager.android.ELMBT;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.OOBMessageTypes;
import com.gtosoft.libvoyager.util.PIDDecoder;
import com.gtosoft.libvoyager.util.RoutineScan;


/**
 * Presently, the PIDDecoder is tightly woven with just one device. So if
 * instantiating multiple devices, it's necessary to instantiate new PIDDecoder
 * instances. For example, the new passive network detection, it locks in the
 * GLOBAL NetworkID variable in PIDDecoder, so other device types won't work in
 * that instance.
 * @author brad
 */

public class HybridSession {

	HardwareDetect mHardwareInfo = null;
	
	boolean mDetectionSuccessful = false;
	// false until we are sure that detection was completed successfully. 

	// crude synchronization flag - if a detection routine is running then this should be true, otherwise false. 
	// Primarily used because elmbt may send state change notifications several times when it connects, so we don't want to start parallel session detections. 
	boolean mIsSessionDetectionRunning = false;

	boolean mSuspendSessionOOBEvents = false;
	
	int mDPNHits = 0;
	
	final boolean DEBUG = false;

	// used locally. Not just for threads, but for loops. When we shutdown() we
	// set this false.
	boolean mThreadsOn = true;

	EventCallback mecbMsg = null;

	// These are the various session types which can be current at any given
	// time.
	public static final int SESSION_TYPE_UNDEFINED = 0;
	public static final int SESSION_TYPE_COMMAND = 1;
	public static final int SESSION_TYPE_OBD2 = 2;
	public static final int SESSION_TYPE_MONITOR = 3;

	// To store our stats, and the stats of all of our children.
	GeneralStats mgStats = new GeneralStats();

	// Set to SESSION_TYPE_BLAH depending on which session is current.
	int mActiveSession = 0;

	// define session objects.
	OBD2Session sess_obd2 = null;
	CommandSession sess_command = null;
	MonitorSession sess_monitor = null;

	// a routine scan helper class that makes the OBD do requests at a regular interval. 
	RoutineScan mRoutineScan = null;
	
	// This hands messages up the chain to our parent. These messages are of an "out of band" nature.  
	EventCallback mOOBDataHandler = null;
	
	// I/O layer.
	ELMBT ebt = null;
	DashDB ddb = null;
	PIDDecoder pd = null;


	/**
	 * Constructor.
	 * 
	 * @param btAdapter - a reference to the bluetooth adapter.
	 * @param btAddr    - Bluetooth address of the peer we want to connect to.
	 * @param dashDB    - an instance of DashDB.
	 */
	public HybridSession(BluetoothAdapter btAdapter, String btAddr, DashDB dashDB, EventCallback OOBEventCallback) {

		// Instantiate a new bluetooth object. It will start trying to connect
		// right away.
		ebt = new ELMBT(btAdapter, btAddr);

		// grab a reference to DDB.
		ddb = dashDB;

		// Instantiate the PIDDecodere with a "space holder" network. We must
		// change this later if using passive mode.
		pd = new PIDDecoder(ddb);

		// Get ready to detect some hardware!
		mHardwareInfo = new HardwareDetect(ebt, ddb, pd);
		
		// Registers the callback which we will use to pass information up to the higher layer(s). 
		registerOOBHandler(OOBEventCallback);

		// set stats to defaults. 
		setAllStatsToDefaults();

		registerCallbacks();

	}

	private void setAllStatsToDefaults() {
		mgStats.setStat("activeSession", getCurrentSessionTypeByName());
	}
	
	/**
	 * Possibly not a fast method... combines stats from all children and
	 * returns everything in a thread-safe hashmap.
	 * 
	 * @return - true on success, false otherwise.
	 */
	public boolean collectChildStats() {

		// DB layer.
		if (ddb != null)
			mgStats.merge("ddb", ddb.getStats());

		// IO layer
		if (ebt != null)
			mgStats.merge("ebt", ebt.getStats());

		// PIDDecoder.
		if (pd != null)
			mgStats.merge("dakota", pd.getStats());
		
		// routine Scan
		if (mRoutineScan != null)
			mgStats.merge("rScan", mRoutineScan.getStats());
			
		// Session layers
		if (sess_obd2 != null)
			mgStats.merge("session.obd2", sess_obd2.getStats());
		if (sess_command != null)
			mgStats.merge("session.command", sess_command.getStats());
		if (sess_monitor != null)
			mgStats.merge("session.monitor", sess_monitor.getStats());
		
		// session detect class
		if (mHardwareInfo != null)
			mgStats.merge("hardwareDetect", mHardwareInfo.getStats());

		return true;
	}
	
	/**
	 * Collect child stats and our own and return it all rolled up into a single genstats instance. 
	 * @return - returns a genstats instance of us and everything beneath us. 
	 */
	public GeneralStats getStats () {
		collectChildStats();
		return mgStats;
	}

	private void msg(String m) {

		if (mecbMsg != null)
			mecbMsg.onNewMessageArrived(m);
		else
			Log.d("HS", m);

	}

	public HashMap<String, String> getStatsAsHashMap() {
		// Go out and collect/update stats from all our children.
		collectChildStats();
		// return the stats, as a hashmap.
		return mgStats.getStatsAsHashmap();
	}

	/**
	 * Call this method to tell us you want to get rid of this instance. After
	 * you run this method, don't access this class.
	 */
	public void shutdown() {

		// this should calm down any local loops which might otherwise continue
		// to loop.
		mThreadsOn = false;
		// sleep a tad, to give loops a bit of time to shut down. That way as we
		// deallocate objects below, our loops aren't as likely to reference
		// them during a shutdown.
		EasyTime.safeSleep(500);

		if (mRoutineScan != null)
			mRoutineScan.shutdown();
		
		if (mHardwareInfo != null)
			mHardwareInfo.shutdown();

		if (ebt != null)
			ebt.shutdown();

		if (pd != null)
			pd.shutdown();

		if (sess_obd2 != null)
			sess_obd2.shutdown();
		if (sess_command != null)
			sess_command.shutdown();
		if (sess_monitor != null)
			sess_monitor.shutdown();

		if (ddb != null)
			ddb.shutdown();
	}

	EventCallback messageCallback = new com.gtosoft.libvoyager.util.EventCallback() {
		@Override
		public void onNewMessageArrived(String message) {
			msg("HS: " + message);
		}
	};

	/**
	 * Defines what to do when the state of the I/O (EBT) layer changes.
	 * Basically when the bluetooth connection is established, we launch right
	 * into a detection algorithm!
	 */
	EventCallback mecbIOStateChangeCallback = new EventCallback() {
		@Override
		public void onStateChange(int oldState, int newState) {
			// Fire off a message to the upper layers, letting them know of the state change. 
			// Tell them before we take action, so that they can do stuff with the objects before we blow them away. 
			sendOOBMessage(OOBMessageTypes.IO_STATE_CHANGE, "" + newState);
			
			// Are we going from disconnected to connected state?
			if (oldState <= 0 && newState > 0) {
				// just connected
			} else {
				// just disconnected.
				postDisconnectCleanup ();
				// close PIDDecoder?
			}

		};
	};
	
	/**
	 * Do things to clean up 
	 */
	private void postDisconnectCleanup() {

		msg ("We just disconnected?");
//		if (mHardwareInfo != null) {
//			mHardwareInfo.shutdown();
//			mHardwareInfo = null;
//		}
	}
	
	/**
	 * Defines what to do when the state of the I/O (EBT) layer changes.
	 * Basically when the bluetooth connection is established, we launch right
	 * into a detection algorithm!
	 */
	EventCallback mecbSessionChangeCallback = new EventCallback() {
		@Override
		public void onStateChange(int oldState, int newState) {
			// pass along the news to our friends. 
			sendOOBMessage(OOBMessageTypes.SESSION_STATE_CHANGE, "" + newState);
		}
	};

	
	EventCallback mLocalOOBHandler = new EventCallback () {
		public void onOOBDataArrived(String dataName, String dataValue) {
			// TODO: Do something based on the event?
			
			// retransmit it up the chain.
			sendOOBMessage(dataName, dataValue);
		}
	};
	

	/**
	 * Override onDPArrived method of EventCallback and register with us to get a call any time a DPN is decoded.
	 * @return - true on success. 
	 */
	public boolean registerDPArrivedCallback(EventCallback DPArrivedCallback) {
		if (pd == null) {
			msg ("ERROR: Unable to register DPN callback because pd not available. ");
			return false;
		}

		pd.registerOnDPArrivedCallback(DPArrivedCallback);

		return true;
	}


	/**
	 * Returns true if we're ready for use. false otherwise. 
	 * @return
	 */
	public boolean isOBDReady () {

		// if OBD2 is current, and it's active, and is connected...
		if (sess_obd2 != null && mActiveSession == SESSION_TYPE_OBD2 && sess_obd2.getCurrentState() == OBD2Session.STATE_OBDCONNECTED)
			return true;
		
		// if session detection took place. 
		if (mDetectionSuccessful == true)
			return true;
		
		return false;
	}
	
	/**
	 * For a list of available session types, see the static final int's in HybridSession starting with SESSION_TYPE...  
	 * @return - returns an integer describing which session is currently active, based on the mActiveSession global variable. 
	 */
	public int getCurrentSessionType () {
		return mActiveSession;
	}


	private void suspendSessionOOBEvents() {
		mSuspendSessionOOBEvents = true;
	}
	
	private void unSuspendSessionOOBEvents(){
		mSuspendSessionOOBEvents = false;
	}
	
	private boolean areSessionOOBEventsSuspended() {
		return mSuspendSessionOOBEvents;
	}
	
	/**
	 * * This is the main use of this class! It detects which sessions are available. It utilizes the profile table to speed up the process by remembering what is supported from last time. 
	 * * New: we suspend and unsuspend oob session events for the duration of detection - this prevents problems with other folks taking action when session state changes. 
	 * @return - true on success. 
	 */
	public synchronized boolean runSessionDetection() {
		
		suspendSessionOOBEvents();
		
		if (mHardwareInfo == null) {
			if (DEBUG) msg ("BLOCKED request to detect hardware - no mhardwareinfo is defined yet.");
			unSuspendSessionOOBEvents();
			return false;
		}
		
		HashMap<String,String> hmCapabilities;
		
		msg ("Attempting to get capabilities!");
		hmCapabilities = mHardwareInfo.getCapabilities();
		// we won't necessarily use the capabilities hashmap but in a second we'll make individual calls to methods in hardwareinfo class to find capabilities. 
		
		// Assign handles from hDetect class over to us. some or all of these may be null if not supported or detected. 
		sess_obd2 	 = mHardwareInfo.getOBD2Session();
		sess_monitor = mHardwareInfo.getMonitorSession();
		sess_command = mHardwareInfo.getCommandSession();

		msg ("Got capabilities!");

		// Dump Capabilities as diag msg. 
		String iterationReportString = getCapabilitiesString () ;
		
		msg (iterationReportString);
		
		sendOOBMessage(OOBMessageTypes.AUTODETECT_SUMMARY, iterationReportString);
		
		unSuspendSessionOOBEvents();
		// all done. return true if successful, false otherwise. 
		if (mHardwareInfo.isDetectionValid())
			return true;
		else
			return false;
		
	}
	
	public HardwareDetect getHardwareDetectData () {
		return mHardwareInfo;
	}

	public String getCapabilitiesString () {

		String ret = "CAPABILITIES: MAC=" + ebt.getPeerMAC() + 
		" SWCAN=" + mHardwareInfo.isHardwareSWCAN() + 
		" OBD="   + mHardwareInfo.isOBD2Supported() + 
		" MONI="  + mHardwareInfo.isMoniSupported() ;

		if (mHardwareInfo.isDetectionValid()) 
			return ret;
		else 
			return "undetected";
		
	}
	
	/**
	 *  
	 * @return - True if a detection was executed and successful. false otherwise. 
	 */
	public boolean isDetectionValid () {
		return mHardwareInfo.isDetectionValid();
	}
	
	public RoutineScan getRoutineScan () {
		return mRoutineScan;
	}
	
	private void sendOOBMessage (String dataName, String dataValue) {
		if (mOOBDataHandler == null)
			return;

		if (dataName.equals(OOBMessageTypes.SESSION_STATE_CHANGE) && areSessionOOBEventsSuspended() == true) {
			
			if (DEBUG) msg ("Supressed one Message: Session state changed, value=" + dataValue);
			return;
			
		}
		
		mOOBDataHandler.onOOBDataArrived(dataName, dataValue);
	}

	private void registerCallbacks() {

		if (ebt != null) {
			ebt.registerMessageCallback(messageCallback);
			ebt.registerStateChangeCallback(mecbIOStateChangeCallback);
			ebt.registerOOBCallback(mLocalOOBHandler);
		}

		if (pd != null) {
			pd.registerNewMessageHandler(messageCallback);
		}

		if (sess_obd2 != null) {
			sess_obd2.registerMessageCallback(messageCallback);
			sess_obd2.registerOnStateChangedListener(mecbSessionChangeCallback);
		}
		
		if (sess_command != null) {
			sess_command.registerMsgCallback(messageCallback);
			sess_command.registerStateChangeCallback(mecbSessionChangeCallback);
		}

		if (sess_monitor != null) {
			sess_monitor.registerMessageCallback(messageCallback);
			sess_monitor.registerMonitorStateChangeListener(mecbSessionChangeCallback);
		}

	}

	/**
	 * Use this method to switch from one session to another. It will block
	 * until the transition has been successfully made.
	 * 
	 * @return
	 */
	public synchronized boolean setActiveSession(int newSession) {
		
		if (mHardwareInfo == null || mHardwareInfo.isDetectionValid() != true) {
			msg ("ERROR: unable to set active session to " + newSession + " as requested because session detection is not yet successful.");
			return false;
		}
		
			
		int oldSession = mActiveSession;

		long startTime = EasyTime.getUnixTime();

//		// Weird case.
//		if (oldSession == newSession) {
//			msg("Session switch requested but old new session matches old session so not doing anything.");
//			return true;
//		}

		if (DEBUG) msg("Session Switch - initiating switch from " + oldSession + " to " + newSession);

		// shut down / suspend the current session
		if (oldSession != newSession) {
			switch (oldSession) {
				case 0: 
					// do nothing. this is like a holding pattern.
					break;
				case SESSION_TYPE_OBD2:
					if (sess_obd2 != null)
						sess_obd2._suspend();
					
					// shutdown the routine scan. 
					if (mRoutineScan != null)  {
						if (DEBUG) msg ("Shutting down routinescan");
						mRoutineScan.shutdown();
						mRoutineScan = null;
					}
				
					break;
				case SESSION_TYPE_MONITOR:
					if (sess_monitor != null)
						sess_monitor._suspend();
					break;
				case SESSION_TYPE_COMMAND:
					if (sess_command != null)
						sess_command.suspend();
					break;
				default:
					if (DEBUG) msg("Warning: Unknown oldSession type " + oldSession + " so not suspending it.");
					break;
			}
		}// end of "if the old session type doesn't match the new session type".

		
		// resume the new session type.
		switch (newSession) {
			case 0: 
				// do nothing. this is like a holding pattern.
				break;
			case SESSION_TYPE_COMMAND:
				// instantiate for first time if necessary
				if (sess_command == null) {
					msg ("ERROR: command session not instantiated. ");
				}
				
				if (sess_command != null) {
					sess_command.resume();
					// TODO: check the link and if there is no traffic, send a wake-up. 
					//sess_command.wakeUpAllNetworks();
				}
				else {
					return false;
				}
				
				break;
			case SESSION_TYPE_MONITOR:
				sess_monitor.resume();

				break;
			case SESSION_TYPE_OBD2:
				sess_obd2.resume();
				
				// Make sure the OBD2 session is registered with the pdidecoder. 
				pd.registerOBD2SessionLayer(sess_obd2);

				//[re]create Routine Scan. Should we be doing this at the obd2session level?
				if (mRoutineScan != null) mRoutineScan.shutdown();
				mRoutineScan = new RoutineScan(sess_obd2, pd);
				
				break;
			default:
				if (DEBUG)
					msg("ERROR: request for nonexistent session type of " + newSession + " resulted in NO active session. ");
				break;
		}

		// register to get messages from all sessions, some of which might have just recently been defined. 
		registerCallbacks();

		mActiveSession = newSession;
		if (DEBUG) msg("Session Switch - complete, new session is " + newSession);

		// kick off an event. 
		sendOOBMessage(OOBMessageTypes.SESSION_SWITCHED, "" + newSession);
		mgStats.setStat("activeSession", getCurrentSessionTypeByName());

		
		// a sanity check type of thing. Since we're geting rid of debug
		// messages in general, this will be a nice verification step to spot
		// problem behavior.
		long elapsedTime = EasyTime.getUnixTime() - startTime;
		if (elapsedTime > 5) {
			msg("WARNING: session state change took " + elapsedTime + " seconds to switch from " + oldSession + " to " + newSession);
		}

		
		return true;
	}// end of session switch method. 

	/**
	 * @return - returns a string that describes the current active state. Examples are: MONITOR, OBD2, COMMAND. 
	 */
	public String getCurrentSessionTypeByName () {
		String ret = "";
		
		if (getCurrentSessionType() == SESSION_TYPE_COMMAND) 
			ret = ret + "COMMAND";
		if (getCurrentSessionType() == SESSION_TYPE_MONITOR) 
			ret = ret + "MONITOR";
		if (getCurrentSessionType() == SESSION_TYPE_OBD2) 
			ret = ret + "OBD2";
		if (getCurrentSessionType() == SESSION_TYPE_UNDEFINED) 
			ret = ret + "UNDEFINED";
		if (isHardwareSWCAN() == true) 
			ret = ret + "(SWCAN)";
		
		return ret;
	}

	public boolean isHardwareSWCAN() {
		
		if (mHardwareInfo != null && mHardwareInfo.isHardwareSWCAN().equals("true")) {
			return true;
		} else {
			return false;
		}
	}
	

	public boolean registerMsgCallback(EventCallback ecbMsg) {

		// already registered.
		if (mecbMsg != null)
			return false;

		// new registration.
		mecbMsg = ecbMsg;

		// success.
		return true;
	}


	/**
	 * Allow upper layers to register an event listener to be notified of out of band information.
	 * We pass information to the upper layers via this callback, using our local method sendOOB....   
	 * @param newOOBHandler
	 */
	public void registerOOBHandler (EventCallback newOOBHandler) {
		if (mOOBDataHandler != null) {
			if (DEBUG) msg ("HS - overwriting existing OOB data handler");
		}
		
		mOOBDataHandler = newOOBHandler;
		
		if (DEBUG) mOOBDataHandler.onOOBDataArrived("special message from hybridsession", "blahbittyblah");
	}

	public String getAllStatsAsString () {
		String allStats = "";
		
		HashMap<String,String> hmStats = getStatsAsHashMap();
		
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

	public ELMBT getEBT () {
		return ebt;
	}

	public OBD2Session getOBDSession () {
		return sess_obd2;
	}

	public PIDDecoder getPIDDecoder () {
		return pd;
	}

	public MonitorSession getMonitorSession () {
		return sess_monitor;
	}

	public CommandSession getCommandSession () {
		return sess_command;
	}
	
	/**
	 * returns true if the hardware supports sniffing. 
	 * @return
	 */
	public boolean isHardwareSniffable () {

		if (mHardwareInfo != null && mHardwareInfo.isMoniSupported().equals("true")) {
			return true;
		} else {
			return false;
		}
		
	}

	/**
	 * Sets the number of milliseconds to pause in between iterations of the routine scan (used only in OBD2 mode). 
	 * @param delayMillis
	 */
	public void setRoutineScanDelay (int delayMillis) {
		if (mRoutineScan != null) {
			mRoutineScan.setScanLoopDelay(delayMillis);
		} else {
			if (DEBUG) msg ("FAILED to set routine scan loop delay because it's not instantiated yet.");
		}
	}
	
}
