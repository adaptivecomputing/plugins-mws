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
			// The goal is to keep half a year of data and keep the collection
			// capped at about 20 MB (arbitrary, but seems reasonable).
			// Using the numbers below, the total collection size is about
			// 21 MB (reportDocumentSize * reportSize).
			config {
				cpuHighThreshold = 75 // 75%
				cpuLowThreshold = 25 // 25%
				memoryHighThreshold = 75 // 75%
				memoryLowThreshold = 25 // 25%
				reportConsolidationDuration = 14400 // 4 hours, or 6 datapoints per day
				reportDocumentSize = 20480 // 20 KB
				reportSize = 1098 // 183 (days in half a year) times 6 (datapoints per day)
			}
		}

		'vm-report' {
			pluginType = "VMUtilizationReportPlugin"
			pollInterval = 60
			autoStart = true
			config {
				cpuHighThreshold = 75
				cpuLowThreshold = 25
				memoryHighThreshold = 75
				memoryLowThreshold = 25
				reportConsolidationDuration = 14400
				reportDocumentSize = 20480
				reportSize = 1098
			}
		}
	}
}
