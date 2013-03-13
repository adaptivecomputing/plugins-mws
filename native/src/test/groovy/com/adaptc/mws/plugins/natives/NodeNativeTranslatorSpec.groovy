package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.NodeReport
import com.adaptc.mws.plugins.NodeReportState
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
		def plugin = mockPlugin(NativePlugin)
		
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
                ";OSLIST=linux,windows"+
				";RACK=4"+
				";SLOT=2"+
				";VARATTR=HVTYPE=esx+attr1:val1+attr2=val2+attr3+attr4"
		NodeReport node = translator.createReport(plugin.parseWiki([wiki]))
		
		then:
		1 * genericNativeTranslator.getGenericMap("ares") >> [res1:"1"]
		1 * genericNativeTranslator.getGenericMap("cres") >> [res2:"2"]
		1 * genericNativeTranslator.getGenericMapWithDisplayValue("HVTYPE=esx+attr1:val1+attr2=val2+attr3+attr4", "\\+", ":|=") >>
				[HVTYPE:"esx",attr1:[value:"val1", displayValue: "value one"]]
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
	}

	def "Wiki to domain null values handled correctly"() {
		given:
		GenericNativeTranslator genericNativeTranslator = Mock()
		translator.genericNativeTranslator = genericNativeTranslator
		def plugin = mockPlugin(NativePlugin)

		and:
		long time = 12348473

		when:
		def wiki = "node1 STATE=${NodeReportState.IDLE}" +
				";UPDATETIME=${(time).toLong()}"
		NodeReport node = translator.createReport(plugin.parseWiki([wiki]))

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
	}

	def "Object for #clazz.simpleName has property #property"(Class clazz, String property, boolean result) {
		expect:
		translator.objectHasProperty(clazz.newInstance(), property)==result

		where:
		clazz						| property		| result
		MyNodeReport				| "id"			| true
		MyNodeReport				| "attributes"	| false
		MyNodeReportWithAttributes	| "id"			| true
		MyNodeReportWithAttributes	| "attributes"	| true
	}
}

class MyNodeReport {
	private String id
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id
	}
}

class MyNodeReportWithAttributes {
	private String id
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id
	}
	private String attributes;
	public String getAttributes() {
		return attributes
	}
	public void setAttributes(String attributes) {
		this.attributes = attributes
	}
}