/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;

import android.util.Log;

public class UnitConverter {

	final boolean DEBUG=false;
	

	boolean CONVERT_KPH_TO_MPH = false;
	boolean CONVERT_C_TO_F = false;
	
	
	String dataPointName;
	String dataShortName;
	String request;
	String formula;
	String description;
	double minValue;
	double maxValue;

	public void activateConversionCTOF (boolean newState) {
		CONVERT_C_TO_F = newState;
	}
	
	public void activateKPHToMPH(boolean newState) {
		CONVERT_KPH_TO_MPH = newState;
	}

	public String getDPN() {
		return dataPointName;
	}

	public String getShortName() {
		return dataShortName;
	}

	public String getformula() {
		return formula;
	}

	public String getDescription() {
		return description;
	}

	
	public double getMinValue() {
		return minValue;
	}

	public double getMaxValue() {
		return maxValue;
	}

	public void setData(String RAWdataPointName, String RAWdataShortName,String RAWrequest, String RAWformula, String RAWdescription, double RAWminValue, double RAWmaxValue) {

if (DEBUG==true) msg ("Got setData with OBDRequest = " + RAWrequest);


		// Test case, SPEED...
		if (CONVERT_KPH_TO_MPH==true && RAWdataPointName.equals("SPEED")) {
			// Append some steps to get it into MPH.
			RAWformula = RAWformula + ",X*10,X/16";
			RAWdataShortName = "MPH";
			RAWdescription.replace("kph", "mph");
			// change min/max.
			RAWminValue = RAWminValue / 1.6d;
			RAWmaxValue = RAWmaxValue / 1.6d;
			msg ("Applied KPH->MPH conversion for DPN=" + RAWdataPointName);
		}

		// Celcius to Faranheit... 
		if (CONVERT_C_TO_F==true && RAWdataShortName.endsWith(" C")) {
			// modify the formula... 
			RAWformula = RAWformula + ",X*12,X/10,X+32";
			// change the suffix
			RAWdataShortName = RAWdataShortName.replace(" C"," F");
			// change min/max. 
			RAWminValue = RAWminValue * 1.2d + 32;
			RAWmaxValue = RAWmaxValue * 1.2d + 32;
			msg ("Applied C->F conversion for DPN=" + RAWdataPointName);
		}
		
		
		// Change all the member variables. 
		dataPointName = RAWdataPointName;
		dataShortName = RAWdataShortName;
		request = RAWrequest;
		formula = RAWformula;
		description = RAWdescription;
		minValue = RAWminValue;
		maxValue = RAWmaxValue;
	}


	private static void msg (String message) {
		Log.d("UC.m",message);
	}

	public void setUSUnits(boolean trueIfConvertFromMetricToUS) {
		activateConversionCTOF(trueIfConvertFromMetricToUS);
		activateKPHToMPH(trueIfConvertFromMetricToUS);
	}
	
}
