package com.adaptc.mws.plugins.natives.utils

class NativeNumberUtils {
    static Double parseDouble(Double value) {
        return value
    }

	static Double parseDouble(String valueString, Double defaultVal=null) {
		if (!valueString)
			return defaultVal
		try {
			return valueString.toDouble()
		} catch (NumberFormatException nfe) {
			return defaultVal
		}
	}

    static Integer parseInteger(Integer value) {
        return value
    }

    static Integer parseInteger(String valueString, Integer defaultVal=null) {
        if (!valueString)
            return defaultVal
        try {
            return valueString.toInteger()
        } catch (NumberFormatException nfe) {
            try {
                return getSafeNumeric(valueString, defaultVal.toString()).toInteger()
            }
            catch (NumberFormatException nfeInner) {
                // valueString is too large or too small to store in an int.
                if (valueString.startsWith("-")) {
                    return Integer.MIN_VALUE
                } else {
                    return Integer.MAX_VALUE
                }
            }
        }
    }

    static Long parseLong(Long value) {
        return value
    }

	static Long parseLong(String valueString, Long defaultVal=null) {
		if (!valueString)
			return defaultVal
		if (valueString ==~ /(?i)^.*infinity.*$/)
			return NativeDateUtils.MMAX_TIME
		try {
			return valueString.toLong()
		} catch (NumberFormatException nfe) {
			try {
				return getSafeNumeric(valueString, defaultVal.toString()).toLong()
			}
			catch (NumberFormatException nfeInner) {
				// valueString is too large or too small to store in a long.
				if (valueString.startsWith("-")) {
					return Long.MIN_VALUE
				} else {
					return Long.MAX_VALUE
				}
			}
		}
	}

	private static String getSafeNumeric(String valueString, String defaultVal = null) {
		if (!valueString)
			return defaultVal
		try {
			return Math.round(valueString.toDouble()).toString()
		} catch (NumberFormatException nfs) {
			def isNegative = valueString.startsWith("-")
			valueString = valueString.replaceAll("[^\\d]+", "")
			if (!valueString)
				return defaultVal
			// At this point, valueString contains one or more decimal
			// digits and nothing else.
			return isNegative ? "-${valueString}" : valueString
		}
	}
}
