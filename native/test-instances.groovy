// Place any plugin instances you would like to create when using 'generate-test-instances' in here
// using the following format
/*
plugins {
	pluginId {
	  pluginType = "MyType"
	  pollInterval = 30
	  config {
		option = "value"
		option2 = "value2"
	  }
	}

	// plugin2Id {...
}
*/
plugins {
//	msm {
//		pluginType = "MSM"
//		config {
//			msmHome = "/msm"
//		}
//	}
	native1 {
		pluginType = "Native"
		pollInterval = 10
		config {
			getCluster = "file:///opt/mws/etc/nodes.txt"
		}
	}
//	native1 {
//		pluginType = "Native"
//		pollInterval = 10
//		config {
//			getCluster = "file:///opt/mws/etc/nodes1.txt"
//		}
//	}
//	native2 {
//		pluginType = "Native"
//		pollInterval = 10
//		config {
//			getCluster = "file:///opt/mws/etc/nodes2.txt"
//		}
//	}
//	native3 {
//		pluginType = "Native"
//		pollInterval = 10
//		config {
//			getCluster = "file:///opt/mws/etc/nodes3.txt"
//		}
//	}
}