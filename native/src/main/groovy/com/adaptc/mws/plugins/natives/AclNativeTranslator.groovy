package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.AclReportAffinity
import com.adaptc.mws.plugins.AclReportRule
import com.adaptc.mws.plugins.AclReportType
import com.adaptc.mws.plugins.ReportComparisonOperator
/**
 * Created by adaptive on 10/29/14.
 */
class AclNativeTranslator {
	public List<AclReportRule> parseAclRules(String wiki) {
		return wiki?.split(",")?.inject([]) { List list, String acl ->
			AclReportType parsedType
			ReportComparisonOperator parsedComparator
			boolean parsedExcludeFromAcl = false;
			boolean parsedRequireAll = false;
			boolean parsedXorWithAcl = false;
			boolean parsedCredentialLock = false;
			boolean parsedHardPolicyOnly = false;

			AclReportAffinity parsedAffinity

			int index = 0
			acl.toCharArray().inject("") { String parsedText, Character c ->
				index++

				if(!parsedType) {
					parsedType = AclReportType.parse(parsedText)
					if(parsedType)
						//reset the parsedText
						return c.toString()
					else
						//continue looking
						return parsedText + c.toString()
				}

				if(!parsedComparator) {
					parsedComparator = ReportComparisonOperator.parse(parsedText)
					if (parsedComparator) {
					//If the next iteration will also be a parsedComparator wait for it
						if (ReportComparisonOperator.parse(parsedText + c)) {
							parsedComparator = null
							return parsedText + c.toString()
						}
					//reset the parsedText
					return c.toString()
					}
					else
						//continue looking
						return parsedText + c.toString()
				}

				if(parsedText.equals("!")) {
						parsedExcludeFromAcl = true
						//reset the parsedText
						return c.toString()
				}

				if(parsedText.equals("*")) {
					parsedRequireAll = true
					//reset the parsedText
					return c.toString()
				}

				if(parsedText.equals("^")) {
					parsedXorWithAcl = true
					//reset the parsedText
					return c.toString()
				}

				if(parsedText.equals("&")) {
					parsedCredentialLock = true
					//reset the parsedText
					return c.toString()
				}

				if(parsedText.equals("~")) {
					parsedHardPolicyOnly = true
					//reset the parsedText
					return c.toString()
				}

				if(c != ":" && index != acl.length()) {
					//continue looking because rule hasn't completed
					return parsedText + c.toString()
				}


				if(c != ":") {
					parsedText = parsedText + c.toString()
				}

				if(parsedText[-1] == "-") {
					parsedText = parsedText.substring(0,parsedText.length() - 1)
					parsedAffinity = AclReportAffinity.NEGATIVE
				} else if (parsedText[-1] == "+") {
					parsedText = parsedText.substring(0,parsedText.length() - 1)
					parsedAffinity = AclReportAffinity.POSITIVE
				} else if (parsedText[-1] == "=") {
					parsedText = parsedText.substring(0,parsedText.length() - 1)
					parsedAffinity = AclReportAffinity.NEUTRAL
				} else {
					parsedAffinity = AclReportAffinity.POSITIVE
				}

				AclReportRule rule = new AclReportRule()
				rule.setType(parsedType)
				rule.setComparator(parsedComparator)
				rule.setExcludeFromAcl(parsedExcludeFromAcl)
				rule.setRequireAll(parsedRequireAll)
				rule.setXorWithAcl(parsedXorWithAcl)
				rule.setCredentialLock(parsedCredentialLock)
				rule.setHardPolicyOnly(parsedHardPolicyOnly)
				rule.setValue(parsedText)
				rule.setAffinity(parsedAffinity)
				list << rule

				//reset parsed modifiers for next rule
				parsedExcludeFromAcl = false
				parsedRequireAll = false
				parsedXorWithAcl = false
				parsedCredentialLock = false
				parsedHardPolicyOnly = false

				//reset parsedText and eat the colon if it is there in case there is another rule
				return ""
			}

			return list
		}
	}
}
