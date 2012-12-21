package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.testing.TestFor
import spock.lang.Specification

@TestFor(GenericNativeTranslator)
class GenericNativeTranslatorSpec extends Specification {
	def "Get generic map"() {
		when: "No wiki"
		def result = translator.getGenericMap(null)
		
		then:
		!result
		
		when: "Default delimiters"
		result = translator.getGenericMap("attr:value,attr2:value2")
		
		then:
		result==[attr:"value", attr2:"value2"]
		
		when: "Custom delimiters"
		result = translator.getGenericMap("attr=value;attr2=value2", ";", "=")
		
		then:
		result==[attr:"value", attr2:"value2"]
	}

	def "Get attributes map with multiple delimiters and null values"() {
		when:
		def result = translator.getGenericMap("attr1:val1+attr2+attr3=val3", "\\+", ":|=")

		then:
		result==[attr1:"val1",attr2:null,attr3:"val3"]
	}
}