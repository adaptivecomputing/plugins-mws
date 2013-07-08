import com.adaptc.mws.plugins.Suite

class NativeProject {
	// Plugin information
	String title = "Native"
	String description = "Native basic plugin implementation"
	String author = "Adaptive Computing Enterprises, Inc."
	String website = "http://www.adaptivecomputing.com"

	// Versioning properties
	String version = "1.6-SNAPSHOT"
	String mwsVersion = "7.1.4 > *"
	String license = "APACHE"
	
	// Documentation properties
	String issueManagementLink = ""
	String documentationLink = ""
	String scmLink = ""
	
	def initialPlugins = {
		// Only initialize the cloud-native plugin if the suite is CLOUD
		if (suite==Suite.CLOUD) {
			'cloud-native' {
				pluginType = "Native"
				pollInterval = 30
				config {
					getCluster = "file://${appConfig.mws.home.location}/etc/nodes.txt"
				}
			}
		}
	}
}
