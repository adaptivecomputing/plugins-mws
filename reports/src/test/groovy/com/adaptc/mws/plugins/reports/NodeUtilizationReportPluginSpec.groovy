package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.testing.*
import spock.lang.*

import org.springframework.mock.web.MockHttpServletResponse
import static com.adaptc.mws.plugins.reports.NodeUtilizationReportPlugin.*

@TestFor(NodeUtilizationReportPlugin)
@TestMixin(TranslatorUnitTestMixin)
class NodeUtilizationReportPluginSpec extends Specification {
	def setup() {
		plugin.utilizationReportTranslator = mockTranslator(UtilizationReportTranslator)
	}

	def "Recreate report"() {
		given:
		IMoabRestService moabRestService = Mock()
		plugin.moabRestService = moabRestService

		and:
		config.reportConsolidationDuration = 10
		config.reportSize = 2

		when: "Report cannot be deleted or created without messages"
		def result = plugin.recreateReport([:])

		then:
		1 * moabRestService.delete("/rest/reports/node-utilization") >> new MoabRestResponse(null, null, false)
		1 * moabRestService.post({
			assert it.data.name=="node-utilization"
			assert it.data.description
			assert it.data.consolidationFunction=="average"
			assert it.data.datapointDuration==10
			assert it.data.reportSize==2
			assert it.data.keepSamples==false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, null, false)
		0 * _._

		and:
		result.messages.size()==1
		result.messages[0]=="nodeUtilizationReportPlugin.recreateReport.failure.message"

		when: "Report cannot be deleted or created with messages"
		result = plugin.recreateReport([:])

		then:
		1 * moabRestService.delete("/rest/reports/node-utilization") >> new MoabRestResponse(null, null, false)
		1 * moabRestService.post({
			assert it.data.name=="node-utilization"
			assert it.data.description
			assert it.data.consolidationFunction=="average"
			assert it.data.datapointDuration==10
			assert it.data.reportSize==2
			assert it.data.keepSamples==false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, [messages:["message1", "message2"]], false)
		0 * _._

		and:
		result.messages.size()==3
		result.messages[0]=="nodeUtilizationReportPlugin.recreateReport.failure.message"
		result.messages[1]=="message1"
		result.messages[2]=="message2"

		when: "Report can be deleted and recreated"
		result = plugin.recreateReport([:])

		then:
		1 * moabRestService.delete("/rest/reports/node-utilization") >> new MoabRestResponse(null, null, true)
		1 * moabRestService.post({
			assert it.data.name=="node-utilization"
			assert it.data.description
			assert it.data.consolidationFunction=="average"
			assert it.data.datapointDuration==10
			assert it.data.reportSize==2
			assert it.data.keepSamples==false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, null, true)
		0 * _._

		and:
		result.messages.size()==1
		result.messages[0]=="nodeUtilizationReportPlugin.recreateReport.success.message"
	}

	def "Poll"() {
		given:
		IMoabRestService moabRestService = Mock()
		plugin.moabRestService = moabRestService
		IPluginDatastoreService pluginDatastoreService = Mock()
		plugin.pluginDatastoreService = pluginDatastoreService
		MockHttpServletResponse httpResponse = Mock()
		plugin.grailsApplication = [metadata:['app.version':"7.2.0"]]

		and:
		config.reportConsolidationDuration = 10
		config.reportSize = 2

		when: "Report does not exist and cannot be created"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 404

		then:
		1 * moabRestService.post({
			assert it.data.name=="node-utilization"
			assert it.data.description
			assert it.data.consolidationFunction=="average"
			assert it.data.datapointDuration==10
			assert it.data.reportSize==2
			assert it.data.keepSamples==false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, [messages:["message1", "message2"]], false)
		0 * _._

		when: "Report does not exist and can be created, but could not get node information v2"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 404

		then:
		1 * moabRestService.post({
			assert it.data.name=="node-utilization"
			assert it.data.description
			assert it.data.consolidationFunction=="average"
			assert it.data.datapointDuration==10
			assert it.data.reportSize==2
			assert it.data.keepSamples==false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, null, true)

		then:
		1 * moabRestService.get({
			assert it.params.'api-version'==2
			assert it.params.fields=="metrics.cpuUtilization,attributes.MOAB_DATACENTER,lastUpdatedDate,states.state,name,resources.memory"
			return true
		}, "/rest/nodes") >> new MoabRestResponse(null, null, false)
		0 * _._

		when: "Report does exist but could not get node information v1"
		plugin.grailsApplication = [metadata:['app.version':"7.1.3"]]
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 200

		then:
		1 * moabRestService.get({
			assert it.params.'api-version'==1
			assert it.params.fields=="genericMetrics.cpuUtilization,attributes.MOAB_DATACENTER,lastUpdateDate,states.state,id,availableMemory,totalMemory"
			return true
		}, "/rest/nodes") >> new MoabRestResponse(null, null, false)
		0 * _._

		when: "Sample could not be created with no data returned"
		plugin.grailsApplication = [metadata:['app.version':"7.2.0"]]
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		1 * moabRestService.get({
			assert it.params.'api-version'==2
			assert it.params.fields=="metrics.cpuUtilization,attributes.MOAB_DATACENTER,lastUpdatedDate,states.state,name,resources.memory"
			return true
		}, "/rest/nodes") >> new MoabRestResponse(null, [totalCount:0, resultCount:0, results:[]], true)

		then:
		1 * moabRestService.post("/rest/reports/node-utilization/samples", {
			def result = it.call()
			assert result.agent=="Node Utilization Report Plugin"
			assert result.data.size()==1
			assert result.data[ALL_DATACENTERS].total==0
			assert result.data[ALL_DATACENTERS].cpuHigh==0
			assert result.data[ALL_DATACENTERS].cpuLow==0
			assert result.data[ALL_DATACENTERS].cpuMedium==0
			assert result.data[ALL_DATACENTERS].cpuAverage==0
			assert result.data[ALL_DATACENTERS].memoryHigh==0
			assert result.data[ALL_DATACENTERS].memoryLow==0
			assert result.data[ALL_DATACENTERS].memoryMedium==0
			assert result.data[ALL_DATACENTERS].memoryAverage==0
			assert result.data[ALL_DATACENTERS].high==0
			assert result.data[ALL_DATACENTERS].low==0
			assert result.data[ALL_DATACENTERS].medium==0
			return true
		}) >> new MoabRestResponse(null, null, false)
		0 * _._

		when: "Sample could be created with data returned for all cases v2"
		config.cpuHighThreshold = 75
		config.cpuLowThreshold = 25
		config.memoryHighThreshold = 75
		config.memoryLowThreshold = 25
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		_ * moabRestService.post("/rest/events", _ as Closure) >> new MoabRestResponse(null, [:], true)
		1 * moabRestService.get({
			assert it.params.'api-version'==2
			return true
		}, "/rest/nodes") >> new MoabRestResponse(null, [totalCount:17, resultCount:17, results:[
		        [name:"node01", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:100]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:0]],
		        [name:"node02", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:10]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:10]],
		        [name:"node03", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:25]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:25]],
		        [name:"node04", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:75]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:50], attributes:[MOAB_DATACENTER:"myDC"]],
		        [name:"node05", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:50]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:75]],
		        [name:"node06", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:80]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:80]],
		        [name:"node07", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:100]],
				[name:"node09", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.DOWN.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node10", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.DRAINED.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node11", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.FLUSH.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node12", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.NONE.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node13", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.UNKNOWN.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node14", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.UP.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node15", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.RESERVED.toString()], genericMetrics:[cpuUtilization:100]],
				[name:"node16", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()]],
		        [name:"node17", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[:]],
				[name:"node18", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:null]],
		]], true)
		17 * pluginDatastoreService.getData(NODE_LAST_UPDATED_COLLECTION, "name", _ as String)
		17 * pluginDatastoreService.addData(NODE_LAST_UPDATED_COLLECTION, _ as Map) >> true

		then:
		1 * moabRestService.post("/rest/reports/node-utilization/samples", {
			def result = it.call()
			assert result.agent=="Node Utilization Report Plugin"
			assert result.data.size()==2
			assert result.data[ALL_DATACENTERS].total==7
			assert result.data[ALL_DATACENTERS].cpuHigh==3
			assert result.data[ALL_DATACENTERS].cpuLow==2
			assert result.data[ALL_DATACENTERS].cpuMedium==2
			assert result.data[ALL_DATACENTERS].cpuAverage==48.571428571428571428571428571429
			assert result.data[ALL_DATACENTERS].memoryHigh==3
			assert result.data[ALL_DATACENTERS].memoryLow==2
			assert result.data[ALL_DATACENTERS].memoryMedium==2
			assert result.data[ALL_DATACENTERS].memoryAverage==51.428571428571428571428571428571
			assert result.data[ALL_DATACENTERS].high==5
			assert result.data[ALL_DATACENTERS].low==1
			assert result.data[ALL_DATACENTERS].medium==1
			assert result.data["myDC"].total==1
			assert result.data["myDC"].cpuHigh==0
			assert result.data["myDC"].cpuLow==0
			assert result.data["myDC"].cpuMedium==1
			assert result.data["myDC"].cpuAverage==50
			assert result.data["myDC"].memoryHigh==0
			assert result.data["myDC"].memoryLow==0
			assert result.data["myDC"].memoryMedium==1
			assert result.data["myDC"].memoryAverage==25
			assert result.data["myDC"].high==0
			assert result.data["myDC"].low==0
			assert result.data["myDC"].medium==1
			return true
		}) >> new MoabRestResponse(null, null, true)
		0 * _._

		when: "Sample could be created with data returned for all cases v1"
		config.cpuHighThreshold = 75
		config.cpuLowThreshold = 25
		config.memoryHighThreshold = 75
		config.memoryLowThreshold = 25
		plugin.grailsApplication = [metadata:['app.version':"7.1.3"]]
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		_ * moabRestService.post("/rest/events", _ as Closure) >> new MoabRestResponse(null, [:], false)
		1 * moabRestService.get({
			assert it.params.'api-version'==1
			return true
		}, "/rest/nodes") >> new MoabRestResponse(null, [totalCount:17, resultCount:17, results:[
				[id:"node01", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:100,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[cpuUtilization:0]],
				[id:"node02", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:10,
						states:[state:NodeReportState.BUSY.toString()], genericMetrics:[cpuUtilization:10]],
				[id:"node03", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:25,
						states:[state:NodeReportState.RUNNING.toString()], genericMetrics:[cpuUtilization:25]],
				[id:"node04", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:75,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[cpuUtilization:50], attributes:[MOAB_DATACENTER:"myDC"]],
				[id:"node05", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:50,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[cpuUtilization:75]],
				[id:"node06", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:80,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[cpuUtilization:80]],
				[id:"node07", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node09", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.DOWN.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node10", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.DRAINED.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node11", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.FLUSH.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node12", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.NONE.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node13", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.UNKNOWN.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node14", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.UP.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node15", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.RESERVED.toString()], genericMetrics:[cpuUtilization:100]],
				[id:"node16", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.IDLE.toString()]],
				[id:"node17", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[:]],
				[id:"node18", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						states:[state:NodeReportState.IDLE.toString()], genericMetrics:[cpuUtilization:null]],
		]], true)
		17 * pluginDatastoreService.getData(NODE_LAST_UPDATED_COLLECTION, "name", _ as String)
		17 * pluginDatastoreService.addData(NODE_LAST_UPDATED_COLLECTION, _ as Map) >> true

		then:
		1 * moabRestService.post("/rest/reports/node-utilization/samples", {
			def result = it.call()
			assert result.agent=="Node Utilization Report Plugin"
			assert result.data.size()==2
			assert result.data[ALL_DATACENTERS].total==7
			assert result.data[ALL_DATACENTERS].cpuHigh==3
			assert result.data[ALL_DATACENTERS].cpuLow==2
			assert result.data[ALL_DATACENTERS].cpuMedium==2
			assert result.data[ALL_DATACENTERS].cpuAverage==48.571428571428571428571428571429
			assert result.data[ALL_DATACENTERS].memoryHigh==3
			assert result.data[ALL_DATACENTERS].memoryLow==2
			assert result.data[ALL_DATACENTERS].memoryMedium==2
			assert result.data[ALL_DATACENTERS].memoryAverage==51.428571428571428571428571428571
			assert result.data[ALL_DATACENTERS].high==5
			assert result.data[ALL_DATACENTERS].low==1
			assert result.data[ALL_DATACENTERS].medium==1
			assert result.data["myDC"].total==1
			assert result.data["myDC"].cpuHigh==0
			assert result.data["myDC"].cpuLow==0
			assert result.data["myDC"].cpuMedium==1
			assert result.data["myDC"].cpuAverage==50
			assert result.data["myDC"].memoryHigh==0
			assert result.data["myDC"].memoryLow==0
			assert result.data["myDC"].memoryMedium==1
			assert result.data["myDC"].memoryAverage==25
			assert result.data["myDC"].high==0
			assert result.data["myDC"].low==0
			assert result.data["myDC"].medium==1
			return true
		}) >> new MoabRestResponse(null, null, true)
		0 * _._
	}
}