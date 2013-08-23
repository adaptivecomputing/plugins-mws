package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class StorageNativeTranslator {
	GenericNativeTranslator genericNativeTranslator

	boolean lowerCaseNames = true

	public StorageReport createReport(IPluginEventService pluginEventService, Map attrs) {
		def id = attrs.remove("id")
		StorageReport storage = new StorageReport(id)
		// Manually set name field to make sure it is not lower-cased
		if (!lowerCaseNames)
			storage.@name = id

		attrs.each { String key, value ->
			def field = StorageNativeField.parseWikiAttribute(key)
			switch(field) {
				case StorageNativeField.STATE:
					if (value?.contains(":")) {
						def states = value.tokenize(":")
						storage.state = NodeReportState.parse(states[0])
						storage.subState = states[1]
					} else {
						storage.state = NodeReportState.parse(value)
					}
					break
				case StorageNativeField.SLAVE:
					storage.slaveReport = value?.toBoolean() ?: false
					break
				case StorageNativeField.UPDATE_TIME:
					storage.timestamp = NativeDateUtils.secondsToDate(value)
					break
				case StorageNativeField.PROCESSORS_CONFIGURED:
					storage.resources[RESOURCE_PROCESSORS].total = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.PROCESSORS_AVAILABLE:
					storage.resources[RESOURCE_PROCESSORS].available = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.MEMORY_CONFIGURED:
					storage.resources[RESOURCE_MEMORY].total = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.MEMORY_AVAILABLE:
					storage.resources[RESOURCE_MEMORY].available = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.DISK_CONFIGURED:
					storage.resources[RESOURCE_DISK].total = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.DISK_AVAILABLE:
					storage.resources[RESOURCE_DISK].available = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.SWAP_CONFIGURED:
					storage.resources[RESOURCE_SWAP].total = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.SWAP_AVAILABLE:
					storage.resources[RESOURCE_SWAP].available = NativeNumberUtils.parseInteger(value)
					break
				case StorageNativeField.GENERIC_RESOURCES_CONFIGURED:
					genericNativeTranslator.getGenericMap(value)?.each {
						storage.resources[it.key].total = NativeNumberUtils.parseInteger(it.value)
					}
					break
				case StorageNativeField.GENERIC_RESOURCES_AVAILABLE:
					genericNativeTranslator.getGenericMap(value).each {
						storage.resources[it.key].available = NativeNumberUtils.parseInteger(it.value)
					}
					break
				case StorageNativeField.ARCHITECTURE:
					storage.architecture = value
					break
				case StorageNativeField.CPU_LOAD:
					storage.metrics.cpuLoad = NativeNumberUtils.parseDouble(value)
					break
				case StorageNativeField.FEATURES:
					value?.split(":")?.each { storage.features << it }
					break
				case StorageNativeField.GENERIC_METRICS:
					value?.each { storage.metrics[it.key] = NativeNumberUtils.parseDouble(it.value) }
					break
				case StorageNativeField.MESSAGES:
					value?.each { storage.messages << it }
					break
				case StorageNativeField.NETWORK_ADDRESS:
					storage.ipAddress = value
					break
				case StorageNativeField.PARTITION:
					storage.partition = value
					break
				case StorageNativeField.POWER:
					storage.power = NodeReportPower.parse(value)
					break
				case StorageNativeField.SPEED:
					storage.metrics[METRIC_SPEED] = NativeNumberUtils.parseDouble(value)
					break
				case StorageNativeField.VARIABLES:
					storage.variables = value ?: [:]
					break
				case StorageNativeField.ATTRIBUTES:
					genericNativeTranslator.getGenericMapWithDisplayValue(value, "\\+", ":|=")?.each { String attrKey, attrValue ->
						storage.attributes[attrKey] = new ReportAttribute(value:attrValue.value, displayValue:attrValue.displayValue)
					}
					break
				case StorageNativeField.MIGRATION_DISABLED:
					// If the correct wiki key is present, set migration disabled based on this (alternative to VARATTRs)
					storage.migrationDisabled = value?.toBoolean() ?: false
					break
				case StorageNativeField.TYPE:
					// Do nothing, this is purely to differentiate between types of objects
					break
				default:
					def message = message(code:"storageNativeTranslator.invalid.attribute", args:[storage.name, key, value])
					log.warn(message)
					pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
							message, new IPluginEventService.AssociatedObject(id:storage.name, type:"Storage"), null)
					break
			}
		}

		return storage
	}

	boolean isStorageWiki(Map attrs) {
		def typeAttr = attrs.find { it.key.equalsIgnoreCase(StorageNativeField.TYPE.wikiKey) }
		return typeAttr?.value?.equalsIgnoreCase("Storage")
	}
}

enum StorageNativeField {
	STATE("state"),
	SLAVE("slave"),
	UPDATE_TIME("updatetime"),
	PROCESSORS_CONFIGURED("cproc"),
	PROCESSORS_AVAILABLE("aproc"),
	MEMORY_CONFIGURED("cmemory"),
	MEMORY_AVAILABLE("amemory"),
	DISK_CONFIGURED("cdisk"),
	DISK_AVAILABLE("adisk"),
	SWAP_CONFIGURED("cswap"),
	SWAP_AVAILABLE("aswap"),
	ARCHITECTURE("arch"),
	GENERIC_RESOURCES_CONFIGURED("cres"),
	GENERIC_RESOURCES_AVAILABLE("ares"),
	CPU_LOAD("cpuload"),
	FEATURES("feature"),
	GENERIC_METRICS("gmetric"),
	MESSAGES("message"),
	NETWORK_ADDRESS("netaddr"),
	PARTITION("partition"),
	POWER("power"),
	SPEED("speed"),
	TYPE("type"),
	VARIABLES("variable"),
	MIGRATION_DISABLED("migrationdisabled"),
	ATTRIBUTES("varattr")

	String wikiKey

	public String getWikiKeyDisplay() {
		return wikiKey.toUpperCase()
	}

	private StorageNativeField(String wikiKey) {
		this.wikiKey = wikiKey
	}

	public static StorageNativeField parseWikiAttribute(String attribute) {
		if (!attribute)
			return null
		return values().find { it.wikiKey.equalsIgnoreCase(attribute) }
	}
}