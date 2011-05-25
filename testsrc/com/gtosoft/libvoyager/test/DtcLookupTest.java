package com.gtosoft.libvoyager.test;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import com.gtosoft.libvoyager.net.NetDTCInfo;

public class DtcLookupTest {

	@Test
	public void testNetDTCLookup() throws Exception {

		String testdtcs [] = {
				"P0021",
				"P070F",
				"P0819"};
		String dtcDescriptions [] = {
				"Camshaft Position Timing Over-Advanced or System Performance Bank 2",
				"Transmission Fluid Level Too Low",
				"Up and Down Shift Switch to Transmission Range Correlation"};

		NetDTCInfo ndi = new NetDTCInfo("unitTest");
		
		String thisDescr = "";
		for (int i=0;i<testdtcs.length;i++) {
			thisDescr = ndi.getDTCDescription(testdtcs[i]);
			assertEquals (dtcDescriptions[i], thisDescr);
		}

	}// end of testNetDTCLookup. 

	
}
