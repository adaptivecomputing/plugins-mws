package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.NodeReport
import com.adaptc.mws.plugins.NodeReportState
import com.adaptc.mws.plugins.natives.utils.NativeUtils
import com.adaptc.mws.plugins.testing.PluginUnitTestMixin
import com.adaptc.mws.plugins.testing.TestFor
import com.adaptc.mws.plugins.testing.TestMixin
import spock.lang.Specification
import spock.lang.Unroll

import static com.adaptc.mws.plugins.PluginConstants.*

@Unroll
@TestFor(NodeNativeTranslator)
@TestMixin(PluginUnitTestMixin)
class NodeNativeTranslatorSpec extends Specification {
	def "Wiki to domain"() {
		given:
		GenericNativeTranslator genericNativeTranslator = Mock()
		translator.genericNativeTranslator = genericNativeTranslator

		and:
		long time = 12348473
		
		when:
		def wiki = "node1 STATE=${NodeReportState.IDLE}:subs" +
				";UPDATETIME=${(time).toLong()}"+
				";ARES=ares"+
				";CRES=cres"+
				";CPROC=4"+
				";APROC=2"+
				";NETADDR=10.0.0.1"+
				";CMEMORY=1024"+
				";AMEMORY=256"+
				";CDISK=1000"+
				";ADISK=512"+
				";SPEED=1.2"+
				";CPULOAD=1.2"+
				";MESSAGE=message1"+
				';MESSAGE="message 2"'+
				";OS=linux"+
				";VMOSLIST=cent5,cent6"+
                ";OSLIST=linux,windows"+
				";RACK=4"+
				";SLOT=2"+
				";VARATTR=HVTYPE=esx+attr1:val1+attr2=val2+attr3+attr4"
		def imageInfo = new HVImageInfo()
		NodeReport node = translator.createReport(NativeUtils.parseWiki([wiki])[0], imageInfo)
		
		then:
		node
		1 * genericNativeTranslator.getGenericMap("ares") >> [res1:"1"]
		1 * genericNativeTranslator.getGenericMap("cres") >> [res2:"2"]
		1 * genericNativeTranslator.getGenericMapWithDisplayValue("HVTYPE=esx+attr1:val1+attr2=val2+attr3+attr4", "\\+", ":|=") >>
				[HVTYPE:[value:"esx"],attr1:[value:"val1", displayValue: "value one"]]
		0 * _._
		
		and:	
		node.name=="node1"
		node.state==NodeReportState.IDLE
		node.subState=="subs"
		node.ipAddress=="10.0.0.1"
		node.timestamp==new Date(time*1000)
		node.resources[RESOURCE_PROCESSORS].total==4
		node.resources[RESOURCE_PROCESSORS].available==2
		node.resources[RESOURCE_MEMORY].total==1024
		node.resources[RESOURCE_MEMORY].available==256
		node.resources[RESOURCE_DISK].total==1000
		node.resources[RESOURCE_DISK].available==512
		node.metrics[METRIC_CPULOAD]==1.2
		node.metrics[METRIC_SPEED]==1.2d
		node.metrics.size()==2
		node.messages.size()==2
		node.messages[0]=="message1"
		node.messages[1]=="message 2"
		node.image=="linux"
        node.imagesAvailable.size()==2
        node.imagesAvailable[0]=="linux"
        node.imagesAvailable[1]=="windows"
		node.variables.size()==0
		
		and:
		node.resources.size()==6
		node.resources["res1"].available==1
		node.resources["res2"].total==2

		and:
		node.attributes.size()==1
		node.attributes.attr1.value=="val1"
		node.attributes.attr1.displayValue=="value one"

		and:
		imageInfo.nodeName=="node1"
		imageInfo.name=="linux"
		imageInfo.hypervisorType=="esx"
		imageInfo.vmImageNames.size()==2
		imageInfo.vmImageNames.contains("cent5")
		imageInfo.vmImageNames.contains("cent6")
	}

	def "Wiki to domain null values handled correctly"() {
		given:
		GenericNativeTranslator genericNativeTranslator = Mock()
		translator.genericNativeTranslator = genericNativeTranslator

		and:
		long time = 12348473

		when:
		def wiki = "node1 STATE=${NodeReportState.IDLE}" +
				";UPDATETIME=${(time).toLong()}"
		def imageInfo = new HVImageInfo()
		NodeReport node = translator.createReport(NativeUtils.parseWiki([wiki])[0], imageInfo)

		then:
		2 * genericNativeTranslator.getGenericMap(null) >> null
		1 * genericNativeTranslator.getGenericMapWithDisplayValue(null, "\\+", ":|=")
		0 * _._

		and:
		node.name=="node1"
		node.state==NodeReportState.IDLE
		node.subState==null
		node.ipAddress==null
		node.timestamp==new Date(time*1000)
		node.resources[RESOURCE_PROCESSORS].total==null
		node.resources[RESOURCE_PROCESSORS].available==null
		node.resources[RESOURCE_MEMORY].total==null
		node.resources[RESOURCE_MEMORY].available==null
		node.resources[RESOURCE_DISK].total==null
		node.resources[RESOURCE_DISK].available==null
		node.metrics[METRIC_CPULOAD]==null
		node.metrics[METRIC_SPEED]==null
		node.metrics.size()==2
		node.image==null
		node.imagesAvailable.size()==0
		node.messages.size()==0
		node.variables.size()==0
		node.attributes.size()==0

		and:
		imageInfo.nodeName=="node1"
		imageInfo.name==null
		imageInfo.vmImageNames.size()==0
		imageInfo.hypervisorType==null
	}

	def "Floating point update time is handled correctly"() {
		given:
		GenericNativeTranslator genericNativeTranslator = Mock()
		translator.genericNativeTranslator = genericNativeTranslator

		and:
		String time = "1363865607.000"

		when:
		def wiki = "node1 STATE=$NodeReportState.IDLE;UPDATETIME=$time"
		NodeReport node = translator.createReport(NativeUtils.parseWiki([wiki])[0], new HVImageInfo())

		then:
		2 * genericNativeTranslator.getGenericMap(null) >> null
		1 * genericNativeTranslator.getGenericMapWithDisplayValue(null, "\\+", ":|=")
		0 * _._

		and:
		node.name == "node1"
		node.state == NodeReportState.IDLE
		node.timestamp.time == 1363865607000
	}

	def "Migration disabled flag for '#migrationWiki' and attributes '#attrWiki' is #result"() {
		given:
		translator.genericNativeTranslator = mockTranslator(GenericNativeTranslator)

		when:
		def wiki = "node1 "+
				migrationWiki+
				attrWiki
		NodeReport node = translator.createReport(NativeUtils.parseWiki([wiki])[0], new HVImageInfo())

		then:
		node?.name=="node1"
		node.migrationDisabled==result
		node.attributes.size()==attributesSize

		where:
		migrationWiki				| attrWiki							|| attributesSize	| result
		""							| ""								|| 0				| null
		"MIGRATIONDISABLED=true"	| ""								|| 0				| true
		"MIGRATIONDISABLED=false"	| ""								|| 0				| false
		"MIGRATIONDISABLED=1"		| ""								|| 0				| true
		"MIGRATIONDISABLED=0"		| ""								|| 0				| false
		"MIGRATIONDISABLED="		| ""								|| 0				| false
		"MIGRATIONDISABLED=true"	| ";VARATTR=attr1"					|| 1				| true
		"MIGRATIONDISABLED=false"	| ";VARATTR=attr1"					|| 1				| false
		"MIGRATIONDISABLED=1"		| ";VARATTR=attr1"					|| 1				| true
		"MIGRATIONDISABLED=0"		| ";VARATTR=attr1"					|| 1				| false
		"MIGRATIONDISABLED="		| ";VARATTR=attr1"					|| 1				| false
		""							| ";VARATTR=AllowVmmIgrations"		|| 0				| false
		""							| ";VARATTR=nOVmmIgrations"			|| 0				| true
		""							| ";VARATTR=AllowVmmIgrations+attr1"|| 1				| false
		""							| ";VARATTR=nOVmmIgrations+attr1"	|| 1				| true
		"MIGRATIONDISABLED=true"	| ";VARATTR=novmmigrations"			|| 0				| true
		"MIGRATIONDISABLED=false"	| ";VARATTR=allowvmmigrations"		|| 0				| false
		"MIGRATIONDISABLED=1"		| ";VARATTR=novmmigrations"			|| 0				| true
		"MIGRATIONDISABLED=0"		| ";VARATTR=allowvmmigrations"		|| 0				| false
		"MIGRATIONDISABLED="		| ";VARATTR=allowvmmigrations"		|| 0				| false
	}
}
