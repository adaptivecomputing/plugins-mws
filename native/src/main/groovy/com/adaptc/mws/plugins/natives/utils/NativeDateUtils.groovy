package com.adaptc.mws.plugins.natives.utils

class NativeDateUtils {
	// From include/moab.h
	public static final long MMAX_TIME = 2140000000

	/**
	 * Converts a String (representing seconds since the Epoch) to
	 * a Date. If the String is "infinity" (case-insensitive), or if
	 * the String represents a number greater than MMAX_TIME, returns
	 * the Date representation of MMAX_TIME (2037-10-24 12:26:40 UTC).
	 */
	static Date secondsToDate(String valueString) {
		if (valueString ==~ /(?i)^.*infinity.*$/)
			return new Date(MMAX_TIME * 1000)
		Long seconds = NativeNumberUtils.parseLong(valueString)
		if (!seconds || seconds <= 0)
			return null
		if (seconds > MMAX_TIME)
			seconds = MMAX_TIME
		return new Date(seconds * 1000)
	}
}
