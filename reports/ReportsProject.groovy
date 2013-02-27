
class ReportsProject {
	// Plugin information
	String title = "Reports"
	String description = "Creates reports based on results from the MWS REST API."
	String author = "Adaptive Computing Enterprises, Inc."
	String website = "http://www.adaptivecomputing.com"

	// Versioning properties
	String version = "1.3-SNAPSHOT"
	String mwsVersion = "7.1.4 > *"
	String commonsVersion = "0.9.4 > *"
	String license = "APACHE"
	
	// Documentation properties
	String issueManagementLink = ""
	String documentationLink = ""
	String scmLink = ""

	def initialPlugins = {
		// Removing from 1.0 since it pre-allocates 10 GB of space for this report
//		'node-report' {
//			pluginType = "NodeUtilizationReportPlugin"
//			pollInterval = appConfig.plugins.reports.nodeUtilization.sampleInterval ?:
//				appConfig.plugins.reports.defaults.sampleInterval ?: 60
//			autoStart = true
//			config {
//				highThreshold = appConfig.plugins.reports.nodeUtilization.highThreshold ?: 75
//				lowThreshold = appConfig.plugins.reports.nodeUtilization.lowThreshold ?: 25
//				reportConsolidationDuration = appConfig.plugins.reports.nodeUtilization.reportConsolidationDuration ?:
//					appConfig.plugins.reports.defaults.reportConsolidationDuration ?: 900 // 15 minutes
//				reportSize = appConfig.plugins.reports.nodeUtilization.reportSize ?:
//					appConfig.plugins.reports.defaults.reportSize ?: 672 // 1 week
//			}
//		}
	}
}