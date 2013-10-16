package com.adaptc.mws.plugins.reports

/**
 * 
 * @author jpratt
 */
public enum UtilizationLevel {
	HIGH,
	MEDIUM,
	LOW

	public static UtilizationLevel parse(String utilizationLevel) {
		return values().find { it.name().equalsIgnoreCase(utilizationLevel) }
	}
}

