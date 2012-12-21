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
}
