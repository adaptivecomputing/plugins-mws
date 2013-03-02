package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.NodeReportState

import static com.adaptc.mws.plugins.PluginConstants.*

class NodeUtilizationReportPlugin extends AbstractPlugin {
	IPluginDatastoreService pluginDatastoreService
	IMoabRestService moabRestService
	UtilizationReportTranslator utilizationReportTranslator
	def grailsApplication

	static title = "Node Utilization Report"
	static description = "Creates a node-utilization report with data on node CPU and memory utilization metrics."
	private static final NODE_REPORT_NAME = "node-utilization"
	private static final REPORTS_URL = "/rest/reports/"
	private static final SAMPLES_URL = "/samples"
	private static final NODES_URL = "/rest/nodes"
	private static final NODE_LAST_UPDATED_COLLECTION = "node-last-updated-date"
	private static final ALL_DATACENTERS = "all"


	static constraints = {
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
		reportConsolidationDuration type: Integer, defaultValue: 900, min: 1
		reportDocumentSize type: Integer, defaultValue: 20480, min: 1
		reportSize type: Integer, min: 1
		pollInterval defaultValue: 60
	}

	public void poll() {
		log.debug("Verifying that the ${NODE_REPORT_NAME} report is created")
		if (moabRestService.get(REPORTS_URL + NODE_REPORT_NAME)?.response?.status == 404) {
			log.debug("Report does not exist, creating")
			def createResponse = moabRestService.post(REPORTS_URL, data: getCreateJsonMap())
			if (!createResponse.success) {
				logEvent(message(code: "nodeUtilizationReportPlugin.could.not.create.report", args: [NODE_REPORT_NAME, createResponse.data?.messages?.join(", ")]),
						"NodeReportCreationFailure",
						"ERROR",
						NODE_REPORT_NAME,
						"reports"
				)
				log.error("Could not create report '${NODE_REPORT_NAME}': ${createResponse.data?.messages?.join(", ")}")
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
				fields: "${metricsField}.${METRIC_CPU_UTILIZATION},attributesExtended.MOAB_DATACENTER," +
						"${lastUpdatedDateField},${stateField},${nameField}," +
						(apiVersion == 1 ? 'availableMemory,totalMemory' : 'resources.memory'),
		])
		if (!response?.success) {
			logEvent(message(code: "nodeUtilizationReportPlugin.node.query.error", args: [apiVersion, response?.data?.messages?.join(", ")]),
					"NodeQueryFailure",
					"ERROR",
					null
			)
			log.error("Nodes query resulted in error, not creating samples: " + response?.data?.messages?.join(", "))
			return
		}

		def dataCenters = [(ALL_DATACENTERS): utilizationReportTranslator.getDefaultSampleDatacenter()]
		def data = pluginDatastoreService.getCollection(NODE_LAST_UPDATED_COLLECTION)

		response?.convertedData.results.each {
			String nodeName = it[nameField]
			if (!nodeName) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.name.null", args: [nodeName]),
						"InvalidVirtualMachineProperties",
						"ERROR",
						nodeName
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
				dataCenter = it?.attributesExtended?.MOAB_DATACENTER?.displayName
				if (!dataCenter) {
					logEvent(message(code: "nodeUtilizationReportPlugin.node.datacenter.null", args: [nodeName]),
							"InvalidNodeProperties",
							"WARN",
							nodeName
					)
					log.warn("Not including node ${nodeName}'s dataCenter in the node-utilization report because it was null.")
				}

				if (dataCenter && !dataCenters[dataCenter])
					dataCenters[dataCenter] = utilizationReportTranslator.getDefaultSampleDatacenter()
			}

			def nodeLastUpdatedTime = data.find { it.name == nodeName }

			if (nodeLastUpdatedTime?.lastUpdatedDate == it[lastUpdatedDateField]) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.notUpdated", args: [nodeName]),
						"InvalidNodeProperties",
						"WARN",
						nodeName
				)
				log.warn("Not including node ${nodeName} in the node-utilization report because it has not been updated " +
						"since the last poll at ${it[lastUpdatedDateField]}")
			}

			utilizationReportTranslator.addOrUpdateData(pluginDatastoreService, NODE_LAST_UPDATED_COLLECTION,
					nodeName, [name: nodeName, lastUpdatedDate: it[lastUpdatedDateField]])

			if (state == null) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.state.null", args: [nodeName]),
						"InvalidNodeProperties",
						"ERROR",
						nodeName
				)
				log.error("Not including Node ${nodeName} in the node-utilization report because its state is null.")
				return
			}

			if (state == NodeReportState.DOWN) {
				log.info("Not including Node ${nodeName} in the node-utilization report because the report does not include nodes that are down.")
				return
			} else if (state != NodeReportState.RUNNING && state != NodeReportState.BUSY && state != NodeReportState.IDLE) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.state.invalid", args: [nodeName, state.toString()]),
						"InvalidNodeProperties",
						"WARN",
						nodeName
				)
			}

			def cpuUtils = it?.getAt(metricsField)?.getAt(METRIC_CPU_UTILIZATION)

			if (cpuUtils == null) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.cpuUtils.null", args: [nodeName]),
						"InvalidNodeProperties",
						"ERROR",
						nodeName
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the CPU utilization " +
						"metric is not present or null")
				return
			}

			if (cpuUtils == 0) {
				//In this case we do not ignore the node
				logEvent(message(code: "nodeUtilizationReportPlugin.cpu.zero.message", args: [nodeName]),
						"InvalidNodeProperties",
						"WARN",
						nodeName
				)
				log.warn("Node ${nodeName} has CPU utilization set to 0")
			}

			if (realMemory == null) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.realMemory.null", args: [nodeName]),
						"InvalidNodeProperties",
						"ERROR",
						nodeName
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the real memory reported is null")
				return
			}

			if (realMemory == 0) {
				logEvent(message(code: "nodeUtilizationReportPlugin.total.memory.zero.message", args: [nodeName]),
						"InvalidNodeProperties",
						"ERROR",
						nodeName
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the real memory reported is 0")
				return
			}

			if (availableMemory == null) {
				logEvent(message(code: "nodeUtilizationReportPlugin.node.availableMemory.null", args: [nodeName]),
						"InvalidNodeProperties",
						"ERROR",
						nodeName
				)
				log.error("Not including node ${nodeName} in the node-utilization report because the available memory reported is null")
				return
			}

			if (availableMemory == realMemory) {
				//In this case we do not ignore the node
				logEvent(message(code: "nodeUtilizationReportPlugin.available.equals.total.memory.message", args: [
						nodeName, availableMemory, realMemory
				]),
						"InvalidNodeProperties",
						"WARN",
						nodeName
				)
				log.warn("Node ${nodeName} has available and total memory set to the same value")
			}

			Double memoryUtils = utilizationReportTranslator.calculateUtilization((double) realMemory,
					(double) availableMemory)

			UtilizationLevel cpuUtilLevel = utilizationReportTranslator.getUtilizationLevel(cpuUtils,
					config.cpuLowThreshold, config.cpuHighThreshold)
			UtilizationLevel memoryUtilLevel = utilizationReportTranslator.getUtilizationLevel(memoryUtils,
					config.memoryLowThreshold, config.memoryHighThreshold)

			if (dataCenter)
				utilizationReportTranslator.countUtilizationLevels(dataCenters, dataCenter, cpuUtilLevel,
						memoryUtilLevel, cpuUtils, memoryUtils)
			utilizationReportTranslator.countUtilizationLevels(dataCenters, ALL_DATACENTERS, cpuUtilLevel,
					memoryUtilLevel, cpuUtils, memoryUtils)
		}

		dataCenters.each {dataCenterName, metrics ->
			// Do not divide by 0
			if (metrics.total != 0) {
				metrics.cpuAverage /= metrics.total
				metrics.memoryAverage /= metrics.total
			}
		}

		response = moabRestService.post(REPORTS_URL + NODE_REPORT_NAME + SAMPLES_URL) {
			[
					agent: "Node Utilization Report Plugin",
					data: dataCenters,
			]
		}
		if (response?.success)
			log.debug("Successfully created sample for node utilization report")
		else
			logEvent(message(code: "nodeUtilizationReportPlugin.could.not.create.report.sample", args: [response?.data?.messages?.join(", ")]),
					"NodeReportSampleCreationFailure",
					"ERROR",
					NODE_REPORT_NAME,
					"reports"
			)
		log.error("Could not create sample for node utilization report: ${response?.data?.messages?.join(", ")}")
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

	private void logEvent(String message, String type, String severity, String objectId, String objectType = "node") {
		def response = moabRestService.post("/rest/events") {
			[
					details: [
							pluginId: id,
					],
					errorMessage: [
							message: message,
					],
					eventCategory: "nodeReport",
					eventTime: new Date(),
					eventType: type,
					facility: "reporting",
					primaryObject: [
							id: objectId,
							type: objectType,
					],
					sourceComponent: "NodeUtilizationReportPlugin",
					severity: severity
			]
		}
		if (response?.success)
			log.trace("Successfully logged event")
		else
			log.trace("Could not log event ${message} (${objectId})")
	}
}
