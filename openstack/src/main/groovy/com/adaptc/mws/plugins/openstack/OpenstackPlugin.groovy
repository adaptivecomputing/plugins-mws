package com.adaptc.mws.plugins.openstack

import com.adaptc.mws.plugins.*
import org.openstack4j.api.OSClient
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
		return [servers:osClient.compute().servers().list()*.name]
	}
}