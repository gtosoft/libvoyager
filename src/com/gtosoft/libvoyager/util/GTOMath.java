/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.util;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import android.util.Log;

/**
 * 
 * A Class which will compute OBD math stuff. 
 *
 */

public class GTOMath {
	private static boolean DEBUG = false;
	
	private static final String FORMULA_STEP_SEPARATOR=",";
	private static final int FORMULA_DEFAULT_PRECISION=4;
	public static HashMap<String,String> mhmResponses;
	
	/*
	 * Dump the provided hash map into something readable. 
	 */
	public static String dumpHashMap (HashMap<String, String> hm) {
		String response = "";
		
		Set<String>s = hm.keySet();
		Iterator <String>i = s.iterator();
		
		String K="";
		String V="";
		while (i.hasNext()) {
			K=(String)i.next();
			V=hm.get(K);
			response += " " + K + "/" + V;
		}
		
		response=response.trim();
		
		return response;
	}
	
	
	/*
	 * Given an ELM "response", return just the response data portion.
	 * Sample response: ATI|ELM327 v1.2a||>
	 */
	public static String getAsATResponse (String response) {
		String retval="";

		// Sample: ATRV|14.0V||>
		//         0000 11111 23
		String responseParts[] = response.split("\\|");
		
//		Log.d("GTOMATH","AT response " + response + " has " + responseParts.length + " parts.");
		
		
		if (responseParts.length < 4) {
			Log.w("GTOMath.getAsATResponse()","Throwing out AT response with too few fields: " + response);
			return response;
		}

		// split it up. 
		retval = responseParts[1];

//		Log.d("GTOMath.getAsATResponse()","1response=" + response + " retval=" + retval);
		
		//Volts?
//		Log.d("GTOMATH","Checking IF this is volts: " + retval);
		if (retval.endsWith("V") && responseParts[0].contains("ATRV")) 
			retval = retval.replace("V", "");

//		Log.d("GTOMath.getAsATResponse()","2response=" + response + " retval=" + retval);

		
		return retval;
	}

	/*
	 * Sample response: 0902|014 |0: 49 02 01 32 47 31 |1: 57 44 35 38 43 32 36 |2: 39 32 35 39 36 33 38 ||>
	 */
	public static String getAsVIN (String hexBytes) {
		String retString = "";
		String hexbytes[] = {};
		
		// break it out into individual hex bytes. 
		hexbytes = hexBytes.split(" ");
		
		Log.d("GTOMath","getAsVIN(): Converting " + hexBytes + " into a VIN...");
		
		// loop through each hex byte and append each one to the return string.
		char thisHex='\0';
		for (int i=0;i<hexbytes.length;i++) {
			hexbytes[i] = hexbytes[i].trim();
			if (hexbytes[i].length() != 2) 
				continue;
			try {thisHex = (char) Integer.parseInt(hexbytes[i], 16);
			} catch (NumberFormatException e) {
				Log.e("GTOMath.getAsVIN()","Error while processing hexbyes at index " + i + " value=" + hexbytes[i],e);
				return "";
			}
			//Log.d("GTOMath.getAsVIN()","Appending character " + (int)thisHex + " chr=" + thisHex);
			retString += thisHex;
		} // end for. 

		return retString;
	}
	
	public static String getAsDTCs (int ELMProtocolNumber, String request, String response) {
		String dtcList = "";
		
		OBDPacketParser opp = new OBDPacketParser(ELMProtocolNumber);
		
		HashMap<String,String> hmResponses = opp.getData(request, response);
		
		// Using a tree-set here so as to add a level of predictability (for junit tests, etc) in the order of responses based on ECU.
		Set<String> s = new TreeSet<String> (hmResponses.keySet());
		Iterator<String> i = s.iterator();
		
		String thisKey = "";
		String thisVal = "";
		String dtcsThisNode="";
		while (i.hasNext()) {
			thisKey = i.next();
			thisVal = hmResponses.get(thisKey);
			dtcsThisNode = getAsMultipleDTCs(thisVal);
			// if this node has DTCs, then append those DTCs to the main list. 
			if (dtcsThisNode.length() > 0) {

				// if the list already contains items, append a command before the next set of dtcs. 
				if (dtcList.length()>0)
					dtcList = dtcList + ",";

				// append dtcs for this node. 
				dtcList = dtcList + dtcsThisNode;
			}
		}

		// trim leading/trailing spaces. 
		dtcList = dtcList.trim();
		
		return dtcList;
	}
	
//	private static String getAsMultipleDTCs (String multipleDTCs) {
//		String dtclist = "";
//		
//		multipleDTCs.replace(" ", "");
//		
//		String thisDTC = "";
//		for (int i=0;i<multipleDTCs.length();i+=4) {
//
//			try {
//				thisDTC = multipleDTCs.substring(i,i+5);
//			} catch (Exception e) {
//				Log.d ("GTOMath","WARNING: Oddly shaped compound DTC response: " + multipleDTCs);
//				return dtclist;
//			}
//			
//			// append this single DTC. 
//			dtclist = dtclist + "," + getAsSingleDTC(thisDTC);
//		}
//			 
//		
//		
//		return dtclist;
//	}

	/**
	 * DTCs are binary coded decimal - each 4 bits represents a character. With a prefix for the category of the DTC. 
	 * @param response - hex bytes of the response 
	 * @return - human readable DTCs, comma separated if more than one present.
	 * "unknown" if hexbytes is blank
	 * "NONE" if hexbytes is all zeros, indicating no codes are present
	 * 
	 */
	// 03|48 6B 0E 43 04 20 00 00 00 00 28 ||>
	// 03|48 6B 10 43 00 00 00 00 00 00 06 ||>    <---- Honda accord, 1997?
	// 03|48 6B 10 43 03 06 03 56 00 00 DC ||>    <---- 2001 Dodge Dakota. 
	public static String getAsMultipleDTCs (String hexBytes) {

		String dtcList = "";
		
		// remove all spaces.
		hexBytes = hexBytes.replace (" ","");

		if (hexBytes.length() == 0) {
			//Log.w("GTOMath.getasDTCs()","Warning: Odd number of DTC Bytes! " + hexBytes);
			return "";
		}
		
		if (hexBytes.replace("0", "").equals("")) {
			return "";
		}
		
		if (hexBytes.length() % 4 != 0) {
			Log.w("GTOMath.getasDTCs()","Warning: Odd number of DTC Bytes! " + hexBytes);
		}

		
		for (int i=0;i<hexBytes.length() / 4;i++) {
			String thisCode = getAsSingleDTC(hexBytes.substring(i*4, i*4 + 4));
			if (thisCode.length() > 0)
				dtcList = dtcList + thisCode + ","; 
			//Log.d("GTOMath.DecodeAsDTC()","After iteration " + i + " dtcList=" + dtcList);
		}

		if (dtcList.endsWith(","))
			dtcList = dtcList.substring(0, dtcList.length()-1);

		if (dtcList.length() > 0 && dtcList.length() < 5) 
			Log.w("GTOMath.getAsDTCs()","Warning: Returning what looks to be a malformed DTC: ->" + dtcList + "<-");

		
		return dtcList;
	}
	
	/**
	 * Given a single DTC (2 bytes), return the DTC held within those two bytes.  
	 * @param hexBytes - two hex bytes which we'll turn into a DTC.  
	 * @return - a single DTC code, such as "P2048".
	 */
	public static String getAsSingleDTC (String hexBytes) {
		
		String dtc = "";
		
		hexBytes = hexBytes.replace(" ","");
		
		if (hexBytes.length() != 4) {
			Log.e("GTOMath.getAsSingleDTC()","Error: the given DTC is not 4 nibbles long: " + hexBytes);
			return "ERR8 " + hexBytes;// + hexBytes;
		}

		// if its not a code, don't return it. 
		if (hexBytes.equals("0000")) {
			return "";
		}

		// Grab the first nibble, which indicates to the code category - Powertrain, Chassis, Body, Network 
		String s_a7654 = hexBytes.substring(0,1);
		int a76 = 0; 
		try {
			a76 = Integer.valueOf(s_a7654,16) >> 2; // get it and shift bits 7 and 6 down into our integer.
		} catch (Exception e) {
			Log.e("GTOMath.getASingleDTC()","Error! processing=" + s_a7654 + " E=" + e.getMessage());
			return "ERR9 " + s_a7654;
		}
//		Log.d("GTOMath.getAsSingleDTC()","Got a76=" + a76);
		int a54 = 0;
		try {
			// get the bottom two bits of this nibble with a bitwise and.
			a54 = Integer.valueOf(s_a7654,16) & 3;
		} catch (NumberFormatException e) {
			Log.e("GTOMath.getASingleDTC()","Error! processing=" + s_a7654 + " E=" + e.getMessage());
			return "ERRJ " + s_a7654;
		}  
//		Log.d("GTOMath.getAsSingleDTC()","Got a54=" + a54);

		if (a76 > 3) {
			Log.w("GTOMath.getAsSingleDTC()","Warning: Unknown DTC prefix type of " + a76 + ". ");
			dtc = dtc + "?";
		} else {
			// determine the prefix - 0,1,2, or 3 depending on what we havea for a76. 
			String prefixes[] = {"P","C","B","U"};
			dtc = dtc + String.valueOf(prefixes [a76]);
		}

//		Log.d("GTOMath.getAsSingleDTC()","Before first numeric: dtc=" + dtc);
		
		// append the lower two bits of the first nibble as the first numeric part of the DTC. 
		dtc = dtc + String.valueOf(a54);	

//		Log.d("GTOMath.getAsSingleDTC()","Before last 3 numerics: dtc=" + dtc);

		// decode the remaining 3 characters as BCD. Since we're already in hex bytes format, we're sort of in BCD already.  
		dtc = dtc + hexBytes.substring (1,4);

		if (DEBUG) Log.d("GTOMath.getAsSingleDTC()","AFTER last 3 numerics: dtc=" + dtc + " input=" + hexBytes);
		
		return dtc;		
	}
	
	
	/*
	 * Given a response string, return a list of integers corresponding to PIDs supported. 
	 */
	public static String getAsSupportedPIDs (String response) {

		// then its invalid and we can't help you. NEXT. 
		if (response.length() <2)
			return "";
		
		// to store each individual hex byte later on. 
		int thisByte = 0;
		
		// split the hex bytes into different array elements. 
		String responses[] = response.split (" ");

		// to store the string of bytes each converted to binary. 
		String retBin = "";

		// loop through each hex byte (4) and for each one, convert it to decimal then 
		// to binary string and then return the string of conctenated binary strings. 
		for (int i=0;i<responses.length;i++) {
			responses[i] = responses[i].trim();
			
			// skip it if its not a valid length. 
			if (responses[i].length() <1)
				continue;
			
			// grab the hex byte and make it an integer. 
			try {thisByte = Integer.parseInt(responses[i], 16);
			} catch (NumberFormatException e) {
				Log.e("GTOMath.getassupportedPIDS()","Error processing integer " + responses[i],e);
				return "";
			}
			
			// convert the integer to binary string. 
			String thisbin = Integer.toBinaryString(thisByte);

			// pad with leading-0's if necessary. 
			while (thisbin.length() < 8)
				thisbin = "0" + thisbin;

			retBin = retBin + thisbin + " ";
		}

		return retBin;
	}

	/*
	 * Pass this method a request such as "0902" and  
	 * the hex bytes from the response packet
	 * and we'll analyze the request to determine how to decode the data bytes
	 * and then we'll return a string representation of your data!
	 * NEW: pass it a formula string too. We'll fall back on the formula for decoding it. 
	 */
	public static String decodeAutoDetect (int ELMProtocolNumber, String request,String formula, String response, String hexBytes) {
		
		if (formula == null || formula.length()<1) {
			return "";
		}
		
		// Special processing. 
		if (formula.equals("VIN"))
			return getAsVIN(hexBytes);
		
		if (formula.equals("DTC"))
			return getAsDTCs(ELMProtocolNumber, request, response);

		if (formula.equals("BINARY"))
			return getAsSupportedPIDs(hexBytes);
		// end of special processing.
		
		// AT Commands: no decoding to do! instead of returning blank though, return something useful. 
		if (request.startsWith("AT")) {
			return getAsATResponse(hexBytes);
		}
		
		// Split the hex bytes into array elements. 
		String variables[] = hexBytes.split(" ");
		
		// bail out if nothing was splitted.
		if (variables.length < 1) {
			Log.d("GTOMath.decodeAutoDetect()","Warning: no bytes found in: " + hexBytes + " formula=" + formula);
			return "";
		}
		
		// for each hex byte, switch it from a hex byte into a decimal string
		for (int i=0;i<variables.length;i++) {
			if (variables[i].length()<1)
				continue;
			try {variables[i] = "" + Integer.parseInt(variables[i], 16);
			} catch (NumberFormatException e) {
				Log.e("GTOMath.decodeAutoDetect()","Error processing expected integer. request=" + request + " formula=" + formula + " hexbytes=" + hexBytes,e);
				return "";
			}
		}
		
		return mathFormula(variables, formula);
		
		//return formulaResult;
		
	}

	/*
	 * given a set of steps in our special format,
	 * AND an array containing the values for A,B,C,D, etc.
	 * parse those steps and return the result as a string. 
	 */
	private static String mathFormula (String[] variables, String formulaSteps) {
		// start with a brand new bigdecimal. 
		BigDecimal X = new BigDecimal ("0");
		
		for (int i=0;i<variables.length;i++) {
			if (variables[i].length()==0) {
if (DEBUG==true)Log.w("GTOMath.mathFormula()","Warning: Throwing out formula because variable(s) blank! variable index=" + i + " formula=" + formulaSteps);
				return "";
			}
		}// end of for-loop that sanity-checks the variables. 

		// split the steps into array elements based on the globally defined separator. 
		String[] step = formulaSteps.split(FORMULA_STEP_SEPARATOR);
		
		// process each step of the operation. 
if (DEBUG==true) Log.d("GTOMath","Before loop. " + "X=" + X + " A=" + variables[0]);
		for (int i=0;i<step.length;i++) {
			X = mathOperation(X,variables,step[i]);
			if (X.compareTo(new BigDecimal(0)) < 0)
				Log.e("GTOMath.mathFormula()","Error: Math operation less than zero and we can't handle negatives! formulasteps=" + formulaSteps);					
			
if (DEBUG==true) Log.d("GTOMath","Math Step " + i + "X=" + X);

		}
		
		return X.toPlainString();
	}
	
	/**
	 *  Perform a single math operation (add, subtract, multiple, or divide). 
	 *  we 1. take the formula and replace variables, X is a special variable
	 *  then 2. perform the operation specified in formula, 
	 *  then 3. return the result as a BigDecimal. 
	 *  * we chose BigDecimal because float couldn't offer exact decimal results. 
	 *  
	 *  Sample Valid formulas: "X+5","X-5","X*5","X/5","A+A","X+0","a+b"
	 *  args[] contains the values of A,B,C, etc. specify zero or more. up to 26.
	 *  
	 *  Known limitation: negatives... we don't know how to handle them. 
	 */
	private static BigDecimal mathOperation (BigDecimal X, String[] variables, String formula) {
		BigDecimal result = new BigDecimal("0");

		// drop any and all spaces!
		formula = formula.replace (" ","");

		// make variables uppercase. 
		formula = formula.toUpperCase();
		
		// Perform replacements. X is a special variable. 
		if (formula.contains("X"))
			formula = formula.replace("X",new String (X.toPlainString()));

		String old="";
		for (char i=0;i<variables.length;i++) {
			// finaggle a string together that consists of the letter corresponding to our loop count. A,B,C, etc. 
			old = "" + (char)(i+'A'); 
			// Replace all instances of that letter with its value. 
			formula = formula.replace(old, variables[i]);
			if (variables[i].length()==0) 
				Log.d("GTOMath.MathOperation()","Warning: Blank variable " + old + " in formula " + formula);
		}
		
		// at this point we shouldn't have any variables left in the formula. 
		// if we do, then its a problem with the calling method not giving us enough variables. 
		// in that case, we're just going to return to avoid number format exceptions.
		if (	formula.contains("X") || 
				formula.contains("A") ||
				formula.contains("B") || 
				formula.contains("C") || 
				formula.contains("D") )
			return result;
		
		
		String args[];
		
		if (formula.contains("+")) {
			//Log.d("GTOMath.mathoperation()","Math Operation: Addition");
			// Split the formula into the left side and right side of plus sign: 
			args=formula.split("\\+");
			if (args.length != 2 || args[0].length()<1 || args[1].length()<1) {
				Log.e("GTOMath.mathOperation()","ERROR: number of operations not equal to one OR left/right side invalid. " + formula);
				return result;
			}
			
			// set result to the left side, then run .add against result and the right side. 
			try {result = new BigDecimal(args[0]);
			} catch (Exception e1) {
				return result;
			}
			
			try {result = result.add(new BigDecimal (args[1]));} catch (Exception e) {
				Log.e("GTOMath.mathOperation()","Error processing addition. Formula=" + formula + " args0=" + args[0] + " args1=" + args[1],e);
			}
			return result;
		}
		
		if (formula.contains("-")) {
			//Log.d("GTOMath.mathoperation()","Math Operation: Subtraction");
			// Split the formula into the left side and right side of plus sign: 
			args=formula.split("\\-");
			if (args.length != 2 || args[0].length()<1 || args[1].length()<1) {
				Log.e("GTOMath.mathOperation()","ERROR: number of operations not equal to one! " + formula);
				return result;
			}
			
			// set result to the left side, then run .add against result and the right side. 
			try {result = new BigDecimal(args[0]);} catch (Exception e1) {
				return result;
			}

			try {result = result.subtract(new BigDecimal (args[1]));} catch (Exception e) {
				Log.e("GTOMath.mathOperation()","Error processing subtraction. Formula=" + formula + " args0=" + args[0] + " args1=" + args[1],e);
			}
			return result;
		}
		
		if (formula.contains ("*")) {
			//Log.d("GTOMath.mathoperation()","Math Operation: Multiplication");

			// Split the formula into the left side and right side of plus sign: 
			args=formula.split("\\*");
			if (args.length != 2 || args[0].length()<1 || args[1].length()<1) {
				Log.e("GTOMath.mathOperation()","ERROR: number of operations not equal to one! " + formula);
				return result;
			}
			
			// set result to the left side, then run .add against result and the right side. 
			result = new BigDecimal(args[0]);
			try {result = result.multiply(new BigDecimal (args[1]));} catch (Exception e) {
				Log.e("GTOMath.mathOperation()","Error processing multiplication. Formula=" + formula + " args0=" + args[0] + " args1=" + args[1],e);
			}
			return result;
		}
		
		if (formula.contains ("/")) {
			//Log.d("GTOMath.mathoperation()","Math Operation: Division");

			// Split the formula into the left side and right side of plus sign: 
			args=formula.split("\\/");
			if (args.length != 2 || args[0].length()<1 || args[1].length()<1) {
				Log.e("GTOMath.mathOperation()","ERROR: number of operations not equal to one! " + formula);
				return result;
			}
			
			// set result to the left side, then run .add against result and the right side. 
			result = new BigDecimal(args[0]);
			try {result = result.divide(new BigDecimal (args[1]),FORMULA_DEFAULT_PRECISION,RoundingMode.UP);} catch (Exception e) {
				Log.e("GTOMath.mathOperation()","ERROR during Divide, Formula=" + formula + " arg0=" + args[0] + " arg1=" + args[1],e);
			}
			return result;
		}

		// No operation specified, assume it's a lone variable such as "A". 
		try {result = new BigDecimal (formula);} catch (Exception e) {
			Log.e("GTOMath.mathOperation()","Warning: last attempt to convert formula failed. formula=" + formula);
			result = new BigDecimal ("0");
		}
		return result;
	}
	

	public static double safeStringToDouble (String doubleAsString) {
		double d = 0.00d;
		
		try {
			d = Double.valueOf(doubleAsString);
		} catch (NumberFormatException e) {
			Log.e("GTOMath.SafeStringToDouble","Error converting to double: " + doubleAsString);
		}
		
		return d;
	}
	
	
	
} // end of class. 