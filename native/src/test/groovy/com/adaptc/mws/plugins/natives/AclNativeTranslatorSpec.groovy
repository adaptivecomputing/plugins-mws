package com.adaptc.mws.plugins.natives
import com.adaptc.mws.plugins.AclReportAffinity
import com.adaptc.mws.plugins.AclReportModifier
import com.adaptc.mws.plugins.AclReportRule
import com.adaptc.mws.plugins.AclReportType
import com.adaptc.mws.plugins.ReportComparisonOperator
import com.adaptc.mws.plugins.testing.TestFor
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by adaptive on 10/29/14.
 */
@TestFor(AclNativeTranslator)
class AclNativeTranslatorSpec extends Specification {
	@Unroll
	def "Test parse ACL Rules: #desc"() {
		when:
		List<AclReportRule> rules = translator.parseAclRules(wiki)
		AclReportRule rule = rules[ruleIndex]

		then:
		rule.type == type
		rule.comparator == comparator
		rule.modifier == modifier
		rule.value == value
		rule.affinity == affinity

		where:
		wiki													| desc														|ruleIndex	|type					|comparator 									|modifier							|value		|affinity
		"GROUP%=DEV"											|"default positive affinity"								|0			|AclReportType.GROUP	|ReportComparisonOperator.LEXIGRAPHIC_EQUAL		|null								|"DEV"		|AclReportAffinity.POSITIVE
		"GROUP%=DEV:MARKETING:ENGINEERING,USER>=BILL-"			|"multiple credentials"										|3			|AclReportType.USER		|ReportComparisonOperator.GREATER_THAN_OR_EQUAL	|null								|"BILL"		|AclReportAffinity.NEGATIVE
		"USER==!BILL="											|"modifier"													|0			|AclReportType.USER		|ReportComparisonOperator.EQUAL					|AclReportModifier.NOT				|"BILL"		|AclReportAffinity.NEUTRAL
		"USER==BI!LL="											|"misplaced modifier"										|0			|AclReportType.USER		|ReportComparisonOperator.EQUAL					|null								|"BI!LL"	|AclReportAffinity.NEUTRAL
		"USER==^~BILL="											|"2 modifiers"												|0			|AclReportType.USER		|ReportComparisonOperator.EQUAL					|AclReportModifier.EXCLUSIVE_OR		|"~BILL"	|AclReportAffinity.NEUTRAL
		"DURATION%!!23425-:~524=:&6234+"						|"!comparator rule modifier"								|0			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|AclReportModifier.NOT				|"23425"	|AclReportAffinity.NEGATIVE
		"DURATION%!!23425-:~524=:&6234+"						|"multiple rule modifiers"									|1			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|AclReportModifier.HARD_POLICY		|"524"		|AclReportAffinity.NEUTRAL
		"DURATION%!!23425-:~524=:&6234+"						|"multiple rule affinity"									|2			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|AclReportModifier.CREDENTIAL_LOCK	|"6234"		|AclReportAffinity.POSITIVE
		"GROUP!=DEV,USER!=BILL,DURATION%!!23425-:~524=:&6234+"	|"multiple rule affinity after multiple credentials"		|4			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|AclReportModifier.CREDENTIAL_LOCK	|"6234"		|AclReportAffinity.POSITIVE
	}

}
