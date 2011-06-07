/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.android;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;

/*
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

/**
 * This class connects to, and remains connected to, the given bluetooth device.
 * This class DOES NOT send any data to the device on its own accord. Instead, data must be sent by a parent class. - 
 * This ensures that the calling class can be in any state such as ATMA monitor-all, and we won't disrupt that.
 * Exception 1: Upon connecting, we may go out and query the connected device with a few AT commands for statistical purposes.
 */

public class ELMBT {
	
	// Keeps track of the number of times we fail to connect over the course of our lifetime, regardless of successful connects. 
	int mTotalFailedConnects = 0;
	int mTotalSuccessfulConnects = 0;
	
	final boolean DEBUG=false;

	final boolean USE_REFLECTION = false;
	
	GeneralStats mGenStats = new GeneralStats();

	// Define the UUID which specifies that we want to make an RFCOMM connection with the peer.
	//static final UUID UUID_RFCOMM_GENERIC = new UUID(0x0000110100001000L,0x800000805F9B34FBL);
	static final UUID UUID_RFCOMM_GENERIC = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// keeps track of number of sequential connect() failures. reset to 0 upon successful connect.
	int mSuccessiveFailedConnects = 0;
	// defines the maximum number of failures we allow ourself before giving up completely and shutting ourself down. 
	int mMaxSuccessiveConnectFails = 30;

	// minimum number of seconds that must elapse between two connect()'s. 
	int MINIMUM_RECONNECT_PERIOD = 15;

	// maximum number of bytes to accept in the input buffer before we start blocking/dropping. 
	final int INPUT_BUFFER_SIZE = 32767;

	// Max sequential Input or Output errors that can occur before we consider the connection DEAD. 
	final int MAX_IO_ERRORS = 3;
	
	// Number of seconds to pause between each check to the status. 
	final int STATUS_THREAD_UPDATE_INTERVAL = 5;
	
	// Bluetooth MAC address of our peer. Provided to us by calling class via our constructor.  
	String 	mPeerMAC 	= "";

	// bluetooth adapter which provides access to bluetooth functionality. 
	BluetoothAdapter 	mBTAdapter 	= null;
	// socket represents the open connection.
	BluetoothSocket		mBTSocket   = null;
	// device represents the peer
	BluetoothDevice 	mBTDevice 	= null; 
	
	// streams
	BufferedInputStream mBTInputStream 	= null;
	//InputStream 		mBTInputStream  = null;
	OutputStream 		mBTOutputStream = null;

	// if true, then if we are disconnected we try to reconnect every so often.
	boolean	mReconnectIfDisconnected  = true;

	// The reconnect Thread.
	Thread 	mtOverallStatus = null;
	
	// number of seconds between each overall-Status check.
	int		mOASDelay = 15;
	
	// I/O error counters
	int mErrorsIn 	= 0;
	int mErrorsOut 	= 0;

	// managed internally, this variable is true when we're connected, false otherwise. 
	boolean mConnected = false;
	// the uptime timestamp taken when we switched into the current state.
	long mUTLastStateChange = 0;
	// time of last connection attempt. 
	long mUTLastConnectionAttempt = 0;
	
	// current time in unix seconds. Set by a timer. 
	Thread mtTimer = null;
	
	EasyTime eTime = new EasyTime();
	
	boolean mThreadsOn = true;
	
	EventCallback mMessageCallback = null;
	EventCallback mStateChangeCallback = null;

	// Stringbuilder, used ONLY by the IO Input reader. Defined here so that it doesn't have to get allocated each time. 
	StringBuilder sbuf = new StringBuilder(INPUT_BUFFER_SIZE + 32);

	// STATS
	long mBytesIn = 0;
	
	
	/**
	 * @return - returns the number of seconds we've spent in the current state. 
	 */
	public long getTimeInCurrentState () {
		return eTime.getUptimeSeconds() - mUTLastStateChange;
	}
	
	
	
	/**
	 * Constructor... 
	 * @param numHeaderBits - 11 or 29
	 * @param defaultAdapter - just pass us an adapter. You can do it since you have the context. We're just a java class without the context to grab the default adapter. 
	 * @param peerMACAddress - MAC address, all uppercase, of the Bluetooth peer for which we are to connect.
	 */
	public ELMBT(BluetoothAdapter defaultAdapter,
			String peerMACAddress) {
		
		mPeerMAC 	= peerMACAddress;
		mBTAdapter 	= defaultAdapter;

		init();
	}

	/**
	 * Called on initial startup or after shutdown() has been used.  
	 * @return
	 */
	private boolean init () {
		
		if (DEBUG) msg ("init(): Initializing");
		resetConnection();
		startOverallStatusThread();
		
		mUTLastStateChange = eTime.getUptimeSeconds();
		mUTLastConnectionAttempt = eTime.getUptimeSeconds() - 60;
		if (DEBUG) msg ("init(): Initialization complete.");

		
		return true;
	}
	
	/**
	 * Called by connect() upon connecting. 
	 */
	private void resetErrorCounters() {
		mErrorsIn 	= 0;
		mErrorsOut 	= 0;
	}
	
	
	/**
	 * @return - true if bluetooth is connected and ready for IO, false otherwise. 
	 */
	private boolean isBTConnected() {
		
		if (mBTSocket == null) {
			if (DEBUG==true) msg ("Not connected: btsocket");
			return false;
		}
		
		if (mBTAdapter == null) {
			if (DEBUG==true) msg ("Not connected: btAdapter");
			return false;
		}

		if (mBTInputStream == null) {
//			msg ("Not connected: i.stream");
			return false;
		}

		if (mBTOutputStream == null) {
//			msg ("Not connected: o.stream");
			return false;
		}
		
		if (mErrorsIn > MAX_IO_ERRORS) {
			if (DEBUG==true) msg ("Not connected: Lost input integrity. E=(" + mErrorsIn + ")");
			return false;
		}

		if (mErrorsOut > MAX_IO_ERRORS) {
			if (DEBUG==true) msg ("Not connected: Lost output integrity. E=(" + mErrorsOut + ")");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Starts the thread which figures out the overall status and does things to manage it.   
	 * @return - true on success, false otherwise. 
	 */
	private boolean startOverallStatusThread() {

		if (mtOverallStatus != null) {
			return false;
		}
		
		mtOverallStatus = new Thread() {
			
			public void run () {
				while (mThreadsOn == true) {
					// if appropriate, try to connect. 
					if (isConnected() == false) connectIfAble();
					
					// take a nap. If we're interrupted, break out of the loop. 
					if (EasyTime.safeSleep(1000) == false) break;
					
				}// end of main while loop. 
				
				if (DEBUG) msg ("Overall Status Thread: The party's over.");
			} // end of run() which defines the reconnect thread.  
		};
		
		// start the thread. 
		mtOverallStatus.start();
		
		return true;
	}

	
	/**
	 * Are conditions right for us to connect? 
	 * @return
	 */
	private boolean ableToConnect () {
		// sanity checks
		if (mReconnectIfDisconnected != true)
			return false;
		
		if (mSuccessiveFailedConnects > mMaxSuccessiveConnectFails)
			return false;

		if (getTimeSinceLastConnect() < MINIMUM_RECONNECT_PERIOD) {
			//msg ("BLocking attempt to connect due to insufficient time passed since last connect attempt. " + getTimeSinceLastConnect() + " < " + MINIMUM_RECONNECT_PERIOD);
			if (DEBUG) msg ("Next reconnect in: " + (MINIMUM_RECONNECT_PERIOD - getTimeSinceLastConnect()) + " seconds.");
			return false;
		}

		return true;
	}
	
	/**
	 * If conditions are appropriate, then try to make a connection. 
	 * @return - false if conditions weren't appropriate to try to connect. True means we tried to connect (may or may not have been successful) 
	 */
	private boolean connectIfAble() {
		boolean ret;
		
		if (!ableToConnect())
			return false;

		
		// if execution reaches this point then the stars have aligned, and conditions are appropriate for us to try to connect.
		
		// mark the timestamp when we last tried to connect(). 
		mUTLastConnectionAttempt = eTime.getUptimeSeconds();
		// Perform the actual connection attempt
		ret = connect();
		// If the connection failed, then run a function that keeps track of that sort of thing. 
		if (!ret) 
			connectFailed();
		
		// whether we connected or not, set the connection status. 
		setConnected(ret);

		return true;
	}
	
	/**
	 * All messages generated by this clas come here. If the caller wants to debug this class, they should 
	 * register their receiver using our registerMessageCallback() method.
	 * @param message
	 */
	private void msg (String message) {
		if (mMessageCallback != null) {
			mMessageCallback.onNewMessageArrived("bt:" + message);
		} 
		else {
			Log.d("BT:",message);
		}
	}

	/**
	 * A means for a parent class to receive messages generated by this class. 
	 * Very useful for debugging.
	 * @param eventCallback - override the newMessage() method. 
	 */
	public void registerMessageCallback(EventCallback eventCallback) {
		mMessageCallback = eventCallback;
		if (DEBUG) msg ("Registered debug callback.");
	}

	
	/**
	 * @return - returns number of seconds since we last tried to connect. 
	 */
	public long getTimeSinceLastConnect() {
		return (eTime.getUptimeSeconds() - mUTLastConnectionAttempt);
	}
	
	/**
	 * Try to establish a connection with the peer. 
	 * This method runs synchronously and blocks for one or more seconds while it does its thing 
	 * SO CALL IT FROM A NON-UI THREAD!
	 * @return - returns true if the connection has been established and is ready for use. False otherwise. 
	 */
	private synchronized boolean connect() {
		
		if (isBTConnected() == true) {
			if (DEBUG) msg ("Warning: connect() called while we're already connected to peer " + mPeerMAC + ". Returning true.");
			return true;
		}
		
		if (checkBluetoothSettings() != true) {
			msg ("connect(): ERROR: bluetooth NOT enabled and ready. Unable to connect().");
			return false;
		}
		
		if (BluetoothAdapter.checkBluetoothAddress(mPeerMAC) != true) {
			msg ("connect(): ERROR: bluetooth MAC is INVALID: " + mPeerMAC);
			return false;
		}
		
		// Reset all streams and socket.
		resetConnection();

		// make sure peer is defined as a valid device based on their MAC. If not then do it. 
		if (mBTDevice == null) 
			mBTDevice = mBTAdapter.getRemoteDevice(mPeerMAC);
		

		// Use reflection, maybe. It doesn't help. 
		// As described here: http://stackoverflow.com/questions/2660968/
		// And complained about here: http://code.google.com/p/android/issues/detail?id=5427
		if (USE_REFLECTION==true) {
			try {
				if (DEBUG) msg ("Creating socket binding using reflection...");
				Method m = mBTDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
				if (DEBUG) msg ("Creating sock");
				BluetoothSocket sock = (BluetoothSocket) m.invoke(mBTDevice, 1);
				if (DEBUG) msg ("Socket binding with reflection was successful!");
				mBTSocket = sock;
			} catch (Exception e) {
				msg ("Error during rfcomm reflection: " + e.getMessage());
				return false;
			}
		} else {
			// Make an RFCOMM binding. 
			try {mBTSocket = mBTDevice.createRfcommSocketToServiceRecord(UUID_RFCOMM_GENERIC);
			} catch (Exception e1) {
				msg ("connect(): Failed to bind to RFCOMM by UUID. msg=" + e1.getMessage());
				return false;
			}
		}


		// They say it is good to cancel discovery before connect, but does this cause us trouble if we try to connect to two different adapters at the same time? 
		if (mBTAdapter.isDiscovering()) {
			if (DEBUG) msg ("Cancelling active discovery session...");
			mBTAdapter.cancelDiscovery();
		}
		
		
		if (DEBUG) msg ("connect(): Trying to connect...");

		try {
			mBTSocket.connect();
		} catch (Exception e) {
			msg ("connect(): Failed attempt number " + (mSuccessiveFailedConnects+1) + " of " + mMaxSuccessiveConnectFails + " while connecting to " + mPeerMAC + "(" + mBTDevice.getName() + "). Error was: " + e.getMessage());
			return false;
			// there was a problem connecting... make a note of the particulars and move on. 
		}

		if (DEBUG) msg ("connect(): CONNECTED! Peer=" + mBTDevice.getAddress() + "(" + mBTDevice.getName() + ")");
		
		try {
			mBTOutputStream = mBTSocket.getOutputStream();
			mBTInputStream  = new BufferedInputStream (mBTSocket.getInputStream(),INPUT_BUFFER_SIZE);
			//msg ("Connecting non-buffered input stream...");
			//mBTInputStream  = mBTSocket.getInputStream();
		} catch (Exception e) {
			msg ("connect(): Error attaching i/o streams to socket. msg=" + e.getMessage());
			return false;
		}
		
		
		return true;
	}
	
	/**
	 * Reset input and output streams and make sure socket is closed. 
	 * This method will be used during shutdown() to ensure that the connection is properly closed during a shutdown.  
	 * @return
	 */
	private void resetConnection() {

		// applies only to scantool.net devices: 
		// do a quick thing that will kick the tool out of ATMA mode. also submitted hardware feature request to scantool.net for this. 
		sendRaw("X");
		
		if (mBTInputStream != null) {
			try {mBTInputStream.close();} catch (Exception e) {}
			mBTInputStream = null;
		}
		
		if (mBTOutputStream != null) {
			try {mBTOutputStream.close();} catch (Exception e) {}
			mBTOutputStream = null;
		}
		
		if (mBTSocket != null) {
			try {mBTSocket.close();} catch (Exception e) {}
			mBTSocket = null;
		}

		setConnected(false);

	}


	/**
	 * Note: After a successful connect() we won't know a disconnect happened until the I/O failcount goes up high enough to suggest that the connection is dead. 
	 * NEW: we will now offer a method notifyBluetoothDisconnected which can be executed, with bluetooth address, to notify us that *A* bluetooth device connected, and we can see if it was us and if so execute setConnected
	 * @return - Returns true if the bluetooth is connected and ready for I/O. False otherwise.
	 */
	public boolean isConnected () {
		return mConnected;
	}

	private void connectFailed() {
		msg ("Connect Failed.");
		
		mTotalFailedConnects++;
		mGenStats.setStat("totalFailedConnects", "" + mTotalFailedConnects);
		mSuccessiveFailedConnects++;
		if (mSuccessiveFailedConnects > mMaxSuccessiveConnectFails)
			if (DEBUG==true) msg ("WARNING: REACHED MAXIMUM CONNECT RETRIES (" + mSuccessiveFailedConnects + ")" + ". NOT TRYING ANY MORE. ");
	}

	
	/**
	 * This gets called each time a connection attempt is made. 
	 * The result of the connection attempt is passed to this method as soon as the attempt is made.  
	 * @param newConnectedState
	 */
	private void setConnected (boolean newConnectedState) {

		// reset talles if we connected. 
		// If a connection attempt failed, we log that with connectFailed() method. 
		if (newConnectedState == true)  {
			mSuccessiveFailedConnects=0;
			mTotalSuccessfulConnects++;
			mGenStats.setStat("totalSuccessfulConnects", "" + mTotalSuccessfulConnects);
		}

		
		// Check and see if the state is changed or the same state repeated...
		if (mConnected == newConnectedState)
			return;
		else {
			// state changed, so set the variable then execute the state change event
			mConnected = newConnectedState;

			// state change event. 
			connectionStateChanged ();
		}

		
	}
	

	/**
	 * Run this method upon detecting a connect/disconnect.  
	 */
	private void connectionStateChanged() {
	
		// we just connected
		if (mConnected == true) { 
			if (DEBUG) msg ("connectionStateChanged(): We just connected!");
			resetErrorCounters();
			mBytesIn = 0;
			resendEventCallback();

			// Collect a few initial stats. 
			// Send this warmstart with retries, so we can clear the stream and get to a clean I/O state with no buffers or junk on the stream.
			String WSReponse=sendATCommandWithRetry("ATWS");
			if (DEBUG) msg ("Initial warmstart. R=" + WSReponse);
			// Send a few queries to populate statistical things. 
			mGenStats.setStat("elmVersion", sendATCommand2("ATI"));
			mGenStats.setStat("elmAdvancedVersion", sendATCommand2("STI"));
			mGenStats.setStat("elmVoltsAtConnect", sendATCommand2("ATRV"));
		} else {
			// we just disconnected. 
			if (DEBUG) msg ("connectionStateChanged(): We just disconnected.");
		}

		msg ("new connection state: " + mConnected + " last state lasted for " + getTimeInCurrentState() + " seconds.");

		mUTLastStateChange = eTime.getUptimeSeconds();
		
		// And finally, fire off the parent class' on-state-change event handler, if defined.  
		try {
			if (mStateChangeCallback != null) {
				if (mConnected == true) {
					mStateChangeCallback.onStateChange(0,1);
				} else {
					mStateChangeCallback.onStateChange(1,0);
				}
			}
		} catch (Exception e) {
			msg ("!ebt_statechange e=" + e.getMessage());
		}
		
		
		updateAllStats();
	}

	/**
	 * Re-sends the last event, basically fires an event even though the state hasn't changed. 
	 */
	private void resendEventCallback () {
		
		if (mStateChangeCallback != null) {
			if (mConnected == true) {
				mStateChangeCallback.onStateChange(1,1);
			} else {
				mStateChangeCallback.onStateChange(0,0);
			}
		}

		updateAllStats();
	}
	
	/**
	 * Shut down this class. After a shutdown it is expected that this class instance is no longer in use.
	 */
	public void shutdown () {

		// unregister callbacks registered by our friends. 
		mMessageCallback 	 = null;
		mStateChangeCallback = null;
		
		if (DEBUG) msg ("I/O LAYER SHUTTING DOWN. ");

		// shutdown the connection. 
		resetConnection();
		
		// tell threads the party's over. 
		mThreadsOn = false;

		// Jolt the threads so they come out of any sleep they might be in. 
		try {mtOverallStatus.interrupt();} catch (Exception e) {		}
		mtOverallStatus = null;
		
		eTime.shutdown();
		
	}

	/**
	 * - Check the system bluetooth settings and make sure bluetooth is enabled and ready for use!
	 * - This method may kick off a dialog to the user to request that bluetooth be enabled. 
	 * @return - true if its available right now, false otherwise. 
	 */
	public boolean checkBluetoothSettings() {
		// Check the 
		if (mBTAdapter == null) {
			msg ("WARNING: checkBluetoothSettings() was called without first initializing mbtadapter.");
			return false;
		}

		String name = mBTAdapter.getName();
		
		// If we can get the name of the local bluetooth device then bt is definitely enabled and ready for use.  
		if (name != null && name.length() > 0) {
			//msg ("We are " + name);
			return true;
		} else {
			msg ("Unable to determine name of local BT Adapter.");
		}
		
		if (mBTAdapter.isEnabled() != true) {
			// An activity could, at this point, display a system dialog requesting Bluetooth be activated...
			return false;
		}
		
		return false;
	}


	/**
	 * A away for calling class to register a method to get fired off when our state changes (when we disconnect or connect). 
	 * @param eventCallback - override onStateChange().
	 */
	public void registerStateChangeCallback (EventCallback eventCallback) {
		if (mStateChangeCallback != null)
			msg ("ERROR ERROR ERROR - ebt state change callback already registered. Blowing away last registration.");

		
		mStateChangeCallback = eventCallback;
	}

	/**
	 * Convenience method to get the response from the ELM ATCS command. 
	 * It returns T:XX R:XX 
	 * @return CAN Error counts: 
	 * 		GOOD: "T:00 R:00"  
	 * 		BAD(either number nonzero): "T:00 R:87"
	 */
	public String getCANErrorCounts () {
		
		if (isConnected() != true)
			return "";
		
		return sendATCommand2("ATCS");
	}
	
	/**
	 * Clears the input buffer such as before we send an AT command...
	 */
	public String clearInputBuffer () {
		String throwAway = readInputBuffer((char)0);

		return throwAway;
	}
	
	/**
	 * Send a command to the device, it can be an OBD command...
	 * Response is expected to be terminated with a prompt ('>') character.
	 * @param Command - for example "01 00"
	 * @return - returns the string obtained from the device in response to the command.
	 */
	public synchronized String sendOBDCommand (String Command) {
		String response = "";
		
		if (Command.length() < 2) {
			msg ("Warning: OBD request too short! " + Command);
			return "";
		}

		long startTime = eTime.getUptimeSeconds();
		
		clearInputBuffer();
		sendRaw(Command + "\r");
		response = readUpToPrompt();
		
		// Special case: First attempt to send an OBD command, the device is searching... 
		if (!response.endsWith(">") && response.contains("SEARCHING")) {
			if (DEBUG) msg ("EBT Special case: SEARCHING for OBD...");
			response = response + readUpToPrompt();
			// NEW: remove the searching... blip. 
			// Sample: 01 0C|SEARCHING...|7E8 04 41 0C 09 63 ||>
			response = response.replace("SEARCHING...|","");
		}

		// TODO: This could be helpful in some situations but it's just too much spam in most cases. 
		// TMI. 
		if (DEBUG == true) msg (Command + "=" + response);

		long responseTime = eTime.getUptimeSeconds() - startTime;

		if (responseTime > 3)
			msg ("OBD delayed response (" + responseTime + "s) request=" + Command + " response=" + response);
		
		return response;
	}
	
	/**
	 * Send a single AT command and return the response. 
	 * @param ATCommand - the AT command without \r. 
	 * @return - returns the string response from the device. 
	 */
	public String sendATCommand (String ATCommand) {
		String response = sendOBDCommand(ATCommand);

		// pretty heavy debugging here. 
		// NO NO NO This gets logged by sendOBDCommand if debug is on. // if (DEBUG==true) msg (ATCommand + "=" + response);

		return response;

	}
	
	/**
	 * Send AT command, return only the response data. 
	 * This method wraps the "sendAtCommandWithRetry()" method, so it should be fairly robust.  
	 * @param ATCommand
	 * @return
	 */
	public String sendATCommand2 (String cmd ) {
		
		String parts [] = null;
		String responseData = "";
		
		String response = sendATCommandWithRetry(cmd);
		
		
		// Split up the string. 
		try {
			parts =response.split("\\|"); 
			if (parts.length < 1)
				return "";
		} catch (Exception e) {
			// There was an error - either the string was blank, or something went wrong. 
			return "";
		}
	

		if (parts.length >= 2)
			responseData = parts[1];
		else  {
			// There was no AT command response.
		}
					
 		return responseData;
	}
	
	/**
	 * Like sendATCommand except: 
	 * 1. We must see OK or ELM in the response
	 * 2. we must see the original command echo'd back. 
	 * 3. We will try a few times to get a valid response. 
	 * 4. if no valid response was received, we return a blank string. 
	 * 5. return value is true/false, indicating success or failure. 
	 * @param ATCommand - the AT command to send, which should result in an OK or ELM response. 
	 * @return - the response string on success, otherwise a blank string.  
	 */
	public String sendATCommandWithRetry (String ATCommand) {
		long timeoutTime = eTime.getUptimeSeconds() + 5;
		
		String response = "";
		int tries=0;
		
		while (isConnected() == true && eTime.getUptimeSeconds() < timeoutTime) {
			clearInputBuffer();// new: Prevents this: 12-13 16:18:31.600 D/Dash2   (27963): D2: HS: bt:req=ATPP 2D SV 0F resp=R |0F FF E0 97 RTR |0F FF E0 80 RTR |0F FF E0 99 RTR |0F FF E0 B0 RTR |0F FF E0 C0 RTR |10 04 A0 40 F0 10 57 01 00 00 ||> FAILS echo-back test.
			response = sendATCommand(ATCommand);
			// for some reason, we often get a space in front of the response... 
			response = response.trim();
			if (isValidATResponse(ATCommand, response))
				return response;
			
			// since the command failed, take a little nap. 
			try {Thread.sleep(500);} catch (InterruptedException e) {
				break;
			}
			
			tries++;
		}
		
		if (tries > 0)
			msg ("sendATCommandWithRetry(): tried " + tries + " times to get a valid response to AT command " + ATCommand + " but failed. Last response = " + response);
		
		return "";
	}

	public boolean isValidATResponse (String request, String response) {
		
		if (response.length() < request.length() && !response.contains("|OK|")) {
			if (DEBUG) msg ("req=" + request + " resp=" + response + " FAILS length comparison test.");
			return false;
		}
		
		if (!response.startsWith(request)) {
			if (DEBUG) msg ("req=" + request + " resp=" + response + " FAILS echo-back test.");
			return false;
		}
		
//		// Response must at least contain ELM or OK otherwise it's bad. 
//		if (!response.contains("ELM") && !response.contains("OK")) {
// 			msg ("req=" + request + " resp=" + response + " FAILS valid-reply test.");
//			return false;
//		}

		if (response.contains("ERR")) {
			if (DEBUG) msg ("req=" + request + " resp=" + response + " FAILS error-check test.");
 			return false;
		}
		
		if (response.trim().length() < 1) {
			if (DEBUG) msg ("req=" + request + " resp=" + response + " FAILS length test.");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Send a series of AT commands, one after another, only proceeding if the last succeeded. 
	 * @param atCommands - a String array containing the list of initialization commands. 
	 * @return - return value is TRUE if all commands succeeded. False otherwise. 
	 */
	public boolean sendATInitialization (String [] atCommands) {
		String ret = "";
		
		if (atCommands == null) {
			if (DEBUG) msg ("sendATInitialization(): WARNING: Null command set.");
			return false;
		}
		
		// is the connection open?
		
		for (int i=0;i<atCommands.length; i++) {
			ret = sendATCommandWithRetry(atCommands[i]);
			if (ret.length() == 0) {
				msg ("sendATInitialization(): Failed at " + atCommands[i]);
				return false;
			}
		}
		
		return true;
	}
	
	/**
//	 * Send the exact string provided. 
	 * We don't append a CRLF or anything like that - we just send the exact string to the device as-is. 
	 * @param sendThis - exact string to send to the device. 
	 * @return - returns true unless a problem occurs, in which case we return false;
	 */
	public boolean sendRaw(String sendThis) {

		// Ya can't send data if we're not connected!
		if (isConnected() != true)
			return false;
		
		byte bsendThis[] = sendThis.getBytes();
		
		try {mBTOutputStream.write(bsendThis);} catch (Exception e) {
			msg ("IO_OUT_ERR=" + e.getMessage());
			ioErrorOccurredDuringOutput();
			return false;
		}
		
		if (DEBUG) msg ("RAW: " + sendThis);
		ioResetOutputErrorCount();
		return true;
	}
	
	/**
	 * Wrapper method for getting number of bytes in the input buffer. 
	 * We log errors if they occur. 
	 * @return - the number of bytes waiting in the input buffer. 
	 */
	public int inputBytesAvailable () {
		int avail = 0; 

		// there ain't nothing there if we're not connected!
		if (isConnected() == false)
			return 0;
		
		try {avail = mBTInputStream.available();} catch (Exception e) {
			msg ("IO_IN_ERR=" + e.getMessage());
			ioErrorOccurredDuringInput();
		}
		
		return avail;
	}

	/**
	 * A convenience method for sleeping the current thread. 
	 * Exceptions are caught by this method so you don't have to. 
	 * @param millis
	 */
	private void safeSleep (int millis) {
		try {Thread.sleep(millis);} catch (InterruptedException e) { }
	}

	/**
	 * Convenience method to read up to the prompt character. Waits for new data up to about 3 seconds if necessary.
	 * @return
	 */
	public String readUpToPrompt() {
		return readUpToCharacter('>',3);
	}

	/**
	 * Shall return the number of bytes currently queued within the inputStream buffer.
	 * @return
	 */
	public int getNumInputBytesBuffered () {
		try {return mBTInputStream.available();
		} catch (Exception e) {
			return 0;
		}
	}
	
	/**
	 * Loop if necessary (limited by a timeout value) to read data from input buffer up to and including the prompt character. 
	 * This method is used by the sniffer logic, while in sniff mode.
	 * @return
	 */
	public String readUpToCharacter (char stopAtThisCharacter, int maxWaitSeconds) {
		String ret = "";
		int loops=0;
		
		// maximum number of seconds we allow ourselves to spend waiting to see the prompt character. This may block the caller so keep it low. 
		final int READ_TIMEOUT = maxWaitSeconds;
		// If no data is waiting in the input buffer, pause this number of milliseconds. 
		final int READ_INTERVAL = 300;
		
		long upTimeLimit = eTime.getUptimeSeconds() + READ_TIMEOUT;
		
		
		long TEMPTIMESTART = EasyTime.getUnixTime();

		
		// print a useful message. 
//		if (DEBUG) msg ("about to read bytes up to this character: " + stopAtThisCharacter + " buffer contains " + getNumInputBytesBuffered() + " bytes. Max wait seconds=" + maxWaitSeconds);

		long timeLeft = 1;
		while (mThreadsOn == true && !ret.endsWith("" + stopAtThisCharacter) && isConnected() != false) {
			timeLeft = upTimeLimit - eTime.getUptimeSeconds();

			// tracking down bugs. 
			if (timeLeft > READ_TIMEOUT ) {
				msg ("WTFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF - timeleft=" + timeLeft + " and timeout=" + READ_TIMEOUT + " THIS SHIT SHOULD NOT FUCKING HAPPEN. BAILING OUT.");
				timeLeft = 0;
			}
			
//			if (DEBUG) msg ("ReadUpToCharacter(): Loops=" + loops + " bytes=" + ret.length() + " timeLeft=" + (upTimeLimit - eTime.getUptimeSeconds()));

			// slow down the looping in the case when there is no data waiting in the buffer.
			if (inputBytesAvailable() == 0) {
//				if (DEBUG) msg ("NO data waiting in input buffer, sleeping, loops=" + loops + " timeleft=" + timeLeft);
				// Sleep a bit, to slow down the looping. 
				safeSleep(READ_INTERVAL);
			}
			
			ret = ret + readInputBuffer(stopAtThisCharacter); 

			// don't loop for too long. 
//			if (timeLeft > 0) {
			if (timeLeft <= 0) {
				if (DEBUG) msg ("Timeout during read: didn't see prompt within " + READ_TIMEOUT + " seconds. RETURNING " + ret.length() + " bytes, timeout=" + READ_TIMEOUT + " loops=" + loops);
				break;
			}
			
			loops++;
		}

		if (DEBUG) {
			long elpsd =TEMPTIMESTART - EasyTime.getUnixTime(); 
			if (elpsd < 5 && ret.length() < 1) {
				if (DEBUG) msg ("ebt.readuptocharacter: No bytes were read by elapsed time seems to be too low... elapsed=" + elpsd);
			}
				
		}
		
		if (loops>30) {
			if (DEBUG) msg ("readUpToPrompt(): Looped " + loops + " times waiting for prompt (that was probably too many). ");
		} else {
//			if (DEBUG) msg ("readuptoprompt(): Good read. Loops=" + loops + " read " + ret.length() + " bytes.");
		}
		
		return ret;
	}
	
	/**
	 * Read the contents of the input buffer and return it as a nicely formatted string. 
	 * Note that CR/LF characters are converted into '|' by this method. 
	 * null characters and other invalid things are thrown away. 
	 * NEW: Pass it a character value upon which reading we'll stop reading from the input buffer.
	 * NOTE: This method does not wait for new data to arrive. It just reads from the existing input buffer.  
	 * This may be useful for reading individual messages during a sniff session. 
	 * @return - returns the contents of the input buffer as a string.  
	 * @param UpToThisCharacter - set to '\0' to disable, or set it to the character at which we will read and stop reading. 
	 */
	private String readInputBuffer_stringbuilder(char UpToThisCharacter) {
		char thisByte = 0;

		// sbuf will contain the bytes that we read from the input buffer. We read them one at 
		//    a time, and for that reason I've chosen to go with a string builder because it is 
		//    mutable, whereas a String is immutable and inevidebly slower. 
		sbuf.setLength(0);
		
		// convert the input buffer bytes, one-by-one, into a string, which we'll return. 
		try {
			while (mBTInputStream != null && mBTInputStream.available() > 0) {
				thisByte = (char)mBTInputStream.read();

				//msg ("HALLALUGJAH THERE WAS A BYTE IN THE BUFFER!");
				// for stats. 
				mBytesIn++;
				
				// is it a valid printable character? 
				if (thisByte >= 32 && thisByte <=  127)  {
					sbuf.append(thisByte);
				} else {
					// convert CR/LF into vertical bar, other nonprintable characters get thrown away to preserve formatting.
					if (thisByte == 13 || thisByte == 10) {
						sbuf.append("|");
						// a hack, for later comparison.
						thisByte = '|';
					}
					
					else // must have been a bad character. throw it away. 
						msg ("readInputBuffer(): Threw out one INVALID character. code=" + (int)thisByte);
				}
				
				// if we just processed the "stop here" character then break out of the loop!
				if (UpToThisCharacter != 0 && thisByte == UpToThisCharacter) {
					//msg ("Got sentinel. String=" + sbuf.toString());
					break;
				} 
				
				
			}// end of while. 
		} catch (Exception e) {
			msg ("IO_IN_ERR=" + e.getMessage());
			ioErrorOccurredDuringInput();
			// return whatever bytes we were able to read. 
			return sbuf.toString();
		}

		// if we got something then reset input error count.
		if (sbuf.length()>0)
			ioResetInputErrorCount();
		
		return sbuf.toString();
	}

	public String readInputBuffer (char UpToThisCharacter) {
		return readInputBuffer_string(UpToThisCharacter);
	}

	/**
	 * Reads all bytes from the input buffer UP TO 4K. 
	 * In the case of a device that is in sniff mode, it would read forever unless we put the byte limit (4k) on it.  
	 * @param UpToThisCharacter
	 * @return
	 */
	public String readInputBuffer_string(char UpToThisCharacter) {
		char thisByte = 0;

		String sbuf = "";
		
		// convert the input buffer bytes, one-by-one, into a string, which we'll return. 
		try {
			while (mBTInputStream != null && mBTInputStream.available() > 0 && sbuf.length() < 4096) {
				thisByte = (char)mBTInputStream.read();

				//msg ("HALLALUGJAH THERE WAS A BYTE IN THE BUFFER!");
				// for stats. 
				mBytesIn++;
				
				// is it a valid printable character? 
				if (thisByte >= 32 && thisByte <=  127)  {
					sbuf = sbuf + thisByte;
				} else {
					// convert CR/LF into vertical bar, other nonprintable characters get thrown away to preserve formatting.
					if (thisByte == 13 || thisByte == 10) {
						sbuf = sbuf + "|";
						// a hack, for later comparison.
						thisByte = '|';
					}
					
					else // must have been a bad character. throw it away. 
						msg ("readInputBuffer_String(): Threw out one INVALID character. code=" + (int)thisByte);
				}
				
				// if we just processed the "stop here" character then break out of the loop!
				if (UpToThisCharacter != 0 && thisByte == UpToThisCharacter) {
					//msg ("Got sentinel. String=" + sbuf.toString());
					break;
				} 
				
				
			}// end of while. 
		} catch (Exception e) {
			msg ("IO_IN_ERR=" + e.getMessage());
			ioErrorOccurredDuringInput();
			// return whatever bytes we were able to read. 
			return sbuf.toString();
		}

		// if we got something then reset input error count.
		if (sbuf.length()>0)
			ioResetInputErrorCount();
		
		return sbuf;
	}

	
	/**
	 * Log a single InputStream related error. 
	 * When enough of these occur, the connection is marked disconnected.
	 */
	private void ioErrorOccurredDuringInput () { 
		mErrorsIn++;
		//msg ("ELMBT Input Error! Count=" + mErrorsIn);
		if (mErrorsIn > MAX_IO_ERRORS) {
			if (isConnected() == false) msg ("Warning: data receive attempted while not connected.");

			setConnected(false);
		}
	}

	/**
	 * Call this method when a successful input occurs, suggesting the connection is alive!
	 */
	private void ioResetInputErrorCount() {
		mErrorsIn = 0;
	}

	/**
	 * Log a single OutputStream related error. 
	 * When enough of these occur, the connection is marked disconnected.
	 */
	private void ioErrorOccurredDuringOutput () { 
		mErrorsOut++;
		if (DEBUG) msg ("ELMBT Output Error! Count=" + mErrorsOut);
		if (mErrorsOut > MAX_IO_ERRORS) {
			if (isConnected() == false) msg ("Warning: data sent while not connected.");
				
			setConnected(false);
		}
	}
	
	/**
	 * Call this method after a successful output i/o occurs, suggesting the connection is OK for sends. 
	 */
	private void ioResetOutputErrorCount() {
		mErrorsOut = 0;
	}
	
	/**
	 * This method lets you specify whether or not the bluetooth connection should be re-established in the event that it dies. 
	 * @param truetoReconnect - set to true if you want ELMBT to reconnect if disconnected. false means we stay disconnected if the connection dies. 
	 */
	public void reconnectIfDisconnected (boolean truetoReconnect) {
		mReconnectIfDisconnected = truetoReconnect;
	}
	

	/**
	 * See how many AT commands we can send in the given time interval. 
	 * @param atCommand
	 * @param testDuration
	 * @return - returns the number of loops we performed in the given interval. 
	 */
	public long getATLoopCount(String atCommand, int testDuration) {
    	long timeDone = eTime.getUptimeSeconds() + testDuration;
    	
    	long loops = 0;
    	String response = "";
    	
    	// Perform one request to wake up the bluetooth connection. this one doesn't count. 
		response = sendATCommand(atCommand);

		while (eTime.getUptimeSeconds() <= timeDone) {

    		response = sendATCommand(atCommand);
    		
    		loops++;
    	}
    	
    	msg ("getATLoopCount(): Performed a " + testDuration + " second loop test using AT command " + atCommand + ". Looped " + loops + " times. last response=" + response);
    	return loops;
    }

//	public long getUptime() {
//		return eTime.getUptimeSeconds();
//	}


	/**
	 * Passes the value of the input byte counter. Increments for every byte we read. 
	 * @return - returns the number of bytes read on the rfcomm interface since we connected. 
	 */
	public long getBytesIn() {
		return mBytesIn;
	}

	/**
	 * Returns the read rate in bytes per second.
	 * @return - returns bytes per second. 
	 */
	public long getReadRate() {
		
		// prevent divide-by-zero. 
		if (getTimeInCurrentState() == 0)
			return 1;
		
		return getBytesIn() / getTimeInCurrentState();
		
	}
	

	/**
	 * sets the number of seconds to wait in between connect re-tries. 
	 * @param retryPeriodInSeconds - number of seconds. 
	 */
	public void setConnectRetryPeriod (int retryPeriodInSeconds) {
		MINIMUM_RECONNECT_PERIOD = retryPeriodInSeconds;
		if (DEBUG) msg ("info: reconect period is set to " + MINIMUM_RECONNECT_PERIOD);
	}


	public void setMaxRetries(int maxRetries) {
		mMaxSuccessiveConnectFails = maxRetries;
		if (DEBUG) msg ("info: try up to " + maxRetries + " times to connect.");
	}

	public int getNumFailedConnects () {
		//msg ("connect(): Failed attempt number " + (mConnectFails+1) + " of " + mMaxFails + " while connecting to " + mPeerMAC + "(" + mBTDevice.getName() + "). Error was: " + e.getMessage());
		return mSuccessiveFailedConnects;
	}

	/**
	 * @return - returns the max number of times we'll try to re-connect. 
	 */
	public int getNumMaxRetries () {
		return mMaxSuccessiveConnectFails;
	}
	
	/**
	 * Rerturns the MAC address of the device's bluetooth device. 
	 * @return - returns a string representation of the bluetooth device's MAC address. 
	 */
	public String getAdapterMAC () {
		return mBTAdapter.getAddress();
	}

	/**
	 * @return - the name of the bluetooth peer device as found through discovery. 
	 */
	public String getPeerName() {
		if (mBTDevice != null) 
			return mBTDevice.getName();
		else
			return "";
	}
	
	/**
	 * @return - the MAC of the bluetooth peer device as found through discovery. 
	 */
	public String getPeerMAC() {
		if (mBTDevice != null) 
			return mBTDevice.getAddress();
		else
			return "";
	}

	/**
	 * 
	 * @return - returns true if the IO layer is done trying to reconnect. 
	 */
	public boolean isIODoneTrying() {
		if (mSuccessiveFailedConnects >= mMaxSuccessiveConnectFails) 
			return true;
		else
			return false;
	}
	
	/**
	 * Will kick the I/O Layer into trying to connect again. 
	 */
	public void setIOReconnectNOW() {
		mSuccessiveFailedConnects = 0;
	}

	public void setIOStopTryingToConnect() {
		mSuccessiveFailedConnects = mMaxSuccessiveConnectFails + 1;
	}
	
	/**
	 * We cancel a sniff (ATMA/ATMT/ATMR) session by sending a single space character. 
	 * A Space shouldn't be disruptive if we're not in a monitor session, so it is quite ideal. 
	 */
	public void cancelSniff() {
		if (isConnected() != true)
			return;
		
		sendRaw(" ");
	}


	/**
	 * Call this method with the MAC of a bluetooth device which we just disconnected from. 
	 * Having this method prevents us from needing to register broadcast recievers from the ELMBT layer. 
	 * @param address - Bluetooth address, all caps. 
	 */
	public void notifyBluetoothDisconnected(String address) {
		// Does the specified MAC match our own? 
		if (address.equals(mPeerMAC)) 
			// force the connected state to "disconnected" so that we have to re-connect.
			disconnect();
	}

	private void disconnect () {
		resetConnection();
	}


	/**
	 * This method should be written to execute LIGHTNING fast. It may be called upon regularly. 
	 */
	private void updateAllStats () {
		mGenStats.setStat("connectfails","" + mSuccessiveFailedConnects);
		// reset this one just in case it hasn't been set for the first time. 
		mGenStats.setStat("connected","" + mConnected);
		mGenStats.setStat("connectStopped", "" + isIODoneTrying());

		mGenStats.setStat("useReflection","" + USE_REFLECTION);
		
		mGenStats.setStat("errorsIn","" + mErrorsIn);
		mGenStats.setStat("errorsOut","" + mErrorsOut);
		mGenStats.setStat("bytesIn","" + mBytesIn);
		
		mGenStats.setStat("peerMAC","" + mPeerMAC);
		
		mGenStats.setStat("state","" + isConnected());
		mGenStats.setStat("stateTime", "" + getTimeInCurrentState());
		mGenStats.setStat("timeSinceLastConnect", "" + getTimeSinceLastConnect());


		try {
			mGenStats.setStat("localMAC","" + mBTAdapter.getAddress());
			mGenStats.setStat("localName","" + mBTAdapter.getName());
			mGenStats.setStat("peerName","" + mBTDevice.getName());
			mGenStats.setStat("peerClass","" + mBTDevice.getBluetoothClass().getDeviceClass());
			mGenStats.setStat("peerBondState","" + mBTDevice.getBondState());
			if (mBTSocket != null) {
				mGenStats.setStat("socketReady","true");
			} else {
				mGenStats.setStat("socketReady","false");
			}
			
		} catch (Exception e) {
			msg ("Error while fetching peer device stats: " + e.getMessage());
			mGenStats.setStat("statError","" + e.getMessage());
		}
		
		
	}
	
	public GeneralStats getStats () {
		updateAllStats();
		return mGenStats;
	}
	
	/**
	 * May be called by outsiders to tell us to bust out of any sleeps we might be in. 
	 */
	public void cancelCurrentSleeps () {
		mtOverallStatus.interrupt();
	}


	/**
	 * Check the CAN error counters. Only applicable if connected via CAN network. 
	 * @return - returns TRUE if the connection is error free. False otherwise. 
	 */
	public boolean isCANErrorFree() {
		
		// if we're not even connected, then we can't really check the error counters can we!
		if (isConnected() != true)
			return false;

		// obtain current error counter string. 
		String errorCounts = getCANErrorCounts();
		
		if (errorCounts.contains("T:00") && errorCounts.contains("R:00")) {
			return true;
		} else {
			if (DEBUG) msg ("Connection NOT error free. Counters: " + errorCounts);
		}
		
		return false;
	}


	/**
	 * Performs a query against the ELM device and returns the protocol description (ATDP)
	 * @return
	 */
	public String getProtocolString () {
		
		if (!isBTConnected())
			return "";
		
		return sendATCommand2("ATDP");
	}


	/**
	 * Returns the ELM327 ATDPN protocol number. 
	 * @return
	 */
	public int getProtocolNumber() {
		int ret = 0;
		
		if (!isBTConnected())
			return 0;
		
		String response = sendATCommand2("ATDPN");
		
		// If in AUTO mode, then there will be an "A" prefix that we don't want. 
		if (response.length() == 2 && response.startsWith("A")) {
			response = response.substring(1);
		}

		
		try {ret = Integer.valueOf(response,16);
		} catch (NumberFormatException e) {
			msg ("Error converting protocol number from hex to decimal: " + response);
		}

		mGenStats.setStat("elmProtocolNum","" + ret); 
		
		return ret;
	}

	/**
	 * Returns true if the local bt adapter is in active discovery mode. 
	 * @return - true if we're discovering, false if we're not, or an error occurred. 
	 */
	public boolean isDiscovering () {

		boolean x = false;
		try {
			x = mBTAdapter.isDiscovering();
		} catch (Exception e) {
			msg ("ERROR while trying to check discovery status. E=" + e.getMessage());
		}
		
		return x;
		
	}
	
}
