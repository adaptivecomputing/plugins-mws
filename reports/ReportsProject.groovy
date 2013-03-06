
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
		'node-report' {
			pluginType = "NodeUtilizationReportPlugin"
			pollInterval = 60
			autoStart = true
            //This should take about 22 MB of disk space for the mongo capped collection
			config {
			    cpuHighThreshold = 75 // 75%
				cpuLowThreshold = 25 // 25%
			    memoryHighThreshold = 75 // 75%
				memoryLowThreshold = 25 // 25%
				reportConsolidationDuration = 14400 // 4 hours
                reportDocumentSize = 20480 // 20 kB
				reportSize =  1096 // 6 months
			}
		}

		'vm-report' {
			pluginType = "VMUtilizationReportPlugin"
			pollInterval = 60
			autoStart = true
            //This should take about 22 MB of disk space for the mongo capped collection
			config {
			    cpuHighThreshold = 75 // 75%
				cpuLowThreshold = 25 // 25%
			    memoryHighThreshold = 75 // 75%
				memoryLowThreshold = 25 // 25%
				reportConsolidationDuration = 14400 // 4 hours
                reportDocumentSize = 20480 // 20 kB
				reportSize =  1096 // 6 months
			}
		}
	}
}
