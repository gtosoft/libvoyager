/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import com.gtosoft.libvoyager.android.ELMBT;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GTOMath;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.PIDDecoder;

import android.util.Log;


/**
 * - A Session layer which connects ELMBT, with a higher level class, such as
 * PIDDecoder. - We will maintain an open line of OBD2 communication so that
 * bluetooth commands can be sent. - When an OBD2 response is received, we'll
 * leave the decoding up to the PIDDecoder class. - PIDDecoder will probably
 * even request OBD data through us. - Usage: instantiate this class with an
 * alrady -configured ELMBT, and an already-instantiated PIDDecoder. - And make
 * sure to shut down this class by calling its shutdown() - otherwise threads
 * will continue to live beyond our lifetime. - To get data, make a request
 * through PIDDecoder. PIDDecoder will use a callback, which we register, to
 * request us to get data from the bluetooth. We will in turn get that data and
 * pass it back to PIDDecoder for decoding.
 */

// A typical implementation involves a PIDDecoder asking an OBD2Session to
// perform an OBD request and get the response. The PIDDecoder then translates
// the response(s). This is all initiated by a call to
// PIDDecoder.getDataByName("RPM") for example.

public class OBD2Session {
	final boolean DEBUG = false;

	// This will be set to the correct value as soon as we detect that we are in a connected state. 
	int mELMProtocol = 0;
	
	String mOBDDetectedProtocol = "";
	// To store all any and all stats we might want to make available upstream
	// (to the hybridSession/manager).
	GeneralStats mgStats = new GeneralStats();

	// Define all possible states which we may be in at any given time.
	public static final int STATE_UNINITIALIZED = 0;
	public static final int STATE_BTCONNECTED = 10;
	public static final int STATE_BTCONFIGURED = 20;
	public static final int STATE_OBDCONNECTED = 40;
	// (once we've reached obdConnected, there's no higher we need to go.)

	EventCallback mMessageCallback = null;
	EventCallback mStateChangeCallback = null;

	// We have our own reference to Dash DB because if we get re-created due to
	// change in network, we must create a new dashDB with blank hashmaps.
	// Otherwise we get cached data for the last network.
	DashDB ddb = null;

	// Current state of affairs in the OBD2Session world. Always corresponds to
	// one of the STATE constants defined above.
	int mCurrentState = 0;

	// true as long as threads should be running. Set to false during shutdown.
	boolean mThreadsOn = true;

	// the thread that runs and manages the state. No other methods should touch
	// ELMBT.
	Thread mtStateManagementThread = null;

	// Holds a reference to the ElmBT that was created for us to communicate
	// across.
	ELMBT ebt = null;

	// Defines which network we're on. this is quite important.
	// Only allowed to be set during instantiation.
	// Once it's set, things get cached and it would be a mess if the network#
	// changed.
	// old cached data would relate to one network and the rest to the new
	// network.
	// String mNetwork = "00";

	// The parent defines this and passes us the reference. It may be a
	// PIDDecoder which is also used by other sessions, such as the monitor
	// seession layer.
	PIDDecoder piddecoder = null;

	boolean mInitialInitSent = false;
	String[] mInitInitial = { "AT WS" };
	String[] mInitSuspend = {};
	String[] mInitResume = { "AT CAF 1", "AT H 1" };

	// public String getNetwork() {
	// return mNetwork;
	// }

	public int getCurrentState() {
		return mCurrentState;
	}

	/**
	 * @return - returns a string representation of the current state.
	 */
	public String getCurrentStateByName() {
		// static final int STATE_UNINITIALIZED = 0;
		// static final int STATE_BTCONNECTED = 10;
		// static final int STATE_BTCONFIGURED = 20;
		// static final int STATE_OBDCONNECTED = 40;

		String stateByName = "";

		switch (getCurrentState()) {
		case STATE_UNINITIALIZED:
			stateByName = "UNINITIALIZED";
			break;
		case STATE_BTCONNECTED:
			stateByName = "BT_CONNECTED";
			break;
		case STATE_BTCONFIGURED:
			stateByName = "BT_CONFIGURED";
			break;
		case STATE_OBDCONNECTED:
			stateByName = "OBD_CONNECTED";
			break;
		default:
			stateByName = "UNKNOWN:" + getCurrentState();
		}

		return stateByName;
	}

	/**
	 * defines and starts the state management thread.
	 * 
	 * @return - returns true if we successfully started the thread. False if it
	 *         was already running or we couldn't start it.
	 */
	public boolean startStateManagementThread() {
		if (mtStateManagementThread != null) {
			if (DEBUG) msg ("RE-creating state management thread.");
			mtStateManagementThread.interrupt();
			mtStateManagementThread = null;
//			return false; 
		}

		mtStateManagementThread = new Thread() {
			public void run() {
				while (mThreadsOn == true) {

					if (ebt != null && ebt.isConnected() != true)
						setCurrentState(STATE_UNINITIALIZED);

					// Global state management functionality happens here. Check
					// state, if not to our liking then do what we can to get
					// into a better state.
					if (ebt != null && getCurrentState() != STATE_OBDCONNECTED) {

						// do what we can to get into the right state
						long startTime = EasyTime.getUnixTime();
						int newState = enterStateForOBD();
						long duration = EasyTime.getUnixTime() - startTime;
						mgStats.setStat("timeToSetupOBD", "" + duration + "s");

						// let the setCurrent method decide whether state has
						// changed or not.
						setCurrentState(newState);
					}

					// Nap for a second or so to give things a break between
					// retries.
					try {
						Thread.sleep(2 * 1000);
					} catch (InterruptedException e) {
						// if we're interrupted, break out of this while loop.
						break;
					}
				}// end of while() threads on.
				if (DEBUG)
					msg("The state Management Thread has terminated.");

				// Set the management thread to null. This allows for us to
				// re-run this method later on AND so other aspects of this
				// class know the thread has DIED or is dead.
				mtStateManagementThread = null;

			}// end of run()
		};// end of state management thread class definition.

		mtStateManagementThread.start();
		return true;
	}

	private void setCurrentState(int newState) {

		int oldState = mCurrentState;

		// Notice that we're first setting the global state variable THEN
		// notifying people. This is important.
		if (newState != oldState) {
			stateChanged(oldState, newState);
		}

		// Important! Set the "newstate" variable AFTER firing the events above.
		// The reason being, that folks checking "getCurrentState()" should only
		// see the new state value AFTER we do things like collect data in the
		// event changer methods.
		mCurrentState = newState;

	}

	/**
	 * If the global state management thread detects a change in state, it kicks
	 * off this method. This method gets called BEFORE the global state variable
	 * is updated, so use the oldstate and newstate variables to know the
	 * old/new states.
	 * 
	 * @param oldState
	 * @param newState
	 */
	private void stateChanged(int oldState, int newState) {
		if (DEBUG)
			msg("OBD2Session(): State changed: old=" + oldState + " new="
					+ newState);

		// fire off the callback, if defined.
		if (mStateChangeCallback != null)
			mStateChangeCallback.onStateChange(oldState, newState);

		// Collect a few OBD stats...
		if (newState >= STATE_OBDCONNECTED) {
			mOBDDetectedProtocol = ebt.sendATCommand2("ATDP");
			HashMap<String, String> responses = sendOBDRequestByName("VIN");
			mgStats.setStat("vin", getResponsesAsString(responses));
		}
	}


	/**
	 * Define a method that you want to be executed any time the OBD2 state
	 * changes.
	 * 
	 * @param newStateChangeListener
	 *            - an instance of EventCallback with onStateChange() defined
	 *            (overridden).
	 */
	public void setOnStateChangedListener(EventCallback newStateChangeListener) {
		mStateChangeCallback = newStateChangeListener;

		// good measure?
		if (mStateChangeCallback != null) {
			if (DEBUG) msg("Firing state change event for current state ");
			mStateChangeCallback.onStateChange(mCurrentState, mCurrentState);
		} else {
			msg("Warning: Null state change listener assigned");
		}

	}

	/**
	 * Convenience method that wraps the setOnStateChangedListener.
	 * @param newStateChangeListener
	 */
	public void registerOnStateChangedListener (EventCallback newStateChangeListener) {
		setOnStateChangedListener(newStateChangeListener);
	}

	private void msg(String message) {
		if (mMessageCallback != null) {
			mMessageCallback.onNewMessageArrived("obd: " + message);
		} else {
			Log.d("OBD(sess):", message);
		}
	}

	/**
	 * A means for a parent class to receive messages generated by this class.
	 * Very useful for debugging.
	 * 
	 * @param eventCallback
	 *            - override the newMessage() method.
	 */
	public void registerMessageCallback(EventCallback eventCallback) {
		mMessageCallback = eventCallback;
		msg("Registered debug callback.");
	}

	/**
	 * Shutdown threads and clean up.
	 */
	public void shutdown() {

		setCurrentState(0);

		mThreadsOn = false;

		if (mtStateManagementThread != null)
			mtStateManagementThread.interrupt();
	}

	/**
	 * Constructor.
	 * 
	 * @param network
	 *            - defines which network we're on. This is quite important.
	 *            Records in the DB are distinguished by this network ID.
	 * @param e
	 *            - An instance of ELMBT which we'll use for I/O.
	 * @param p
	 *            - An instance of PIDDecoder. We register ourself with this
	 *            piddecoder so they can pass us requests.
	 */
	public OBD2Session(ELMBT e, PIDDecoder p, String NOTUSEDnetwork, DashDB d) {
		ebt = e;
		piddecoder = p;
		ddb = d;

		// register with the PIDDecoder so they can direct us to perform queries
		// if they need specific data points.
		registerSelfWithPIDDecoder();

		startStateManagementThread();
		// note we don't call resume(). That's because the state management
		// thread does that sort of thing.

	}

	/**
	 * Returns a handle to the local DashDB instance...
	 * 
	 * @return
	 */
	public DashDB getDashDB() {
		return ddb;
	}

	/**
	 * Register with the PIDDecoder so it knows that we're here in case it wants
	 * to perform OBD2 queries to get data.
	 */
	private void registerSelfWithPIDDecoder() {
		// register ourself. The PIDDecoder may then ask us to perform OBD
		// queries, which we will glady execute and return the RAW response.

		if (piddecoder != null)
			piddecoder.registerOBD2SessionLayer(this);
		else
			msg("registerSelfWithPIDDecoder(): ERROR: PIDDecoder not available (null)");
	}

	/**
	 * Sends the specified OBD Command and obtains the response. This shall be
	 * the lowest level OBD commanding method available and thus it is
	 * synchronized for thread safety.
	 * 
	 * @param obdRequest
	 *            - raw request without the CR/LF.
	 * @return - returns the raw response from the device, up to the prompt
	 *         character ('>').
	 */
	public synchronized String obdCommand(String obdRequest) {
		return ebt.sendOBDCommand(obdRequest);
	}

	/**
	 * Do what we can to open a clean line of communication to the vehicle for
	 * the purpose of sending/receiving OBD commands.
	 * 
	 * @return - returns the max state which we achieved.
	 */
	// static final int STATE_UNINITIALIZED = 0;
	// static final int STATE_BTCONNECTED = 10;
	// static final int STATE_BTCONFIGURED = 20;
	// static final int STATE_OBDCONNECTED = 30;
	// // (once we've reached obdConnected, there's no higher we need to go.)
	private int enterStateForOBD() {
		boolean ret = false;

		// this is the state we'll return. Throughout each step of the process
		// it increments until the end when we finally return it, or if it gets
		// cut off along the way it gets returned early.
		int newState = STATE_UNINITIALIZED;

		// if bluetooth is connected then we're at lest in BTCONNECTED state.
		if (ebt.isConnected() != true) {
			return newState;
		}

		newState = STATE_BTCONNECTED;

		// if we're able to get a positive response from the ATInitialization
		// method, then we must be BTCONFIGURED (bluetooth device configured,
		// lines of communications open. ).
		// msg ("Sending AT Initialization...");
		ret = sendATInitialization();
		if (DEBUG == true)
			msg("OBD2 AT-Init got response: " + ret);

		if (ret != true) {
			return newState;
		}

		newState = STATE_BTCONFIGURED;

		// Try to run an OBD command that should definitely return a response.
		ret = detectIfOBDIsAlive();
		
		// set protocol to zero to auto detect. 
		if (ret != true)
			ebt.sendATCommand("ATSP0");
		
		if (DEBUG)
			msg(ret == true ? "OBD is reachable. " : "No OBD responses");

		if (ret != true) {
			return newState;
		}

		// the final level is the highest.
		newState = STATE_OBDCONNECTED;
		
		// NEW: Make note of the detected ELM protocol. This will be used for packet decoding. 
		mELMProtocol = ebt.getProtocolNumber();

		return newState;
	}

	/**
	 * Sends the AT Initialization string to get the OBD device into the right
	 * mood.
	 * 
	 * @return - true if everything went well. False if anything went wrong.
	 */
	private boolean sendATInitialization() {
		boolean ret = true;

		if (mInitialInitSent != true) {
			ret = ebt.sendATInitialization(mInitInitial);
			if (ret == true)
				mInitialInitSent = true;
		}

		// if we did an init and it failed, then bail now.
		if (ret != true)
			return false;

		return ebt.sendATInitialization(mInitResume);

	}

	/**
	 * Allow the sending of AT commands... if the conditions are appropriate.
	 * 
	 * @param ATCommand
	 * @return
	 */
	public String sendATCommand(String ATCommand) {

		return ebt.sendATCommand(ATCommand);

	}

	/**
	 * sends a command or two to determine whether the bluetooth connection is
	 * in a state which permits OBD commands and responses.
	 * 
	 * @return
	 */
	private boolean detectIfOBDIsAlive() {
		String firstResponse = "";

		String request = "01 0C";
		String response = "";

		if (DEBUG == true)
			msg("OBD2 session sending RPM request...");
		response = ebt.sendOBDCommand(request);

		if (DEBUG == true)
			msg("OBD2 session RPM=" + response);

		if (response.contains("41 0C")) {
			mgStats.setStat("OBDAliveCriteria", "method #1 " + request + "=" + response);
			return true;
		}

		firstResponse = response;

		request = "01 00";
		if (DEBUG == true)
			msg("OBD2 session sending SupportedPIDs request...");
		response = ebt.sendOBDCommand(request);
		if (DEBUG == true)
			msg("OBD2 session SupportedPIDs=" + response);

		if (response.contains("41 00")) {
			msg("detectIfOBDIsAlive(): Warning: First request for RPM failed, but request for supported codes succeeded. Response to RPM request was " + firstResponse);
			mgStats.setStat("OBDAliveCriteria", "method#2 " + request + "=" + response);
			return true;
		}

		mgStats.setStat("OBDAliveCriteria", "method#1 and method#2 both failed");
		return false;
	}

	private String getKeysString(HashMap<String, String> hm) {
		String ret = "";

		Set<String> s = hm.keySet();
		Iterator<String> i = s.iterator();

		while (i.hasNext()) {
			if (ret.length() > 0)
				ret = ret + "," + i.next();
			else
				ret = i.next();

		}

		return ret;
	}

	private String getValuesString(HashMap<String, String> hm) {
		String ret = "";

		Set<String> s = hm.keySet();
		Iterator<String> i = s.iterator();

		while (i.hasNext()) {
			if (ret.length() > 0)
				ret = ret + "," + hm.get(i.next());
			else
				ret = hm.get(i.next());

		}

		return ret;
	}

	/**
	 * 
	 * @param hmResponses
	 * @return - returns a string containing the responses.
	 */
	private String getResponsesAsString(HashMap<String, String> hmResponses) {
		String ret = "";

		Set<String> s = hmResponses.keySet();
		Iterator<String> i = s.iterator();

		String thisKey = "";
		while (i.hasNext()) {
			thisKey = i.next();
			ret = ret + " K=" + thisKey + " D=" + hmResponses.get(thisKey);
			ret = ret + "; ";
		}

		return ret;
	}

	private boolean isValidOBDEchoBack(String request, String response) {

		request = request.replace(" ", "");
		response = response.replace(" ", "");

		if (!response.contains(request)) {
			return false;
		}

		return true;
	}

	/**
	 * Given an OBD response such as ... parse it into individual responses and
	 * return it as a hashmap.
	 * 
	 * @param obdResponse
	 * @return - returns a hashmap containing individual responses and the data
	 *         payload.
	 */
	HashMap<String, String> parseOBDResponse(String DPN, String obdRequest,
			String obdResponse, HashMap<String, String> hmResponses) {

		HashMap<String, String> hmDecoded = new HashMap<String, String>();

		// Example: req="01 0C" then the responseAckBytes would be "41 0C".
		String responseAckBytes;

		String modenibble = obdRequest.substring(0, 2);

		// Default case: mode 01.
		responseAckBytes = "4" + obdRequest.substring(1);

		// mode 22 requests.
		if (modenibble.startsWith("22"))
			responseAckBytes = "6" + obdRequest.substring(1);

		// in case the request didn't include a space between bytes, we need it
		// there to search for it in the response.
		if (responseAckBytes.length() > 3
				&& !responseAckBytes.substring(2, 3).equals(" ")) {
			// insert a space between the first and second byte.
			responseAckBytes = responseAckBytes.substring(0, 2) + " "
					+ responseAckBytes.substring(2, 4);
		}

		// msg ("parseOBDResponse(): Searching for [" + responseAckBytes +
		// "] within [" + obdResponse + "]");

		// Special case for AT commands...
		if (obdRequest.startsWith("AT")) {
			// Formula must contain something, even though it will be ignored,
			// and the decoder will actually know how to decode it based on the
			// request in the case of AT commands.
			hmDecoded.put("AT",GTOMath.decodeAutoDetect(0,obdRequest,"AT","",obdResponse));
			return hmDecoded;
		}

		String[] responses = obdResponse.split("\\|");

		String hedr = ""; // header
		String hexBytes = ""; // data
		// loop through the responses, add each valid one to the hashmap.
		for (int i = 0; i < responses.length; i++) {
			// if (isValidOBDEchooBack (responses[i],responseAckBytes)) {
			if (responses[i].contains(responseAckBytes)) {
				// split the header from the data bytes, add it to the hashmap.
				int firstSpace = responses[i].indexOf(" ");

				// 29-bit
				if (firstSpace == 2) {
					try {
						hedr = responses[i].substring(0, 12);
					} catch (Exception e) {
						msg("parseOBDResponse(): Malformed packet. not 29-bit? "
								+ responses[i] + " err=" + e.getMessage());
						// skip the packet by continuing.
						hedr = "?";
					}
				}

				// 11-bit
				if (firstSpace == 3) {
					try {
						hedr = responses[i].substring(0, 3);
					} catch (Exception e) {
						msg("parseOBDResponse(): Malformed packet. not 11-bit? "
								+ responses[i] + " err=" + e.getMessage());
						// skip the packet by continuing.
						hedr = "?";
					}
				}

				// find data (hex bytes)
				int dataStart = responses[i].indexOf(responseAckBytes)
						+ responseAckBytes.length() + 1;

				// // If it's a mode22 type request, then the data is one byte
				// further than we though. UGLY HACK!
				// if (obdRequest.replace(" ","").length()==6)
				// dataStart += 1;

				try {
					hexBytes = responses[i].substring(dataStart);
				} catch (Exception e) {
					msg("parseOBDResponse(): Unexpected data offset. expected="
							+ dataStart + " response=" + responses[i]);
					hexBytes = responses[i];
				}

				// Store the hexbytes.
				if (hmResponses.containsKey(hedr)) {
					// append multi-packet response.
					hmResponses.put(hedr, hmResponses.get(hedr) + " "
							+ hexBytes);
				} else {
					// new.
					hmResponses.put(hedr, hexBytes);
				}

				if (obdRequest.contains("0902")) {
					msg("VIN: " + GTOMath.dumpHashMap(hmResponses)
							+ " And Current response is " + responses[i]);
				}
			} // end of if-the-response-contains-the-ack-bytes.
			else {
				// responses[i].contains(responseAckBytes)
				// if (DEBUG==true) msg
				// ("Response fails repeat test. responses[i]=" + responses[i] +
				// " does not contain " + responseAckBytes);
			}
		} // end of for-loop.

		// Now loop through all response packets and decode them. We will later
		// return that decoded value.
		// TODO: Check and see if there are multiple responses first, if not,
		// then don't do this complicated logic.
		Set<String> s = hmResponses.keySet();
		Iterator<String> it = s.iterator();

		String thisKey = "";
		String decodedValue = "";
		// String formula = ddb.getOBDFormulaByRequest(obdRequest);
		String formula = ddb.getOBDFormulaByName(DPN);
		// Loop through responses. They may be from one or more CPUs.
		while (it.hasNext()) {
			thisKey = it.next();
//			decodedValue = GTOMath.decodeAutoDetect(obdRequest, formula, hmResponses.get(thisKey));
			decodedValue = GTOMath.decodeAutoDetect(mELMProtocol, obdRequest, formula, obdResponse, hexBytes);
			// if (DEBUG == true) msg ("KEY=" + thisKey + " val=" + decodedValue
			// + " OBDreq=" + obdRequest);
			hmDecoded.put(thisKey, decodedValue);
		}

		//
		return hmDecoded;
	}

	/**
	 * 
	 * ** Intended to be called by piddecoder, not you... use piddecoder's "getDataViaOBD" to get OBD2 data. 
	 * 
	 * @param dataPointName
	 *            - the data point name for which we want data.
	 * @return - returns a hashmap containing all DECODED responses. K=ECU, V=(decoded) Data.
	 */
	public HashMap<String, String> sendOBDRequestByName(String dataPointName) {
		String obdResponse = "";

		// TODO: Let the calling method define the hashmap so we don't allocate
		// it every time?
		HashMap<String, String> hmResponses = new HashMap<String, String>();

		// query the DB for the OBD request related to this data point name
		String obdRequest = ddb.getOBDRequestByName(dataPointName);
//		if (DEBUG == true) msg("Lookup of DPN " + dataPointName + " has request=" + obdRequest);
		// Make sure the request found in the DB is valid.
		if (obdRequest.length() < 1) {
			msg("Warning: Lookup returned empty request-string for datapoint " + dataPointName);
			return hmResponses;
		}

		if (obdRequest.startsWith("AT")) {
			// AT command... Send it regardless of our connection state.
			obdResponse = sendATCommand(obdRequest);
		} else {

			// Request is NOT an AT-request. Check and make sure we're in a suitable state.
			if (getCurrentState() != STATE_OBDCONNECTED) {
				// TODO: Keep track of number of ignored denied requests?
				if (DEBUG == true) msg("Ignoring data request - OBD not connected. datapoint=" + dataPointName + " State=" + getCurrentState());
				 
				// slow your roll
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
				}
				
				return hmResponses;
			}
			
			// apparently we're connected!
			obdResponse = obdCommand(obdRequest);
		}

		// Check the response, if its not valid, throw it out.
		if (obdResponse.length() < 3) {
			msg("Threw out invalid response: " + obdResponse + " to request "
					+ obdRequest);
			return hmResponses;
		}

		// parse the response packet and return it all at once.
		return parseOBDResponse(dataPointName, obdRequest, obdResponse,hmResponses);
	}

	/**
	 * 
	 * @return - returns true if the IO layer is done trying to reconnect.
	 */
	public boolean isIODoneTrying() {
		if (ebt != null) {
			return ebt.isIODoneTrying();
		} else {
			return true;
		}

	}

	/**
	 * Will kick the I/O Layer into trying to connect again.
	 */
	public void setIOReconnectNOW() {
		if (ebt != null)
			ebt.setIOReconnectNOW();
	}

	public String sendDTCReset() {
		if (ebt != null)
			return ebt.sendOBDCommand("04");

		return "";
	}

	/**
	 * Do whatever to shutdown all local threads as quick as possible.
	 */
	private void threadsOff() {
		// Signal to the state thread that it's time to shutdown.
		mThreadsOn = false;
		// interrupt any ongoing STUFF
		if (mtStateManagementThread != null)
			mtStateManagementThread.interrupt();

	}

	/**
	 * Suspend operations by this class/session and return the network to a
	 * usable state for others. We will go to sleep as long as we're suspended.
	 * 
	 * @return - true on success, false otherwise. Re-entrant: no
	 */
	public boolean _suspend() {
		final int sleepDuration = 200;

		// already suspended?
		if (isSuspended() == true)
			return true;
		
		threadsOff();

		// Loop (FOREVER) while we wait for the state thread to die.
		int loopCount = 0;
		if (DEBUG)
			msg("DEBUG: entering suspend while loop");
		while (!isSuspended()) {
			if (!EasyTime.safeSleep(sleepDuration))
				break;
			loopCount++;
			if (loopCount == 50) {
				msg("WARNING: suspend loop has surpassed "
						+ (sleepDuration * 50) + "ms and counting...");
				mgStats.setStat("timeToSuspend", ">"
						+ (loopCount * sleepDuration) + "ms");
			}
		}

		if (DEBUG)
			msg("DEBUG: Gracefully exited suspend while loop after "
					+ (loopCount * sleepDuration) + "ms.");
		mgStats.setStat("timeToSuspend", "" + (loopCount * sleepDuration)
				+ "ms");

		// Clean up variables...
		mCurrentState = STATE_UNINITIALIZED;

		return true;
	}

	/**
	 * Resume from a suspended state - re-initialize the device in a way that
	 * ensures that it is ready for our state.
	 * 
	 * @return - true on success, false otherwise. Re-entrant: no
	 */
	public boolean resume() {

		mThreadsOn = true;
		
		// NOT NECESSARY - the state management thread does this as part of the
		// state detection routine.
		// sendATInitialization();

		return startStateManagementThread();
	}

	public GeneralStats getStats() {

		mgStats.setStat("state", "" + getCurrentState());
		mgStats.setStat("stateName", getCurrentStateByName());

		return mgStats;
	}

	/**
	 * Call this AFTER you detect that the session state is in OBDconnected
	 * state.
	 * 
	 * @return - returns the OBD protocol that was detected upon successful
	 *         connection, or a blank string if none was detected.
	 */
	public String getOBDProtocol() {
		return mOBDDetectedProtocol;
	}

	public boolean isSuspended () {
		if (mThreadsOn == false && mtStateManagementThread == null) {
			mgStats.setStat("isSuspended", "true");
			return true;
		} else {
			mgStats.setStat("isSuspended", "false");
			return false;
		}
	}
	
	

}// end of obd2 session class. 