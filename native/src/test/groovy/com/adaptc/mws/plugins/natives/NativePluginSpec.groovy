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
		IJobRMService jobRMService = Mock()
		plugin.jobRMService = jobRMService

		and: "Setup objects"
		def node1 = new NodeReport("node1")
		def nodes = [node1]
		def job = new JobReport("job.1")
		def jobs = [job]

		and:
		plugin.id = "plugin1"

		when: "getNodes"
		config = [:]
		plugin.metaClass.getNodes = { -> nodes }
		plugin.metaClass.getJobs = { -> jobs }
		plugin.poll()

		then:
		1 * nodeRMService.save(nodes)
		1 * jobRMService.save(jobs)
		0 * _._

		when: "getCluster returns nodes"
		config = [getCluster: "getCluster"]
		plugin.metaClass.getCluster = { -> nodes }
		plugin.poll()

		then:
		1 * nodeRMService.save(nodes)
		1 * jobRMService.save(jobs)
		0 * _._
	}

	@Issue("WS-1542")
	def "Calls to RM services happen even when no objects exist"() {
		given: "Mocks"
		INodeRMService nodeRMService = Mock()
		plugin.nodeRMService = nodeRMService
		IJobRMService jobRMService = Mock()
		plugin.jobRMService = jobRMService

		and:
		plugin.id = "plugin1"

		when: "getNodes"
		config = [:]
		plugin.metaClass.getNodes = { -> [] }
		plugin.metaClass.getJobs = { -> [] }
		plugin.poll()

		then: "Save calls are still executed"
		1 * nodeRMService.save([])
		1 * jobRMService.save([])
		0 * _._

		when: "getCluster only"
		config = [getCluster: "getCluster"]
		plugin.metaClass.getCluster = { -> [] }
		plugin.poll()

		then: "Save calls are still executed"
		1 * nodeRMService.save([])
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
		NodeNativeTranslator nodeNativeTranslator = Mock()
		plugin.nodeNativeTranslator = nodeNativeTranslator
		IPluginEventService pluginEventService = Mock()
		plugin.pluginEventService = pluginEventService

		and:
		def node = new NodeReport("node01")

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
		def result = plugin.getCluster()

		then:
		nodeReports * nodeNativeTranslator.createReport(pluginEventService, _ as Map) >> node
		0 * _._
		result.size() == resultSize

		where:
		pluginConfig              | readURLResult                    | hasError | parseWikiResult        	| nodeReports	| resultSize
		[:]                       | null                             | true     | null                   	| 0				| 0
		[getCluster: "file:///c"] | [exitCode: 128]                  | true     | [null]                 	| 0				| 0
		[getCluster: "file:///c"] | [exitCode: 0, content: ["Line"]] | false    | [[wiki: true]]         	| 1				| 1
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
		def result = plugin.getNodes()

		then:
		calls * nodeNativeTranslator.createReport(pluginEventService, { it.wiki }) >> node
		0 * _._
		result.size() == resultSize

		where:
		pluginConfig            | readURLResult                    | hasError | resultSize | calls
		[:]                     | null                             | true     | 0          | 0
		[getNodes: "file:///n"] | [exitCode: 128]                  | true     | 0          | 0
		[getNodes: "file:///n"] | [exitCode: 0, content: ["Line"]] | false    | 1          | 1
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

		and: "Without submission string and flags"
		def retVal = plugin.jobSubmit([name: "job.1"], "", "")

		then:
		(0..1) * jobNativeTranslator.convertJobToWiki([name:"job.1"], null, "") >>
				[NAME:"job.1", UNAME:"myuser", TASKS:1, RMFLAGS:"flag1 flag2"]
		0 * _._
		retVal == result

		when: "With submission string and flags"
		retVal = plugin.jobSubmit([name: "job.1"], "sleep 30", "flag1 flag2")

		then:
		(0..1) * jobNativeTranslator.convertJobToWiki([name:"job.1"], {it instanceof File && it.text=="sleep 30"}, "flag1 flag2") >>
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

		and:
		boolean runPoll = true
		int pollsRunning = 0
		plugin.metaClass.getNodes = { ->
			pollsRunning++
			while (runPoll)
				sleep(100)
			pollsRunning--
			return []
		}
		plugin.metaClass.getJobs = {-> [] }

		and:
		plugin2.metaClass.getNodes = { ->
			pollsRunning++
			while (runPoll)
				sleep(100)
			pollsRunning--
			return []
		}
		plugin2.metaClass.getJobs = {-> [] }

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

		when:
		plugin.configure()

		then:
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
