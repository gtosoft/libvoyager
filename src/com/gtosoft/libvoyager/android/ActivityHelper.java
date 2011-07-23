/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */
// test comment.
package com.gtosoft.libvoyager.android;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.OOBMessageTypes;

/**
 * This class will contain useful things that are helpful to users of
 * Activities. This might be for example the ability to perform a BT discovery
 * and connect to the first discovered device.
 * Basic use:  
 * 	1.  Register your "found elm device" callback: aHelper.registerChosenDeviceCallback(chosenCallback);
   	2.  aHelper.startDiscovering();
   	3.  Expect to get a call to your chosenCallback EventCallback. It will pass you the MAC of the chosen device. 
 * 
 */  
public class ActivityHelper {
	Context mctxParent = null;
	EventCallback mOnELMDeviceChosenCallback = null;
	EventCallback mParentOOBHandler = null;
	private final boolean DEBUG = true;
	Thread mDiscoveryThread = null;

	/**
	 * Default constructor - obtain context of the parent activity.
	 * @param c
	 */
	public ActivityHelper(Context ctxOfActivity) { 
		mctxParent = ctxOfActivity;
	}

	/**
	 * Registers all broadcast receivers. When adding broadcast receivers here,
	 * also add an unregister statement in unregisterReceivers()
	 * 
	 * @return
	 */
	private boolean registerBroadcastReceivers() {
		if (DEBUG) msg ("register broadcast receivers.");
		
		if (mctxParent == null) {
			msg ("ERROR: parent ctx is null");
			return false;
		}
		
		if (mbtDiscoveryReceiver == null) {
			msg ("ERROR: btdiscovery receiver is null");
		}

		// Register to receive bluetooth device discovery messages
		if (DEBUG) msg("Registering for bluetooth discovery messages.");
		IntentFilter if_btDiscovery = new IntentFilter();
		if_btDiscovery.addAction(BluetoothDevice.ACTION_FOUND);
		
		if (if_btDiscovery == null) 
			if (DEBUG) msg ("intentfilter null");
		
		((Activity) mctxParent).registerReceiver(mbtDiscoveryReceiver, if_btDiscovery);
		
		return true;
	}



	/**
	 * Take action for each bluetooth device discovered. This method is
	 * registered by the registerReceiver method during Activity startup.
	 */
	private BroadcastReceiver mbtDiscoveryReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action.equals(BluetoothDevice.ACTION_FOUND)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				if (device == null || device.getName() == null || device.getAddress() == null) {
					String addr="";
					try { if (device != null ) addr = device.getAddress(); 	} catch (Exception e) {	}
					if (DEBUG) msg("Throwing out null discovered device. MAC=" + addr);
					return;
				}

				// Does it have a name that suggests it is an OBD device?
				if (	device.getName().toLowerCase().contains("obd") || 
						device.getName().toLowerCase().contains("cantool") ||
						device.getName().toLowerCase().contains("plx")) {
					msg("ELM Bluetooth device FOUND! Name=" + device.getName() + " MAC=" + device.getAddress());
					// unregister broadcast receiver!
			    	unregisterAllReceivers();
					// We have our device, so run the "we found a device" callback. 
					if (mOnELMDeviceChosenCallback == null) {
						msg ("ERROR: you forgot to register a ELMDeviceChosen callback! We found a device but can't do anything!");
						return;
					}
					mOnELMDeviceChosenCallback.onELMDeviceChosen(device.getAddress());
				} else {
					if (DEBUG)
						msg("Throwing out bluetooth device with Name=" + device.getName() + " MAC=" + device.getAddress());
				}
			}// end of "if it was a found action".
		}// end of onReceive
	};// end of our bluetooth discovery broadcast receiver method.

	private synchronized boolean startDiscovery() {
    	// Kicks off a discovery. 
		registerBroadcastReceivers();
		
    	try {
			BluetoothAdapter.getDefaultAdapter().startDiscovery();
		} catch (Exception e) {
			msg ("ERROR: Unable to start a bluetooth discovery session: " + e.getMessage());
			return false;
		}
		
		return true;
    }

    private void unregisterAllReceivers() {
    	if (mctxParent == null) {
    		msg ("Unable to de-register broadcast receivers because parent context is unset");
    		return;
    	}
    	    	
		try {
			mctxParent.unregisterReceiver(mbtDiscoveryReceiver);
		} catch (Exception e) {
			msg ("Error unregistering mbtDiscoveryReceiver: E=" + e.getMessage());
		}
	}

	private void msg(String m) {
		Log.d("ActivityHelper", m);
	}

	public void startDiscovering () {
        startDiscovery();
        startDiscoveryEventsThread();
	}

	public void shutdown () {
    	unregisterAllReceivers();
    	
	}
	
	/**
	 * This thread should get kicked off when discovery is started. the thread dies as soon as discovery is finished. 
	 * Just before the thread completely exits, it kicks an OOB message, indicating the end of BT discovery. 
	 * @return
	 */
	private boolean startDiscoveryEventsThread () {
		
		// if the thread is already running. 
		if (mDiscoveryThread != null) {
			return false;
		}
		
		mDiscoveryThread = new Thread() {
			public void run () {
				final int SLEEP_TIMESLICE = 500;
				// loop while discovery is in progress. 
				int loops=0;
				sendOOBEvent(OOBMessageTypes.DISCOVERING_STATE_CHANGE, "" + isDiscovering());
				while (isDiscovering() == true) {
					// sleep. If interrupted, break out of the loop. 
					if (!EasyTime.safeSleep(SLEEP_TIMESLICE)) break;
					loops++;
				}// end of while()

				if (DEBUG) msg ("Discovery ended after " + (loops * SLEEP_TIMESLICE)/1000 + " s.");
				
				// discovery has ended. 
				sendDiscoveryEvent(false);
				
			}// end of run();
		}; //end of thread instantiation. 

		mDiscoveryThread.start();
		
		return true;
	}

	private boolean isDiscovering() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

		if (adapter == null) {
			if (DEBUG) msg ("Warning: Bluetooth not activated or not supported. Adapter is null");
			return false;
		}

		return adapter.isDiscovering();
	}
	
	private void sendDiscoveryEvent (boolean discoveryStatus) {
		sendOOBEvent(OOBMessageTypes.DISCOVERING_STATE_CHANGE, "" + discoveryStatus);
	}
	
	/**
	 * send an OOB event. pass it up the chain. 
	 * @param dataName
	 * @param dataValue
	 */
	private void sendOOBEvent (String dataName, String dataValue) {
		if (mParentOOBHandler != null) {
			mParentOOBHandler.onOOBDataArrived (dataName,dataValue);
		}
	}
	
	/**
	 * Allows a class to register a single EventCallback handler to be executed when an OOB event is generated by ActivityHelper. 
	 * @param newOOBHandler
	 */
	public void registerOOBHandler (EventCallback newOOBHandler) {
		mParentOOBHandler = newOOBHandler;
	}
	
	/**
	 * Call this to register an eventcallback with onELMDeviceChosen overridden with your code. 
	 * @param chosenCallback
	 */
	public void registerChosenDeviceCallback (EventCallback chosenCallback) {
		mOnELMDeviceChosenCallback = chosenCallback;
	}
}
