/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.session;

import com.gtosoft.libvoyager.android.ELMBT;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.PIDDecoder;

import android.util.Log;

/**
 * This class will communicate with the ELMBT layer to maintain a consistent BT
 * and OBD state, while presenting new data to upper layers by way of a callback
 * method.
 * 
 */
public class MonitorSession {
	final boolean DEBUG = true;

	// A place to store stats. Collected by our upstream friends.
	GeneralStats mgStats = new GeneralStats();

	// standard CAN/OBD
	public static final int INTERFACE_STANDARD = 10;
	// single-wire CAN
	public static final int INTERFACE_SWCAN = 20;

	// Define all possible states which we may be in at any given time.
	public static final int STATE_UNINITIALIZED = 0;
	public static final int STATE_BTCONNECTED = 10;
	public static final int STATE_BTCONFIGURED = 20;
	public static final int STATE_OBDCONNECTED = 30;
	public static final int STATE_SNIFFING = 40;
	// current state.
	int mCurrentState = 0;
	long mLastStateChangeTime = 0;
	
	EventCallback mMessageCallback = null;

	int mBufferFullCount = 0;

	// The state thread is responsible for managing the state and processing new
	// data.
	Thread mtState = null;

	// threads owned by this class may run as long as this is true. When false,
	// threads should cease asap.
	boolean mThreadsOn = true;

	//
	ELMBT ebt = null;
	//
	PIDDecoder pd = null;

	// if the parent class registers a message listener, the following variable
	// will store that class instance where they define and registered it.
	// EventCallback mNewPacketListener = null;
	// HashMap<String,EventCallback> mhmNewPacketListeners = new
	// HashMap<String,EventCallback>();

	// Will be used to pass state changes up to calling class.
	EventCallback mMonitorStateChangeListener = null;

	// This will be set later on based on the interface that is present -
	// Standard or SWCAN.
	String[] initCommands = null;

	EasyTime metime = new EasyTime();

	// default to standard protocol.
	int mInterfaceType = INTERFACE_STANDARD;

	// initialize the bus for use with standard CAN as well as other BUS types.
	String[] initStandard = { "AT WS", // warm start
			"AT R0", "AT SP0", // set proto to 0/AUTO
			"AT CAF0", // CAN auto-formatting OFF (SUSPECT CAF0 may cause "0100"
						// and such commands to fail).
			"AT H1" // show CAN header bytes.
	};

	String[] initSWCAN = { "AT PP 2D SV 0F", // baud to 33.3k
			"AT PP 2C SV 40", // Send in 29-bit mode, receive in 0x40=29bit,
								// 0x60=29&11.
			"AT PP 2D ON", // turn on baud rate pp
			"AT PP 2C ON", // turn on receive bits mode
			"AT PP 2A OFF", // turn off CAN ERROR Checking
			"ATWS", // warm start the ELM so PP's take effect.
			"ATR0", // Responses = off. (expect no responses to anything we
					// send).
			"AT CAF0", // turn on CAN Auto formatting.
			"AT SP B", // Set proto to "B" - user defined.
			"ATH1" // show CAN header bytes.
	};

	String[] unInitString = { "AT WS" };

	// parent-overridable string which holds the AT commmand which kicks off the
	// monitoring.
	String mMonitorCommand = "ATMA";

	long mLastSniffDataSeen = 0;

	/**
	 * set the monitor mode to "monitor everything", no filters.
	 */
	public void monitorAll() {
		mMonitorCommand = "ATMA";
		setCurrentState(0);
	}

	/**
	 * monitor the bus for packets with source node address equal to that
	 * specified.
	 * 
	 * @param whichTransmistter
	 */
	public void monitorTransmitter(String whichTransmistter) {
		mMonitorCommand = "ATMT" + whichTransmistter;
		setCurrentState(0);
	}

	/**
	 * Monitor the bus for packets with receiver field set to this.
	 * 
	 * @param whichReceiver
	 */
	public void monitorReceiver(String whichReceiver) {
		mMonitorCommand = "ATMR" + whichReceiver;
		setCurrentState(0);
	}

	/**
	 * Specifies the protocol of the connected device. By default, expect
	 * standard CAN/OBD.
	 * 
	 * @return
	 */
	public void setProto(int interfaceType) {

		mInterfaceType = interfaceType;
		
		if (mInterfaceType == INTERFACE_SWCAN) {
			mgStats.setStat("proto", "SWCAN");
		} else {
			mgStats.setStat("proto", "HSCAN");
		}

		switch (interfaceType) {
		case INTERFACE_STANDARD:
			initCommands = initStandard;
			break;
		case INTERFACE_SWCAN:
			initCommands = initSWCAN;
			break;
		default:
			initCommands = initStandard;
			msg("setProto(): Warning: Didn't recognize requested interface type "
					+ interfaceType);
			break;
		}

		// set state to 0 to force a re-initialization on the next loop
		// iteration.
		setCurrentState(0);
	}

	/**
	 * Run this method to try and enter sniff state. There is no guarantee that
	 * it will succeed.
	 * 
	 * @param rebuildsession
	 *            - set this to FALSE if you don't want to re-run certain active
	 *            tests which could be time consuming. Set to TRUE to re-test
	 *            each step, starting from scratch.
	 * @return - returns the state which it was able to attain. Will be one of
	 *         the STATE_* constants.
	 */
	private int enterStateToSniff(boolean rebuildSession) {
		boolean ret = false;
		int newState = STATE_UNINITIALIZED;

		if (mThreadsOn != true) {
			msg("ERROR STop calling enterStateToSniff when threads are off!");
			return 0;
		}

		// either stays 0 (in the case that they want the whole session
		// re-built) or can be set to the current state so as to not re-run any
		// unnecessary checks/test.
		int initialState = STATE_UNINITIALIZED;

		if (rebuildSession != true)
			initialState = getCurrentState();

		// quick sanity check...
		if (initCommands == null) {
			msg("enterStateToSniff(): Warning: init commands not yet defined. Waiting for you to call setProto()");
			return newState;
		}

		// Bail out if BT not available, because our next step will be to talk
		// to the ELM.
		if (ebt.isConnected() != true)
			return newState;

		newState = STATE_BTCONNECTED;

		// if we're able to successfully send AT commands to configure the
		// device then we're in State 20
		if (initialState < STATE_BTCONFIGURED) {
			// ebt.cancelSniff();
			ret = ebt.sendATInitialization(initCommands);
			if (ret != true || confirmProtocol() != true)
				return newState; // failed to initialize the device.
		}

		newState = STATE_BTCONFIGURED;

		// If we're able to send the AT monitor command and see that echo'd back
		// to us (readupto(|)) then we're in State 40.

		if (initialState < STATE_SNIFFING) {
			// Send the monitor command. Make sure it was sent successfully.
			ret = sendMonitorRequest();
			if (ret != true) {
				if (DEBUG)
					msg("error sending monitor request. Breaking out of enterState");
				return newState;
			}
			
			// Now make sure that:
			// 1. We see data flowing
			// 2. That data does not contain errors
			// 3. The CAN error counters are zero.
			
			// #1 and #2 - check that data is flowing and is error free. This
			// may block for a few seconds if necessary to collect enough
			// packets.
			if (isDataClean() != true) {
				if (DEBUG) msg("Connection is not clean. Killing Monitor session altogether. ");
				
				if (DEBUG) msg ("Kamakazee bypassed.");
				
//				_suspend();
				if (DEBUG) msg("Kamakaze suspend thread finished.");
				
				return STATE_UNINITIALIZED;
			}

		}

		newState = STATE_SNIFFING;

		// return the STATE_* constant corresponding to the highest state we
		// were able to attain.
		return newState;
	}

	/**
	 * Sniff X packets. If half or more of those packets contain the word
	 * "ERROR" then data is NOT clean. So we return false if: 1. We encounter a
	 * lack of input (empty packet, timed out waiting for packet) 2. We read
	 * more than 5 packets which contain the word "ERR". We check 10 packets.
	 * 
	 * @return - returns true if we think the data is clean.
	 */
	private boolean isDataClean() {
		final int maxLoops = 10;
		final int maxErrors = 5;
		int loopCount = 0;
		int errorCount = 0;

		String thisPacket = "";
		while (mThreadsOn == true && ebt.isConnected() && loopCount < maxLoops) {
			thisPacket = ebt.readUpToCharacter('|', 5);
			// if we read a blank packet then consider this a fail.
			if (thisPacket.length() < 1) {
				if (DEBUG) msg("empty packet on loop " + loopCount + " suggests unclean data stream.");
				return false;
			}

			// Check this packet.
			if (thisPacket.contains("ERR")) {
				errorCount++;
				// Check the cumulative error count. If it crossed the
				// threshold, then return false.
				if (errorCount >= maxErrors) {
					msg("ERROR: too many ERROR packets (" + errorCount
							+ "). connection is unclean. Last packet: "
							+ thisPacket);
					return false;
				}
			}

			loopCount++;
		}

		if (DEBUG)
			msg("INFO: data stream contained " + errorCount
					+ " errors. Connection is clean.");

		return true;
	}

	/**
	 * Perform a confirmation check to make sure that the protocol we want, is
	 * the protocol that is currently set on the device. The main purpose of
	 * this method is to prevent us from "thinking" we're in a
	 * 
	 * @return - true if the desired interface type is what is selected.
	 */
	private boolean confirmProtocol() {

		// anchor case.
		mgStats.setStat("swcanSupported", "false");

		if (mInterfaceType == INTERFACE_SWCAN) {
			// then we need to use ATDPN to get the currently set protocol, and
			// make sure it is B
			String currentProtocol = ebt.sendATCommand2("ATDPN");

			if (!currentProtocol.equals("B")) {
				msg("ERROR: Tried to activate SWCAN protocol, but device did not support it. Current device protocol is "
						+ currentProtocol);
				mgStats.setStat("swcanSupported", "false");
				return false;
			} else {
				mgStats.setStat("swcanSupported", "true");
			}
		}

		return true;
	}


	private boolean sendMonitorRequest() {
		boolean ret = false;
		String response = "";

		// Clear the input buffer and send the monitor command.
		response = ebt.clearInputBuffer();
		if (response.length() > 3)
			msg("Before re-sending Moni request, to stay in sync we're throwing away this: " + response);

		if (DEBUG)
			msg("DEBUG: Sending monitor command...");
		ret = ebt.sendRaw(mMonitorCommand + "\r");
		if (!ret)
			return false;

		// read the response.
		if (DEBUG) msg("DEBUG: Reading up to |...");
		response = ebt.readUpToCharacter('|', 3);
		if (DEBUG) msg("DEBUG: Read returned. response=" + response);
		// msg ("sendMonitorRequest(): ATMA Response: " + response);
		if (response.contains(mMonitorCommand)) return true;

		for (int i=0;i<5;i++) {
			msg ("SPECIAL CASE: Trying extra times to send monitor command. Try#=" + (i+1));
			ebt.cancelSniff();
			EasyTime.safeSleep(250);
			ret = ebt.sendRaw(mMonitorCommand + "\r");
			response = ebt.readUpToCharacter('|', 3);
			if (response.contains(mMonitorCommand)) {
				msg ("SPECIAL CASE: went into overtime trying to send monitor command! try#=" + (i+1));
				return true;
			}
		}
		
		if (DEBUG) msg("Problem sending Monitor command. Response does not contain request. Response was: " + response);
		return false;

	}

	/**
	 * Use this method to change the state.
	 * 
	 * @param newState
	 *            - the new state, must be one of the STATE_* constants.
	 */
	private void setCurrentState(int newState) {
		int oldState = mCurrentState;

		mCurrentState = newState;

		// Notice that we're first setting the global state variable THEN
		// notifying people. This is important.
		if (oldState != newState)
			stateChanged(oldState, newState);
	}

	/**
	 * Returns the current state as an integer.
	 * 
	 * @return - the current state - corresponds to one of
	 *         MonitorSession.STATE_* constants.
	 */
	public int getCurrentState() {

		// an extra safeguard to make sure that we never get caught up doing
		// stuff while ebt is not connected.
		if (mCurrentState > 0 && ebt.isConnected() != true) {
			if (DEBUG)
				msg("SPECIAL CASE: getCurrentState detected a disconnect, so resetting newstate from "
						+ mCurrentState + " to 0.");
			setCurrentState(0);
		}

		return mCurrentState;
	}

	/**
	 * Executed when the state of this class changes.
	 * 
	 * @param oldState
	 * @param newstate
	 */
	private void stateChanged(int oldState, int newstate) {
		if (DEBUG)
			msg("State changed from " + oldState + " to " + newstate);

		mLastStateChangeTime = metime.getUptimeSeconds();

		if (mMonitorStateChangeListener != null) {
			mMonitorStateChangeListener.onStateChange(oldState, newstate);
		}

		if (oldState < STATE_SNIFFING && newstate == STATE_SNIFFING) {
			// reset buffer full count.
			mBufferFullCount = 0;
		}

	}

	/**
	 * Constructor.
	 * 
	 * @param elmbt
	 *            - an instance of elmbt which has been initialized and ready
	 *            for us to use.
	 */
	public MonitorSession(ELMBT elmbt, int CANNetworkProtocol, PIDDecoder p) {
		ebt = elmbt;
		pd = p;

		// sanity check/s
		if (ebt == null) {
			msg("MonitorSession Constructor: ebt is NULL :(");
		}

		// sets the CAN Network type - 29 or 11 bit?
		setProto(CANNetworkProtocol);

		resume();
	}

	/**
	 * Start up the state management thread. This thread is also responsible for
	 * launching any IO that needs to take place. In addition, this thread is
	 * responsible for kicking off the method that processes newly sniffed
	 * messages.
	 * 
	 * @return
	 */
	private synchronized boolean startStateThread() {

		if (mtState != null)
			return false;

		// Make sure threads are on going into starting this thing!
		mThreadsOn = true;

		mtState = new Thread() {
			public void run() {

				while (mThreadsOn == true) {
					if (DEBUG)
						msg("Sniff Loop. Threads=" + mThreadsOn);
					makeSureWeAreSniffing();

					// if we're sniffing, then process messages.
					if (getCurrentState() == STATE_SNIFFING) {
						sniffOrRebuildSniffConnection();
					} else {
						// not sniffing, so slow down this loop.
						try {
							Thread.sleep(1 * 1000);
						} catch (InterruptedException e) {
							// if sleep is interrupted, break out of the loop.
							break;
						}
					}

				}// end of thread main while loop.

				if (DEBUG)
					msg("State Thread finished.");
				// set thread reference to null so that a resume() is able to
				// re-start the thread if desired.
				mtState = null;
			}// end of run()
		};// end of thread class definition.

		// kick off the thread.
		mtState.start();

		return true;
	}// end of thread start method.

	public void shutdown() {
		_suspend();
	}

	/**
	 * Called by the state management thread, this method is responsible for
	 * processing messages, and if we haven't seen sniff data in a while, it
	 * re-builds the sniffer connection.
	 * 
	 * @return
	 */
	private boolean sniffOrRebuildSniffConnection() {
		// As long as they are coming in. The processMessages method will block
		// us while it processes them one after another.
		processMessages();

		// if it's been 10 seconds or more since seeing sniff data on the BUS,
		// then re-build the snif connection.
		if (metime.getUptimeSeconds() - mLastSniffDataSeen > 10) {
			if (DEBUG)
				msg("No sniff data for 10 seconds. Re-building sniffer session.");
			int newState = enterStateToSniff(true);
			if (DEBUG)
				msg("Session re-built. Are we seeing data yet?");
			if (newState != getCurrentState()) {
				if (DEBUG)
					msg("No sniff data for 10 seconds, so we tried to get into sniff mode but instead we found that the new state is "
							+ newState + " connected=" + ebt.isConnected());
				setCurrentState(newState);
			}
		}

		return true;
	}

	/**
	 * Called by the state management thread, to ensure that we stay in sniffing
	 * state.
	 * 
	 * @return
	 */
	private boolean makeSureWeAreSniffing() {
		// Manage the current state - if current state less than SNIFF, then get
		// back into sniff mode.
		// NEW: Also check that ebt is connected, so that we don't go into a
		// process of trying to configure the device if it is offline. This was
		// causing infinite BLOCKING!
		if (mCurrentState < STATE_SNIFFING && ebt.isConnected() == true) {

			ebt.cancelSniff();
			// throw away characters leading up to the prompt.
			// ebt.readUpToCharacter('>', 1);
			if (DEBUG)
				msg("Entering state to sniff.............");
			int newstate = enterStateToSniff(false);
			if (DEBUG)
				msg("Attempt to enter sniff resulted in new state " + newstate
						+ " old state " + getCurrentState());
			setCurrentState(newstate);
		}

		if (getCurrentState() == STATE_SNIFFING) {
			return true;
		} else {
			return false;
		}
	}

	private void msg(String message) {
		if (mMessageCallback != null) {
			mMessageCallback.newMsg("moni: " + message);
		} else {
			Log.d("moni:", message);
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
		if (DEBUG)
			msg("Registered debug callback.");
	}


	public void registerMonitorStateChangeListener(EventCallback ecb) {
		mMonitorStateChangeListener = ecb;

		// fire it off for the first time.
		if (mMonitorStateChangeListener != null)
			mMonitorStateChangeListener.onStateChange(mCurrentState,
					mCurrentState);

	}

	/**
	 * Go out to the bluetooth layer and pick up one or more messages to
	 * process.
	 * 
	 * @return
	 */
	private void processMessages() {
		String message = "";
		String[] msgs;

		// loop, getting messages with each iteration.
		int loops = 0;

		if (DEBUG)
			msg("Processing messages...");
		// It seems this is where sniffing does most of its magic, so added a
		// check to make sure threads are on as long as we're looping!
		if (DEBUG)
			msg("BEFORE processMessages() while loop. ");
		while (mThreadsOn == true && mCurrentState == STATE_SNIFFING
				&& (message = ebt.readUpToCharacter('>', 5)).length() > 0) {

			msgs = message.split("\\|");

			// Process each message, individually.
			for (int i = 0; i < msgs.length - 2; i++) {
				// if it's a buffer full message, then skip it.
				if (msgs[i].endsWith("FULL") || msgs[i].endsWith(">")) {
					continue;
				}

				// / GRRR - we shouldn't check this every time, just at first,
				// because it is wasfetul
				// TODO: Only check this for the first few messages.
				if (!msgs[i].contains("ERR"))
					processOneMessage(msgs[i]);
			}

			// Check and see if we need to get back into sniff mode.
			if (message.endsWith(">") || message.contains("BUF") || message.contains("FULL")) {
				sendMonitorRequest();
				mBufferFullCount++;
			}

			loops++;
		}// end of while...
		if (DEBUG)
			msg("AFTER processMessages() while loop. loops=" + loops);

		if (DEBUG)
			msg("Processing messages - complete.");

	}

	/**
	 * Process a single sniffed message. It might be 11-bit or 29-bit. Throw out
	 * junk by returning false;
	 * 
	 * @param message
	 *            - the single message.
	 * @return - true if the message was good and processed successfully, false
	 *         otherwise.
	 */
	private boolean processOneMessage(String message) {

		// msg ("processOneMessage(): " + message);

		// throw out runt messages.
		if (message.length() < 2)
			return false;

		// chop off the bar character.
		if (message.endsWith("|"))
			message = message.substring(0, message.length());

		// at this point, message seems to be valid.
		mLastSniffDataSeen = metime.getUptimeSeconds();

		// Pass the message to the PIDDecoder which is registered with us.
		if (pd != null)
			pd.decodeOneMessage(message);

		return true;
	}

	public long getTimeInCurrentState() {
		return metime.getUptimeSeconds() - mLastStateChangeTime;
	}

	public long getBufferFullsPerMinute() {

		// make sure we're in a sniff state, otherwise this statistic isn't
		// valid.
		if (getCurrentState() != STATE_SNIFFING)
			return 0;

		long timeInState = getTimeInCurrentState();

		// prevent divide-by-zero scenario.
		if (timeInState == 0)
			timeInState = 1;

		return mBufferFullCount * 60 / timeInState;
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

	public GeneralStats getStats() {

		mgStats.setStat("state", "" + getCurrentState());
		mgStats.setStat("stateTime", "" + getTimeInCurrentState() + "s");
		mgStats.setStat("blocksPerMin", "" + getBufferFullsPerMinute());

		// Build a human readable "protocol" string.
		switch (mInterfaceType) {
			case INTERFACE_STANDARD:
				mgStats.setStat("btAdapterType", "PROTO_STANDARD");
				break;
			case INTERFACE_SWCAN:
				mgStats.setStat("btAdapterType", "PROTO_SWCAN");
				break;
			default:
				mgStats.setStat("btAdapterType", "NOT SET");
				break;
		}

		mgStats.setStat("monitorMode", "" + mMonitorCommand);

		return mgStats;
	}

	/**
	 * Take steps to start the shutdown of local threads. This method will
	 * return before the threads are dead though, so implement your own local
	 * wait loop if necessary.
	 */
	private void threadsOff() {
		if (DEBUG)
			msg("TURNING THREADS OFF!");
		mThreadsOn = false;

		if (mtState != null)
			mtState.interrupt();
		if (DEBUG)
			msg("THREADS SHOULD BE OFF!");

	}

	/**
	 * Suspend operations by this class/session and return the network to a
	 * usable state for others. We will go to sleep as long as we're suspended.
	 * 
	 * @return
	 */
	public boolean _suspend() {
		final int sleepDuration = 200;
		
		// Already suspended?
		if (isSuspended() == true) 
			return true;

		boolean ret;
		// BH NEW. To get rid of problems on uninit.
		// ebt.sendATCommand("ATI");

		ret = ebt.sendATInitialization(unInitString);
		if (ret == false) {
			if (DEBUG)
				msg("Moni was unable to uninit. device may not be ready for next session!");
		}

		threadsOff();

		// Loop, potentially forever if that's what it takes.
		int loopCount = 0;
		while (!isSuspended()) {
			if (!EasyTime.safeSleep(sleepDuration))
				break;
			loopCount++;
			if (loopCount == 50) {
				msg("WARNING: suspend loop has surpassed " + (sleepDuration * 50) + "ms and counting...");
			}

			// Update this every time I guess... This gives us great precision
			// for the suspend time stat.
			mgStats.setStat("timeToSuspend", ">" + (loopCount * sleepDuration) + "ms");
		}

		mgStats.setStat("timeToSuspend", "" + (loopCount * sleepDuration) + "ms");
		if (DEBUG) msg("DEBUG: Gracefully exited suspend while loop after " + (loopCount * sleepDuration) + "ms.");

		return true;
	}
	
	/**
	 * @return - returns true if this class is in a suspended state. false otherwise. 
	 */
	public boolean isSuspended () {
		if (mThreadsOn == false && mtState == null) {
			mgStats.setStat("isSuspended", "true");
			return true;
		} else {
			mgStats.setStat("isSuspended", "false");
			return false;
		}
		
	}

	public boolean resume() {

		
		// Set initial state for threads to be kicked off.
		mThreadsOn = true;

		// Initial state.
		setCurrentState(0);

		// Kick off state thread, which manages state and delegages the
		// procesing of new data as it arrives.
		boolean ret = startStateThread();
		if (ret == false)
			msg("ERROR: MonitorSession resume(): attempt to start state thread failed. ");
		else
			msg("MonitorSession State Thread kicked off successfully!");

		return true;
	}

}
