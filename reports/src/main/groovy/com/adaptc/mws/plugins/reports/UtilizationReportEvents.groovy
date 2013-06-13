package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.EventEnumeration
import static com.adaptc.mws.plugins.IPluginEventService.EscalationLevel.*
import static com.adaptc.mws.plugins.IPluginEventService.Severity.*

/**
 * @author bsaville
 */
@EventEnumeration
enum UtilizationReportEvents {
	REPORT_1NAME_CREATE_ERROR_2MESSAGES("Create", ERROR, ADMIN),
	NO_SAMPLES_1TYPE("Errors", ERROR, ADMIN),
	SAMPLE_CREATE_ERROR_1MESSAGES("Sample Create", ERROR, ADMIN)

	static String EVENT_TYPE_PREFIX = "Report"
}
