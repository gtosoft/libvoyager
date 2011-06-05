package com.gtosoft.libvoyager.autosession;

import android.util.Log;

import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;

/**
 * Auto sessions are something I dreamed up today at the book store. 
 * They will run at a higher level than the traditional OBD/moni sessions. 
 * They will provide a fully automated presence. In other words, if you instantiate an OBD Auto-Session, it will 
 * start up, configure stuff, and work all by itself, without any user intervention. 
 * 
 * The purpose of these is to facilitate services using libvoyager to run on their own. 
 * Auto Sessions will allow outside forces to change their state. For example a TCP/IP adapter. 
 * 
 * @author Brad Hein / GTOSoft LLC
 *
 */

public class AutoSessionMoni {
	
	GeneralStats	mgStats = new GeneralStats();
	EventCallback	mOOBDataHandler = null;
	

	/**
	 * Default constructor. 
	 */
	public AutoSessionMoni() {
		
	}
	
	public GeneralStats getStats () {
		// TODO: add any last-minute stats here. 
		return mgStats;
	}
	
	private void sendOOBMessage (String dataName, String dataValue) {
		if (mOOBDataHandler == null)
			return;

		mOOBDataHandler.onOOBDataArrived(dataName, dataValue);
	}

	private void msg (String m) {
		Log.d("AutoSessionOBD",m);
	}
	
	public void shutdown () {
		
	}

}
