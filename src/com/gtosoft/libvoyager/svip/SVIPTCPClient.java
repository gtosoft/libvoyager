package com.gtosoft.libvoyager.svip;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.Queue;

import android.util.Log;

import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.EventCallback;
import com.gtosoft.libvoyager.util.GeneralStats;

/**
 * This class will connect to the specified peer IP and open a connection for commands/events using
 * the SVIP - Simple Vehicle Interface Protocol. 
 * @author Brad Hein / GTOSoft LLC
 */

public class SVIPTCPClient {

	Thread			mtMessagesAndState;
	BufferedInputStream 	mInput;
	OutputStream   	mOutput;
	Socket		   	mServerSocket;
	boolean			mThreadsOn = true;
	Queue<String>   mResponseMessageQueue = new LinkedList<String>();
	EventCallback	mECBDPArrivedHandler;
	EventCallback	mECBOOBArrivedHandler;
	GeneralStats	mgStats = new GeneralStats();
	
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
	 * @return
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
		if (theMessage.startsWith("DPDATA")) {
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
		
		if (msgparts == null || msgparts.length < 4) {
			msg ("Runt DP message: " + theMessage);
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

		if (msgparts == null || msgparts.length < 4) {
			msg ("Runt OOB message: " + theMessage);
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
			ret = tryToConnect();
			if (ret) {
				// successful connect! attach streams. 
				msg ("Successful connect! attaching streams.");
				ret = attachStreams();
				if (ret) {
					sendOOBEvent ("client.connected","");
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
		SocketAddress remoteAddr = new InetSocketAddress("localhost", 62009);
		
		try {
			mServerSocket.connect(remoteAddr);
		} catch (Exception e) {
			msg ("Error connecting to server. E=" + e.getMessage());
			return false;
		}

		return true;
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
	
}// end of class. 
