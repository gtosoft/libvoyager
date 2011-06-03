/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

/**
 * This class was designed to privode a means for low level classes to percolate messages and events up the stack in 
 * an event-driven manner. For example, if a low level I/O class wants to notify a higher level class that new data 
 * is available for processing, it may do so by using a method defined in this class. The higher level class defines 
 * an instance of this class and overrides the needed method, then registers the class with the lower level class
 * the lower level class checks to see if the class has been registered and runs the methdo if so. 
 * @author brad
 *
 */

public class EventCallback {
		

	/**
	 * To be used to pass packets from the session layer up to the decoder. 
	 * @param packet
	 */
	public void newPacket (String packet) {
		
	}
	
	/**
	 * A method to be executed by a lower level class in order to 
	 * @param message
	 */
	public void onNewMessageArrived (String message) {
	}

	/**
	 * A method to be executed when the connection state changes. 
	 * @param newState
	 */
	public void onStateChange (int oldState, int newState) {
	}

//	/**
//	 * Called by monitorSession when the monitor state changes. 
//	 * Monitor state can be 0,10,20,30, etc, depending on Bluetooth state, AT initialization, OBD initialization, and more. 
//	 * @param oldState
//	 * @param newState
//	 */
////	public void onMonitorStateChange (int oldState, int newState) {
////	}

	/**
	 * This method should be kicked off any time new data arrives. 
	 * The second arg may be optional depending on the situation. 
	 * @param dataPointName
	 * @param newData
	 */
	public void onNewDataArrived (String dataPointName, String header, String oldData, String newData, boolean isNew, boolean dataChanged) {
		
	}

	/**
	 * This method gets fired off by the PIDDecoder class every time a new datapoint is decoded. A Parent class can then use the data specified in either the string or int arg, depending on the type of data.
	 * @param DPN - DataPointName
	 * @param sDecodedData - String representation of the datapoint 
	 * @param iDecodedData - integer representation of the datapoint. 
	 */
	public void onDPArrived (String DPN, String sDecodedData, int iDecodedData) {
		
	}

	
	/**
	 * This will be used to pass certain information up the layers to the top. 
	 * The type of information being passed upward will be of an "out of band" nature. 
	 * For example, one message might contain results from a network device scan.   
	 * @param dataName - name of the value, "like" a datapoint, but out of band stuff doesn't have DPNs. 
	 * @param dataValue - string representation of the value, most commonly used
	 * @param iDataValue - in some instances it may be beneficial to pass the information as an integer, in which case this arg may be utilized. 
	 */
	public void onOOBDataArrived (String dataName, String dataValue) {
		
	}

	/**
	 * This method is to be used by the libVoyager ActivityHelper class which can be used to discover nearby ELM devices. 
	 * @param MAC
	 */
	public void onELMDeviceChosen (String MAC) {
	}
	
}
