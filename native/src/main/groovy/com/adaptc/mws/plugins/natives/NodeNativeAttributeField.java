package com.adaptc.mws.plugins.natives;

enum NodeNativeAttributeField {
	; // All prior enums have been removed

	private String wikiKey;

	public String getWikiKeyDisplay() {
		return wikiKey.toUpperCase();
	}

	private NodeNativeAttributeField(String wikiKey) {
		this.wikiKey = wikiKey;
	}

	public static NodeNativeAttributeField parseWikiAttribute(String attribute) {
		if (attribute==null || attribute.isEmpty())
			return null;
		for (NodeNativeAttributeField value : values()) {
			if (value.wikiKey.equalsIgnoreCase(attribute))
				return value;
		}
		return null;
	}
}
