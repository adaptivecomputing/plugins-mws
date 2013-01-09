package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.AbstractPlugin
import com.adaptc.mws.plugins.IMoabRestService
import com.adaptc.mws.plugins.IPluginDatastoreService
import com.adaptc.mws.plugins.NodeReportState
import net.sf.json.JSONNull

import static com.adaptc.mws.plugins.PluginConstants.METRIC_CPU_UTILIZATION

class VMUtilizationReportPlugin extends AbstractPlugin {
	IPluginDatastoreService pluginDatastoreService
	IMoabRestService moabRestService
	UtilizationReportTranslator utilizationReportTranslator
	def grailsApplication

	static title = "Virtual Machine Utilization Report"
	static description = "Creates a vm-utilization report with data on vm CPU and memory utilization metrics."
	private static final VM_REPORT_NAME = "vm-utilization"
	private static final REPORTS_URL = "/rest/reports/"
	private static final SAMPLES_URL = "/samples"
	private static final NODES_URL = "/rest/nodes"
	private static final VMS_URL = "/rest/vms"
	private static final VM_LAST_UPDATED_COLLECTION = "vm-last-updated-date"
	private static final ALL_DATACENTERS = "all"


	static constraints = {
		cpuHighThreshold type:Double, defaultValue:75d, min:0d, max:100d, validator:{ val, obj ->
			if (val<obj.config.cpuLowThreshold)
				return "invalid.less.than.cpuLowThreshold"
		}
		cpuLowThreshold type:Double, defaultValue:25d, min:0d, max:100d
		memoryHighThreshold type:Double, defaultValue: 75d, min: 0d, max: 100d, validator: {val, obj ->
			if (val<obj.config.memoryLowThreshold)
				return "invalid.less.than.memoryLowThreshold"
		}
		memoryLowThreshold type:Double, defaultValue: 25d, min: 0d, max: 100d
		reportConsolidationDuration type:Integer, defaultValue:900, min:1
		reportDocumentSize type:Integer, defaultValue: 20480, min:1
		reportSize type:Integer, min:1
		pollInterval defaultValue:60
	}

	public void poll() {
		log.debug("Verifying that the ${VM_REPORT_NAME} report is created")
		if (moabRestService.get(REPORTS_URL + VM_REPORT_NAME).response.status == 404) {
			log.debug("Report does not exist, creating")
			def createResponse = moabRestService.post(REPORTS_URL, data: getCreateJsonMap())
			if (!createResponse.success) {
				logEvent(message(code: "vmUtilizationReportPlugin.could.not.create.report", args: [VM_REPORT_NAME, createResponse.data?.messages?.join(", ")]),
						"VMReportCreationFailure",
						"error",
						VM_REPORT_NAME,
						"reports"
				)
				log.error("Could not create report '${VM_REPORT_NAME}': ${createResponse.data?.messages?.join(", ")}")
				return
			}
		}

		log.debug("Determining the version of MWS being used")
		def apiVersion = 1
		def metricsField = "genericMetrics"
		def lastUpdatedDateField = "lastUpdateDate"
		def nameField = "id"
		def hostField = "node.id"
		def stateField = "state"
		def nodesResponse

		if (!grailsApplication.metadata.'app.version'?.startsWith("7.1")) {
			nodesResponse = moabRestService.get(NODES_URL, params: [
					'api-version': 2,
					fields: "attributes.MOAB_DATACENTER,name",
			])

			if (nodesResponse.success) {
				apiVersion = 2
				nameField = "name"
				metricsField = "metrics"
				lastUpdatedDateField = "lastUpdatedDate"
				hostField = "host.name"
				stateField = "states.state"
			} else {
				log.warn("Node query using api version 2 failed resulted in error, using api version 1 which does not include data centers.  Error messages are : " + response.data?.messages?.join(", "))
			}

		}

		log.debug("Querying the CPU and memory utilization values from the vms REST API using API version ${apiVersion}")
		def response = moabRestService.get(VMS_URL, params:[
				'api-version':apiVersion,
				fields: "${metricsField}.${METRIC_CPU_UTILIZATION},${hostField},"+
						"${lastUpdatedDateField},${stateField},${nameField},"+
						(apiVersion==1?'availableMemory,totalMemory':'resources.memory'),
			])

		if (!response.success) {
			logEvent(message(code: "vmUtilizationReportPlugin.vm.query.error", args : [apiVersion, response.data?.messages?.join(", ")]),
					"VMQueryFailure",
					"error",
					null
			)
			log.error("Vms query resulted in error, not creating samples: " + response.data?.messages?.join(", "))
			return
		}

		def dataCenters = [(ALL_DATACENTERS): utilizationReportTranslator.getDefaultSampleDatacenter()]
		def data = pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION)

		response.data.results.each {
			String vmName = it?.getAt(nameField)
			def cpuUtils = it?.getAt(metricsField)?.getAt(METRIC_CPU_UTILIZATION)

			NodeReportState state
			Integer configuredMemory
			Integer availableMemory
			def dataCenter

			if (apiVersion==1) {
				state = NodeReportState.parse(it?.state)
				configuredMemory = it?.totalMemory
				availableMemory = it?.availableMemory
			} else {
				String hostName = it?.host?.name
				state = NodeReportState.parse(it?.states?.state)
				configuredMemory = it?.resources?.memory?.configured
				availableMemory = it?.resources.memory?.available
				if (!hostName || hostName instanceof JSONNull) {
					log.warn("Not including VM ${vmName}'s dataCenter in the vm-utilization report because its host name is null.")
				}
				//Include all datacenters regardless if we skip the vms in them or not
				dataCenter = nodesResponse.data.results.find{it?.getAt(nameField) == hostName}?.attributes?.MOAB_DATACENTER
				if(dataCenter && !dataCenters[dataCenter])
					dataCenters[dataCenter] = utilizationReportTranslator.getDefaultSampleDatacenter()
			}

			if (!vmName || vmName instanceof JSONNull) {
				logEvent(message(code:"vmUtilizationReportPlugin.vm.name.null", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because its name is null.")
				return
			}

			if (!state) {
				logEvent(message(code:"vmUtilizationReportPlugin.vm.state.null", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because its state is null.")
				return
			}

			if (configuredMemory == null || configuredMemory instanceof JSONNull) {
				logEvent(message(code:"vmUtilizationReportPlugin.vm.configuredMemory.null", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because the configured memory reported is null")
				return
			}

			if(configuredMemory == 0) {
				logEvent(message(code:"vmUtilizationReportPlugin.total.memory.zero.message", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"error",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because the configured memory reported is 0")
				return
			}

			if (availableMemory == null || availableMemory instanceof JSONNull) {
				logEvent(message(code:"vmUtilizationReportPlugin.vm.availableMemory.null", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because the available memory reported is null")
				return
			}

			if (availableMemory == configuredMemory) {
				logEvent(message(code:"vmUtilizationReportPlugin.available.equals.total.memory.message", args:[
						vmName, availableMemory, configuredMemory
				]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("VM ${vmName} has available and total memory set to the same value")
			}

			// Make sure that the METRIC_CPU_UTILIZATION is not null
			if (cpuUtils==null || cpuUtils instanceof JSONNull) {
				logEvent(message(code:"vmUtilizationReportPlugin.vm.cpuUtils.null", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because the CPU utilization is null")
				return
			}

			if (cpuUtils==0) {
				logEvent(message(code:"vmUtilizationReportPlugin.cpu.zero.message", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("VM ${vmName} has CPU utilization set to 0")
			}

			if(state!=NodeReportState.RUNNING && state != NodeReportState.BUSY && state != NodeReportState.IDLE) {
				log.info("Not including VM ${vmName} in the vm-utilization report because it has a state of ${state} and the report shows only Idle, Busy, and Running vms")
				return
			}

			def vmLastUpdatedTime = data.find{it.name == vmName}

			if(vmLastUpdatedTime?.lastUpdatedDate == it[lastUpdatedDateField]) {
				logEvent(message(code:"vmUtilizationReportPlugin.vm.notUpdated", args:[vmName]),
						"InvalidVirtualMachineProperties",
						"warn",
						vmName
				)
				log.warn("Not including VM ${vmName} in the vm-utilization report because it has not been updated since the last poll at ${it[lastUpdatedDateField]}")
				return
			}

			utilizationReportTranslator.addOrUpdateData(pluginDatastoreService, VM_LAST_UPDATED_COLLECTION,
					vmName, [name:vmName, lastUpdatedDate: it[lastUpdatedDateField]])

			Double memoryUtils = utilizationReportTranslator.calculateUtilization((double)configuredMemory,
					(double)availableMemory)

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

		dataCenters.each {dataCenterName, metrics->
			// Do not divide by 0
			if (metrics.total != 0) {
				metrics.cpuAverage /= metrics.total
				metrics.memoryAverage /= metrics.total
			}
		}

		response = moabRestService.post(REPORTS_URL+VM_REPORT_NAME+SAMPLES_URL) {
			[
					agent:"VM Utilization Report Plugin",
					data:dataCenters,
			]
		}
		if (response.success)
			log.debug("Successfully created sample for VM utilization report")
		else
			log.warn("Could not create sample for VM utilization report: ${response.data?.messages?.join(", ")}")
	}

	public def recreateReport(Map params) {
		// Blow away the report and the data (use the new datapointDuration)
		log.info("Destroying (if it exists) and recreating VM utilization report")
		moabRestService.delete(REPORTS_URL+VM_REPORT_NAME)
		log.debug("Recreating report")
		def messages = []
		def response = moabRestService.post(REPORTS_URL, data:getCreateJsonMap())
		if (response.success)
			messages << message(code:"vmUtilizationReportPlugin.recreateReport.success.message", args:[VM_REPORT_NAME])
		else {
			messages << message(code:"vmUtilizationReportPlugin.recreateReport.failure.message", args:[VM_REPORT_NAME])
			if (response.data?.messages)
				messages.addAll(response.data.messages)
		}
		return [
			messages:messages
		]
	}

	private Map getCreateJsonMap() {
		return [
				name:VM_REPORT_NAME,
				description:"The report for VM CPU and memory utilization produced by the VMUtilizationReport plugin",
				consolidationFunction:"average",
				datapointDuration:config.reportConsolidationDuration,
				reportSize:config.reportSize,
				keepSamples:false,
				reportDocumentSize:config.reportDocumentSize
		]
	}

	private void logEvent(String message, String type, String status, String objectId, String objectType = "vm") {
		def response = moabRestService.post("/rest/events") {
			[
					details:[
							pluginId:id,
					],
					errorMessage:[
							message:message,
					],
					eventCategory:"vmReport",
					eventTime:new Date(),
					eventType:type,
					facility:"reporting",
					primaryObject:[
							id:objectId,
							type: objectType,
					],
					sourceComponent:"VMUtilizationReportPlugin",
					status:status
			]
		}
		if (response.success)
			log.trace("Successfully logged event")
		else
			log.trace("Could not log event ${message} (${objectId})")
	}
}