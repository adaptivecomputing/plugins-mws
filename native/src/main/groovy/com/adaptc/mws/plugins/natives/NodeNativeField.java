package com.adaptc.mws.plugins.natives;

enum NodeNativeField {
	STATE("state"),
	SLAVE("slave"),
	UPDATE_TIME("updatetime"),
	PROCESSORS_CONFIGURED("cproc"),
	PROCESSORS_AVAILABLE("aproc"),
	MEMORY_CONFIGURED("cmemory"),
	MEMORY_AVAILABLE("amemory"),
	DISK_CONFIGURED("cdisk"),
	DISK_AVAILABLE("adisk"),
	SWAP_CONFIGURED("cswap"),
	SWAP_AVAILABLE("aswap"),
	ARCHITECTURE("arch"),
	GENERIC_RESOURCES_CONFIGURED("cres"),
	GENERIC_RESOURCES_AVAILABLE("ares"),
	CPU_LOAD("cpuload"),
	HYPERVISOR_TYPE("hvtype"),
	FEATURES("feature"),
	GENERIC_METRICS("gmetric"),
	MESSAGES("message"),
	OS("os"),
	OS_LIST("oslist"),
	NETWORK_ADDRESS("netaddr"),
	PARTITION("partition"),
	POWER("power"),
	SPEED("speed"),
	TYPE("type"),
	VARIABLES("variable"),
	ATTRIBUTES("varattr"),
	MIGRATION_DISABLED("migrationdisabled"),
	VM_OS_LIST("vmoslist"),
	TIME_TO_LIVE("timetolive"),
	ACL("acl"),
	REQUESTID("requestid");

	private String wikiKey;

	public String getWikiKeyDisplay() {
		return wikiKey.toUpperCase();
	}

	private NodeNativeField(String wikiKey) {
		this.wikiKey = wikiKey;
	}

	public static NodeNativeField parseWikiAttribute(String attribute) {
		if (attribute==null || attribute.isEmpty())
			return null;
		for (NodeNativeField value : values()) {
			if (value.wikiKey.equalsIgnoreCase(attribute))
				return value;
		}
		return null;
	}
}
