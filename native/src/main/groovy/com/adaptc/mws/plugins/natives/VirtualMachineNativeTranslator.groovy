package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils
import com.adaptc.mws.plugins.natives.utils.NativeUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class VirtualMachineNativeTranslator {
	IPluginEventService pluginEventService

	boolean lowerCaseNames = true

	public VirtualMachineReport createReport(Map attrs, VMImageInfo imageInfo) {
		def id = attrs.remove("id")
		VirtualMachineReport vm = new VirtualMachineReport(id)
		// Manually set name field to make sure it is not lower-cased
		if (!lowerCaseNames)
			vm.@name = id

		attrs.each { String key, value ->
			def field = VMNativeField.parseWikiAttribute(key)
			switch (field) {
				case VMNativeField.STATE:
					vm.state = NodeReportState.parse(value)
					break
				case VMNativeField.POWER:
					vm.power = NodeReportPower.parse(value)
					break
				case VMNativeField.UPDATE_TIME:
					vm.timestamp = NativeDateUtils.secondsToDate(value)
					break
				case VMNativeField.CONTAINER_NODE:
					vm.host = value
					break
				case VMNativeField.NETWORK_ADDRESS:
					vm.ipAddress = value
					break
				case VMNativeField.PROCESSORS_CONFIGURED:
					vm.resources[RESOURCE_PROCESSORS].total = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.PROCESSORS_AVAILABLE:
					vm.resources[RESOURCE_PROCESSORS].available = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.MEMORY_CONFIGURED:
					vm.resources[RESOURCE_MEMORY].total = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.MEMORY_AVAILABLE:
					vm.resources[RESOURCE_MEMORY].available = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.DISK_CONFIGURED:
					vm.resources[RESOURCE_DISK].total = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.DISK_AVAILABLE:
					vm.resources[RESOURCE_DISK].available = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.SWAP_CONFIGURED:
					vm.resources[RESOURCE_SWAP].total = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.SWAP_AVAILABLE:
					vm.resources[RESOURCE_SWAP].available = NativeNumberUtils.parseInteger(value)
					break
				case VMNativeField.CPU_LOAD:
					vm.metrics[METRIC_CPULOAD] = NativeNumberUtils.parseDouble(value)
					break
				case VMNativeField.OS_LIST:
					value?.split(",")?.each { vm.imagesAvailable << it }
					break
				case VMNativeField.MIGRATION_DISABLED:
					vm.migrationDisabled = value?.toBoolean() ?: false
					break
				case VMNativeField.GENERIC_METRICS:
					value?.each { vm.metrics[it.key] = NativeNumberUtils.parseDouble(it.value) }
					break
				case VMNativeField.OS:
					vm.image = value
					break
				case VMNativeField.VARIABLES:
					vm.variables = value ?: [:]
					break
				default:
					def message = message(code:"virtualMachineNativeTranslator.invalid.attribute", args:[vm.name, key, value])
					log.warn(message)
					pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
							message,
							new IPluginEventService.AssociatedObject(id:vm.name, type:"VM"), null)
					break
			}
		}

		// Set image info fields
		imageInfo.name = vm.image

		return vm
	}
}

enum VMNativeField {
	STATE("state"),
	POWER("power"),
	UPDATE_TIME("updatetime"),
	CONTAINER_NODE("containernode"),
	NETWORK_ADDRESS("netaddr"),
	PROCESSORS_CONFIGURED("cproc"),
	PROCESSORS_AVAILABLE("aproc"),
	MEMORY_CONFIGURED("cmemory"),
	MEMORY_AVAILABLE("amemory"),
	DISK_CONFIGURED("cdisk"),
	DISK_AVAILABLE("adisk"),
	SWAP_CONFIGURED("cswap"),
	SWAP_AVAILABLE("aswap"),
	GENERIC_METRICS("gmetric"),
	OS("os"),
	OS_LIST("oslist"),
	CPU_LOAD("cpuload"),
	VARIABLES("variable"),
	MIGRATION_DISABLED("migrationdisabled")

	String wikiKey

	private VMNativeField(String wikiKey) {
		this.wikiKey = wikiKey
	}

	public static VMNativeField parseWikiAttribute(String attribute) {
		if (!attribute)
			return null
		return values().find { it.wikiKey.equalsIgnoreCase(attribute) }
	}
}
