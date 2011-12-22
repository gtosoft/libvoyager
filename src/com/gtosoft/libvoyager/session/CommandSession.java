/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.session;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gtosoft.libvoyager.android.ELMBT;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;



/**
 * This class defines methods and procedures for sending commands on the SWCAN Network.  
 * @author brad
 */


public class CommandSession {
	final boolean DEBUG = true;
	
	// A place to store stats. Collected by our upstream friends. 
	GeneralStats mgStats = new GeneralStats();
	
	int mCurrentState = 0;
	public static final int STATE_UNINITIALIZED =  0;
	public static final int STATE_READY 		=  40; // ready to send commands. 

	// one-time init commands. this isn't the full initialization - these must be followed by the resume commands below!
	boolean mOneTimeInitSucceeded = false;
	private final String oneTimeInit[] = {
            "ATPP 2D SV 0F",                // baud rate to 33.3kbps
            "ATPP 2C SV 40",                // send in 29-bit address mode, receive both(0x60) just 29 (0x40)  
            "ATPP 2D ON",                   // activate baud rate PP. 
            "ATPP 2C ON",                   // activate addressing pp.
            "ATPP 2A OFF",                  // turn off the CAN ERROR checking flags used by wakeUp()
	};

	// A smaller set of commands, since PP's have already been programmed once. 
	// WARNING: Do not mess with PP's here, as we may do that in a different place and then re-run these commands afterwards. 
	private final String resumeCommands [] = {
            "ATWS",                         // reset chip so changes take effect
            "ATCAF1",                       // CAN auto-formatting on
            "ATSPB",                        // set protocol to B (user defined 1)
            "ATH1",                         // show headers
            "ATR0"                          // responses off - we don't expect responses to what we're sending.
	};

	// a minimal set of commands necessary to get into the right state, assuming the initial initialization worked. 
	private final String suspendCommands [] = {
            "ATWS",                         // reset chip so changes take effect
            "ATCAF1",                       // CAN auto-formatting on
            "ATSPB",                        // set protocol to B (user defined 1)
            "ATH1"                         // show headers
//            "ATR1" 						// ATR1 is a default so it's not necessary. 
	};
	
	
	
	Thread mStateManagementThread = null;
	Thread mtWorkerThread = null;
	Handler mhWorkerHandler = null;
	
	EventCallback mMsgEventCallback = null;
	EventCallback mStateChangeCallback = null;
	
	// All threads owned by this class should periodically look at this variable to know whether they should be alive or not. 
	boolean mThreadsOn = true;

	// Reference to the ELMBT object that we use to communicate with the I/O layer. 
	ELMBT ebt;
	
	boolean mSendKeepalives = false;
	
	DashDB dashdb = null;
	
	// The following two variables make up a 4-byte CAN header. 
	// If we set the header, set these too, then we know whether we need to set it again or if its already set to what we want. 
	String mCurrentPriority = "";
	String mCurrentHeader   = "";

	
	/**
	 * Default Constructor. 
	 * @param bt - a reference to the ElmBT object which we should use to connect to the command network. 
	 * Please initialize bt before passint it to us.
	 */
	public CommandSession(ELMBT bt, DashDB dashDB) {
		if (bt == null) 
			msg ("ERROR: elmbt is null. crash imminent. (sess_comand)");
		
		ebt = bt;
		dashdb = dashDB;

//		ebt.registerMessageCallback(new EventCallback () {
//			@Override
//			public void newMsg(String message) {
//				msg ("cmdBT: " + message);
//			}
//		});

		// set initial state. 
		setNewState(STATE_UNINITIALIZED);

		// perform resume tasks such as init (first-time init if necessary), and starting of threads. 
		resume();
		
	}// end of default constructor. 


	
//	private void BTstateChangeHandler (int oldState, int newState) {
//		boolean ret;
//		msg ("State Change: old=" + oldState + " new=" + newState);
//		
//		// if the I/O Layer just connected! 
//		if (oldState == 0 && newState == 1) {
//			msg ("It looks like the I/O layer just connected, to let's send an AT initialization string.");
//			ret = ebt.sendATInitialization(oneTimeInit);
//			if (ret)
//				setNewState (STATE_READY);
//		}
//
//		// if the connection was lost then there are some local variables that we need to reset. 
//		if (newState == 0) {
//			setNewState (STATE_UNINITIALIZED);
//			resetVariables();
//		}
//	}

	/**
	 * Call this to update the CommandSession's state. 
	 * @param newState - new state. one of CommandSession.STATE_*
	 */
	private void setNewState (int newState) {
		int oldState = mCurrentState;
		mCurrentState = newState;

		stateChanged (oldState, newState);
		
	}

	private void stateChanged (int oldState, int newState) {
		if (DEBUG) msg ("Session state changed from " + oldState + " to " + newState);
		
		if (oldState < STATE_READY && newState == STATE_READY) {
			if (DEBUG) msg ("Playing Voyager V NOW!");
			doVoyagerVMorseCode();
			if (DEBUG) msg ("Played Voyager V.");
		}
		
		if (mStateChangeCallback != null) {
			mStateChangeCallback.onStateChange(oldState, newState);
		}
		
	}

	/**
	 * This method sends a command to the network which invokes the audio system and plays a "V" in Morse code, representative of Voyager command session having successfully connected.
	 */
	public void doVoyagerVMorseCode() {
		// This may get executed by the state change method, in which case the global state has not yet been set to 40 but is in fact 100% ready. So in short, don't check the status here. 

		// set headers for that which makes sound :)
		setHeaders("10 01 E0 95");
		
		// dit dit dit
		ebt.sendOBDCommand("84 10 03 60 00");
		
		/// <PAUSE>
		// 3/26/2011 having to decrease the delay between dits and dah, because with the current code, 450ms is noticably too long. Suspect the proguard obfuscator. 
		EasyTime.safeSleep(550 - 100 - 175);

		// daaaah
		ebt.sendOBDCommand("84 40 01 60 00");
		
		// Block, while the command plays out. 
		EasyTime.safeSleep(750);

	}

	
	public void registerStateChangeCallback (EventCallback newStateChangeCallback) {
		mStateChangeCallback = newStateChangeCallback;
		
		// Kick out an event for our current status to get things started clean. 
		if (mStateChangeCallback != null) {
			mStateChangeCallback.onStateChange(mCurrentState, mCurrentState);
		}
		
	}
	
	/**
	 * Lets others know our internal state!
	 * @return
	 */
	public int getCurrentState () {
		return mCurrentState;
	}
	
	
	/**
	 * Resets variables which need to be reset if the connection is reset or the device is restarted. 
	 */
	private void resetVariables () {
		mCurrentHeader = "";
		mCurrentPriority = "";
	}
	
	/**
	 * Starts the worked thread. 
	 * The worker thread is here for us to send work to which we don't want to block the UI thread. 
	 * Stuff sent to the worker thrad is all performed one after another, sequentially, in the background.  
	 * @return - returns true if the thread was created and started, false if it was alrady started previously or something else happened. 
	 */
	private boolean startWorkerThread () {
		
		if (mtWorkerThread != null) {
			if (DEBUG==true) msg ("Worker thread already started.");
			return false;
		}
		
		mtWorkerThread = new Thread() {
			public void run () {
				Looper.prepare();
				
				// Assign the handler within the thread. 
				mhWorkerHandler = new Handler();
				
				while (mThreadsOn == true) {
					Looper.loop();
					// sleep a bit, break out of loop if we're interrupted. 
					try {Thread.sleep(300);} catch (InterruptedException e) {break;}
				}
				
				if (DEBUG==true) msg ("Command worker terminated.");
				mtWorkerThread = null;
			}// end of worker thread's run()
		};// end of worker thread definition
		
		mtWorkerThread.start();
		
		return true;
	}

	/**
	 * With this method you can set whether or not we shall send keepalive messages to the bluetooth peer. This has two purposes: 
	 * 1. Enhanced responsiveness, since the connection never goes to sleep
	 * 2. Quickly detect dead connections, since I/O must be flowing in order to know the connection has died.  
	 * @param onOrOff - true to turn them on, false turns them off. 
	 */
	public void setKeepalives (boolean onOrOff) {
		mSendKeepalives = onOrOff;
	}
	
	private boolean startStateManagementThread() {
		
		if (mStateManagementThread != null) {
			if (DEBUG==true) msg ("State management thread already running");
			return false;
		}
		
		// We might not even need this thread, since we subscribe to event-driven changes from ebt!
		mStateManagementThread = new Thread() {
			public void run () {
				while (mThreadsOn == true) {

					// If Keepalives are true, send an AT command, periodically. This does two things
					// 1. prevents bt from going to sleep and so responsiveness is always very quick
					// 2. helps detect a stale connection quite quickly. 
					if (mSendKeepalives == true)  {
						if (checkCANErrorCounts() != true) {
							msg ("Resetting internal command session due to elevated CAN error counts: " + ebt.getCANErrorCounts());
							resetConnection();
						}
						
					}
					
					// TODO: If we're not in a ready state, then get ready!?
					if (getCurrentState() != 40) 
						enterStateForCommands();
					
					// sleep a bit. If we're interrupted, then break out of the loop. 
					try {Thread.sleep(5000);} catch (InterruptedException e) {break;}
				}// end of while. 
				
				if (DEBUG==true) msg ("Command state management terminated.");
				mStateManagementThread = null;
				
				
			}// end of run()
			
		};// end of new thread definition. 
		
		mStateManagementThread.start();
		
		return true;
	}

	/**
	 * Do things necessary to get from state 0 to 40!
	 * @return - returns TRUE if it was successful in getting into the new state!
	 */
	private boolean enterStateForCommands() {
		boolean ret;
		
		// if Bluetooth not even connected, then don't try. 
		if (ebt.isConnected() != true) {
			return false;
		}
		
		// if we haven't done our one-time first-time initialization string, then do that FIRST.
		if (mOneTimeInitSucceeded != true) {
			ret = ebt.sendATInitialization(oneTimeInit);
			if (ret == true)
				mOneTimeInitSucceeded = true;
			else {
				// bail right out! no point in starting threads and such. 
				msg ("WARNING: failed to resume due to failed one-time init");
				return false;
			}
		}
		
		// send the AT commands necessary to get into the right mood. 
		ret = ebt.sendATInitialization(resumeCommands);
		
		if (ret != true || confirmProtocol() != true) {
			msg ("ERROR: command session failed to resume due to failed resume init. ");
			return false;
		}
		
		setNewState(STATE_READY);
		
		msg ("READY!");
		
		return true;
	}
	
	/**
	 * To be used in the case when we detect a significant problem such as CAN errors. 
	 * We will reset the connection to a safe state, and then set our internal state to 0. 
	 */
	private void resetConnection() {
		if (ebt.isConnected() != true)
			return;
		
		ebt.sendATCommand2("ATWS");
		setNewState(0);
	}
	
	/**
	 * Perform a trivial network opperation, main purpose is to keep the network alive, with the secondary goal of updating a useful stat.
	 * @return - true if we're ERROR FREE. False if there are CAN errors. 
	 */
	private boolean checkCANErrorCounts() {

		String errorCounts = ebt.getCANErrorCounts();
		boolean isErrorFree = ebt.isCANErrorFree();
		
		mgStats.setStat("networkErrorCounts", errorCounts);

		return isErrorFree;
	}
	
	public void shutdown () {
		threadsOff();
	}
	
	private void msg (String message) {
		if (mMsgEventCallback == null)
			Log.d ("cmd: ",message);
		else
			mMsgEventCallback.onNewMessageArrived("cmd: " + message);
	}

	/**
	 * Call this method to register your very own callback to receive messages from this class! 
	 * @param eCallback - this is an instance of com.gtosoft.dash.EventCallback with the newMsg method overridden with your own code. 
	 */
	public void registerMsgCallback (EventCallback eCallback) {
		mMsgEventCallback = eCallback;
				
		if (DEBUG) msg ("CMDSess Registered debug callback.");
	}

	/**
	 * Send a command over the network asynchronously. 
	 * @param commandName
	 */
	public void sendCommand (String commandName) {

		final String _cmdname = commandName;
		
		mhWorkerHandler.post(new Runnable () {
			public void run() {
				sendCommand_SYNC(_cmdname);
			}
		});

	}
	
	/**
	 * synchronously (not in a separate thread thread) send the specified command. 
	 * - Added synchronized keyword because the new I/O logic is extremely fast and causing us to send new commands before the prior has been seen. 
	 * @param commandName
	 * @return
	 */
	public synchronized boolean sendCommand_SYNC (String commandName) {
		String response = "";
		
		if (ebt.isConnected() != true) {
			msg ("Ignoring command request - we're not connected.");
		}

		String command = dashdb.getCommandByName(commandName);
		
		if (command == null || command.length() < 1) {
			msg ("Command not known: " + commandName);
			return false;
		}
			
		String cmdParts[] = command.split(";");
		String thisCommand = "";
		
		for (int i=0;i<cmdParts.length;i++) {
			thisCommand = cmdParts[i].trim();

			// is it a Sleep command?
			if (thisCommand.startsWith("S")) {
				try {
					if (DEBUG) msg ("Encountered sleep sub-command.");
					String ssleepduration = thisCommand.substring(1);
					int isleepduration = Integer.valueOf(ssleepduration);
					Thread.sleep(isleepduration * 100);
				} catch (Exception e) {
					msg ("ERROR Processing sleep command: " + thisCommand + " Err=" + e.getMessage());
				}
				
				// move on to the next loop. 
				continue;
			}
			
			// is this a request to wake up all networks? TODO: Add ability to wake up specific networks?
			if (thisCommand.contains("WAKEALL")) {
				if (DEBUG) msg ("Encountered wake-up sub-command. performing all-node wakeup!");
				wakeUpAllNetworks();
				continue;
			}
			
			
			// Is a header present, if so then set headers. 
			if (cmdParts[i].length() > 11) {
				setHeaders (cmdParts[i].substring(0,12));
				thisCommand = cmdParts[i].substring(12);
			}

			// send it on its way. 
			response = ebt.sendOBDCommand(thisCommand);
			
		}// end of loop that loops through all the commands in the semi-colon separated list. 
		
		// This only checks the last command in the string but if it returns an error, return false. 
		if (response.contains("ERROR"))
			return false;
		
		return true;
	}
	
	public boolean setHeaders (String fourByteHeader) {
		String response = "";
		
		fourByteHeader = fourByteHeader.trim();
		
		if (fourByteHeader.length() < 11) {
			msg ("ERROR: Invalid Header: " + fourByteHeader);
			return false;
		}
		
		String priority = fourByteHeader.substring(0,2);
		String actualHeader = fourByteHeader.substring(3);


		// Check against what's already set, if already set then don't send again, otherwise send it and store it. 
		if (!mCurrentPriority.equals(priority)) {
			response = ebt.sendATCommandWithRetry("AT CP " + priority);
			if (response.length() == 0)
				return false;
			else
				mCurrentPriority = priority;
		}

		// Check against what's already set, if already set then don't send again, otherwise send it and store it. 
		if (!mCurrentHeader.equals(actualHeader)) {
			response = ebt.sendATCommandWithRetry("AT SH " + actualHeader);
			if (response.length() == 0)
				return false;
			else
				mCurrentHeader = actualHeader;
		}
		
		
		return true;
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
		
		isSuspended(); // updates stat. 
		
		// for now, collect stats on demand, right here. 
		mgStats.setStat("state", "" + getCurrentState());
		mgStats.setStat("currentPriority", "" + mCurrentPriority);
		mgStats.setStat("currentHeader", "" + mCurrentHeader);
		mgStats.setStat("initialInitSuccess", "" + mOneTimeInitSucceeded);

		return mgStats;
	}

	private void threadsOff() {
		mThreadsOn = false;
		if (mtWorkerThread != null) 
			mtWorkerThread.interrupt();

		if (mStateManagementThread != null) 
			mStateManagementThread.interrupt();
		
	}
	/**
	 * Suspend operations by this class/session and return the network to a usable state for others. We will go to sleep as long as we're suspended. 
	 * @return
	 */
	public boolean suspend () {
		final int sleepDuration = 200;
		
		// Already suspended?
		if (isSuspended() == true) 
			return true;

		threadsOff();
		
		int loopCount = 0;
		while (!isSuspended()) {
			if (!EasyTime.safeSleep(sleepDuration)) break;
			loopCount++;
			if (loopCount == 50){
				msg ("WARNING: suspend loop has surpassed " + (sleepDuration * 50) + "ms and counting...");
				mgStats.setStat("timeToSuspend", ">" + (loopCount * sleepDuration) + "ms");
			}
		}

		mgStats.setStat("timeToSuspend", "" + (loopCount * sleepDuration) + "ms");
		if (DEBUG) msg ("DEBUG: Gracefully exited suspend while loop after " + (loopCount * sleepDuration) + "ms.");

		// Send AT commands to get out of current state and back into a regular state. 
		return ebt.sendATInitialization(suspendCommands);
	}


	/**
	 * Perform any actions necessary to resume from a suspend. This method may also be used during initial startup. 
	 * @return
	 */
	public boolean resume() {

		resetVariables();

		// Starts a thread to which we can assign work and it will perform it asynchronously. 
		startWorkerThread();

		// start the state management thread. 
		startStateManagementThread();

		return true;
	}

	/**
	 * Perform a confirmation check to make sure that the protocol we want, is the protocol that is currently set on the device.
	 * The main purpose of this method is to prevent us from "thinking" we're in a  
	 * @return - true if the desired interface type is what is selected. 
	 */
	private boolean confirmProtocol() {
		
		// then we need to use ATDPN to get the currently set protocol, and make sure it is B
		String currentProtocol = ebt.sendATCommand2("ATDPN");
		
		if (!currentProtocol.equals("B")) {
			msg ("ERROR: Protocol validation failed. Device doesn't support SWCAN? Current device protocol is " + currentProtocol + " but expected 'B'");
			return false;
		}
		
		return true;
	}


	/**
	 * worker thread doesn't count. it runs all the time. 
	 * @return - returns true if we're suspended, false otherwise. 
	 */
	public boolean isSuspended () {
		if (mThreadsOn == false && mStateManagementThread == null) {
			mgStats.setStat("isSuspended", "true");
			return true;
		} else {
			mgStats.setStat("isSuspended", "false");
			return false;
		}
	}



	/**
	 * 1. Switch to 11-bit transmit mode
	 * 2. send "100 00" (header=100, data=00)
	 * 3. Switch back to 29-bit transmit mode. 
	 */
	public void wakeUpAllNetworks() {
		if (mhWorkerHandler != null) {
			mhWorkerHandler.post(new Runnable () {
				public void run () {
					switchTo11BitTransmitMode();
					sendWakeupAllNodes();
					switchTo29BitTransmitMode();
				}

				});// end of post
		}// end of "if worker handler isn't null"
	}// end of "wake up all networks" command. 

	
	private boolean switchTo29BitTransmitMode() {
		if (DEBUG) msg ("Switching transmit mode to SWCAN + 29-bit");
		
		if (!ebt.sendATInitialization(new String[] {"AT PP 2C SV 40"})) return false;
		// warm-start the ELM chip, so the PP parameter change takes affect. Do this by running the resume command sequence, so we end up in a predictable state. 
		if (!ebt.sendATInitialization(resumeCommands)) return false;
		
		return true;
	}

	private boolean sendWakeupAllNodes() {
		String response;
		
		if (DEBUG) msg ("Setting headers and transmiting 11-bit wake-up message...");
		// set the header to "100"
		if (!ebt.sendATInitialization(new String[] {"AT SH 100"})) return false;
		// Transmit a CAN message consisting of a single null character (we have to send at least one byte. If we could send zero bytes we would do that, since just the header "100" is needed).
		response = ebt.sendOBDCommand("00");
		response = ebt.sendOBDCommand("00");
		if (!ebt.sendATInitialization(new String[] {"AT SH 621"})) return false;
		response = ebt.sendOBDCommand("01 7F"); // lites up everything as far as I can tell.  
		response = ebt.sendOBDCommand("01 02"); // Keyfob sets this network level upon press 
//		response = ebt.sendOBDCommand("00 19"); // OnStar is alive when network is in this mode. 
//		response = ebt.sendOBDCommand("00 19"); // OnStar is alive when network is in this mode. 
//		response = ebt.sendOBDCommand("00 19"); // OnStar is alive when network is in this mode. 
//		response = ebt.sendOBDCommand("00 19"); // OnStar is alive when network is in this mode. 
//		response = ebt.sendOBDCommand("00 19"); // OnStar is alive when network is in this mode. 
		if (DEBUG) msg ("wake-up message has been sent. Response was: " + response);
		
		return true;
	}

	private boolean switchTo11BitTransmitMode() {
		if (DEBUG) msg ("Switching transmit mode to SWCAN + 11-bit");
		if (!ebt.sendATInitialization(new String[] {"AT PP 2C SV C0"})) return false;
		// warm-start the ELM chip, so the PP parameter change takes affect. Do this by running the resume command sequence, so we end up in a predictable state. 
		if (!ebt.sendATInitialization(resumeCommands)) return false;
		return true;
	}

	
}// end of CommandSession Class. 
