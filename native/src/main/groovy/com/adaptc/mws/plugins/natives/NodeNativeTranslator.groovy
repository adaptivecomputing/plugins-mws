package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class NodeNativeTranslator {
	GenericNativeTranslator genericNativeTranslator

	public NodeReport createReport(IPluginEventService pluginEventService, Map attrs, HVImageInfo imageInfo) {
		def id = attrs.remove("id")
		NodeReport node = new NodeReport(id)

		attrs.each { String key, value ->
			def field = NodeNativeField.parseWikiAttribute(key)
			switch(field) {
				case NodeNativeField.STATE:
					if (value?.contains(":")) {
						def states = value.tokenize(":")
						node.state = NodeReportState.parse(states[0])
						node.subState = states[1]
					} else {
						node.state = NodeReportState.parse(value)
					}
					break
				case NodeNativeField.SLAVE:
					node.slaveReport = value?.toBoolean() ?: false
					break
				case NodeNativeField.UPDATE_TIME:
					node.timestamp = NativeDateUtils.secondsToDate(value)
					break
				case NodeNativeField.PROCESSORS_CONFIGURED:
					node.resources[RESOURCE_PROCESSORS].total = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.PROCESSORS_AVAILABLE:
					node.resources[RESOURCE_PROCESSORS].available = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.MEMORY_CONFIGURED:
					node.resources[RESOURCE_MEMORY].total = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.MEMORY_AVAILABLE:
					node.resources[RESOURCE_MEMORY].available = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.DISK_CONFIGURED:
					node.resources[RESOURCE_DISK].total = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.DISK_AVAILABLE:
					node.resources[RESOURCE_DISK].available = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.SWAP_CONFIGURED:
					node.resources[RESOURCE_SWAP].total = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.SWAP_AVAILABLE:
					node.resources[RESOURCE_SWAP].available = NativeNumberUtils.parseInteger(value)
					break
				case NodeNativeField.GENERIC_RESOURCES_CONFIGURED:
					genericNativeTranslator.getGenericMap(value)?.each {
						node.resources[it.key].total = NativeNumberUtils.parseInteger(it.value)
					}
					break
				case NodeNativeField.GENERIC_RESOURCES_AVAILABLE:
					genericNativeTranslator.getGenericMap(value).each {
						node.resources[it.key].available = NativeNumberUtils.parseInteger(it.value)
					}
					break
				case NodeNativeField.ARCHITECTURE:
					node.architecture = value
					break
				case NodeNativeField.CPU_LOAD:
					node.metrics.cpuLoad = NativeNumberUtils.parseDouble(value)
					break
				case NodeNativeField.HYPERVISOR_TYPE:
					imageInfo.hypervisorType = value
					break
				case NodeNativeField.FEATURES:
					value?.split(":")?.each { node.features << it }
					break
				case NodeNativeField.GENERIC_METRICS:
					value?.each { node.metrics[it.key] = NativeNumberUtils.parseDouble(it.value) }
					break
				case NodeNativeField.MESSAGES:
					value?.each { node.messages << it }
					break
				case NodeNativeField.OS:
					node.image = value
					break
				case NodeNativeField.OS_LIST:
					value?.split(",")?.each { node.imagesAvailable << it }
					break
				case NodeNativeField.NETWORK_ADDRESS:
					node.ipAddress = value
					break
				case NodeNativeField.PARTITION:
					node.partition = value
					break
				case NodeNativeField.POWER:
					node.power = NodeReportPower.parse(value)
					break
				case NodeNativeField.SPEED:
					node.metrics[METRIC_SPEED] = NativeNumberUtils.parseDouble(value)
					break
				case NodeNativeField.VARIABLES:
					node.variables = value ?: [:]
					break
				case NodeNativeField.ATTRIBUTES:
					genericNativeTranslator.getGenericMapWithDisplayValue(value, "\\+", ":|=")?.each { String attrKey, attrValue ->
						def attributeField = NodeNativeAttributeField.parseWikiAttribute(attrKey)
						switch(attributeField) {
							case NodeNativeAttributeField.HYPERVISOR_TYPE:
								imageInfo.hypervisorType = attrValue.value
								break
							case NodeNativeAttributeField.ALLOW_VM_MIGRATIONS:
								node.migrationDisabled = false
								break
							case NodeNativeAttributeField.NO_VM_MIGRATIONS:
								node.migrationDisabled = true
								break
							default:
								// Add all others as typical VARATTRs
								node.attributes[attrKey] = new ReportAttribute(value:attrValue.value, displayValue:attrValue.displayValue)
								break
						}
					}
					break
				case NodeNativeField.MIGRATION_DISABLED:
					// If the correct wiki key is present, set migration disabled based on this (alternative to VARATTRs)
					node.migrationDisabled = value?.toBoolean() ?: false
					break
				case NodeNativeField.VM_OS_LIST:
					value?.split(",")?.each { imageInfo.vmImageNames << it }
					break
				case NodeNativeField.TYPE:
					// Do nothing, this is purely to differentiate between types of objects
					break
				default:
					def message = message(code:"nodeNativeTranslator.invalid.attribute", args:[node.name, key, value])
					log.warn(message)
					pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
							message, new IPluginEventService.AssociatedObject(id:node.name, type:"Node"), null)
					break
			}
		}

		// Set image information fields
		imageInfo.nodeName = node.name
		imageInfo.name = node.image
		// hypervisorType set in attributes
		// vmImageNames set in VM_OS_LIST

		// Verify that all image information will come out as expected
		if (imageInfo.hypervisorType) {
			if (!imageInfo.name) {
				pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
						message(code:"nodeNativeTranslator.hypervisorType.without.image", args:[
								node.name,
								NodeNativeField.ATTRIBUTES.wikiKeyDisplay,
								NodeNativeAttributeField.HYPERVISOR_TYPE.wikiKeyDisplay,
								NodeNativeField.OS.wikiKeyDisplay
						]),
						new IPluginEventService.AssociatedObject(id:node.name, type:"Node"), null)
			}
			if (!imageInfo.vmImageNames) {
				pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
						message(code:"nodeNativeTranslator.hypervisorType.without.vmImageNames", args:[
								node.name,
								NodeNativeField.ATTRIBUTES.wikiKeyDisplay,
								NodeNativeAttributeField.HYPERVISOR_TYPE.wikiKeyDisplay,
								NodeNativeField.VM_OS_LIST.wikiKeyDisplay
						]),
						new IPluginEventService.AssociatedObject(id:node.name, type:"Node"), null)
			}
		}
		if (imageInfo.vmImageNames && !imageInfo.name) {
			pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
					message(code:"nodeNativeTranslator.vmImageNames.without.image", args:[
							node.name,
							NodeNativeField.VM_OS_LIST.wikiKeyDisplay,
							NodeNativeField.OS.wikiKeyDisplay
					]),
					new IPluginEventService.AssociatedObject(id:node.name, type:"Node"), null)
		}

		return node
	}
}

