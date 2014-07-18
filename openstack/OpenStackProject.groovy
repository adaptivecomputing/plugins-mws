
class OpenStackProject {
	// Plugin information
	String title = "OpenStack"
	String description = "OpenStack provisioning plugin for cloud bursting"
	String author = "Adaptive Computing Enterprises, Inc."
	String website = "http://www.adaptivecomputing.com"

	// Versioning properties
	String version = "0.1-SNAPSHOT"
	String mwsVersion = "7.1.4 > *"
	String commonsVersion = "1.1.0 > *"
	String license = "APACHE"
	
	// Documentation properties
	String issueManagementLink = ""
	String documentationLink = ""
	String scmLink = ""
	
	// The following defines initial plugins that should be created
	//	after the plugin type has been created in MWS.  This is only
	//	an initial operation and will not affect updates unless the
	//	plugin instance is deleted first.
	/*
	def initialPlugins = {
		sample-plugin {
			pluginType = "OpenStackPlugin"
			pollInterval = 30
			autoStart = true
			config {
				option = "value"
				option2 = "value2"
			}
		}

		// plugin2Id {...
	}
	*/
}