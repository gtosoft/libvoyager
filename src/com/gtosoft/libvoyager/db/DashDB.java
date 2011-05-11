/**
 * (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
 * Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
 * use, modify the code as long as you do not include it as part of commercial software.
 */

package com.gtosoft.libvoyager.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.gtosoft.libvoyager.util.EasyTime;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.UnitConverter;

//import com.android.vending.licensing.AESObfuscator;
//import com.android.vending.licensing.ValidationException;
//import com.gtosoft.dash.util.EasyTime;
//import com.gtosoft.dash.util.GeneralStats;
//import com.gtosoft.dash.util.UnitConverter;



/**
 * A Class to facilitate Dash related database queries. 
 * This class will be responsible` for fetching data from the database if necessary and caching it locally in hashmaps. 
 * Upon fetching an item from the DB, assume it won't chnage and read from hashmap. 
 * Include a method to reload hashmaps (invalidate all cached data)
 * 
 * The DB will get overwritten on upgrade or reinstall. we're assuming no persistently needed data will be stored here. 
 *
 */

public class DashDB extends SQLiteOpenHelper {
	final boolean DEBUG=false;
	
	GeneralStats mgStats = new GeneralStats();
	
	// Please redo this. It's a hack, a global flag, which we use to determine whether or not to convert units. 
	boolean CONVERT_TO_US_UNITS = false;

	public static final String SCHEMA_ASSET_FILE_NAME = "schema.sql";
	
	// Fields of the array which we cache for each DPN. 
	public static final int OBD_CACHEFIELD_DPN 		= 0;
	public static final int OBD_CACHEFIELD_DATASHORTNAME = 1;
	public static final int OBD_CACHEFIELD_REQUEST 	= 2;
	public static final int OBD_CACHEFIELD_FORMULA 	= 3;
	public static final int OBD_CACHEFIELD_DESCRIPTION= 4;
	public static final int OBD_CACHEFIELD_MINVALUE 	= 5;
	public static final int OBD_CACHEFIELD_MAXVALUE 	= 6;
	
//	dataPointName = uc.getDPN();
//	dataShortName = uc.getShortName();
//	formula 	  = uc.getformula();
//	description	  = uc.getDescription();
//	minValue 	  = uc.getMinValue();
//	maxValue 	  = uc.getMaxValue();

	
	
	// To store the network for which we're getting PID data for in passive mode.
	// We store it so that we can tell if the caller has changed the network ID for which they are making requests. 
	String mPIDNetwork = "";
	
	private boolean mIsReady = true;


	public final static String DB_NAME = "dash.db";
	public final static int DB_VERSION = 20;
	// Version Changes: 
	// 2 -> 3 - Added a bunch of OnStar data points, TPMS, Air Temp, speed, RPM.
	// 3 -> 4 - Added a bunch of database schema and records to support standard OBD2 requests, as well as some "convbaseline" data for baselining the OBD2 converter methods.
	// 4 -> 5 - Added new field to obdRequest: "dataPointName" and set a value for this field on many of the request records.
	// 5 -> 6 - Updated convBaseLine for AT respnses to put result data into data field rather than hexBytes field.
	// 6 -> 7 - added command table, adjusted network records.
	// 7 -> 8 - modified the command table structure and added a few command records.
	// 8 -> 9 - Added some new commands, tweaked schema so tables aren't dropped.
	// 9 -> 10 - Added DTC_RESET command to reset the MIL Lamp. Also deleted table dataChanges and added monitorsession table.   
	// 10 -> 11 - Added profiles table to store vehicle profiles and gauge preferences, and whatever else we want. Also major updates to obdRequest. 
	// 11 -> 12 - Added formulas for Lambda PIDs in OBD2 mode. 
	// 12 -> 13 - added a ton of DataPoint decode logic and asociated DB modifications to accomode passive-mode data decoding.
	// 13 -> 14 - Added VIN datapoint to network 01/LSCAN for more CAN decoding fun.
	// 14 -> 15 - added FOB command as datapoint for testing. 
	// 15 -> 16 - adjusted a few datapoint formulas.
	// 16 -> 17 - Adding shittons of passive datapoints.  
	// 17 -> 18 - mode datapoints and fixes.
	// 18 -> 19 - changed obdRequest a little bit in support of the new units - US/Metric conversion ability.
	// 19 -> 20 - Added timestamp field to the profiles table. 
	
	// Used by the passive data notification logic. our method "getDPSForHDR" returns a string array where the Y indices are these fields. 
	public static final int DPS_FIELD_SIGBYTES 	= 0; // significant bytes, if any. 
	public static final int DPS_FIELD_DPN 			= 1; // aka dataname, datapointname, etc. 
	public static final int DPS_FIELD_FORMULA	= 2; // decode formula
	
	// caches number of sig bytes used for each data point name. 
	HashMap <String,Integer>mhmSigByteCount = null;
//	// caches formulas for data point names. 
	HashMap <String,String> mhmFormulas     = null;
	// header with sig bytes by data point name
	HashMap <String,String> mhmHeaderAndSigs = null;
//	// caches (datapointName,obdRequest) pairs. 
//	HashMap <String,String> mhmOBDRequest = null;
//	// caches (datapointName,formula) pairs. 
//	HashMap <String,String> mhmOBDFormula = null;
	// caches (commandname, command) pairs. 
	HashMap <String,String> mhmCommands = null;
//	// dataPointNames by header
//	HashMap <String,String> mhmDataPointNameByHeader  = null;
	
	// For passive Datapoint extraction logic. HDR=CAN Header,DPS=Datapoints and formulas.   
	HashMap<String,String[][]> mhmHDRToDPS = null;
	
	// This will cache various OBDRequest things based on DPN as the key. Mainly intended to simplify Unit conversions. 
	HashMap<String,String[]> mhmDPNCache = null;
	
	Context mctx = null;

	int mNumDPNHeaderFails = 0;

	
	
	private void resetHashmaps () {
		mhmDPNCache			=  new HashMap<String,String[]>();
		mhmSigByteCount  	=  new HashMap<String,Integer>();
		mhmFormulas		 	=  new HashMap<String,String> ();
		mhmHeaderAndSigs 	=  new HashMap<String,String> ();
//		mhmOBDRequest	 	=  new HashMap<String,String> ();
//		mhmOBDFormula    	=  new HashMap<String,String> ();
		mhmCommands		 	=  new HashMap<String,String> ();
//		mhmDataPointNameByHeader  = new HashMap<String,String> ();
		mhmHDRToDPS 		= new HashMap<String,String[][]>();
		mNumDPNHeaderFails = 0;
	}
	
	/**
	 * Main constructor. 
	 * @param context
	 */
	public DashDB(Context context) {
		super(context, DB_NAME, null, DB_VERSION);

		mctx = context;
		
		resetHashmaps();

	}


	public static void safeSleep (int numSeconds) {
		try {
			Thread.sleep(numSeconds * 1000);
		} catch (InterruptedException e) { }
	}

	/**
	 * used internally to this class to send messages to a useful place. 
	 * @param message
	 */
	private void msg (String message) {
		Log.d("DashDB","MSG=" + message);
	}
	

	/**
	 * @return - the number of data bytes, starting from the first, leftmost one, which need to be appended to the header to make the data unique. 
	 * In other words, some PIDs contain two or more different data points even though they come from the same PID. 
	 * This method scans the dataPoint table for the given network and header and returns the longest sigBytes legnth.  
	 */
	public int getSigByteCount (String network, String header) {
		int maxSigBytes = 0;
		
		if (mhmSigByteCount.containsKey(header))
			return mhmSigByteCount.get(header);
		
		Cursor c = null;
		
		String sql = "select max(length(sigBytes)) m, sigBytes from dataPoint where network = ? and header = ?";
		String selectionArgs[] = {network,header};
		c = getReadableDatabase().rawQuery(sql, selectionArgs);
		
		if (c == null) {
			msg ("getSigByteCount(): Error: null cursor returned from query: " + sql);
			mhmSigByteCount.put(header, 0);
			return 0;
		}
		
		if (c.getCount() < 1) {
			msg ("getSigByteCount(): Error: no records returned from query: " + sql);
			mhmSigByteCount.put(header, 0);
			c.close();
			return 0;
		}

		// some risky business here, better surround with try-catch. 
		try {
			c.moveToFirst();
			// grab the actual sigBytes field value 
			
			// Are none of them defined? In which case, longest would be zero:  
			if (c.getInt(0) == 0) {
				maxSigBytes = 0;
			} else {
				// Split the sigBytes field into components to find out exactly how many sigbytes are there. 
				String longest = c.getString(1);
				// trim off any leading/trailing spaces.
				longest = longest.trim();
				
				maxSigBytes = longest.split(" ").length;
			}
			
		} catch (Exception e) { 
			msg ("getSigByteCount(): Problem while calculating sigBytes max length for header " + header + ": msg=" + e.getMessage());
		}
		
		c.close();
		// write it to the hashmap (cache it). 
		mhmSigByteCount.put(header, maxSigBytes);
		return maxSigBytes;
	}

	/**
	 * Execute this method to populate the database schema.  
	 * @return - returns false if something went wrong. True otherwise. 
	 */
	private boolean buildSchema(SQLiteDatabase db) {
		
		//SQLiteDatabase db = getWritableDatabase();
		mIsReady = false;

		// Read the schema file, execute each line in a single transaction. 
		InputStream schemaStream = null;
		try {schemaStream = mctx.getAssets().open(SCHEMA_ASSET_FILE_NAME);} catch (Exception e) {
			msg ("buildSchema(): Exception during asset open: " + e.getMessage());
			return false;
		}
		BufferedReader r = new BufferedReader(new InputStreamReader (schemaStream));
		
		db.beginTransaction();
		String thisline = "";
		boolean ret = false;
		
		// Main loop, also surrounded with try-catch. 
		try {
			while (r.ready()) {
				thisline = r.readLine();
				
				// trim leading and trailing spaces. 
				thisline = thisline.trim();
				
				// Skip lines starting with hash mark.
				if (thisline.startsWith("#") || thisline.length() < 2)
					continue;

				ret = safeSQL (db,thisline);
				if (ret == true) { 
				} else { 
					msg ("buildSchema(): Looks like this statement just blew it: " + thisline);
				}
				
			}
		} catch (IOException e) {
			msg ("buildSchema(): Exception after line=" + thisline + "   Error: " + e.getMessage());
			return false;
		}
		
		db.setTransactionSuccessful();
		db.endTransaction();
		
		mIsReady = true;
		return true;
	}
	

    /*
     * Given a database and a statement, execute the statement against the
     * database. Catch any errors, return false on error, true on success.
     */
    private boolean safeSQL(SQLiteDatabase db, String sql) {

            if (sql.length() < 1)
                    return false;

            if (sql.endsWith(";"))
                    sql = sql.substring(0, sql.length() - 1);

            try {
                    db.execSQL(sql);
            } catch (Exception e) {
                    msg ("SafeSQL(): Error while executing statement: " + sql + " err=" + e.getMessage());
                    return false;
            }

            msg ("safeSQL(): Successful execution of " + sql);
            return true;
    }

    
    /**
     * Returns the formula for the given datapoint name. 
     * @param network - the network from network table. 01 = SWCAN. 
     * @param dataPointName - datapointname
     * @return - returns the formula, such as: 33-63,ONSTAR_LAT or 32-32,BIT
     */
    public String getFormula (String network, String dataPointName) {
    	String formula = "";
    	
    	if (mhmFormulas.containsKey(dataPointName))
    		return mhmFormulas.get(dataPointName);
    	
    	Cursor c = null;
    	
    	String sql = "SELECT formula from dataPoint where network = ? and dataName = ?";
    	String selectionArgs[] = {network,dataPointName};
    	// perform the query. 
    	c = getReadableDatabase().rawQuery(sql, selectionArgs);

    	// check the cursor, if its valid with a record, good!
    	if (c == null)
    		return "ERROR:NullCursor";
    	
    	if (c.getCount()>0)
    		c.moveToFirst();
    	else {
    		c.close();
    		return "ERROR:NoFormula";
    	}
    	
    	// obtain the formula from the first field
    	formula = c.getString(0);
    	
    	// always close cursor when finished using it. 
    	c.close();

    	// cache the newly found formula. 
    	mhmFormulas.put(dataPointName, formula);
    	
    	return formula;
    }
    
    
	/**
	 * To know how many data bytes to append, we look up the pid in the database 
	 * @param header
	 * @param data
	 * @return
	 */
	public String getHeaderWithSigBytes (String network, String header, String data) {
		String ret = "";
		
		
		int sigBytes = getSigByteCount(network, header);

		// simple case: no sig bytes crapp to worry about!
		if (sigBytes == 0)
			return header;
		
		String dBytes[] = data.split(" ");
		
		ret = header + ":";
		// TODO: Make this faster by not using split and instead returning a carefully calculated substring of data() appended to the header. 
		// Loop through sigbytes, and as long as we're within bounds of the split up bytes... 
		for (int i=0;i<sigBytes && i<dBytes.length;i++) {
			// Append one more byte.  
			ret = ret + dBytes[i];
		}

		return ret;
	}


	/**
	 * This method will be used when the calling class wants to search its data for the given data point but their data is arranged by header+sig.
	 * @param dataPointName
	 * @return
	 */
	public String getHeaderWithSigBytes (String network, String dataPointName) {

		// check and see if it's cached. 
		if (mhmHeaderAndSigs.containsKey(dataPointName))
			return mhmHeaderAndSigs.get(dataPointName);

		// not cached... we need to consult the DB.
		
		String ret = "";
		Cursor c = null;
		
		String sql = "SELECT header, sigBytes from dataPoint where network = ? and dataName = ?";
		String selectionArgs[] = {network, dataPointName};
		//msg ("DEBUG: Get cursor. datapoint=" + dataPointName + " network=" + network + " ctx=" + mctx);
		SQLiteDatabase db = getWritableDatabase();
		c = db.rawQuery(sql, selectionArgs);
		//msg ("DEBUG: cursor obtained.");
		
		if (c == null) {
			msg ("getHeaderWithSigBytes(): Error: null cursor for datapointname=" + dataPointName + " network " + network);
			return "";
		}

		if (c.getCount() < 1) {
			msg ("getHeaderWithSigBytes(): Warning: No data point with name " + dataPointName + " for network " + network);
			c.close();
			return "";
		}
		
		c.moveToFirst();
		String header = c.getString(0);
		String sigBytes = c.getString(1);

		c.close();
		
		header = header.trim();
		sigBytes = sigBytes.trim();
		
		// if there are sig bytes, append them with the proper syntax (header:sigbytes). Otherwise just return the header. 
		if (sigBytes.length() > 0)
			ret = header + ":" + sigBytes;
		else
			ret = header;

		// cache it for next time. 
		mhmHeaderAndSigs.put(dataPointName, ret);
		
		return ret;
	}

	/**
	 * Returns a set containing all the datapoint names in the database for the given network.
	 * @return
	 */
	public Set <String> getDataPointNamesSet(String whichNetwork) {
		Set<String> s = new HashSet<String>(); // = new Set<String>() ;

		Cursor c = null;

		// open the cursor
		String sql = "SELECT dataName from dataPoint where network = ? and length(dataName)>0";
		String [] selectionArgs = {whichNetwork};
		c = getReadableDatabase().rawQuery(sql, selectionArgs);
		
		if (c == null) {
			msg ("getDataPointNamesSet(): Error: null cursor (no datapoints found for network " + whichNetwork);
			return s;
		}
		
		if (c.getCount() < 1) {
			msg ("getDataPointNamesSet(): Error: empty set (no datapoints found for network " + whichNetwork);
			c.close();
			return s;
		}
		
		// iterate through the records
		c.moveToFirst();
		String thisDataPointName = "";
		while (!c.isAfterLast()) {
			// for each record, add the point name to the set
			thisDataPointName = c.getString(0);
			s.add(thisDataPointName);
			c.moveToNext();
		}

		// close the cursor
		c.close();
		
		// return the set
		return s;
	}
	
	public boolean backupDB (String optionalName) {
		String sourcePath 		= mctx.getDatabasePath(DB_NAME).getAbsolutePath();
		String backupFileName 	= "dash-backup-" + optionalName + ".db";
		String backupDirectory 	= Environment.getExternalStorageDirectory() + "/Dash";;
		
		// mkdir Dash if necessary
		mkdir(backupDirectory);

		msg ("About to try to back up " + sourcePath + " to " + backupDirectory + "/" + backupFileName);

		// commence with file copy, as few lines as possible. 
		try {copyFile(sourcePath, backupDirectory + "/" + backupFileName);} catch (IOException e) {
			msg ("backupDB(): Exception during file copy:" + e.getMessage());
			return false;
		}
		
		return true;
	}
	
	
	public void dbMsg (String msg) {
		
		String timeStamp = "" + System.currentTimeMillis() / 1000L;
		
		String sql = "INSERT INTO message (timestamp,msg) VALUES (?,?)";
		String [] bindArgs = {timeStamp,msg};
		try {getWritableDatabase().execSQL(sql,bindArgs);} catch (Exception e) {
			Log.d("DashDB","MSG->DB Failed. Msg=" + msg + " Exception=" + e.getMessage());
		}
		
	}
	
	/**
	 * @return - true if DB ready for use, false otherwise.  
	 */
	public boolean isDBReady() {
		return mIsReady;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		msg ("DashDB.onCreate(): CREATING Database");
		
		buildSchema(db);
	}


	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		msg ("DB Upgrade from " + oldVersion + " to " + newVersion);

		try {
			Toast.makeText(mctx,"Voyager Database upgrade in progress...",Toast.LENGTH_SHORT).show();
		} catch (Exception e) {msg ("Toast failed: " + e.getMessage());}
		
		buildSchema(db);

		try {
			Toast.makeText(mctx,"Database upgrade complete!",Toast.LENGTH_SHORT).show();
		} catch (Exception e) {msg ("Toast failed: " + e.getMessage());}
	}

	

	/**
	 * Simply copy a file from source to destination. 
	 * @param sourceFile
	 * @param destFile
	 * @throws IOException
	 * Credit: http://stackoverflow.com/questions/106770/standard-concise-way-to-copy-a-file-in-java
	 */
	private boolean copyFile(String src, String dest) throws IOException {
		
		File sourceFile = new File (src);
		File destFile   = new File (dest);
		
		 if(!destFile.exists()) {
		  destFile.createNewFile();
		 }

		 FileChannel source = null;
		 FileChannel destination = null;

		 try {
		  source = new FileInputStream(sourceFile).getChannel();
		  destination = new FileOutputStream(destFile).getChannel();
		  destination.transferFrom(source, 0, source.size());
		 } catch (Exception e) {
			 msg ("copyFile(): Error while copying: msg=" + e.getMessage() + " from " + sourceFile + " to " + destFile);
			 return false;
		 }
		 
		 if (source != null)
			 source.close();
		 if (destination != null)
			 destination.close();

		 return true;
	}// end of copyFile method. 


	/** 
	 * Create the specified directory (the last part of the path only, not the whole tree). 
	 * @param directory - directory, for example: /sdcard/Dash
	 * @return - returns true on success, false otherwise.
	 */
	public boolean mkdir (String directory) {

		try {new File(directory).mkdir();
		} catch (Exception e) {
			msg ("mkdir(): Error creating directory: " + directory + " msg=" + e.getMessage());
			return false;
		}
		
		return true;
	}

	/**
	 * Go to the OBDRequest cache and pull out a string field for the given DPN. 
	 * @param DPN - datpointname
	 * @param fieldnum - field num of the cache entry, defined in DashDB.ODB_CACHEFIELD_*
	 * @return
	 */
	private String getOBDCacheString (String DPN, int fieldnum) {
		cacheOBDRequestFormulaStuff(DPN);
		if (mhmDPNCache.containsKey(DPN)) {
			String [] attribs = mhmDPNCache.get(DPN);
			return attribs[fieldnum];
		} else {
			return "";
		}
	}
	
	/**
	 * Go to the OBDRequest cache and pull out a double-precision field value for the given DPN. 
	 * @param DPN - datpointname
	 * @param fieldnum - field num of the cache entry, defined in DashDB.ODB_CACHEFIELD_*
	 * @return
	 */
	private double getOBDCacheDouble (String DPN, int fieldnum) {
		cacheOBDRequestFormulaStuff(DPN);
		String [] attribs = mhmDPNCache.get(DPN);
		return safeStringToDouble(attribs[fieldnum]);
	}
	
	public String getOBDRequestByName (String DPN) {
		return getOBDCacheString(DPN,OBD_CACHEFIELD_REQUEST);
	}
	
	public String getOBDFormulaByName (String DPN) {
		return getOBDCacheString(DPN,OBD_CACHEFIELD_FORMULA);
	}

	public String getOBDShortName(String DPN) {
		return getOBDCacheString(DPN,OBD_CACHEFIELD_DATASHORTNAME);
	}
	
	public double getOBDMinValue(String DPN) {
		return getOBDCacheDouble(DPN,OBD_CACHEFIELD_MINVALUE);
	}

	public double getOBDMaxValue(String DPN) {
		return getOBDCacheDouble(DPN,OBD_CACHEFIELD_MAXVALUE);
	}
	
	public void setUSUnits (boolean trueIfConvertToUS) {
		CONVERT_TO_US_UNITS = trueIfConvertToUS;
//		msg ("DPN=" + DPN + " use US units? " + CONVERT_TO_US_UNITS + " Thread=" + Thread.currentThread().getId());

		if (DEBUG==true) msg ("Set Unit conversion to US = " + trueIfConvertToUS + " Thread=" + Thread.currentThread().getId());
		
		// invalidate the cache, so that formulas get re-calculated from the DB.
		mhmDPNCache = new HashMap<String,String[]>();
	}

	/**
	 * For the given DPN, cache the formula stuff like formula, min, max, units. 
	 * This allows us to process all of those fields through a unit conversion if necessasry. 
	 * @param DPN
	 * @return
	 */
	private boolean cacheOBDRequestFormulaStuff (String DPN) {

		// If it's already cached, just return true...
		if (mhmDPNCache.containsKey(DPN))
			return true;
		
		UnitConverter uc = new UnitConverter();

		if (DEBUG==true) msg ("DPN=" + DPN + " use US units? " + CONVERT_TO_US_UNITS + " Thread=" + Thread.currentThread().getId());
		
		uc.setUSUnits(CONVERT_TO_US_UNITS);
		
		// Use a Cursor to pull up the desired record. 
		Cursor c = null;
		String SQL = "SELECT dataPointName, dataShortName, request, formula, description, minValue, maxValue FROM obdRequest where dataPointName = ?";
		String [] selectionArgs = {DPN};
		try {
			c = getReadableDatabase().rawQuery(SQL, selectionArgs);
		} catch (Exception e) {
			msg ("Error querying obdRequest: E=" + e.getMessage()  + " SQL=" + SQL);
			return false;
		}
		
		if (c.getCount() < 1) {
			msg ("WARNING: DPN Not Found in obdRequest table: " + DPN);
			closeCursor(c);
			return false;
		}

		if (c.getCount() > 1) {
			msg ("WARNING: obdRequest table contains duplicate entries for DPN=" + DPN);
		}
		
		// position ourself at the record. 
		c.moveToFirst();
		
		// Obtain all the fields as local variables. 
		String dataPointName = c.getString(OBD_CACHEFIELD_DPN);
		String dataShortName = c.getString(OBD_CACHEFIELD_DATASHORTNAME);
		String request = c.getString(OBD_CACHEFIELD_REQUEST);
		String formula = c.getString(OBD_CACHEFIELD_FORMULA);
		String description = c.getString(OBD_CACHEFIELD_DESCRIPTION);
		double minValue = safeStringToDouble (c.getString(OBD_CACHEFIELD_MINVALUE));
		double maxValue = safeStringToDouble (c.getString(OBD_CACHEFIELD_MAXVALUE));
			
		// The setData method takes all the DPN attributes, converts them if necessary, and makes them available immediately. 
		uc.setData (
				dataPointName,
				dataShortName,
				request,
				formula,
				description,
				minValue,
				maxValue
				);
		
		// Now obtain the fields, post-convrsion. 
		dataPointName = uc.getDPN();
		dataShortName = uc.getShortName();
		formula 	  = uc.getformula();
		description	  = uc.getDescription();
		minValue 	  = uc.getMinValue();
		maxValue 	  = uc.getMaxValue();
		
		// Put the converted data back into hashmaps. 
		String[] DPNCacheEntry = {
				dataPointName,
				dataShortName,
				request,
				formula,
				description,
				"" + minValue,
				"" + maxValue
				};
		
		mhmDPNCache.put(DPN,DPNCacheEntry);

		// return true on success
		closeCursor(c);
		return true;
	}
	
	/**
	 * 
	 * @param doublestring
	 * @return
	 */
	public static double safeStringToDouble (String doublestring) {
		Double d = 0.0d;
		
		try {
			d = Double.valueOf(doublestring);
		} catch (Exception e) {
//			Log.d("DashDB.safeStringToDouble","Error converting string to double: " + doublestring + " E=" + e.getMessage());
		}
		
		return d;
	}

	
	/**
	 * For use in baseline testing. 
	 * @return - returns a cursor containing all fields and all records of the convBaseline table. The return value will be null in the event of an error. 
	 */
	public Cursor getConvBaselineRecords () {
		Cursor c = null;
		
		String sql = "SELECT id,ProtocolID, VehicleID, request, response, source, hexBytes, data from convbaseline";

		// perform the query. 
		try {c = getReadableDatabase().rawQuery(sql,null);} catch (Exception e) {
			msg ("getConvBaselineRecords(): Error querying baseline records! err=" + e.getMessage());
		}
		
		// return the recordset. If there was an error, its null. 
		return c;
	}


	public void shutdown () {

		// end any transactions which may have been ongoing. 
		try {getReadableDatabase().endTransaction();} catch (Exception e) { }
		
		// close the database. 
		try {close();} catch (Exception e) { }
	}

	
	/**
	 * returns a Set<String> containing all the datapoints defined for OBD. 
	 * @return
	 */
	public Set<String> getOBDDatapoints () {
		Set<String> s = new HashSet<String>();
		
		Cursor c = null;
		
		String sql = "SELECT dataPointName from obdRequest where length(dataPointName)>0 ";
		try {
			c = getReadableDatabase().rawQuery(sql, null);
		} catch (Exception e) {
			msg ("Error during query: " + e.getMessage());
			return s;
		}

		if (c == null)
			return s;
		
		if (c.getCount() < 1) {
			msg ("No OBD datapoints found in DB.");
			c.close();
			return s;
		}
		
		c.moveToFirst();
		
		// Loop through all the records, making note of the datapointname each time and adding it to the return map. 
		while (!c.isAfterLast()) {
			s.add(c.getString(0));
			c.moveToNext();
		}
		
		c.close();
		return s;
	}

	// TODO: Add routines to access network table

	/**
	 * Given a command name, we assume networkID 01 (SWCAN) We do a lookup and return the command(s) to send for this command. 
	 */
	public String getCommandByName (String commandName) {
		// is it cached? 
		if (mhmCommands.containsKey(commandName)) {
			return mhmCommands.get(commandName);
		}
		
		final String networkID="01";
		String ret = "";
		Cursor c = null;
		String sql = "SELECT command from command where name=? and network=? ";
		String[] selectionArgs = {commandName, networkID};

		c = getReadableDatabase().rawQuery(sql, selectionArgs);
		
		if (c == null) {
			msg ("Warning: SWCAN Command not found in DB: " + commandName + " on network " + networkID);
			return "";
		}
	
		if (c.getCount() > 0) {
			if (c.getCount() > 1) msg ("Warning: More than one commands found with name=" + commandName + " and networkID " + networkID + ". Delete the duplicate from the DB." );
			c.moveToFirst();
			ret = c.getString(0);
		}
		
		c.close();
		
		// cache it. 
		mhmCommands.put(commandName, ret);
		return ret;
	}

	public Cursor getCommandRecords () {
		Cursor c = null;
		
		// TODO: also constrain by network ID. 
		String sql = "SELECT id _id, name, command, description FROM command order by name";
		
		try {
			c = getReadableDatabase().rawQuery(sql, null);
		} catch (Exception e) {
			msg ("Error while querying command records: " + e.getMessage());
		}

		return c;
	}
	
	
	// Add methods to write sniffed data to the DB. 
		// before starting the sniff, run a sync task to purge the last sniff session? to prevent DB size geting too big. 

	/**
	 * Get the next monitor session ID in the DB. That means create one. 
	 */
	public String getNextMonitorSessionID (String startTime) {
		ContentValues cv = new ContentValues();
		String sessionID="";
		
		cv.put("startTime",startTime);
		
		// insert the record. insert returns the record id as a Long, so we convert it to a string by using a string append. 
		sessionID = "" + getWritableDatabase().insert("monitorSession", null, cv);
		
		return sessionID;
	}
	
	public boolean addMonitorRecord (String sessionID, String timeStamp, String header, String oldData, String newData, long numChanges, long changeRate, long transmitRate, long numTransmits) {
		ContentValues cv = new ContentValues();

		cv.put("sessionID",sessionID);
		cv.put("timeStamp",timeStamp);
		cv.put("header",header);
		cv.put("oldData",oldData);
		cv.put("newData", newData);
		cv.put("numChanges",numChanges);
		cv.put("changeRate",changeRate);
		cv.put("transmitRate",transmitRate);
		cv.put("numTransmits",numTransmits);
		
		getWritableDatabase().insert("monitorData", null, cv);
		
		return true;
	}
	
	public boolean setMonitorSessionStopTime (String sessionID, String stopTime) {
		ContentValues cv = new ContentValues();
		
		cv.put("stopTime",stopTime);
		
		int rows = getWritableDatabase().update("monitorSession", cv, "id=?",new String []{sessionID});
		
		if (rows != 1) {
			msg ("Warning: Update to monitor session ID " + sessionID + " with stopTime " + stopTime + " resulted in an update of " + rows + " records.");
			return false;
		}
		
		return true;
	}
	
	public boolean setProfileValue (String proType, String proSubType, String proKey, String proValue) {
		ContentValues cv = new ContentValues();

		cv.put("ProType",proType);
		cv.put("proSubType",proSubType);
		cv.put("proKey",proKey);
		cv.put("proValue",proValue);
		cv.put("timeStamp", EasyTime.getUnixTime());
		
		long recID = -1;
		int rowsAffected = 0;
		
		// Figure out whether we need to do an INSERT or an UPDATE. 
		recID = getProfileRecordID(proType, proSubType, proKey);
		if(DEBUG==true) msg ("About to set profile record data. K=" + proKey + " Old RecID=" + recID);
		if (recID >= 0){
			// Record exists, update it. 
			rowsAffected = getWritableDatabase().update("profiles", cv, "id=" + recID, null);
			if (DEBUG==true) msg ("Profile record update affected " + rowsAffected + " Rows.");
			if (rowsAffected < 1) 
				msg ("WARNING: This shouldn't happen, but we tried to update profile record ID " + recID + " but the update affected 0 records.");
			if (DEBUG) msg ("Profile record updated: K=" + proKey);
		} else {
			if (DEBUG==true) msg ("Existing record not found for K=" + proKey + " Inserting new profile record.");
			recID = getWritableDatabase().insert("profiles", null, cv);
			if (DEBUG) msg ("Profile record added: Type=" + proType + " subtype=" + proSubType + " K=" + proKey + " V=" + proValue);
		}
		
		
		return true;
	}

	/**
	 * Given the specified inputs, find the associated record ID, find first is ok. 
	 * @param proType
	 * @param proSubType
	 * @param proKey
	 * @return
	 */
	private long getProfileRecordID (String proType, String proSubType, String proKey) {
		String sql = "SELECT id from profiles where proType=? and proSubType=? and proKey=?";
		String sqlArgs[] = {proType, proSubType, proKey};
		Cursor c = null;
		long recID = -1;
		
		// perform the query and obtain the record ID. 
		try {
			c = getReadableDatabase().rawQuery(sql, sqlArgs);
			c.moveToFirst();
			if (c.getCount() > 0) {
				recID = c.getLong(0);
			}
		} catch (Exception e) {
			msg ("ERROR During profiles RecID query: " + e.getMessage());
		}

		if (c != null)
			c.close();
		
		return recID;
	}
	
	//CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, proType TEXT, proSubType TEXT, proKey TEXT, proValue TEXT);
	public String getProfileValue (String proType, String proSubType, String proKey) {
		String proValue = "";
		
		String sql = "SELECT proValue from profiles where proType=? and proSubType=? and proKey=?";
		String sqlArgs[] = {proType, proSubType, proKey};
		
		Cursor c = null;

		// perform the query. 
		try {c = getReadableDatabase().rawQuery(sql, sqlArgs);
		} catch (Exception e) {
			msg ("ERROR During profiles query: " + e.getMessage());
		}
		
		if (c == null) {
			msg ("Warning: NULL Cursor while querying profile records where T=" + proType + " ST=" + proSubType + " K=" + proKey);
			return "";
		}
		
		if (c.getCount() < 1) {
			if (DEBUG) msg ("No profile data for T=" + proType + " ST=" + proSubType + " K=" + proKey);
			c.close();
			return "";
		}

		if (c.getCount() > 1) {
			msg ("ERROR: AMBIGUOUS result (" + c.getCount() + ") while querying profile records for T=" + proType + " ST=" + proSubType + " K=" + proKey);
		}
		
		// Get the data and close the cursor. 
		c.moveToFirst();
		proValue = c.getString(0);
		c.close();
		
		return proValue;
	}

	/**
	 * Returns the string value of a specified field, as identified by its datapointname. 
	 * @param fieldName
	 * @param dataPointName
	 * @return
	 */
	public String X_getOBDFieldByName(String fieldName, String dataPointName) {
		String description = "";

		String sql = "SELECT " + fieldName + " from obdRequest where dataPointName = ?";
		String sqlArgs[] = {dataPointName};
		
		Cursor c = null;
		try {
			c = getReadableDatabase().rawQuery(sql, sqlArgs);
		} catch (Exception e) {
			msg ("Error while querying obdRequest: " + e.getMessage());
		}
		
		if (c == null) 
			return "";

		if (c.getCount() < 1) {
			c.close();
			return "";
		}
		
		c.moveToFirst();
		
		try {
			description = c.getString(0);
		} catch (Exception e) { msg ("Error getting field " + fieldName + " from result. E=" + e.getMessage());
		}
		
		c.close();
		
		return description;
	}


	/**
	 * A convenience method to close the specified cursor. 
	 * @param c
	 */
	private static void closeCursor (Cursor c ) {
		try {
			c.close();
		} catch (Exception e) {
			Log.e("DashDB.closeCursor()","Error closing cursor with convenience method. E=" + e.getMessage());
		}
	}

	/**
	 * This method builds a hashset containing the value of column 0 of each record, if that record contains something. 
	 * @param SQLQuery
	 * @return - always returns a set. That set may contain zero or more elements. 
	 */
	public Set<String> getColumnSet (String SQLQuery) {
		Set <String> s = new HashSet<String>();
		
		Cursor c = null;
	
		try {
			c = getReadableDatabase().rawQuery(SQLQuery, null);
		} catch (Exception e) {
			msg ("Error during query. Q=" + SQLQuery);
			closeCursor(c);
			return s;
		}
		
		// If c is null, then we can't close the cursor. All we can do is just return (an empty set), so do it. 
		if (c==null)
			return s;
		
		// Any records in the cursor? 
		if (c.getCount() < 1) {
			msg ("Warning: No records found using Q=" + SQLQuery);
		} else {
			c.moveToFirst();
		}

		String thisData = "";
		// Loop through all the records, adding record col0 values to our set along the way. 
		while (!c.isAfterLast()) {
			// Obtain a string containing the stuff from column 0. 
			thisData = c.getString(0);
			// If the string is valid then add it to our return set. 
			if (thisData.length() > 0) 
				s.add(thisData);
			
			// iterate to the next record. 
			c.moveToNext();
		}
		
		// Close the cursor. 
		closeCursor(c);
		
		return s;
	}
	
	
	/**
	 * Returns a list of ACTIVE (scannable) obd request records. 
	 * @return - returns a cursor covering those records.  fields: id, dataPointName, request, description.
	 * Used by the preferences dialog to display a list of available OBD2 datapoints.  
	 */
	public Cursor getOBDActiveRequests () {
		Cursor c = null;
		
		String sql = "SELECT id _id, dataPointName, request, description FROM obdRequest WHERE scannable = 1";

		try {c = getReadableDatabase().rawQuery(sql, null);
		} catch (Exception e) {
			msg ("Error while performing query. E=" + e.getMessage() + " Q=" + sql);
		}

		return c;
	}

	/**
	 * Returns a list of ACTIVE (scannable) obd request records. 
	 * @return - returns a cursor covering those records.  fields: id, dataPointName, request, description. 
	 */
	public Cursor getAllPassiveDPNs() {
		Cursor c = null;
		
		String sql = "SELECT id _id, network, dataName DPN, description FROM dataPoint GROUP BY dataName ORDER BY dataName";

		try {c = getReadableDatabase().rawQuery(sql, null);
		} catch (Exception e) {
			msg ("Error while performing query. E=" + e.getMessage() + " Q=" + sql);
		}

		return c;
	}

	/**
	 * This method will look up the given header in the dataPoint table of the database. It will return a 2-dimensional  (say X and Y) string array: 
	 * X = zero or more instances of block Y to be searched for the given PID. 
	 * Y = Sigbytes, DPN (Datapoint Name), Formula
	 * - The String array indices will be defined in DashDB.DPS_FIELD_XXXX
	 * - This will be used by the PIDDecoder class to extract data points from data packets. 
	 * @param HDR - the 29 or 11-bit CAN header such as "10 00 20 40" or "199"... 
	 * @return - 
	 *  - Returns a reference to the string array containing key information necessary for decoding/identifying the datapoint. 
	 * 	- Returns null if there are no DPNs for the given header. 
	 */
	public String[][] getDPSForHDR (String networkID, String HDR) {
		// If the Network ID changed, that's bad - because we aren't storing the cached data by network ID, so in the interest of not mixing up stuff from different network IDs, we just invalidate the cache if necessary and generate a log message. 
		if (!networkID.equals(mPIDNetwork)) {
			// only spit out a message if we're NOT setting the network for the first time.
			if (mPIDNetwork.length() > 0) 
				msg ("WARNING: Network ID changed from " + mPIDNetwork + " TO " + networkID + " Optimizations are being restored.");
			 mPIDNetwork = networkID;
			 // invalidate all cached data (with the point being that we're invalidating any cache data we generated with the other network ID. 
			 resetHashmaps();
		}

		// Does the HDR exist in local cache? If so, return it from the cache!
		if (mhmHDRToDPS != null && mhmHDRToDPS.containsKey(HDR))
			return mhmHDRToDPS.get(HDR);
		
		// Not cached yet - need to query the database and produce a string array. 
		String [][] ret = null;
		Cursor c = null;

		
		// 									***         	*** 			***
		String sql = "SELECT sigBytes, dataName, formula FROM dataPoint WHERE Network = ? AND header = ?";
		String sqlArgs[] = {networkID, HDR};
		try {c = getReadableDatabase().rawQuery(sql,sqlArgs);
		} catch (Exception e) {
			msg ("getDPSForHDR(): Error during query. Net=" + networkID + " E=" + e.getMessage());
			return null;
		}

		// null cursor, shouldn't happen. 
		if (c == null) {
			msg ("getDPSForHDR(): Warning: Null cursor");
			return null;
		}
		
		// at least one record? If not, enter a stub to prevent future db scans. 
		if (c.getCount() < 1) {
			mhmHDRToDPS.put(HDR, null); // enter a stub to signify that we've scanned the DB but there were no matches for this HDR.
			if (DEBUG==true) msg ("No DPNs defined for HDR=" + HDR + " Network=" + networkID);
			mNumDPNHeaderFails++;
			mgStats.setStat("numHeadersWithoutDPNs", "" + mNumDPNHeaderFails);
			c.close();
			return null;
		}
		
		if (DEBUG == true) msg("Loading " + c.getColumnCount() + " DPNs for HDR=" + HDR);
		
		
		// Iterate through the records and produce the 2d string array.
		ret = new String[c.getCount()][]; // first, allocate dimension X - one index per cursor record.  
		c.moveToFirst();
		
		for (int i=0;i<c.getCount();i++) {
			// First, get each field as a string; don't allow nulls into the fields.  
			String sigBytes = "";		if (c.getString(0) != null) sigBytes 	= c.getString(0);
			String dataName = ""; 	if (c.getString(1) != null) dataName 	= c.getString(1);
			String formula = ""; 		if (c.getString(2) != null) formula 		= c.getString(2);
			//if (formula.length() < 64) {
			//	// Update that formula in the DB with encrypted version of it. 
			//	//updateFormula(formula,encryptFormula(formula));
			//} else {
			//	// it's long enough to be encrypted, so decrypt it. 
			//	formula = unencryptFormula(formula);
			//}


			// add the 1-d array to the new X element of the 2d array. 
			ret[i] = new String [] {sigBytes, dataName, formula};

			// Don't forget to move to next record! 
			c.moveToNext();

			//if (DEBUG==true) msg ("DEBUG: getDPSForHDR added this DPS " + dataName + " formula " + formula +  " HDR=" + HDR + " to index " + i);
			 
		}// end of for-loop which iterates through the datapoint records. 

		// Close the cursor. 
		c.close();
		
		// At this point we have a 2-dimensional array.   ret[x][y]. See notes atop this method for definition of those fields. 
		
		// Save the newly queried string array to hashmap. 
		mhmHDRToDPS.put(HDR, ret);
		
		// Return the (reference to) string array from the hashmap. 
		return ret;
	}


	/**
	 * Clear any cached data based on NetworkID. 
	 * This will be called by parent if the network ID changes. 
	 */
	public void clearCachedNetworkIDData() {
		 resetHashmaps();
	}

	public GeneralStats getStats() {
		
		mgStats.setStat("dbVer","" + DB_VERSION);
		mgStats.setStat("dbName",DB_NAME);
		if (CONVERT_TO_US_UNITS == true) {
			mgStats.setStat("units","IMPERIAL");
		} else {
			mgStats.setStat("units","METRIC");
		}
		
		mgStats.setStat("cacheLevel.commands",		"" + mhmCommands.size());
		mgStats.setStat("cacheLevel.dpns",			"" + mhmDPNCache.size());
		mgStats.setStat("cacheLevel.formulas",		"" + mhmFormulas.size());
		mgStats.setStat("cacheLevel.hdrtodps",		"" + mhmHDRToDPS.size());
		mgStats.setStat("cacheLevel.hdrplssigs",	"" + mhmHeaderAndSigs.size());
		mgStats.setStat("cacheLevel.sigbytecount",	"" + mhmSigByteCount.size());
		
		return mgStats;
	}

	
	/**
	 * @return - returns a set containing all network IDs present in the dataPoint table.
	 */
	public Set<String> getAllDPNNetIDs () {
		return getColumnSet("SELECT network from dataPoint group by network");
	}

	/**
	 * @param networkID - the NetworkID for which you want all the known CAN IDs as seen in the dataPoint table of the database. 
	 * @return - returns a distinct Set containing zero or more elements corresponding to all CAN IDs known for that Network ID. 
	 */
	public Set<String> getDPNCANIDsForNetworkID (String networkID) {
		
		if (networkID.length() != 2) {
			msg ("WARNING: request for DPN IDs for apparently invalid network ID = \"" + networkID + "\"");
		}
		
		return getColumnSet("select header from dataPoint where network = \"" + networkID + "\"" + " group by header");
	}

	/**
	 * Take the provided hashmap and write it to profile table. Write them under the type/subtype provided. 
	 * @param typeName
	 * @param subTypeName
	 * @return
	 */
	public boolean saveHashmapToStorage(String proType, String proSubType, HashMap<String,String> hmData) {
		// Sanity Checks. 
		if (hmData == null || hmData.size() < 1) {
			if (DEBUG) msg ("saveHMToStorage: null/empty hm provided.");
			return false;
		}

		// Iterate. 
		Set<String> s = new TreeSet<String>(hmData.keySet());
		Iterator<String> i = s.iterator();
		
		String proKey = "";
		String proValue = "";
		// Loop through all hashmap entries and write each one. 
		while (i.hasNext()) {
			proKey = i.next();
			proValue = hmData.get(proKey);
			
			// write it to profile record. 
			setProfileValue(proType, proSubType, proKey, proValue);
		}

		return true;
	}

	/**
	 * returns a hashmap of key/value pairs for matching "profile"-table records.
	 */
	public HashMap<String,String> restoreHashmapFromStorage(String proType, String proSubType) {
		HashMap<String,String> hmData = new HashMap<String,String> ();
		
		String sql = "SELECT proKey, proValue FROM profiles WHERE proType = ? and proSubType = ?";
		String selectionArgs[] = {proType, proSubType};
		
		// get a cursor containing the desired records. 
		Cursor c = null; 
		try {
			c = getReadableDatabase().rawQuery(sql, selectionArgs);
		} catch (Exception e) {
			msg ("ERROR during query: " + sql + " E=" + e.getMessage());
		}
		
		// Iterate through the cursor and save the key/values to hashmap. 
		if (c != null && c.getCount() > 0) {
			String thisKey = "";
			String thisVal = "";
			c.moveToFirst();
			while (!c.isAfterLast()) {
				// For extra measure of safety I'm appending the field to empty string, in case the column data is null or weird. 
				thisKey = "" + c.getString(0);
				thisVal = "" + c.getString(1);
				
				// write it to hashmap. 
				hmData.put(thisKey,thisVal);
				
				c.moveToNext();
			}
		}
		
		// properly close the cursor. 
		if (c != null)
			c.close();
		
		return hmData;
	}
	
	
}// end of class...
	
