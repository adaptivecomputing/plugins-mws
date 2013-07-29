package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeUtils
import com.adaptc.mws.plugins.testing.*
import spock.lang.Specification

import static com.adaptc.mws.plugins.PluginConstants.*

@TestFor(VirtualMachineNativeTranslator)
@TestMixin(PluginUnitTestMixin)
class VirtualMachineNativeTranslatorSpec extends Specification {
	def "Wiki to domain"() {
		given:
		long time = 12348473
		
		when:
		def wiki = "vm1 STATE=${NodeReportState.IDLE}" +
				";UPDATETIME=${(time).toLong()}"+
				";POWER=${NodeReportPower.STANDBY}"+
				";CONTAINERNODE=node1"+
				";CPROC=4"+
				";APROC=2"+
				";ASWAP=256"+
				";CSWAP=512"+
				";CMEMORY=1024"+
				";AMEMORY=256"+
				";CDISK=1000"+
				";ADISK=512"+
				";CPULOAD=1.2"+
				";OS=linux"+
				";OSLIST=linux,windows"
		def imageInfo = new VMImageInfo()
		VirtualMachineReport virtualMachine = translator.createReport(NativeUtils.parseWiki([wiki])[0], imageInfo)
		
		then:
		0 * _._
		
		and:
		virtualMachine.name=="vm1"
		virtualMachine.state==NodeReportState.IDLE
		virtualMachine.power==NodeReportPower.STANDBY
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
		VirtualMachineReport virtualMachine = translator.createReport(NativeUtils.parseWiki([wiki])[0], new VMImageInfo())

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
}
