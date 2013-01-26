package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import static com.adaptc.mws.plugins.PluginConstants.*

import com.adaptc.mws.plugins.testing.*

import spock.lang.Specification

/**
 * 
 * @author bsaville
 */
@TestFor(JobNativeTranslator)
@TestMixin(PluginUnitTestMixin)
class JobNativeTranslatorSpec extends Specification {
    def "Wiki translated to Job domain"() {
        given:
        GenericNativeTranslator genericNativeTranslator = Mock()
        translator.genericNativeTranslator = genericNativeTranslator
        def plugin = mockPlugin(NativePlugin)

        when:
        def wiki = "moab.1 STATE=" + JobReportState.IDLE + ";ACCOUNT=account.1;" +
                "ARGS=--version;COMMENT=comments;COMPLETETIME=5;ENDDATE=6;" +
                "ERROR=/var/spool/moab.1.err;EXEC=" +
                "/var/spool/moab.1.err;EXITCODE=1" +
                ";FLAGS=" + JobReportFlag.SHAREDMEM +	"," + JobReportFlag.COALLOC +
                ";GNAME=group.1;HOSTLIST=n01,n02;INPUT=/var/spool/moab.1.in;" +
                "IWD=/home/bob/;NAME=\"bob's job\";NODES=2;OUTPUT=" +
                "/var/spool/moab.1.out;" +
                "PRIORITY=1000;QOS=qos.1;QUEUETIME=1;RARCH=arch1;RDISK=100;" +
                "REQRSV=rsv.1;RMEM=2048;RNETWORK=eth0;ROPSYS=win2k8;RSWAP=100;" +
                "STARTDATE=2;STARTTIME=3;" +
                "PREF=FEAT1:FEAT2;RFEATURES=FEAT3:FEAT4;" +
                "DGRES=dgres;ENV=env;" +
                "SUSPENDTIME=100;TASKLIST=n03,n04;TASKS=2;TASKPERNODE=2;" +
                "UNAME=sam;WCLIMIT=2000"
        JobReport job = translator.createReport(plugin.parseWiki([wiki]))

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
        job.image == "win2k8"
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
}
