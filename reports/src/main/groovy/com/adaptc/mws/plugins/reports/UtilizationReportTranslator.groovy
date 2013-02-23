package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.IPluginDatastoreService

/**
 *
 * @author jpratt , bsaville
 */
class UtilizationReportTranslator {
	public void countUtilizationLevels(Map<String, Map<String, Object>> dataCenters, String dataCenter,
									UtilizationLevel cpuUtilLevel, UtilizationLevel memoryUtilLevel,
									BigDecimal cpuUtils, BigDecimal memoryUtils) {
		dataCenters[dataCenter].cpuAverage += cpuUtils
		if (cpuUtilLevel == UtilizationLevel.LOW)
			dataCenters[dataCenter].cpuLow++
		else if (cpuUtilLevel == UtilizationLevel.HIGH)
			dataCenters[dataCenter].cpuHigh++
		else
			dataCenters[dataCenter].cpuMedium++

		dataCenters[dataCenter].memoryAverage += memoryUtils
		if (memoryUtilLevel == UtilizationLevel.LOW)
			dataCenters[dataCenter].memoryLow++
		else if (memoryUtilLevel == UtilizationLevel.HIGH)
			dataCenters[dataCenter].memoryHigh++
		else
			dataCenters[dataCenter].memoryMedium++

		if (memoryUtilLevel == UtilizationLevel.HIGH || cpuUtilLevel == UtilizationLevel.HIGH)
			dataCenters[dataCenter].high++
		else if (memoryUtilLevel == UtilizationLevel.MEDIUM || cpuUtilLevel == UtilizationLevel.MEDIUM)
			dataCenters[dataCenter].medium++
		else
			dataCenters[dataCenter].low++

		dataCenters[dataCenter].total++
	}

	public void addOrUpdateData(IPluginDatastoreService pluginDatastoreService,
								String collection, String name, Map json) {
		if (collection == null || name == null || json == null) {
			throw new IllegalArgumentException("null input param : collection-" + (collection == null) + ",name-" + (name == null) + ", json-" + (json == null));
		}
		json.name = name
		def data = pluginDatastoreService.getData(collection, "name", name)
		if (!data) {
			if (pluginDatastoreService.addData(collection, json))
				log.debug("Persist saving new ${name}: " + json)
			else
				log.error("Could not persist new ${name}: " + json)
		} else {
			if (pluginDatastoreService.updateData(collection, "name", name, json))
				log.debug("Persist update ${name}: " + json)
			else
				log.error("Could not update ${name}: " + json)
		}
	}

	public UtilizationLevel getUtilizationLevel(Double utilization, Double lowThreshold, Double highThreshold) {
		if (utilization < lowThreshold)
			return UtilizationLevel.LOW
		else if (utilization >= highThreshold)
			return UtilizationLevel.HIGH
		return UtilizationLevel.MEDIUM
	}

	public double calculateUtilization(Double total, Double available) {
		return (total - available) / total * 100d
	}

	public Map getDefaultSampleDatacenter() {
		return [
				cpuHigh: 0d,
				cpuLow: 0d,
				cpuMedium: 0d,
				cpuAverage: 0d,
				memoryHigh: 0d,
				memoryLow: 0d,
				memoryMedium: 0d,
				memoryAverage: 0d,
				high: 0d,
				low: 0d,
				medium: 0d,
				total: 0,
		]
	}
}
