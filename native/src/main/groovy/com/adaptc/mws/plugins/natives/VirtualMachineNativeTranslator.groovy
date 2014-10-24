package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.NodeReportPower
import com.adaptc.mws.plugins.NodeReportState
import com.adaptc.mws.plugins.VirtualMachineReport
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils
import groovy.transform.CompileStatic

import static com.adaptc.mws.plugins.PluginConstants.*

class VirtualMachineNativeTranslator {
	public VirtualMachineReport createReport(IPluginEventService pluginEventService, Map attrs, VMImageInfo imageInfo) {
		def id = attrs.remove("id")
		VirtualMachineReport vm = new VirtualMachineReport(id)

		attrs.each { String key, value ->
			def field = VMNativeField.parseWikiAttribute(key)
			switch (field) {
				case VMNativeField.STATE:
					vm.state = NodeReportState.parse(value)
					break
				case VMNativeField.SLAVE:
					vm.slaveReport = value?.toBoolean() ?: false
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
				case VMNativeField.TYPE:
					// Do nothing, this is purely to differentiate between types of objects
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

	boolean isVirtualMachineWiki(Map attrs) {
		def typeAttr = attrs.find { it.key.equalsIgnoreCase(VMNativeField.TYPE.wikiKey) }
		if (typeAttr?.value)
			return typeAttr.value.equalsIgnoreCase("VM")
		return attrs.find { it.key.equalsIgnoreCase(VMNativeField.CONTAINER_NODE.wikiKey) } // Only VMs have CONTAINERNODE
	}
}

