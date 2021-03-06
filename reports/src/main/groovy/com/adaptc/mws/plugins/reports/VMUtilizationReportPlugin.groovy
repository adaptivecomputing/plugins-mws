package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.AbstractPlugin
import com.adaptc.mws.plugins.IMoabRestService
import com.adaptc.mws.plugins.IPluginDatastoreService
import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.IPluginEventService.AssociatedObject
import com.adaptc.mws.plugins.IPluginEventService.EscalationLevel
import com.adaptc.mws.plugins.NodeReportState

import static com.adaptc.mws.plugins.PluginConstants.METRIC_CPU_UTILIZATION

class VMUtilizationReportPlugin extends AbstractPlugin {
	IPluginDatastoreService pluginDatastoreService
	IMoabRestService moabRestService
	UtilizationReportTranslator utilizationReportTranslator
	IPluginEventService pluginEventService

	static title = "Virtual Machine Utilization Report"
	static description = "Creates a vm-utilization report with data on vm CPU and memory utilization metrics."
	private static final VM_REPORT_NAME = "vm-utilization"
	private static final REPORTS_URL = "/rest/reports/"
	private static final SAMPLES_URL = "/samples/"
	private static final NODES_URL = "/rest/nodes/"
	private static final VMS_URL = "/rest/vms/"
	private static final VM_LAST_UPDATED_COLLECTION = "vm-last-updated-date"
	private static final ALL_DATACENTERS = "all"
	private static final VM_OBJECT_TYPE = "VM"

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
		log.debug("Verifying that the ${VM_REPORT_NAME} report is created")
		if (moabRestService.get(REPORTS_URL + VM_REPORT_NAME, params: ['api-version': 3])?.response?.status == 404) {
			log.debug("Report does not exist, creating")
			def createResponse = moabRestService.post(REPORTS_URL, params: ['api-version': 3], data: getCreateJsonMap())
			if (!createResponse.success) {
				def messages = createResponse.data?.messages?.collect { it?.toString() }?.join(", ")
				pluginEventService.createEvent(UtilizationReportEvents.REPORT_1NAME_CREATE_ERROR_2MESSAGES,
					[VM_REPORT_NAME, messages], null)
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

		if (moabRestService.isAPIVersionSupported(3)) {
			nodesResponse = moabRestService.get(NODES_URL, params: [
					'api-version': 3,
					fields: "attributes.MOAB_DATACENTER,name",
			])
			apiVersion = 3
			nameField = "name"
			metricsField = "metrics"
			lastUpdatedDateField = "lastUpdatedDate"
			hostField = "host.name"
			stateField = "states.state"
		}

		log.debug("Querying the CPU and memory utilization values from the vms REST API using API version ${apiVersion}")
		def response = moabRestService.get(VMS_URL, params: [
				'api-version': apiVersion,
				fields: "${metricsField}.${METRIC_CPU_UTILIZATION},${hostField},variables," +
						"${lastUpdatedDateField},${stateField},${nameField}," +
						(apiVersion == 1 ? 'availableMemory,totalMemory' : 'resources.memory'),
		])

		if (!response?.success) {
			def messages = response?.data?.messages?.collect { it?.toString() }?.join(", ")
			pluginEventService.createEvent(ResourceQueryEvents.QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES,
				[VMS_URL, apiVersion, messages], null)
			log.error("Vms query resulted in error, not creating samples: ${messages}")
			return
		}

		def dataCenters = [(ALL_DATACENTERS): utilizationReportTranslator.getDefaultSampleDatacenter()]
		def data = pluginDatastoreService.getCollection(VM_LAST_UPDATED_COLLECTION)
		int vmErrorCount = 0
		int vmSuccessCount = 0

		response?.convertedData?.results?.each {
			String vmName = it?.getAt(nameField)
			if (!vmName) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.vm.name.null", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				return
			}

			NodeReportState state
			Integer configuredMemory
			Integer availableMemory
			def dataCenter

			if (apiVersion == 1) {
				state = NodeReportState.parse(it?.state)
				configuredMemory = it?.totalMemory
				availableMemory = it?.availableMemory
			} else {
				def hostName = it?.host?.name
				state = NodeReportState.parse(it?.states?.state)
				configuredMemory = it?.resources?.memory?.configured
				availableMemory = it?.resources.memory?.available
				log.debug("hostName is $hostName")
				if (!hostName) {
					vmErrorCount++
					pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
							message(code: "vmUtilizationReportPlugin.vm.host.null", args: [vmName]),
							new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
					log.warn("Not including VM ${vmName}'s dataCenter in the vm-utilization report because its host name is null.")
				}

				//Include all datacenters regardless if we skip the vms in them or not
				dataCenter = nodesResponse.convertedData.results.find {it?.getAt(nameField) == hostName}?.attributes?.MOAB_DATACENTER?.displayValue
				if (hostName && !dataCenter) {
					vmErrorCount++
					pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
							message(code: "vmUtilizationReportPlugin.vm.datacenter.null", args: [hostName]),
							new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
					log.warn("Not including VM ${vmName}'s dataCenter in the vm-utilization report because its host name is null.")
				}

				if (dataCenter && !dataCenters[dataCenter])
					dataCenters[dataCenter] = utilizationReportTranslator.getDefaultSampleDatacenter()
			}

			if (!state) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.vm.state.null", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.error("Not including VM ${vmName} in the vm-utilization report because its state is null.")
				return
			}

			if (state == NodeReportState.DOWN) {
				vmErrorCount++
				log.info("Not including VM ${vmName} in the vm-utilization report because the report does not include vms that are down.")
				return
			}

			if (configuredMemory == null) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.vm.configuredMemory.null", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.error("Not including VM ${vmName} in the vm-utilization report because the configured memory reported is null")
				return
			}

			if (configuredMemory == 0) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.total.memory.zero.message", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.error("Not including VM ${vmName} in the vm-utilization report because the configured memory reported is 0")
				return
			}

			if (availableMemory == null) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.vm.availableMemory.null", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.error("Not including VM ${vmName} in the vm-utilization report because the available memory reported is null")
				return
			}

			if (availableMemory == configuredMemory) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.available.equals.total.memory.message",
								args: [vmName, availableMemory, configuredMemory]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.warn("VM ${vmName} has available and total memory set to the same value")
			}

			def cpuUtils = it?.getAt(metricsField)?.getAt(METRIC_CPU_UTILIZATION)

			if (cpuUtils == null) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.vm.cpuUtils.null", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.error("Not including VM ${vmName} in the vm-utilization report because the CPU utilization is null")
				return
			}

			if (cpuUtils == 0) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.cpu.zero.message", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.warn("VM ${vmName} has CPU utilization set to 0")
			}

			def vmLastUpdatedTime = data.find {it.name == vmName}

			if (vmLastUpdatedTime?.lastUpdatedDate == it[lastUpdatedDateField]) {
				vmErrorCount++
				pluginEventService.updateNotificationCondition(EscalationLevel.ADMIN,
						message(code: "vmUtilizationReportPlugin.vm.notUpdated", args: [vmName]),
						new AssociatedObject(type:VM_OBJECT_TYPE, id:vmName), null)
				log.warn("Not including VM ${vmName} in the vm-utilization report because it has not been updated since the last poll at ${it[lastUpdatedDateField]}")
				return
			}

			utilizationReportTranslator.addOrUpdateData(pluginDatastoreService, VM_LAST_UPDATED_COLLECTION,
					vmName, [name: vmName, lastUpdatedDate: it[lastUpdatedDateField]])

			Double memoryUtils = utilizationReportTranslator.calculateUtilization((double) configuredMemory,
					(double) availableMemory)

			UtilizationLevel cpuUtilLevel = utilizationReportTranslator.getUtilizationLevel(cpuUtils,
					config.cpuLowThreshold, config.cpuHighThreshold)
			UtilizationLevel memoryUtilLevel = utilizationReportTranslator.getUtilizationLevel(memoryUtils,
					config.memoryLowThreshold, config.memoryHighThreshold)
			UtilizationLevel bothUtilLevel = utilizationReportTranslator.getCPUAndMemoryUtilizationLevel(cpuUtilLevel, memoryUtilLevel)


			if (!it.variables?.CPU_UTILIZATION_CATEGORY ||
					UtilizationLevel.parse(it.variables?.CPU_UTILIZATION_CATEGORY) != cpuUtilLevel ||
					UtilizationLevel.parse(it.variables?.MEMORY_UTILIZATION_CATEGORY) != memoryUtilLevel ||
					UtilizationLevel.parse(it.variables?.CPU_AND_MEMORY_UTILIZATION_CATEGORY) != bothUtilLevel
			) {
				//Update VMs with categories
				def vmsResponse = moabRestService.put(VMS_URL + vmName, params: ['api-version': 3]) {
					[variables: [CPU_UTILIZATION_CATEGORY: cpuUtilLevel, MEMORY_UTILIZATION_CATEGORY: memoryUtilLevel, CPU_AND_MEMORY_UTILIZATION_CATEGORY: bothUtilLevel]]
				}

				if (!vmsResponse?.success)
					log.warn("Failed to update vm $vmName with utilization categories.")
			}

			if (dataCenter)
				utilizationReportTranslator.countUtilizationLevels(dataCenters, dataCenter, cpuUtilLevel,
						memoryUtilLevel, bothUtilLevel, cpuUtils, memoryUtils)
			utilizationReportTranslator.countUtilizationLevels(dataCenters, ALL_DATACENTERS, cpuUtilLevel,
					memoryUtilLevel, bothUtilLevel, cpuUtils, memoryUtils)
			vmSuccessCount++
		}

		dataCenters.each {dataCenterName, metrics ->
			// Do not divide by 0
			if (metrics.total != 0) {
				metrics.cpuAverage /= metrics.total
				metrics.memoryAverage /= metrics.total
			}
		}

		if (!vmSuccessCount && vmErrorCount) {
			pluginEventService.createEvent(UtilizationReportEvents.NO_SAMPLES_1TYPE, null, null)
		} else {
			response = moabRestService.post(REPORTS_URL + VM_REPORT_NAME + SAMPLES_URL, params: ['api-version': 3]) {
				[
						agent: "VM Utilization Report Plugin",
						data: dataCenters,
				]
			}
			if (response?.success)
				log.debug("Successfully created sample for VM utilization report")
			else {
				def messages = response?.data?.messages?.collect { it?.toString }?.join(", ")
				pluginEventService.createEvent(UtilizationSampleEvents.CREATE_1REPORT_ERROR_2MESSAGES,
					[VM_REPORT_NAME, messages], null)
				log.error("Could not create sample for VM utilization report: ${messages}")
			}
		}
	}

	public def recreateReport(Map params) {
		// Blow away the report and the data (use the new datapointDuration)
		log.info("Destroying (if it exists) and recreating VM utilization report")
		moabRestService.delete(REPORTS_URL + VM_REPORT_NAME, params: ['api-version': 3])
		log.debug("Recreating report")
		def messages = []
		def response = moabRestService.post(REPORTS_URL, data: getCreateJsonMap(), params: ['api-version': 3])
		if (response.success)
			messages << message(code: "vmUtilizationReportPlugin.recreateReport.success.message", args: [VM_REPORT_NAME])
		else {
			messages << message(code: "vmUtilizationReportPlugin.recreateReport.failure.message", args: [VM_REPORT_NAME])
			if (response.data?.messages)
				messages.addAll(response.data.messages)
		}
		return [
				messages: messages
		]
	}

	private Map getCreateJsonMap() {
		return [
				name: VM_REPORT_NAME,
				description: "The report for VM CPU and memory utilization produced by the VMUtilizationReport plugin",
				consolidationFunction: "average",
				datapointDuration: config.reportConsolidationDuration,
				reportSize: config.reportSize,
				keepSamples: false,
				reportDocumentSize: config.reportDocumentSize
		]
	}
}
