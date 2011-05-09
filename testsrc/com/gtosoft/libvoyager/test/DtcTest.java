package com.gtosoft.libvoyager.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.gtosoft.libvoyager.util.GTOMath;
import com.gtosoft.libvoyager.util.OBDPacketParser;

/*
 * DTC Codes explained. 
 * 
	Prefix Code  Actual  Hex
	-----------------------------
	P  0+x 00xx  P0100 = 0100 
	P  0+x 00xx  P0200 = 0200
	P  0+x 00xx  P0300 = 0300
	C  4+x 01xx  C0300 = 4300
	B  8+x 10xx  B0200 = 8200
	U 12+x 11xx  U0100 = C100
 */

// This class is a test case.
// Sample DTC data from scantool.net simulator: 41 6B 10 43 01 00 02 00 03 00 43 |41 6B 10 43 43 00 82 00 C1 00 11 |41 6B 18 43 01 01 00 00 00 00 18 ||>
public class DtcTest {

	/**
	 * Test every DTC Protocol. 
	 * Also refer to https://vpn.qwerty/repos/android/eclipse/notes/DTC-fixups.txt for details of how these tests were assembled. 
	 * @throws Exception
	 */
	@Test
	public void testDTCsAllProtocols() throws Exception {
		String dtcSolutions = "P0100,P0200,P0300,C0300,B0200,U0100,P0101";
		
		String request  = "03";
		String response = "";
		String dtcs = "";
		
		// scantool.net Simulator SP=5 ATDPN=6
		response =  "03|7E8 10 0E 43 06 01 00 02 00 |7E8 21 03 00 43 00 82 00 C1 |7E8 22 00 00 00 00 00 00 00 |7E9 04 43 01 01 01 ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_ISO15765_4CAN11500, request, response);
		assertEquals(dtcSolutions, dtcs);

		// scantool.net Simulator SP=4 ATDPN=4
		response =  "03|87 F1 10 43 01 00 02 00 03 00 D1 |87 F1 10 43 43 00 82 00 C1 00 51 |87 F1 18 43 01 01 00 00 00 00 D5 ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_ISO14230_4KWP, request, response);
		assertEquals(dtcSolutions, dtcs);

		// scantool.net Simulator SP=3 ATDPN=3
		response = "03|48 6B 10 43 01 00 02 00 03 00 0C |48 6B 10 43 43 00 82 00 C1 00 8C |48 6B 18 43 01 01 00 00 00 00 10 ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_ISO9141_2, request, response);
		assertEquals(dtcSolutions, dtcs);
		
		// scantool.net Simulator SP=2 ATDPN=2
		response = "03|48 6B 10 43 01 00 02 00 03 00 05 |48 6B 10 43 43 00 82 00 C1 00 57 |48 6B 18 43 01 01 00 00 00 00 5E ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_SAEJ1850VPW, request, response);
		assertEquals(dtcSolutions, dtcs);
		
		// scantool.net Simulator SP=1 ATDPN=1
		response = "03|48 6B 10 43 01 00 02 00 03 00 05 |48 6B 10 43 43 00 82 00 C1 00 57 |48 6B 18 43 01 01 00 00 00 00 5E ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_SAEJ1850PWM, request, response);
		assertEquals(dtcSolutions, dtcs);

		// 2001 Dodge Dakota 
		response = "03|48 6B 10 43 03 06 03 56 00 00 DC ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_SAEJ1850PWM, request, response);
		assertEquals("P0306,P0356", dtcs);
		
		// 2003ToyotaCamry 
		response = "03|43 00 00 00 00 00 00 ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_SAEJ1850PWM, request, response);
		assertEquals("", dtcs);
		
		// 2006 impala ss 
		response = "03|7E8 02 43 00 |7EA 02 43 00 ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_ISO15765_4CAN11500, request, response);
		assertEquals("", dtcs);
		
		// Nissan Maxima (2001?) 
		response = "03|48 6B 10 43 00 00 00 00 00 00 06 ||>";
		dtcs = GTOMath.getAsDTCs(OBDPacketParser.PROTOCOL_SAEJ1850PWM, request, response);
		assertEquals("", dtcs);
		
	}// end of dtc test case.
	
}// end of class.
