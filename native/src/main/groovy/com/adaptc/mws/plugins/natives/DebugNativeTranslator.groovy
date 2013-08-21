package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.IPluginEvent
import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.natives.utils.NativeUtils

/**
 * @author bsaville
 */
class DebugNativeTranslator {
	NodeNativeTranslator nodeNativeTranslator
	VirtualMachineNativeTranslator virtualMachineNativeTranslator
	JobNativeTranslator jobNativeTranslator
	StorageNativeTranslator storageNativeTranslator

	public Map verifyClusterWiki(wiki, String id) {
		return verifyWiki(wiki, id, { DebugEventService debugEventService, Map attrs, Map lineInfo ->
			if (virtualMachineNativeTranslator.isVirtualMachineWiki(attrs))
				return virtualMachineNativeTranslator.createReport(debugEventService, attrs, new VMImageInfo())
			else if (storageNativeTranslator.isStorageWiki(attrs))
				return storageNativeTranslator.createReport(debugEventService, attrs)
			else // Default to node
				return nodeNativeTranslator.createReport(debugEventService, attrs, new HVImageInfo())
		})
	}

	public Map verifyWorkloadWiki(wiki, String id) {
		return verifyWiki(wiki, id, { DebugEventService debugEventService, Map attrs, Map lineInfo ->
			return jobNativeTranslator.createReport(debugEventService, attrs)
		})
	}

	private Map verifyWiki(wiki, String id, Closure callTranslator) {
		def debugEventService = new DebugEventService()
		def map = [
				totalErrors:0,
				totalLines:0,
				lines:[],
		]

		def lines = wiki?.toString()?.readLines() ?: []
		def filteredLines = NativeUtils.filterLines(lines)
		NativeUtils.parseWiki(filteredLines).eachWithIndex { Map attrs, int i ->
			map.totalLines++
			Map lineInfo = [
					content:filteredLines[i],
			]

			debugEventService.errors = []
			lineInfo.report = callTranslator.call(debugEventService, attrs, lineInfo)
			lineInfo.report.pluginId = id
			lineInfo.type = lineInfo.report.getClass().simpleName - "Report"

			lineInfo.errors = debugEventService.errors
			if (lineInfo.errors)
				map.totalErrors += lineInfo.errors.size()
			map.lines << lineInfo
		}

		map.valid = map.totalErrors == 0
		return map
	}


	private class DebugEventService implements IPluginEventService {
		List<String> errors = []

		@Override
		void createEvent(IPluginEventService.Severity severity, IPluginEventService.EscalationLevel escalationLevel,
						 int entryCode, String eventType, String originSuffix, String message, List<String> arguments,
						 List<IPluginEventService.AssociatedObject> objects) throws Exception {
		}

		@Override
		void createEvent(Date eventDate, IPluginEventService.Severity severity, IPluginEventService.EscalationLevel escalationLevel,
						 int entryCode, String eventType, String originSuffix, String message, List<String> arguments,
						 List<IPluginEventService.AssociatedObject> objects) throws Exception {
		}

		@Override
		void createEvent(IPluginEvent pluginEvent, List<String> arguments,
						 List<IPluginEventService.AssociatedObject> objects) throws Exception {
		}

		@Override
		void createEvent(Date eventDate, IPluginEvent pluginEvent, List<String> arguments,
						 List<IPluginEventService.AssociatedObject> objects) throws Exception {
		}

		@Override
		void updateNotificationCondition(IPluginEventService.EscalationLevel escalationLevel, String message,
						 IPluginEventService.AssociatedObject associatedObject, Map<String, String> details) throws Exception {
			updateNotificationCondition(escalationLevel, message, associatedObject, details, null)
		}

		@Override
		void updateNotificationCondition(IPluginEventService.EscalationLevel escalationLevel, String message,
										 IPluginEventService.AssociatedObject associatedObject,
										 Map<String, String> details, Long expirationDuration) throws Exception {
			updateNotificationCondition(null, escalationLevel, message, associatedObject, details, expirationDuration)
		}

		@Override
		void updateNotificationCondition(Date observedDate, IPluginEventService.EscalationLevel escalationLevel,
										 String message, IPluginEventService.AssociatedObject associatedObject,
										 Map<String, String> details) throws Exception {
			updateNotificationCondition(observedDate, escalationLevel, message, associatedObject, details, null)
		}

		@Override
		void updateNotificationCondition(Date observedDate, IPluginEventService.EscalationLevel escalationLevel,
										 String message, IPluginEventService.AssociatedObject associatedObject,
										 Map<String, String> details, Long expirationDuration) throws Exception {
			errors << message
		}
	}
}
