package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.NodeReportState
import com.adaptc.mws.plugins.natives.utils.NativeUtils
import com.adaptc.mws.plugins.testing.PluginUnitTestMixin
import com.adaptc.mws.plugins.testing.TestFor
import com.adaptc.mws.plugins.testing.TestMixin
import spock.lang.Specification
import spock.lang.Unroll

import static com.adaptc.mws.plugins.PluginConstants.*
import com.adaptc.mws.plugins.StorageReport

@Unroll
@TestFor(StorageNativeTranslator)
@TestMixin(PluginUnitTestMixin)
class StorageNativeTranslatorSpec extends Specification {
	def "Wiki to domain"() {
		given:
		GenericNativeTranslator genericNativeTranslator = Mock()
		translator.genericNativeTranslator = genericNativeTranslator

		and:
		long time = 12348473
		
		when:
		def wiki = "storage1 StaTE=${NodeReportState.IDLE}:subs" +
				";UPdaTETIME=${(time).toLong()}"+
				";ArES=ares"+
				";CrES=cres"+
				";CpROC=4"+
				";ApROC=2"+
				";CsWAP=512"+
				";AsWAP=128"+
				";NeTADDR=10.0.0.1"+
				";CmEMORY=1024"+
				";AmEMORY=256"+
				";CdISK=1000"+
				";AdISK=512"+
				";SpEED=1.2"+
				";CpULOAD=1.2"+
				";MeSSAGE=message1"+
				';MeSSAGE="message 2"'+
				";Type=storage"+	// Ignored
				";VaRATTR=attr1:val1+attr2=val2+attr3+attr4"
		StorageReport storage = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])
		
		then:
		storage
		1 * genericNativeTranslator.getGenericMap("ares") >> [res1:"1"]
		1 * genericNativeTranslator.getGenericMap("cres") >> [res2:"2"]
		1 * genericNativeTranslator.getGenericMapWithDisplayValue("attr1:val1+attr2=val2+attr3+attr4", "\\+", ":|=") >>
				[attr1:[value:"val1", displayValue: "value one"]]
		0 * _._
		
		and:	
		storage.name=="storage1"
		storage.state==NodeReportState.IDLE
		storage.subState=="subs"
		storage.ipAddress=="10.0.0.1"
		storage.timestamp==new Date(time*1000)
		storage.resources[RESOURCE_PROCESSORS].total==4
		storage.resources[RESOURCE_PROCESSORS].available==2
		storage.resources[RESOURCE_MEMORY].total==1024
		storage.resources[RESOURCE_MEMORY].available==256
		storage.resources[RESOURCE_DISK].total==1000
		storage.resources[RESOURCE_DISK].available==512
		storage.resources[RESOURCE_SWAP].total==512
		storage.resources[RESOURCE_SWAP].available==128
		storage.metrics[METRIC_CPULOAD]==1.2
		storage.metrics[METRIC_SPEED]==1.2d
		storage.metrics.size()==2
		storage.messages.size()==2
		storage.messages[0]=="message1"
		storage.messages[1]=="message 2"
		storage.variables.size()==0
		
		and:
		storage.resources.size()==6
		storage.resources["res1"].available==1
		storage.resources["res2"].total==2

		and:
		storage.attributes.size()==1
		storage.attributes.attr1.value=="val1"
		storage.attributes.attr1.displayValue=="value one"
	}

	def "Wiki to domain null values handled correctly"() {
		given:
		long time = 12348473

		when:
		def wiki = "storage1 STATE=${NodeReportState.IDLE}" +
				";UPDATETIME=${(time).toLong()}"
		StorageReport storage = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		storage.name=="storage1"
		storage.state==NodeReportState.IDLE
		storage.subState==null
		storage.ipAddress==null
		storage.timestamp==new Date(time*1000)
		storage.resources.size()==0
		storage.metrics.size()==0
		storage.messages.size()==0
		storage.variables.size()==0
		storage.attributes.size()==0
	}

	def "Floating point update time is handled correctly"() {
		given:
		String time = "1363865607.000"

		when:
		def wiki = "storage1 STATE=$NodeReportState.IDLE;UPDATETIME=$time"
		StorageReport storage = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		storage.name == "storage1"
		storage.state == NodeReportState.IDLE
		storage.timestamp.time == 1363865607000
	}

	def "Migration disabled flag for '#migrationWiki' and attributes '#attrWiki' is #result"() {
		given:
		translator.genericNativeTranslator = mockTranslator(GenericNativeTranslator)

		when:
		def wiki = "storage1 "+
				migrationWiki
		StorageReport storage = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		storage?.name=="storage1"
		storage.migrationDisabled==result
		storage.attributes.size()==attributesSize

		where:
		migrationWiki				|| attributesSize	| result
		""							|| 0				| null
		"mIGRATIONDISABLED=true"	|| 0				| true
		"mIGRATIONDISABLED=false"	|| 0				| false
		"mIGRATIONDISABLED=1"		|| 0				| true
		"mIGRATIONDISABLED=0"		|| 0				| false
		"mIGRATIONDISABLED="		|| 0				| false
	}

	def "Notifications for invalid attributes"() {
		given:
		IPluginEventService pluginEventService = Mock()

		when:
		def object = translator.createReport(pluginEventService, [id:"id1", bogus1:"value", bogus2:"value2"])

		then:
		object.name=="id1"

		and:
		2 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"storageNativeTranslator.invalid.attribute", {it.type=="Storage" && it.id=="id1" }, null)
		0 * _._
	}

	def "Lower-case names is #lowerCase (#id converted to #name)"() {
		given:
		translator.lowerCaseNames = lowerCase

		expect:
		translator.createReport(null, [id:id]).name==name

		cleanup:
		translator.lowerCaseNames = true

		where:
		lowerCase	| id		|| name
		true		| "ID"		|| "id"
		true		| "id"		|| "id"
		true		| "iD"		|| "id"
		false		| "ID"		|| "ID"
		false		| "id"		|| "id"
		false		| "iD"		|| "iD"
	}

	def "Slave flag"() {
		when:
		def wiki = "storage1 "+slaveWiki
		StorageReport storage = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		storage?.name=="storage1"
		storage.slaveReport==result

		where:
		slaveWiki			|| result
		""					|| false
		"SLAVE=true"		|| true
		"SLAVE=false"		|| false
		"SLAVE=1"			|| true
		"SLAVE=0"			|| false
		"SLAVE="			|| false
	}
}
