package com.adaptc.mws.plugins.natives.utils

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author bsaville
 */
@Unroll
class NativeUtilsSpec extends Specification {
	def "Filter lines"() {
		when:
		def wikiStr = "SC=0 Ignore this\n# Ignore this too\nnode1 donotignore=true"
		def wiki = NativeUtils.filterLines(wikiStr.readLines())

		then:
		wiki.size()==1
		wiki[0]=="node1 donotignore=true"

		when:
		wiki = NativeUtils.parseWiki(wikiStr.readLines())

		then:
		wiki.size()==1
		wiki[0].size()==2
		wiki[0].id=="node1"
		wiki[0].donotignore=="true"
	}

	def testParseTypicalWiki() {
		when:
		def wikiStr = "node001 STATE=Idle;UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		1==wiki.size()

		when:
		def map = wiki[0]

		then:
		3==map.size()
		"node001"==map.id
		"Idle"==map.STATE
		"1039483"==map.UPDATETIME
	}

	def testParseDelimiterEndedWiki() {
		when:
		def wikiStr = "node001 STATE=Idle;UPDATETIME=1039483;"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.size()==3 //"Wiki ending in semi-colons should not have empty attributes"
	}

	def testParseSemiColonWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
	}

	def testParseSpaceWiki() {
		when:
		def wikiStr = "node001 STATE=Idle UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		1==wiki.size()

		when:
		def map = wiki[0]

		then:
		3==map.size()
		"node001"==map.id
		"Idle"==map.STATE
		"1039483"==map.UPDATETIME
	}

	def testParseMultipleSpacesWiki() {
		when:
		def wikiStr = "node001   STATE=Idle  UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		1==wiki.size()

		when:
		def map = wiki[0]

		then:
		3==map.size()
		"node001"==map.id
		"Idle"==map.STATE
		"1039483"==map.UPDATETIME
	}

	def testParseTabAndSpaceWiki() {
		when:
		def wikiStr = "node001	STATE=Idle	 	 UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		1==wiki.size()

		when:
		def map = wiki[0]

		then:
		3==map.size()
		"node001"==map.id
		"Idle"==map.STATE
		"1039483"==map.UPDATETIME
	}

	def testParseDoubleQuotedSpaceWiki() {
		when:
		def wikiStr = 'node001 STATE=Idle UPDATETIME=1039483 COMMENTS="This is a comment"'
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==4
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.COMMENTS=="This is a comment"
	}

	def "Parse variables with spaces #desc"() {
		when:
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==4
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.VARIABLE?.size()==1
		map.VARIABLE.var1==resultStr

		where:
		desc << [
		        "double quotes",
				"single quotes"
		]
		wikiStr << [
				'node001 STATE=Idle UPDATETIME=1039483 VARIABLE=var1="var with spaces"',
				"node001 STATE=Idle UPDATETIME=1039483 VARIABLE=var1='var with spaces'",
		]
		resultStr << [
		        'var with spaces',
				"'var with spaces'"
		]
	}

	def testParseSingleQuotedSpaceWiki() {
		when:
		def wikiStr = "node001 STATE=Idle UPDATETIME=1039483 COMMENTS='This is a comment'"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==4
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.COMMENTS=="'This is a comment'"
	}

	def testParseNewLineWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483\nnode002;STATE=Running;UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki(wikiStr.split("\n"))

		then:
		wiki.size()==2

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"

		when:
		map = wiki[1]

		then:
		map.size()==3
		map.id=="node002"
		map.STATE=="Running"
		map.UPDATETIME=="1039483"
	}

	def testParseHashLineWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483#node002;STATE=Running;UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki(wikiStr.split("\n"))

		then:
		wiki.size()==2

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"

		when:
		map = wiki[1]

		then:
		map.size()==3
		map.id=="node002"
		map.STATE=="Running"
		map.UPDATETIME=="1039483"
	}

	def testParseNewAndHashLineWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483#node002;STATE=Suspended;UPDATETIME=1039483\nnode003;STATE=Running;UPDATETIME=1039483"
		def wiki = NativeUtils.parseWiki(wikiStr.split("\n"))

		then:
		wiki.size()==3

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"

		when:
		map = wiki[1]

		then:
		map.size()==3
		map.id=="node002"
		map.STATE=="Suspended"
		map.UPDATETIME=="1039483"

		when:
		map = wiki[2]

		then:
		map.size()==3
		map.id=="node003"
		map.STATE=="Running"
		map.UPDATETIME=="1039483"
	}

	def testParseEscapedHashWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483;COMMENTS='Hash\\#'"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==4
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.COMMENTS=="'Hash#'"
	}

	def testParseEscapedSemiColonWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483;COMMENTS='SemiColon\\;'"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==4
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.COMMENTS=="'SemiColon;'"
	}

	def testWithColon() {
		when:
		def wikiStr = "job.1;STATE=Idle;PREF=FEAT1:FEAT2"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="job.1"
		map.STATE=="Idle"
		map.PREF=="FEAT1:FEAT2"
		map.PREF.split(":").size()==2
	}

	def testParseEscapedSemicolonWiki() {
		when:
		def wikiStr = "node001;STATE=Idle;UPDATETIME=1039483;COMMENTS='SemiColon\\;'"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==4
		map.id=="node001"
		map.STATE=="Idle"
		map.UPDATETIME=="1039483"
		map.COMMENTS=="'SemiColon;'"
	}

	@Unroll
	def testParseVariableWiki() {
		when:
		String wikiStr = "vm1 CONTAINERNODE=node001;$line"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size() == 1

		when:
		def map = wiki[0]

		then:
		map["VARIABLE"].size() == expectedVariables.size()
		expectedVariables.each { def key, def val ->
			assert map["VARIABLE"][key] == val
		}

		where:
		line                                     | expectedVariables
		'VARIABLE=abc123'                        | [abc123: '']
		'VARIABLE=def456=ghi789'                 | [def456: 'ghi789'] // test with = as a delimiter
		'VARIABLE=jkl012=mno~!@$%^&*()_+|pqr'    | [jkl012: 'mno~!@$%^&*()_+|pqr']
		'VARIABLE=stu345=vwx`-\\[]{}.,?yza'      | [stu345: 'vwx`-\\[]{}.,?yza']
		'VARIABLE=a=1;VARIABLE=b=2;VARIABLE=c=3' | [a: '1', b: '2', c: '3']
		'VARIABLE=d=1+2=3'                       | [d: '1+2=3']
		'VARIABLE=e=http://example.com?a=1&b=2'  | [e: 'http://example.com?a=1&b=2']
		'VARIABLE=def456:ghi789'                 | [def456: 'ghi789'] // test with : as a delimiter
		'VARIABLE=jkl012:mno~!@$%^&*()_+|pqr'    | [jkl012: 'mno~!@$%^&*()_+|pqr']
		'VARIABLE=stu345:vwx`-\\[]{}.,?yza'      | [stu345: 'vwx`-\\[]{}.,?yza']
		'VARIABLE=a:1;VARIABLE=b:2;VARIABLE=c:3' | [a: '1', b: '2', c: '3']
		'VARIABLE=d:1+2=3'                       | [d: '1+2=3']
		'VARIABLE=e:http://example.com?a=1&b=2'  | [e: 'http://example.com?a=1&b=2']
	}

	def testFullClusterWiki() {
		when:
		def wikiStr = this.class.classLoader.getResource("testfiles/msm-cluster-query.wiki").text
		def wiki = NativeUtils.parseWiki(wikiStr.readLines())

		then:
		wiki.size()==17

		when:
		def map = wiki[0]

		then:
		map.id=="hv1"
		map.GMETRIC
		map.GMETRIC.size()==3
		map.GMETRIC.vmcount=="6"
		map.GMETRIC.watts=="200"
		map.GMETRIC.temp=="120"
		map.VARATTR=="HVTYPE=kvm"
		map.size()==(19-map.GMETRIC.size()+1)

		and:
		wiki[1].id=="hv2"
		wiki[2].id=="hv3"
		wiki[3].id=="moeVm"
		wiki[4].id=="moeVm2"
		wiki[5].id=="node1"
		wiki[6].id=="node2"
		wiki[7].id=="node3"
		wiki[8].id=="node4"
		wiki[9].id=="vm1"
		wiki[9].size()==14
		wiki[10].id=="vm2"
		wiki[11].id=="vm3"
		wiki[12].id=="vm4"
		wiki[13].id=="vm5"
		wiki[14].id=="vm6"
		wiki[15].id=="vm7"
		wiki[16].id=="vm8"

		and:
		wiki[16].VARIABLE
		wiki[16].VARIABLE.size()==2
		wiki[16].VARIABLE.var1=="val1"
		wiki[16].VARIABLE.containsKey("var2")
		wiki[16].VARIABLE.var2==""
	}

	def testFullClusterWikiWithSubState() {
		when:
		def wikiStr = this.class.classLoader.getResource("testfiles/msm-cluster-query-substate.wiki").text
		def wiki = NativeUtils.parseWiki(wikiStr.readLines())

		then:
		wiki.size()==15

		when:
		def map = wiki[0]

		then:
		map.id=="hv1"
		map.GMETRIC
		map.GMETRIC.size()==3
		map.GMETRIC.vmcount=="4"
		map.GMETRIC.watts=="200"
		map.GMETRIC.temp=="104"
		map.VARATTR=="HVTYPE=kvm"
		map.size()==19-map.GMETRIC.size()+1

		and:
		wiki[1].id=="hv2"
		wiki[2].id=="hv3"
		wiki[3].id=="hv4"
		wiki[4].id=="node1"
		wiki[5].id=="node2"
		wiki[6].id=="node3"
		wiki[7].id=="node4"
		wiki[8].id=="vm1"
		wiki[8].size()==14
		wiki[9].id=="vm10"
		wiki[10].id=="vm2"
		wiki[11].id=="vm3"
		wiki[12].id=="vm4"
		wiki[13].id=="vm5"
		wiki[14].id=="vm6"
	}

	def testFullWorkloadQuery() {
		when:
		def wikiStr = this.class.classLoader.getResource("testfiles/msm-workload-query.wiki").text
		def wiki = NativeUtils.parseWiki(wikiStr.readLines())

		then:
		wiki.size()==11

		and:
		wiki[0].id=="moab.15"
		wiki[0].size()==11
		wiki[1].id=="moab.17"
		wiki[2].id=="moab.18"
		wiki[3].id=="moab.20"
		wiki[4].id=="moab.21"
		wiki[5].id=="moab.22"
		wiki[6].id=="moab.23"
		wiki[7].id=="moab.24"
		wiki[8].id=="moab.25"
		wiki[9].id=="moab.26"
		wiki[10].id=="moab.27"
	}

	def "Messages are handled correctly"() {
		when:
		def wikiStr = 'job.1;STATE=Idle;MESSAGE=message1;MESSAGE="message 2";MESSAGE="message \'3\'"'
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="job.1"
		map.STATE=="Idle"
		map.MESSAGE.size()==3
		map.MESSAGE[0]=="message1"
		map.MESSAGE[1]=="message 2"
		map.MESSAGE[2]=="message '3'"
	}

	def "Spaces in features and attributes"() {
		when: "Entire value is quoted"
		def wikiStr = 'node1 FEATURE="feature1:feature2:feature 3" VARATTR="attr1:val1+attr 2+attr3:val 3"'
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==1

		when:
		def map = wiki[0]

		then:
		map.size()==3
		map.id=="node1"
		map.FEATURE=='feature1:feature2:feature 3'
		map.VARATTR=='attr1:val1+attr 2+attr3:val 3'
	}

	def "Ignore status (SC) lines"() {
		when:
		def wikiStr = "SC=0 This is some random status"
		def wiki = NativeUtils.parseWiki([wikiStr])

		then:
		wiki.size()==0

		when:
		def wikiStrs = ["SC=1 error occurred", "node1 STATE=Idle"]
		wiki = NativeUtils.parseWiki(wikiStrs)

		then:
		wiki.size()==1
		wiki[0].size()==2
		wiki[0].id=="node1"
		wiki[0].STATE=="Idle"
	}
}