package com.adaptc.mws.plugins.openstack

import com.adaptc.mws.plugins.*
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.model.compute.Server
import org.openstack4j.model.compute.ServerCreate
import org.openstack4j.openstack.OSFactory

class OpenstackPlugin extends AbstractPlugin {
	static constraints = {
		// You can insert constraints here on pollInterval or any arbitrary field in
		// the plugin's configuration.  All parameters defined here default to required:true.
		//confidentialParameter password:true
		//optionalBoolean required:false, blank:false, type:Boolean
	}

	public def triggerBurst(Map params) {
		OSClient osClient = OSFactory.builder()
				.endpoint("https://os.endpoint.bogus")
				.credentials("someone", "somepass")
				.tenantName("Tenant1")
				.authenticate()
		def flavorId = osClient.compute().flavors().list().find { it.name=="flavor-name" }.id
		def imageId = osClient.compute().images().list().find { it.name=="image-name" }.id
		log.debug("Using flavor ${flavorId} and image ${imageId}")
		ServerCreate newServer = Builders.server()
				.name("burst-vm-${new Date().time}")
				.flavor(flavorId)
				.image(imageId)
				.build()
		Server server = osClient.compute().servers().boot(newServer)
		def startDate = new Date()
		while(server.status!=Server.Status.ACTIVE) {
			if ((new Date().time - startDate.time) > 30000) {
				log.error("Could not get information from server, took too long to get IP")
				break
			}
			// Else keep trying
			sleep(1000)
			server = osClient.compute().servers().get(server.id)
		}
		return [
				id:server.id,
		        ipAddress:server.getAddresses().getAddresses()['vlan-name'].first().addr,
				name:server.name,
				powerState:server.powerState
		]
	}
}