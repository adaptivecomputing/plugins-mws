package com.adaptc.mws.plugins.natives

class GenericNativeTranslator {
	public Map getGenericMap(String wiki, String outerDelimiter=",", String innerDelimiter=":") {
		return wiki?.split(outerDelimiter)?.inject([:]) { Map map, String pair ->
			def entry = pair.split(innerDelimiter)
			if (entry.size()==1)
				map[entry[0]] = null
			else
				map[entry[0]] = entry[1]
			return map
		}
	}

	public Map getGenericMapWithDisplayValue(String wiki, String outerDelimiter="\\+", String innerDelimiter=":|=") {
		return wiki?.split(outerDelimiter)?.inject([:]) { Map map, String attrWiki ->
			String value
			String displayValue
			def entry = attrWiki.split(innerDelimiter, 3)
			String key = entry[0]
			if (entry.size() > 1)
				value = entry[1]
			if (entry.size() > 2)
				displayValue = entry[2]

			map[key] = [value:value, displayValue: displayValue]

			return map
		}
	}
}
