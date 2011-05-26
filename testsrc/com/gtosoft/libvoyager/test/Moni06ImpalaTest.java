package com.gtosoft.libvoyager.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.Before;
import org.junit.Test;

import android.test.AndroidTestCase;

import com.gtosoft.libvoyager.db.DashDB;
import com.gtosoft.libvoyager.util.GeneralStats;
import com.gtosoft.libvoyager.util.PIDDecoder;

public class Moni06ImpalaTest extends AndroidTestCase {

	BufferedReader inStream;
	
	
    @Before
    public void setup() throws IOException {
        inStream = new BufferedReader(
            new InputStreamReader(getClass().getResourceAsStream("impalass.dmp")));
    }


	@Test
	public void testMoniImpala() throws Exception {

		// TODO: How can we test piddecoder without a lookup DB? Need to abstract DDB away from android.jar... 
		DashDB ddb = new DashDB(getContext()); // todo: fix context here. It can't be null. 
		PIDDecoder pd = new PIDDecoder(ddb);
		
		String thisLine = "";
		
		// loop through the whole file. 
		while (inStream.ready()) {
			thisLine = inStream.readLine();
			
			// Throw out lines we aren't interest in.
			if (thisLine.startsWith("#"))
				continue;
			
			// Process one message at a time. piddecoder pulls it apart, handles notifications, looks it up and converts it, etc. 
			pd.decodeOneMessage(thisLine);
		}
		
		GeneralStats pdStats = pd.getStats();
		// TODO: Perform rigorous testing here based on genStats data. possibly more, such as DPN lookups, or even directly into netStats. 
		
		//		assertEquals (dtcDescriptions[i], thisDescr);
		assertEquals ("A","B");
		
		// close it out. 
		inStream.close();
		

	}// end of test. 

}
