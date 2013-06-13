package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.IMoabRestService
import com.adaptc.mws.plugins.IPluginDatastoreService
import com.adaptc.mws.plugins.MoabRestResponse
import com.adaptc.mws.plugins.NodeReportState
import com.adaptc.mws.plugins.testing.TestFor
import com.adaptc.mws.plugins.testing.TestMixin
import com.adaptc.mws.plugins.testing.TranslatorUnitTestMixin
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification
import spock.lang.Unroll

import static com.adaptc.mws.plugins.reports.NodeUtilizationReportPlugin.ALL_DATACENTERS
import static com.adaptc.mws.plugins.reports.NodeUtilizationReportPlugin.NODE_LAST_UPDATED_COLLECTION
import com.adaptc.mws.plugins.IPluginEventService

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
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService
		IPluginDatastoreService pluginDatastoreService = Mock()
		plugin.pluginDatastoreService = pluginDatastoreService
		MockHttpServletResponse httpResponse = Mock()
		Date startDate = new Date(new Date().time - (6400 * 1000))
		Date endDate = new Date(new Date().time + (6400 * 1000))

		and:
		config.reportConsolidationDuration = 10
		config.reportSize = 2

		when: "Report does not exist and cannot be created"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 404

		then:
		1 * pluginEventService.createEvent(UtilizationReportEvents.REPORT_1NAME_CREATE_ERROR_2MESSAGES,
				['node-utilization', 'message1, message2'], null)
		1 * moabRestService.post({
			assert it.data.name=="node-utilization"
			assert it.data.description
			assert it.data.consolidationFunction=="average"
			assert it.data.datapointDuration==10
			assert it.data.reportSize==2
			assert it.data.keepSamples==false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, [messages:['message1', "message2"]], false)
		0 * _._

		when: "Report does not exist and can be created, but could not get node information v2"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 404

		then:
		1 * moabRestService.isAPIVersionSupported(2) >> true
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
			assert it.params.fields=="metrics.cpuUtilization,attributes.MOAB_DATACENTER,lastUpdatedDate,states.state,name,virtualMachines,resources.memory"
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, [messages:["message1", "message2"]], false)
		1 * pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
				['/rest/nodes/', 2, 'message1, message2'], null)
		0 * _._

		when: "Report does exist but could not get node information v1"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 200

		then:
		1 * moabRestService.isAPIVersionSupported(2) >> false
		1 * moabRestService.get({
			assert it.params.fields=="genericMetrics.cpuUtilization,attributes.MOAB_DATACENTER,lastUpdateDate,state,id,virtualMachines,availableMemory,totalMemory"
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, null, false)
		1 * pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
				['/rest/nodes/', 1, null], null)
		0 * _._

		when: "Sample could not be created with no data returned"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * moabRestService.isAPIVersionSupported(2) >> true
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		1 * moabRestService.get(['params':['api-version':2, 'fields':"metrics.cpuUtilization,attributes.MOAB_DATACENTER,lastUpdatedDate,states.state,name,virtualMachines,resources.memory"]], '/rest/nodes/') >> new MoabRestResponse(null, [totalCount:0, resultCount:0, results:[]], true)
		1 * moabRestService.get(['params':['api-version':1, 'fields':'label,allocatedNodes,flags,startDate,endDate']], '/rest/reservations/') >> new MoabRestResponse(null, [totalCount:0, resultCount:0, results:[]], true)

		then:
		1 * moabRestService.post("/rest/reports/node-utilization/samples/", {
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
		1 * pluginEventService.createEvent(UtilizationSampleEvents.CREATE_1REPORT_ERROR_2MESSAGES,
				['node-utilization', null], null)
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
		1 * moabRestService.isAPIVersionSupported(2) >> true
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		1 * moabRestService.get(['params':['api-version':1, 'fields':'label,allocatedNodes,flags,startDate,endDate']], '/rest/reservations/') >> new MoabRestResponse(null, [totalCount:1, resultCount:1, results:[[
			allocatedNodes: [
					[
						id: "reservedNode"
					]
			],
			endDate: endDate.toString(),
			flags: [
					"EVACVMS",
					"ISCLOSED",
					"ISACTIVE",
					"REQFULL",
					"TRIGHASFIRED"
			],
			label: "test",
			startDate: startDate.toString()
		]]], true)
		1 * moabRestService.convertDateString(startDate.toString()) >> startDate
		1 * moabRestService.convertDateString(endDate.toString()) >> endDate
		1 * moabRestService.get({
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, [totalCount:17, resultCount:17, results:[
				[name:"reservedNode", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:100]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:0],virtualMachines:[]],
		        [name:"node01", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:100]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:0],virtualMachines:[[name:"vm1"]]],
		        [name:"node02", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:10]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:10],virtualMachines:[[name:"vm1"]]],
		        [name:"node03", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:25]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:25],virtualMachines:[[name:"vm1"]]],
		        [name:"node04", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:75]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:50],virtualMachines:[[name:"vm1"]], attributes:[MOAB_DATACENTER:[value:"value", displayValue:"myDC"]]],
		        [name:"node05", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:50]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:75],virtualMachines:[[name:"vm1"]]],
		        [name:"node06", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:80]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:80],virtualMachines:[[name:"vm1"]]],
		        [name:"node07", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node09", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.DOWN.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node10", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.DRAINED.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node11", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.FLUSH.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node12", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.NONE.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node13", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.UNKNOWN.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node14", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.UP.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node15", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.RESERVED.toString()], genericMetrics:[cpuUtilization:100],virtualMachines:[[name:"vm1"]]],
				[name:"node16", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()],virtualMachines:[[name:"vm1"]]],
		        [name:"node17", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[:],virtualMachines:[[name:"vm1"]]],
				[name:"node18", lastUpdatedDate:"12:12:12 01-01-01", resources:[memory:[real:100, available:0]],
						states:[state:NodeReportState.IDLE.toString()], metrics:[cpuUtilization:null],virtualMachines:[[name:"vm1"]]]
		]], true)
		18 * pluginDatastoreService.getData(NODE_LAST_UPDATED_COLLECTION, "name", _ as String)
		18 * pluginDatastoreService.addData(NODE_LAST_UPDATED_COLLECTION, _ as Map) >> true
		7 * moabRestService.put(_, _) >> new MoabRestResponse(null,null,true)
		_ * pluginEventService.updateNotificationCondition(*_)

		then:
		1 * moabRestService.post("/rest/reports/node-utilization/samples/", {
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
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/node-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * moabRestService.isAPIVersionSupported(2) >> false
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		1 * moabRestService.get(['params':['api-version':1, 'fields':'label,allocatedNodes,flags,startDate,endDate']], '/rest/reservations/') >> new MoabRestResponse(null, [totalCount:1, resultCount:1, results:[[
				allocatedNodes: [
						[
								id: "reservedNode"
						]
				],
				endDate: endDate.toString(),
				flags: [
						"EVACVMS",
						"ISCLOSED",
						"ISACTIVE",
						"REQFULL",
						"TRIGHASFIRED"
				],
				label: "test",
				startDate: startDate.toString()
		]]], true)
		1 * moabRestService.convertDateString(startDate.toString()) >> startDate
		1 * moabRestService.convertDateString(endDate.toString()) >> endDate
		1 * moabRestService.get({
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, [totalCount:17, resultCount:17, results:[
				[id:"reservedNode", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:100,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:0],virtualMachines: []],
				[id:"node01", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:100,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:0],virtualMachines:[[id:"vm1"]]],
				[id:"node02", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:10,
						state:NodeReportState.BUSY.toString(), genericMetrics:[cpuUtilization:10],virtualMachines:[[id:"vm1"]]],
				[id:"node03", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:25,
						state:NodeReportState.RUNNING.toString(), genericMetrics:[cpuUtilization:25],virtualMachines:[[id:"vm1"]]],
				[id:"node04", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:75,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:50],virtualMachines:[[id:"vm1"]], attributes:[MOAB_DATACENTER:[value:"value", displayValue:"myDC"]]],
				[id:"node05", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:50,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:75],virtualMachines:[[id:"vm1"]]],
				[id:"node06", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:80,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:80],virtualMachines:[[id:"vm1"]]],
				[id:"node07", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node09", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.DOWN.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node10", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.DRAINED.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node11", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.FLUSH.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node12", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.NONE.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node13", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.UNKNOWN.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node14", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.UP.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node15", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.RESERVED.toString(), genericMetrics:[cpuUtilization:100],virtualMachines:[[id:"vm1"]]],
				[id:"node16", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,virtualMachines:[[id:"vm1"]],
						state:NodeReportState.IDLE.toString()],
				[id:"node17", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.IDLE.toString(), genericMetrics:[:],virtualMachines:[[id:"vm1"]]],
				[id:"node18", lastUpdateDate:"12:12:12 01-01-01", totalMemory:100, availableMemory:0,
						state:NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:null],virtualMachines:[[id:"vm1"]]],
		]], true)
		18 * pluginDatastoreService.getData(NODE_LAST_UPDATED_COLLECTION, "name", _ as String)
		18 * pluginDatastoreService.addData(NODE_LAST_UPDATED_COLLECTION, _ as Map) >> true
		13 * moabRestService.put(_, _) >> new MoabRestResponse(null,null,true)
		_ * pluginEventService.updateNotificationCondition(*_)

		then:
		1 * moabRestService.post("/rest/reports/node-utilization/samples/", {
			def result = it.call()
			assert result.agent=="Node Utilization Report Plugin"
			assert result.data.size()==1
			assert result.data[ALL_DATACENTERS].total==13
			assert result.data[ALL_DATACENTERS].cpuHigh==9
			assert result.data[ALL_DATACENTERS].cpuLow==2
			assert result.data[ALL_DATACENTERS].cpuMedium==2
			assert result.data[ALL_DATACENTERS].cpuAverage==72.3076923076923
			assert result.data[ALL_DATACENTERS].memoryHigh==9
			assert result.data[ALL_DATACENTERS].memoryLow==2
			assert result.data[ALL_DATACENTERS].memoryMedium==2
			assert result.data[ALL_DATACENTERS].memoryAverage==73.84615384615384
			assert result.data[ALL_DATACENTERS].high==11
			assert result.data[ALL_DATACENTERS].low==1
			assert result.data[ALL_DATACENTERS].medium==1
			return true
		}) >> new MoabRestResponse(null, null, true)
		0 * _._
	}

	@Unroll
	def "Notification '#errorMessage' is created with api-version 2"() {
		given: "Mock"
		IMoabRestService moabRestService = Mock()
		plugin.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService
		IPluginDatastoreService pluginDatastoreService = Mock()
		plugin.pluginDatastoreService = pluginDatastoreService
		MockHttpServletResponse httpResponse = Mock()
		config.reportConsolidationDuration = 10
		config.reportSize = 2
		config.cpuHighThreshold = 75
		config.cpuLowThreshold = 25
		config.memoryHighThreshold = 75
		config.memoryLowThreshold = 25

		when:
		plugin.poll()

		then:
		1 * moabRestService.isAPIVersionSupported(2) >> true
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION) >> [[name: "node1", lastUpdatedDate: "12:12:12 01-01-00" ]]
		1 * moabRestService.get(['params':['api-version':1, 'fields':'label,allocatedNodes,flags,startDate,endDate']], '/rest/reservations/') >>
				new MoabRestResponse(null, [totalCount:0, resultCount:0, results:[]], true)
		1 * moabRestService.get(_, "/rest/nodes/") >>
				new MoabRestResponse(null, [totalCount: 1, resultCount: 1, results: [
						node,
						[name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]]
				], true)
		1 * moabRestService.post("/rest/reports/node-utilization/samples/", {
			return true
		}) >> new MoabRestResponse(null, null, true)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				errorMessage, { it.type=="Node" && it.id==node.name }, null)
		1 * moabRestService.get("/rest/reports/node-utilization")
		_ * pluginDatastoreService._
		_ * moabRestService.put('/rest/nodes/node1', _ as Closure)
		0 * _._

		where:
		severity	| errorMessage															| node
		"ERROR"		| "nodeUtilizationReportPlugin.node.name.null"							| [lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"ERROR"		| "nodeUtilizationReportPlugin.node.state.null"							| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100, available: 80]], states: [state: null], metrics: [cpuUtilization: 45],attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"ERROR"		| "nodeUtilizationReportPlugin.node.realMemory.null"					| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"ERROR"		| "nodeUtilizationReportPlugin.total.memory.zero.message"				| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 0, available: 40]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"ERROR"		| "nodeUtilizationReportPlugin.node.availableMemory.null"				| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"ERROR"		| "nodeUtilizationReportPlugin.node.cpuUtils.null"						| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"WARN"		| "nodeUtilizationReportPlugin.available.equals.total.memory.message"	| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 80, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"WARN"		| "nodeUtilizationReportPlugin.cpu.zero.message"						| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 0], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"WARN"		| "nodeUtilizationReportPlugin.node.notUpdated"							| [name: "node1", lastUpdatedDate: "12:12:12 01-01-00", resources: [memory: [real: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45], attributes: [ MOAB_DATACENTER: [value:"value", displayValue:"datacenter"]] ]
		"WARN"		| "nodeUtilizationReportPlugin.node.datacenter.null"					| [name: "node1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [real: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]

	}

	@Unroll
	def "Notification '#errorMessage' is created with api-version 1"() {
		given: "Mock"
		IMoabRestService moabRestService = Mock()
		plugin.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService
		IPluginDatastoreService pluginDatastoreService = Mock()
		plugin.pluginDatastoreService = pluginDatastoreService
		MockHttpServletResponse httpResponse = Mock()
		config.reportConsolidationDuration = 10
		config.reportSize = 2
		config.cpuHighThreshold = 75
		config.cpuLowThreshold = 25
		config.memoryHighThreshold = 75
		config.memoryLowThreshold = 25

		when:
		plugin.poll()

		then:
		1 * moabRestService.isAPIVersionSupported(2) >> false
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION) >> [[name: "node1", lastUpdatedDate: "12:12:12 01-01-00" ]]
		1 * moabRestService.get(['params':['api-version':1, 'fields':'label,allocatedNodes,flags,startDate,endDate']], '/rest/reservations/') >> new MoabRestResponse(null, [totalCount:0, resultCount:0, results:[]], true)
		1 * moabRestService.get({
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, [totalCount: 1, resultCount: 1, results: [node,[id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45] ] ]], true)
		1 * moabRestService.post("/rest/reports/node-utilization/samples/", {
			return true
		}) >> new MoabRestResponse(null, null, true)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				errorMessage, { it.type=="Node" && it.id==node.id }, null)
		1 * moabRestService.get("/rest/reports/node-utilization")
		_ * pluginDatastoreService._
		_ * moabRestService.put('/rest/nodes/node1', _ as Closure)
		0 * _._

		where:
		severity	| errorMessage															| node
		"ERROR"		| "nodeUtilizationReportPlugin.node.name.null"							| [lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
		"ERROR"		| "nodeUtilizationReportPlugin.node.state.null"							| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, genericMetrics:[cpuUtilization:45]]
		"ERROR"		| "nodeUtilizationReportPlugin.node.realMemory.null"					| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", availableMemory: 80, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
		"ERROR"		| "nodeUtilizationReportPlugin.total.memory.zero.message"				| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 0, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
		"ERROR"		| "nodeUtilizationReportPlugin.node.availableMemory.null"				| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
		"ERROR"		| "nodeUtilizationReportPlugin.node.cpuUtils.null"						| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString()]
		"WARN"		| "nodeUtilizationReportPlugin.cpu.zero.message"						| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:0]]
		"WARN"		| "nodeUtilizationReportPlugin.available.equals.total.memory.message"	| [id: "node1",  lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 1, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
		"WARN"		| "nodeUtilizationReportPlugin.node.notUpdated"							| [id: "node1",  lastUpdateDate: "12:12:12 01-01-00", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
	}

	def "All nodes had errors"() {
		given: "Mock"
		IMoabRestService moabRestService = Mock()
		plugin.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService
		IPluginDatastoreService pluginDatastoreService = Mock()
		plugin.pluginDatastoreService = pluginDatastoreService
		MockHttpServletResponse httpResponse = Mock()
		config.reportConsolidationDuration = 10
		config.reportSize = 2
		config.cpuHighThreshold = 75
		config.cpuLowThreshold = 25
		config.memoryHighThreshold = 75
		config.memoryLowThreshold = 25

		when:
		plugin.poll()

		then:
		1 * moabRestService.isAPIVersionSupported(2) >> false
		1 * pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION) >> [[name: "node1", lastUpdatedDate: "12:12:12 01-01-00" ]]
		1 * moabRestService.get(['params':['api-version':1, 'fields':'label,allocatedNodes,flags,startDate,endDate']], '/rest/reservations/') >> new MoabRestResponse(null, [totalCount:0, resultCount:0, results:[]], true)
		1 * moabRestService.get(_, "/rest/nodes/") >> new MoabRestResponse(null, [totalCount: 1, resultCount: 1, results: [node]], true)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, errorMessage, { it.type=="Node" && it.id==null }, null)
		1 * pluginEventService.createEvent(UtilizationReportEvents.NO_SAMPLES_1TYPE, ["nodes"], null)
		1 * moabRestService.get("/rest/reports/node-utilization")
		0 * _._

		where:
		severity	| errorMessage									| node
		"ERROR"		| "nodeUtilizationReportPlugin.node.name.null"	| [lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics:[cpuUtilization:45]]
	}
}
