package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class NodeNativeTranslator {
	def genericNativeTranslator

	public NodeReport createReport(Map attrs) {
		NodeReport node = new NodeReport(attrs.id)
		
		if (attrs.STATE?.contains(":")) {
			def states = attrs.STATE.tokenize(":")
			node.state = NodeReportState.parse(states[0])
			node.subState = states[1]
		} else {
			node.state = NodeReportState.parse(attrs.STATE)
		}
		node.timestamp = NativeDateUtils.parseSecondsToDate(attrs.UPDATETIME)
		node.resources[RESOURCE_PROCESSORS].total = NativeNumberUtils.parseInteger(attrs.CPROC)
		node.resources[RESOURCE_PROCESSORS].available = NativeNumberUtils.parseInteger(attrs.APROC)
		node.resources[RESOURCE_MEMORY].total = NativeNumberUtils.parseInteger(attrs.CMEMORY)
		node.resources[RESOURCE_MEMORY].available = NativeNumberUtils.parseInteger(attrs.AMEMORY)
		node.resources[RESOURCE_DISK].total = NativeNumberUtils.parseInteger(attrs.CDISK)
		node.resources[RESOURCE_DISK].available = NativeNumberUtils.parseInteger(attrs.ADISK)
		node.architecture = attrs.ARCH
		genericNativeTranslator.getGenericMap(attrs.ARES).each { node.resources[it.key].available = NativeNumberUtils.parseInteger(it.value) }
		genericNativeTranslator.getGenericMap(attrs.CRES)?.each { node.resources[it.key].total = NativeNumberUtils.parseInteger(it.value) }
		node.resources[RESOURCE_SWAP].available = NativeNumberUtils.parseInteger(attrs.ASWAP)
		node.resources[RESOURCE_SWAP].total = NativeNumberUtils.parseInteger(attrs.CSWAP)
		node.metrics.cpuLoad = NativeNumberUtils.parseDouble(attrs.CPULOAD)
		attrs.FEATURE?.split(":")?.each { node.features << it }
		attrs.GMETRIC?.each { node.metrics[it.key] = NativeNumberUtils.parseDouble(it.value) }
		attrs.MESSAGE?.each { node.messages << it }
		node.image = attrs.OS
        attrs.OSLIST?.split(",")?.each { node.imagesAvailable << it }
		node.ipAddress = attrs.NETADDR
		node.partition = attrs.PARTITION
		node.power = NodeReportPower.parse(attrs.POWER)
		node.metrics[METRIC_SPEED] = NativeNumberUtils.parseDouble(attrs.SPEED)
		node.variables = attrs.VARIABLE ?: [:]
		// Backwards support for 0.9.x commons
		if (objectHasProperty(node, "attributes")) {
			node.attributes = genericNativeTranslator.getGenericMap(attrs.VARATTR, "\\+", ":|=")?.findAll {
				it.key!="HVTYPE"
			} ?: [:]
		}

		node
	}

	private boolean objectHasProperty(object, String property) {
		return object.getClass().metaClass.getMetaProperty(property).asBoolean()
	}
}
