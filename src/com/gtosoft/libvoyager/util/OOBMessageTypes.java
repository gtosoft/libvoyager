package com.gtosoft.libvoyager.util;


public final class OOBMessageTypes {
	// a long string with crlf's that describes the session detection results. 
	public static final String AUTODETECT_SUMMARY = "AutodetectSummary";
	// Corresponds to the new state of bluetooth, as returned by ebt.getcurrentstate()
	public static final String IO_STATE_CHANGE = "IOStateChange";
	// Corresponds to the new state of session layer (could be one or more sessions), as returned by obd2session.getcurrentstate()
	public static final String SESSION_STATE_CHANGE = "SessionStateChange";
	// corresponds to us changing from one session to another.
	public static final String SESSION_SWITCHED = "SessionSwitched";
	// ready state will be "0" or "1" depending on whether readiness is now false or true. 
	public static final String READY_STATE_CHANGE = "ReadyStateChange";
	// Bluetooth discovery started or stopped.
	public static final String DISCOVERING_STATE_CHANGE = "DiscoveringStateChange";
	
	// Bluetooth failed connect occurred. Data should contain number of tries remaining. 
	public static final String BLUETOOTH_FAILED_CONNECT = "BluetoothFailedConnect";
	
}
