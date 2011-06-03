# (C) 2011 libvoyager is licensed under a Creative Commons Attribution-NonCommercial 3.0 Unported License. 
# Permissions beyond the scope of this license may be available at http://www.gtosoft.com. You can download, 
# use, modify the code as long as you do not include it as part of commercial software.

# TO verify the schema: 
# 	rm /tmp/x.db; cat schema-dashdb.sql  | grep -v ^\# > /tmp/schema.sql ; cat /tmp/schema.sql | sqlite3 /tmp/x.db
# 	 	if any errors are encountered, review /tmp/schema.sql. 

# This whole schema gets executed as a single transaction SO MAKE SURE IT IS COMPLETELY ERROR FREE!

# For bus monitoring
# DROP TABLE IF EXISTS monitorSession;
CREATE TABLE monitorSession (id INTEGER PRIMARY KEY AUTOINCREMENT, startTime INTEGER, stopTime INTEGER);
CREATE INDEX idx1_monisess ON monitorSession (startTime);

# For bus monitoring
DROP TABLE IF EXISTS dataChanges;
DROP TABLE IF EXISTS monitorData;
CREATE TABLE monitorData (id INTEGER PRIMARY KEY AUTOINCREMENT, sessionID text, timeStamp INTEGER, header TEXT, oldData TEXT, newData TEXT, numChanges INTEGER, changeRate INTEGER, transmitRate INTEGER, numTransmits INTEGER);
CREATE INDEX idx1_mdata ON monitorData (sessionID);

CREATE TABLE userCommand (id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT, message TEXT);

# For example, store which PIDs are supported by a particular vehicle
# Also, store specifics about vehicle, such as which networks are available, preferences, etc.
# We may also store gauge selections here too. 
# DROP TABLE IF EXISTS profiles;
CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, proType TEXT, proSubType TEXT, proKey TEXT, proValue TEXT, timeStamp INTEGER);
CREATE INDEX idx1_profiles on profiles (proType);
CREATE INDEX idx2_profiles on profiles (proSubType);


# DROP TABLE IF EXISTS message;
CREATE TABLE message (id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER,tripID TEXT, msg TEXT);
CREATE INDEX idx1_log on message (timestamp);
CREATE INDEX idx2_log on message (tripID);

# commands, which can be sent via OBD. 
# <<Dont drop the table. Just add records by record ID and if they are there then good if not they the new ones get added.>> DROP TABLE IF EXISTS command;
CREATE TABLE command (id INTEGER PRIMARY KEY AUTOINCREMENT, network TEXT, name TEXT, command TEXT, description TEXT);
CREATE INDEX idx1_cmd on command (name);
CREATE INDEX idx2_cmd on command (network);

# networks. 
# DROP TABLE IF EXISTS network;
CREATE TABLE network (id INTEGER PRIMARY KEY AUTOINCREMENT, networkID TEXT, description TEXT);
# KNOWN NETWORKS?
INSERT INTO network (id,networkID,description) VALUES (1,'00','OBDII');
INSERT INTO network (id,networkID,description) VALUES (2,'01','LSCAN GM');
INSERT INTO network (id,networkID,description) VALUES (3,'02','MSCAN GM');
INSERT INTO network (id,networkID,description) VALUES (4,'03','HSCAN GM');
INSERT INTO network (id,networkID,description) VALUES (5,'05','HEV HSCAN');


# Drop dataPoint so when we add records below, they get added and not duplicated. 
### DROP TABLE IF EXISTS dataPoint;
CREATE TABLE dataPoint (id INTEGER PRIMARY KEY AUTOINCREMENT,network TEXT,header TEXT, sigBytes TEXT, formula TEXT, dataName TEXT, description TEXT);
CREATE INDEX idx1_dp on dataPoint (network);
CREATE INDEX idx2_dp on dataPoint (header);

# Wish this worked, so we could allocate a block for our own purposes... UPDATE "sqlite_sequence" SET seq = 5000 where 'name' = "dataPoint";

INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(1,'03','514','','0-63,ASCII','VIN1OF2','first half of VIN');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(2,'03','4E1','','0-63,ASCII','VIN2OF2','second half of VIN');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(3,'01','10 00 A0 B0','','16-23,MULT4','TPMS_TP_FL','Tire Pressure Monitor System, Tire Pressure, Front-Left tire kPaG');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(4,'01','10 00 A0 B0','','24-31,MULT4','TPMS_TP_FR','Tire Pressure Monitor System, Tire Pressure, Front-Right tire kPaG');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(5,'01','10 00 A0 B0','','32-39,MULT4','TPMS_TP_RR','Tire Pressure Monitor System, Tire Pressure, Rear-Right tire kPaG');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(6,'01','10 00 A0 B0','','40-47,MULT4','TPMS_TP_RL','Tire Pressure Monitor System, Tire Pressure, Rear-Left tire kPaG');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(7,'01','10 0C 20 99','','8-15,DICTEMP','TEMP_OUTSIDE','Outdoor Temperature, as seen on the DIC');
# CAN ID $28
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(8,'01','10 05 00 40','','17-31,DIV64','SPEED_AVERAGE','Speed (Average)');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(9,'01','10 05 00 40','','32-47,DIV4','RPM','RPM');
# CAN ID $53
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(10,'01','10 0A 60 97','','0-7,INT','ONSTAR_DATEYEAR','OnStar current year from GPS');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(11,'01','10 0A 60 97','','12-15,INT','ONSTAR_DATEMONTH','OnStar current month from GPS');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(12,'01','10 0A 60 97','','19-23,INT','ONSTAR_DATEDAY','OnStar current day of month');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(13,'01','10 0A 60 97','','26-30,INT','ONSTAR_DATEHOUR','OnStar current hour');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(14,'01','10 0A 60 97','','33-38,INT','ONSTAR_DATEMINUTE','OnStar current minute');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(15,'01','10 0A 60 97','','41-46,INT','ONSTAR_DATESECOND','OnStar current seconds');
# CAN ID $55
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(16,'01','10 0A A0 97','','2-2,BIT','ONSTAR_LATITUDE_VALID','0 If Onstar Latitude reading Is Valid');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(17,'01','10 0A A0 97','','0-31,ONSTAR_LAT','ONSTAR_LATITUDE','OnStar Latitude, in Degrees');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(18,'01','10 0A A0 97','','32-32,BIT','ONSTAR_LONGITUDE_VALID','0 If Onstar Longitude reading Is Valid');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(19,'01','10 0A A0 97','','32-63,ONSTAR_LON','ONSTAR_LONGITUDE','OnStar Longitude in Degrees');
# CAN ID $56
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(20,'01','10 0A C0 97','','4-15,DIV10','ONSTAR_HEADING','OnStar current heading');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(21,'01','10 0A C0 97','','16-23,INT','ONSTAR_PRECISION','OnStar validity/precision bits');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(22,'01','10 0A C0 97','','32-39,INT','ONSTAR_SPEED','OnStar current GPS-calculated speed');
INSERT INTO "dataPoint" (id, network, header, sigBytes, formula, dataName, description) VALUES(23,'01','10 0A C0 97','','43-63,M=1,B=-100000','ONSTAR_ELEVATION','OnStar current GPS-calculated elevation in Centimeters');

INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(24,'05','030','','32-39,INT','BREAK_POSITION','Break Position');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(25,'05','039','','00-07,INT','ENGINE_TEMP','Engine (ICE) Temperature');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(26,'05','3C8','','08-23,INT','RPM','Engine RPM');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(27,'01','10 02 40 40','','0-63,ASCII','VIN1OF2','first half of VIN');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(28,'01','10 02 60 40','','0-63,ASCII','VIN2OF2','second half of VIN');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(29,'01','08 00 80 B0','','0-15,HEX','FOB_COMMAND','A FOB Command was detected.');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(30,'03','0C9','','8-23,DIV4',		'RPM',			'Speed as broadcast on the 11-bit network');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(31,'03','0C9','','32-39,MULT2051',	'PEDAL_DEPRESSION','Pedal depression, in percent');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(32,'03','120','','0-31,DIV64',		'ODOMETER',			'Odometer Reading km');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(33,'03','12A','','32-39,MULT2051',	'BATTERY_SOC',		'Battery state of charge percentage');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(34,'03','12A','','40-47,M=0.1&B=3',	'VOLTS',			'Battery Volts');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(35,'03','19D','','56-63,DIV64',		'GEAR_RATIO_TRANNY',		'Transmission Gear Ratio');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(36,'03','1C1','','4-15,M=0.25&B=-200','ENGINE_TORQUE',	'Engine Torque');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(37,'03','1E9','','36-47,M=1/16',		'YAW',				'Vehicle yaw rate');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(38,'03','1F5','','0-2,INT',			'TRANNY_CLUTCH_MODE','Tranny clutch mode, 1=transitioning, 2=slip, 3=locked');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(39,'03','1F5','','4-7,INT',			'GEAR_ESTIMATED',	'Tranny Estimated Gear');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(40,'03','1F5','','12-15,INT',		'GEAR_COMMANDED',	'Tranny Commanded Gear');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(41,'03','1F5','','28-31,INT',		'GEAR_SHIFT_POSITION',	'Tranny gear shifter position');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(42,'03','2F9','','16-23,M=4&B=-40',	'TEMP_BREAKS',	'Break Temperature degrees C');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(43,'03','2F9','','24-31,MULT2051',	'LOAD_BREAKS',	'Breaking Load in %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(44,'03','2F9','','32-39,MULT205100',	'ROAD_ROUGHNESS',	'Road Roughness in Gs between 0 and 1');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(45,'03','3F9','','24-31,MULT2051',	'GENERATOR_DUTY_CYCLE',	'Generator Duty Cycle %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(46,'03','3F9','','40-47,MULT2051',	'ENGINE_COOLFAN_SPEED',	'Speed of the engine cooling fan %');
# CAN ID $37
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(47,'01','10 06 E0 40','','16-23,MULT4',	'ENGINE_OIL_PRESSURE',	'Engine Oil Pressure in kPa');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(48,'01','10 06 E0 40','','24-31,M=1&B=-40',	'ENGINE_OIL_TEMP',	'Engine Oil Temperature in C');
# CAN ID $278
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(49,'01','10 4F 00 40','','16-23,MULT4',	'AC_PRESSURE',	'A/C high-side Pressure in kPa');
# CAN ID $60
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(50,'01','00 0C 00 99','','8-15,M=0.5&B=-40',	'TEMP_OUTSIDE_CORRECTED',	'The outside temperature, corrected');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(51,'01','00 0C 00 99','','16-23,DIV10',	'AC_COMPRESSOR_LOAD',	'A/C Normalized Load in Liters per minute');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(52,'01','00 0C 00 99','','32-39,M=0.5&B=-40',	'TEMP_OCCUPANT_FRONT',	'The temperature in the front of the car');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(53,'01','00 0C 00 99','','48-55,M=0.5&B=-40',	'TEMP_OCCUPANT_BACK',	'The temperature in the back of the car');
# CAN ID $2F
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(54,'01','10 05 E0 40','','20-31,DIV16',	'CRUISE_SPEED',	'Requested cruise control speed');
# CAN ID $29
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(55,'01','10 05 20 40','','8-15,MULT2051',	'ENGINE_COOLFAN_SPEED',	'Engine cooling fan speed %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(56,'01','10 05 20 40','','36-47,M=0.25&B=-200',	'ENGINE_TORQUE',	'Actual Engine Torque');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(57,'01','10 05 20 40','','48-55,M=1&B=-40',	'TEMP_COOLANT',	'Engine coolant temperature in C');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(58,'01','10 05 20 40','','56-63,M=1&B=-40',	'TEMP_INTAKE',	'Intake Air Temperature in C');
# CAN ID $27
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(59,'01','10 04 E0 60','','0-31,DIV64',	'ODOMETER',	'Odometer Reading km');
# CAN ID $26 
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(60,'01','10 04 C0 40','','8-15,MULT2051',	'FUEL_LEVEL',	'Fuel level %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(61,'01','10 04 C0 40','','36-47,DIV40',	'FUEL_CONSUMPTION_RATE',	'Snapshot fuel consumption rate');
# CAN ID $25
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(62,'01','10 04 A0 40','','0-3,INT',	'GEAR_ESTIMATED',	'Tranny estimated gear.');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(63,'01','10 04 A0 40','','8-11,INT',	'GEAR_SHIFT_POSITION',	'Tranny Shifter position.');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(64,'01','10 04 A0 40','','16-23,M=1&B=-40',	'TRANNY_OIL_TEMP',	'Tranny Oil temperature.');
# CAN ID $18
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(65,'01','10 03 00 40','','8-15,M=0.1&B=3',	'VOLTS',	'Battery Voltage Reading');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(66,'01','10 03 00 40','','16-23,MULT2051',	'BATTERY_SOC',	'Battery State Of Charge %');
# $3F9
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(67,'03','3F9','','48-55,MULT2051',	'ENGINE_OIL_LIFE',	'Engine oil life remaining %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(68,'03','3F9','','56-63,MULT14',	'AC_PRESSURE',	'AC system high-side pressure kPaG');
# $4C1
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(69,'03','4C1','','8-15,M=0.5',		'BAROMETER',	'Absolute Atmospheric Barometric Pressure kPa');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(70,'03','4C1','','16-23,M=1&B=-40',	'TEMP_COOLANT',	'Coolant Temperature C');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(71,'03','4C1','','24-31,M=1&B=-40',	'TEMP_INTAKE',	'Intake Temperature C');
# $4C9
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(72,'03','4C9','','8-15,M=1&B=-40',	'TRANNY_OIL_TEMP',	'Tranny Oil Temp C');
# $4D1
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(73,'03','4D1','','8-15,M=1&B=-40',	'ENGINE_OIL_TEMP',	'Tranny Oil Temp C');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(74,'03','4D1','','16-23,MULT4',	'ENGINE_OIL_PRESSURE',	'Engine Oil Pressure kPa');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(75,'03','4D1','','40-47,MULT2051',	'FUEL_LEVEL',	'Fuel Level %');
# $52A
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(76,'03','52A','','16-23,MULT4',	'TPMS_TP_FL',	'Tire Pressure - Front Left. kPaG');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(77,'03','52A','','24-31,MULT4',	'TPMS_TP_FR',	'Tire Pressure - Front Right. kPaG');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(78,'03','52A','','32-39,MULT4',	'TPMS_TP_RR',	'Tire Pressure - Rear Right. kPaG');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(79,'03','52A','','40-47,MULT4',	'TPMS_TP_RL',	'Tire Pressure - Rear Left. kPaG');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(80,'03','52A','','56-63,MULT4',	'TPMS_TP_SP',	'Tire Pressure - Spare Tire. kPaG');
# 
INSERT INTO "dataPoint" (id,network,header,sigBytes,formula,dataName,description)		VALUES(81,'01','10 00 A0 B0','','56-63,MULT4','TPMS_TP_SP','Tire Pressure Monitor System, Tire Pressure, Spare tire kPaG');
# $17D
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(82,'03','17D','','20-31,SIGNED-DIV100',	'ACCEL_ACTUAL',	'Actual Vehicle Acceleration in m/s^2');
# $0F1
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(83,'03','0F1','','8-15,MULT2051',	'PEDAL_POSITION_BRAKE',	'Position of the break pedal %');
# $1F1
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(84,'03','1F1','','48-55,M=1/10',	'AC_LOAD_NORMALIZED',	'Normalized load on AC compressor');
# $348
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(85,'03','348','','2-15,M=1/32',	'SPEED_WHEEL_LD',	'Speed of left driven wheel kph');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(86,'03','348','','18-31,M=1/32',	'SPEED_WHEEL_RD',	'Speed of right driven wheel kph');
# $34A
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(87,'03','34A','','2-15,M=1/32',	'SPEED_WHEEL_LND',	'Speed of left non-driven driven wheel kph');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(88,'03','34A','','2-15,M=1/32',	'SPEED_WHEEL_RND',	'Speed of right non-driven wheel kph');
# $3D1
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(89,'03','3D1','','8-15,MULT2051',	'THROTTLE_POSITION',	'Throttle position %');
# $4D1
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(90,'03','4D1','','28-39,M=1/8',	'FUEL_TANK_CAPACITY',	'Fuel tank capacity in Liters');
# Door ajar
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(91,'01','10 03 60 40','','6-6,BIT',	'DOOR_OPEN_DRIVER',	'True/1 if the driver door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(92,'01','10 03 60 40','','7-7,BIT',	'DOOR_AJAR_DRIVER',	'True/1 if the driver door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(93,'01','10 03 80 40','','6-6,BIT',	'DOOR_OPEN_PASSENGER',	'True/1 if the passenger door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(94,'01','10 03 80 40','','7-7,BIT',	'DOOR_AJAR_PASSENGER',	'True/1 if the passenger door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(95,'01','10 03 A0 40','','6-6,BIT',	'DOOR_OPEN_RL',	'True/1 if the rear left door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(96,'01','10 03 A0 40','','7-7,BIT',	'DOOR_AJAR_RL',	'True/1 if the rear left door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(97,'01','10 03 C0 40','','6-6,BIT',	'DOOR_OPEN_RR',	'True/1 if the rear right door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(98,'01','10 03 C0 40','','7-7,BIT',	'DOOR_AJAR_RR',	'True/1 if the rear right door is ajar');
# 
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(99,'03','12A','','10-10,BIT',	'REAR_OPEN',	'True if the rear is open (trunk?)');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(100,'03','12A','','11-11,BIT',	'REAR_AJAR',	'True if the rear is ajar (trunk?)');

INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(101,'03','12A','','12-12,BIT',	'TCS_DISABLE',	'Traction control system disabled flag');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(102,'03','12A','','15-15,BIT',	'BREAK_FLUID_LOW',	'True if the break fluid level is low');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(103,'03','12A','','16-16,BIT',	'DOOR_OPEN_RR',	'True/1 if the rear right door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(104,'03','12A','','17-17,BIT',	'DOOR_AJAR_RR',	'True/1 if the rear right door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(105,'03','12A','','18-18,BIT',	'DOOR_OPEN_RL',	'True/1 if the rear left door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(106,'03','12A','','19-19,BIT',	'DOOR_AJAR_RL',	'True/1 if the rear left door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(107,'03','12A','','20-20,BIT',	'DOOR_OPEN_PASSENGER',	'True/1 if the passenger door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(108,'03','12A','','21-21,BIT',	'DOOR_AJAR_PASSENGER',	'True/1 if the passenger door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(109,'03','12A','','22-22,BIT',	'DOOR_OPEN_DRIVER',	'True/1 if the driver door is open');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(110,'03','12A','','23-23,BIT',	'DOOR_AJAR_DRIVER',	'True/1 if the driver door is ajar');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(111,'03','12A','','29-31,HEX',	'DRIVER_ID',	'7 if unknown, otherwise ID# of driver 0-6');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(112,'03','3FD','','8-15,M=1,B=-40',	'TEMP_TRANSFER_CASE',	'Temperature of the transfer case C');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(113,'03','3FD','','16-23,M=1,B=-40',	'TEMP_TRANSFER_CLUTCH',	'Temperature of the transfer case clutch C');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(114,'03','3E9','','1-15,DIV64',	'SPEED_AVERAGE', 'Average Speed kph');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(115,'03','3F1','','8-15,MULT2051',	'GENERATOR_SETPOINT_DUTY_CYCLE', 'Generator setpoint duty cycle %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(116,'03','4C1','','7-7,BIT',	'ENGINE_WARMED_UP', 'Engine warmed up');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(117,'03','1A1','','14-15,INT',	'CYLINDER_DEACTIVATION_MODE', '0=all active, 1=in progress, 2=half deactivated 3=reactivation in progress');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(118,'03','1F1','','6-7,INT',	'SYSTEM_POWER_MODE', 'system power mode: 0-3: off, accessory, run, crank.');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(119,'01','10 02 20 40','','0-7,MULT2051',	'INTERIOR_DIM_LEVEL', 'Interior dimming level %');
INSERT INTO "dataPoint"	(id,network,header,sigBytes,formula,dataName,description) 		VALUES(120,'01','10 02 20 40','','8-15,MULT2051',	'INTERIOR_DISPLAY_DIM_LEVEL', 'Interior display dimming level %');

# one record per ELM response, which can contain multiple responses either from multiple ECUs or the same ECU (such as the case of a long message split across multiple frames). 
DROP TABLE IF EXISTS obdRequest;
CREATE TABLE obdRequest (id INTEGER PRIMARY KEY AUTOINCREMENT,scannable INTEGER,dataPointName TEXT, dataShortName TEXT, request TEXT, formula TEXT, description TEXT,frequency INTEGER,lastused INTEGER,numuses INTEGER, minValue INTEGER, maxValue INTEGER, numDataBytes INTEGER);
CREATE INDEX idx2_obdRequest on obdRequest (request);
CREATE INDEX idx3_obdRequest on obdRequest (id);

# Expose field issues with this: $ grep -i "insert.into..obdrequest" /tmp/schema.sql |perl -lne '@fields=split (/,/); $count=@fields; print "C=$count 4=$fields[3] $_"; '|sort

# BIG obdRequest population cruft here. 
INSERT INTO obdRequest VALUES(1,0,'PIDS_01_0120'	,'','0100','BINARY','PIDs Supported 0-20',9999,NULL,NULL,0,NULL,4);
INSERT INTO obdRequest VALUES(2,0,'MILDTCINFO'	,'','0101','BINARY','MIL/DTC Bit Encoded Info',9999,NULL,NULL,0,NULL,4);
INSERT INTO obdRequest VALUES(3,0,'FREEZEDTC'		,'','0102',NULL,'Freeze DTC',9999,NULL,NULL,0,NULL,8);
INSERT INTO obdRequest VALUES(4,0,'FUELSTAT'		,'','0103','BINARY','Fuel System Status',9999,NULL,NULL,0,NULL,2);
INSERT INTO obdRequest VALUES(5,1,'ENGINE_CALCULATED_LOAD','Load %','0104','A*100,X/255','Calculated Engine Load Value',1,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(6,1,'TEMP_COOLANT'	,'Coolant C','0105','A-40','Engine Coolant Temperature',10,NULL,NULL,-40,215,1);
INSERT INTO obdRequest VALUES(7,1,'TRIM_SHORT_BANK1'	,'TrimSB1','0106','A-128,X*100,X/128','Short term fuel % trim—Bank 1',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(8,1,'TRIM_LONG_BANK1'	,'TrimLB1','0107','A-128,X*100,X/128','Long term fuel % trim—Bank 1',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(9,1,'TRIM_SHORT_BANK2'	,'TrimSB2','0108','A-128,X*100,X/128','Short term fuel % trim—Bank 2',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(10,1,'TRIM_LONG_BANK2'	,'TrimLB2','0109','A-128,X*100,X/128','Long term fuel % trim—Bank 2',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(11,1,'FUEL_PRESSURE'	,'Fuel Press','010A','A*3','Fuel Pressure',1,NULL,NULL,0,765,1);
INSERT INTO obdRequest VALUES(12,1,'IMAP'		,'IMAP','010B','A','Intake Manifold Absolute Pressure',5,NULL,NULL,0,255,1);
INSERT INTO obdRequest VALUES(13,1,'RPM'		,'RPM','010C','A*256,X+B,X/4','Engine RPM ',1,NULL,NULL,0,9000,2);
INSERT INTO obdRequest VALUES(14,1,'SPEED'		,'Speed','010D','A','Vehicle Speed',1,NULL,NULL,0,250,1);
INSERT INTO obdRequest VALUES(15,1,'TIMING_ADVANCE'	,'Advance','010E','A/2,X-64','Timing advance degrees relative to cyl1',9999,NULL,NULL,-64,64,1);
INSERT INTO obdRequest VALUES(16,1,'TEMP_INTAKE'	,'Intake C','010F','A-40','Intake Air Temperature',6,NULL,NULL,-40,215,1);
INSERT INTO obdRequest VALUES(17,1,'MAF_FLOW_RATE'	,'MAF','0110','A*256,X+B,X/100','MAF Air Flow Rate',6,NULL,NULL,0,656,2);
INSERT INTO obdRequest VALUES(18,1,'TPS'		,'TPS','0111','A*100,X/255','Throttle Position',2,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(19,0,'AIR_STATUS_SECONDARY','','0112','BINARY','Commanded secondary air status',9999,NULL,NULL,0,NULL,1);
INSERT INTO obdRequest VALUES(20,0,'O2SENSORS'	,'','0113','BINARY','Oxygen sensors present (Bit Encoded)',9999,NULL,NULL,0,NULL,1);
INSERT INTO obdRequest VALUES(21,1,'O2B1S1'		,'O2B1S1 %','0114','B-128,X*100,X/128','B1S1 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(22,1,'O2B1S2'		,'O2B1S2 %','0115','B-128,X*100,X/128','B1S2 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(23,1,'O2B1S3'		,'O2B1S3 %','0116','B-128,X*100,X/128','B1S3 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(24,1,'O2B1S4'		,'O2B1S4 %','0117','B-128,X*100,X/128','B1S4 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(25,1,'O2B2S1'		,'O2B2S1 %','0118','B-128,X*100,X/128','B2S1 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(26,1,'O2B2S2'		,'O2B2S2 %','0119','B-128,X*100,X/128','B2S2 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(27,1,'O2B2S3'		,'O2B2S3 %','011A','B-128,X*100,X/128','B2S3 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(28,1,'O2B2S4'		,'O2B2S4 %','011B','B-128,X*100,X/128','B2S4 O2 Sensor percent',9999,NULL,NULL,-100,100,2);
INSERT INTO obdRequest VALUES(29,0,'OBDSTANDARD'	,'','011C','BINARY','OBD Standard This Vehicle Conforms To',9999,NULL,NULL,0,NULL,1);
INSERT INTO obdRequest VALUES(30,0,'O2SENSORS2'	,'','011D','BINARY','O2 Sensors Present (Bit Encoded)',9999,NULL,NULL,0,NULL,1);
INSERT INTO obdRequest VALUES(31,0,'AUXINSTATUS'	,'','011E','BINARY','AUX Input Status',9999,NULL,NULL,0,NULL,1);
INSERT INTO obdRequest VALUES(32,1,'ENGINE_RUNTIME'	,'Run Secs','011F','A*256,X+B','Run Time Since Engine Start',60,NULL,NULL,0,65535,2);
INSERT INTO obdRequest VALUES(33,0,'PIDS_01_2140'	,'','0120','BINARY','PIDS Supported 21-40',9999,NULL,NULL,0,NULL,4);
INSERT INTO obdRequest VALUES(34,1,'DISTANCE_MIL_ON'	,'','0121','A*256,X+B','Distance Travelled with MIL Light On',9999,NULL,NULL,0,65535,2);
INSERT INTO obdRequest VALUES(35,1,'PRESSURE_FUEL_RAIL','Rail kPa','0122','A*256,X+B,X*0.079','Fuel Rail Pressure Relative To Manifold Vacuum',15,NULL,NULL,0,5178,2);
INSERT INTO obdRequest VALUES(36,1,'DIESELRAIL'		,'Rail kPa','0123','A*256,X+B,X*10','Diesel Fuel Rail Pressure',15,NULL,NULL,0,NULL,2);
INSERT INTO obdRequest VALUES(37,1,'LAMBDA_V_O2S1'	,'LamV 02S1','0124','C*256,X+D,X/8192','O2S1_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(38,1,'LAMBDA_V_O2S2'	,'LamV 02S2','0125','C*256,X+D,X/8192','O2S2_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(39,1,'LAMBDA_V_O2S3'	,'LamV 02S3','0126','C*256,X+D,X/8192','O2S3_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(40,1,'LAMBDA_V_O2S4'	,'LamV 02S4','0127','C*256,X+D,X/8192','O2S4_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(41,1,'LAMBDA_V_O2S5'	,'LamV 02S5','0128','C*256,X+D,X/8192','O2S5_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(42,1,'LAMBDA_V_O2S6'	,'LamV 02S6','0129','C*256,X+D,X/8192','O2S6_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(43,1,'LAMBDA_V_O2S7'	,'LamV 02S7','012A','C*256,X+D,X/8192','O2S7_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(44,1,'LAMBDA_V_O2S8'	,'LamV 02S8','012B','C*256,X+D,X/8192','O2S8_WR_lambda(1) Equiv Ratio Volts',15,NULL,NULL,0,8,4);
INSERT INTO obdRequest VALUES(45,1,'EGR_COMMANDED'	,'EGR %','012C','A*100,X/255','Commanded EGR',5,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(46,1,'EGR_ERROR'		,'EGR Err%','012D','A*0.78125,X-100','EGR Error',9999,NULL,NULL,-100,100,1);
INSERT INTO obdRequest VALUES(47,1,'EVAP_COMMANDED_PURGE','Evap Press','012E','A*100,X/255','Commanded Evaporative Purge',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(48,1,'FUEL_LEVEL'		,'Fuel %','012F','A*100,X/255','Fuel Level Input',10,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(49,1,'WARMUPS_SINCE_DTC_CLEAR'	,'Warmups','0130','A','Number of warmups since DTCs Cleared',9999,NULL,NULL,0,255,1);
INSERT INTO obdRequest VALUES(50,1,'DIST_SINCE_DTC_CLEAR'	,'Miles','0131','A*256,X+B','Distance traveled since DTCs cleared',9999,NULL,NULL,0,65535,2);
INSERT INTO obdRequest VALUES(51,1,'EVAP_VAPOR_PRESSURE'	,'Evap Pa','0132','A*256,X+B,X/4,X-8192','Evap. System Vapor Pressure',9999,NULL,NULL,-8192,8192,2);
INSERT INTO obdRequest VALUES(52,1,'BAROMETER'		,'Baro kPa','0133','A','Barometric Pressure',20,NULL,NULL,0,255,1);
INSERT INTO obdRequest VALUES(53,1,'LAMBDA_C_O2S1'	,'LamC 02S1','0134','C*256,X+D,X/256,X-128','O2S1_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(54,1,'LAMBDA_C_O2S2'	,'LamC 02S2','0135','C*256,X+D,X/256,X-128','O2S2_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(55,1,'LAMBDA_C_O2S3'	,'LamC 02S3','0136','C*256,X+D,X/256,X-128','O2S3_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(56,1,'LAMBDA_C_O2S4'	,'LamC 02S4','0137','C*256,X+D,X/256,X-128','O2S4_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(57,1,'LAMBDA_C_O2S5'	,'LamC 02S5','0138','C*256,X+D,X/256,X-128','O2S5_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(58,1,'LAMBDA_C_O2S6'	,'LamC 02S6','0139','C*256,X+D,X/256,X-128','O2S6_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(59,1,'LAMBDA_C_O2S7'	,'LamC 02S7','013A','C*256,X+D,X/256,X-128','O2S7_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(60,1,'LAMBDA_C_O2S8'	,'LamC 02S8','013B','C*256,X+D,X/256,X-128','O2S8_WR_lambda(1) Equiv Ratio milliamps',9999,NULL,NULL,-128,128,4);
INSERT INTO obdRequest VALUES(61,1,'CATALYST_TEMP_B1S1'	,'Cat B1S1 C','013C','A*256,X+B,X/10,X-40','B1S1 Catalyst Temperature',9999,NULL,NULL,-40,215,2);
INSERT INTO obdRequest VALUES(62,1,'CATALYST_TEMP_B2S1'	,'Cat B2S1 C','013D','A*256,X+B,X/10,X-40','B2S1 Catalyst Temperature',9999,NULL,NULL,-40,215,2);
INSERT INTO obdRequest VALUES(63,1,'CATALYST_TEMP_B1S2'	,'Cat B1S2 C','013E','A*256,X+B,X/10,X-40','B1S2 Catalyst Temperature',9999,NULL,NULL,-40,215,2);
INSERT INTO obdRequest VALUES(64,1,'CATALYST_TEMP_B2S2'	,'Cat B2S2 C','013F','A*256,X+B,X/10,X-40','B2S2 Catalyst Temperature',9999,NULL,NULL,-40,215,2);
INSERT INTO obdRequest VALUES(65,0,'PIDS_01_4160'			,'','0140','BINARY','PIDs supported 41-60',9999,NULL,NULL,0,NULL,4);
INSERT INTO obdRequest VALUES(66,0,'MONITOR_STATUS'		,'','0141','BINARY','Monitor Status This Drive Cycle',9999,NULL,NULL,0,NULL,4);
INSERT INTO obdRequest VALUES(67,1,'VOLTAGE_CONTROLMODULE','CM Volts','0142','A*256,X+B,X/1000','Control Module Voltage',30,NULL,NULL,0,15,2);
INSERT INTO obdRequest VALUES(68,1,'LOAD_ABSOLUTE'	,'Load Abs','0143','A*256,X+B,X*100,X/255','Absolute Load Value',1,NULL,NULL,0,100,2);
INSERT INTO obdRequest VALUES(69,1,'COMMAND_EQUIVALENCE_RATIO'	,'Eq Ratio','0144','A*256,X+B,X*305,X/10000000','Command Equivalence Ratio',9999,NULL,NULL,0,2,2);
INSERT INTO obdRequest VALUES(70,1,'THROTTLE_POSITION_RELATIVE'	,'TPS Rel','0145','A*100,X/255','Relative Throttle Position',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(71,1,'TEMP_AIR_AMBIENT'	,'Ambient C','0146','A-40','Ambient Air Temperature',10,NULL,NULL,-40,215,1);
INSERT INTO obdRequest VALUES(72,1,'TPS_B'	,'TPS_B','0147','A*100,X/255','Absolute Throttle Position B',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(73,1,'TPS_C'	,'TPS_C','0148','A*100,X/255','Absolute Throttle Position C',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(74,1,'TPS_D'	,'TPS_D','0149','A*100,X/255','Absolute Throttle Position D',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(75,1,'TPS_E'	,'TPS_E','014A','A*100,X/255','Absolute Throttle Position E',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(76,1,'TPS_F'	,'TPD_F','014B','A*100,X/255','Absolute Throttle Position F',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(77,1,'THROTTLE_COMMANDED'	,'Throttle','014C','A*100,X/255','Commanded Throttle Actuator',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(78,0,'TIME_RUN_MIL_ON'	,'TimeMilON','014D','A*256,X+B','Time Run with MIL Light On',60,NULL,NULL,0,65535,2);
INSERT INTO obdRequest VALUES(79,0,'TIME_SINCE_MIL_CLEAR'	,'DTCClearS','014E','A*256,X+B','Time Since Trouble Codes Cleared',9999,NULL,NULL,0,65535,2);
INSERT INTO obdRequest VALUES(80,0,''		,'','014F',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(81,0,''		,'','0150',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(82,0,''		,'','0151',NULL,'Fuel Type',9999,NULL,NULL,0,NULL,1);
INSERT INTO obdRequest VALUES(83,0,'FUEL_ETHANOL_PCT','Ethanol %','0152','A*100,X/255','Ethanol Fuel %',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(84,0,''		,'','0153',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(85,0,''		,'','0154',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(86,0,''		,'','0155',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(87,0,''		,'','0156',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(88,0,''		,'','0157',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(89,0,''		,'','0158',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(90,0,''		,'','0159',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(91,0,''		,'','01C0',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(92,0,''		,'','01C1',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(93,0,''		,'','01C2',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(94,0,''		,'','01C3',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(95,0,''		,'','01C4',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(96,0,''		,'','01C5',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(97,0,''		,'','01C6',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(98,0,''		,'','01C7',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(99,0,''		,'','01C8',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(100,0,''	,'','01C9',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(101,0,''	,'','01CA',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(102,0,''	,'','01CB',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(103,0,''	,'','01CC',NULL,'',9999,NULL,NULL,0,NULL,0);
INSERT INTO obdRequest VALUES(104,0,'DTC'	,'','03','DTC','DTC/Trouble Codes',60,NULL,NULL,NULL,NULL,0);
INSERT INTO obdRequest VALUES(105,0,'DTC_RESET'	,'','04',NULL,'Clear Trouble Codes',60,NULL,NULL,NULL,NULL,0);
INSERT INTO obdRequest VALUES(106,0,'PIDS_09_0120'	,'','0900','BINARY','Mode 9 Supported PIDs 1-20',9999,NULL,NULL,NULL,NULL,4);
INSERT INTO obdRequest VALUES(107,0,'VIN'		,'','0902','VIN','Vehicle VIN',9999,NULL,NULL,NULL,NULL,17);
INSERT INTO obdRequest VALUES(108,1,'VOLTS'		,'OBD Volts','ATRV',NULL,'Battery Voltage',20,NULL,NULL,0,15,0);
INSERT INTO obdRequest VALUES(109,0,'ELM_VERSION'	,'','ATI',NULL,'ELM327 Chip Version',9999,NULL,NULL,NULL,NULL,0);
INSERT INTO obdRequest VALUES(110,0,'PROTO'		,'','ATDPN',NULL,'Describe OBDii Protocol by number. See ELM327DS',9999,NULL,NULL,NULL,NULL,0);
INSERT INTO obdRequest VALUES(111,0,'FREEZE_FRAME_DTC','','0202',NULL,'Freeze frame trouble code',9999,NULL,NULL,NULL,NULL,2);
# HACK: mode 22 (actually, just 6-character OBD requests) is sort of broken right now, ABCD are shifted by one, so instead of A use B, etc... for now. 
INSERT INTO obdRequest VALUES(112,1,'GM_MISFIRE_CNT','Misfires','221200','B','GM Misfire count. Reset every 100-200 revolutions.',9999,NULL,NULL,0,100,1);
INSERT INTO obdRequest VALUES(113,1,'GM_TEMP_OIL','TempC','221602','B+40,X*256,X/205','GM OIL Temp',9999,NULL,NULL,0,300,1);
INSERT INTO obdRequest VALUES(114,1,'GM_TEMP_TRANNY','TempC','221603','B+40,X*3,X/4','GM Transmission Temp',9999,NULL,NULL,0,300,1);
INSERT INTO obdRequest VALUES(115,1,'GM_GEAR','GEAR','221958','A','GM Transmission Gear',9999,NULL,NULL,0,12,1);
INSERT INTO obdRequest VALUES(116,1,'GM_TRANNY_MOTOR_STATUS','BITWISE','1978','A','GM Transmission Motor Status',9999,NULL,NULL,0,255,1);
INSERT INTO obdRequest VALUES(117,1,'GM_KNOCK_RETARD','Knock Deg.','2211A6','B*256,X/23','GM Knock Retard in Degrees',9999,NULL,NULL,0,360,1);
INSERT INTO obdRequest VALUES(118,1,'GM_SUPER_BOOST','Boost%','221174','A*100,X/256','GM Turbocharger Boost %',9999,NULL,NULL,0,200,1);


# insert oil temp and tranny temp PIDs here. 

# END OF Big obdRequest population cruft. 

# commands for controlling things via SWCAN OBD (GM Cars). 
# to insert a pause (sleep) use "SX" to sleep for XX * 1/10 second/s. ex: S5 - sleeps for half a second (5 *100 milliseconds).
INSERT INTO command (id,name,network,command, description) VALUES (1,'VOL_UP','01','10 0D 00 40 03 00; 10 0D 00 40 00 00','Stereo Volume Up');
INSERT INTO command (id,name,network,command, description) VALUES (2,'VOL_DOWN','01','10 0D 00 40 02 00; 00 00','Stereo Volume Down');
# Volume Down once to make sure its not already muted, then immediately mute. 
INSERT INTO command (id,name,network,command, description) VALUES (3,'VOL_MUTE','01','10 0D 00 40 02 00; 10 0D 00 40 00 00; 10 0D 00 40 01 00; 10 0D 00 40 00 00','Stereo Mute');
INSERT INTO command (id,name,network,command, description) VALUES (4,'VOL_UNMUTE','01','10 0D 00 40 03 00; 10 0D 00 40 00 00','Stereo Unmute');
# Keyfob controls
INSERT INTO command (id,name,network,command, description) VALUES (5,'FOB_LOCK'   ,'01','08 00 80 B0 02 01',			'Keyfob Lock Doors');
INSERT INTO command (id,name,network,command, description) VALUES (6,'FOB_UNLOCK' ,'01','08 00 80 B0 02 02',			'Keyfob Unlock Driver Door');
INSERT INTO command (id,name,network,command, description) VALUES (7,'FOB_TRUNK'  ,'01','08 00 80 B0 02 04',			'Keyfob Trunk Open');
INSERT INTO command (id,name,network,command, description) VALUES (8,'FOB_STARTER','01','08 00 80 B0 02 01; 02 0C',	'Keyfob Remote Starter');
# Heated seat button works on a button-down, button-up principle just like the steering wheel controls. 
INSERT INTO command (id,name,network,command, description) VALUES (9,'HEAT_SEAT_DRIVER',    '01','10 2A 00 99 04; 00; 00',	'Driver heated seat button');
INSERT INTO command (id,name,network,command, description) VALUES (10,'HEAT_SEAT_PASSENGER','01','10 2A 40 99 04; 00; 00',	'Passenger heated seat button');

