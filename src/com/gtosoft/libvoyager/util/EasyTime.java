/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.SystemClock;
import android.util.Log;


public class EasyTime {
	final boolean DEBUG=false;
	long mUpTimeMillis = 0;
	Thread mtUpdate = null;
	// default update interval - milliseconds. This is how long we sleep between updates to the timestamp. 
	int mUpdateInterval = 200;
	int mTimeUpdates = 0;
	// as long as this is true, any threads owned by this class may still run.
	boolean mThreadsEnabled = true;
	long mCurrentUptimeMillis = 0;
	long mCurrentUptimeSeconds = 0;
	long mInstantiationTime = 0;
	int numTimeRequests = 0;

	
	
	/**
	 * Constructor. 
	 */
	public EasyTime() {
		// NEW: don't start the thread automatically. Only start it if necessary. We now have this method to help: updateTimesNoThread();
		
		mInstantiationTime = getUptimeMillis() / 1000L; // SystemClock.elapsedRealtime() / 1000L;

		// temp. we don't have to start it right away.
		// startUpdateThread();
	}

	
	/**
	 * Starts the updater thread in the background which updates the timevariables. 
	 * This is useful in cases where a method is hammering the time methods. By calculating it at an interval in the background, we prevent excessive math operations. 
	 * In other words, the updater thread updates the EasyTime variables, and outside methods can hammer those variables all they want. 
	 * @return
	 */
	private synchronized boolean startUpdateThread() {
		if (mtUpdate != null) 
			return false;
		
		mtUpdate = new Thread() {
			public void run () {
				while (mThreadsEnabled == true) {
					updateTimes();
					
					// Sleep, or if we can't, then bust out. 
					if (EasyTime.safeSleep(mUpdateInterval))
						break;
				}// end of while()
				if (DEBUG) msg ("updateThread has terminated. mte=" + mThreadsEnabled);
			}// end of run()
		};
		
		mtUpdate.start();

		return true;
	}// end of start updatethread method

	private void updateTimes() {
		
		mTimeUpdates++;
		
		// this seems to return the number of seconds since the thread came to life? 
		mCurrentUptimeMillis = SystemClock.elapsedRealtime();
		// New: And we Subtract seconds since we were instantiated. This is mainly to help with getUptimeSecondsINT() method's modulus to not roll over during regular execution. 
		mCurrentUptimeSeconds = (mCurrentUptimeMillis / 1000L) - mInstantiationTime;

		// Check and see if we need to start the background updater thread.
		// NEW: Blocked this code with a true==false. The thread seems to have problems getting enough priority to stay active. it leads to strange problems!
		if (true == false && mtUpdate == null && mTimeUpdates > 1000) {
			long hitRate = mCurrentUptimeMillis * 60;
			hitRate /= mTimeUpdates;
			msg ("ET Thread ID" + Thread.currentThread().getId() + " surpassed 1000 hits with a hit rate of " + hitRate + " per minute. activating optimization logic.");
			startUpdateThread();
		}
		

	}
	
	/**
	 * @return - returns current uptime millis time stamp. 
	 */
	public long getUptimeMillis () {
//		mCurrentUptimeMillis = SystemClock.elapsedRealtime();
		
		if (mtUpdate == null)
			updateTimes();
		
		return mCurrentUptimeMillis;
	}

	/**
	 * @return - returns uptime of this instance of EasyTime. In seconds. 
	 */
	public long getUptimeSeconds() {

		// If update thread not running, get time manually. 
		if (mtUpdate == null)
			updateTimes();
		
		return mCurrentUptimeSeconds;
	}
	
	/**
	 * @return - an integer of the current seconds & 0xFFFF so its really just seconds incrementing. 
	 */
	public int getUptimeSecondsINT() {
		int secondsInt = 0;
		
		secondsInt = (int)(getUptimeSeconds() & 0xFFFF);
		
		return secondsInt;
	}
	

	public static long getUnixTime () {
		return System.currentTimeMillis() / 1000L;
	}


	/**
	 * Returns a string containing the current time stamp. 
	 * @return - a string. 
	 */
	public static String currentDateAndTimeStamp() {
		String ret = "";
		
		Date d = new Date();

		// TODO: Don't define this every single time!
		SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss a zzz");
		
		ret = formatter.format(d);

		return ret;
	}

	/**
	 * Sleep for the specified number of milliseconds. 
	 * @param millis - number of milliseconds to pause the current thread. 
	 * @return - false if the sleep was interrupted, true otherwise. 
	 */
	public static boolean safeSleep (int millis) {
		try {
			Thread.sleep (millis);
		} catch (InterruptedException e) {
			return false;
		}

		return true;
	}

	/**
	 * Sets the number of milliseconds between updates to the time variable. 
	 * @param millisToWaitBetweenUpdates - number of milliseconds. 
	 */
	public void setMillisBetweenUpdate (int millisToWaitBetweenUpdates) {
		mUpdateInterval = millisToWaitBetweenUpdates;
	}

	
//	private void XXX_updateTimesNoThread () {
//		// If thread not running, 
//		if (mtUpdate == null) {
//			updateTimes();
//			numTimeRequests++;
//			
//			// If there have been 10 time requests then maybe it's a good idea to start the thread.  
//			if (numTimeRequests > 10) {
//				if (DEBUG) msg ("Starting convenience thread.");
//				startUpdateThread();
//			}
//		}
//	}

//	/**
//	 * Returns a string containing the current time stamp. 
//	 * @return - a string. 
//	 */
//	// added _ugly because this method produces a TON of garbage for GC.
//	public static String currentTimeStamp_ugly() {
//		String ret = "";
//		
//		Date d = new Date();
//		
//		// TODO: Don't define this every single time!
//		SimpleDateFormat timeStampFormatter = new SimpleDateFormat("hh:mm:ss");
//		
//		ret = timeStampFormatter.format(d);
//		
//		return ret;
//	}


	private void msg (String message) {
		Log.d("EasyTime",message);
	}


	/**
	 * Shuts down the EasyTime class and prepares it for gc. 
	 */
	public void shutdown () {
		
		if (DEBUG) msg ("shutdown(): shutting down.");
		mThreadsEnabled = false;

		// interrupt the update thread, in case its sleeping.
		if (mtUpdate != null)
			mtUpdate.interrupt();

	}




	
	
}// end of EasyTime class. 
