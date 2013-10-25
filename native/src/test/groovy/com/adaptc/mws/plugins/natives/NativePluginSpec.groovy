package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.natives.utils.NativeUtils
import com.adaptc.mws.plugins.testing.*
import spock.lang.*
import com.adaptc.mws.plugins.*
import spock.util.concurrent.PollingConditions

@Unroll
@TestFor(NativePlugin)
class NativePluginSpec extends Specification {
	def "Poll"() {
		given: "Mocks"
		INodeRMService nodeRMService = Mock()
		plugin.nodeRMService = nodeRMService
		IVirtualMachineRMService virtualMachineRMService = Mock()
		plugin.virtualMachineRMService = virtualMachineRMService
		IStorageRMService storageRMService = Mock()
		plugin.storageRMService = storageRMService
		IJobRMService jobRMService = Mock()
		plugin.jobRMService = jobRMService
		ImageNativeTranslator imageNativeTranslator = Mock()
		plugin.imageNativeTranslator = imageNativeTranslator

		and: "Setup objects"
		def node1 = new NodeReport("node1")
		def nodes = [node1]
		def vm = new VirtualMachineReport("vm1")
		def vms = [vm]
		def job = new JobReport("job.1")
		def jobs = [job]
		def storage = new StorageReport("storage1")
		def storageList = [storage]

		and:
		plugin.id = "plugin1"

		when: "getNodes, getVMs, and getStorage"
		config = [reportImages:true]
		plugin.metaClass.getNodes = { AggregateImagesInfo imagesInfo -> nodes }
		plugin.metaClass.getVirtualMachines = { AggregateImagesInfo imagesInfo -> vms }
		plugin.metaClass.getJobs = { -> jobs }
		plugin.metaClass.getStorage = { -> storageList }
		plugin.poll()

		then:
		1 * nodeRMService.save(nodes)
		1 * virtualMachineRMService.save(vms)
		1 * storageRMService.save(storageList)
		1 * jobRMService.save(jobs)
		1 * imageNativeTranslator.updateImages("plugin1", _ as AggregateImagesInfo)
		0 * _._

		when: "getCluster returns nodes, VMs, and storage"
		config = [getCluster: "getCluster", reportImages:false]
		plugin.metaClass.getCluster = { AggregateImagesInfo imagesInfo -> nodes + vms + storageList }
		plugin.poll()

		then:
		1 * nodeRMService.save(nodes)
		1 * virtualMachineRMService.save(vms)
		1 * storageRMService.save(storageList)
		1 * jobRMService.save(jobs)
		0 * _._
	}

	@Issue("WS-1542")
	def "Calls to RM services happen even when no objects exist"() {
		given: "Mocks"
		INodeRMService nodeRMService = Mock()
		plugin.nodeRMService = nodeRMService
		IVirtualMachineRMService virtualMachineRMService = Mock()
		plugin.virtualMachineRMService = virtualMachineRMService
		IStorageRMService storageRMService = Mock()
		plugin.storageRMService = storageRMService
		IJobRMService jobRMService = Mock()
		plugin.jobRMService = jobRMService
		ImageNativeTranslator imageNativeTranslator = Mock()
		plugin.imageNativeTranslator = imageNativeTranslator

		and:
		plugin.id = "plugin1"

		when: "getNodes, getVirtualMachines, and getStorage"
		config = [reportImages:true]
		plugin.metaClass.getNodes = { AggregateImagesInfo imagesInfo -> [] }
		plugin.metaClass.getVirtualMachines = { AggregateImagesInfo imagesInfo -> [] }
		plugin.metaClass.getJobs = { AggregateImagesInfo imagesInfo -> [] }
		plugin.metaClass.getStorage = { -> [] }
		plugin.poll()

		then: "Save calls are still executed"
		1 * nodeRMService.save([])
		1 * virtualMachineRMService.save([])
		1 * storageRMService.save([])
		1 * jobRMService.save([])
		1 * imageNativeTranslator.updateImages("plugin1", _ as AggregateImagesInfo)
		0 * _._

		when: "getCluster only"
		config = [getCluster: "getCluster"]
		plugin.metaClass.getCluster = { AggregateImagesInfo imagesInfo -> [] }
		plugin.poll()

		then: "Save calls are still executed"
		1 * nodeRMService.save([])
		1 * virtualMachineRMService.save([])
		1 * storageRMService.save([])
		1 * jobRMService.save([])
		0 * _._
	}

	def "Before start"() {
		when: "No URL"
		config = [:]
		plugin.beforeStart()

		then:
		true

		when: "Start failure"
		config = [startUrl: "file:///start"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/start"
			return [exitCode: 128, content: ["ERROR"]]
		}
		plugin.beforeStart()

		then:
		true

		when: "Start exception"
		config = [startUrl: "file:///start"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/start"
			throw new Exception()
		}
		plugin.afterStop()

		then:
		notThrown(Exception)

		when: "Start success"
		config = [startUrl: "file:///start"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/start"
			return [exitCode: 0, content: ["content line"]]
		}
		plugin.beforeStart()

		then:
		true
	}

	def "After stop"() {
		when: "No URL"
		config = [:]
		plugin.afterStop()

		then:
		true

		when: "Stop failure"
		config = [stopUrl: "file:///stop"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/stop"
			return [exitCode: 128, content: ["ERROR"]]
		}
		plugin.afterStop()

		then:
		true

		when: "Stop exception"
		config = [stopUrl: "file:///stop"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/stop"
			throw new Exception()
		}
		plugin.afterStop()

		then:
		notThrown(Exception)

		when: "Stop success"
		config = [stopUrl: "file:///stop"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/stop"
			return [exitCode: 0, content: ["content line"]]
		}
		plugin.afterStop()

		then:
		true
	}

	def "Has error for #scriptResult is #result"() {
		expect:
		result == plugin.hasError(scriptResult)

		where:
		scriptResult                     	| result
		null								| true
		[exitCode: 128]                 	| true
		[exitCode: 0]                   	| true
		[exitCode: 0, content: []]      	| false
		[exitCode: 0, content: ["ERROR"]]	| true
		[exitCode: 0, content: ["ERROR="]]	| false
		[exitCode: 0, content: ["Line"]]	| false
	}

	def "Get cluster"() {
		given:
		VirtualMachineNativeTranslator virtualMachineNativeTranslator = Mock()
		plugin.virtualMachineNativeTranslator = virtualMachineNativeTranslator
		StorageNativeTranslator storageNativeTranslator = Mock()
		plugin.storageNativeTranslator = storageNativeTranslator
		NodeNativeTranslator nodeNativeTranslator = Mock()
		plugin.nodeNativeTranslator = nodeNativeTranslator
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService

		and:
		def node = new NodeReport("node01")
		def vm = new VirtualMachineReport("vm1")
		def storage = new StorageReport("storage1")

		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/c"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { lines ->
			assert lines == ["Line"]
			return parseWikiResult
		}
		plugin.metaClass.hasError = { resultParam ->
			return hasError
		}
		def imagesInfo = new AggregateImagesInfo()
		def result = plugin.getCluster(imagesInfo)

		then:
		vmReports * virtualMachineNativeTranslator.createReport(pluginEventService, _ as Map, _ as VMImageInfo) >> vm
		nodeReports * nodeNativeTranslator.createReport(pluginEventService, _ as Map, _ as HVImageInfo) >> node
		storageReports * storageNativeTranslator.createReport(pluginEventService, _ as Map) >> storage
		_ * virtualMachineNativeTranslator.isVirtualMachineWiki(_ as Map) >>> isVMList
		_ * storageNativeTranslator.isStorageWiki(_ as Map) >>> isStorageList
		0 * _._
		result.size() == resultSize
		imagesInfo.hypervisorImages.size()==nodeReports
		imagesInfo.vmImages.size()==vmReports

		where:
		pluginConfig              | readURLResult                    | hasError | parseWikiResult        	| isVMList		| isStorageList	| vmReports	| storageReports	| nodeReports	| resultSize
		[:]                       | null                             | true     | null                   	| []			| []			| 0	        | 0					| 0				| 0
		[getCluster: "file:///c"] | [exitCode: 128]                  | true     | [null]                 	| []			| []			| 0			| 0					| 0				| 0
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[wiki: true]]         	| [false]		| [false]		| 0			| 0					| 1				| 1
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[CONTAINERNODE: "vm1"]] 	| [true]		| []			| 1			| 0					| 0				| 1
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[TYPE: "vM"]]           	| [true]		| []			| 1			| 0					| 0				| 1
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[TYPE:"vM"],[wiki:true]]	| [true,false]	| [false]		| 1			| 0					| 1				| 2
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[TYPE: "st"]]           	| [false]		| [true]		| 0			| 1					| 0				| 1
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[TYPE:"st"],[wiki:true]]	| [false,false]	| [true,false]	| 0			| 1					| 1				| 2
	}

	def "Get jobs for config #pluginConfig and result #readURLResult"() {
		given:
		JobNativeTranslator jobNativeTranslator = Mock()
		plugin.jobNativeTranslator = jobNativeTranslator
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService

		and:
		def job = new JobReport("job.1")

		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/n"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { lines ->
			assert lines == ["Line"]
			return [[wiki: true]]
		}
		plugin.metaClass.hasError = { resultParam ->
			return hasError
		}
		def result = plugin.getJobs()

		then:
		calls * jobNativeTranslator.createReport(pluginEventService, { it.wiki }) >> job
		0 * _._
		result.size() == resultSize

		where:
		pluginConfig           | readURLResult                    | hasError | resultSize | calls
		[:]                    | null                             | true     | 0          | 0
		[getJobs: "file:///n"] | [exitCode: 128]                  | true     | 0          | 0
		[getJobs: "file:///n"] | [exitCode: 0, content: ["Line"]] | false    | 1          | 1
	}

	def "Get nodes for config #pluginConfig and result #readURLResult"() {
		given:
		NodeNativeTranslator nodeNativeTranslator = Mock()
		plugin.nodeNativeTranslator = nodeNativeTranslator
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService

		and:
		def node = new NodeReport("node01")

		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/n"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { lines ->
			assert lines == ["Line"]
			return [[wiki: true]]
		}
		plugin.metaClass.hasError = { resultParam ->
			return hasError
		}
		def imagesInfo = new AggregateImagesInfo()
		def result = plugin.getNodes(imagesInfo)

		then:
		calls * nodeNativeTranslator.createReport(pluginEventService, { it.wiki }, _ as HVImageInfo) >> node
		0 * _._
		result.size() == resultSize
		imagesInfo.hypervisorImages.size()==resultSize
		imagesInfo.vmImages.size()==0

		where:
		pluginConfig            | readURLResult                    | hasError | resultSize | calls
		[:]                     | null                             | true     | 0          | 0
		[getNodes: "file:///n"] | [exitCode: 128]                  | true     | 0          | 0
		[getNodes: "file:///n"] | [exitCode: 0, content: ["Line"]] | false    | 1          | 1
	}

	def "Get virtual machines for config #pluginConfig and result #readURLResult"() {
		given:
		VirtualMachineNativeTranslator virtualMachineNativeTranslator = Mock()
		plugin.virtualMachineNativeTranslator = virtualMachineNativeTranslator
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService

		and:
		def vm = new VirtualMachineReport("vm1")

		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/v"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { lines ->
			assert lines == ["Line"]
			return [[wiki: true]]
		}
		plugin.metaClass.hasError = { resultParam ->
			return hasError
		}
		def imagesInfo = new AggregateImagesInfo()
		def result = plugin.getVirtualMachines(imagesInfo)

		then:
		calls * virtualMachineNativeTranslator.createReport(pluginEventService, { it.wiki }, _ as VMImageInfo) >> vm
		0 * _._
		result.size() == resultSize
		imagesInfo.hypervisorImages.size()==0
		imagesInfo.vmImages.size()==resultSize

		where:
		pluginConfig                      | readURLResult                    | hasError | resultSize | calls
		[:]                               | null                             | true     | 0          | 0
		[getVirtualMachines: "file:///v"] | [exitCode: 128]                  | true     | 0          | 0
		[getVirtualMachines: "file:///v"] | [exitCode: 0, content: ["Line"]] | false    | 1          | 1
	}

	def "Job cancel for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?job.1"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.jobCancel("job.1")

		then:
		result == success

		where:
		pluginConfig               | hasError | success
		[:]                        | true     | false
		[jobCancel: "file:///url"] | true     | false
		[jobCancel: "file:///url"] | false    | true
	}

	def "Job modify for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?job.1&prop=val&prop2=\"val 2\""
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.jobModify("job.1", [prop: "val", prop2: "val 2"])

		then:
		result == success

		where:
		pluginConfig               | hasError | success
		[:]                        | true     | false
		[jobModify: "file:///url"] | true     | false
		[jobModify: "file:///url"] | false    | true
	}

	def "Job resume for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?job.1"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.jobResume("job.1")

		then:
		result == success

		where:
		pluginConfig               | hasError | success
		[:]                        | true     | false
		[jobResume: "file:///url"] | true     | false
		[jobResume: "file:///url"] | false    | true
	}

	def "Job requeue for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?job.1"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.jobRequeue("job.1")

		then:
		result == success

		where:
		pluginConfig                | hasError | success
		[:]                         | true     | false
		[jobRequeue: "file:///url"] | true     | false
		[jobRequeue: "file:///url"] | false    | true
	}

	def "Job start for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?job.1&node01,node01&user1"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.jobStart("job.1", ["node01","node01"], "user1")

		then:
		result == success

		where:
		pluginConfig              | hasError | success
		[:]                       | true     | false
		[jobStart: "file:///url"] | true     | false
		[jobStart: "file:///url"] | false    | true
	}

	def "Job submit for config #pluginConfig and hasError #hasError and content #content"() {
		given:
		JobNativeTranslator jobNativeTranslator = Mock()
		plugin.jobNativeTranslator = jobNativeTranslator

		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?NAME=job.1&UNAME=myuser&TASKS=1&RMFLAGS=\"flag1 flag2\""
			return [result: true, content:content]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam.result==true
			return hasError
		}
		def retVal = plugin.jobSubmit([name: "job.1"], "flag1 flag2")

		then:
		(0..1) * jobNativeTranslator.convertJobToWiki([name:"job.1"], "flag1 flag2") >>
				[NAME:"job.1", UNAME:"myuser", TASKS:1, RMFLAGS:"flag1 flag2"]
		0 * _._
		retVal == result

		where:
		pluginConfig               | hasError | content		| result
		[:]                        | true     | null		| null
		[jobSubmit: "file:///url"] | true     | null		| null
		[jobSubmit: "file:///url"] | false    | null		| "job.1"
		[jobSubmit: "file:///url"] | false    | []			| "job.1"
		[jobSubmit: "file:///url"] | false    | ["job.2"]	| "job.2"
	}

	def "Job suspend for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?job.1"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.jobSuspend("job.1")

		then:
		result == success

		where:
		pluginConfig                | hasError | success
		[:]                         | true     | false
		[jobSuspend: "file:///url"] | true     | false
		[jobSuspend: "file:///url"] | false    | true
	}

	def "Node modify for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?node1,node2&--set&prop=val&prop2=\"val 2\""
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.nodeModify(["node1", "node2"], [prop: "val", prop2: "val 2"])

		then:
		result == success

		where:
		pluginConfig                | hasError | success
		[:]                         | true     | false
		[nodeModify: "file:///url"] | true     | false
		[nodeModify: "file:///url"] | false    | true
	}

	def "Node power for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?node1,node2&ON"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.nodePower(["node1", "node2"], NodeReportPower.ON)

		then:
		result == success

		where:
		pluginConfig               | hasError | success
		[:]                        | true     | false
		[nodePower: "file:///url"] | true     | false
		[nodePower: "file:///url"] | false    | true
	}

	def "VM power for config #pluginConfig and hasError #hasError"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString() == "file:/url?vm1,vm2&ON"
			return [result: true]
		}
		plugin.metaClass.hasError = { resultParam ->
			assert resultParam == [result: true]
			return hasError
		}
		def result = plugin.virtualMachinePower(["vm1", "vm2"], NodeReportPower.ON)

		then:
		result == success

		where:
		pluginConfig               				| hasError | success
		[:]                        				| true     | false
		[virtualMachinePower: "file:///url"] 	| true     | false
		[virtualMachinePower: "file:///url"] 	| false    | true
	}

	def "IO exceptions are caught"() {
		given:
		NativePlugin.metaClass.setEnvironment = { urlConn -> }

		when: "File not found exception"
		URL.metaClass.openConnection = {->
			return [connect: {}, content: [readLines: {
				throw new FileNotFoundException("this is my message")
			}]]
		}
		def result = plugin.readURL("file:///url".toURL())

		then:
		result == null
		plugin.hasError(result)

		when:
		URL.metaClass.openConnection = {->
			return [connect: {}, content: [readLines: {
				throw new IOException("this is my message")
			}]]
		}
		result = plugin.readURL("file:///url".toURL())

		then:
		result == null
		plugin.hasError(result)
	}

	def "Single poll runs at a time for each plugin"() {
		given:
		def plugin2 = mockPlugin(NativePlugin)

		and:
		INodeRMService nodeRMService = Mock()
		plugin.nodeRMService = nodeRMService
		plugin2.nodeRMService = nodeRMService
		IJobRMService jobRMService = Mock()
		plugin.jobRMService = jobRMService
		plugin2.jobRMService = jobRMService
		IVirtualMachineRMService virtualMachineRMService = Mock()
		plugin.virtualMachineRMService = virtualMachineRMService
		plugin2.virtualMachineRMService = virtualMachineRMService
		ImageNativeTranslator imageNativeTranslator = Mock()
		plugin.imageNativeTranslator = imageNativeTranslator
		plugin2.imageNativeTranslator = imageNativeTranslator

		and:
		boolean runPoll = true
		int pollsRunning = 0
		plugin.metaClass.getNodes = { AggregateImagesInfo imagesInfo ->
			pollsRunning++
			while (runPoll)
				sleep(100)
			pollsRunning--
			return []
		}
		plugin.metaClass.getVirtualMachines = { AggregateImagesInfo imagesInfo -> [] }
		plugin.metaClass.getJobs = {-> [] }
		plugin.metaClass.getStorage = {-> [] }

		and:
		plugin2.metaClass.getNodes = { AggregateImagesInfo imagesInfo ->
			pollsRunning++
			while (runPoll)
				sleep(100)
			pollsRunning--
			return []
		}
		plugin2.metaClass.getVirtualMachines = { AggregateImagesInfo imagesInfo -> [] }
		plugin2.metaClass.getJobs = {-> [] }
		plugin2.metaClass.getStorage = {-> [] }

		and:
		def conditions = new PollingConditions(timeout: 10)

		when:
		Thread.start {
			plugin.poll()
		}
		Thread.start {
			plugin2.poll()
		}

		then:
		conditions.within(1) {
			pollsRunning == 2
			0 * _._
		}

		when: "Polling waits for previous poll to finish"
		Thread.start {
			plugin.poll()
		}
		Thread.start {
			plugin2.poll()
		}

		and: "Delay the initial evaluation to make sure they start another poll"
		conditions.initialDelay = 1

		then:
		conditions.within(1) {
			pollsRunning == 2
			0 * _._
		}

		when: "Stop polling"
		runPoll = false

		and: "Reset initial delay"
		conditions.initialDelay = 0

		then:
		conditions.within(1) {
			pollsRunning == 0
			//1 * nodeRMService.save([])
			//1 * jobRMService.save([])
			//1 * virtualMachineRMService.save([])
			0 * _._
		}

		when: "Another poll runs without issues"
		runPoll = true
		Thread.start {
			plugin.poll()
		}
		Thread.start {
			plugin2.poll()
		}

		then:
		conditions.within(1) {
			pollsRunning == 2
			0 * _._
		}

		cleanup: "stop all polling"
		runPoll = false
	}

	def "Set environment for #env"() {
		given:
		URLConnection urlConnection = GroovyMock()

		when:
		config = [environment:env]
		plugin.setEnvironment(urlConnection)

		then:
		calls * urlConnection.setEnvironment(resultEnv)
		0 * _._

		where:
		env						|| calls			| resultEnv
		null					|| 0				| null
		""						|| 0				| null
		"one"					|| 1				| [one:null]
		"one=val"				|| 1				| [one:"val"]
		"one=val&two&three=3"	|| 1				| [one:"val",two:null,three:"3"]
	}

	def "Configure"() {
		given:
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService
		ImageNativeTranslator imageNativeTranslator = Mock()
		plugin.imageNativeTranslator = imageNativeTranslator

		when:
		plugin.configure()

		then:
		1 * imageNativeTranslator.setPluginEventService(pluginEventService)
		0 * _._
	}

	def "Verify queries failures for #params"() {
		when:
		plugin.verifyClusterQuery(params)

		then:
		WebServiceException e = thrown()
		e.responseCode==400
		e.messages.size()==1
		e.messages[0]=="nativePlugin.verify.wiki.empty.message"

		when:
		plugin.verifyWorkloadQuery(params)

		then:
		e = thrown()
		e.responseCode==400
		e.messages.size()==1
		e.messages[0]=="nativePlugin.verify.wiki.empty.message"

		where:
		params << [
				[:],
				[body:null],
				[body:[:]],
				[body:[content:null]],
				[body:[content:""]],
				[wiki:null],
				[wiki:""],
		]
	}

	def "Verify queries success"() {
		given:
		DebugNativeTranslator debugNativeTranslator = Mock()
		plugin.debugNativeTranslator = debugNativeTranslator
		plugin.id = "plugin1"

		when:
		def result = plugin.verifyClusterQuery([wiki:"wikiLines"])

		then:
		1 * debugNativeTranslator.verifyClusterWiki("wikiLines", "plugin1") >> [test:true]
		0 * _._
		result==[test:true]

		when:
		result = plugin.verifyWorkloadQuery([wiki:"wikiLines"])

		then:
		1 * debugNativeTranslator.verifyWorkloadWiki("wikiLines", "plugin1") >> [test:true]
		0 * _._
		result==[test:true]
	}
}
