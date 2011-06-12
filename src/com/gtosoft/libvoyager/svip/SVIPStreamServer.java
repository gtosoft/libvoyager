package com.gtosoft.libvoyager.svip;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.util.Log;

import com.gtosoft.libvoyager.session.HybridSession;
import com.gtosoft.libvoyager.util.EasyTime;

/**
 * The life of this class corresponds to a single open SVIP connection. When the
 * connection dies, so should this class When a new SVIP connection is made,
 * instantiate this class with the associated streams.
 * 
 * @author Brad Hein / GTOSoft LLC.
 * 
 */

public class SVIPStreamServer {
	// this String will contain partial message as we read them from the input buffer. 
	String mCurrentMessage = "";

	final boolean DEBUG = true;
	boolean mThreadsOn = true;
	Thread mtCommandHandler;

	BufferedInputStream mInStream;
	OutputStream mOutStream;
	HybridSession hs;

	/**
	 * Default Constructor. To instantiate this class you must have an open
	 * hybridSession and stream (TCP/IP, etc).
	 * 
	 * @param hs - a valid hybridsession.
	 * @param bufferedInputStream - buffered inputstream that we will read from
	 * @param outStream - an output stream, which we will send responses to.
	 */
	public SVIPStreamServer(HybridSession hsession,
			BufferedInputStream bufferedInputStream, OutputStream outStream) {
		hs = hsession;
		mInStream = bufferedInputStream;
		mOutStream = outStream;

		// Kick off handler thread.
		startCommandHandler();

		msg("SVIPStream Server initialized.");
	}

	private boolean startCommandHandler() {

		if (mtCommandHandler != null) {
			msg("Not re-creating already-running command handler thread.");
			return false;
		}

		// define the thread.
		mtCommandHandler = new Thread() {
			public void run() {
				String newMessage = "";
				while (mThreadsOn == true) {
					// check for commands, process them.
					newMessage = getNextMessage();

					if (newMessage != null && newMessage.length() > 0) {
						// Process The Command!
						String[] responses = processRawCommand(newMessage);
						sendSVIPMessage(SVIPConstants.RESPONSE_ACK, responses);
					}

					// pause between loops. if interrupted, break out of the
					// loop.
					if (!EasyTime.safeSleep(500))
						break;
				}// end of while.
				msg("SVIP Stream / Command handler thread has died. Threadson="
						+ mThreadsOn);
			}// end of run();
		};// end of thread definition.

		mtCommandHandler.start();

		return true;
	}

	/**
	 * This method gets next message or however much of it is available. 
	 * It stores partial messages in mCurrentMessage global variable until the message is complete. 
	 * @return - a whole message if available, otherwise NULL. 
	 */
	private synchronized String getNextMessage() {
		// read from the input stream. If it's not a whole message, then append
		// what we have.
		// if the message is whole, then return it and clear our internal
		// buffer.
		
		// Read zero or more bytes from the input stream, up to the end-of-message character. 
		String newData = readUpToCharacter('>');
		// append this data. 
		mCurrentMessage = mCurrentMessage + newData;
		
		// Do we have a complete message?
		if (mCurrentMessage.endsWith(">") && !mCurrentMessage.endsWith("\\>")) {
			// Message is complete - return it!  
			String thisMessage = "" + mCurrentMessage;
			
			// We have consumed the contents of this string so remove its contents so the next call to this function starts clean.
			mCurrentMessage = "";
			
			// unescape any special characters. 
			thisMessage = unEscapeStuff(thisMessage);
			return thisMessage;
		}

		// No messages are ready at this time. 
		return null;
	}

	/**
	 * read from the input buffer up to the given character. 
	 * In most cases the character will be the end-of-message character (">"). 
	 * @param stopCharacter
	 * @return - null, or however much of the message is available, possibly the whole thing.  
	 */
	private String readUpToCharacter (int stopCharacter) {
		int avail = -1;
		
		try {
			avail = mInStream.available();
		} catch (IOException e) {
			// Error reading from the socket? close it down. 
			msg ("Error reading input stream size. Closing it down. E=" + e.getMessage());
			shutdown();
		}

		// bytes are available, get them. 
		if (avail > 0) {
			String messageData = "";
			// word is... there are some bytes to be read... get them.  
			int thisChar=-1;
			try {
				// core loop. appends bytes one at a time, stops at (and appends) stopCharacter if encounterd. 
				while (mInStream.available() > 0 && thisChar != stopCharacter) {
					thisChar = mInStream.read();
					if (thisChar > 0)
						messageData = messageData + (char) thisChar;
					else 
						if (DEBUG) msg ("Threw out NULL from input stream.");
				}// end of while() that grabs input bytes one at a time until it reaches the end or the stop character. 
			} catch (IOException e) {
				msg ("Exception while reading from input stream: E=" + e.getMessage());
				shutdown ();
				return null;
			}

			return messageData;
		}// end of "if bytes are available in the input buffer" 
		

		// no bytes were available. 
		return null;
	}
	

	/**
	 * Processes the given command and returns the response that we should send
	 * to the peer.
	 * 
	 * @param rawCommand
	 * @return
	 */
	private String[] processRawCommand(String rawCommand) {
		// initialize response with a single message - "NACK", which gets used only if we don't recognize the request.  
		String[] responses = {SVIPConstants.RESPONSE_NACK};

		// Ping/pong?
		if (rawCommand.equals(SVIPConstants.REQUEST_PING)) {
			responses = new String [] {SVIPConstants.RESPONSE_ACK, SVIPConstants.REQUEST_PONG};
		}
		
		
		return responses;
	}

	/**
	 * Creates an outgoing SVIP OOB message and sends it out.
	 * 
	 * @param dataName - name, from SVIPConstants.
	 * @param dataValue -
	 * @return - false if anything goes wrong. A return value of false may cause the parent to deinstantiate us.
	 */
	public boolean sendOOB(String dataName, String dataValue) {
		return sendSVIPMessage(SVIPConstants.OOB_ARRIVED, dataName, dataValue, null, null);
	}

	public boolean sendDPArrived(String DPN, String sDecodedData) {
		return sendSVIPMessage(SVIPConstants.DPN_ARRIVED, DPN, sDecodedData, null, null);
	}

	private String escapeStuff(String stringWithStuff) {
		
		if (stringWithStuff.contains("|") || stringWithStuff.contains(">")) {
			String newString = stringWithStuff.replace("|", "\\|");
			newString = newString.replace(">", "\\>");
			return newString;
		}

		return stringWithStuff;
	}

	private String unEscapeStuff(String stringWithEscapedStuff) {

		// if there are items that need to be fixed, then fix them. 
		if (stringWithEscapedStuff.contains("\\|") || stringWithEscapedStuff.contains("\\>")) {
			String unescapedString = stringWithEscapedStuff.replace("\\|", "|");
			unescapedString = unescapedString.replace("\\>", ">");
			return unescapedString;
		}
		
		// nothing needed to be changed, so just return the same string. 
		return stringWithEscapedStuff;
	}

	/**
	 * Convenience method that allows you to pass args as an array to send an
	 * SVIP Message.
	 * 
	 * @param cmd
	 * @param args
	 * @return
	 */
	private boolean sendSVIPMessage(String cmd, String[] args) {
		String arg1 = null;
		String arg2 = null;
		String arg3 = null;
		String arg4 = null;

		// Grab each arg one at a time.
		if (args != null && args.length > 0)
			arg1 = args[0];
		if (args != null && args.length > 1)
			arg2 = args[1];
		if (args != null && args.length > 2)
			arg3 = args[2];
		if (args != null && args.length > 3)
			arg4 = args[3];

		return sendSVIPMessage(cmd, arg1, arg2, arg3, arg4);
	}

	/**
	 * Package up the given data and send it out the port. This method is
	 * synchronized to avoid subatomic interleaving of messages.
	 * 
	 * @param cmd - the command name such as "PONG" or "DPARRIVED", etc.
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @return - returns true if the message was successfully assembled and
	 *         transmitted. False otherwise.
	 */
	private synchronized boolean sendSVIPMessage(String cmd, String arg1,
			String arg2, String arg3, String arg4) {
		String packet = "";

		// Packet starts with the command.
		packet = escapeStuff(cmd) + "|";
		// append all args one after another. Remember to escape any pipe
		// characters within the args as they arrive using escapeStuff method.
		if (arg1 != null && arg1.length() > 0)
			packet = packet + escapeStuff(arg1) + "|";
		if (arg2 != null && arg2.length() > 0)
			packet = packet + escapeStuff(arg2) + "|";
		if (arg3 != null && arg3.length() > 0)
			packet = packet + escapeStuff(arg3) + "|";
		if (arg4 != null && arg4.length() > 0)
			packet = packet + escapeStuff(arg4) + "|";

		// Append the message terminator to complete the packet.
		packet = packet + ">";

		// TODO: send the "packet" variable out the interface.
		msg("TODO: Send packet: " + packet);

		byte[] buffer = packet.getBytes();

		// try to write the bytes to the output stream. In the event of a
		// problem, return alse.
		try {
			mOutStream.write(buffer, 0, buffer.length);
		} catch (IOException e) {
			msg("SEND FAILED. E=" + e.getMessage());
			return false;
		}

		if (DEBUG)
			msg("sent " + packet + " (" + buffer.length + " bytes)");

		return true;
	}

	/**
	 * Called by our parent if they want to tear down this SVIP connection
	 * stream.
	 */
	public void shutdown() {

		mThreadsOn = false;
		if (mtCommandHandler != null)
			mtCommandHandler.interrupt();

		// Gracefully close the socket.
		try {
			mInStream.close();
			mOutStream.close();
		} catch (Exception e) {
		}

	}

	private void msg(String m) {
		Log.d("SVIPStreamServer", m);
	}
}
