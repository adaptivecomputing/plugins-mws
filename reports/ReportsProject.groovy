class ReportsProject {
	// Plugin information
	String title = "Reports"
	String description = "Creates reports based on results from the MWS REST API."
	String author = "Adaptive Computing Enterprises, Inc."
	String website = "http://www.adaptivecomputing.com"
	Integer eventComponent = 2

	// Versioning properties
	String version = "1.5-SNAPSHOT"
	String commonsVersion = "1.1.0 > *"
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
