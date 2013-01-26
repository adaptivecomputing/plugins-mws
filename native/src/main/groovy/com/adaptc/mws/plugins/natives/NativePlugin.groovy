package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*

/**
 * The Native Plugin is a replication of the Moab Native resource manager interface in MWS.
 * Documentation may be found by going to the reference guide -&gt; Bundled Plugins -&gt;
 * Native.
 * @author bsaville
 */
class NativePlugin extends AbstractPlugin {
	static description = "Basic implementation of a native plugin"

	static constraints = {
		environment(required:false)
		getCluster required:false, scriptableUrl:true
		getNodes required:false, scriptableUrl:true
		getVirtualMachines required:false, scriptableUrl:true
		getJobs required:false, scriptableUrl:true
		jobCancel required:false, scriptableUrl:true
		jobModify required:false, scriptableUrl:true
		jobRequeue required:false, scriptableUrl:true
		jobResume required:false, scriptableUrl:true
		jobStart required:false, scriptableUrl:true
		jobSubmit required:false, scriptableUrl:true
		jobSuspend required:false, scriptableUrl:true
		nodeModify required:false, scriptableUrl:true
		nodePower required:false, scriptableUrl:true
		resourceCreate required:false, scriptableUrl:true
		startUrl required:false, scriptableUrl:true
		stopUrl required:false, scriptableUrl:true
		systemModify required:false, scriptableUrl:true
		systemQuery required:false, scriptableUrl:true
		virtualMachineMigrate required:false, scriptableUrl:true
	}

	JobNativeTranslator jobNativeTranslator
	NodeNativeTranslator nodeNativeTranslator
	VirtualMachineNativeTranslator virtualMachineNativeTranslator
    IJobRMService jobRMService
    INodeRMService nodeRMService
	IVirtualMachineRMService virtualMachineRMService

	private def getConfigKey(String key) {
		if (config.containsKey(key))
			return config[key]
		return null
	}

	/**
	 * Overrides the default implementation of poll so that a single
	 * cluster query can be used for both nodes and VMs.
	 */
	public void poll() {
		def nodes = []
		def vms = []
		if (getConfigKey("getCluster")) {
			log.debug("Polling getCluster URL")
			getCluster()?.groupBy { it.class }?.each { Class clazz, List values ->
				switch(clazz) {
					case NodeReport.class:
                        nodes = values
						break;
					case VirtualMachineReport.class:
                        vms = values
						break;
					default:
						log.warn("Unknown object found from cluster query, ignoring values: ${clazz}")
						break;
				}
			}
		} else {
			log.debug("Polling getNodes and getVirtualMachines URLs")
            nodes = getNodes()
            vms = getVirtualMachines()
		}

		// Ensure that save is called EVERY time the poll is executed
		nodeRMService.save(nodes)
		virtualMachineRMService.save(vms)

        log.debug("Polling getJobs URL")
        jobRMService.save(getJobs());
	}
	
	public void beforeStart() {
		try {
			def url = getConfigKey("startUrl")?.toURL()
			if (!url)
				return
			log.debug("Starting plugin ${id} with ${url}")
			def result = readURL(url)
			if (result.exitCode==0)
				log.info("Started plugin ${id} (${result.content})")
			else
				log.warn("Could not start plugin ${id} (${result.exitCode}): ${result.content}")
		} catch(Exception e) {
			log.warn("Could not start plugin ${id} due to exception: ${e.message}")
		}
	}
	
	public void afterStop() {
		try {
			def url = getConfigKey("stopUrl")?.toURL()
			if (!url)
				return
			log.debug("Stopping plugin ${id} with ${url}")
			def result = readURL(url)
			if (result.exitCode==0)
				log.info("Stopped plugin ${id} (${result.content})")
			else
				log.warn("Could not stop plugin ${id} (${result.exitCode}): ${result.content}")
		} catch(Exception e) {
			log.warn("Could not stop plugin ${id} due to exception: ${e.message}")
		}
	}

    public List<JobReport> getJobs() {
        def url = getConfigKey("getJobs")?.toURL()
        if (!url)
            return []
        def result = readURL(url)
        if (!hasError(result, true)) {
            return parseWiki(result.content).collect { Map attrs ->
                jobNativeTranslator.createReport(attrs)
            }
        }
        return []
    }

	public List<?> getCluster() {
		def url = getConfigKey("getCluster")?.toURL()
		if (!url)
			return []
		def result = readURL(url)
		if (!hasError(result, true)) {
			return parseWiki(result.content).collect { Map attrs ->
				if (attrs.CONTAINERNODE)	// Only VMs have this attribute
					return virtualMachineNativeTranslator.createReport(attrs)
				else	// Default to a node
					return nodeNativeTranslator.createReport(attrs)
			}
		}
		return []
	}
	
	public List<NodeReport> getNodes() {
		def url = getConfigKey("getNodes")?.toURL()
		if (!url)
			return []
		def result = readURL(url)
		if (!hasError(result, true)) {
			return parseWiki(result.content).collect { Map attrs ->
				nodeNativeTranslator.createReport(attrs)
			}
		}
		return []
	}

	public List<VirtualMachineReport> getVirtualMachines() {
		def url = getConfigKey("getVirtualMachines")?.toURL()
		if (!url)
			return []
		def result = readURL(url)
		if (!hasError(result, true)) {
			return parseWiki(result.content).collect { Map attrs ->
				virtualMachineNativeTranslator.createReport(attrs)
			}
		}
		return []
	}
	
	// TODO Cancel more than just the first job
	public boolean jobCancel(List<String> jobs) {
		def url = getConfigKey("jobCancel")?.toURL()
		if (!url)
			return false
		def jobId = jobs[0]
		url.query = jobId
		log.debug("Canceling job ${jobId}")
		def result = readURL(url)
		return !hasError(result)
	}

	// TODO Modify more than just the first job
	public boolean jobModify(List<String> jobs, Map properties) {
		def url = getConfigKey("jobModify")?.toURL()
		if (!url)
			return false
		def jobId = jobs[0]
		def queryParams = [jobId]
		queryParams.addAll(properties.collect { "${it.key}="+(it.value?.contains(" ")?"\"${it.value}\"":it.value) })
		url.query = queryParams.join("&")
		log.debug("Modifying job ${jobId} with properties ${properties}")
		def result = readURL(url)
		return !hasError(result)
	}
	
	// TODO Resume more than just the first job
	public boolean jobResume(List<String> jobs) {
		def url = getConfigKey("jobResume")?.toURL()
		if (!url)
			return false
		def jobId = jobs[0]
		url.query = jobId
		log.debug("Resuming job ${jobId}")
		def result = readURL(url)
		return !hasError(result)
	}

	// TODO Requeue more than just the first job
	public boolean jobRequeue(List<String> jobs) {
		def url = getConfigKey("jobRequeue")?.toURL()
		if (!url)
			return false
		def jobId = jobs[0]
		url.query = jobId
		log.debug("Requeuing job ${jobId}")
		def result = readURL(url)
		return !hasError(result)
	}

	//TODO Handle properties in jobStart
	public boolean jobStart(String jobId, String taskList, String userName, Map<String, String> properties=null) {
		def url = getConfigKey("jobStart")?.toURL()
		if (!url)
			return false
		url.query = [jobId, taskList, userName].join("&")
		log.debug("Starting job ${jobId} with task list ${taskList} and user ${userName}")
		def result = readURL(url)
		return !hasError(result)
	}

	public boolean jobSubmit(Map properties) {
		def url = getConfigKey("jobSubmit")?.toURL()
		if (!url)
			return false
		url.query = properties?.collect { "${it.key}="+(it.value?.contains(" ")?"\"${it.value}\"":it.value) }.join("&")
		log.debug("Submitting job ${properties.NAME}")
		def result = readURL(url)
		return !hasError(result)
	}

	// TODO Suspend more than just the first job
	public boolean jobSuspend(List<String> jobs) {
		def url = getConfigKey("jobSuspend")?.toURL()
		if (!url)
			return false
		def jobId = jobs[0]
		url.query = jobId
		log.debug("Suspending job ${jobId}")
		def result = readURL(url)
		return !hasError(result)
	}

	public boolean nodeModify(List<String> nodes, Map<String, String> properties) {
		def url = getConfigKey("nodeModify")?.toURL()
		if (!url)
			return false
		url.query = nodes.join(",")+"&--set&"
		url.query += properties.collect { "${it.key}="+(it.value?.contains(" ")?"\"${it.value}\"":it.value) }.join("&")
		log.debug("Modifying nodes ${nodes} with properties ${properties}")
		def result = readURL(url)
		return !hasError(result)
	}

	public boolean nodePower(List<String> nodes, NodeReportPower state) {
		def url = getConfigKey("nodePower")?.toURL()
		if (!url)
			return false
		url.query = [nodes.join(","), state.name()].join("&")
		log.debug("Changing power state to ${state} for nodes ${nodes}")
		def result = readURL(url)
		return !hasError(result)
	}
	
	public boolean resourceCreate(String type, String id, Map<String, String> attributes) {
		def url = getConfigKey("resourceCreate")?.toURL()
		if (!url)
			return false
		url.query = [type, id].join("&")+"&"
		url.query += attributes.collect { "${it.key}="+(it.value?.contains(" ")?"\"${it.value}\"":it.value) }.join("&")
		log.debug("Creating ${type} resource with ID ${id} and attributes ${attributes}")
		def result = readURL(url)
		return !hasError(result)
	}

	public boolean systemModify(Map<String, String> properties) {
		def url = getConfigKey("systemModify")?.toURL()
		if (!url)
			return false
		def operation = properties.remove("operation")
		url.query = operation + "&" + properties.collect { "${it.key}="+
			(it.value?.contains(" ")?"\"${it.value}\"":it.value) }.join("&")
		log.debug("Modifying system with operation ${operation} and properties ${properties}")
		def result = readURL(url)
		return !hasError(result)
	}

	// TODO Implement querying multiple system attributes
	public List<String> systemQuery(List<String> attributes) {
		def url = getConfigKey("systemQuery")?.toURL()
		if (!url)
			return []
		def attribute = attributes[0]
		url.query = attribute
		log.debug("Querying system for ${attribute}")
		def result = readURL(url)
		if (!hasError(result))
			return result.content
		return []
	}

	public boolean virtualMachineMigrate(String vmId, String hypervisorId, String operationId) {
		def url = getConfigKey("virtualMachineMigrate")?.toURL()
		if (!url)
			return false
		// --vmigrate vm1.pn=hv1 operationid=vmmigrate-1 ??
		url.query = ["--vmigrate", "${vmId}.pn=${hypervisorId}", "operationid=${operationId}"].join("&")
		log.debug("Migrating VM ${vmId} to hypervisor ${hypervisorId}${operationId?' (${operationId})':''}")
		def result = readURL(url)
		return !hasError(result)
	}
	
	
	/**
	 * Returns a list of maps containing the wiki parameters, with the id stored as id
	 * and all other parameters as key/value pairs.<br><br>
	 * Also handles hashes for newline delimiters
	 * @param lines Native wiki lines delimited by semi-colons and equals (; and =)
	 * @return
	 */
	List<Map> parseWiki(lines) {
		def wikiLines = []
		lines.each { String line ->
			// Check for # for separation of wiki objects and check for escaped
			if (!line || line.trim().isEmpty() || line =~ /^#/) {
				// do nothing with empty commented (starting with #) lines
			} else if (line.contains("#"))	// Catch hashed lines
				wikiLines.addAll(line.replaceAll(/\\#/, '{HASH}').split("#").collect { it.replaceAll(/\{HASH\}/, "#") })
			else
				wikiLines.add(line)
		}
		return wikiLines.collect { String line ->
			// Replace escaped characters
			line = line.replaceAll(/\\;/, '{SEMICOLON}')

			// Split on spaces, replace escaped chars, and split on =
			def map = [:]
			List attrs = (line =~ /(?:^|[ \t]+|\;)([^ \t;"']+(?:".*?"|'.*?'|))+/).collect { it[1] }
			map.id = attrs.remove(0)
			def lastKey = ""
			attrs.each {
				def pair = it.replaceAll(/\{SEMICOLON\}/, ';')

				// Check for generic metrics
				if (it.startsWith("GMETRIC")) {
					def match = pair =~ /GMETRIC\[(.*?)\]=(.*)/
					def key = match[0][1]
					def val = match[0][2]
					if (!map.GMETRIC)
						map.GMETRIC = [:]
					map.GMETRIC[key] = val
					return
				}
				
				// Check for generic events
				if (it.startsWith("GEVENT")) {
					def match = pair =~ /GEVENT\[(.*?)\]=(.*)/
					def key = match[0][1]
					def val = match[0][2]
					if (!map.GEVENT)
						map.GEVENT = [:]
					map.GEVENT[key] = val
					return
				}
				
				// Check for variables
				if (it.startsWith("VARIABLE")) {
					def match = pair =~ /VARIABLE\=(.*?)(?:\=(.*)|)$/
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
				if (lastKey=="STATE" && entry.size()==1) {	// STATE=state:substate, append to last one
					map[lastKey] += ":${pair}"
					return
				}
				if (entry.size()==1) {
					log.warn("Invalid Wiki detected for attribute ${pair} on line '${line}'")
					return
				}

				// Handle messages correctly, adding to a list and removing quotes around them
				if (entry[0]=="MESSAGE") {
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
	
	private def readURL(URL url) {
		log.debug("Retrieving URL ${url}")
		try {
			if (url.protocol!="exec") {
				def content = url.readLines()
				log.debug("Received non-script content of ${content}")
				return [content:content, exitCode:0]	// mimic success
			}

			// Exec protocol handling for exit code return
			def conn = url.openConnection()
			conn.connect()
			setEnvironment(conn)
			def content = ((InputStream)conn.content).readLines()
			def exitCode = conn.exitCode
			log.debug("Received script content of ${content} with exit code ${exitCode}")
			return [content:content,exitCode:exitCode]
		} catch(FileNotFoundException e) {
			log.error("Could not read from file ${url}, it does not exist!")
		} catch(IOException e) {
			log.error("Could not read from file ${url}: ${e.message}")
		}
		return null
	}
	
	private void setEnvironment(urlConnection) {
		def env = getConfigKey("environment")
		if (!env)
			return
		// parse into a map
		def envMap = env.tokenize("&").inject([:]) { Map map, entry ->
			def entrySplit = entry.tokenize("=")
			if (entrySplit.size()>1)
				map[entrySplit[0]] = entrySplit[1]
			else
				map[entry] = null
			map
		}
		log.debug("Setting environment to ${env} for URL")
		urlConnection.setEnvironment(envMap)
	}
	
	private boolean hasError(result, boolean canBeEmpty = false) {
		if (result==null)
			return true
		if (canBeEmpty)
			return result.exitCode!=0 || result.content==null || (result.content.size()!=0 && result.content[0].contains("ERROR"))
		return result.exitCode!=0 || !result.content || result.content[0].contains("ERROR")
	}

	private static final QUOTE_TRIM_PATTERN = /^"(.*)"$/
	private String trimQuotes(String str) {
		if (!str)
			return str
		return str.replaceAll(QUOTE_TRIM_PATTERN, "\$1")
	}
}
