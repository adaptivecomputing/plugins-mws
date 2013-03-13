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

	def "Test getGenericMapWithDisplayValue()"() {
		when: "No wiki"
		def result = translator.getGenericMap(null)

		then:
		!result

		when: "No value or display names"
		result = translator.getGenericMapWithDisplayValue("attr+attr2", "\\+", ":|=")

		then:
		result.attr.value == null
		result.attr.displayValue == null
		result.attr2.value == null
		result.attr2.displayValue == null


		when: "Test no display names"
		result = translator.getGenericMapWithDisplayValue("attr:value+attr2:value2", "\\+", "[:=]")

		then:
		result.attr.value == "value"
		result.attr.displayValue == null
		result.attr2.value == "value2"
		result.attr2.displayValue == null

		when: "Test display names with colons"
		result = translator.getGenericMapWithDisplayValue("attr:value:v:a:l:u:e+attr2:value2:v:a:l:u:e:2", "\\+", ":|=")

		then:
		result.attr.value == "value"
		result.attr.displayValue == "v:a:l:u:e"
		result.attr2.value == "value2"
		result.attr2.displayValue == "v:a:l:u:e:2"

		when: "Test display names with equal and colons"
		result = translator.getGenericMapWithDisplayValue("attr=value=v:a=l:u=e+attr2=value2:v:a=l:u:e=2", "\\+", ":|=")

		then:
		result.attr.value == "value"
		result.attr.displayValue == "v:a=l:u=e"
		result.attr2.value == "value2"
		result.attr2.displayValue == "v:a=l:u:e=2"

		when: "Test display names with colon identical values"
		result = translator.getGenericMapWithDisplayValue("attr:value:val:val+attr2:value2:val2:val2", "\\+", ":|=")

		then:
		result.attr.value == "value"
		result.attr.displayValue == "val:val"
		result.attr2.value == "value2"
		result.attr2.displayValue == "val2:val2"
	}
}