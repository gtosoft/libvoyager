/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.session.OBD2Session;

import android.util.Log;


/**
 * @author brad
 */

public class PIDDecoder {
	final boolean DEBUG=false;

	// will be used to throttle the rate at which new requests are placed. 
	long mTimeOfLastDetectRequest = 0;


	NetworkStats nStats;
	NetDetect  mNetDetect;

	GeneralStats mgStats = new GeneralStats();
	
	// Will store the trip ID for the current "trip" as long as we're in a trip
	// (car running).
	String mTripID = "";

	// If we're monitoring data changes and logging those changes to the
	// monitorData table, this keeps track of the Monitor session ID used to
	// separate each different monitor session.

//	Context mCTX = null;

	String mNetwork = "";

	// Keeps track of datapoints and their current value.
	HashMap<String, String> mhmDataByName = null;
	// Stores the number of times each PID has been seen IN THIS INTERVAL (gets reset each stats interval)

	// For cacheing Datapoint data so we don't re-decode the same datapoint if
	// the source data hasn't changed.
	HashMap<String, String> mhmDPRawData = null;

	DashDB ddb = null;

//	Thread mtStatsThread = null;
//	String mStatsString = "";
	// re-calculate stats every X seconds as specified by the following
	// constant.
	final int STATS_CALCULATION_INTERVAL = 30;

	// If an OBD2 session layer is present, it can register itself with us and
	// we keep a reference to it in this variable.
	OBD2Session mOBD = null;
	// HashMap<String,String> mhmOBDData = null;
	// HashMap<String,Long> mhmOBDDataLastSeen = null;


	// can header length = 29 or 11.
	// int mCANBits = 0;

	// true until we're asked to shut down, in which case all our threads should
	// gracefully DIE!
	boolean mThreadsOn = true;
	boolean mInjectorThreadOn = true;

	Thread mInjectorThread = null;

	// our EasyTime class - great for cases where a timestamp is needed very
	// frequently.
	EasyTime metime = new EasyTime();

	EventCallback mNewMessageHandler = null;
	EventCallback mNewDataHandler = null;
	EventCallback mECBOnDPArrived = null;

	/**
	 * Reset things like the PID hashmap for a new use. also resets stats.
	 */
	public void reset() {
		mhmDataByName = new HashMap<String, String>();
		mhmDPRawData = new HashMap<String, String>();

		if (DEBUG) msg ("instantiating a new networkStats object.");
		nStats = new NetworkStats(ddb);
	}


	
	/**
	 * A simple constructor. 
	 * @param d - An existing DashDB instance.
	 */
	public PIDDecoder (DashDB d) {
		ddb = d;
		// Instantiate the stats collector which collects mostly passive network stats.
		nStats = new NetworkStats(ddb);
		// Instantiate the class which detects the network in passive situations. 
		mNetDetect = new NetDetect(ddb);
		reset();
		if (DEBUG) msg ("Dakota instantiated.");
	}
	
	/**
	 * Change the network ID at runtime. 
	 * 
	 * @param newNetworkID
	 */
	public void setNetworkID (String newNetworkID) {

		// Make a note of the network ID for immediate use. 
		mNetwork = newNetworkID;
		
		// Invalidate any data cached based on networkID. Do this IMMEDIATELY and AFTER setting the network ID. 
		ddb.clearCachedNetworkIDData();
		
		// Save the new network ID to persistant storage. 
		setCachedNetworkID();
		
//		mgStats.setStat("moniNetworkID", mNetwork);
	}

	/**
	 * Process a single sniffed PID message. This is the entry-point into the
	 * class for new sniffed data.
	 * 
	 * @param m
	 *            - the message as sniffed.
	 * @return - returns true unless there was a problem processing the message.
	 */
	public boolean decodeOneMessage(String m) {
		String HDR;
		String data;

		if (m.length() < 6) {
			// Length too short. Invalid message. Throw it out.
			return false;
		}

		int canBits = 0;
		// message too short.
		if (m.length() < 3)
			return false;

		m = m.trim();
		int firstSpace = m.indexOf(" ");
		switch (firstSpace) {
		case 3:
			canBits = 11;
			break;
		case 2:
			canBits = 29;
			break;
		default:
			// could not determine number of CAN bits based on the message.
			return false;
		}

		// depending on the CAN header type, pull apart the header and data and
		// store it in a hashmap.
		if (canBits == 11) {
			if (m.length() < 6) {
				return false;
			}

			// 7DF 02 01 00 00 00 00 00
			// PPP DD DD DD DD DD DD DD
			// PID Data
			HDR = m.substring(0, 3);
			data = m.substring(4, m.length());

			// new Datapoint decoding logic:
			DPRawPacketArrived(HDR, data);

			// save the PID as the key and data as the value.
//			setPIDDataByHeader(HDR, data);
			return true;
		}

		if (canBits == 29) {
			if (m.length() < 14) {
				return false;
			}

			// 10 02 40 60 10 10 10 10
			// HH HH HH HH DD DD DD DD
			// HEADER/PID Data
			HDR = m.substring(0, 11);
			data = m.substring(12, m.length());

			// new Datapoint decoding logic:
			DPRawPacketArrived(HDR, data);

			// save the PID as the key and data as the value.
//			setPIDDataByHeader(HDR, data);
			return true;
		}

		msg("processOneMessage(): This Shouldn't happen: Message wasn't processed, but we don't know why. m="
				+ m);
		return false;
	}// end of DecodeOneMessage method, which is called for every raw packet
		// received in passive mode.


	private void reSendDataArrivedEvent (String DPN) {
		
		// Extract existing value
		String data = getCachedDataByName(DPN);
		int iDecodedData = 0;
		try {iDecodedData = Integer.valueOf(data);
		} catch (NumberFormatException e) { }
		
		// re-send the data through the normal channels, as if we had just decoded it. 
		setPIDDataByName(DPN, data, iDecodedData);
	}


	private void setPIDDataByName(String DPN, String decodedData, int iDecodedData) {
		boolean isNew = false;

		// capture whether this is a new datapoint or not.
		if (!mhmDataByName.containsKey(DPN)) {
			isNew = true;
			// Log the current timestamp so we can later calculate how long it's been since the last new PID arrived. 
		}

		
		// Store it in the hashmap.
		atomicPIDDataByName(true, DPN, decodedData);

		// Execute new local event hook.
		DPDataArrived(DPN, decodedData, iDecodedData, isNew);

		if (mNewDataHandler != null)
			mNewDataHandler.onNewDataArrived(DPN, "", "", decodedData, false,
					true);

		// event handler.
		if (mECBOnDPArrived != null)
			mECBOnDPArrived.onDPArrived(DPN, decodedData, iDecodedData);

	}

	/**
	 * Atomically handle the reading and writing of the hashmap. We make it
	 * synchronized to prevent collisions. USE THIS METHOD any time you want to
	 * read or write from the mhmDataByName hashmap. It will keep you out of
	 * trouble.
	 * 
	 * @param trueIfWritingFalseToRead
	 * @param DPN
	 * @param decodedData
	 * @return
	 */
	private synchronized String atomicPIDDataByName(
			boolean trueIfWritingFalseToRead, String DPN, String decodedData) {
		if (trueIfWritingFalseToRead == true)
			mhmDataByName.put(DPN, decodedData);
		else {
			String ret = mhmDataByName.get(DPN);
			// prevent returning a null AND perform just one get^^^.
			if (ret != null)
				return ret;
			else
				return "";
		}

		return "";
	}


	public void registerOBD2SessionLayer(OBD2Session o) {
		mOBD = o;
	}

	// Allows another class, such as a higher-level class such as Dash Activity
	// - to register a method to be fired off each time we generate a message
	// (so they can get the message too!)
	public void registerNewMessageHandler(EventCallback ecb) {
		mNewMessageHandler = ecb;
		msg("Registered debug callback.");
	}

	public void registerNewDataArrivedHandler(EventCallback ecb) {
		mNewDataHandler = ecb;
	}

	private void msg(String m) {

		// pass messages up to the Dash Activity if they registered a callback.
		if (mNewMessageHandler != null)
			mNewMessageHandler.newMsg("Dakota: " + m);
		else
			Log.d("Dakota", m);
	}


	/**
	 * call this method to initiate a stop/shutdown of all threads and running
	 * tasks owned by this class instance. The class will no longer be usable
	 * after a shutdown - re-instantiate it for use after a shutdown.
	 */
	public void shutdown() {
		mThreadsOn = false;
		mInjectorThreadOn = false;

//		// shutdown the stats thread.
//		if (mtStatsThread != null)
//			mtStatsThread.interrupt();

		// the EasyTime class which keeps track of an uptime variable.
		if (metime != null)
			metime.shutdown();

		if (ddb != null) {
			try {
				ddb.shutdown();
			} catch (Exception e) {
			}
		}

	}



	/**
	 * Contacts the OBD2 layer, asks it to send an OBD request, and get the
	 * response. We will then decode the response and return it as a string.
	 * 
	 * @return
	 */
	public String getDataViaOBD(String dataPointName) {
		String decodedResponses = "";

		if (mOBD == null) {
			msg("getDataViaOBD(): WARNING: request for datapoint " + dataPointName + " but OBD is not instantiated/ is null.");
			return "";
		}

		if (mOBD.getCurrentState() < OBD2Session.STATE_BTCONNECTED) {
			//			if (DEBUG == true) msg("Ignoring request for data " + dataPointName + " because OBD Session setup not complete.");
			return "";
		}

		// Send the OBD2 request by name and get the responses and parse them!
		HashMap<String, String> responses = mOBD.sendOBDRequestByName(dataPointName);

		Set<String> s = responses.keySet();
		Iterator<String> i = s.iterator();

		// Iterate through responses and convert them into a
		// comma-separated-string.
		String thiskey = "";
		while (i.hasNext()) {
			thiskey = i.next();

			// decide whether or not to use a comma. don't use a comma if its
			// the first (possibly only) entry in the set.
			if (decodedResponses.length() == 0)
				decodedResponses += responses.get(thiskey);
			// decodedResponses += thiskey + ": " + responses.get(thiskey);
			else
				decodedResponses += "," + responses.get(thiskey);
			// decodedResponses += "," + thiskey + ": " +
			// responses.get(thiskey);
		}

		// Set the data in the hashmap in such a way that the newdata event gets
		// kicked off.
		setPIDDataByName(dataPointName, decodedResponses, 0);

//		TODO: also set the data in a way that fires off DPArrived.
		
		return decodedResponses;
	}

	/**
	 * Given a single OBD response packet, convert it into usable form.
	 * 
	 * @param response
	 *            - the response packet. For example: ??/
	 * @return - returns a usable response.
	 */
	// public String decodeOBDResponse (String response) {
	// msg ("TODO: Decode this: " + response);
	//		
	// // TODO: Finish this method.
	// return "<FAIL:decode:" + response + ">";
	// }

	/**
	 * Goes to the local cache and looks for the specified datapoint name. If
	 * found, it is returned right out of the cache. Note that the cache could
	 * be any age, we're not checking.
	 * 
	 */
	public String getCachedDataByName(String dataPointName) {
		return atomicPIDDataByName(false, dataPointName, null);
	}

	/**
	 * Convenience method that just calls getCachedDataByName. So that we have a
	 * get/set for DataByName.
	 * 
	 * @param dataPointName
	 * @return
	 */
	public String getPIDDataByName(String dataPointName) {
		return getCachedDataByName(dataPointName);
	}


	/**
	 * Send an OBD2 PID request. The response will be propagated through the
	 * event system. In other words, to get the data, Register/Subscribe to the
	 * DPArrived event.
	 * 
	 * @param dataPointName
	 * @return
	 */
	public boolean sendOBDRequestByName(String dataPointName) {
		// Determine what the request should be
		// Send the OBD request
		if (mOBD != null) {
			// THIS now happens in the same method that gets the data and decodes it: setPIDDataByName(dataPointName, obdData, 0);
			String obdData = getDataViaOBD(dataPointName);
		} else {
			return false;
		}
		// Get the response
		// Decode the response
		// Pass the decoded data to one of the DP methods so that the DPArrived event gets raised.
		return true;
	}



	public HashMap<String, String> getAllDatapoints() {
		HashMap<String, String> ret = new HashMap<String, String>();

		Set<String> s = ddb.getDataPointNamesSet(mNetwork);
		Iterator<String> i = s.iterator();

		String thisPoint = "";
		while (i.hasNext()) {
			thisPoint = i.next();
			ret.put(thisPoint, getCachedDataByName(thisPoint));
		}

		return ret;
	}


	/**
	 * returns one big string with CRLF line terminators, describing all DPNs. 
	 * @return
	 */
	public String getAllDataPointsAsString () {
		String allDPNsAsString = "";

		HashMap<String,String> hmAllDPNs = new HashMap<String,String> (getAllDatapoints());
		
		Set<String> s = new TreeSet<String> (hmAllDPNs.keySet());
		Iterator<String> i = s.iterator();
		
		String thisKey = "";
		while (i.hasNext()) {
			thisKey = i.next();
			allDPNsAsString = allDPNsAsString + " " + thisKey + "=" + hmAllDPNs.get(thisKey) + "\n";
		}

		return allDPNsAsString;
	}
	

	/**
	 * Define an eventCallback with the onDPArrived method overridden to receive
	 * a callback every time a datapoint is decoded.
	 * 
	 * @param newDPArrivedHandler
	 */
	public void registerOnDPArrivedCallback(EventCallback newDPArrivedHandler) {
		if (mECBOnDPArrived != null) 
			msg ("Blowing away existing PD DP arrived callback");
		mECBOnDPArrived = newDPArrivedHandler;
	}

	/**
	 * To be executed for every passive packet that arrives. Should be called
	 * after the packet's CAN header and data are separated.
	 * 
	 * @param HDR
	 *            - 29 or 11-bit CAN header.
	 * @param newData
	 *            - the data part of the packet. typically up to 8 bytes
	 * 
	 *            DPArrived -> DPParseFormula -> DPDataArrived -> DPDecode
	 */
	private void DPRawPacketArrived(String HDR, String newData) {

//		msg ("PACKET: " + HDR + " / " + newData);
		
		// Pass the packet to the stats calculation logic. 
		nStats.setPIDDataByHeader(HDR, newData);

		
		// For each new packet that arrives, if the network ID is still blank, then perform detection if conditions are appropriate. 
		if (!isNetworkIDValid())
			performPassiveNetworkDetection();
		
		String[][] DPS = ddb.getDPSForHDR(mNetwork, HDR);

		// If there isn't even a single decoder for this packet, then bail right away. 
		if (DPS == null)
			return;

		// not null, but no records?
		if (DPS.length < 1)
			return;


		
		// So there's at least one array element, loop through it!
		String thisSigbytes;
		for (int i = 0; i < DPS.length; i++) {
			// Make a reference to the sibbytes for the current iteration
			// through the X dimension.
			thisSigbytes = DPS[i][DashDB.DPS_FIELD_SIGBYTES];

			// if (DEBUG==true) msg ("DEBUG: DPArrived: HDR=" + HDR +
			// " SigBytes=" + thisSigbytes + " DPN=" +
			// DPS[i][DashDB.DPS_FIELD_DPN] + " newdata=" + newData + " i=" +
			// i);

			// Does our packet contain a sigbyte match?
			if (thisSigbytes.length() == 0 || newData.startsWith(thisSigbytes)) {
				// SIGBYTE MATCH! (either no sigbytes present, which is a match,
				// or otherwise.
				// Pass all the relevant info to the decoder, which will decode
				// the data and do anything else necessary with the data.
				DPParseFormula(HDR, newData, DPS[i][DashDB.DPS_FIELD_DPN],
						DPS[i][DashDB.DPS_FIELD_FORMULA]);
			} // end of "if the sigbytes match".
		}// end of for-loop which loops through all the datapoint records (DPS)
			// for the given header.
	}// end of DPArrived method which executes for each packet received in
	

	private boolean isNetworkIDValid () {
		
		if (mNetwork.equals("XX"))
			return false;
		
		if (mNetwork.length() != 2)
			return false;

		return true;
	}

	
	/**
	 * Convenience method. Will be used to get the number of seconds elapsed since the last net detect was initiated (and accepted). 
	 * @return
	 */
	private long getTimeSinceLastNetDetect () {
		long elapsedTime = EasyTime.getUnixTime() - mTimeOfLastDetectRequest;

		// Do sanitizing of the elapsedTime here. 
		
		return elapsedTime;
	}
	
	/**
	 * Used alongside teh getTimeSinceLastNetDetect. 
	 * This method sets the time at which a net detect is performed.  
	 */
	private void setTimeOfNetDetect () {
		mTimeOfLastDetectRequest = EasyTime.getUnixTime();
	}

	
	/**
	 * Even if mNetwork is already set, we detect the network based on the PIDs observed by the nStats class.
	 * But we ONLY perform the detection if conditions are appropriate, such as sufficient PIDs are known.  
	 * This method may block for as long as the NetDetect.getBestGuess... method blocks for. 
	 * As long as the network ID is unset, there will be a steady flow of calls to this method.
	 * Made this method synchronized because it does some timing logic that is most reliable if the method is non re-entrant. 
	 */
	private synchronized void performPassiveNetworkDetection() {
		// Just detect the network even if network ID already set.
		
		long msgsProcessed = nStats.getNumMessagesProcessed();
		String bestGuess = "";

		// only try every X messages...
		if (msgsProcessed % 50 != 0)
			return;
	
		// Only try if we have seen at least X messages.
		if (msgsProcessed < 50) {
			if (DEBUG) msg ("Not enough messages (" + msgsProcessed +  ") have been observed. Waiting to perform network ID");
			return;
		}
		
		// Check and see how long it's been since the last net detect. 
		final int minTimeBetweenTries = 10;
		if (getTimeSinceLastNetDetect() < minTimeBetweenTries) {
			if (DEBUG) msg ("Ignoring extra request to ID net. elapsed=" + getTimeSinceLastNetDetect() + " numprocessed=" + msgsProcessed);
			return;
		}
		
		//////////////////// If execution continues past this point, then we'll be performing the net detection. 
		
		if (DEBUG) msg ("Performing network Identification NOW. Msgs=" + msgsProcessed + " time since last net detect=" + getTimeSinceLastNetDetect() + "s");
		
		// Mark the time which we last performed a detection. 
		setTimeOfNetDetect();

		// Perform the method which gets the network ID. remember, it may block. 
		bestGuess = getBestGuessNetworkID();

		// validate the guess to make sure it is valid. 
		if (bestGuess.length()>0) {
			setNetworkID(bestGuess);
			// we have a NetworkID Match... collect a stat.
			mgStats.setStat("msgsPresentUponNetworkIDMatch", "" + msgsProcessed);
		} else {
			// Well, we tried, but were unable to identify this network. 
			if (msgsProcessed > 1000) {
				// Furthermore, we have been trying for a long time so we'll probably never recognize this network. 
				msg ("Processed over 1000 messages and wes still don't recognize this network. Performing FP...");
				msg ("FP=" + getMoniNetFingerprint());
			}
		}

		if (DEBUG) msg ("Performed best guest Network detection and the result was " + bestGuess);
		
	}

	/**
	 * Don't call this method unless you are performPAssiveNetworkDetection. 
	 * Does the network detection stuff immeidately without delay. To be called only if conditions are right because it could block for a few seconds. 
	 * @return - returns the network ID that was detected. 
	 */
	private String getBestGuessNetworkID() {

		// make note of starting time, so we can calculate elapsed time. 
		long startTime = EasyTime.getUnixTime();
		
		Set<String> sCANIDs = nStats.getIDSet();
		if (DEBUG) msg ("PD nstats ID Set has " + sCANIDs.size());
		String bestGuess = mNetDetect.getBestGuessNetworkID(sCANIDs);

		// collect a stat. 
		long elapsedTime = EasyTime.getUnixTime() - startTime;
		mgStats.setStat("timeToGuessNetworkID", "" + elapsedTime);
		
		return bestGuess;
	}
	
	/**
	 * public method to get the network ID decided by the getBestGuessNetworkID method which is called by another chain of functions.  
	 * @return - a network ID string corresponding to the network ID field of the database. As related to passive network stuff. 
	 */
	public String getNetworkID () {
		return "" + mNetwork;
	}
	
	

		// pasive mode.

	/**
	 * This method decodes the given datapoint. It may also store the data, do
	 * stats, notify parent (fire events), etc.
	 * 
	 * @param HDR
	 *            - CAN header
	 * @param newData
	 *            - data part of the CAN packet.
	 * @param DPN
	 *            - Datapoint Name.
	 * @param formula
	 *            - formula which, when applied, extracts useful data from the
	 *            packet. Some sample formulas: 16-23,HEX 16-31,SPEED10
	 *            33-63,ONSTAR_LAT 32-39,INTEGER 0-63,ASCII
	 * 
	 *            DPArrived -> DPParseFormula -> DPDataArrived -> DPDecode
	 * 
	 */
	private void DPParseFormula(String HDR, String newData, String DPN,
			String formulaAndBitPositions) {
		final int INDEX_BYTERANGE = 0;
		final int INDEX_FORMULA = 1;

		// This string will hold the two general parts of the formula: byte
		// range specification, and formula specification.
		String formulaParts[] = null;

		// Split the formula into two parts: bit/byte range, and formula.
		try {
			formulaParts = formulaAndBitPositions.split(",");
		} catch (Exception e) {
			msg("PD ERROR during decode: DPN=" + DPN
					+ " formulaAndBitPositions=" + formulaAndBitPositions
					+ " E= " + e.getMessage());
			return;
		}

		if (formulaParts == null || formulaParts.length != 2) {
			msg("PD ERROR: invalid formulaAndBitPositions: "
					+ formulaAndBitPositions + " Expected two parts, got "
					+ formulaParts.length);
			return;
		}

		// now obtain a string which contains only the desired byte(s).
		String byteRange[] = null;
		try {
			byteRange = formulaParts[0].split("-");
		} catch (Exception e) {
			msg("PD Error: Byte range specification is missing hiphen: "
					+ formulaParts[0] + " formulaAndBits="
					+ formulaAndBitPositions + " E=" + e.getMessage());
			return;
		}

		// grab the formula part out of the chunk.
		String formula = formulaParts[INDEX_FORMULA];

		if (byteRange == null || byteRange.length != 2) {
			msg("PD Error: malformed formulaAndBitPositions: "
					+ formulaAndBitPositions
					+ " expected forma: STARTBIT-LASTBIT.");
			return;
		}

		int iStart = -1;
		int iStop = -1;

		try {
			iStart = Integer.valueOf(byteRange[0]);
			iStop = Integer.valueOf(byteRange[1]);
		} catch (NumberFormatException e) {
			msg("PD Error converting byte range to integer! range="
					+ formulaParts[INDEX_BYTERANGE] + " E=" + e.getMessage());
			return;
		}

		String targetBytes = "";
		// If the start and stop encompass an exact byte boundary...
		// ex: 0-7 is byte0, 8-15 is byte1.
		int iStringStart = getStringPositionOfBit(iStart);
		int iStringStop = getStringPositionOfBit(iStop);

		long lTargetBytes = 0;
		if ((iStart % 8 == 0) && ((iStop + 1) % 8 == 0)) {
			try {
				// very tricky logic in the stop byte. remember substring second
				// arg is one byte BEYOND the last byte we want.
				targetBytes = newData.substring(iStringStart, iStringStop);
			} catch (Exception e) {
				if (DEBUG) msg("PD ERROR reaching target bytes. istringstart="
						+ iStringStart + " istringstop=" + iStringStop
						+ " formulaAndBits: " + formulaAndBitPositions
						+ " Data=" + newData);
				return;
			}
		} else {
			// if (DEBUG == true) msg
			// ("PD: ABOUT TO PERFORM ODD BIT BOUNDARY decode. start=" + iStart
			// + " stop=" + iStop + " formulaAndBits=" + formulaAndBitPositions
			// + " Data=" + newData + " DPN=" + DPN + " HDR=" + HDR);
			try {
				// if (DEBUG == true) msg
				// ("DEBUG: Getting subbits method 2 for range " + iStart + "-"
				// + iStop + " data=" + newData);

				// first get the numeric value of the desired hex bytes, then
				// convert it back into hex.
				lTargetBytes = getTargetSubBits(newData, iStart, iStop);
				if (lTargetBytes != 0) {
					targetBytes = Long.toString(lTargetBytes, 16);
				} else {
					targetBytes = "0";
				}

				// if (DEBUG == true) msg
				// ("PD: GOT ODD-BIT-BOUNDARY TARGET BYTES: " +targetBytes);
			} catch (Exception e) {
				msg("ERROR converting subbits to hex string. data=" + newData
						+ " start=" + iStart + " stop=" + iStop + " E="
						+ e.getMessage());
				return;
			}
		}

		if (targetBytes.length() < 1) {
			msg("WARNING: no targetbytes calculated for formulaAndBits="
					+ formulaAndBitPositions + " data=" + newData
					+ " lTargetBytes=" + lTargetBytes);
			return;
		}

		// Optimization/Cacheing: If the raw data associated with the datpoint
		// hasn't changed, don't re-decode the data (and thus no event gets
		// fired for the data not having changed.).
		if (mhmDPRawData.containsKey(DPN)
				&& mhmDPRawData.get(DPN).equals(targetBytes)) {
			reSendDataArrivedEvent(DPN);
			// data unchanged, bail out now.
			return;
		} else {
			// cache the new value.
			mhmDPRawData.put(DPN, targetBytes);
			// data has changed. proceed with decode.
		}

		int iDecodedData = 0;
		String decodedData = "PD TODO: DECODE THIS: formula=" + formula
				+ " targetbytes=" + targetBytes + " DATA=" + newData;

		// Decode the data here.
		decodedData = DPDecode(formula, targetBytes);

		// try to get integer part. If unsuccessful, no biggie.
		try {
			float f = Float.valueOf(decodedData);
			iDecodedData = Math.round(f);
		} catch (NumberFormatException e) {
		}

		// This one fires the events, etc. 
		setPIDDataByName(DPN, decodedData, iDecodedData);

		// fire off the event which passes the newly decoded data up to the
		// parent.
	}

	/**
	 * Given the formula and target bytes we should be able to decode the data
	 * and return it as a string.
	 * 
	 * @param DPN
	 * @param formula
	 * @param targetBytes
	 * @return * DPArrived -> DPParseFormula -> DPDataArrived -> DPDecode
	 */
	private String DPDecode(String formula, String targetBytes) {
		String ret = "";

		// sanity checks.
		if (targetBytes == null || targetBytes.equals("") || formula == null
				|| formula.length() < 1) {
			if (DEBUG) msg("Threw out invalid formula or targetbytes: F=" + formula
					+ " TB=" + targetBytes);
			
			if (DEBUG)
				return "INVALID_FORMULA_OR_TARGETBYTES F=" + formula + "TB=" + targetBytes;
			else // if debug not enabled, then don't show the formula and target bytes. 
				return "INVALID_FORMULA_OR_TARGETBYTES";
			
		}

		// msg ("Processing formula " + formula + " targetBytes=" +
		// targetBytes);

		if (formula.equals("MULT10")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r * 10);
			return ret;
		}

		if (formula.equals("MULT14")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r * 14);
			return ret;
		}

		if (formula.equals("MULT4")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r * 4);
			return ret;
		}

		if (formula.equals("DIV10")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 10);
			return ret;
		}

		if (formula.equals("DIV16")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 16);
			return ret;
		}

		if (formula.equals("DIV40")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 40);
			return ret;
		}

		if (formula.equals("DIV4")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 4);
			return ret;
		}

		if (formula.equals("DIV100")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 100);
			return ret;
		}

		if (formula.equals("MULT2051")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (20f * r / 51);
			return ret;
		}

		if (formula.equals("MULT205100")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (20f * r / 5100);
			return ret;
		}

		if (formula.equals("DIV64")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 64);
			return ret;
		}

		if (formula.equals("DIV128")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r / 128);
			return ret;
		}

		if (formula.equals("INT")) {
			ret = "" + getIntFromHexString(targetBytes);
			return ret;
		}

		if (formula.equals("ASCII")) {
			ret = convertHexStringToAscii(targetBytes);
			return ret;
		}

		if (formula.equals("HEX")) {
			ret = targetBytes;
			return ret;
		}

		if (formula.equals("BIT")) {

			long l = getIntFromHexString(targetBytes);

			if (l == 0)
				ret = "0";
			else
				ret = "1";

			return ret;
		}

		if (formula.equals("M=1&B=-40")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r - 40);
			return ret;
		}

		if (formula.equals("M=1/8")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + ((1f * r) / 8);
			return ret;
		}

		if (formula.equals("M=1/10")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + ((1f * r) / 10);
			return ret;
		}

		if (formula.equals("M=1/32")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + ((1f * r) / 32);
			return ret;
		}

		if (formula.equals("M=0.5")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (0.5f * r);
			return ret;
		}

		if (formula.equals("M=4&B=-40")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (4f * r - 40);
			return ret;
		}
		// M=0.5&B=-40
		if (formula.equals("M=0.5&B=-40")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (0.5f * r - 40);
			return ret;
		}

		if (formula.equals("M=0.1&B=3")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (0.1f * r + 3);
			return ret;
		}

		if (formula.equals("M=1,B=-100000")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r - 100000);
			return ret;
		}
		// M=1/16
		if (formula.equals("M=1/16")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + ((1f * r) / 16);
			return ret;
		}
		// M=0.25&B=-200
		if (formula.equals("M=0.25&B=-200")) {
			long r = getIntFromHexString(targetBytes);
			ret = "" + (1f * r * 0.25 - 200);
			return ret;
		}

		if (formula.equals("ONSTAR_LAT")) {
			// get raw milliarcseconds
			long MAS = getIntFromHexString(targetBytes);

			// throw out the 4 unneeded bits (the rightmost one is the valid
			// bit, we'll get that through another datapoint).
			MAS &= 0x0FFFFFFF;

			// Convert MAS to DMS.
			ret = "MAS=" + MAS + " DMS=" + convertMASToDMS(MAS);

			return ret;
		}

		if (formula.equals("ONSTAR_LON")) {
			boolean isLongitudeNegative = false;
			// get raw milliarcseconds
			long MAS = getIntFromHexString(targetBytes);

			// Is it negative?
			if (MAS >= 0x40000000) {
				// Flip all bits, including the negative sign bit.
				MAS ^= 0xFFFFFFFF;
				// Strip unwanted bit
				MAS &= 0x7FFFFFFF;
				isLongitudeNegative = true;
			}

			// Convert MAS to DMS and add east/west based on whether the number
			// was negative.
			if (isLongitudeNegative == true)
				ret = "MAS=" + MAS + " DMS=(west)" + convertMASToDMS(MAS);
			else
				ret = "MAS=" + MAS + " DMS=(east)" + convertMASToDMS(MAS);

			return ret;
		}

		if (formula.equals("TPMS")) {

			// TODO: We must get the real value here! But which PID is the data
			// kept in?
			int currentBarometerInKPA = 1;

			float tireData = getIntFromHexString(targetBytes);

			float tirePressure = 25f - currentBarometerInKPA + 0.34475f * tireData;

			ret = "" + tirePressure;
			return ret;
		}

		// if execution reaches this point, then no decoder took the bait.
		if (DEBUG == true) {
			msg("DPDecode: No decoder for formula=" + formula + " targetbytes=" + targetBytes);
			ret = "ERROR: No decoder for formula " + formula + " TB=" + targetBytes;
		}

		return ret;
	}

	/**
	 * Given a hex string and desired start and stop bits, we'll extract those
	 * bits into an integer value.
	 * 
	 * @param s
	 *            - input hexstring, spaces are OK.
	 * @param startBit
	 *            - start bit
	 * @param lastBit
	 *            - last bit to include in the decoded output. ex: 0-7 returns a
	 *            8-bit number.
	 * @return - returns an integer value of the desired bits.
	 */
	private long getTargetSubBits(String s, int startBit, int lastBit) {
		// remove spaces from the hex string.
		s = s.replace(" ", "");
		
		// TODO: Optimizations to trim pieces of the string before converting it, otherwise we can't actually handle 8-byte hex strings! 

		// sanity check: make sure string is long enough to be valid.
		if (s.length() < 1) {
			msg("Warning: unable to get target bytes - string too short: " + s);
			return 0;
		}

		// Get the value of the hexstring.
		long l = 0;
		try {
			l = Long.valueOf(s, 16);
		} catch (NumberFormatException e) {
			msg("Error converting hex to long. String was " + e.getMessage() + " target was " + startBit + "-" + lastBit);
			return 0;
		}

		// ...
		int numTotalBits = s.length() * 4; // String length * 4 (four bits per
											// character/nibble).
		int numSaveBits = lastBit - startBit + 1;
		int rightExtraBits = numTotalBits - lastBit - 1;
		long saveMask = 0;
		try {
			// generate the MASK by raising to power one bit past what we want,
			// then minus 1 to drop that extra bit and flip all lower bits ON.
			saveMask = (long) Math.pow(2, numSaveBits) - 1;
		} catch (Exception e) {
			msg("ERROR while generating mask with " + numSaveBits + " bits. E="
					+ e.getMessage());
			return 0;
		}

		// blow away unwanted lower bits and shift the target bits into focus.
		// if (DEBUG==true) msg ("getTargetSubBits(): DEBUG 1/4: String=" + s);
		// if (DEBUG==true) msg ("getTargetSubBits(): DEBUG 2/4: long=" + l +
		// " Hex 0x" + Long.toString(l, 16));
		l >>= rightExtraBits;
		// if (DEBUG==true) msg ("getTargetSubBits(): DEBUG 3/4: shiftedDown=" +
		// l + " Hex 0x" + Long.toString(l, 16));
		l &= saveMask;
		// if (DEBUG==true) msg ("getTargetSubBits(): DEBUG 4/4: stripedAway=" +
		// l + " This is the result we'll return. Hex: 0x" + Long.toString(l,
		// 16));

		return l;
	}

	private String convertHexStringToAscii(String hexString) {
		String ret = "";

		String[] eachByte = null;

		try {
			eachByte = hexString.split(" ");
		} catch (Exception e) {
			msg("Error getting ASCII from " + hexString);
			return "";
		}

		if (eachByte == null) {
			msg("Error: no ascii bytes in " + hexString);
			return "";
		}

		char thisByte = ' ';
		for (int i = 0; i < eachByte.length; i++) {
			thisByte = (char) getIntFromHexString(eachByte[i]);
			if (thisByte > 0 && thisByte < 128)
				ret = ret + thisByte;
			else {
				ret = ret + "?";
				msg("Warning: Skipping non-ASCII byte code " + (int) thisByte
						+ " while converting to ascii this string: "
						+ hexString);
			}
		}

		return ret;
	}

	/**
	 * @param MAS
	 *            - milliarcseconds
	 * @return - String representation of DMS. (degrees mionutes seconds)
	 */
	private String convertMASToDMS(long MAS) {

		long milliarcseconds = MAS; /* input and output */
		long degrees; /* output */
		long minutes; /* output */
		long seconds; /* output */

		degrees = milliarcseconds / 3600000;
		milliarcseconds -= degrees * 3600000;
		minutes = milliarcseconds / 60000;
		milliarcseconds -= minutes * 60000;
		seconds = milliarcseconds / 1000;
		milliarcseconds -= minutes * 1000;

		return "" + degrees + "," + minutes + "," + seconds;
	}

	/**
	 * Returns an integer representation of the given hex string.
	 * 
	 * @param hexString
	 * @return
	 */
	private long getIntFromHexString(String hexString) {
		if (hexString == null || hexString.length() < 1)
			return 0;

		long ret = 0;

		try {
			ret = Long.valueOf(hexString.replace(" ", ""), 16);
		} catch (NumberFormatException e) {
		}

		return ret;
	}

	/**
	 * This is a local event-hook. It can do anything it wants based on new
	 * data.
	 * 
	 * @param DPN
	 * @param oldValue
	 * @param newValue
	 * @param iNewValue
	 */
	// TODO: merge this with the existing pid data logic such as
	// setpiddatabyname, and the existing events.
	// the goal being to have a single event firer and everything cascades down
	// from there.
	private void DPDataArrived(String DPN, String newValue, int iNewValue, boolean isNew) {
		
		String VIN = "";
		// setPIDDataByName(DPN, newValue,iNewValue);

		// Was it a VIN message - part one or two (we're not assuming 1 arrives
		// before 2)
		if (DPN.equals("VIN2OF2") || DPN.equals("VIN1OF2")) {
			// Check and make sure we have both parts, if so, assemble the whole
			// thing and set the data!
			if (getCachedDataByName("VIN1OF2").length() > 0
					&& getCachedDataByName("VIN2OF2").length() > 0) {
				VIN = getCachedDataByName("VIN1OF2")
						+ getCachedDataByName("VIN2OF2");
				setPIDDataByName("VIN", VIN, 0);
			}
		}

		// so there's new data - a VIN - hooray!
		if (DPN.equals("VIN") && isNew == true) {
			msg("VIN Detected! VIN=" + newValue);
			startANewTrip(getCachedDataByName("VIN"));
			
			// This is for the passive network stuff... If we already know the network ID then pull it from cache. Don't perform detection here because we're most likely not in a state where it's appropriate. 
			String potentialNetworkID = getCachedNetworkID();
			if (potentialNetworkID.length() > 0) 
				mNetwork = potentialNetworkID;
		}

		// Log all "new" DPNs to the attributes table to get a "baseline" of
		// every paramter.
		if (isNew && mTripID.length() > 0) {
			ddb.setProfileValue("TRIPINITIALVALUE", mTripID, DPN, newValue);
		}

		// If we just got a wheel speed reading, average all 4 wheels to produce a "SPEED" broadcast. We do this because I just can't find a general speed reading anywhere else on the 11-bit network.
		// Also note that we are looking for a change in "RND" which is right non-driven wheel. That is always the last of the four wheel readings to get decoded. 
		if (DPN.equals("SPEED_WHEEL_RND")) {
			double s = 0;
			
			try {
				s = s + Double.valueOf(getCachedDataByName("SPEED_WHEEL_RND"));
				s = s + Double.valueOf(getCachedDataByName("SPEED_WHEEL_LND"));
				s = s + Double.valueOf(getCachedDataByName("SPEED_WHEEL_RD"));
				s = s + Double.valueOf(getCachedDataByName("SPEED_WHEEL_LD"));
				s = s / 4;

				// All the above calculations must have been exception free, so kick out a SPEED broadcast.
				setPIDDataByName("SPEED", "" + s, (int)Math.round(s));

			} catch (Exception e) {
				// Do nothing, at least we tried. 
			}

		}
			
		
	}

	/**
	 * Starts a new trip record in the DB.
	 * 
	 * @param VIN
	 * @return
	 */
	// TODO: Move STARTANEWTRIP to dashDB and keep the "initial value" saves in
	// this class.
	private String startANewTrip(String VIN) {
		mTripID = "" + EasyTime.getUnixTime();

		ddb.setProfileValue("VEHICLE", "TRIP", "VIN", VIN);

		// Define some things that we'll go out and try to get. If they aren't
		// present (yet) then they will be captured on their initial detection
		// after the trip is created by way of isNew paramter and
		// dpDataArrived().
		String usefulDPNs[] = { "ODOMETER", "VIN", "TEMP_COOLANT", "BAROMETER",
				"RPM", "BATTERY_SOC", "GEAR_SHIFT_POSITION", "GEAR_ESTIMATED",
				"TRANNY_OIL_TEMP", "ENGINE_OIL_PRESSURE", "BATTERY_SOC" };

		String thisDPN = "";
		String thisDPNValue = "";
		// Loop through all the useful paramters and log each one to the
		// attributes table.
		for (int i = 0; i < usefulDPNs.length; i++) {
			thisDPN = usefulDPNs[i];
			thisDPNValue = getCachedDataByName(usefulDPNs[i]);
			if (getCachedDataByName(thisDPN).length() > 0) {
				ddb.setProfileValue("TRIPINITIALVALUE", mTripID, thisDPN,thisDPNValue);
			}
		}

		return mTripID;
	}

	/**
	 * Given the bit position specified in a formula, translate that to the
	 * string position within the data hexstring where we go to get the data.
	 * This function takes into account two things and returns the appropriate
	 * result to be used in String.substring: - formula stop bit is assumed to
	 * be one bit before the next byte, IOW includes the last bit of the data it
	 * wants - String substring function requires us to specify the byte BEYOND
	 * the last byte we want from the string.
	 * 
	 * @param bitPos
	 *            - bit position specified in the formula.
	 * @return - returns the string position to be used in a substring. Odds are
	 *         assumed to be stop bits, event are start.
	 */
	private static int getStringPositionOfBit(int bitPos) {
		int ret = -1;

		switch (bitPos) {
		case 0:
			ret = 0;
			break;
		case 8:
			ret = 3;
			break;
		case 16:
			ret = 6;
			break;
		case 24:
			ret = 9;
			break;
		case 32:
			ret = 12;
			break;
		case 40:
			ret = 15;
			break;
		case 48:
			ret = 18;
			break;
		case 56:
			ret = 21;
			break;

		case 7:
			ret = 2;
			break;
		case 15:
			ret = 5;
			break;
		case 23:
			ret = 8;
			break;
		case 31:
			ret = 11;
			break;
		case 39:
			ret = 14;
			break;
		case 47:
			ret = 17;
			break;
		case 55:
			ret = 20;
			break;
		case 63:
			ret = 23;
			break;
		}

		return ret;
	}

	/**
	 * Inject test data into the stream of PIDs we're supposedly reading from
	 * the device. We will read that test data from a file on the device called
	 * /sdcard/Dash/replaylog This file is NOT distributed with Voyager.
	 */
	public boolean injectTestData() {

		if (mInjectorThread != null) {
			msg("Not creating injector thread twice.");
			return false;
		}

		FileReader fr = null;
		BufferedReader inStream = null;

		try {
			fr = new FileReader("/sdcard/Dash/replaylog");
			inStream = new BufferedReader(fr);
		} catch (FileNotFoundException e) {
			return false;
		}

		final BufferedReader _inStream = inStream;

		// OK so we have a good file handle now. lets use it.
		mInjectorThread = new Thread() {
			public void run() {
				String thisLine = "";
				// loop for as long as there is data to read and the injector
				// flag variable is true!
				while (mInjectorThreadOn == true) {
					try {
						thisLine = _inStream.readLine();
					} catch (Exception e) {
						// break out of the while at the first sign of trouble.
						msg("Injector done. E=" + e.getMessage());
						break;
					}
					// process the line.
					decodeOneMessage(thisLine);
					//safeSleep(10);
				}// end of while which loops through the whole input file!
			}// end of run()
		};// end of thread definition

		mInjectorThread.start();

		return true;
	}

	public void stopInjectingTestData() {
		mInjectorThreadOn = false;
		mInjectorThread = null;
	}

	/**
	 * Sleeps for the specified interval, or shorter if interrupted. Exceptions
	 * are caught so you don't have to.
	 * 
	 * @param millis
	 */
	private void safeSleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			return;
		}

	}

	/**
	 * Public interface to our stats calculation logic. 
	 * @return
	 */
	public NetworkStats getNetworkStats () {
		int i=0;

		// if nStats is null, then try to prevent nullpointer in the caller, by blocking for a few seconds. 
		while (nStats == null && i < 3) {
			msg ("WARNING: nStats is null, so we're blocking until it's instantiated.");
			safeSleep (1000);
			i++;
		}
		
		return nStats;
	}

	/**
	 * Passes a reference to our stats class up to a calling class.
	 * @return - returns a collection of our stats and those collected from our children. 
	 */
	public GeneralStats getStats() {
		// Collect a few of our own stats...
		mgStats.setStat("tripID", mTripID);
		mgStats.setStat("networkID", mNetwork);
		if (mhmDataByName != null) mgStats.setStat("cachelevel.databyname","" + mhmDataByName.size());
		if (mhmDPRawData != null) mgStats.setStat("cachelevel.dprawdata","" + mhmDPRawData.size());
		mgStats.setStat("thread.age", "" + metime.getUptimeSeconds());
		if (mNewDataHandler != null) 
			mgStats.setStat("datahandlerdefined", "true");
		else
			mgStats.setStat("datahandlerdefined", "false");
		
		// collect stats from network stats thingie. 
		if (nStats != null)
			mgStats.merge("netStats",nStats.getStats());

		// Collect stats from the net detector. 
		if (mNetDetect != null)
			mgStats.merge("netDetect", mNetDetect.getStats());
		
		return mgStats;
	}


	/**
	 * You must be in CAN monitor/passive mode, collecting data.
	 * This method blocks for a while - possibly 60 seconds, to wait for the network to settle.  
	 * Returns a "fingerprint" of the network. 
	 * The finterprint should be sufficiently unique to uniquely identify the protocol (GM/Toyota/Ford, etc) in use on the network. 
	 * @return - blank string ("") on error, or a string describing the network... (the "fingerprint")
	 */
	public String getMoniNetFingerprint() {
		
		long timeStart = EasyTime.getUnixTime();

		// block while network settles, if timeout then false is returned, suggesing the network isn't settled. 
		boolean settled = waitForMoniNetToSettle();
		long timeSpent = EasyTime.getUnixTime() - timeStart;

		if (settled == false) {
			mgStats.setStat("timeToGetFingerprint", ">" + timeSpent + "s");
			return "";
		} else {
			mgStats.setStat("timeToGetFingerprint", "" + timeSpent + "s");
			return moniFingerprintNow();
		}
		
	}

	/**
	 * Blocks until the network is settled, or a timeout occurs
	 * @return - return false if a timeout occurred, or true if the network is settled. 
	 */
	private boolean waitForMoniNetToSettle() {
		
		int loopCount = 0;
		// Keep in mind that the network has to see no new PIDs for 5+ seconds for it to be considered settled. 
		// That means we have to wait AT LEAST 5 + X seconds for it to settle, where X is a reasonable number.
		final int loopDelay = 250;
		final int maxSecondsToSettle = 60;
		final int maxLoops = (1000/loopDelay) * maxSecondsToSettle;
		
		// wait for it to settle, or a timeout. 
		while (!networkIsSettled() && loopCount < maxLoops) {
			EasyTime.safeSleep(loopDelay);
			loopCount++;
			if (loopCount > 0 && (loopCount % 20) == 0)
				msg ("Network fingerprint waiting for network to settle. Elapsed=" + (loopCount * loopDelay) + "ms" + " lastNewPID=" + nStats.getTimeSinceLastNewPID() + "s");
		} // end of while loop. 

		
		if (networkIsSettled()) {
			// network settled before a timeout occurred!
			mgStats.setStat("timeTilNetworkSettled", "" + (loopCount * loopDelay) + "ms");
			return true;
		}
		else {
			// network didn't settle before our timeout. 
			mgStats.setStat("timeTilNetworkSettled", ">" + (loopCount * loopDelay) + "ms");
			msg ("Timeout waiting for network to settle. PIDs=" + nStats.getPIDCount() + " time since last=" + nStats.getTimeSinceLastNewPID());
		}
			
		return false;
		
	}

	/**
	 * Determine if the monitor/passive network is settled. 
	 * @return - true if it's settled, false otherwise. 
	 */
	private boolean networkIsSettled() {
		if (nStats.getTimeSinceLastNewPID() >= 5)
			return true;
		
		return false;
	}

	/**
	 * Returns an immediate fingerprint of the network. 
	 * Recommend not calling this directly, rather call it via getMoniNetFingerprint() because it will wait for the net to settle if necessary. 
	 * @return
	 */
	private String moniFingerprintNow () {
		String fingerprint = "";
		
		// Number of PIDs total. 
//		fingerprint = "PIDCOUNT=" + nStats.getPIDCount();
		fingerprint += "," + nStats.getIDList();
		
		
		return fingerprint;
	}
	
	
	/**
	 * Convenience method to get the cached network ID based on the VIN DPN, if present.
	 * YOU MUST SET mNetwork to the value that this method returns, if you like what we have to say. 
	 * @return - returns "" if VIN is not set and or no Network ID is cached.  
	 */
	private String getCachedNetworkID () {
		String VIN = getPIDDataByName("VIN");
		String cachedNetID = "";
		
		if (VIN.length() <=0) 
			return "";
		
		 cachedNetID = ddb.getProfileValue("NETWORK", "ID", VIN);
		
		// Is it cached? If so, return it! 
		if (cachedNetID.length()>0) {
			msg ("Found cached data for network detection. VIN " + VIN + " network ID is " + cachedNetID);
			return cachedNetID;
		}

		return cachedNetID;
	}
	
	/**
	 * Looks at the current mNetwork value (which is the passive decoder network ID) and saves it to persistent storage.
	 * @return - returns false if there was a problem, true otherwise. 
	 */
	private boolean setCachedNetworkID () {
		String VIN = getPIDDataByName("VIN");
		if (VIN.length() > 0)
			ddb.setProfileValue("NETWORK", "ID", VIN, mNetwork);
		else
			return false;
		
		return true;
	}

	/**
	 * Convenience method to make a request, and if we get a response from multiple ECUs, we just return the first. 
	 * @param DPN (data point name)
	 * @return - returns the response from the first ECU. Could be different ECU across multiple requets for the same DPN.
	 */
	public String getDataViaOBD_singleNode(String DPN) {
		String ret = "";
	
		String resp = "";
		resp = getDataViaOBD(DPN);
		
		if (resp.contains(",")) {
			// grab first response. 
			ret = resp.split(",")[0];
		}
		
		return ret;
	}

	
	
	
}// end of PIDDecoder class
