CREATE TABLE "OSK"."REG_MAIL" 
   (	"USER_Z" VARCHAR2(100) NOT NULL ENABLE, 
	"DATA_Z" DATE NOT NULL ENABLE, 
	"FAM" VARCHAR2(20) NOT NULL ENABLE, 
	"NAM" VARCHAR2(20) NOT NULL ENABLE, 
	"FAT" VARCHAR2(20) NOT NULL ENABLE, 
	"RD" VARCHAR2(2) NOT NULL ENABLE, 
	"BM" VARCHAR2(2) NOT NULL ENABLE, 
	"BY" VARCHAR2(4) NOT NULL ENABLE
   );
CREATE TABLE "OSK"."OFFLINE_USERS" 
   (	"USER_NAME" NVARCHAR2(100), 
	"ID" NUMBER
   );
