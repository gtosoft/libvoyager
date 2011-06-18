/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.util.Iterator;
import java.util.TreeSet;

import android.util.Log;

import com.gtosoft.libvoyager.session.OBD2Session;


public class RoutineScan {
	
	int scanLoopDelay = 0;
	
	final boolean DEBUG = false;
	
	int successfulRequests = 0;
	
	GeneralStats mgStats = new GeneralStats();
	
	TreeSet<String> mDPNs = new TreeSet<String> ();
	
	OBD2Session mOBD = null;
	PIDDecoder  mPD  = null;

	boolean mThreadsOn = true;
	
	Thread mScanThread = null;
	
	public RoutineScan(OBD2Session newOBDSess, PIDDecoder pd) {
		mOBD = newOBDSess;
		mPD  = pd;

		mgStats.setStat("successfulReqs","0");
		
		startThreads();
	}

	
	private void startThreads () {
		if (mScanThread != null) 
			return;
		
		// define the actual routine scan thread. 
		mScanThread = new Thread() { 
			public void run () {
				int loops = 0;
				while (mThreadsOn == true) {
					loops++;
					mgStats.setStat("loops", "" + loops);


					// If we're not connected, sleep a bit. If we're connected, then scan the DPNs!
					if (mOBD.getCurrentState() < 40) {
						EasyTime.safeSleep(500);
						continue;
					} else {
						// make a request for each and every DPN in the set. 
						requestAllDPNs();
					}

					
					if (scanLoopDelay > 0) 
						EasyTime.safeSleep(scanLoopDelay);
					
				}// end of while. 
			}// end of run().
		};// end of mscanthread definition. 
		mScanThread.start();
	}

	/**
	 * sets the number of milliseconds to pause between each iteration of the routine scan. default is zero. 
	 * @param delayMillis
	 */
	public void setScanLoopDelay (int delayMillis) {
		scanLoopDelay = delayMillis;
	}
	
	/**
	 * For each DPN in the set, make an OBD request for it. This triggers PidDecoder to make a request, and then a subsequent DataArrived event gets fired, which our parent is hopefully listening for. 
	 * @return
	 */
	private boolean requestAllDPNs() {
		
		// sanity check.
		if (mOBD.getCurrentState() < OBD2Session.STATE_OBDCONNECTED) {
			// Warning! We're
			if (DEBUG) msg ("not getting DTCs because OBD not connected. state=" + mOBD.getCurrentState());
			return false;
		}
		
		Iterator<String> i = mDPNs.iterator();
		
		String thisDPN = "";
		while (i.hasNext()) {
			
			// a try-catch is necessary because during a shutdown, various objects may suddenly become unavailable and so we can't reference them. 
			try {
				thisDPN = i.next();
			} catch (Exception e) {
				if (DEBUG) msg ("ERROR while iterating through DPNs. e=" + e.getMessage());
				// bust out of the loop. 
				break;
			}

			// Make a request but we don't care about the response in this context. Rather, the mere fact that we made the request, will kick off logic within the pidDecoder to fire off "new data arrived" events, which is what the other classes will be looking for. 
			mPD.getDataViaOBD(thisDPN);
			
			// stats - log number of successful stats. 
			successfulRequests++;
			mgStats.setStat("successfulReqs","" + successfulRequests);
		}
		
		return true;
	}
	
	public void shutdown () {
		mThreadsOn = false;

		removeAllDPNs();

		try {
			if (mScanThread != null) mScanThread.interrupt();
		} catch (Exception e) { }
		
	}

	/**
	 * Add a dpn to the routine scan set!
	 * @param DPN
	 */
	public void addDPN (String DPN) {
		if (!mDPNs.contains(DPN))
			mDPNs.add(DPN);
	}

	/**
	 * Remove all DPNs from the routine scan set. 
	 */
	public void removeAllDPNs () {
		mDPNs.clear();
	}

	/**
	 * Removes a single DPN from the routine scan set. 
	 * @param DPN
	 */
	public void removeDPN (String DPN) {
		mDPNs.remove(DPN);
	}

	public GeneralStats getStats () {
		return mgStats;
	}

	private void msg (String message) {
		Log.d("RS",message);
	}
}
