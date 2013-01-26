package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.testing.*
import spock.lang.Specification

import static com.adaptc.mws.plugins.PluginConstants.*

@TestFor(VirtualMachineNativeTranslator)
@TestMixin(PluginUnitTestMixin)
class VirtualMachineNativeTranslatorSpec extends Specification {
	def "Wiki to domain"() {
		given:
		def plugin = mockPlugin(NativePlugin)
		
		and:
		long time = 12348473
		
		when:
		def wiki = "vm1 STATE=${NodeReportState.IDLE}" +
				";UPDATETIME=${(time).toLong()}"+
				";POWER=${NodeReportPower.STANDBY}"+
				";CONTAINERNODE=node1"+
				";CPROC=4"+
				";APROC=2"+
				";CMEMORY=1024"+
				";AMEMORY=256"+
				";CDISK=1000"+
				";ADISK=512"+
				";CPULOAD=1.2"+
				";OS=linux"
		VirtualMachineReport virtualMachine = translator.createReport(plugin.parseWiki([wiki]))
		
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
		virtualMachine.metrics[METRIC_CPULOAD]==1.2
		virtualMachine.metrics.size()==1
		virtualMachine.image=="linux"
		virtualMachine.variables.size()==0
	}
}
