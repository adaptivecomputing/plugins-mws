package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeUtils
import com.adaptc.mws.plugins.testing.*
import spock.lang.*

import static com.adaptc.mws.plugins.PluginConstants.*

@TestFor(VirtualMachineNativeTranslator)
@TestMixin(PluginUnitTestMixin)
@Unroll
class VirtualMachineNativeTranslatorSpec extends Specification {
	def "Wiki to domain"() {
		given:
		long time = 12348473
		
		when:
		def wiki = "vm1 StaTE=${NodeReportState.IDLE}" +
				";UPdaTETIME=${(time).toLong()}"+
				";PowER=${NodeReportPower.ON}"+
				";CONtaINERNODE=node1"+
				";CPrOC=4"+
				";APrOC=2"+
				";ASwAP=256"+
				";CSwAP=512"+
				";CMeMORY=1024"+
				";AMeMORY=256"+
				";CDiSK=1000"+
				";ADiSK=512"+
				";CPuLOAD=1.2"+
				";Type=vm"+	// Ignored
				";Os=linux"+
				";OsLIST=linux,windows"
		def imageInfo = new VMImageInfo()
		VirtualMachineReport virtualMachine = translator.createReport(null, NativeUtils.parseWiki([wiki])[0], imageInfo)
		
		then:
		0 * _._
		
		and:
		virtualMachine.name=="vm1"
		virtualMachine.state==NodeReportState.IDLE
		virtualMachine.power==NodeReportPower.ON
		virtualMachine.timestamp==new Date(time*1000)
		virtualMachine.resources[RESOURCE_PROCESSORS].total==4
		virtualMachine.resources[RESOURCE_PROCESSORS].available==2
		virtualMachine.resources[RESOURCE_MEMORY].total==1024
		virtualMachine.resources[RESOURCE_MEMORY].available==256
		virtualMachine.resources[RESOURCE_DISK].total==1000
		virtualMachine.resources[RESOURCE_DISK].available==512
		virtualMachine.resources[RESOURCE_SWAP].total==512
		virtualMachine.resources[RESOURCE_SWAP].available==256
		virtualMachine.metrics[METRIC_CPULOAD]==1.2
		virtualMachine.metrics.size()==1
		virtualMachine.image=="linux"
		virtualMachine.variables.size()==0
		virtualMachine.imagesAvailable.size()==2
		virtualMachine.imagesAvailable[0]=="linux"
		virtualMachine.imagesAvailable[1]=="windows"

		and:
		imageInfo.name=="linux"
	}

	def "Migration disabled flag"() {
		when:
		def wiki = "vm1 "+migrationWiki
		VirtualMachineReport virtualMachine = translator.createReport(null, NativeUtils.parseWiki([wiki])[0], new VMImageInfo())

		then:
		virtualMachine?.name=="vm1"
		virtualMachine.migrationDisabled==result

		where:
		migrationWiki					|| result
		""								|| null
		"MIGRATIONDISABLED=true"		|| true
		"MIGRATIONDISABLED=false"		|| false
		"MIGRATIONDISABLED=1"			|| true
		"MIGRATIONDISABLED=0"			|| false
		"MIGRATIONDISABLED="			|| false
	}

	def "Notifications for invalid attributes"() {
		given:
		IPluginEventService pluginEventService = Mock()

		when:
		def object = translator.createReport(pluginEventService, [id:"id1", bogus1:"value", bogus2:"value2"], new VMImageInfo())

		then:
		object.name=="id1"

		and:
		2 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"virtualMachineNativeTranslator.invalid.attribute", {it.type=="VM" && it.id=="id1" }, null)
		0 * _._
	}

	def "Slave flag"() {
		when:
		def wiki = "vm1 "+slaveWiki
		VirtualMachineReport virtualMachine = translator.createReport(null, NativeUtils.parseWiki([wiki])[0], new VMImageInfo())

		then:
		virtualMachine?.name=="vm1"
		virtualMachine.slaveReport==result

		where:
		slaveWiki			|| result
		""					|| false
		"SLAVE=true"		|| true
		"SLAVE=false"		|| false
		"SLAVE=1"			|| true
		"SLAVE=0"			|| false
		"SLAVE="			|| false
	}

	def "Is virtual machine wiki: #attrs is #result"() {
		expect:
		translator.isVirtualMachineWiki(attrs)==result

		where:
		attrs								|| result
		null								|| false
		[:]									|| false
		[type:"node"]						|| false
		[type:"vm"]							|| true
		[tYpE:"vM"]							|| true
		[containerNoDe:"node1"]				|| true
		[type:"node",containernode:"node1"]	|| false
	}
}
