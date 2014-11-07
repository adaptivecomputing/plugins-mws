package com.adaptc.mws.plugins.natives.utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat

class NativeDateUtils {

	// From include/moab.h
	public static final long MMAX_TIME = 2140000000

	/**
	 * Based on grails-app/i18n/messages.properties:default.date.format.
	 * Note that we use ZZZ instead of z. The reason for the difference is that
	 * Joda Time does not parse time zone names (but it does parse time zone ids).
	 * References:
	 *   http://joda-time.sourceforge.net/timezones.html
	 *   http://joda-time.sourceforge.net/apidocs/org/joda/time/format/DateTimeFormat.html
	 */
	private static final String mwsDateFormatString =
			"yyyy-MM-dd HH:mm:ss ZZZ"

	/**
	 * A DateTimeFormatter for the pattern "yyyy-MM-dd HH:mm:ss ZZZ"
	 * <br>
	 * Example: 2013-05-02 15:32:02 UTC
	 */
	static final DateTimeFormatter mwsDateTimeFormatter =
			DateTimeFormat.forPattern(mwsDateFormatString).withZoneUTC()

	/**
	 * A DateTimeFormatter for the ISO 8601 pattern "yyyy-mm-dd'T'HH:MM:SS.SSSZZ"
	 * <br>
	 * Example: 2013-05-02T16:02:55.781-06:00
	 */
	static final DateTimeFormatter isoDateTimeFormatter =
			ISODateTimeFormat.dateTime().withZoneUTC()

	/**
	 * A DateTimeFormatter for the ISO 8601 pattern "yyyy-mm-dd'T'HH:MM:SSZZ"
	 * <br>
	 * Example: 2013-05-02T16:02:55-06:00
	 */
	static final DateTimeFormatter isoDateTimeNoMillisFormatter =
			ISODateTimeFormat.dateTimeNoMillis().withZoneUTC()

	/**
	 * Converts a String to a Date using mwsDateTimeFormatter.
	 * If the String is null or empty, returns null.
	 * @throws IllegalArgumentException if the text to parse is invalid
	 */
	static Date dateStringToDate(String dateString) {
		return dateString ? mwsDateTimeFormatter.parseDateTime(dateString).toDate() : null
	}

	/**
	 * Converts a String to a Date using isoDateTimeFormatter.
	 * If the String is null or empty, returns null.
	 * @throws IllegalArgumentException if the text to parse is invalid
	 */
	static Date isoDateStringToDate(String dateString) {
		return dateString ? isoDateTimeFormatter.parseDateTime(dateString).toDate() : null
	}
	/**
	 * Converts a String to a Date using isoDateTimeNoMillisFormatter.
	 * If the String is null or empty, returns null.
	 * @throws IllegalArgumentException if the text to parse is invalid
	 */
	static Date isoDateStringNoMillisToDate(String dateString) {
		return dateString ? isoDateTimeNoMillisFormatter.parseDateTime(dateString).toDate() : null
	}

	/**
	 * Converts a String to a Long (representing seconds since the Epoch)
	 * using mwsDateTimeFormatter.
	 * If the String is null or empty, returns null.
	 * @throws IllegalArgumentException if the text to parse is invalid
	 */
	static Long dateStringToSeconds(String dateString) {
		return dateString ? dateStringToDate(dateString).time / 1000 : null
	}

	/**
	 * Converts a Date to a String using mwsDateTimeFormatter.
	 * If the Date is null, returns null.
	 * @throws IllegalArgumentException if the text to parse is invalid
	 */
	static String dateToDateString(Date date) {
		return date ? mwsDateTimeFormatter.print(new DateTime(date)) : null
	}

	/**
	 * Converts a Date to a String using mwsDateTimeFormatter.
	 * If the Date is null, returns null.
	 * @throws IllegalArgumentException if the text to parse is invalid
	 */
	static String dateToIsoDateNoMillisString(Date date) {
		return date ? isoDateTimeNoMillisFormatter.print(new DateTime(date)) : null
	}


	/**
	 * Converts a String (representing seconds since the Epoch) to
	 * a Date. If the String is "infinity" (case-insensitive), or if
	 * the String represents a number greater than MMAX_TIME, returns
	 * the Date representation of MMAX_TIME (2037-10-24 12:26:40 UTC).
	 */
	static Date secondsToDate(String valueString) {
		if (valueString ==~ /(?i)^.*infinity.*$/)
			return new Date(MMAX_TIME * 1000)
		long seconds = NativeNumberUtils.parseLong(valueString)
		if (seconds <= 0)
			return null
		if (seconds > MMAX_TIME)
			seconds = MMAX_TIME
		return new Date(seconds * 1000)
	}

	/**
	 * Converts a String with format [[[DD:]HH:]MM:]SS to seconds
	 * @throws NumberFormatException if DD, HH, MM, or SS can not be parsed into a Long
	 */
	static Long moabIntervalToSeconds(String moabInterval) throws NumberFormatException {
		if(!moabInterval)
			return 0
		moabInterval = moabInterval.replaceAll("\\s*","")
		moabInterval = "0$moabInterval".replaceAll(":",":0")
		List groups = moabInterval.tokenize(":").collect{ it.toLong()}
		Long daySeconds = 0
		Long hourSeconds = 0
		Long minuteSeconds = 0
		Long seconds = 0
		try {
			daySeconds = groups.getAt(-4) * 86400
		} catch (IndexOutOfBoundsException ignore) {}
		try {
			hourSeconds = groups.getAt(-3) * 3600
		} catch (IndexOutOfBoundsException ignore) {}
		try {
			minuteSeconds = groups.getAt(-2) * 60
		} catch (IndexOutOfBoundsException ignore) {}
		try {
			seconds = groups.getAt(-1)
		} catch (IndexOutOfBoundsException ignore) {}

		return daySeconds + hourSeconds + minuteSeconds + seconds
	}

}
