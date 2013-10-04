package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.IMoabRestService
import com.adaptc.mws.plugins.IPluginDatastoreService
import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.MoabRestResponse
import com.adaptc.mws.plugins.NodeReportState
import com.adaptc.mws.plugins.testing.TestFor
import com.adaptc.mws.plugins.testing.TestMixin
import com.adaptc.mws.plugins.testing.TranslatorUnitTestMixin
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification
import spock.lang.Unroll

import static com.adaptc.mws.plugins.reports.VMUtilizationReportPlugin.ALL_DATACENTERS
import static com.adaptc.mws.plugins.reports.VMUtilizationReportPlugin.VM_LAST_UPDATED_COLLECTION

@TestFor(VMUtilizationReportPlugin)
@TestMixin(TranslatorUnitTestMixin)
class VMUtilizationReportPluginSpec extends Specification {
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
		1 * moabRestService.delete("/rest/reports/vm-utilization") >> new MoabRestResponse(null, null, false)
		1 * moabRestService.post({
			assert it.data.name == "vm-utilization"
			assert it.data.description
			assert it.data.consolidationFunction == "average"
			assert it.data.datapointDuration == 10
			assert it.data.reportSize == 2
			assert it.data.keepSamples == false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, null, false)
		0 * _._

		and:
		result.messages.size() == 1
		result.messages[0] == "vmUtilizationReportPlugin.recreateReport.failure.message"

		when: "Report cannot be deleted or created with messages"
		result = plugin.recreateReport([:])

		then:
		1 * moabRestService.delete("/rest/reports/vm-utilization") >> new MoabRestResponse(null, null, false)
		1 * moabRestService.post({
			assert it.data.name == "vm-utilization"
			assert it.data.description
			assert it.data.consolidationFunction == "average"
			assert it.data.datapointDuration == 10
			assert it.data.reportSize == 2
			assert it.data.keepSamples == false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, [messages: ["message1", "message2"]], false)
		0 * _._

		and:
		result.messages.size() == 3
		result.messages[0] == "vmUtilizationReportPlugin.recreateReport.failure.message"
		result.messages[1] == "message1"
		result.messages[2] == "message2"

		when: "Report can be deleted and recreated"
		result = plugin.recreateReport([:])

		then:
		1 * moabRestService.delete("/rest/reports/vm-utilization") >> new MoabRestResponse(null, null, true)
		1 * moabRestService.post({
			assert it.data.name == "vm-utilization"
			assert it.data.description
			assert it.data.consolidationFunction == "average"
			assert it.data.datapointDuration == 10
			assert it.data.reportSize == 2
			assert it.data.keepSamples == false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, null, true)
		0 * _._

		and:
		result.messages.size() == 1
		result.messages[0] == "vmUtilizationReportPlugin.recreateReport.success.message"
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

		and:
		config.reportConsolidationDuration = 10
		config.reportSize = 2

		when: "Report does not exist and cannot be created"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/vm-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 404

		then:
		1 * moabRestService.post({
			assert it.data.name == "vm-utilization"
			assert it.data.description
			assert it.data.consolidationFunction == "average"
			assert it.data.datapointDuration == 10
			assert it.data.reportSize == 2
			assert it.data.keepSamples == false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, [messages: ["message1", "message2"]], false)
		1 * pluginEventService.createEvent(UtilizationReportEvents.REPORT_1NAME_CREATE_ERROR_2MESSAGES,
				['vm-utilization', 'message1, message2'], null)
		0 * _._

		when: "Report does not exist and can be created, but could not get vm information v2"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/vm-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 404
		1 * moabRestService.isAPIVersionSupported(2) >> true
		1 * pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
				['/rest/vms/', 2, null], null)
		_ * pluginEventService.updateNotificationCondition(*_)
		1 * moabRestService.post({
			assert it.data.name == "vm-utilization"
			assert it.data.description
			assert it.data.consolidationFunction == "average"
			assert it.data.datapointDuration == 10
			assert it.data.reportSize == 2
			assert it.data.keepSamples == false
			return true
		}, "/rest/reports/") >> new MoabRestResponse(null, null, true)
		1 * moabRestService.get(['params': ['api-version': 2, 'fields': 'attributes.MOAB_DATACENTER,name']], '/rest/nodes/') >> new MoabRestResponse(null, null, true)
		1 * moabRestService.get({
			assert it.params.fields == "metrics.cpuUtilization,host.name,lastUpdatedDate,states.state,name,resources.memory"
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, null, false)
		0 * _._

		when: "Report does exist but could not get vm information v1"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/vm-utilization") >> new MoabRestResponse(httpResponse, null, false)
		1 * httpResponse.getStatus() >> 200
		1 * moabRestService.isAPIVersionSupported(2) >> false
		1 * pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
				['/rest/vms/', 1, null], null)
		_ * pluginEventService.updateNotificationCondition(*_)
		1 * moabRestService.get({
			assert it.params.fields == "genericMetrics.cpuUtilization,node.id,lastUpdateDate,state,id,availableMemory,totalMemory"
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, null, false)
		0 * _._

		when: "Sample could not be created with no data returned"
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/vm-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * moabRestService.isAPIVersionSupported(2) >> true
		1 * pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION)
		1 * moabRestService.get(['params': ['api-version': 2, 'fields': 'attributes.MOAB_DATACENTER,name']], '/rest/nodes/') >> new MoabRestResponse(null, null, true)
		1 * moabRestService.get(_, "/rest/vms/") >>
				new MoabRestResponse(null, [totalCount: 0, resultCount: 0, results: []], true)

		then:
		1 * moabRestService.post("/rest/reports/vm-utilization/samples/", {
			def result = it.call()
			assert result.agent == "VM Utilization Report Plugin"
			assert result.data.size() == 1
			assert result.data[ALL_DATACENTERS].total == 0
			assert result.data[ALL_DATACENTERS].cpuHigh == 0
			assert result.data[ALL_DATACENTERS].cpuLow == 0
			assert result.data[ALL_DATACENTERS].cpuMedium == 0
			assert result.data[ALL_DATACENTERS].cpuAverage == 0
			assert result.data[ALL_DATACENTERS].memoryHigh == 0
			assert result.data[ALL_DATACENTERS].memoryLow == 0
			assert result.data[ALL_DATACENTERS].memoryMedium == 0
			assert result.data[ALL_DATACENTERS].memoryAverage == 0
			assert result.data[ALL_DATACENTERS].high == 0
			assert result.data[ALL_DATACENTERS].low == 0
			assert result.data[ALL_DATACENTERS].medium == 0
			return true
		}) >> new MoabRestResponse(null, null, false)
		_ * pluginEventService.updateNotificationCondition(*_)
		1 * pluginEventService.createEvent(UtilizationSampleEvents.CREATE_1REPORT_ERROR_2MESSAGES,
				['vm-utilization', null], null)
		0 * _._

		when: "Sample could be created with data returned for all cases v2"
		config.cpuHighThreshold = 75
		config.cpuLowThreshold = 25
		config.memoryHighThreshold = 75
		config.memoryLowThreshold = 25
		plugin.poll()

		then:
		1 * moabRestService.get("/rest/reports/vm-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * moabRestService.isAPIVersionSupported(2) >> true
		1 * pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION)
		_ * pluginEventService.updateNotificationCondition(*_)
		1 * moabRestService.get({
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, [totalCount: 2, resultCount: 2, results: [
				[name: "node01", attributes: [MOAB_DATACENTER: [value:"value", displayValue:"myDC"]]],
				[name: "node02", attributes: [MOAB_DATACENTER: [value:"value", displayValue:"myDC2"]]]
		]], true)
		1 * moabRestService.get({
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, [totalCount: 17, resultCount: 17, results: [
				[name: "vm01", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 100]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 0]],
				[name: "vm02", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 10]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 10]],
				[name: "vm03", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 25]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 25]],
				[name: "vm04", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 75]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 50]],
				[name: "vm05", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 50]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 75]],
				[name: "vm06", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 80]],
				[name: "vm07", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 100]],
				[name: "vm09", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.IDLE.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm10", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.DRAINED.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm11", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.FLUSH.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm12", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.NONE.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm13", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.UNKNOWN.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm14", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.IDLE.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm15", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.RESERVED.toString()], genericMetrics: [cpuUtilization: 100]],
				[name: "vm16", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.IDLE.toString()]],
				[name: "vm17", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [:]],
				[name: "vm18", host: ["name": "node02"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 0]],
						states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: null]]
		]], true)
		7 * pluginDatastoreService.getData(VM_LAST_UPDATED_COLLECTION, "name", _ as String)
		7 * pluginDatastoreService.addData(VM_LAST_UPDATED_COLLECTION, _ as Map) >> true
		7 * moabRestService.put(_, _) >> new MoabRestResponse(null,null,true)

		then:
		1 * moabRestService.post("/rest/reports/vm-utilization/samples/", {
			def result = it.call()
			assert result.agent == "VM Utilization Report Plugin"
			assert result.data.size() == 3
			assert result.data[ALL_DATACENTERS].total == 7
			assert result.data[ALL_DATACENTERS].cpuHigh == 3
			assert result.data[ALL_DATACENTERS].cpuLow == 2
			assert result.data[ALL_DATACENTERS].cpuMedium == 2
			assert result.data[ALL_DATACENTERS].cpuAverage == 48.57142857142857
			assert result.data[ALL_DATACENTERS].memoryHigh == 3
			assert result.data[ALL_DATACENTERS].memoryLow == 2
			assert result.data[ALL_DATACENTERS].memoryMedium == 2
			assert result.data[ALL_DATACENTERS].memoryAverage == 51.42857142857143
			assert result.data[ALL_DATACENTERS].high == 5
			assert result.data[ALL_DATACENTERS].low == 1
			assert result.data[ALL_DATACENTERS].medium == 1
			assert result.data["myDC"].total == 3
			assert result.data["myDC"].cpuHigh == 0
			assert result.data["myDC"].cpuLow == 2
			assert result.data["myDC"].cpuMedium == 1
			assert result.data["myDC"].cpuAverage == 11.666666666666666
			assert result.data["myDC"].memoryHigh == 2
			assert result.data["myDC"].memoryLow == 1
			assert result.data["myDC"].memoryMedium == 0
			assert result.data["myDC"].memoryAverage == 55.0
			assert result.data["myDC"].high == 2
			assert result.data["myDC"].low == 1
			assert result.data["myDC"].medium == 0
			assert result.data["myDC2"].total == 4
			assert result.data["myDC2"].cpuHigh == 3
			assert result.data["myDC2"].cpuLow == 0
			assert result.data["myDC2"].cpuMedium == 1
			assert result.data["myDC2"].cpuAverage == 76.25
			assert result.data["myDC2"].memoryHigh == 1
			assert result.data["myDC2"].memoryLow == 1
			assert result.data["myDC2"].memoryMedium == 2
			assert result.data["myDC2"].memoryAverage == 48.75
			assert result.data["myDC2"].high == 3
			assert result.data["myDC2"].low == 0
			assert result.data["myDC2"].medium == 1
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
		1 * moabRestService.get("/rest/reports/vm-utilization") >> new MoabRestResponse(httpResponse, null, true)
		1 * httpResponse.getStatus() >> 200
		1 * moabRestService.isAPIVersionSupported(2) >> false
		1 * pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION)
		_ * pluginEventService.updateNotificationCondition(*_)
		1 * moabRestService.get({
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, [totalCount: 17, resultCount: 17, results: [
				[id: "vm01", node: [id: "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 100,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 0]],
				[id: "vm02", node: [id: "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 10,
						state: NodeReportState.BUSY.toString(), genericMetrics: [cpuUtilization: 10]],
				[id: "vm03", node: [id: "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 25,
						state: NodeReportState.RUNNING.toString(), genericMetrics: [cpuUtilization: 25]],
				[id: "vm04", node: [id: "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 75,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 50]],
				[id: "vm05", node: [id: "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 50,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 75]],
				[id: "vm06", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 80,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 80]],
				[id: "vm07", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm09", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.DOWN.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm10", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.DRAINED.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm11", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.FLUSH.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm12", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.NONE.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm13", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.UNKNOWN.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm14", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm15", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.RESERVED.toString(), genericMetrics: [cpuUtilization: 100]],
				[id: "vm16", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.IDLE.toString()],
				[id: "vm17", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.IDLE.toString(), genericMetrics: [:]],
				[id: "vm18", node: [id: "node02"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, availableMemory: 0,
						state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: null]],
		]], true)
		13 * pluginDatastoreService.getData(VM_LAST_UPDATED_COLLECTION, "name", _ as String)
		13 * pluginDatastoreService.addData(VM_LAST_UPDATED_COLLECTION, _ as Map) >> true
		13 * moabRestService.put(_, _) >> new MoabRestResponse(null,null,true)

		then:
		1 * moabRestService.post("/rest/reports/vm-utilization/samples/", {
			def result = it.call()
			assert result.agent == "VM Utilization Report Plugin"
			assert result.data.size() == 1
			assert result.data[ALL_DATACENTERS].total == 13
			assert result.data[ALL_DATACENTERS].cpuHigh == 9
			assert result.data[ALL_DATACENTERS].cpuLow == 2
			assert result.data[ALL_DATACENTERS].cpuMedium == 2
			assert result.data[ALL_DATACENTERS].cpuAverage == 72.3076923076923
			assert result.data[ALL_DATACENTERS].memoryHigh == 9
			assert result.data[ALL_DATACENTERS].memoryLow == 2
			assert result.data[ALL_DATACENTERS].memoryMedium == 2
			assert result.data[ALL_DATACENTERS].memoryAverage == 73.84615384615384
			assert result.data[ALL_DATACENTERS].high == 11
			assert result.data[ALL_DATACENTERS].low == 1
			assert result.data[ALL_DATACENTERS].medium == 1
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
		1 * moabRestService.get({
			return true
		}, "/rest/nodes/") >> new MoabRestResponse(null, [totalCount: 2, resultCount: 2, results: [
				[name: "node01", attributes: [MOAB_DATACENTER: [value:"value", displayValue:"myDC"]]],
				[name: "node02", attributes: [MOAB_DATACENTER: [value:"value", displayValue:"myDC2"]]]
		]], true)
		1 * moabRestService.get({
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, [totalCount: 1, resultCount: 1, results: [vm,[name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]]], true)
		1 * moabRestService.post("/rest/reports/vm-utilization/samples/", {
			def result = it.call()
			assert result.agent == "VM Utilization Report Plugin"
			return true
		}) >> new MoabRestResponse(null, null, true)
		1 * pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION) >> [[name: "vm1", lastUpdatedDate: "12:12:12 01-01-00" ]]
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, errorMessage,
				{ it.type=="VM" && it.id==vm.name }, null)
		1 * moabRestService.get('/rest/reports/vm-utilization')
		_ * pluginDatastoreService.getData('vm-last-updated-date', 'name', 'vm1')
		_ * pluginDatastoreService.addData('vm-last-updated-date', ['name':'vm1', 'lastUpdatedDate':'12:12:12 01-01-01'])
		_ * moabRestService.put('/rest/vms/vm1', _ as Closure)
		0 * _._

		where:
		severity | errorMessage															| vm
		"ERROR"	| "vmUtilizationReportPlugin.vm.name.null"								| [host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"ERROR"	| "vmUtilizationReportPlugin.vm.state.null"								| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: null], metrics: [cpuUtilization: 45]]
		"ERROR"	| "vmUtilizationReportPlugin.vm.configuredMemory.null"					| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"ERROR"	| "vmUtilizationReportPlugin.total.memory.zero.message"					| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 0, available: 40]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"ERROR"	| "vmUtilizationReportPlugin.vm.availableMemory.null"					| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"ERROR"	| "vmUtilizationReportPlugin.vm.cpuUtils.null"							| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()]]
		"WARN"	| "vmUtilizationReportPlugin.available.equals.total.memory.message"		| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 80, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"WARN"	| "vmUtilizationReportPlugin.cpu.zero.message"							| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 0]]
		"WARN"	| "vmUtilizationReportPlugin.vm.notUpdated"								| [name: "vm1", host: ["name": "node01"], lastUpdatedDate: "12:12:12 01-01-00", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"WARN"	| "vmUtilizationReportPlugin.vm.host.null"								| [name: "vm1", lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]
		"WARN"	| "vmUtilizationReportPlugin.vm.datacenter.null"						| [name: "vm1", host: ["name": "node04"], lastUpdatedDate: "12:12:12 01-01-01", resources: [memory: [configured: 100, available: 80]], states: [state: NodeReportState.IDLE.toString()], metrics: [cpuUtilization: 45]]

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
		1 * pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION) >> [[name: "vm1", lastUpdatedDate: "12:12:12 01-01-00" ]]
		1 * moabRestService.get({
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, [totalCount: 1, resultCount: 1, results: [vm,[id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]]], true)
		1 * moabRestService.post("/rest/reports/vm-utilization/samples/", {
			def result = it.call()
			assert result.agent == "VM Utilization Report Plugin"
			return true
		}) >> new MoabRestResponse(null, null, true)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, errorMessage,
				{ it.type=="VM" && it.id==vm.id }, null)
		1 * moabRestService.get('/rest/reports/vm-utilization')
		_ * pluginDatastoreService.getData('vm-last-updated-date', 'name', 'vm1')
		_ * pluginDatastoreService.addData('vm-last-updated-date', ['name':'vm1', 'lastUpdatedDate':'12:12:12 01-01-01'])
		_ * moabRestService.put('/rest/vms/vm1', _ as Closure)
		0 * _._

		where:
		severity | errorMessage															| vm
		"ERROR"  | "vmUtilizationReportPlugin.vm.name.null"								| [node: ["name": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
		"ERROR"  | "vmUtilizationReportPlugin.vm.state.null"							| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, states: [state: null], genericMetrics: [cpuUtilization: 45]]
		"ERROR"  | "vmUtilizationReportPlugin.vm.configuredMemory.null"					| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", availableMemory: 80, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
		"ERROR"  | "vmUtilizationReportPlugin.total.memory.zero.message"				| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 0, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
		"ERROR"  | "vmUtilizationReportPlugin.vm.availableMemory.null"					| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 100, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
		"ERROR"  | "vmUtilizationReportPlugin.vm.cpuUtils.null"							| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString()]
		"WARN"   | "vmUtilizationReportPlugin.available.equals.total.memory.message"	| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 2, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
		"WARN"   | "vmUtilizationReportPlugin.cpu.zero.message"							| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 0]]
		"WARN"   | "vmUtilizationReportPlugin.vm.notUpdated"							| [id: "vm1", node: ["id": "node01"], lastUpdateDate: "12:12:12 01-01-00", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
	}

	def "All VMs had errors"() {
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
		1 * pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION) >> [[name: "vm1", lastUpdatedDate: "12:12:12 01-01-00" ]]
		1 * moabRestService.get({
			return true
		}, "/rest/vms/") >> new MoabRestResponse(null, [totalCount: 1, resultCount: 1, results: [vm]], true)
		1 * moabRestService.get('/rest/reports/vm-utilization')
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, errorMessage,
				{ it.type=="VM" && it.id==vm.name }, null)
		1 * pluginEventService.createEvent(UtilizationReportEvents.NO_SAMPLES_1TYPE, null, null)
		0 * _._

		where:
		severity | errorMessage									| vm
		"ERROR"  | "vmUtilizationReportPlugin.vm.name.null"		| [node: ["name": "node01"], lastUpdateDate: "12:12:12 01-01-01", totalMemory: 1, availableMemory: 2, state: NodeReportState.IDLE.toString(), genericMetrics: [cpuUtilization: 45]]
	}
}
