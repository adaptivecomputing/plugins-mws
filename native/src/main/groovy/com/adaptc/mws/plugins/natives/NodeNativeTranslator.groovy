package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class NodeNativeTranslator {
	GenericNativeTranslator genericNativeTranslator
	AclNativeTranslator aclNativeTranslator

	public NodeReport createReport(IPluginEventService pluginEventService, Map attrs) {
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
				case NodeNativeField.REQUESTID:
					node.requestId = value
					break
				case NodeNativeField.TIME_TO_LIVE:
					node.timeToLive = NativeDateUtils.isoDateStringNoMillisToDate(value)
					break
				case NodeNativeField.ACL:
					node.aclRules = aclNativeTranslator.parseAclRules(value)
					break
				case NodeNativeField.CPU_LOAD:
					node.metrics.cpuLoad = NativeNumberUtils.parseDouble(value)
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
					node.operatingSystem = value
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
							default:
								// Add all others as typical VARATTRs
								node.attributes[attrKey] = new ReportAttribute(value:attrValue.value, displayValue:attrValue.displayValue)
								break
						}
					}
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

		return node
	}
}

