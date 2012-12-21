package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class JobNativeTranslator {
	def genericNativeTranslator

	JobReport update(Map attrs, boolean submit=false) {
		JobReport job = new JobReport(attrs.id)
		
		job.account = attrs.ACCOUNT
		job.commandLineArguments = attrs.ARGS
		job.extension = attrs.COMMENT
		job.completedDate = NativeDateUtils.parseSecondsToDate(attrs.COMPLETETIME)
        genericNativeTranslator.getGenericMap(attrs.DGRES)?.each {
            job.requirements.resourcesPerTask[it.key] = getResource(NativeNumberUtils.parseInteger(it.value))
        }
		job.deadlineDate = NativeDateUtils.parseSecondsToDate(attrs.ENDDATE)
		genericNativeTranslator.getGenericMap(attrs.ENV)?.each { job.environmentVariables[it.key] = it.value }
		job.standardErrorFilePath = attrs.ERROR
		job.commandFile = attrs.EXEC
		job.completionCode = NativeNumberUtils.parseInteger(attrs.EXITCODE)
		attrs.FLAGS?.split(",")?.each { job.flags << JobReportFlag.parse(it) }
		job.group = attrs.GNAME
		attrs.HOSTLIST?.split(/\;|,/)?.each { job.nodesRequested << it }
		job.standardInputFilePath = attrs.INPUT
		job.initialWorkingDirectory = attrs.IWD
		job.customName = attrs.NAME
		job.requirements.nodeCount = NativeNumberUtils.parseInteger(attrs.NODES)
		job.standardOutputFilePath = attrs.OUTPUT
		attrs.PARTITIONMASK?.split(":")?.each { job.partitionAccessList << it }
		job.systemPriority = NativeNumberUtils.parseLong(attrs.PRIORITY)
		job.qos = attrs.QOS
		if (!attrs.QUEUETIME && submit)
			job.submitDate = new Date()
		else
			job.submitDate = NativeDateUtils.parseSecondsToDate(attrs.QUEUETIME)
		job.requirements.architecture = attrs.RARCH
		job.requirements.resourcesPerTask[RESOURCE_DISK] = getResource(NativeNumberUtils.parseInteger(attrs.RDISK))
		job.reservationRequested = attrs.REQRSV
        attrs.RFEATURES?.split(":")?.each { job.requirements.features << it }
		job.requirements.resourcesPerTask[RESOURCE_MEMORY] = getResource(NativeNumberUtils.parseInteger(attrs.RMEM))
		job.image = attrs.ROPSYS
		job.requirements.resourcesPerTask[RESOURCE_SWAP] = getResource(NativeNumberUtils.parseInteger(attrs.RSWAP))
		job.earliestStartDate = NativeDateUtils.parseSecondsToDate(attrs.STARTDATE)
		job.startDate = NativeDateUtils.parseSecondsToDate(attrs.STARTTIME)
		job.state = JobReportState.parse(attrs.STATE)
		job.durationSuspended = NativeNumberUtils.parseLong(attrs.SUSPENDTIME)
		attrs.TASKLIST?.split(",")?.each { job.requirements.nodes << it }
		job.requirements.taskCount = NativeNumberUtils.parseInteger(attrs.TASKS)
		job.requirements.tasksPerNode = NativeNumberUtils.parseInteger(attrs.TASKPERNODE)
		job.user = attrs.UNAME
		job.duration = NativeNumberUtils.parseLong(attrs.WCLIMIT)
		
		job
	}

	private ReportResource getResource(Integer value) {
		// Set total and available to make sure that it is the same no matter what
		ReportResource resource = new ReportResource()
		resource.total = value
		resource.available = value
		return resource
	}
	
	//TODO Check the name/id interaction when creating a job - is the id set the actual ID that moab uses?
	JobReport create(Map attrs) {
		//TASKS:1, WCLIMIT:8639999, IWD:/opt/moab/tools, UNAME:adaptive, NAME:moab.5, EXEC:/opt/moab/spool/moab.job.YWnZTy, GNAME:adaptive,
		attrs.id = attrs.remove("NAME")
		return update(attrs, true)
	}
}