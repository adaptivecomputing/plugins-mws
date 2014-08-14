package com.adaptc.mws.plugins.openstack

import com.adaptc.mws.plugins.*
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Server
import org.openstack4j.model.compute.ServerCreate
import org.openstack4j.openstack.OSFactory

import javax.net.ssl.HttpsURLConnection

class OpenStackPlugin extends AbstractPlugin {
	private static final String REQUEST_ID_TOKEN = "{request-id}"
	private static final String DATE_TOKEN = "{date}"
	private static final int SLEEP_VALUE = 1 * 1000l

	static constraints = {
		// You can insert constraints here on pollInterval or any arbitrary field in
		// the plugin's configuration.  All parameters defined here default to required:true.
		//confidentialParameter password:true
		//optionalBoolean required:false, blank:false, type:Boolean
		osEndpoint blank: false, validator: { val, obj ->
			final Integer timeout = 5000

			Integer responseCode = null
			String host
			URL urlObject
			HttpURLConnection httpURLConnection = null
			HttpsURLConnection httpsURLConnection = null

			try {
				urlObject = new URL(val)
			} catch (MalformedURLException e) {
				return ["invalid.malformed", e.message]
			}
			host = urlObject.host
			try {
				switch (urlObject.protocol) {
					case "http":
						httpURLConnection = urlObject.openConnection() as HttpURLConnection
						break
					case "https":
						httpsURLConnection = urlObject.openConnection() as HttpsURLConnection
						ISslService sslService = getService("sslService")
						httpsURLConnection.SSLSocketFactory = sslService.lenientSocketFactory
						httpsURLConnection.hostnameVerifier = sslService.lenientHostnameVerifier
						httpURLConnection = httpsURLConnection
						break
					default:
						return "invalid.protocol"
				}
				httpURLConnection.connectTimeout = timeout
				httpURLConnection.readTimeout = timeout
				httpURLConnection.inputStream.close()
				responseCode = httpURLConnection.responseCode
			}
			catch (UnknownHostException uhe) {
				return ["invalid.host", host]
			}
			catch (SocketTimeoutException ste) {
				return ["invalid.host.timeout", host]
			}
			catch (Exception e) {
				return ["invalid.connection.failure", host, e.message]
			}

			finally {
				httpURLConnection?.disconnect()
				httpsURLConnection?.disconnect()
			}

			if (responseCode != HttpURLConnection.HTTP_OK)
				return ["invalid.response", val, responseCode]
		}
		osUsername blank:false
		osPassword blank:false, password:true
		osTenant blank:false
		osFlavorName blank:false
		osImageName blank:false
		matchImagePrefix type:Boolean, defaultValue:true
		osVlanName required:false, blank:false
		osInstanceNamePattern blank:false, defaultValue: "moab-burst-{request-id}-{date}", validator: { val ->
			if (!(val instanceof String))
				return

			if (!val.contains("{date}") || !val.contains("{request-id}"))
				return ["invalid.format", ["{date}", "{request-id}"].join(', ')]
		}
		activeTimeoutSeconds defaultValue:30, minValue:1
	}

	public def triggerBurst(Map params) {
		if (!params.requestId)
			throw new WebServiceException(message(code:"triggerBurst.missing.request.id.message", args:["requestId"]))

		Map<String, Object> config = getConfig()

		// Connect to OpenStack
		OSClient osClient = OSFactory.builder()
				.endpoint(config.osEndpoint)
				.credentials(config.osUsername, config.osPassword)
				.tenantName(config.osTenant)
				.authenticate()

		// Retrieve flavor and image
		def flavorId = osClient.compute().flavors().list().find { it.name==config.osFlavorName }?.id
		if (!flavorId)
			throw new WebServiceException(message(code:"triggerBurst.invalid.flavor.name.message",
					args:[config.osFlavorName]))
		def allImages = osClient.compute().images().list()
		String imageName = config.osImageName
		if (config.matchImagePrefix) {
			imageName = allImages.collect { it.name }.sort().reverse().find { it.startsWith(imageName) }
		}
		def imageId = allImages.find { it.name==imageName }?.id
		if (!imageId) {
			throw new WebServiceException(message(code:"triggerBurst.invalid.image.name.message",
					args:[config.osImageName]))
		}

		log.debug("Using flavor ${flavorId} and image ${imageId}")

		// Create new server
		ServerCreate newServer = Builders.server()
				.name(((String)config.osInstanceNamePattern)
						.replace(REQUEST_ID_TOKEN, params.requestId)
						.replace(DATE_TOKEN, new Date().time.toString())
				)
				.flavor(flavorId)
				.image(imageId)
				.build()

		// Boot new server and wait for timeout until the server is active
		Server server = osClient.compute().servers().boot(newServer)
		def startTime = new Date().time
		long timeout = config.activeTimeoutSeconds * 10000l
		while(server.status != Server.Status.ACTIVE) {
			if ((new Date().time - startTime) > timeout) {
				log.error("Could not get information from server, timeout after ${config.timeoutSeconds} seconds")
				break
			}
			// Else keep trying
			sleep(SLEEP_VALUE)
			server = osClient.compute().servers().get(server.id)
		}

		// Return data to the client
		List<? extends Address> addresses = null
		if (config.osVlanName)
			addresses = server.getAddresses().getAddresses(config.osVlanName)
		if (!addresses)
			addresses = server.getAddresses().getAddresses().values().find { it } // Get first non-empty list
		return [
				id:server.id,
		        ipAddress:addresses.first()?.addr,
				name:server.name,
				powerState:server.powerState
		]
	}
}