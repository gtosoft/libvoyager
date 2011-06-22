package com.gtosoft.libvoyager.svip;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;

import android.util.Log;

import com.gtosoft.libvoyager.session.HybridSession;
import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.GeneralStats;

/**
 * SVIP - Simple Voyager(Vehicle) Interface Protocol. 
 * This class will serve to manage SVIP socket connections. It will handle commands and events both on the same stream. 
 * - Send Unsolicited Events generated internally by livboyager OOB events. 
 * - Process and respond to command requests from the connected peer.
 * 
 * @author Brad Hein / GTOSoft LLC
 *
 */

public class SVIPTCPServer {
	GeneralStats mgStats = new GeneralStats();
	final boolean DEBUG = true;
	// libvoyager started in 2009. Seems like a good port. 
	public static final int SVIP_SERVER_PORT = 62009;
	Thread mtAcceptThread;
	boolean mThreadsOn = true;
	
	BufferedInputStream mInStream;
	OutputStream mOutStream;
	
	List<SVIPStreamServer> mOpenSockets;

	HybridSession hs;
	
	ServerSocket mServerSocket;
	
	
	/**
	 * Default Constructor. 
	 */
	public SVIPTCPServer(HybridSession h) {
		hs = h;
		// Start a thread that waits for new connections. 
		startAcceptThread();
	}

	/**
	 * Carefully releases the server socket, thus giving up the server port. 
	 */
	private boolean releaseServerSocket () {
		// release the server port. 
		if (mServerSocket != null) {
			// release the server port. 
			try {
				mServerSocket.close();
			} catch (IOException e) { 
				// exception is OK. we're just double checking that the server port is given up. 
				}

			// special, weird, case. hopefully never happens. 
			if (mServerSocket.isClosed() != true) {
				msg ("Tried to release server socket but failed. What do we do now???");
				return false;
			}
		}
		
		return true;
	}
	
	private boolean bindToServerPort () {
		// bind/re-bind to the port. 
		try {
			msg ("Binding to server socket port " + SVIP_SERVER_PORT + " server socket pointer is " + mServerSocket);
			mServerSocket = new ServerSocket(SVIP_SERVER_PORT);
			msg ("Succecssfully bound to port " + SVIP_SERVER_PORT);
		} catch (Exception e) {
			msg ("ERROR instantiating new server socket on port " + SVIP_SERVER_PORT + " E=" + e.getMessage());
			return false;
		}
		
		return true;
	}
	
	/**
	 * If necessary, opens the server side socket port. If not necessary, does nothing. 
	 * @return
	 */
	private boolean instantiateServerSocketIfNecessary() {
		// remains true unless one of the sub functions fails. 
		boolean ret = true;
		
		if (DEBUG) 
			if (mServerSocket != null) 
				msg ("server socket is " + mServerSocket + " bound=" + mServerSocket.isBound() + " closed=" + mServerSocket.isClosed());
			else 
				msg ("server socket is null");

		// unbind if necessary and then bind/re-bind to the port. 
		if (mServerSocket == null) {
			mgStats.incrementStat("bind.attempts");
			// release and re-bind to the socket. 
			if (!releaseServerSocket()) ret = false;
			if (!bindToServerPort()) ret = false;
		} else {
			if (DEBUG) msg ("Server Socket variable is NOT null, so we didn't try to re-bind.");
		}
		return ret;
	}

	/**
	 * Block until incoming connection, instantiate the connection, block waiting for connection, repeat.  
	 * @return
	 */
	private boolean startAcceptThread() {
		if (mtAcceptThread != null) {
			msg ("Accept thread already running. Not creating a new one. ");
			return false;
		}
		
		mtAcceptThread = new Thread() {
			public void run () {
				while (mThreadsOn == true) {
					boolean ret;
					ret = instantiateServerSocketIfNecessary();
					if (!ret) {
						mgStats.incrementStat("bind.fails");
						EasyTime.safeSleep(10000);
					}
					
					Socket sClient;
					// Try and accept() a new connection. we block here if everything is OK in the network world. 
					try {
						sClient = mServerSocket.accept();
						if (sClient.isConnected() == true) {
							mgStats.incrementStat("accept.count");
							// nodelay - to reduce latency. 
							sClient.setTcpNoDelay(true);
							// set a couple options to help keep the connection alive, even if it's not under heavy use.
							sClient.setSoLinger(false, 300);
							sClient.setKeepAlive(true);
							
							// pass the client onto the stream handler which manages the connection via streams.
							addOpenSocket(sClient);
							// TODO: Obtain peer's IP and put it in GenStats. 
						} else {
							msg ("Received a connect but the connection was not connected... wtf");
						}
					} catch (Exception e) {
						// Not a big deal - the socket.accept method failed, which probably means the server socket isn't properly bound. we'll try again in a second when we loop.  
					}

					// if we loop, keep it slow. We could be looping trying to get a valid server socket. 
					EasyTime.safeSleep(1000);
				}// end of core while() loop that accepts connections. 
				if (DEBUG) msg ("Accept thread TERMINATED. Threadson=" + mThreadsOn);
			}// end of run()
		};// end of thread definition. 
		
		// start the thread immediately. 
		mtAcceptThread.start();
		
		return true;
	}
	
	/**
	 * Instantiates and adds a new SVIPStreamServer to the list of open streams.  
	 * @param s
	 */
	private void addOpenSocket (Socket sClient) {
		BufferedInputStream b;
		OutputStream outStream;

		if (DEBUG) msg ("Adding socket!");
		
		try {
			b = new BufferedInputStream(sClient.getInputStream());
			outStream = sClient.getOutputStream();
		} catch (IOException e) {
			msg ("Error attaching streams to new socket. E=" + e.getMessage());
			return;
		}
		
		// Instantiate the new SVIP Stream server. 
		SVIPStreamServer s = new SVIPStreamServer(hs, b, outStream);

		// Add this new stream server to the list to make it official. 
		mOpenSockets.add(s);
		
		if (DEBUG) msg ("Added new stream to open socekts list!");
	}
	
	/**
	 * For each alive TCP Socket, call its sendOOB maddOpenSockethod to pass it this data.
	 * @param dataName - oob data name
	 * @param dataValue - data value for that oob. 
	 */
	public void sendOOB (String dataName, String dataValue) {
		Iterator<SVIPStreamServer> i = mOpenSockets.iterator();
		SVIPStreamServer s;
		boolean ret;
		while (i.hasNext()) {
			s = i.next();
			ret = s.sendOOB(dataName, dataValue);
			// if it failed, then remove that socket from the list. 
			if (!ret) {
				removeDeadSocket (s);
				break;
			}// end of "if the send failed, then close the socket". 
		} // end of while loop that iterates through all open sockets. 
	}// end of sendOOB. 

	/**
	 * For each alive TCP Socket, call its DPArrived method to pass it this data.
	 * @param DPN - data point name. 
	 * @param sDecodedData - decoded data. 
	 */
	public void sendDPArrived (String DPN, String sDecodedData) {
 		Iterator<SVIPStreamServer> i = mOpenSockets.iterator();
		SVIPStreamServer s;
		boolean ret;
		while (i.hasNext()) {
			s = i.next();
			ret = s.sendDPArrived(DPN, sDecodedData);
			// if it failed, then remove that socket from the list. 
			if (!ret) {
				removeDeadSocket (s);
				break;
			}// end of "if the send failed, then close the socket". 
		} // end of while loop that iterates through all open sockets. 
	}


	
	/**
	 * Removes the given SVIP stream server from the list. 
	 * @param s - SVIP Stream server instance. 
	 */
	private void removeDeadSocket (SVIPStreamServer s) {
		try {
			s.shutdown();
			mOpenSockets.remove(s);
			if (DEBUG) msg ("Successfully removed dead SVIP Streamserver instance.");
		} catch (Exception e) {
			msg ("Error removing SVIP Streamserver instance. E=" + e.getMessage());
		}
		
	}
	
	/**
	 * Close all streams and remove them from the list. 
	 * @return
	 */
	private boolean closeAllStreams () {
		if (mOpenSockets == null || mOpenSockets.size() < 1) return true;
		
		Iterator <SVIPStreamServer> i = mOpenSockets.iterator();
		SVIPStreamServer s;
		while (i.hasNext()) {
			s = i.next();
			if (s != null) s.shutdown();
			// try to remove it. since we hust shut it down! but remove it via the Iterator to prevent concurrent modification exception with the Iterator. 
			i.remove();
		}
		
		return true;
	}
	
	public void shutdown () {
		// Shut down all open TCP Sockets too. 
		mThreadsOn = false;
		if (mtAcceptThread != null) mtAcceptThread.interrupt();

		mgStats.setStat("shutdown", "true");
		
		closeAllStreams();
		
		// Try to close the server socket. 
		try { mServerSocket.close();} catch (Exception e) { }
	}

	private void getAllPeerStats() {
		if (mOpenSockets == null || mOpenSockets.size() < 1) 
			return;
		
		Iterator<SVIPStreamServer> i = mOpenSockets.iterator();
		SVIPStreamServer s;
		int streamnum = 0;
		while (i.hasNext()) {
			streamnum++;
			s = i.next();
			mgStats.merge("svipStream[" + streamnum + "]", s.getStats());
		}
	}
	
	/**
	 * get stats. 
	 * @return
	 */
	public GeneralStats getStats () {
		getAllPeerStats();
		return mgStats;
	}
	
	
	private void msg (String m) {
		Log.d("SVIPTCPServer","[T=" + getThreadID() + "] " + m);
	}

    private String getThreadID () {
        final String m = "[T" + Thread.currentThread().getId() + "]";
        return m;
}

}
