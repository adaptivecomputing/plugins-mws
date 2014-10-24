package com.adaptc.mws.plugins.natives;

public enum JobNativeField {
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
	WALL_CLOCK_LIMIT("wclimit");

	private JobNativeField(String wikiKey) {
		this.wikiKey = wikiKey;
	}

	public static JobNativeField parseWikiAttribute(final String attribute) {
		if (attribute==null || attribute.isEmpty())
			return null;
		for (JobNativeField value : values()) {
			if (value.wikiKey.equalsIgnoreCase(attribute))
				return value;
		}
		return null;
	}

	public String getWikiKey() {
		return wikiKey;
	}

	private String wikiKey;
}
