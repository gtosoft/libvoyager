package com.gtosoft.libvoyager.svip;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import android.text.InputFilter.LengthFilter;
import android.util.Log;

import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.OOBMessageTypes;

/**
 * This class will connect to the specified peer IP and open a connection for commands/events using
 * the SVIP - Simple Vehicle Interface Protocol. 
 * @author Brad Hein / GTOSoft LLC
 */

public class SVIPTCPClient {
	boolean 		DEBUG = true;
	Thread			mtMessagesAndState;
	BufferedInputStream 	mInput;
	OutputStream   	mOutput;
	Socket		   	mServerSocket;
	boolean			mThreadsOn = true;
	Queue<String>   mResponseMessageQueue = new LinkedList<String>();
	EventCallback	mECBDPArrivedHandler;
	EventCallback	mECBOOBArrivedHandler;
	GeneralStats	mgStats = new GeneralStats();
	String 			SERVER_NAME = "localhost";
	int 			SERVER_PORT = 62009;
	
	
	
	/**
	 * default constructor. 
	 */
	public SVIPTCPClient() {
		startMessageGetterThread();
	}
	
	public void shutdown () {
		mThreadsOn = false;
		
		// interrupt the loop thread in case it's in a sleep or wait state. 
		if (mtMessagesAndState != null) mtMessagesAndState.interrupt();
		
		tearDownConnection();
	}
	
	/**
	 * break down the connection if there is one. 
	 */
	private void tearDownConnection () {
		try { mInput.close();}			catch (Exception e) {}
		try { mOutput.close();} 		catch (Exception e) {}
		try { mServerSocket.close();}	catch (Exception e) {}
	}
	

	
	/**
	 * starts the message getter thread. It checks for full messages waiting to be processed, and processes them. 
	 */
	private void startMessageGetterThread () {
		if (mtMessagesAndState != null) {
			return;
		}
		
		mtMessagesAndState = new Thread() {
			public void run () {
				// main thread loop. If we're not connected, then connect. Otherwise sleep a bit. 
				//   If there are messages, process them. 
				while (mThreadsOn == true) {
					// Check the connection state. If not connected then try to get connected. If connection fails then sleep a bit. 
					checkConnectionState();
					
					// if connected then get next message and process it (pass it to event handler or command-response handler). 
					if (connected() == true) {
						String thisMessage = getNextMessage ();
//						if (thisMessage.length()>0) 
//							msg ("*****Got next message. len=" + thisMessage.length() + " msg=" + thisMessage);
						if (thisMessage != null && thisMessage.length() > 0) {
							processOneMessage (thisMessage);
						} else {
							// we checked for messages but there were none. 
							EasyTime.safeSleep(250);
						} // end of "if this message has size... or doesn't"
					} else {
						// not connected. Sleep a bit between retries. 
						EasyTime.safeSleep(5000);
					}  // end of "if we're NOT connected"
				}// end of "loop while thread are on". 
				msg ("Main-thread has exited. We'll now tear down the connection.");
				tearDownConnection();
			}// end of run()

		};// end of thread definition. 
		
		mtMessagesAndState.start();
	}
	

	/**
	 * 	read from the input stream UP TO (and including) the ">" character.
	 *  Append whatever we get, to the existing variable  
	 *  if the existing variable is a whole mesage, then return it. 
	 *  OTHERWISE. return null. 
	 */
	String currentMsg = "";
	private synchronized String getNextMessage() {
		String thisChunk = "";
		
		// read from the input buffer up to one whole message. 
		try {
			thisChunk = readUpToCharacter('>');
		} catch (Exception e) {
			msg ("Error getting data: " + e.getMessage());
		}
		
		if (thisChunk == null || thisChunk.length() < 1) {
			return "";
		}

		// append whatever we got, to the global current message accumulator variable. 
		currentMsg = currentMsg + thisChunk;
		
		// is message complete? 
		if (currentMsg.endsWith(">")) {
			// create a brand new string based on the current one (but a different copy)  
			String goodMessage = "" + currentMsg;
			// reset the current message variable, since we just consumed its contents. 
			currentMsg = "";
			return goodMessage;
		}
		
		return null;
	}

	private String readUpToCharacter (int theStopCharacter) throws IOException {
		String thisData = "";
		int thisChar = 0;
		
		// loop until we reach end of buffer or the stop character. 
		while (mInput.available() > 0 && thisChar != theStopCharacter) {
			thisChar = mInput.read();
			thisData = thisData + ((char) thisChar);
		}
		
		return thisData;
	}
	
	/**
	 * Handles a single message from the server. 
	 * Basically we just pass it over to a processor method - Event processor, or command-response processor.
	 * @param theMessage
	 * @return - true if a message was processed. False if none was available or we didn't recognize the last. 
	 */
	private boolean processOneMessage (String theMessage) {

		// is the message packet valid? 
		if (theMessage == null || theMessage.length() < 1) {
			return false;
		}
		
		// Is it a command response packet? 
		if (theMessage.startsWith("ACK") || theMessage.startsWith("NACK")) {
			processResponseMessage (theMessage);
			return true;
		}

		// Is it a new datapoint data? 
		if (theMessage.startsWith("DPN_ARRIVED")) {
			processDPDataMessage (theMessage);
			return true;
		}

		// Is it a new out-of-band message? 
		if (theMessage.startsWith("OOB")) {
			processOOBMessage (theMessage);
			return true;
		}


		// Unrecognized?
		msg ("Unrecognized server message: " + theMessage);
		
		return false;
	}
	
	

	private void processDPDataMessage(String theMessage) {
		String[] msgparts = theMessage.split("\\|");
		
		if (msgparts == null ) {
			msg ("Runt DP message: " + theMessage );
			return;
		}
		
		if (msgparts.length < 4) {
			msg ("Runt DP message: " + theMessage +  " numParts=" + msgparts.length);
			return;
		}
		
		// grab the parts. 
		String DPN = msgparts[1];
		String sDecodedData = msgparts[2];

		// kick off event. 
		if (mECBDPArrivedHandler != null) {
			mECBDPArrivedHandler.onDPArrived(DPN, sDecodedData, 0);
		}
		
	}

	private void processOOBMessage(String theMessage) {
		String[] msgparts = theMessage.split("\\|");

		if (msgparts == null) {
			msg ("Runt OOB message: " + theMessage);
			return;
		}

		if (msgparts.length < 4) {
			msg ("Runt OOB message: " + theMessage + " numParts=" + msgparts.length);
			return;
		}

		// skip msgParts[0], which is the echo-back of the command. 
		String dataName  = msgparts[1];
		String dataValue = msgparts[2];
		
		if (mECBOOBArrivedHandler != null) {
			mECBOOBArrivedHandler.onOOBDataArrived(dataName, dataValue);
		}
		
	}

	/**
	 * Simply queues the command response so whomever sent the command can get it. 
	 * @param theMessage
	 */
	private void processResponseMessage(String theMessage) {
		// sanity checks. 
		if (theMessage == null)
			return;
		
		// Queue the message in its encapsulated form so no parts of it are lost. 
		mResponseMessageQueue.add(theMessage);
	}

	/**
	 * sends the given message to the server
	 * @param theMessage - the message to send
	 * @return - returns true on success, false otherwise. 
	 */
	private boolean sendMessage (String theMessage) {
		byte [] buffer = theMessage.getBytes();
		try {
			mOutput.write(buffer, 0, buffer.length);
		} catch (Exception e) {
			msg ("Error sending " + theMessage + " E=" + e.getMessage());
			return false;
		}
		
		return true;
	}
	
//	/**
//	 * Queries the server by sending a message and getting the response. 
//	 * @param theMessageToSend
//	 * @return
//	 */
//	private String queryServer (String theMessageToSend) {
//		// TODO: Send SVIP request using sendMessage
//		// TODO: Get SVIP Response. 
//		// TODO: Return that response. 
//	}
	
	/**
	 * Pings the server and returns the latency, in ms, accurate to about 200ms. 
	 * @return - returns number of milliseconds between request and response. 
			TODO: use queryserver() to do the ping.
	 */
	public int pingServer () {
		final int sleepSliceTime = 200;
		int loopCount = 0;
		// five second timeout? 
		final int maxLoopCount = 25;
		int latency = 0;

		sendMessage("PING|>");
		
		// wait for a response. Wait no longer than the max loop count lets us (about 5 seconds). 
		while (mResponseMessageQueue.isEmpty() && loopCount <= maxLoopCount) {
			loopCount++;
			// sleep for a bit, break out of the loop if we are interrupted. 
			if (!EasyTime.safeSleep(200)) break;
		}
		
		// Timeout waiting for ping response from server. 
		if (loopCount > maxLoopCount) {
			if (DEBUG) msg ("Timeout while waiting for ping response from server");
			return 9999;
		}
		
		latency = loopCount * sleepSliceTime;
		
		// get a peek at the response. Is it ours? 
		String response = mResponseMessageQueue.peek().toUpperCase(); 
		if (response.contains("PONG") && response.contains("ACK")) {
			response = mResponseMessageQueue.remove();
			if (DEBUG) msg ("Ping response: " + response + " in " + latency + "ms.");
		}
		
		

		// return "milliseconds spent waiting for a response". 
		return (latency);
		
	}
	

	
	/**
	 * basic diagnostic messages thing. 
	 * @param m
	 */
	private void msg (String m) {
		Log.d("SVIPTCPClient",m);
	}
	
	/**
	 * Checks the state of the connection. 
	 * If we're disconnected, try to connect. 
	 * If we're already connected then there's nothing to do here. 
	 */
	private void checkConnectionState () {
		boolean ret;
		// Are we already connected? 
		if (mServerSocket == null || mServerSocket.isConnected() != true) {
			// not connected, so try to connect. 
			msg ("Trying to connect to server...");
			mgStats.incrementStat("svip.connectToServer.attempts");
			ret = tryToConnect();
			if (DEBUG) msg ("connection returned " + ret);
			if (ret) {
				// successful connect! attach streams. 
				msg ("Successful connect! attaching streams.");
				ret = attachStreams();
				if (ret) {
					sendOOBEvent (OOBMessageTypes.SVIP_CLIENT_JUST_CONNECTED,"");
				} else {
					// failed attempt to connect.
					mgStats.incrementStat("svip.connectToServer.fails");
				}
			}// end of "if we just connected"
		}// end of "if we need to connect"
	}

	private void sendOOBEvent (String eventName, String eventData) {
		// TODO: add data to this stub if desired. 
	}
	
	/**
	 * Assuming the socket just came to life, this function will attach the streams (buffered input stream and regular output stream). 
	 * @return
	 */
	private boolean attachStreams () {
		try {
			mInput = new BufferedInputStream(mServerSocket.getInputStream(),1024);
			mOutput = mServerSocket.getOutputStream();
		} catch (Exception e) {
			msg ("Error attaching streams: " + e.getMessage());
			return false;
		}
		
		return true;
	}
	
	/**
	 * try to connect to the server. 
	 * @return
	 */
	private boolean tryToConnect () {
//		SocketAddress remoteAddr = new InetSocketAddress(SERVER_NAME, SERVER_PORT);
		
		if (mServerSocket == null) {
			try {
				mServerSocket = new Socket(SERVER_NAME, SERVER_PORT);
				msg ("**** I think we just connected! server=" + SERVER_NAME);
			} catch (Exception e) {
				msg ("Error creating socket to server " + SERVER_NAME + "on port " + SERVER_PORT + " E=" + e.getMessage());
				return false;
			}
		}
		
//		try {
//			mServerSocket.connect(remoteAddr, 5000);
//		} catch (Exception e) {
//			msg ("Error connecting to server. E=" + e.getMessage());
//			return false;
//		}

		return true;
	}

	/**
	 * 
	 * @return - returns our generalstats object, for assimilation by our parent. 
	 */
	public GeneralStats getStats () {
		return mgStats;
	}
	
	/**
	 * @return - Returns true if we're connected to the server and ready to rock. false otherwise.
	 */
	public boolean connected () {
		if (mServerSocket != null && mServerSocket.isConnected() == true) {
			return true;
		} else {
			return false;
		}
		
	}// end of connected definition. 

	public void registerDPArrivedHandler (EventCallback e) {
		mECBDPArrivedHandler = e;
	}
	
	public void registerOOBArrivedHandler (EventCallback e) {
		mECBOOBArrivedHandler = e;
	}

	/**
	 * @param DPN - the datapoint to which we are to subscribe. 
	 * ONLY CALL THIS METHOD IF SVIP IS CONNECTED! DUH!
	 * @return - true on success, false otherwise. 
	 */
	public boolean subscribe(String DPN) {
		// just build it ourself for now. 
		return sendMessage("SUBSCRIBE|" + DPN + "|>");
	}

	/**
	 * Convenience method to subscribe to a list of datapoints. 
	 * ONLY CALL THIS METHOD IF SVIP IS CONNECTED! DUH!
	 * @param DPSubscriptions
	 */
	public void subscribe(List<String> DPSubscriptions) {
		for (String dp : DPSubscriptions) {
			if (DEBUG) msg ("Adding subscription for DPN " + dp);
			subscribe(dp);
		}
		
	}

	
}// end of class. 
