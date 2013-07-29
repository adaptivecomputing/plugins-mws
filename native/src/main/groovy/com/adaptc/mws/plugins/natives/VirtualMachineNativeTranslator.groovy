package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils
import com.adaptc.mws.plugins.natives.utils.NativeUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class VirtualMachineNativeTranslator {
	public VirtualMachineReport createReport(Map attrs, VMImageInfo imageInfo) {
		VirtualMachineReport vm = new VirtualMachineReport(attrs.id)
		
		vm.state = NodeReportState.parse(attrs.STATE)
		vm.power = NodeReportPower.parse(attrs.POWER)
		vm.timestamp = NativeDateUtils.secondsToDate(attrs.UPDATETIME)
		vm.host = attrs.CONTAINERNODE
		vm.ipAddress = attrs.NETADDR
		vm.resources[RESOURCE_PROCESSORS].total = NativeNumberUtils.parseInteger(attrs.CPROC)
		vm.resources[RESOURCE_PROCESSORS].available = NativeNumberUtils.parseInteger(attrs.APROC)
		vm.resources[RESOURCE_MEMORY].total = NativeNumberUtils.parseInteger(attrs.CMEMORY)
		vm.resources[RESOURCE_MEMORY].available = NativeNumberUtils.parseInteger(attrs.AMEMORY)
		vm.resources[RESOURCE_DISK].total = NativeNumberUtils.parseInteger(attrs.CDISK)
		vm.resources[RESOURCE_DISK].available = NativeNumberUtils.parseInteger(attrs.ADISK)
		vm.resources[RESOURCE_SWAP].available = NativeNumberUtils.parseInteger(attrs.ASWAP)
		vm.resources[RESOURCE_SWAP].total = NativeNumberUtils.parseInteger(attrs.CSWAP)
		vm.metrics[METRIC_CPULOAD] = NativeNumberUtils.parseDouble(attrs.CPULOAD)
		// Backwards compatible with commons 0.9.x
		if (NativeUtils.objectHasProperty(vm, "imagesAvailable")) {
			attrs.OSLIST?.split(",")?.each { vm.imagesAvailable << it }
		}
		if (attrs.containsKey("MIGRATIONDISABLED") && NativeUtils.objectHasProperty(vm, "migrationDisabled")) {
			vm.migrationDisabled = attrs.MIGRATIONDISABLED?.toBoolean() ?: false
		}
		// Simple map
		attrs.GMETRIC?.each { vm.metrics[it.key] = NativeNumberUtils.parseDouble(it.value) }
		vm.image = attrs.OS
		vm.variables = attrs.VARIABLE ?: [:]

		// Set image info fields
		imageInfo.name = vm.image
		
		return vm
	}
}
