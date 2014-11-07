package com.adaptc.mws.plugins.natives
import com.adaptc.mws.plugins.AclReportAffinity
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
		rule.excludeFromAcl == excludeFromAcl
		rule.requireAll == requireAll
		rule.xorWithAcl == xorWithAcl
		rule.credentialLock == credentialLock
		rule.hardPolicyOnly == hardPolicyOnly
		rule.value == value
		rule.affinity == affinity

		where:
		wiki													| desc														|ruleIndex	|type					|comparator 									|excludeFromAcl	|requireAll	|xorWithAcl	|credentialLock	|hardPolicyOnly	|value		|affinity
		"GroUP%=DEV"											|"default positive affinity"								|0			|AclReportType.GROUP	|ReportComparisonOperator.LEXIGRAPHIC_EQUAL		|false			|false		|false		|false			|false			|"DEV"		|AclReportAffinity.POSITIVE
		"GROUP%=DEV:MARKETING:ENGINEERING,USer>=BILL-"			|"multiple credentials"										|3			|AclReportType.USER		|ReportComparisonOperator.GREATER_THAN_OR_EQUAL	|false			|false		|false		|false			|false			|"BILL"		|AclReportAffinity.NEGATIVE
		"USer==!BILL="											|"modifier"													|0			|AclReportType.USER		|ReportComparisonOperator.LEXIGRAPHIC_EQUAL		|true			|false		|false		|false			|false			|"BILL"		|AclReportAffinity.NEUTRAL
		"UsER==BI!LL="											|"misplaced modifier"										|0			|AclReportType.USER		|ReportComparisonOperator.LEXIGRAPHIC_EQUAL		|false			|false		|false		|false			|false			|"BI!LL"	|AclReportAffinity.NEUTRAL
		"USEr==^~BILL="											|"2 modifiers"												|0			|AclReportType.USER		|ReportComparisonOperator.LEXIGRAPHIC_EQUAL		|false			|false		|true		|false			|true			|"BILL"		|AclReportAffinity.NEUTRAL
		"DURATION%!!23425-:~524=:&6234+"						|"!comparator rule modifier"								|0			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|true			|false		|false		|false			|false			|"23425"	|AclReportAffinity.NEGATIVE
		"DUraTION%!!23425-:~524=:&6234+"						|"multiple rule modifiers"									|1			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|false			|false		|false		|false			|true			|"524"		|AclReportAffinity.NEUTRAL
		"DUraTION%!!23425-:~524=:&6234+"						|"multiple rule affinity"									|2			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|false			|false		|false		|true			|false			|"6234"		|AclReportAffinity.POSITIVE
		"GROUp!=DEV,USER!=BILL,DURAtiON%!!23425-:~524=:&6234+"	|"multiple rule affinity after multiple credentials"		|4			|AclReportType.DURATION	|ReportComparisonOperator.LEXIGRAPHIC_NOT_EQUAL	|false			|false		|false		|true			|false			|"6234"		|AclReportAffinity.POSITIVE
	}

}
