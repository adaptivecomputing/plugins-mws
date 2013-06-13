package com.adaptc.mws.plugins.reports

import com.adaptc.mws.plugins.EventEnumeration

import static com.adaptc.mws.plugins.IPluginEventService.EscalationLevel.ADMIN
import static com.adaptc.mws.plugins.IPluginEventService.Severity.ERROR

/**
 * @author bsaville
 */
@EventEnumeration
enum ResourceQueryEvents {
	QUERY_FOR_1RESOURCE_2VERSION_ERROR_3MESSAGES("Query", ERROR, ADMIN)

	static String EVENT_TYPE_PREFIX = "Resource"
}
