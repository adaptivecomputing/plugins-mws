package com.adaptc.mws.plugins.openstack

import com.adaptc.mws.plugins.WebServiceException
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.api.compute.ComputeImageService
import org.openstack4j.api.compute.ComputeService
import org.openstack4j.api.compute.FlavorService
import org.openstack4j.api.compute.ServerService
import org.openstack4j.api.image.ImageService
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Addresses
import org.openstack4j.model.compute.Flavor
import org.openstack4j.model.compute.Server
import org.openstack4j.model.compute.ServerCreate
import org.openstack4j.model.compute.builder.ServerCreateBuilder
import org.openstack4j.model.image.ContainerFormat
import org.openstack4j.model.image.Image
import org.openstack4j.openstack.OSFactory
import spock.lang.*
import com.adaptc.mws.plugins.testing.*

@TestFor(OpenStackPlugin)
@Unroll
class OpenstackPluginSpec extends Specification {
	def "Build client #desc"() {
		given:
		OSFactory osFactoryMock = Mock()
		OSFactory.metaClass.'static'.builder = { ->
			return osFactoryMock
		}
		OSClient osClientMock = Mock()

		when:
		OSClient client = plugin.buildClient(config)
		
		then:
		1 * osFactoryMock.endpoint(endpoint) >> osFactoryMock
		1 * osFactoryMock.credentials(username, password) >> osFactoryMock
		1 * osFactoryMock.tenantName(tenantName) >> osFactoryMock
		1 * osFactoryMock.authenticate() >> osClientMock
		0 * _._

		and:
		client==osClientMock

		where:
		desc << [
		        "with empty configuration",
				"with full config",
		]
		config << [
		        [:],
				[osEndpoint:"endpoint", osUsername:"username", osPassword:"password", osTenant:"tenantName"],
		]
		endpoint << [null, "endpoint"]
		username << [null, "username"]
		password << [null, "password"]
		tenantName << [null, "tenantName"]
	}

	def "Get and verify flavor errors #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService computeService = Mock()
		FlavorService flavorService = Mock()
		Flavor flavor1 = Mock()
		Flavor flavor2 = Mock()

		when:
		def id = plugin.getAndVerifyFlavorId(osClient, config)

		then:
		1 * osClient.compute() >> computeService
		1 * computeService.flavors() >> flavorService
		1 * flavorService.list() >> [flavor1, flavor2]
		_ * flavor1.getName() >> "flavor1"
		_ * flavor2.getName() >> "flavor2"
		0 * _._

		and:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0]=="invalid.flavor.name.message"

		where:
		desc			| config
		"empty config"	| [:]
		"bad name"		| [osFlavorName:"strawberry"]
	}

	def "Get and verify flavor successes #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService computeService = Mock()
		FlavorService flavorService = Mock()
		Flavor flavor1 = Mock()
		Flavor flavor2 = Mock()

		when:
		def result = plugin.getAndVerifyFlavorId(osClient, config)

		then:
		1 * osClient.compute() >> computeService
		1 * computeService.flavors() >> flavorService
		1 * flavorService.list() >> [flavor1, flavor2]
		_ * flavor1.getName() >> "flavor1"
		_ * flavor1.getId() >> "flavor1Id"
		_ * flavor2.getName() >> "flavor2"
		_ * flavor2.getId() >> "flavor2Id"
		0 * _._

		and:
		result==flavorId

		where:
		desc			| config						| flavorId
		"first flavor"	| [osFlavorName:"flavor1"]		| "flavor1Id"
		"second flavor"	| [osFlavorName:"flavor2"]		| "flavor2Id"
	}

	def "Get and verify bootable image #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()

		and:
		ComputeImageService computeImages = Mock()
		org.openstack4j.model.compute.Image computeImage1 = Mock()
		org.openstack4j.model.compute.Image computeImage2 = Mock()

		and:
		ImageService images = Mock()
		Image image1 = Mock()
		Image image2 = Mock()

		when:
		plugin.getAndVerifyImageId(osClient, config)

		then:
		1 * osClient.compute() >> compute
		1 * compute.images() >> computeImages
		1 * computeImages.list() >> [computeImage1, computeImage2]
		_ * computeImage1.getName() >> "image1"
		_ * computeImage1.getId() >> "image1Id"
		_ * computeImage2.getName() >> "image2"
		_ * computeImage2.getId() >> "image2Id"
		_ * osClient.images() >> images
		_ * images.get("image1Id") >> image1
		_ * images.get("image2Id") >> image2
		_ * image1.getContainerFormat() >> ContainerFormat.AKI
		_ * image2.getContainerFormat() >> ContainerFormat.ARI
		0 * _._

		and:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0]=="invalid.image.name.message"

		where:
		desc << [
		        "exact match",
				"prefix matching",
		]
		config << [
				[useBootableImage:true, matchImagePrefix:false, osImageName:"image1"],
				[useBootableImage:true, matchImagePrefix:true, osImageName:"image"],
		]
	}

	def "Get and verify snapshot image #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()

		and:
		ComputeImageService computeImages = Mock()
		org.openstack4j.model.compute.Image computeImage1 = Mock()
		org.openstack4j.model.compute.Image computeImage2 = Mock()

		and:
		ImageService images = Mock()
		Image image1 = Mock()
		Image image2 = Mock()

		when:
		plugin.getAndVerifyImageId(osClient, config)

		then:
		1 * osClient.compute() >> compute
		1 * compute.images() >> computeImages
		1 * computeImages.list() >> [computeImage1, computeImage2]
		_ * computeImage1.getName() >> "image1"
		_ * computeImage1.getId() >> "image1Id"
		_ * computeImage2.getName() >> "image2"
		_ * computeImage2.getId() >> "image2Id"
		_ * osClient.images() >> images
		_ * images.get("image1Id") >> image1
		_ * images.get("image2Id") >> image2
		_ * image1.getContainerFormat() >> ContainerFormat.OVF
		_ * image1.getProperties() >> null
		_ * image2.getContainerFormat() >> ContainerFormat.OVF
		_ * image2.getProperties() >> [(OpenStackPlugin.OS_IMAGE_TYPE_PROPERTY_KEY):"NotASnapshot"]
		0 * _._

		and:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0]=="invalid.image.name.message"

		where:
		desc << [
				"exact match",
				"prefix matching",
		]
		config << [
				[useSnapshot:true, matchImagePrefix:false, osImageName:"image1"],
				[useSnapshot:true, matchImagePrefix:true, osImageName:"image"],
		]
	}

	def "Get and verify not a snapshot image #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()

		and:
		ComputeImageService computeImages = Mock()
		org.openstack4j.model.compute.Image computeImage1 = Mock()
		org.openstack4j.model.compute.Image computeImage2 = Mock()

		and:
		ImageService images = Mock()
		Image image1 = Mock()
		Image image2 = Mock()

		when:
		plugin.getAndVerifyImageId(osClient, config)

		then:
		1 * osClient.compute() >> compute
		1 * compute.images() >> computeImages
		1 * computeImages.list() >> [computeImage1, computeImage2]
		_ * computeImage1.getName() >> "image1"
		_ * computeImage1.getId() >> "image1Id"
		_ * computeImage2.getName() >> "image2"
		_ * computeImage2.getId() >> "image2Id"
		_ * osClient.images() >> images
		_ * images.get("image1Id") >> image1
		_ * images.get("image2Id") >> image2
		_ * image1.getContainerFormat() >> ContainerFormat.OVF
		_ * image1.getProperties() >> [(OpenStackPlugin.OS_IMAGE_TYPE_PROPERTY_KEY):OpenStackPlugin.OS_IMAGE_TYPE_SNAPSHOT]
		_ * image2.getContainerFormat() >> ContainerFormat.OVF
		_ * image2.getProperties() >> [(OpenStackPlugin.OS_IMAGE_TYPE_PROPERTY_KEY):OpenStackPlugin.OS_IMAGE_TYPE_SNAPSHOT]
		0 * _._

		and:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0]=="invalid.image.name.message"

		where:
		desc << [
				"exact match",
				"prefix matching",
		]
		config << [
				[matchImagePrefix:false, osImageName:"image1"],
				[matchImagePrefix:true, osImageName:"image"],
		]
	}

	def "Get and verify image successfully #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()

		and:
		ComputeImageService computeImages = Mock()
		org.openstack4j.model.compute.Image computeImage1 = Mock()
		org.openstack4j.model.compute.Image computeImage2 = Mock()

		and:
		ImageService images = Mock()
		Image image1 = Mock()
		Image image2 = Mock()

		when:
		def result = plugin.getAndVerifyImageId(osClient, config)

		then:
		1 * osClient.compute() >> compute
		1 * compute.images() >> computeImages
		1 * computeImages.list() >> [computeImage1, computeImage2]
		_ * computeImage1.getName() >> "image1"
		_ * computeImage1.getId() >> "image1Id"
		_ * computeImage2.getName() >> "image2"
		_ * computeImage2.getId() >> "image2Id"
		_ * osClient.images() >> images
		_ * images.get("image1Id") >> image1
		_ * images.get("image2Id") >> image2
		_ * image1.getContainerFormat() >> ContainerFormat.AKI
		_ * image1.getProperties() >> [:]
		_ * image2.getContainerFormat() >> ContainerFormat.OVF
		_ * image2.getProperties() >> [(OpenStackPlugin.OS_IMAGE_TYPE_PROPERTY_KEY):OpenStackPlugin.OS_IMAGE_TYPE_SNAPSHOT]
		0 * _._

		and:
		result==imageId

		where:
		desc << [
				"exact match",
				"prefix matching",
		]
		config << [
				[matchImagePrefix:false, osImageName:"image1"],
				[useBootable:true, useSnapshot:true, matchImagePrefix:true, osImageName:"image"],
		]
		imageId << [
		        "image1Id",
				"image2Id"
		]
	}

	def "Build new server request #desc"() {
		given:
		ServerCreateBuilder serverCreateBuilder = Mock()
		ServerCreate serverCreate = Builders.server().build()
		Builders.metaClass.'static'.server = { ->
			return serverCreateBuilder
		}

		when:
		def result = plugin.buildNewServerRequest("abcd", 2, "flavor1", "image1", new Date(1000), config)

		then:
		1 * serverCreateBuilder.name(name) >> serverCreateBuilder
		1 * serverCreateBuilder.flavor("flavor1") >> serverCreateBuilder
		1 * serverCreateBuilder.image("image1") >> serverCreateBuilder
		(0..1) * serverCreateBuilder.keypairName(keypair) >> serverCreateBuilder
		1 * serverCreateBuilder.build() >> serverCreate
		0 * _._

		and:
		serverCreate.@userData==userData
		result==serverCreate

		where:
		desc			| config																	| name						| keypair	| userData
		"typical"		| [osInstanceNamePattern:"server-{date}-{server-number}"]					| "server-1000-2"			| null		| null
		"typical 2"		| [osInstanceNamePattern:"server-{request-id}-{server-number}"]				| "server-abcd-2"			| null		| null
		"with key"		| [osInstanceNamePattern:"server", osKeyPairName:"key1"]					| "server"					| "key1"	| null
		"with user data"| [osInstanceNamePattern:"server", osInitScript:"init script data"]			| "server"					| null		| "aW5pdCBzY3JpcHQgZGF0YQ=="
	}

	def "Boot and wait for active #desc"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()
		ServerService servers = Mock()
		ServerCreate serverCreate = Mock()
		Server server = Mock()

		when:
		def result = plugin.bootAndWaitForActive(osClient, serverCreate, config)

		then:
		1 * serverCreate.getName() >> "serverCreate1"
		_ * osClient.compute() >> compute
		(1.._) * compute.servers() >> servers
		1 * servers.boot(serverCreate) >> server
		_ * server.getId() >> "server1Id"
		_ * server.getName() >> "server1"
		getServerCalls * servers.get("server1Id") >> server
		(1..3) * server.getStatus() >>> statusList
		0 * _._

		and:
		result==server

		where:
		desc 				| config						| getServerCalls	| statusList
		"init active"		| [activeTimeoutSeconds:0.1]	| 0					| [Server.Status.ACTIVE]
		"wait one sec"		| [activeTimeoutSeconds:0.1]	| 1					| [Server.Status.BUILD, Server.Status.ACTIVE]
		"wait two secs"		| [activeTimeoutSeconds:1.1]	| 1					| [Server.Status.BUILD, Server.Status.ACTIVE]
		"last call active"	| [activeTimeoutSeconds:1.1]	| 2					| [Server.Status.BUILD, Server.Status.BUILD, Server.Status.ACTIVE]
	}

	def "Boot and wait for active timeout"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()
		ServerService servers = Mock()
		ServerCreate serverCreate = Mock()
		Server server = Mock()

		when:
		plugin.bootAndWaitForActive(osClient, serverCreate, [activeTimeoutSeconds:1.1])

		then:
		1 * serverCreate.getName() >> "serverCreate1"
		_ * osClient.compute() >> compute
		(1.._) * compute.servers() >> servers
		1 * servers.boot(serverCreate) >> server
		_ * server.getId() >> "server1Id"
		_ * server.getName() >> "server1"
		2 * servers.get("server1Id") >> server
		3 * server.getStatus() >> Server.Status.BUILD
		0 * _._

		and:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0].contains("timeout")
	}

	def "Get IP address"() {
		given:
		Server server = Mock()
		Addresses addresses = Mock()
		Address address = Mock()

		when: "No address keys"
		def result = plugin.getIpAddress(server, [:])

		then:
		1 * server.getName() >> "server1"
		1 * server.getAddresses() >> addresses
		1 * addresses.getAddresses() >> [:]
		0 * _._
		result==null

		when: "No address list"
		result = plugin.getIpAddress(server, [:])

		then:
		1 * server.getName() >> "server1"
		1 * server.getAddresses() >> addresses
		1 * addresses.getAddresses() >> [vlan1:[null]]
		0 * _._
		result==null

		when: "No specified VLAN"
		result = plugin.getIpAddress(server, [:])

		then:
		1 * server.getName() >> "server1"
		1 * server.getAddresses() >> addresses
		1 * addresses.getAddresses() >> [vlan1:[null, address]]
		1 * address.getAddr() >> "ipAddr"
		0 * _._
		result=="ipAddr"

		when: "With VLAN"
		result = plugin.getIpAddress(server, [osVlanName:"vlan2"])

		then:
		1 * server.getName() >> "server1"
		1 * server.getAddresses() >> addresses
		1 * addresses.getAddresses("vlan2") >> [null, address]
		1 * address.getAddr() >> "ipAddr"
		0 * _._
		result=="ipAddr"
	}

	def "Delete server"() {
		given:
		OSClient osClient = Mock()
		ComputeService compute = Mock()
		ServerService servers = Mock()

		when:
		plugin.deleteServer(osClient, "server1")

		then:
		1 * osClient.compute() >> compute
		1 * compute.servers() >> servers
		1 * servers.delete("server1")
		0 * _._
	}

	def "Trigger burst invalid parameters #desc"() {
		when:
		plugin.triggerBurst([*:params, int:{
			try {
				return params[it].toInteger()
			} catch(NumberFormatException e) {
				return null
			}
		}])

		then:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0]==message
		e.responseCode==400

		where:
		desc << [
		        "no request id",
				"no server count",
				"bogus server count",
				"negative server count",
				"zero server count",
		]
		params << [
		        [:],
				[requestId:"abcd"],
				[requestId:"abcd", serverCount:"bogus"],
				[requestId:"abcd", serverCount:"-1"],
				[requestId:"abcd", serverCount:"0"],
		]
		message << [
		        "triggerBurst.missing.parameter.message",
				"triggerBurst.missing.parameter.message",
				"triggerBurst.invalid.server.count.message",
				"triggerBurst.invalid.server.count.message",
				"triggerBurst.invalid.server.count.message",
		]
	}

	def "Trigger burst errors while provisioning"() {
		given: "set configuration"
		config.maxRequestLimit = 1

		and:
		ServerCreate serverCreate1 = Mock()
		ServerCreate serverCreate2 = Mock()
		ServerCreate serverCreate3 = Mock()
		def serverCreateList = [serverCreate1, serverCreate2, serverCreate3]
		Server server1 = Mock()
		Server server2 = Mock()

		and:
		OSClient osClient = Mock()
		plugin.metaClass.buildClient = { Map<String, Object> configParam ->
			assert configParam==config
			return osClient
		}
		plugin.metaClass.getAndVerifyImageId = { OSClient osClientParam, Map<String, Object> configParam ->
			assert osClientParam==osClient
			assert configParam==config
			return "image1Id"
		}
		plugin.metaClass.getAndVerifyFlavorId = { OSClient osClientParam, Map<String, Object> configParam ->
			assert osClientParam==osClient
			assert configParam==config
			return "flavor1Id"
		}
		plugin.metaClass.getIpAddress = { Server serverParam, Map<String, Object> configParam ->
			assert configParam==config
			switch(serverParam) {
				case server1:
					return "1.1.1.1"
				case server2:
					return "2.2.2.2"
				default:
					return null  // server 3 never makes it to this point
			}
		}
		plugin.metaClass.buildNewServerRequest = { String requestId, int serverNumber, String flavorId,
												   String imageId, Date date, Map<String, Object> configParam ->
			assert configParam==config
			assert requestId=="abcd"
			assert serverNumber > 0 && serverNumber <= 3
			assert flavorId=="flavor1Id"
			assert imageId=="image1Id"
			assert date <= new Date()
			// each server gets a different create request
			return serverCreateList[serverNumber-1]
		}
		plugin.metaClass.bootAndWaitForActive = { OSClient osClientParam, ServerCreate serverCreateParam,
												  Map<String, Object> configParam ->
			assert osClientParam==osClient
			assert configParam==config
			if (serverCreateParam==serverCreate1)
				return server1
			if (serverCreateParam==serverCreate2)
				return server2
			else
				throw new Exception("error while creating server 3")
		}

		and:
		def deletedServerIds = []
		plugin.metaClass.deleteServer = { OSClient osClientParam, String idParam ->
			assert osClientParam==osClient
			deletedServerIds << idParam
			if (idParam=="server2Id")
				throw new Exception("bad things happened while deleting")
		}

		when:
		plugin.triggerBurst([requestId:"abcd", serverCount:"3", int:{
			assert it=="serverCount"
			return 3
		}])

		then:
		_ * server1.getName() >> "server1"
		_ * server1.getId() >> "server1Id"
		_ * server1.getPowerState() >> "1"
		_ * server2.getName() >> "server2"
		_ * server2.getId() >> "server2Id"
		_ * server2.getPowerState() >> "1"
		0 * _._

		and:
		WebServiceException e = thrown()
		e.messages.size()==3
		e.messages[0]=="triggerBurst.error.message"
		e.messages[1]=="triggerBurst.exception.message"
		e.messages[2]=="triggerBurst.delete.exception.message"

		and:
		deletedServerIds.size()==2
		deletedServerIds[0]=="server1Id"
		deletedServerIds[1]=="server2Id"
	}

	def "Trigger burst success"() {
		given: "set configuration"
		config.maxRequestLimit = 3

		and:
		ServerCreate serverCreate1 = Mock()
		ServerCreate serverCreate2 = Mock()
		ServerCreate serverCreate3 = Mock()
		def serverCreateList = [serverCreate1, serverCreate2, serverCreate3]
		Server server1 = Mock()
		Server server2 = Mock()
		Server server3 = Mock()

		and:
		OSClient osClient = Mock()
		plugin.metaClass.buildClient = { Map<String, Object> configParam ->
			assert configParam==config
			return osClient
		}
		plugin.metaClass.getAndVerifyImageId = { OSClient osClientParam, Map<String, Object> configParam ->
			assert osClientParam==osClient
			assert configParam==config
			return "image1Id"
		}
		plugin.metaClass.getAndVerifyFlavorId = { OSClient osClientParam, Map<String, Object> configParam ->
			assert osClientParam==osClient
			assert configParam==config
			return "flavor1Id"
		}
		plugin.metaClass.getIpAddress = { Server serverParam, Map<String, Object> configParam ->
			assert configParam==config
			switch(serverParam) {
				case server1:
					return "1.1.1.1"
				case server2:
					return "2.2.2.2"
				case server3:
					return "3.3.3.3"
				default:
					return null
			}
		}
		plugin.metaClass.buildNewServerRequest = { String requestId, int serverNumber, String flavorId,
												   String imageId, Date date, Map<String, Object> configParam ->
			assert configParam==config
			assert requestId=="abcd"
			assert serverNumber > 0 && serverNumber <= 3
			assert flavorId=="flavor1Id"
			assert imageId=="image1Id"
			assert date <= new Date()
			// each server gets a different create request
			return serverCreateList[serverNumber-1]
		}
		plugin.metaClass.bootAndWaitForActive = { OSClient osClientParam, ServerCreate serverCreateParam,
												  Map<String, Object> configParam ->
			assert osClientParam==osClient
			assert configParam==config
			if (serverCreateParam==serverCreate1)
				return server1
			if (serverCreateParam==serverCreate2)
				return server2
			if (serverCreateParam==serverCreate3)
				return server3
		}

		when:
		def results = plugin.triggerBurst([requestId:"abcd", serverCount:"3", int:{
			assert it=="serverCount"
			return 3
		}])

		then:
		_ * server1.getName() >> "server1"
		_ * server1.getId() >> "server1Id"
		_ * server1.getPowerState() >> "1"
		_ * server2.getName() >> "server2"
		_ * server2.getId() >> "server2Id"
		_ * server2.getPowerState() >> "0"
		_ * server3.getName() >> "server3"
		_ * server3.getId() >> "server3Id"
		_ * server3.getPowerState() >> "2"
		0 * _._

		and:
		results.size()==3

		when:
		def sortedResults = results.sort { it.name }

		then:
		sortedResults[0].id=="server1Id"
		sortedResults[0].name=="server1"
		sortedResults[0].ipAddress=="1.1.1.1"
		sortedResults[0].powerState=="Running"
		sortedResults[1].id=="server2Id"
		sortedResults[1].name=="server2"
		sortedResults[1].ipAddress=="2.2.2.2"
		sortedResults[1].powerState=="Unknown"
		sortedResults[2].id=="server3Id"
		sortedResults[2].name=="server3"
		sortedResults[2].ipAddress=="3.3.3.3"
		sortedResults[2].powerState=="Unknown"
	}

	def "Trigger node end"() {
		given:
		config.deleteTimeoutSeconds = 1.1
		OSClient osClient = Mock()
		ComputeService compute = Mock()
		ServerService servers = Mock()
		Server server = Mock()

		and:
		plugin.metaClass.buildClient = { Map<String, Object> configParam ->
			assert config==configParam
			return osClient
		}

		when: "no id"
		plugin.triggerNodeEnd([:])

		then:
		WebServiceException e = thrown()
		e.messages.size()==1
		e.messages[0]=="triggerNodeEnd.missing.parameter.message"
		e.responseCode==400

		when: "server not found"
		plugin.triggerNodeEnd([id:"bogus"])

		then:
		1 * osClient.compute() >> compute
		1 * compute.servers() >> servers
		1 * servers.list(false) >> []
		0 * _._

		and:
		e = thrown()
		e.messages.size()==1
		e.messages[0]=="triggerNodeEnd.not.found.message"
		e.responseCode==404

		when: "timeout waiting for server to delete"
		plugin.triggerNodeEnd([id:"server1"])

		then:
		1 * osClient.compute() >> compute
		1 * compute.servers() >> servers
		2 * servers.get("server1Id") >> server
		1 * servers.delete("server1Id")
		1 * servers.list(false) >> [server]
		_ * server.getName() >> "server1"
		_ * server.getId() >> "server1Id"
		0 * _._

		and:
		e = thrown()
		e.messages.size()==1
		e.messages[0]=="triggerNodeEnd.timeout.message"
		e.responseCode==500

		when: "success"
		def result = plugin.triggerNodeEnd([id:"server1"])

		then:
		1 * osClient.compute() >> compute
		1 * compute.servers() >> servers
		2 * servers.get("server1Id") >>> [server, null]
		1 * servers.delete("server1Id")
		1 * servers.list(false) >> [server]
		_ * server.getName() >> "server1"
		_ * server.getId() >> "server1Id"
		0 * _._

		and:
		result.size()==1
		result.messages?.size()==1
		result.messages[0]=="triggerNodeEnd.success.message"
	}
}