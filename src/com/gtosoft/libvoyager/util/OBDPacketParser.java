/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import android.util.Log;


/**
 * @author brad
 * The purpose of this class is to facilitate the process of finding the data bytes within OBD packets. 
 * The problem being that there are a ton of protocols - 10 in fact are supported by the ELM327. 
 * So basically the hard part is extracting the data from the packets while also hopefully retaining 
 * other important information such as transmitter node address. 
 * 
 *  The ultimate goal is to pass this class something like this:
 *   Request =03 
 *   Response=03|7E8 02 43 00 |7EA 02 43 00 ||>
 *   Protocol=6 (AUTO, ISO 15765-4 (CAN 11/500))
 *  And for us to produce a hashmap containing the following representation of that data: 
 *   7E8=43 00
 *   7EA=43 00 
 *   
 *  Usage: Instantiate it once for the life of the protocol you're using. make repeated calls for various packets to decode them. 
 */


public class OBDPacketParser {
	
	int numPacketsProcessed = 0;
	
	private final boolean DEBUG = false;
	
	GeneralStats genStats = new GeneralStats();

	// define the list of protocols. The integer corresponds to the ELM327 ATDPN value. 
//	public static final int PROTOCOL_UNKNOWN			=  0;	// PWM (41.6K Baud)
	public static final int PROTOCOL_SAEJ1850PWM		=  1;	// PWM (41.6K Baud)
	public static final int PROTOCOL_SAEJ1850VPW		=  2;	// VPW (10.4K Baud)
	public static final int PROTOCOL_ISO9141_2			=  3;	// K-line. Like RS232 (Found in Chrysler, European, Asian vehicles)
	public static final int PROTOCOL_ISO14230_4KWP		=  4;	// KWP
	public static final int PROTOCOL_ISO14230_4KWPfast	=  5;	// KWP
	public static final int PROTOCOL_ISO15765_4CAN11500	=  6;	// CAN
	public static final int PROTOCOL_ISO15765_4CAN29500	=  7;	// CAN
	public static final int PROTOCOL_ISO15765_4CAN11250	=  8;	// CAN
	public static final int PROTOCOL_ISO15765_4CAN29250	=  9;	// CAN
	public static final int PROTOCOL_SAEJ1939CAN29250	= 10;	// CAN

	public class PacketAttributes {
		// the ELM327 Protocol. 
		int PROTOCOL;
		// Number of string bytes occupied by the header - for example "123" occupied 3 and "10 03 00 20" occupies 11. For CAN we need to calculate this on the fly for each packet because we can have 11 and 29-bit packets on the same wire.
		public int numHeaderStringBytes;
		// Is there a trailing checksum on each packet? 
		public boolean hasChecksum; 
		// Is there a DTC Count byte preceeding the data part of an 03 "DTC" request?  
		public boolean hasDTCCountPrefix;
	};

	// Will contain attributes about this particular packet. 
	PacketAttributes pa = new PacketAttributes();
	
	
	/**
	 * Default constructor
	 * @param ELMProtocolNumber - the protocol number from ELM327 command "AT DPN" 
	 */
	public OBDPacketParser (int ELMProtocolNumber) {
		setProtocolNum(ELMProtocolNumber);

		// set initial values. 
		genStats.setStat("numPacketsProcessed", "" + numPacketsProcessed);

	}
	

	/**
	 * Sets/changes the protocolnumber. 
	 * @param ELMProtocolNumber
	 */
	private void setProtocolNum (int ELMProtocolNumber) {
		genStats.setStat("protocolNum", "" + ELMProtocolNumber);
		pa.PROTOCOL = ELMProtocolNumber;
		
		setPacketAttributes();
		
	}

	/**
	 * Returns a reference to our generalstats object so you can make a copy of it or access items from it. Please don't modify it.
	 * @return - returns our GeneralStats object.
	 */
	public GeneralStats getStats () {
		return genStats;
	}

	
	/**
	 * Based on the current PROTOCOL, set the packet attributes. 
	 * @return
	 */
	private boolean setPacketAttributes () {
		
		if (!isCANProtocol()) {
			// Not CAN
			pa.hasChecksum 			= true;		// 
			pa.hasDTCCountPrefix 	= false;	// 
			pa.numHeaderStringBytes = 8; 		// Geez, hope this works!
		} else {
			// Must be CAN
			pa.hasChecksum 			= false; 
			pa.hasDTCCountPrefix 	= true;
			pa.numHeaderStringBytes = -1; 		// unknown right now - need to calculate this for every single packet.
		}
		
		return true;
	}
	

	/**
	 * ONLY for CAN packets!!! Do not pass this any other type of packet, it doesn't understand anything NON-CAN. 
	 * Based on the given packet (only the response part please!!!) we will tell you if it appears to be formatted for 11-bit or 29-bit CAN. 
	 * @param CANPacket - the packet, without echo-back or anythingthing like that. The first byte is assumed to be the start of the header. 
	 * @return
	 */
	private int getCANHeaderLength (String CANPacket) {
		if (CANPacket.length() < 3) {
			return CANPacket.length();
		}
		
		// 7E8 xx yy zz	- results in "3"
		// 10 02 04 55	- results in "2"

		int posOfFirstSpace = CANPacket.indexOf(" ");
		
		if (posOfFirstSpace == 2) {
			// 29-BIT!
			return 11;
		} else {
			// 11-BIT!
			return 3;
		}
	}
	
	/**
	 * @return - TRUE if the current protocol is a CAN protocol. 
	 */
	private boolean isCANProtocol () {
		if (pa.PROTOCOL >= 6)
			return true;
		else
			return false;
	}

	private String getACKBytesForRequest (String request) {
		String retNoSpaces;
		String retWithSpaces;
		request = request.replace(" ", "");
		
		int firstChar=0;
		firstChar = Integer.valueOf(request.substring(0,1),16);
		firstChar += 4;

		
		// set the first character now that we modified it as necessary. 
		retNoSpaces = Integer.toHexString(firstChar) + request.substring(1);

		retWithSpaces = "";
		
		// notice we start at 1. 
		// Loop through all the request characters, inserting a space between each byte (2 characters) 
		for (int i=0; i<retNoSpaces.length();i++) {
			retWithSpaces += retNoSpaces.substring(i,i+1);
			
			// if it's an odd character and if we're not on the last one (the second part of the check makes sure we don't add a trailing space). 
			if (i % 2 == 1 && i < retNoSpaces.length() - 1)
				retWithSpaces = retWithSpaces + " ";
		}
		
		if (request.length() != 2 && request.length() != 4) {
			msg ("WARNING: Oddball request observed: " + request);
			retWithSpaces = request;
		}
		
		if (DEBUG) msg ("For request " + request + " The echo-back is " + retWithSpaces + "(len=" + retWithSpaces.length() + ")");
		
		return retWithSpaces;
	}
	
	public HashMap<String,String> getData (String request, String response) {
		numPacketsProcessed++;
		
		genStats.setStat("numPacketsProcessed", "" + numPacketsProcessed);

		if (isCANProtocol() == true) {
			return getData_CAN(request, response);
		} else {
			return getData_NONCAN(request, response);
		}

	}
	
	/**
	 * @param request - request sequence, for example "03" or "01 0C"
	 * @param response - the exact response received, including all formatting. 
	 * @return - returns a hashmap containing the full re-assembled data for each different node. Or a blank hashmap on error. 
	 */
	public HashMap<String,String> getData_NONCAN (String request, String response) {
		String thisHeader;
		String thisData;
		String ackString = getACKBytesForRequest (request);
		int dataOffset;
		int dataCharCount;
		
		HashMap<String,String> hmData = new HashMap<String,String>();

		// if response isn't valid, return a blank (non-null) response hashmap. 
		if (response.length() < 2)
			return hmData;
		
		// Break out the individual packets. 
		String [] packet = response.split("\\|");
		
		// loop through all the individual packets, processing each one. 
		for (int i=0;i<packet.length;i++) {
			// Throw out runt packets. 
			if (packet[i].length() < 2) continue;
			// If necessary, perform on-the-fly CAN calculations. 
			if (isCANProtocol()) {
				pa.numHeaderStringBytes = getCANHeaderLength(packet[i]);
			}
			
			dataOffset = getDataOffset (packet[i],ackString);
//			if (DEBUG) msg ("Dataoffset=" + dataOffset + " for packet " + packet[i] + " which has ackString=" + ackString);
			
			dataCharCount = getDataCharCount (packet[i],dataOffset,pa);
//			if (DEBUG) msg ("dataCharCount=" + dataCharCount + " for packet " + packet[i]);
			
			if (dataOffset < pa.numHeaderStringBytes) {
				if (DEBUG) msg ("Threw out packet because we dont know where data is. Packet=" + packet[i]);
				continue;
			}
			
			// check for -1 but let zero-length packets through, so we get an entry for packets with no data. 
			if (dataCharCount == -1) {
//				if (DEBUG) msg ("Threw out packet because data length is invalid. Packet=" + packet[i]);
				continue;
			}
			
			// Attempt to extract the header from the packet. 
			try { thisHeader 	= packet[i].substring(0,pa.numHeaderStringBytes);
			} catch (Exception e) {
				msg ("ERROR while extracting header from packet: " + packet[i] + " E=" + e.getMessage());
				continue;
			}

			// Attempt to extract data portion of packet. 
			try { thisData = packet[i].substring(dataOffset,dataOffset + dataCharCount);
			} catch (Exception e) {
				msg ("ERROR while extracting data from packet: " + packet[i] + " E=" + e.getMessage());
				continue;
			}
			
			if (hmData.containsKey(thisHeader)) {
				// Append new data to the existing data. 
				String oldData = hmData.get(thisHeader);
				hmData.put(thisHeader, oldData + " " + thisData);
			} else {
				// create a new entry for this ECU and its initial data. 
				hmData.put(thisHeader, thisData);
			}

		}// end of for-loop which loops through each packet
		
		return hmData;
		
	}// end of getData method. 

	/**
	 * @param request - request sequence, for example "03" or "01 0C"
	 * @param response - the exact response received, including all formatting. 
	 * @return - returns a hashmap containing the full re-assembled data for each different node. Or a blank hashmap on error. 
	 */
	public HashMap<String,String> getData_CAN (String request, String response) {
		String thisHeader;
		String thisData;
		String ackString = getACKBytesForRequest (request);
		int dataOffset;
		int dataCharCount;
		int CANSizeByteOffset;
		
		HashMap<String,String> hmData = new HashMap<String,String>();

		// if response isn't valid, return a blank (non-null) response hashmap. 
		if (response.length() < 2)
			return hmData;
		
		// Break out the individual packets. 
		String [] packet = response.split("\\|");
		
		TreeSet<String> sHeadersSeen = new TreeSet<String>();
		
		// NOTICE that we start at "1" here. That skips us past the AT-terminal-echo-back. 
		// loop through all the individual packets, processing each one. 
		for (int i=1;i<packet.length;i++) {
			// Throw out runt packets. 
			if (packet[i].length() < 2) continue;
			// perform on-the-fly CAN calculations. 
			pa.numHeaderStringBytes = getCANHeaderLength(packet[i]);

			// Attempt to extract the header from the packet. 
			try { thisHeader = packet[i].substring(0,pa.numHeaderStringBytes);
			} catch (Exception e) {
				msg ("ERROR while extracting header from packet: " + packet[i] + " E=" + e.getMessage());
				continue;
			}
			
			if (!sHeadersSeen.contains(thisHeader)) {
				sHeadersSeen.add(thisHeader);
				
				if (isMultipartFirst(packet[i], pa.numHeaderStringBytes)) {
					if (DEBUG) msg ("Packet " + packet[i] + " is a multipart first packet. ");
					// first time seeing this header and it is multipart (so there's one extra byte for seq# in this one packet) 
					CANSizeByteOffset 	= pa.numHeaderStringBytes + 1+2 +1;
					dataOffset 			= pa.numHeaderStringBytes + 1+2 +1+2 +1 + ackString.length() + 1;
//					dataOffset 			+= 1+2 ; // scoot past the "number of items" byte.
					// for all non-mode-1 packets, chop off the "number of items" byte that CAN networks add. 
					if (!request.startsWith("01"))
						dataOffset += 1+2;
				} else {
					if (DEBUG) msg ("Packet " + packet[i] + " is a NON-multipart packet. ");
					// otherwise it's the first time we're seeing a non-multipart packet
					CANSizeByteOffset 	= pa.numHeaderStringBytes +1;
					dataOffset 			= pa.numHeaderStringBytes +1 +1+2 +ackString.length() + 1;
//					dataOffset 			+=  1+2 ; // scoot past the "number of items" byte.
					// for all non-mode-1 packets, chop off the "number of items" byte that CAN networks add. 
					if (!request.startsWith("01"))
						dataOffset += 1+2;
				}
				
			} else {
				if (DEBUG) msg ("Packet " + packet[i] + " is a multipart continuation packet. ");
				// not the first time seeing it, must be multipart. 
				CANSizeByteOffset 	= pa.numHeaderStringBytes + 1;
				dataOffset 			= pa.numHeaderStringBytes + 1+2 +1;
			}

			
			
			dataCharCount = packet[i].length();
			dataCharCount -= dataOffset;
			dataCharCount -= 1;// space after header. 
			
			if (dataOffset < pa.numHeaderStringBytes) {
				if (DEBUG) msg ("Threw out packet because we dont know where data is. Packet=" + packet[i]);
				continue;
			}
			
			// check for -1 but let zero-length packets through, so we get an entry for packets with no data. 
			if (dataCharCount == -1) {
//				if (DEBUG) msg ("Threw out packet because data length is invalid. Packet=" + packet[i]);
				continue;
			}
			

			// Attempt to extract data portion of packet. 
			try { thisData = packet[i].substring(dataOffset,dataOffset + dataCharCount);
			} catch (Exception e) {
				msg ("ERROR while extracting data from packet: " + packet[i] + " E=" + e.getMessage());
				continue;
			}
			
			if (hmData.containsKey(thisHeader)) {
				// Append new data to the existing data. 
				String oldData = hmData.get(thisHeader);
				hmData.put(thisHeader, oldData + " " + thisData);
			} else {
				// create a new entry for this ECU and its initial data. 
				hmData.put(thisHeader, thisData);
			}

		}// end of for-loop which loops through each packet
		
		return hmData;
		
	}// end of getData_CAN method. 

	
	/**
	 * Returns true if this is the first packet in a multipart sequence. 
	 */
	private boolean isMultipartFirst(String packet, int numHeaderStringBytes) {
		String sizeByte1;
		
		// grab the byte immediately following the header byte(s). 
		try {sizeByte1 = packet.substring(numHeaderStringBytes+1,numHeaderStringBytes+1+2);
		} catch (Exception e) {
			msg ("ERROR while trying to get CAN multipart size byte: " + e.getMessage() + " packet=" + packet);
			return false;
		}

		if (DEBUG) msg ("Basing multipart conclusion on this byte: -->" + sizeByte1 + "<--");
		
		// If that byte is "10" then it's a multipart packet. 
		if (sizeByte1.trim().equals("10")) {
			return true;
		} else {
			return false;
		}
		
	}


	/**
	 * @param packet
	 * @param dataOffset
	 * @param p
	 * @return - returns the number of string characters occupied by the data portion of the packet. 
	 */
	private int getDataCharCount (String packet, int dataOffset, PacketAttributes p) {
		int charCount;
		
//		if (DEBUG) msg ("dataCharCount packet=" + packet);
		charCount = packet.length();	// start with the full size of the packet. We'll subtract parts of the packet as we account for them. 
//		if (DEBUG) msg ("dataCharCount (" + charCount + ") starting with length");
		charCount -= dataOffset;		// the header and ack bytes
//		if (DEBUG) msg ("dataCharCount (" + charCount + ") subtracted dataoffset (" + dataOffset + ").");
		charCount -= 1;					// the space after the ack
//		if (DEBUG) msg ("dataCharCount (" + charCount + ") subtracted one haracter for the space.");
		
		if (p.hasChecksum == true)  {
			charCount -= 3;				// subtract the bytes occupied by the checksum and its preceeding space.  
//			if (DEBUG) msg ("dataCharCount (" + charCount + ") subtracted three characters for checksum.");
		}

		return charCount;
	}
	
	/**
	 * @param packet - the packet in its entirety, starting with header. 
	 * @param ackString - the acknowledgement string that we shoul dexpect within the packet. 
	 * @return - the string offset index where the first data byte starts within the given packet. 
	 */
	private int getDataOffset (String packet, String ackString) {
		int ret = -1;
		
		ret = packet.indexOf(ackString) + ackString.length() + 1;
		
		return ret;
	}

	private void msg (String m) {
		Log.d("ByteFinder",m);
	}

	/**
	 * Convenience method: Gets the data as a hashmap and then converts it to a flat string, mainly for testing purposes. 
	 * @return - returns a string representation of all data found in the given response. 
	 */
	String getDataAsString (String request, String response) {
		HashMap<String,String> hmData = getData(request, response);
		String ret = "";
		
		TreeSet<String> s = new TreeSet<String> (hmData.keySet());
		
		Iterator<String> i = s.iterator();
		
		String thisKey;
		String thisVal;
		while (i.hasNext()) {
			thisKey = i.next();
			thisVal = hmData.get(thisKey);
			ret = ret + "[" + thisKey + "]=" + thisVal + " ";
		}
		
		return ret;
	}
	
}
