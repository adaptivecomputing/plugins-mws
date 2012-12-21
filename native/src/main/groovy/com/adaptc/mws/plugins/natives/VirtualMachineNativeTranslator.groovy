package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class VirtualMachineNativeTranslator {
	public VirtualMachineReport update(Map attrs) {
		VirtualMachineReport vm = new VirtualMachineReport(attrs.id)
		
		vm.state = NodeReportState.parse(attrs.STATE)
		vm.power = NodeReportPower.parse(attrs.POWER)
		vm.timestamp = NativeDateUtils.parseSecondsToDate(attrs.UPDATETIME)
		vm.host = attrs.CONTAINERNODE
		vm.resources[RESOURCE_PROCESSORS].total = NativeNumberUtils.parseInteger(attrs.CPROC)
		vm.resources[RESOURCE_PROCESSORS].available = NativeNumberUtils.parseInteger(attrs.APROC)
		vm.resources[RESOURCE_MEMORY].total = NativeNumberUtils.parseInteger(attrs.CMEMORY)
		vm.resources[RESOURCE_MEMORY].available = NativeNumberUtils.parseInteger(attrs.AMEMORY)
		vm.resources[RESOURCE_DISK].total = NativeNumberUtils.parseInteger(attrs.CDISK)
		vm.resources[RESOURCE_DISK].available = NativeNumberUtils.parseInteger(attrs.ADISK)
		vm.metrics[METRIC_CPULOAD] = NativeNumberUtils.parseDouble(attrs.CPULOAD)
		// Simple map
		attrs.GMETRIC?.each { vm.metrics[it.key] = NativeNumberUtils.parseDouble(it.value) }
		vm.image = attrs.OS
		vm.variables = attrs.VARIABLE ?: [:]
		
		vm
	}
}
