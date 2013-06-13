package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.EventEnumeration

import static com.adaptc.mws.plugins.IPluginEventService.EscalationLevel.ADMIN
import static com.adaptc.mws.plugins.IPluginEventService.Severity.ERROR

/**
 * @author bsaville
 */
@EventEnumeration
enum UtilizationSampleEvents {
	CREATE_1REPORT_ERROR_2MESSAGES("Create", ERROR, ADMIN)

	static String EVENT_TYPE_PREFIX = "Sample"
}
