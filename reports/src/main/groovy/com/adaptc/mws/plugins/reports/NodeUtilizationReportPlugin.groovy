package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.AbstractPlugin
import com.adaptc.mws.plugins.IMoabRestService
import com.adaptc.mws.plugins.IPluginDatastoreService
import com.adaptc.mws.plugins.NodeReportState

import static com.adaptc.mws.plugins.PluginConstants.METRIC_CPU_UTILIZATION
import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.IPluginEventService.EscalationLevel
import com.adaptc.mws.plugins.IPluginEventService.AssociatedObject

class NodeUtilizationReportPlugin extends AbstractPlugin {
	IPluginDatastoreService pluginDatastoreService
	IMoabRestService moabRestService
	UtilizationReportTranslator utilizationReportTranslator
	IPluginEventService pluginEventService

	static title = "Node Utilization Report"
	static description = "Creates a node-utilization report with data on node CPU and memory utilization metrics."
	private static final NODE_REPORT_NAME = "node-utilization"
	private static final REPORTS_URL = "/rest/reports/"
	private static final SAMPLES_URL = "/samples/"
	private static final NODES_URL = "/rest/nodes/"
	private static final RESERVATIONS_URL = "/rest/reservations/"
	private static final NODE_LAST_UPDATED_COLLECTION = "node-last-updated-date"
	private static final ALL_DATACENTERS = "all"
	private static final NODE_OBJECT_TYPE = "Node"

	static constraints = {
		// The goal is to keep half a year of data and keep the collection
		// capped at about 20 MB (arbitrary, but seems reasonable).
		// Using the numbers below, the total collection size is about
		// 21 MB (reportDocumentSize * reportSize).
		// Some explanation on the numbers below:
		//   14400: 4 hours, or 6 datapoints per day
		//   20480: 20 KB
		//   1098: 183 (days in half a year) times 6 (datapoints per day)
		cpuHighThreshold type: Double, defaultValue: 75d, min: 0d, max: 100d, validator: { val, obj ->
			if (val < obj.config.cpuLowThreshold)
				return "invalid.less.than.cpuLowThreshold"
		}
		cpuLowThreshold type: Double, defaultValue: 25d, min: 0d, max: 100d
		memoryHighThreshold type: Double, defaultValue: 75d, min: 0d, max: 100d, validator: {val, obj ->
			if (val < obj.config.memoryLowThreshold)
				return "invalid.less.than.memoryLowThreshold"
		}
		memoryLowThreshold type: Double, defaultValue: 25d, min: 0d, max: 100d
		reportConsolidationDuration type: Integer, defaultValue: 14400, min: 1
		reportDocumentSize type: Integer, defaultValue: 20480, min: 1
		reportSize type: Integer, defaultValue: 1098, min: 1
		pollInterval defaultValue: 60
	}

	public void poll() {
		log.debug("Verifying that the ${NODE_REPORT_NAME} report is created")
		if (moabRestService.get(REPORTS_URL + NODE_REPORT_NAME)?.response?.status == 404) {
			log.debug("Report does not exist, creating")
			def createResponse = moabRestService.post(REPORTS_URL, data: getCreateJsonMap())
			if (!createResponse.success) {
				def messages = createResponse.data?.messages?.collect { it?.toString() }?.join(", ")
				pluginEventService.createEvent(UtilizationReportEvents.REPORT_1NAME_CREATE_ERROR_2MESSAGES,
								[NODE_REPORT_NAME, messages], null)
				log.error("Could not create report '${NODE_REPORT_NAME}': ${messages}")
				return
			}
		}

		log.debug("Determining the version of MWS being used")
		def apiVersion
		def metricsField
		def lastUpdatedDateField
		def nameField
		def stateField
		if (moabRestService.isAPIVersionSupported(2)) {
			apiVersion = 2
			nameField = "name"
			metricsField = "metrics"
			lastUpdatedDateField = "lastUpdatedDate"
			stateField = "states.state"
		} else {
			apiVersion = 1
			nameField = "id"
			metricsField = "genericMetrics"
			lastUpdatedDateField = "lastUpdateDate"
			stateField = "state"
		}

		log.debug("Querying the CPU and memory utilization values from the nodes REST API using API version ${apiVersion}")
		def response = moabRestService.get(NODES_URL, params: [
				'api-version': apiVersion,
				fields: "${metricsField}.${METRIC_CPU_UTILIZATION},attributes.MOAB_DATACENTER," +
						"${lastUpdatedDateField},${stateField},${nameField},virtualMachines," +
						(apiVersion == 1 ? 'availableMemory,totalMemory' : 'resources.memory'),
		])
		if (!response?.success) {
			def messages = response?.data?.messages?.collect { it?.toString() }?.join(", ")
			pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
					[NODES_URL, apiVersion, messages], null)
			log.error("Nodes query resulted in error, not creating samples: ${messages}")
			return
		}

		def reservationResponse = moabRestService.get(RESERVATIONS_URL, params: ["api-version" : 1, fields: "label,allocatedNodes,flags,startDate,endDate"])
		if (!reservationResponse?.success) {
			reservationResponse?.data?.messages?.collect { it?.toString() }?.join(", ")
			pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
					[RESERVATIONS_URL, 1, ], null)
			log.error("Reservation query resulted in error, not creating samples: " + reservationResponse?.data?.messages?.join(", "))
			return
		}
		Map nodeUnderReservation = [:]

		reservationResponse?.convertedData?.results.each { def reservation ->
			List flags = reservation.flags
			log.error("Looking at reservation ${flags}")

			long currentTime = new Date().time
			long startTime = moabRestService.convertDateString(reservation.startDate).time
			long endTime = moabRestService.convertDateString(reservation.endDate).time
			if (startTime <= currentTime && currentTime <= endTime) {
				reservation.allocatedNodes.collect {it.id}.each { nodeId ->
					log.debug("Adding node $nodeId to reservation list")
					nodeUnderReservation[nodeId] = true
				}
			}
		}

		def dataCenters = [(ALL_DATACENTERS): utilizationReportTranslator.getDefaultSampleDatacenter()]
		def data = pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)
		int nodeErrorCount = 0
		int nodeSuccessCount = 0

		response?.convertedData.results.each {
			String nodeName = it[nameField]
			if (!nodeName) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.name.null", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				return
			}

			NodeReportState state
			Integer realMemory
			Integer availableMemory
			String dataCenter

			if (apiVersion == 1) {
				state = NodeReportState.parse(it?.state)
				realMemory = it?.totalMemory
				availableMemory = it?.availableMemory
			} else {
				state = NodeReportState.parse(it?.states?.state)
				realMemory = it?.resources?.memory?.real
				availableMemory = it?.resources.memory?.available

				//Include all datacenters regardless if we skip the nodes in them or not
				dataCenter = it?.attributes?.MOAB_DATACENTER?.displayValue
				if (!dataCenter) {
					nodeErrorCount++
					pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
							message(code: "nodeUtilizationReportPlugin.node.datacenter.null", args: [nodeName]),
							new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
					)
					log.warn("The node '${nodeName}' has a null datacenter")
				}

				if (dataCenter && !dataCenters[dataCenter])
					dataCenters[dataCenter] = utilizationReportTranslator.getDefaultSampleDatacenter()
			}

			def nodeLastUpdatedTime = data.find { it.name == nodeName }

			if (nodeLastUpdatedTime?.lastUpdatedDate == it[lastUpdatedDateField]) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.notUpdated", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.warn("Not including node ${nodeName} in the node-utilization report because it has not been updated " +
						"since the last poll at ${it[lastUpdatedDateField]}")
				return
			}

			utilizationReportTranslator.addOrUpdateData(pluginDatastoreService, NODE_LAST_UPDATED_COLLECTION,
					nodeName, [name: nodeName, lastUpdatedDate: it[lastUpdatedDateField]])

			if (nodeUnderReservation[nodeName]  && it.virtualMachines?.size() == 0) {
				log.warn(message(code: "nodeUtilizationReportPlugin.node.reserved", args: [nodeName]))
				return
			}

			if (state == null) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.state.null", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.error("Not including Node ${nodeName} in the node-utilization report because its state is null.")
				return
			}

			if (state == NodeReportState.DOWN) {
				log.info("Not including Node ${nodeName} in the node-utilization report because the report does not include nodes that are down.")
				return
			} else if (state != NodeReportState.RUNNING && state != NodeReportState.BUSY && state != NodeReportState.IDLE) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.state.invalid", args: [nodeName, state.toString()]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
			}

			def cpuUtils = it?.getAt(metricsField)?.getAt(METRIC_CPU_UTILIZATION)

			if (cpuUtils == null) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.cpuUtils.null", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the CPU utilization " +
						"metric is not present or null")
				return
			}

			if (cpuUtils == 0) {
				//In this case we do not ignore the node
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.cpu.zero.message", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.warn("Node ${nodeName} has CPU utilization set to 0")
			}

			if (realMemory == null) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.realMemory.null", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the real memory reported is null")
				return
			}

			if (realMemory == 0) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.total.memory.zero.message", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the real memory reported is 0")
				return
			}

			if (availableMemory == null) {
				nodeErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.node.availableMemory.null", args: [nodeName]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the available memory reported is null")
				return
			}

			if (availableMemory == realMemory) {
				nodeErrorCount++
				//In this case we do not ignore the node
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "nodeUtilizationReportPlugin.available.equals.total.memory.message",
								args: [nodeName, availableMemory, realMemory]),
						new AssociatedObject(type:NODE_OBJECT_TYPE, id:nodeName), null
				)
				log.warn("Node ${nodeName} has available and total memory set to the same value")
			}

			Double memoryUtils = utilizationReportTranslator.calculateUtilization((double) realMemory,
					(double) availableMemory)

			UtilizationLevel cpuUtilLevel = utilizationReportTranslator.getUtilizationLevel(cpuUtils,
					config.cpuLowThreshold, config.cpuHighThreshold)
			UtilizationLevel memoryUtilLevel = utilizationReportTranslator.getUtilizationLevel(memoryUtils,
					config.memoryLowThreshold, config.memoryHighThreshold)
			UtilizationLevel bothUtilLevel = utilizationReportTranslator.getCPUAndMemoryUtilizationLevel(
					cpuUtilLevel, memoryUtilLevel)

			//Update Nodes with categories
			def nodesResponse = moabRestService.put(NODES_URL + nodeName) {
				[variables:[CPU_UTILIZATION_CATEGORY:cpuUtilLevel,MEMORY_UTILIZATION_CATEGORY:memoryUtilLevel, CPU_AND_MEMORY_UTILIZATION_CATEGORY:bothUtilLevel]]
			}

			if (!nodesResponse?.success)
				log.warn("Failed to update node $nodeName with utilization categories.")

			if (dataCenter)
				utilizationReportTranslator.countUtilizationLevels(dataCenters, dataCenter, cpuUtilLevel,
						memoryUtilLevel, bothUtilLevel, cpuUtils, memoryUtils)
			utilizationReportTranslator.countUtilizationLevels(dataCenters, ALL_DATACENTERS, cpuUtilLevel,
					memoryUtilLevel, bothUtilLevel, cpuUtils, memoryUtils)
			nodeSuccessCount++
		}

		dataCenters.each {dataCenterName, metrics ->
			// Do not divide by 0
			if (metrics.total != 0) {
				metrics.cpuAverage /= metrics.total
				metrics.memoryAverage /= metrics.total
			}
		}

		if(!nodeSuccessCount && nodeErrorCount) {
			pluginEventService.createEvent(UtilizationReportEvents.NO_SAMPLES_1TYPE, ["nodes"], null)
		} else {
			response = moabRestService.post(REPORTS_URL + NODE_REPORT_NAME + SAMPLES_URL) {
				[
						agent: "Node Utilization Report Plugin",
						data: dataCenters,
				]
			}
			if (response?.success)
				log.debug("Successfully created sample for node utilization report")
			else {
				pluginEventService.createEvent(UtilizationSampleEvents.CREATE_1REPORT_ERROR_2MESSAGES,
						[NODE_REPORT_NAME, response?.data?.messages?.join(", ")], null)
				log.error("Could not create sample for node utilization report: ${response?.data?.messages?.join(", ")}")
			}
		}

	}

	public def recreateReport(Map params) {
		// Blow away the report and the data (use the new datapointDuration)
		log.info("Destroying (if it exists) and recreating node utilization report")
		moabRestService.delete(REPORTS_URL + NODE_REPORT_NAME)
		log.debug("Recreating report")
		def messages = []
		def response = moabRestService.post(REPORTS_URL, data: getCreateJsonMap())
		if (response?.success)
			messages << message(code: "nodeUtilizationReportPlugin.recreateReport.success.message", args: [NODE_REPORT_NAME])
		else {
			messages << message(code: "nodeUtilizationReportPlugin.recreateReport.failure.message", args: [NODE_REPORT_NAME])
			if (response?.data?.messages)
				messages.addAll(response?.data.messages)
		}
		return [
				messages: messages
		]
	}

	private Map getCreateJsonMap() {
		return [
				name: NODE_REPORT_NAME,
				description: "The report for node CPU and memory utilization produced by the NodeUtilizationReport plugin",
				consolidationFunction: "average",
				datapointDuration: config.reportConsolidationDuration,
				reportSize: config.reportSize,
				keepSamples: false,
				reportDocumentSize: config.reportDocumentSize
		]
	}
}
