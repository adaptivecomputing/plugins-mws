package com.adaptc.mws.plugins.natives;

enum VMNativeField {
	STATE("state"),
	SLAVE("slave"),
	POWER("power"),
	UPDATE_TIME("updatetime"),
	CONTAINER_NODE("containernode"),
	NETWORK_ADDRESS("netaddr"),
	PROCESSORS_CONFIGURED("cproc"),
	PROCESSORS_AVAILABLE("aproc"),
	MEMORY_CONFIGURED("cmemory"),
	MEMORY_AVAILABLE("amemory"),
	DISK_CONFIGURED("cdisk"),
	DISK_AVAILABLE("adisk"),
	SWAP_CONFIGURED("cswap"),
	SWAP_AVAILABLE("aswap"),
	GENERIC_METRICS("gmetric"),
	OS("os"),
	OS_LIST("oslist"),
	CPU_LOAD("cpuload"),
	TYPE("type"),
	VARIABLES("variable"),
	MIGRATION_DISABLED("migrationdisabled");

	private String wikiKey;

	private VMNativeField(String wikiKey) {
		this.wikiKey = wikiKey;
	}

	public static VMNativeField parseWikiAttribute(String attribute) {
		for (VMNativeField value : values()) {
			if (value.wikiKey.equalsIgnoreCase(attribute))
				return value;
		}
		return null;
	}
}
