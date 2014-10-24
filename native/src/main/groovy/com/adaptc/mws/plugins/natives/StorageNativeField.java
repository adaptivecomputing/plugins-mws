package com.adaptc.mws.plugins.natives;

enum StorageNativeField {
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
	FEATURES("feature"),
	GENERIC_METRICS("gmetric"),
	MESSAGES("message"),
	NETWORK_ADDRESS("netaddr"),
	PARTITION("partition"),
	POWER("power"),
	SPEED("speed"),
	TYPE("type"),
	VARIABLES("variable"),
	MIGRATION_DISABLED("migrationdisabled"),
	ATTRIBUTES("varattr");

	private String wikiKey;

	public String getWikiKeyDisplay() {
		return wikiKey.toUpperCase();
	}

	private StorageNativeField(String wikiKey) {
		this.wikiKey = wikiKey;
	}

	public static StorageNativeField parseWikiAttribute(String attribute) {
		for (StorageNativeField value : values()) {
			if (value.wikiKey.equalsIgnoreCase(attribute))
				return value;
		}
		return null;
	}
}