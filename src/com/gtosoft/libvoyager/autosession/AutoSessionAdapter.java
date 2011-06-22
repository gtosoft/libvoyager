package com.gtosoft.libvoyager.autosession;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.util.Log;

import com.gtosoft.libvoyager.android.ServiceHelper;
import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.session.HybridSession;
import com.gtosoft.libvoyager.svip.SVIPTCPServer;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;

/**
 * 
 * @author Brad Hein / GTOSoft LLC
 *
 * This class will manage a HybridSession in a way that requires no user intervention. 
 * For example, right off the bat we'll automatically detect the hardware type, and then 
 * try to detect the CAN network if one is present.  We'll also support muultiple interfaces
 * to the outside world such as TCP sockets (Command or Events). 
 *  
 */


public class AutoSessionAdapter {
	final boolean DEBUG = true;
	HybridSession	 hs; 					// hybrid session is the top of the libVoyager pyramid. it manages everything else. 
	Context			 mctxParentService;		// a reference to the parent context so that we can do things like work with Bluetooth. 
	BluetoothAdapter mbtAdapter;
	DashDB 			 ddb;
	GeneralStats	 mgStats = new GeneralStats();
	ServiceHelper 	 msHelper;

	// SVIP server component. listens for incoming connections. its lifecycle should track that of the hybridsession. 
	SVIPTCPServer mSVIPServer;

	
	AutoSessionMoni  mAutoMoni;
	AutoSessionOBD   mAutoOBD;

	// This hands messages up the chain to our parent. These messages are of an "out of band" nature.  
	EventCallback mOOBDataHandler = null;

	
	/**
	 * Default Constructor. 
	 * This method gathers necessary objects from parent class and pulls the show together. 
	 * @param serviceContext
	 * @param btAdapter
	 */
	public AutoSessionAdapter(Context serviceContext, BluetoothAdapter btAdapter, EventCallback newOOBHandler) {
		mctxParentService = serviceContext;
		mbtAdapter 		  = btAdapter;
		registerOOBHandler(newOOBHandler);
		
		msg ("Spinning up DB");
		ddb = new DashDB(mctxParentService);
		msg ("DB Ready.");

		// Kicks off a BT Discovery or other way to "choose" a peer.
		choosePeerDevice();
	}

	/**
	 * Choose a device, whether by discovery or other means, and then set up the hybrid session for it. 
	 */
	private void choosePeerDevice () {
        msHelper = new ServiceHelper(mctxParentService);
        
        msHelper.registerChosenDeviceCallback(chosenCallback);
		// The discovery process should choose a single device and run setupHSession against it (via the chosenCallback). 
        msHelper.startDiscovering();
	}
	
    /** 
     * libVoyager can do the BT discovery and device choosing for you. When it finds/chooses a device  it runs the device chosen callback.
     * This method defines what to do when a new device is found.  
     */
    EventCallback chosenCallback = new EventCallback () {

        @Override
        public void onELMDeviceChosen(final String MAC) {
//        	new Thread() {
//        		public void run () {
			mgStats.incrementStat("sessionSetupCount");
			long startTime = EasyTime.getUnixTime();
			setupHSession(MAC);
			long stopTime = EasyTime.getUnixTime();
			mgStats.setStat("timeTosetupSession", stopTime - startTime);
//        		}
//        	}.start();
        }
        
    };

	
	
	/**
	 * Instantiate hybridsession. HS will in turn kick off a bluetooth connection attempt, which if 
	 * successful will fire an OOB event to us telling us the IO is connected, at which time we'll 
	 * do a hardware detection routine. 
	 */
	private synchronized boolean setupHSession (String btAddr) {
	   	mgStats.incrementStat ("session.setupcount");

	   	// If HS is null this is the initial connect. Otherwise it's a reconnection. 
		if (hs != null) {
			msg ("WARNING: hs already set up. setting up again. ");
			hs.shutdown();
			mSVIPServer.shutdown();
		} else {
			// Instantiate the hybridsession. It will start by trying to connect ot the bluetooth peer. 
			hs = new HybridSession(mbtAdapter, btAddr, ddb, mOOBEventCallback);
			
			mSVIPServer = new SVIPTCPServer(hs);
		}
		
		// Info/debug message handler.
		hs.registerMsgCallback(mecbMsg);

		// OOB messages coming from lower level classes
		hs.registerOOBHandler(mOOBEventCallback);
		
		// Register to be notified any time a datapoint is decoded. 
		hs.registerDPArrivedCallback(mDPArrivedCallback);

		if (DEBUG) hs.setRoutineScanDelay(1000);
		
		mSVIPServer = new SVIPTCPServer(hs);
		
		
		return true;
	}

	private boolean discoverNetwork () {
		
		long startTime = EasyTime.getUnixTime();

		mgStats.setStat("timeToDiscoverNetwork", "running...");

		
    	// detect hardware and network. Retry forever until we get it or are disconnected. 
    	while (hs.getEBT().isConnected() == true && hs.runSessionDetection() != true) {
        	setCurrentStateMessage("Autodiscovering network...");
    		mgStats.incrementStat("hsDetectTries");
    		msg ("Running session detection. EBT.connected=" + hs.getEBT().isConnected() + " hsdetection.valid=" + hs.isDetectionValid());
    	}
    	
    	// If the above while loop broke out because of a failure, then bust out of the session setup process. 
    	if (hs.isDetectionValid() != true || hs.getEBT().isConnected() != true) {
    		setCurrentStateMessage("network discovery failed");
        	if (hs.getEBT().isConnected() != true) setCurrentStateMessage("Bluetooth disconnected. will reconnect.");
    		long stopTime = EasyTime.getUnixTime();
    		mgStats.setStat("timeToDiscoverNetwork", stopTime - startTime);
    		return false;
    	}

    	setCurrentStateMessage("network discovery successful");
    	long stopTime = EasyTime.getUnixTime();
		mgStats.setStat("timeToDiscoverNetwork", stopTime - startTime);
		return true;
	}
	

	/**
	 * This eventcallback will get executed (by Hybridsession) any time an out-of-band message is generated from any classes below us in the Voyager stack.
	 */
	EventCallback mOOBEventCallback = new EventCallback () {
		@Override
		public void onOOBDataArrived(String dataName, String dataValue) {
//			msg ("(event) OOB Message: " + dataName + "=" + dataValue);
			
			//  If the OOB message is that of the I/O layer just having connected, then kick off a Hybrid Session detection routine. 
			//	If that is successful then move forward with setup
			//	If unsuccessful, then continuously re-try as long as bt remains connected.
			if (dataName.equals(HybridSession.OOBMessageTypes.IO_STATE_CHANGE)) {
				if (dataValue.equals("0")) {
					// Bluetooth just disconnected.
					msg ("Bluetooth just disconnected.");
					if (hs.getEBT().isIODoneTrying() == true) {
						sendOOBMessage("ebt.donetrying", "true");
					}
				} else {
					// Bluetooth just connected!
					sendOOBMessage("ebt.donetrying", "false");
					msg ("Bluetooth just connected. kicking off config thread. ");
					new Thread() {
						public void run () {
							setCurrentStateMessage("BT Connected. Configuring...");
							boolean success = discoverNetwork();
							if (success == true) {
								setCurrentStateMessage("Network configured.");
								// Set up a home session here. the home session will handle main processing of whichever type connection we have.
								if (hs.getHardwareDetectData().isMoniSupported().equals("true") || hs.getHardwareDetectData().isHardwareSWCAN().equals("true")) { 
									// Stars are aligned. Go moni.
									// TODO: Don't necessary switch to moni right now unless the attached CAN network is SUPPORTED/RECOGNIZED.
									mAutoMoni = new AutoSessionMoni (hs, mOOBEventCallback);
								} else {
									// Fall back on OBD. 
									mAutoOBD = new AutoSessionOBD(hs,mOOBEventCallback);
								}
							} 
							
						}
					}.start();
				}
				// TODO: Kick off hardware-type detection. Hopefully it can use cached data as necessary to speed up successive executions. 
			}
			
			if (dataName.equals(HybridSession.OOBMessageTypes.AUTODETECT_SUMMARY)) {
				// TODO: Make sure autodetect was successful. If so, then move forward with the next step of being autonomous. 
			}
			
			// Route it upwards. 
			sendOOBMessage(dataName, dataValue);
		}
	};

	/**
	 * This eventcallback will get executed (by Hybridsession) any time a debug/info message is generated by the code.   
	 */
	EventCallback mecbMsg = new EventCallback () {
		@Override
		public void onNewMessageArrived(String message) {
			msg ("(event)ASA: " + message);
		}
	};
	
	/**
	 * This eventcallback will get executed (by Hybridsession) any time a DP is decoded. 
	 */
	EventCallback mDPArrivedCallback = new EventCallback () {
		@Override
		public void onDPArrived(String DPN, String sDecodedData, int iDecodedData) {
//			msg ("(autoSession event)DP: " + DPN + "=" + sDecodedData);
//			sendOOBMessage("teststate", DPN + "=" + sDecodedData);
		}
	};
	
	/**
	 * - getStats returns the current generalStats object. AutoSessionAdapter is at the top of the pyramid, just above HybridSession.
	 * - getStats also gathers all stats from lower in the libvoyager stack and 
	 * @return - returns a generalStats object.
	 */
	public GeneralStats getStats () {
		
		// TODO: Add any necessary last-minute parameters now.
		
		// Merge stats from hybrid session. 
		if (hs != null) mgStats.merge("hs", hs.getStats());
		if (mSVIPServer != null) mgStats.merge("svip", mSVIPServer.getStats());
		
		// TODO: Merge stats from any connectors such as a command socket or an events socket. 

		return mgStats;
	}
	
	private void setCurrentStateMessage (String m) {
		mgStats.setStat("state", m);
		sendOOBMessage("autosessionadapter.state", m);
		msg (m);
	}
	
	/**
	 * Allow upper layers to register an event listener to be notified of out of band information.
	 * We pass information to the upper layers via this callback, using our local method sendOOB....   
	 * @param newOOBHandler
	 */
	public void registerOOBHandler (EventCallback newOOBHandler) {
		mOOBDataHandler = newOOBHandler;
	}

	
	/**
	 * Sends a message through the OOB pipe. 
	 * @param dataName
	 * @param dataValue
	 */
	private void sendOOBMessage (String dataName, String dataValue) {
		if (mOOBDataHandler == null)
			return;
		
		mOOBDataHandler.onOOBDataArrived(dataName, dataValue);
	}
	
	private void msg (String m) {
		Log.d("AutoSessionAdapter","[T=" + getThreadID() + "] " + m);
	}

    private String getThreadID () {
        final String m = "[T" + Thread.currentThread().getId() + "]";
        return m;
}

	public void shutdown () {
		if (hs != null) hs.shutdown();
		
		// shut down the SVIP socket and any open connections. 
		if (mSVIPServer != null) mSVIPServer.shutdown();
	}

	public HybridSession getHybridSession () {
		return hs;
	}
}
