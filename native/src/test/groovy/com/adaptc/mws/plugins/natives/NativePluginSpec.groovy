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
        IJobRMService jobRMService = Mock()
        plugin.jobRMService = jobRMService
		
		and: "Setup objects"
		def node1 = new NodeReport("node1")
		def nodes = [node1]
		def vm = new VirtualMachineReport("vm1")
		def vms = [vm]
        def job = new JobReport("job.1")
        def jobs = [job]

		when: "getNodes and getVMs"
		config = [:]
		plugin.metaClass.getNodes = { -> nodes }
		plugin.metaClass.getVirtualMachines = { -> vms }
		plugin.metaClass.getJobs = { -> jobs }
		plugin.poll()
		
		then:
		1 * nodeRMService.save(nodes)
		1 * virtualMachineRMService.save(vms)
        1 * jobRMService.save(jobs)
		0 * _._

		when: "getCluster returns nodes and VMs"
		config = [getCluster:"getCluster"]
		plugin.metaClass.getCluster = { -> nodes + vms }
		plugin.poll()
		
		then:
		1 * nodeRMService.save(nodes)
		1 * virtualMachineRMService.save(vms)
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
		IJobRMService jobRMService = Mock()
		plugin.jobRMService = jobRMService

		when: "getNodes and getVirtualMachines"
		config = [:]
		plugin.metaClass.getNodes = { -> [] }
		plugin.metaClass.getVirtualMachines = { -> [] }
		plugin.metaClass.getJobs = { -> [] }
		plugin.poll()

		then: "Save calls are still executed"
		1 * nodeRMService.save([])
		1 * virtualMachineRMService.save([])
		1 * jobRMService.save([])
		0 * _._

		when:
		config = [getCluster:"getCluster"]
		plugin.metaClass.getCluster = { -> [] }
		plugin.poll()

		then: "Save calls are still executed"
		1 * nodeRMService.save([])
		1 * virtualMachineRMService.save([])
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
		config = [startUrl:"file:///start"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/start"
			return [exitCode:128, content:["ERROR"]]
		}
		plugin.beforeStart()
		
		then:
		true
		
		when: "Start exception"
		config = [startUrl:"file:///start"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/start"
			throw new Exception()
		}
		plugin.afterStop()
		
		then:
		notThrown(Exception)
		
		when: "Start success"
		config = [startUrl:"file:///start"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/start"
			return [exitCode:0, content:["content line"]]
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
		config = [stopUrl:"file:///stop"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/stop"
			return [exitCode:128, content:["ERROR"]]
		}
		plugin.afterStop()
		
		then:
		true
		
		when: "Stop exception"
		config = [stopUrl:"file:///stop"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/stop"
			throw new Exception()
		}
		plugin.afterStop()
		
		then:
		notThrown(Exception)
		
		when: "Stop success"
		config = [stopUrl:"file:///stop"]
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/stop"
			return [exitCode:0, content:["content line"]]
		}
		plugin.afterStop()
		
		then:
		true
	}
	
	def "Has error"() {
		expect:
		result==plugin.hasError(scriptResult, canBeEmpty)
		
		where:
		canBeEmpty	| scriptResult						| result
		false		| [exitCode:128]					| true
		false		| [exitCode:0]						| true
		false		| [exitCode:0, content:[]]			| true
		false		| [exitCode:0, content:["ERROR"]]	| true
		false		| [exitCode:0, content:["Line"]]	| false
		true		| [exitCode:128]					| true
		true		| [exitCode:0]						| true
		true		| [exitCode:0, content:[]]			| false
		true		| [exitCode:0, content:["ERROR"]]	| true
		true		| [exitCode:0, content:["Line"]]	| false
	}
	
	def "Get cluster"() {
		given:
		VirtualMachineNativeTranslator virtualMachineNativeTranslator = Mock()
		plugin.virtualMachineNativeTranslator = virtualMachineNativeTranslator
		NodeNativeTranslator nodeNativeTranslator = Mock()
		plugin.nodeNativeTranslator = nodeNativeTranslator
		
		and:
		def node = new NodeReport("node01")
		def vm = new VirtualMachineReport("vm1")
		
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/c"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { String line ->
			assert line=="Line"
			return [parseWikiResult]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert canBeEmpty
			return hasError
		}
		def result = plugin.getCluster()
		
		then:
		(0..1) * virtualMachineNativeTranslator.createReport(vmAttrs) >> vm
		(0..1) * nodeNativeTranslator.createReport(nodeAttrs) >> node
		result.size()==resultSize
		
		where:
		pluginConfig			| readURLResult					 | hasError		| parseWikiResult		| vmAttrs				| nodeAttrs	 | resultSize
		[:]						| null							 | true			| null			 		| null					| null		 | 0
		[getCluster:"file:///c"]| [exitCode:128]				 | true			| null			 		| null					| null		 | 0
		[getCluster:"file:///c"]| [exitCode:0, content:["Line"]] | false		| [wiki:true]	 		| null					| [wiki:true]| 1
		[getCluster:"file:///c"]| [exitCode:0, content:["Line"]] | false		| [CONTAINERNODE:"vm1"]	| [CONTAINERNODE:"vm1"]	| null		 | 1
	}

    def "Get jobs"() {
        given:
        JobNativeTranslator jobNativeTranslator = Mock()
        plugin.jobNativeTranslator = jobNativeTranslator

        and:
        def job = new JobReport("job.1")

        when:
        config = pluginConfig
        plugin.metaClass.readURL = { URL url ->
            assert url.toString()=="file:/n"
            return readURLResult
        }
        NativeUtils.metaClass.'static'.parseWiki = { lines ->
            assert lines==["Line"]
            return [[wiki:true]]
        }
        plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
            assert canBeEmpty
            return hasError
        }
        def result = plugin.getJobs()

        then:
        calls * jobNativeTranslator.createReport({ it.wiki }) >> job
        0 * _._
        result.size()==resultSize

        where:
        pluginConfig			| readURLResult					 | hasError		| resultSize	| calls
        [:]						| null							 | true			| 0				| 0
        [getJobs:"file:///n"]	| [exitCode:128]				 | true			| 0				| 0
        [getJobs:"file:///n"]	| [exitCode:0, content:["Line"]] | false		| 1				| 1
    }
	
	def "Get nodes with result #readURLResult"() {
		given:
		NodeNativeTranslator nodeNativeTranslator = Mock()
		plugin.nodeNativeTranslator = nodeNativeTranslator
		
		and:
		def node = new NodeReport("node01")

		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/n"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { lines ->
			assert lines==["Line"]
			return [[wiki:true]]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert canBeEmpty
			return hasError
		}
		def result = plugin.getNodes()
		
		then:
		calls * nodeNativeTranslator.createReport({ it.wiki }) >> node
		0 * _._
		result.size()==resultSize
		
		where:
		pluginConfig			| readURLResult					 | hasError		| resultSize	| calls
		[:]						| null							 | true			| 0				| 0
		[getNodes:"file:///n"]	| [exitCode:128]				 | true			| 0				| 0
		[getNodes:"file:///n"]	| [exitCode:0, content:["Line"]] | false		| 1				| 1
	}
	
	def "Get virtual machines"() {
		given:
		VirtualMachineNativeTranslator virtualMachineNativeTranslator = Mock()
		plugin.virtualMachineNativeTranslator = virtualMachineNativeTranslator
		
		and:
		def vm = new VirtualMachineReport("vm1")
		
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/v"
			return readURLResult
		}
		NativeUtils.metaClass.'static'.parseWiki = { lines ->
			assert lines==["Line"]
			return [[wiki:true]]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert canBeEmpty
			return hasError
		}
		def result = plugin.getVirtualMachines()
		
		then:
		calls * virtualMachineNativeTranslator.createReport({ it.wiki }) >> vm
		0 * _._
		result.size()==resultSize
		
		where:
		pluginConfig					| readURLResult					 | hasError		| resultSize	| calls
		[:]								| null							 | true			| 0				| 0
		[getVirtualMachines:"file:///v"]| [exitCode:128]				 | true			| 0				| 0
		[getVirtualMachines:"file:///v"]| [exitCode:0, content:["Line"]] | false		| 1				| 1
	}
	
	def "Job cancel"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?job.1"
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobCancel(["job.1"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobCancel:"file:///url"]	| true			| false
		[jobCancel:"file:///url"]	| false			| true
	}
	
	def "Job modify"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?job.1&prop=val&prop2=\"val 2\""
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobModify(["job.1"], [prop:"val", prop2:"val 2"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobModify:"file:///url"]	| true			| false
		[jobModify:"file:///url"]	| false			| true
	}
	
	def "Job resume"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?job.1"
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobResume(["job.1"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobResume:"file:///url"]	| true			| false
		[jobResume:"file:///url"]	| false			| true
	}
	
	def "Job requeue"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?job.1"
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobRequeue(["job.1"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobRequeue:"file:///url"]	| true			| false
		[jobRequeue:"file:///url"]	| false			| true
	}
	
	def "Job start"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?job.1&node01,node01&bsaville"
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobStart("job.1", "node01,node01", "bsaville")
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobStart:"file:///url"]	| true			| false
		[jobStart:"file:///url"]	| false			| true
	}
	
	def "Job submit"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?NAME=job.1&MESSAGE=\"message here\""
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobSubmit([NAME:"job.1", MESSAGE:"message here"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobSubmit:"file:///url"]	| true			| false
		[jobSubmit:"file:///url"]	| false			| true
	}
	
	def "Job suspend"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?job.1"
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.jobSuspend(["job.1"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[jobSuspend:"file:///url"]	| true			| false
		[jobSuspend:"file:///url"]	| false			| true
	}
	
	def "Node modify"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?node1,node2&--set&prop=val&prop2=\"val 2\""
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.nodeModify(["node1","node2"], [prop:"val", prop2:"val 2"])
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[nodeModify:"file:///url"]	| true			| false
		[nodeModify:"file:///url"]	| false			| true
	}
	
	def "Node power"() {
		when:
		config = pluginConfig
		plugin.metaClass.readURL = { URL url ->
			assert url.toString()=="file:/url?node1,node2&ON"
			return [result:true]
		}
		plugin.metaClass.hasError = { resultParam, boolean canBeEmpty = false ->
			assert !canBeEmpty
			assert resultParam==[result:true]
			return hasError
		}
		def result = plugin.nodePower(["node1","node2"], NodeReportPower.ON)
		
		then:
		result==success
		
		where:
		pluginConfig				| hasError		| success
		[:]							| true			| false
		[nodePower:"file:///url"]	| true			| false
		[nodePower:"file:///url"]	| false			| true
	}
	
	def "IO exceptions are caught"() {
		given:
		NativePlugin.metaClass.setEnvironment = { urlConn -> }

		when: "File not found exception"
		URL.metaClass.openConnection = { ->
			return [connect:{}, content:[readLines:{
				throw new FileNotFoundException("this is my message")
			}]]
		}
		def result = plugin.readURL("file:///url".toURL())

		then:
		result==null
		plugin.hasError(result)

		when:
		URL.metaClass.openConnection = { ->
			return [connect:{}, content:[readLines:{
				throw new IOException("this is my message")
			}]]
		}
		result = plugin.readURL("file:///url".toURL())

		then:
		result==null
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

    and:
		boolean runPoll = true
		int pollsRunning = 0
		plugin.metaClass.getNodes = { ->
			pollsRunning++
			while(runPoll)
				sleep(100)
			pollsRunning--
      return []
		}
    plugin.metaClass.getVirtualMachines = {-> [] }
    plugin.metaClass.getJobs = {-> [] }

    and:
		plugin2.metaClass.getNodes = { ->
			pollsRunning++
			while(runPoll)
				sleep(100)
			pollsRunning--
      return []
		}
    plugin2.metaClass.getVirtualMachines = {-> [] }
    plugin2.metaClass.getJobs = {-> [] }

		and:
		def conditions = new PollingConditions(timeout:10)

		when:
    Thread.start {
		  plugin.poll()
    }
    Thread.start {
      plugin2.poll()
    }

    then:
    conditions.within(1) {
      pollsRunning==2
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
			pollsRunning==2
      0 * _._
		}

		when: "Stop polling"
		runPoll = false

    and: "Reset initial delay"
    conditions.initialDelay = 0

		then:
		conditions.within(1) {
			pollsRunning==0
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
			pollsRunning==2
      0 * _._
		}

		cleanup: "stop all polling"
		runPoll = false
	}
}
