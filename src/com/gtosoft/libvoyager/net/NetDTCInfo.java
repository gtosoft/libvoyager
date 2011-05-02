/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.net;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;

import android.util.Log;

import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.GeneralStats;

/**
 * The purpose of this class is to abstract the process of connecting to a
 * server and retrieve diagnostic trouble code descriptions. 
 * Input: DTC such as "P0521" 
 * Response Format: "DTC|DESCRIPTION_OF_DTC|" (Which we parse, validate, and return just the description of.
 * REQUIRES: manifest.xml: <uses-permission android:name="android.permission.INTERNET" />
 */

public class NetDTCInfo {
	final boolean DEBUG = false;
	
	int mReadErrorCount = 0;
	int mTotalNetworkLookups = 0;
	
	GeneralStats mgStats = new GeneralStats();

	// identifies client.
	String mClientID = "";
	
	HashMap<String, String> hmDTCInfo = new HashMap<String, String>();

	Socket socket = null;
	final String GTOServerAddress = "apps.gtosoft.com";

	public NetDTCInfo(String clientID) {
		mClientID = clientID;
	}

	/**
	 * Opens the socket (creates the TCPIP connection through which we can
	 * transfer data). This might block for a long time if you're not careful.
	 * 
	 */
	private boolean openSocket() {
		// Try and open the socket.
		try {
			socket = new Socket(GTOServerAddress, 80);
			mgStats.setStat("ip.local", "" + socket.getLocalSocketAddress());
			mgStats.setStat("socket.error", "");
		} catch (Exception e) {
			msg("Error establishing socket for DTC lookup. E=" + e.getMessage());
			mgStats.setStat("socket.error", "" + e.getMessage());
			return false;
		}

		return true;
	}

	public String getDTCDescription(String DTC) {
		String description = "";

		description = getDTCCachedDescription(DTC);
		
		if (description.length() > 0) {
			if (DEBUG) msg("Using cached value for DTC " + DTC + " descr=" + description);
			return description;
		} else {
			if (DEBUG) msg ("DTC " + DTC + " not yet cached..");
		}

		// If the connection is bad then give up altogether for the duration of this session (instantiation). 
		if (isConnectionClean() != true) {
			return "";
		}

		// // Looks like we have to figure it out ourselves.

		description = getServerDTCDescription(DTC);

		if (description.length() > 0) {
			setDTCDescription(DTC, description);
			// must have been a good read? 
			resetReadErrorCount();
		} else {
			// there was a problem getting the DTC. 
			readErrorOccurred();
		}
				

		return description;
	}
	
	private void readErrorOccurred () {
		mReadErrorCount++;
		if (DEBUG) msg ("DTC READ ERROR. count=" + mReadErrorCount);
	}

	/**
	 * Simply returns true if we have not crossed the # of errors threshold yet. 
	 * @return - true unless there are a bunch of sequential read errors. 
	 */
	private boolean isConnectionClean () {

		// too many errors. 
		if (mReadErrorCount > 5) {
			return false;
		}
		
		// default: return true.
		return true;
	}

	private void resetReadErrorCount () {
		mReadErrorCount = 0;
		if (DEBUG) msg ("DTC - ERROR FREE");
	}
	
	/**
	 * Get an open socket. This method could potentially block for a long time
	 * (for example if DNS sucks).
	 * 
	 * @return - returns true if the socket ends up open and useable.
	 * 
	 */
	private boolean getOpenSocket() {

		// Is the socket undefined?
		if (socket == null) {
			if (DEBUG) msg("Opening socket...");
			openSocket();
			if (DEBUG) msg("Socket open.");
		}

		// try opening socket even if it's not null.
		if (socket.isConnected() != true) {
			openSocket();
		}

		// Is the socket open?
		if (socket.isConnected() != true) {
			if (DEBUG) msg("After opening socket, it was not connected.");
			return false;
		}

		if (DEBUG) msg("Successfully opened socket.");
		return true;
	}

	/**
	 * Perform an internet based TCPIP lookup by sending the DTC to the GTO
	 * server and getting the response description.
	 * 
	 * @param DTC
	 * @return
	 */
	private String getServerDTCDescription(String DTC) {
		String description = "";
		String prefix = "GET /dtc/index.php?DTC=";
		String suffix = "&auth=" + mClientID;

		// make sure we're connected.
		if (getOpenSocket() != true)
			return "";

		// Set up output stream
		PrintWriter out;
		try {
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
		} catch (Exception e) {
			msg("Error attaching output stream: " + e.getMessage());
			return "";
		}

		// set up input stream.
		BufferedInputStream inStream = null;
		try {
			inStream = new BufferedInputStream(socket.getInputStream());
		} catch (Exception e) {
			msg("Error attaching input stream: " + e.getMessage());
			return "";
		}

		// make the pseudo-HTTP request:
		out.println(prefix + DTC + suffix + "\n");

		description = readWithTimeout(".", inStream, 5);

		// all done, shut down the socket.
		try {
			inStream.close();
			out.close();
		} catch (IOException e) {
			msg("Error closing streams: " + e.getMessage());
		}

		// close the connection. 
		closeSocket();

		mTotalNetworkLookups++;
		mgStats.setStat("net.lookups", "" + mTotalNetworkLookups);

		// Split it up. response should be in this format: 
		// "DTC|DESCRIPTION_OF_DTC|"
		String[] responseParts = description.split("\\|");

	// perform validation.

		// valid number of parts?
		if (responseParts.length < 2) {
			if (DEBUG)
				msg("server response failed length test. length=" + responseParts);
			return "";
		}

//		// invalid echo-back?
//		if (!responseParts[0].equals(DTC)) {
//			if (DEBUG)
//				msg("server response failed echoback");
//			return "";
//		}

		
		description = responseParts[1];
		
//		try {
//			// chop off trailing stuff.
//			description = responseParts[1].substring(0,
//					responseParts[1].length() - 4);
//		} catch (Exception e) {
//			msg("Error chopping response. Response must be too short. response="
//					+ description);
//		}

		return description;
	}

	/**
	 * 
	 * @param terminatorByte
	 *            - when we see that the response ends with this character, we
	 *            stop reading.
	 * @param inStream
	 * @param maxSeconds
	 *            - max seconds to wait or try to get a response.
	 * @return
	 */
	private String readWithTimeout(String terminatorByte,
			BufferedInputStream inStream, int maxSeconds) {
		String ret = "";

		// Collect the response.
		String newBytes = "";
		long expireTime = EasyTime.getUnixTime() + maxSeconds; // simple
																// 5-second
																// timeout.
		while (socket != null && socket.isConnected()
				&& !ret.trim().endsWith(terminatorByte)
				&& (EasyTime.getUnixTime() < expireTime)) {
			newBytes = readAllStreamInput(inStream);

			// if no data avilable yet, then chill.
			if (newBytes.length() < 1) {
				EasyTime.safeSleep(1000);
			} else {
				// append new data.
				if (DEBUG)
					msg("Appending: " + newBytes);
				ret = ret + newBytes;
			}
		}

		return ret;
	}

	private String readAllStreamInput(BufferedInputStream inStream) {
		String ret = "";
		try {
			while (inStream.available() > 0) {
				ret = ret + (char) inStream.read();
			}
		} catch (IOException e) {
			if (DEBUG) msg("Error reading from input stream. E=" + e.getMessage());
		}

		return ret;
	}

	private boolean closeSocket() {
		
		try {
			socket.close();
		} catch (Exception e) {
			if (DEBUG) msg("Error closing socket! E=" + e.getMessage());
			return false;
		}

		socket = null;
		
		return true;
	}

	/**
	 * 
	 * @param DTC
	 * @param description
	 */
	private void setDTCDescription(String DTC, String description) {
		
		if (description.length() < 1)
			return;
		
		if (DTC.length() < 1 )
			return;
		
		hmDTCInfo.put(DTC, description);
	}

	private String getDTCCachedDescription(String DTC) {
		String description = "";

		// Get it if it exists.
		if (hmDTCInfo.containsKey(DTC))
			description = hmDTCInfo.get(DTC);

		return description;
	}

	private void msg(String m) {
		Log.d("NetDTCInfo", "[T=" + Thread.currentThread().getId() + "] " + m);
	}

	/**
	 * Call this if you want us to close all open resources.
	 */
	public void shutdown() {
		closeSocket();
		hmDTCInfo.clear();
	}
	
	public GeneralStats getStats () {
		return mgStats;
	}

}// end of class.
