package com.adaptc.mws.plugins.natives.utils

import java.text.SimpleDateFormat

class NativeDateUtils {

	// From include/moab.h
	public static final long MMAX_TIME = 2140000000

	// From grails-app/i18n/messages.properties
	private static final String standardDateFormatString =
		"yyyy-MM-dd HH:mm:ss z"

	static final SimpleDateFormat standardDateFormat =
		new SimpleDateFormat(standardDateFormatString)

	static Date parseSecondsToDate(String valueString) {
		if (valueString ==~ /(?i)^.*infinity.*$/)
			return new Date(MMAX_TIME * 1000)
		Long seconds = NativeNumberUtils.parseLong(valueString)
		if (!seconds || seconds <= 0)
			return null
		if (seconds > MMAX_TIME)
			seconds = MMAX_TIME
		return new Date(seconds * 1000)
	}

	static Long parseDateStringToSeconds(String dateString) {
		try {
			return standardDateFormat.parse(dateString).time / 1000
		} catch (Exception e) {
			return null
		}
	}
	
	static Date parseDateString(String dateString) {
		try {
			return standardDateFormat.parse(dateString)
		} catch(Exception e) {
			return null
		}
	}
	
	static Long parseDateToSeconds(Date date) {
		return date.time/1000
	}
}
