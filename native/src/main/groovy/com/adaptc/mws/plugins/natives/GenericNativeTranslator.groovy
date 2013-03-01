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

	public Map getGenericMapWithDisplayName(String wiki, String outerDelimiter="\\+", String innerDelimiter=":|=") {
		return wiki?.split(outerDelimiter)?.inject([:]) { Map map, String attrWiki ->
			String value
			String displayName
			def entry = attrWiki.split(innerDelimiter)
			String key = entry[0]
			if (entry.size() > 1)
				value = entry[1]
			if (entry.size() > 2)
				displayName = attrWiki.substring(attrWiki.lastIndexOf(entry[2]))

			map[key] = [value:value, displayName: displayName]

			return map
		}
	}
}
