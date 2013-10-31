package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeDateUtils
import com.adaptc.mws.plugins.natives.utils.NativeNumberUtils

import static com.adaptc.mws.plugins.PluginConstants.*

class JobNativeTranslator {
	GenericNativeTranslator genericNativeTranslator

	JobReport createReport(IPluginEventService pluginEventService, Map attrs) {
		def id = attrs.remove("id")
		JobReport job = new JobReport(id)

		attrs.each { String key, value ->
			def field = JobNativeField.parseWikiAttribute(key)
			switch (field) {
				case JobNativeField.SLAVE:
					job.slaveReport = value?.toBoolean() ?: false
					break
				case JobNativeField.ACCOUNT:
					job.account = value
					break
				case JobNativeField.ARGUMENTS:
					job.commandLineArguments = value
					break
				case JobNativeField.COMMENT:
					job.extension = value
					break
				case JobNativeField.COMPLETE_TIME:
					job.completedDate = NativeDateUtils.secondsToDate(value)
					break
				case JobNativeField.GENERIC_RESOURCES_DEDICATED:
					genericNativeTranslator.getGenericMap(value)?.each {
						job.requirements.resourcesPerTask[it.key] = getResource(NativeNumberUtils.parseInteger(it.value))
					}
					break
				case JobNativeField.END_DATE:
					job.deadlineDate = NativeDateUtils.secondsToDate(value)
					break
				case JobNativeField.ENVIRONMENT:
					genericNativeTranslator.getGenericMap(value)?.each { job.environmentVariables[it.key] = it.value }
					break
				case JobNativeField.ERROR:
					job.standardErrorFilePath = value
					break
				case JobNativeField.EXECUTABLE:
					job.commandFile = value
					break
				case JobNativeField.EXIT_CODE:
					job.completionCode = NativeNumberUtils.parseInteger(value)
					break
				case JobNativeField.FLAGS:
					value?.split(",")?.each { job.flags << JobReportFlag.parse(it) }
					break
				case JobNativeField.GROUP:
					job.group = value
					break
				case JobNativeField.HOST_LIST:
					value?.split(/\;|,/)?.each { job.nodesRequested << it }
					break
				case JobNativeField.INPUT:
					job.standardInputFilePath = value
					break
				case JobNativeField.INITIAL_WORKING_DIR:
					job.initialWorkingDirectory = value
					break
				case JobNativeField.NAME:
					job.customName = value
					break
				case JobNativeField.NODES:
					job.requirements.nodeCount = NativeNumberUtils.parseInteger(value)
					break
				case JobNativeField.OUTPUT:
					job.standardOutputFilePath = value
					break
				case JobNativeField.PARTITION_MASK:
					value?.split(":")?.each { job.partitionAccessList << it }
					break
				case JobNativeField.PRIORITY:
					job.systemPriority = NativeNumberUtils.parseLong(value)
					break
				case JobNativeField.QOS:
					job.qos = value
					break
				case JobNativeField.QUEUE_TIME:
					job.submitDate = NativeDateUtils.secondsToDate(value)
					break
				case JobNativeField.REQUIRED_ARCHITECTURE:
					job.requirements.architecture = value
					break
				case JobNativeField.REQUIRED_DISK:
					job.requirements.resourcesPerTask[RESOURCE_DISK] = getResource(NativeNumberUtils.parseInteger(value))
					break
				case JobNativeField.REQUESTED_RESERVATION:
					job.reservationRequested = value
					break
				case JobNativeField.REQUIRED_FEATURES:
					value?.split(":")?.each { job.requirements.features << it }
					break
				case JobNativeField.REQUIRED_MEMORY:
					job.requirements.resourcesPerTask[RESOURCE_MEMORY] = getResource(NativeNumberUtils.parseInteger(value))
					break
				case JobNativeField.REQUIRED_OS:
					job.requirements.image = value
					break
				case JobNativeField.REQUIRED_SWAP:
					job.requirements.resourcesPerTask[RESOURCE_SWAP] = getResource(NativeNumberUtils.parseInteger(value))
					break
				case JobNativeField.START_DATE:
					job.earliestStartDate = NativeDateUtils.secondsToDate(value)
					break
				case JobNativeField.START_TIME:
					job.startDate = NativeDateUtils.secondsToDate(value)
					break
				case JobNativeField.STATE:
					job.state = JobReportState.parse(value)
					break
				case JobNativeField.SUSPEND_TIME:
					job.durationSuspended = NativeNumberUtils.parseLong(value)
					break
				case JobNativeField.TASK_LIST:
					value?.split(",")?.each { job.requirements.nodes << it }
					break
				case JobNativeField.TASKS:
					job.requirements.taskCount = NativeNumberUtils.parseInteger(value)
					break
				case JobNativeField.TASKS_PER_NODE:
					job.requirements.tasksPerNode = NativeNumberUtils.parseInteger(value)
					break
				case JobNativeField.USER:
					job.user = value
					break
				case JobNativeField.WALL_CLOCK_LIMIT:
					job.duration = NativeNumberUtils.parseLong(value)
					break
				default:
					def message = message(code: "jobNativeTranslator.invalid.attribute", args: [job.name, key, value])
					log.warn(message)
					pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
							message,
							new IPluginEventService.AssociatedObject(id: job.name, type: "Job"), null)
					break
			}
		}

		return job
	}

	/**
	 * Converts a job API version 2+ into a map of wiki attributes (key=value).
	 * @param job The job definition following the MWS API version 2+
	 * @param spoolFile The spooled file containing the submission string (executable file)
	 * @param submissionFlags Flags used in the submission
	 * @return A map of wiki attributes
	 */
	Map<String, String> convertJobToWiki(Map<String, Object> job, File spoolFile, String submissionFlags) {
		if (!job)
			return [:]
		// Used for job submission interface, only supports the following attributes:
		// 	UNAME=<userName> GNAME=<groupName> WCLIMIT=<wallClock> TASKS=<tasksNumber> NAME=<jobId> IWD=<initialWorkingDirectory> EXEC=<executable>
		return [
				(JobNativeField.USER.wikiKey.toUpperCase()):job.credentials?.user,
				(JobNativeField.GROUP.wikiKey.toUpperCase()):job.credentials?.group,
				(JobNativeField.WALL_CLOCK_LIMIT.wikiKey.toUpperCase()):job.duration,
				(JobNativeField.TASKS.wikiKey.toUpperCase()):job.requirements?.getAt(0)?.taskCount,
				(JobNativeField.NAME.wikiKey.toUpperCase()):job.name,
				(JobNativeField.INITIAL_WORKING_DIR.wikiKey.toUpperCase()):job.initialWorkingDirectory,
				(JobNativeField.EXECUTABLE.wikiKey.toUpperCase()):spoolFile?.absolutePath,
				RMFLAGS:submissionFlags,	// Not documented except in Moab code
		]
	}

	private ReportResource getResource(Integer value) {
		// Set total and available to make sure that it is the same no matter what
		ReportResource resource = new ReportResource()
		resource.total = value
		resource.available = value
		return resource
	}
}

enum JobNativeField {
	STATE("state"),
	SLAVE("slave"),
	ACCOUNT("account"),
	ARGUMENTS("args"),
	COMMENT("comment"),
	COMPLETE_TIME("completetime"),
	GENERIC_RESOURCES_DEDICATED("dgres"),
	END_DATE("enddate"),
	ENVIRONMENT("env"),
	ERROR("error"),
	EXECUTABLE("exec"),
	EXIT_CODE("exitcode"),
	FLAGS("flags"),
	GROUP("gname"),
	HOST_LIST("hostlist"),
	INPUT("input"),
	INITIAL_WORKING_DIR("iwd"),
	NAME("name"),
	NODES("nodes"),
	OUTPUT("output"),
	PARTITION_MASK("partitionmask"),
	PRIORITY("priority"),
	QOS("qos"),
	QUEUE_TIME("queuetime"),
	REQUIRED_ARCHITECTURE("rarch"),
	REQUIRED_DISK("rdisk"),
	REQUESTED_RESERVATION("reqrsv"),
	REQUIRED_FEATURES("rfeatures"),
	REQUIRED_MEMORY("rmem"),
	REQUIRED_OS("ropsys"),
	REQUIRED_SWAP("rswap"),
	START_DATE("startdate"),
	START_TIME("starttime"),
	SUSPEND_TIME("suspendtime"),
	TASK_LIST("tasklist"),
	TASKS("tasks"),
	TASKS_PER_NODE("taskpernode"),
	USER("uname"),
	WALL_CLOCK_LIMIT("wclimit")

	String wikiKey

	private JobNativeField(String wikiKey) {
		this.wikiKey = wikiKey
	}

	public static JobNativeField parseWikiAttribute(String attribute) {
		if (!attribute)
			return null
		return values().find { it.wikiKey.equalsIgnoreCase(attribute) }
	}
}