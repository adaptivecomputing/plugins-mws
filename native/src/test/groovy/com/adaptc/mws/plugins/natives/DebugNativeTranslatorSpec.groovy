package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.JobReport
import com.adaptc.mws.plugins.NodeReport
import com.adaptc.mws.plugins.testing.TestFor
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
@TestFor(DebugNativeTranslator)
class DebugNativeTranslatorSpec extends Specification {
	def "Verify cluster wiki"() {
		given:
		NodeNativeTranslator nodeNativeTranslator = Mock()
		translator.nodeNativeTranslator = nodeNativeTranslator
		DebugNativeTranslator.DebugEventService debugEventService = Mock()
		NodeReport nodeReport = Mock()

		and:
		translator.metaClass.verifyWiki = { wiki, String id, Closure callTranslator ->
			assert wiki=="wiki"
			assert id=="id"
			assert callTranslator
			def result = callTranslator.call(debugEventService, [testNode:true], [:])
			assert result==nodeReport
			return [result:true]
		}

		when:
		def result = translator.verifyClusterWiki("wiki", "id")

		then:
		1 * nodeNativeTranslator.createReport(debugEventService, [testNode:true]) >> nodeReport
		0 * _._
		result==[result:true]
	}

	def "Verify workload wiki"() {
		given:
		JobNativeTranslator jobNativeTranslator = Mock()
		translator.jobNativeTranslator = jobNativeTranslator
		DebugNativeTranslator.DebugEventService debugEventService = Mock()
		JobReport jobReport = Mock()

		and:
		translator.metaClass.verifyWiki = { wiki, String id, Closure callTranslator ->
			assert wiki=="wiki"
			assert id=="id"
			assert callTranslator
			assert callTranslator.call(debugEventService, [testJob:true], [:])==jobReport
			return [result:true]
		}

		when:
		def result = translator.verifyWorkloadWiki("wiki", "id")

		then:
		1 * jobNativeTranslator.createReport(debugEventService, [testJob:true]) >> jobReport
		0 * _._
		result==[result:true]
	}

	def "Verify wiki main method"() {
		when: "Errors exist"
		def result = translator.verifyWiki("wiki1\n#ignore this line\nwiki2", "id", { DebugNativeTranslator.DebugEventService des, attrs, lineInfo ->
			if (attrs.id=="wiki1") {
				des.updateNotificationCondition(null, "message1", null, null)
				des.updateNotificationCondition(null, "message2", null, null)
				return new NodeReport("invalid")
			}
			return new NodeReport("valid")
		})

		then:
		result.size()==4
		result.valid==false
		result.totalErrors==2
		result.totalLines==2
		result.lines.size()==2
		result.lines[0].content=="wiki1"
		result.lines[0].errors.size()==2
		result.lines[0].errors[0]=="message1"
		result.lines[0].errors[1]=="message2"
		result.lines[0].size()==4
		result.lines[0].type=="Node"
		result.lines[0].report.name=="invalid"
		result.lines[0].report.pluginId=="id"
		result.lines[1].size()==4
		result.lines[1].type=="Node"
		result.lines[1].content=="wiki2"
		result.lines[1].errors.size()==0
		result.lines[1].report.name=="valid"
		result.lines[1].report.pluginId=="id"

		when:
		result = translator.verifyWiki("wiki1\n#ignore this line\nwiki2", "id", { des, attrs, lineInfo ->
			return new NodeReport("valid")
		})

		then:
		result.size()==4
		result.valid==true
		result.totalErrors==0
		result.totalLines==2
		result.lines.size()==2
		result.lines[0].size()==4
		result.lines[0].type=="Node"
		result.lines[0].content=="wiki1"
		result.lines[0].errors.size()==0
		result.lines[0].report.name=="valid"
		result.lines[0].report.pluginId=="id"
		result.lines[1].size()==4
		result.lines[0].type=="Node"
		result.lines[1].content=="wiki2"
		result.lines[1].errors.size()==0
		result.lines[1].report.name=="valid"
		result.lines[1].report.pluginId=="id"
	}
}
