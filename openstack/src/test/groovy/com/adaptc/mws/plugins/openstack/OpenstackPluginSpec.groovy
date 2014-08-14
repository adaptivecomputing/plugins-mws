package com.adaptc.mws.plugins.openstack

import spock.lang.*
import com.adaptc.mws.plugins.testing.*

@TestFor(OpenStackPlugin)
class OpenstackPluginSpec extends Specification {
	def "Feature of the plugin"() {
		given:
		plugin.id = "myId"
		
		when:
		def id = plugin.id
		
		then:
		id=="myId"
	}
}