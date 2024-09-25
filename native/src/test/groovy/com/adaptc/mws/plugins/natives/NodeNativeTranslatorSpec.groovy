package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.NodeReport
import com.adaptc.mws.plugins.NodeReportState
import com.adaptc.mws.plugins.natives.utils.NativeUtils
import com.adaptc.mws.plugins.testing.PluginUnitTestMixin
import com.adaptc.mws.plugins.testing.TestFor
import com.adaptc.mws.plugins.testing.TestMixin
import spock.lang.Specification
import spock.lang.Unroll

import java.text.SimpleDateFormat

import static com.adaptc.mws.plugins.PluginConstants.*

@Unroll
@TestFor(NodeNativeTranslator)
@TestMixin(PluginUnitTestMixin)
class NodeNativeTranslatorSpec extends Specification {
	def "Wiki to domain"() {
		given:
		GenericNativeTranslator genericNativeTranslator = Mock()
		translator.genericNativeTranslator = genericNativeTranslator
		translator.aclNativeTranslator = new AclNativeTranslator()

		and:
		long time = 12348473
		
		when:
		def wiki = "node1 StaTE=${NodeReportState.IDLE}:subs" +
				";UPdaTETIME=${(time).toLong()}"+
				";ArES=ares"+
				";CrES=cres"+
				";ReQUesTID=1234"+
				";TtL=2015-09-09T15:54:00Z"+
				";AcL=USER==FRED:BOB,GRouP==DEV"+
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
				";Type=node"+	// Ignored
				";Os=linux"+
				";VaRATTR=attr1:val1+attr2=val2+attr3+attr4"
		NodeReport node = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])
		
		then:
		node
		1 * genericNativeTranslator.getGenericMap("ares") >> [res1:"1"]
		1 * genericNativeTranslator.getGenericMap("cres") >> [res2:"2"]
		1 * genericNativeTranslator.getGenericMapWithDisplayValue("attr1:val1+attr2=val2+attr3+attr4", "\\+", ":|=") >> [attr1:[value:"val1", displayValue: "value one"]]
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
		node.resources[RESOURCE_SWAP].total==512
		node.resources[RESOURCE_SWAP].available==128
		node.metrics[METRIC_CPULOAD]==1.2
		node.metrics[METRIC_SPEED]==1.2d
		node.metrics.size()==2
		node.messages.size()==2
		node.messages[0]=="message1"
		node.messages[1]=="message 2"
		node.operatingSystem=="linux"
		node.variables.size()==0
		node.requestId == "1234"
		node.aclRules.size()==3
		SimpleDateFormat sdf = new SimpleDateFormat();
		sdf.setTimeZone(new SimpleTimeZone(0, "GMT"));
		sdf.applyPattern("dd MMM yyyy HH:mm:ss z");
		sdf.format(node.timeToLive).toString() == "09 Sep 2015 15:54:00 GMT"

		and:
		node.resources.size()==6
		node.resources["res1"].available==1
		node.resources["res2"].total==2

		and:
		node.attributes.size()==1
		node.attributes.attr1.value=="val1"
		node.attributes.attr1.displayValue=="value one"
	}

	def "Wiki to domain null values handled correctly"() {
		given:
		long time = 12348473

		when:
		def wiki = "node1 STATE=${NodeReportState.IDLE}" +
				";UPDATETIME=${(time).toLong()}"
		NodeReport node = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		node.name=="node1"
		node.state==NodeReportState.IDLE
		node.subState==null
		node.ipAddress==null
		node.timestamp==new Date(time*1000)
		node.resources.size()==0
		node.metrics.size()==0
		node.operatingSystem==null
		node.messages.size()==0
		node.variables.size()==0
		node.attributes.size()==0
	}

	def "Floating point update time is handled correctly"() {
		given:
		String time = "1363865607.000"

		when:
		def wiki = "node1 STATE=$NodeReportState.IDLE;UPDATETIME=$time"
		NodeReport node = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		node.name == "node1"
		node.state == NodeReportState.IDLE
		node.timestamp.time == 1363865607000
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
				"nodeNativeTranslator.invalid.attribute", {it.type=="Node" && it.id=="id1" }, null)
		0 * _._
	}

	def "Slave flag"() {
		when:
		def wiki = "node1 "+slaveWiki
		NodeReport node = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		node?.name=="node1"
		node.slaveReport==result

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
