class ReportsProject {
	// Plugin information
	String title = "Reports"
	String description = "Creates reports based on results from the MWS REST API."
	String author = "Adaptive Computing Enterprises, Inc."
	String website = "http://www.adaptivecomputing.com"

	// Versioning properties
	String version = "1.5-SNAPSHOT"
	String mwsVersion = "7.3 > *"
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
		}

		'vm-report' {
			pluginType = "VMUtilizationReportPlugin"
			pollInterval = 60
			autoStart = true
		}
	}
}
