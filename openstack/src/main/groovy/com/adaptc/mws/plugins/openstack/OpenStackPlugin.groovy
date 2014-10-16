package com.adaptc.mws.plugins.openstack

import com.adaptc.mws.plugins.*
import org.apache.commons.codec.binary.Base64
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Server
import org.openstack4j.model.compute.ServerCreate
import org.openstack4j.model.compute.builder.ServerCreateBuilder
import org.openstack4j.model.image.ContainerFormat
import org.openstack4j.openstack.OSFactory

import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class OpenStackPlugin extends AbstractPlugin {
	private static final String REQUEST_ID_TOKEN = "{request-id}"
	private static final String DATE_TOKEN = "{date}"
	private static final String SERVER_NUMBER_TOKEN = "{server-number}"
	private static final int SLEEP_VALUE = 1 * 1000l
	private static final String OS_IMAGE_TYPE_PROPERTY_KEY = "image_type"
	private static final String OS_IMAGE_TYPE_SNAPSHOT = "snapshot"

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
		osUsername blank: false
		osPassword blank: false, password: true
		osTenant blank: false
		osFlavorName blank: false
		osImageName blank: false
		osKeyPairName blank: false, required: false
		osInitScript blank: false, required: false, widget:"textarea"
		matchImagePrefix type: Boolean, defaultValue: true
		useBootableImage type: Boolean, defaultValue: true, validator: { val, obj ->
			if (val && !obj.config.matchImagePrefix)
				return "invalid.prefix.setting"
		}
		useSnapshot type: Boolean, defaultValue: false, validator: { val, obj ->
			if (val && !obj.config.matchImagePrefix)
				return "invalid.prefix.setting"
		}
		osVlanName required: false, blank: false
		osInstanceNamePattern blank: false, defaultValue: "moab-burst-{date}-{server-number}", validator: { val ->
			if (!(val instanceof String))
				return

			if (!val.contains(OpenStackPlugin.SERVER_NUMBER_TOKEN))
				return ["invalid.format", [OpenStackPlugin.SERVER_NUMBER_TOKEN].join(', ')]
			if (!val.contains(OpenStackPlugin.DATE_TOKEN) && !val.contains(OpenStackPlugin.REQUEST_ID_TOKEN))
				return ["invalid.format.list", [OpenStackPlugin.DATE_TOKEN, OpenStackPlugin.REQUEST_ID_TOKEN].join(', ')]
		}
		activeTimeoutSeconds defaultValue: 120, minValue: 1
		deleteTimeoutSeconds defaultValue: 120, minValue: 1
		maxRequestLimit defaultValue: 10, minValue: 1
	}

	private OSClient buildClient(Map<String, Object> config) {
		return OSFactory.builder()
				.endpoint(config.osEndpoint)
				.credentials(config.osUsername, config.osPassword)
				.tenantName(config.osTenant)
				.authenticate()
	}

	private String getAndVerifyFlavorId(OSClient osClient, Map<String, Object> config) throws WebServiceException {
		String flavorId = osClient.compute().flavors().list().find { it.name == config.osFlavorName }?.id
		if (!flavorId) {
			throw new WebServiceException(message(code: "invalid.flavor.name.message",
					args: [config.osFlavorName]), 500)
		}
		return flavorId
	}

	private String getAndVerifyImageId(OSClient osClient, Map<String, Object> config) throws WebServiceException {
		def useBootableImage = config.useBootableImage
		def useSnapshot = config.useSnapshot
		def matchImagePrefix = config.matchImagePrefix
		def allImages = osClient.compute().images().list()
		String imageName = config.osImageName
		String imageId = allImages.sort { it.name }.reverse().find {
			if ((matchImagePrefix && !it.name.startsWith(imageName)) ||
					(!matchImagePrefix && it.name!=imageName))
				return false

			// Retrieve more information on this image and check boot and/or snapshot status
			def image = osClient.images().get(it.id)

			// Check bootable status
			if (useBootableImage && (image.containerFormat==ContainerFormat.AKI ||
					image.containerFormat==ContainerFormat.ARI))
				return false

			// Check snapshot status
			def isSnapshot = image.properties?.getAt(OS_IMAGE_TYPE_PROPERTY_KEY) == OS_IMAGE_TYPE_SNAPSHOT
			if ((useSnapshot && !isSnapshot) || (!useSnapshot && isSnapshot))
				return false

			// Else all checks pass and this is a valid image
			return true
		}?.id
		if (!imageId) {
			throw new WebServiceException(message(code: "invalid.image.name.message",
					args: [config.osImageName]), 500)
		}
		return imageId
	}

	private ServerCreate buildNewServerRequest(String requestId, int serverNumber,
												String flavorId, String imageId,
												Date date, Map<String, Object> config) {
		final String name = ((String) config.osInstanceNamePattern)
				.replace(REQUEST_ID_TOKEN, requestId)
				.replace(DATE_TOKEN, date.time.toString())
				.replace(SERVER_NUMBER_TOKEN, (serverNumber).toString())
		ServerCreateBuilder builder = Builders.server()
				.name(name)
				.flavor(flavorId)
				.image(imageId)
		if (config.osKeyPairName) {
			log.trace("Setting the keypair name to ${config.osKeyPairName} for request '${requestId}'")
			builder = builder.keypairName(config.osKeyPairName)
		}
		ServerCreate serverCreate = builder.build()

		if (config.osInitScript) {
			def userData = new String(Base64.encodeBase64(config.osInitScript.toString().getBytes("UTF-8")), "UTF-8")
			log.trace("Setting the user data to base64 data for request '${requestId}': ${userData}")
			// Because we are using groovy, we can set this *private* property
			serverCreate.@userData = userData
		}
		log.debug("Creating new server ${serverCreate.getName()} for request '${requestId}'")
		return serverCreate
	}

	private Server bootAndWaitForActive(OSClient osClient, ServerCreate serverCreate, Map<String, Object> config)
			throws WebServiceException {
		log.debug("Starting server ${serverCreate.getName()} and waiting for it to become active")
		Server server = osClient.compute().servers().boot(serverCreate)
		final def startTime = new Date().time
		final long timeout = config.activeTimeoutSeconds * 1000l
		while (server.status != Server.Status.ACTIVE) {
			if ((new Date().time - startTime) > timeout) {
				def message = "Could not get information from server ${server.getName()}, " +
						"timeout after ${config.activeTimeoutSeconds} seconds"
				log.error(message)
				throw new WebServiceException(message)
			}
			// Else keep trying
			sleep(SLEEP_VALUE)
			server = osClient.compute().servers().get(server.id)
		}
		log.debug("Server ${server.getName()} is active")
		return server
	}

	private String getIpAddress(Server server, Map<String, Object> config) {
		log.debug("Attempting to get IP address information from server ${server.getName()}")
		List<? extends Address> addresses = null
		if (config.osVlanName)
			addresses = server.getAddresses().getAddresses(config.osVlanName)
		if (!addresses)
			addresses = server.getAddresses().getAddresses().values().find { it }
		return addresses?.find { it }?.addr
	}

	private void deleteServer(OSClient osClient, String serverId) {
		osClient.compute().servers().delete(serverId)
	}

	public def triggerBurst(Map params) throws WebServiceException {
		if (!params.requestId)
			throw new WebServiceException(message(code: "triggerBurst.missing.parameter.message", args: ["requestId"]), 400)
		if (!params.serverCount)
			throw new WebServiceException(message(code: "triggerBurst.missing.parameter.message", args: ["serverCount"]), 400)
		Integer serverCount = params.int('serverCount')
		if (!serverCount || serverCount < 1)
			throw new WebServiceException(message(code: "triggerBurst.invalid.server.count.message", args: ["serverCount"]), 400)

		Map<String, Object> config = getConfig()

		def osClientRetrieval = buildClient(config)

		// Retrieve flavor and image
		String imageId = getAndVerifyImageId(osClientRetrieval, config)
		String flavorId = getAndVerifyFlavorId(osClientRetrieval, config)
		log.debug("Using flavor ${flavorId} and image ${imageId}")

		// Retrieve current date if needed for the name
		final Date date = new Date()

		// Create new servers while limiting the number of concurrent connections
		final List<String> errors = Collections.synchronizedList([])
		final List<Future> futureList = []
		final def threadPool = Executors.newFixedThreadPool(config.maxRequestLimit)
		for (int i = 1; i <= serverCount; i++) { // i is a human readable number
			futureList << threadPool.submit({ int serverNumber -> // and so is the server number
				try {
					// If errors already exist, do not attempt to start any more servers since they will just be deleted
					if (errors)
						return null

					// Create client and build request
					final OSClient osClient = buildClient(config)
					final ServerCreate serverCreate = buildNewServerRequest(params.requestId.toString(),
							serverNumber, flavorId, imageId, date, config)

					// Boot new servers and wait for timeout until the server is active
					final Server server = bootAndWaitForActive(osClient, serverCreate, config)

					// Return data to the client
					final String ipAddress = getIpAddress(server, config)
					return new ServerInformation(
							id: server.id,
							ipAddress: ipAddress,
							name: server.name,
							powerState: server.powerState=='1' ? 'Running' : 'Unknown'
					)
				} catch (Exception e) {
					log.error("Caught exception while creating server ${serverNumber}", e)
					errors.add(message(code: "triggerBurst.exception.message",
							args: [
									e.getMessage() ?: message(code: "unknown.exception.message")
							]
					))
					return null
				}
			}.curry(i) as Callable)
		}
		// Execute get here in order to let all threads run as they wish in the pool
		final List<ServerInformation> serverInformationList = futureList.collect { it.get() }.findAll { it }

		// If there are errors, use the thread pool to destroy the servers that were started
		if (errors) {
			log.warn("Errors occurred while bursting, destroying ${serverInformationList.size()} created servers")
			futureList.clear()
			def errorCount = errors.size()
			serverInformationList.each { ServerInformation info ->
				futureList << threadPool.submit({ ServerInformation serverInformation ->
					try {
						log.info("Destroying server ${serverInformation.name} (${serverInformation.id})")
						final def osClient = buildClient(config)
						deleteServer(osClient, serverInformation.id)
					} catch (Exception e) {
						log.error("Caught exception while deleting server ${serverInformation.name} (${serverInformation.id})", e)
						errors.add(message(code: "triggerBurst.delete.exception.message", args: [
								serverInformation.name,
								e.getMessage() ?: message(code: "unknown.exception.message")
						]))
					}
				}.curry(info))
			}
			futureList.each { it.get() }
			errors.add(0, message(code:"triggerBurst.error.message", args:[errorCount]))

			// Close down thread pool
			threadPool.shutdown()

			// Throw exception
			throw new WebServiceException(errors, 500)
		}

		// Close down thread pool
		threadPool.shutdown()

		return serverInformationList
	}

	public def triggerNodeEnd(Map params) {
		if (!params.id)
			throw new WebServiceException(message(code: "triggerNodeEnd.missing.parameter.message", args: ["id"]), 400)
		String nodeName = params.id

		Map<String, Object> config = getConfig()
		def osClient = buildClient(config)
		def serverService = osClient.compute().servers()
		def server = serverService.list(false).find { it.getName()==nodeName }
		if (!server) {
			throw new WebServiceException(message(code:"triggerNodeEnd.not.found.message", args:[nodeName]), 404)
		}

		serverService.delete(server.getId())

		final def startTime = new Date().time
		final long timeout = config.deleteTimeoutSeconds * 1000l
		while (server) {
			if ((new Date().time - startTime) > timeout) {
				log.error("Server ${server.getName()} was not deleted successfully, " +
						"timeout after ${config.deleteTimeoutSeconds} seconds")
				throw new WebServiceException(
						message(code:"triggerNodeEnd.timeout.message", args:[nodeName, config.deleteTimeoutSeconds]),
						500)
			}
			// Else keep trying
			sleep(SLEEP_VALUE)
			server = serverService.get(server.id)
		}

		return [messages:[message(code:"triggerNodeEnd.success.message", args:[nodeName])]]
	}
}

class ServerInformation {
	String id
	String name
	String ipAddress
	String powerState
}