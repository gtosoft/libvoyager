/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.net;

import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * This class brokers data between Voyager and network assets. 
 */


	
public class GTONet {
	Thread mWorkerThread 	= null;   // used to identify the worker thread, in case we need to kill it for example. 
	Handler mWorkerHandler	= null; // used to post work to the worker thread.
	
	// to be used to loop and run every X hours and verify license validity. 
	Thread mLicenseCheckThread = null;
	
	private List<NameValuePair> mHTTPVariables;

	// the constructor. 
	public GTONet(List<NameValuePair> nameValuePairs) {
		
		mHTTPVariables = nameValuePairs;

		// start the worker thread. It just chills until we send it work. 
		startWorkerThread();
		
		// Start the thread that wakes up very infrequently just to check the license key. 
		startLicenseCheckThread();

	}

	/**
	 * Stop all threads owned by this class instance.
	 */
	public void stopThreads () {
		
		if (mWorkerThread != null) {
			try {mWorkerThread.interrupt();} catch (Exception e) {
			}
		}
		
		if (mLicenseCheckThread != null) {
			try {mLicenseCheckThread.interrupt();} catch (Exception e) {
			}
		}
		
	}// end of stopThreads()

	/**
	 * convenience method. 
	 */
	public void shutdown () {
		stopThreads();
	}

	private void startLicenseCheckThread () {
		if (mLicenseCheckThread != null)
			return;
		
		// create the thread. 
		mLicenseCheckThread = new Thread () {
				public void run () {
					boolean ret = false;
					int sleepduration=60000;
					
					while (1==1) {
						ret = checkLicenseValidity();
						
						if (ret == false)
							sleepduration = 300 * 1000; // 5 minutes
						else
							sleepduration = 1000*60*60*12; // 12 hours.
						
						// sleep for half a day. 
						try {Thread.sleep(sleepduration);} catch (InterruptedException e) {
							// break out of the loop if interrupted. 
							break;
						}// end of try-catch.
					}// end of while()
				}// end of run(). 
		};// end of thread definition.
		
		mLicenseCheckThread.start();
		
	}
	
	/**
	 * Creates a new "worker" thread, to which we can post new work to be performed asynchronously from the main (UI) thread. 
	 */
	public void startWorkerThread() {
		if (mWorkerThread != null )
			return;
		
		// create a new thread. 
		mWorkerThread = new Thread () {
			public void run() {
				//mWorkerHandler.getLooper();
				Looper.prepare();

				// set the worker to a new handler owned by this thead. 
				mWorkerHandler = new Handler();
				
				// main loop. just loop, sleeping, waiting for work. 
				while (1==1) {
					Looper.loop();
					try {Thread.sleep (1000);} catch (InterruptedException e) {
						break; // break out of the while loop, kills the thread. 
					}
					
				}// end while loop. 
				Looper.myLooper().quit();
			}// end of thread run() 
		};
		mWorkerThread.start();
		
		// sleep for a second while thread starts... Prevents calling tasks from hitting it before it has a chance to start!
		try {Thread.sleep(1000);} catch (InterruptedException e) {
			return;
		}

	}
	
	/**
	 * Check validity of the user's license. 
	 * @return
	 */
	private boolean checkLicenseValidity() {
		String url = "http://apps.gtosoft.com/check/checkDash.jsp";
				
		try {postData(url, mHTTPVariables);} catch (Exception e) {
			Log.d("Net","_Exception: " + e.getMessage());
			return false;
		}  

		// TODO: Do more in-depth license verification. 
		
		return true;
	}
	

	
	/**
	 * @param url
	 * @param args - args formatted in a List of NameValuePairs. 
	 * @return - returns null if something went wrong. Otherwise the HTTP Response. 
	 */
	public HttpResponse postData(String url, List<NameValuePair> args) {  
		
		HttpClient httpclient = new DefaultHttpClient();  
		
		HttpPost httppost;
		try {httppost = new HttpPost(url);} catch (Exception e1) {
			Log.e("VoyagerNet.PostData()","Error: ",e1);
			return null;
		}  
		HttpResponse response = null;
		
		try {  
			httppost.setEntity(new UrlEncodedFormEntity(args));  
			response = httpclient.execute(httppost);  
		} catch (Exception e) {  
			Log.e("VoyagerNet.postData()","Error: " + e.getMessage(),e);
		}
	
    	return response;
    }

}



