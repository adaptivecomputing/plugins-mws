package com.adaptc.mws.plugins.natives.utils;

/**
 * @author bsaville
 */
public class NativeUtils {
	private static final QUOTE_TRIM_PATTERN = /^"(.*)"$/

	/**
	 * Returns a list of maps containing the wiki parameters, with the id stored as id
	 * and all other parameters as key/value pairs.<br><br>
	 * Also handles hashes for newline delimiters
	 * @param lines Native wiki lines delimited by semi-colons and equals (; and =)
	 * @return
	 */
	public static List<Map> parseWiki(lines) {
		return filterLines(lines).collect { String line ->
			// Replace escaped characters
			line = line.replaceAll(/\\;/, '{SEMICOLON}')

			// Split on spaces, replace escaped chars, and split on =
			def map = [:]
			List attrs = (line =~ /(?:^|[ \t]+|\;)([^ \t;"']+(?:".*?"|'.*?'|))+/).collect { it[1] }
			map.id = attrs.remove(0)
			def lastKey = ""
			attrs.each { String attr ->
				def pair = attr.replaceAll(/\{SEMICOLON\}/, ';')

				// Check for generic metrics
				if (attr.size() >= 7 && attr.substring(0, 7).equalsIgnoreCase("gmetric")) {
					def match = pair =~ /(?i)gmetric\[(.*?)\]=(.*)/
					def key = match[0][1]
					def val = match[0][2]
					if (!map.GMETRIC)
						map.GMETRIC = [:]
					map.GMETRIC[key] = val
					return
				}

				// Check for variables
				if (attr.size() >= 8 && attr.substring(0, 8).equalsIgnoreCase("variable")) {
					def match = pair =~ /(?i)variable\=(.*?)(?:[=:](.*)|)$/
					def key = match[0][1]
					def val = match[0][2]
					if (!map.VARIABLE)
						map.VARIABLE = [:]
					// Use default of empty string as null will not be persisted
					//	correctly for some reason (GOPlugin caveat?)
					map.VARIABLE[key] = val ?: ""
					return
				}

				// Everything else
				def entry = pair.split('=', 2)	// Not sure why the limit is 2 here, really should be 1, but only succeeds with 2! (see unit tests)
				//println entry
				// Parse substate correctly (uses a semi-colon)
				if (lastKey?.equalsIgnoreCase("state") && entry.size()==1) {	// STATE=state:substate, append to last one
					map[lastKey] += ":${pair}"
					return
				}
				if (entry.size()==1) {
					log.warn("Invalid Wiki detected for attribute ${pair} on line '${line}'")
					return
				}

				// Handle messages correctly, adding to a list and removing quotes around them
				if (entry[0]?.equalsIgnoreCase("message")) {
					if (!map.MESSAGE)
						map.MESSAGE = []
					map.MESSAGE << trimQuotes(entry[1])
					return
				}

				lastKey = entry[0]
				map.put(entry[0], trimQuotes(entry[1]))
			}
			map
		}
	}

	public static List<String> filterLines(lines) {
		return lines.inject([]) { List list, String line ->
			// Check for # for separation of wiki objects and check for escaped
			if (!line || line.trim().isEmpty() || line =~ /^#/ || line =~ /(?i)^SC=/) {
				// do nothing with empty commented (starting with #) or status (SC=0) lines
			} else if (line.contains("#"))	// Catch hashed lines
				list.addAll(line.replaceAll(/\\#/, '{HASH}').split("#").collect { it.replaceAll(/\{HASH\}/, "#") })
			else
				list << line
			return list
		}
	}

	private static String trimQuotes(String str) {
		if (!str)
			return str
		return str.replaceAll(QUOTE_TRIM_PATTERN, "\$1")
	}
}
