// Place any plugin instances you would like to create when using 'generate-test-instances' or
//	'upload-test' (which combines 'upload' with 'generate-test-instances') in here using the 
//	following format
/*
plugins {
	myPluginId {
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
	'node-report' {
		pluginType = "NodeUtilizationReport"
		config {
			reportConsolidationDuration = 120
			reportSize = 672	// 6 months
		}
	}
	'vm-report' {
		pluginType = "VMUtilizationReport"
		config {
			reportConsolidationDuration = 120
			reportSize = 672	// 6 months
		}
	}
}