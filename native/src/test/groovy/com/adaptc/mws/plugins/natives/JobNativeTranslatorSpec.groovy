package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeUtils

import static com.adaptc.mws.plugins.PluginConstants.*

import com.adaptc.mws.plugins.testing.*

import spock.lang.*

/**
 * 
 * @author bsaville
 */
@TestFor(JobNativeTranslator)
@Unroll
class JobNativeTranslatorSpec extends Specification {
    def "Wiki translated to Job domain"() {
        given:
        GenericNativeTranslator genericNativeTranslator = Mock()
        translator.genericNativeTranslator = genericNativeTranslator

        when:
        def wiki = "moab.1 StaTE=" + JobReportState.IDLE +
				";ACcOUNT=account.1" +
                ";ARgS=--version"+
				";COmMENT=comments"+
				";COmPLETETIME=5"+
				";ENdDATE=6" +
                ";ERrOR=/var/spool/moab.1.err"+
				";EXeC=/var/spool/moab.1.err"+
				";EXiTCODE=1" +
                ";FLaGS=" + JobReportFlag.SHAREDMEM +	"," + JobReportFlag.COALLOC +
                ";GNaME=group.1"+
				";HOsTLIST=n01,n02"+
				";InPUT=/var/spool/moab.1.in" +
                ";IwD=/home/bob/"+
				";NaME=\"bob's job\""+
				";NOdES=2"+
				";OUtPUT=/var/spool/moab.1.out" +
                ";PRiORITY=1000"+
				";QoS=qos.1"+
				";QuEUETIME=1"+
				";RaRCH=arch1"+
				";RdISK=100" +
                ";ReQRSV=rsv.1"+
				";RmEM=2048"+
				";RoPSYS=win2k8"+
				";RsWAP=100" +
                ";StARTDATE=2"+
				";StARTTIME=3" +
				";RfEATURES=FEAT3:FEAT4" +
                ";DgRES=dgres"+
				";EnV=env" +
                ";SuSPENDTIME=100"+
				";TaSKLIST=n03,n04"+
				";TaSKS=2"+
				";TaSKPERNODE=2" +
                ";UnAME=sam"+
				";WcLIMIT=2000"
        JobReport job = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

        then:
        1 * genericNativeTranslator.getGenericMap("dgres") >> [RES1:1,RES2:2]
        1 * genericNativeTranslator.getGenericMap("env") >> [env1:"val1",env2:"val2"]
        0 * _._

        and:
        job.name=="moab.1"
        job.state == JobReportState.IDLE
        job.account == "account.1"
        job.commandLineArguments == "--version"
        job.extension == "comments"
        job.completedDate == new Date(5000)
        job.deadlineDate == new Date(6000)
        job.standardErrorFilePath == "/var/spool/moab.1.err"
        job.commandFile == "/var/spool/moab.1.err"
        job.completionCode == 1 //TODO - bug when exit code is 0?
        job.flags?.size()==2
        job.flags[0]==JobReportFlag.SHAREDMEM
        job.flags[1]==JobReportFlag.COALLOC
        job.group == "group.1"
        job.nodesRequested?.size()==2
        job.nodesRequested[0]=="n01"
        job.nodesRequested[1]=="n02"
        job.standardInputFilePath == "/var/spool/moab.1.in"
        job.initialWorkingDirectory == "/home/bob/"
        job.customName == "bob's job"
        job.systemPriority == 1000
        job.qos == "qos.1"
        job.submitDate == new Date(1000);
        job.standardOutputFilePath == "/var/spool/moab.1.out"
        job.reservationRequested == "rsv.1"
        job.earliestStartDate == new Date(2000)
        job.startDate == new Date(3000)
        job.durationSuspended == 100
        job.user == "sam"
        job.duration == 2000
        job.environmentVariables.size()==2
        job.environmentVariables.env1=="val1"
        job.environmentVariables.env2=="val2"

        and:
        job.requirements?.size()==1

        then:
		job.requirements.operatingSystem == "win2k8"
		job.requirements.nodes?.size()==2
		job.requirements.nodes[0]=="n03"
		job.requirements.nodes[1]=="n04"
        job.requirements.nodeCount == 2
        job.requirements.architecture == "arch1"
        job.requirements.taskCount == 2
        job.requirements.tasksPerNode == 2
		job.requirements.resourcesPerTask.size()==5
		job.requirements.resourcesPerTask[RESOURCE_DISK].total==100
		job.requirements.resourcesPerTask[RESOURCE_DISK].available==100
		job.requirements.resourcesPerTask[RESOURCE_SWAP].total==100
		job.requirements.resourcesPerTask[RESOURCE_SWAP].available==100
		job.requirements.resourcesPerTask[RESOURCE_MEMORY].total==2048
		job.requirements.resourcesPerTask[RESOURCE_MEMORY].available==2048
		job.requirements.resourcesPerTask.RES1.total==1
		job.requirements.resourcesPerTask.RES1.available==1
		job.requirements.resourcesPerTask.RES2.total==2
		job.requirements.resourcesPerTask.RES2.available==2
        job.requirements.features.size()==2
        job.requirements.features[0]=="FEAT3"
        job.requirements.features[1]=="FEAT4"
    }

	def "Notifications for invalid attributes"() {
		given:
		IPluginEventService pluginEventService = Mock()

		when:
		def object = translator.createReport(pluginEventService, [id:"id1", bogus1:"value", bogus2:"value2"])

		then:
		object.name=="id1"

		and:
		2 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"jobNativeTranslator.invalid.attribute", {it.type=="Job" && it.id=="id1" }, null)
		0 * _._
	}

	def "Slave flag"() {
		when:
		def wiki = "job1 "+slaveWiki
		JobReport job = translator.createReport(null, NativeUtils.parseWiki([wiki])[0])

		then:
		job?.name=="job1"
		job.slaveReport==result

		where:
		slaveWiki			|| result
		""					|| false
		"SLAVE=true"		|| true
		"SLAVE=false"		|| false
		"SLAVE=1"			|| true
		"SLAVE=0"			|| false
		"SLAVE="			|| false
	}

	def "Convert job to wiki"() {
		given:
		def spoolFile = new File("tmp")

		when: "No properties defined"
		def result = translator.convertJobToWiki(null, null, null)

		then:
		result.size()==0

		when: "Most properties not defined"
		result = translator.convertJobToWiki([
		        name:"job1",
		], null, null)

		then:
		result.size()==8
		result.EXEC==null
		result.RMFLAGS==null
		result.NAME=="job1"

		when: "Empty requirements and credentials"
		result = translator.convertJobToWiki([
				name:"job1",
				credentials:[:],
				requirements:[],
				commandFile:"/tmp/script.sh"
		], null, "")

		then:
		result.size()==8
		result.EXEC=="/tmp/script.sh"
		result.TASKS==null
		result.UNAME==null
		result.GNAME==null
		result.RMFLAGS==""

		when: "All fields set"
		result = translator.convertJobToWiki([
				name:"job1",
				credentials:[
						user:"myuser",
						group:"mygroup",
				],
				requirements:[
						[taskCount:4],
				],
				duration:1234,
				initialWorkingDirectory:"/root/",
		], spoolFile, "flag1 flag2")

		then:
		result.size()==8
		result.NAME=="job1"
		result.UNAME=="myuser"
		result.GNAME=="mygroup"
		result.TASKS==4
		result.WCLIMIT==1234
		result.IWD=="/root/"
		result.EXEC==spoolFile.absolutePath
		result.RMFLAGS=="flag1 flag2"
	}
}
