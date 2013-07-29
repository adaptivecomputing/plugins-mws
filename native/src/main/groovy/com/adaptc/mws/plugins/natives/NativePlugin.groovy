package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*
import com.adaptc.mws.plugins.natives.utils.NativeUtils
import groovy.transform.Synchronized

/**
 * The Native Plugin is a replication of the Moab Native resource manager interface in MWS.
 * @author bsaville
 */
class NativePlugin extends AbstractPlugin {
	static description = "Basic implementation of a native (Wiki interface) plugin"

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
		startUrl required:false, scriptableUrl:true
		stopUrl required:false, scriptableUrl:true
		reportImages defaultValue:true
	}

	JobNativeTranslator jobNativeTranslator
	NodeNativeTranslator nodeNativeTranslator
	VirtualMachineNativeTranslator virtualMachineNativeTranslator
    IJobRMService jobRMService
    INodeRMService nodeRMService
	IVirtualMachineRMService virtualMachineRMService
	NativeImageTranslator nativeImageTranslator
	IPluginEventService pluginEventService

	private def getConfigKey(String key) {
		if (config.containsKey(key))
			return config[key]
		return null
	}

	/**
	 * Overrides the default implementation of poll so that a single
	 * cluster query can be used for both nodes and VMs.
   * This is also synchronized so that only one poll can run at a time.
	 */
	@Synchronized
	public void poll() {
		def aggregateImagesInfo = new AggregateImagesInfo()
		def nodes = []
		def vms = []
		if (getConfigKey("getCluster")) {
			log.debug("Polling getCluster URL")
			getCluster(aggregateImagesInfo)?.groupBy { it.class }?.each { Class clazz, List values ->
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
			nodes = getNodes(aggregateImagesInfo)
			vms = getVirtualMachines(aggregateImagesInfo)
		}

		// Ensure that save is called EVERY time the poll is executed
		nodeRMService.save(nodes)
		virtualMachineRMService.save(vms)

		log.debug("Polling getJobs URL")
		jobRMService.save(getJobs());

		// Save images
		if (config.reportImages) {
			if (!nativeImageTranslator.pluginEventService)
				nativeImageTranslator.pluginEventService = pluginEventService
			nativeImageTranslator.updateImages(id, aggregateImagesInfo)
		}
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
            return NativeUtils.parseWiki(result.content).collect { Map attrs ->
                jobNativeTranslator.createReport(attrs)
            }
        }
        return []
    }

	public List<?> getCluster(AggregateImagesInfo aggregateImagesInfo) {
		def url = getConfigKey("getCluster")?.toURL()
		if (!url)
			return []
		def result = readURL(url)
		if (!hasError(result, true)) {
			return NativeUtils.parseWiki(result.content).collect { Map attrs ->
				if (attrs.TYPE?.equalsIgnoreCase("VM") || attrs.CONTAINERNODE) { // Only VMs have CONTAINERNODE
					def imageInfo = new VMImageInfo()
					aggregateImagesInfo.vmImages << imageInfo
					return virtualMachineNativeTranslator.createReport(attrs, imageInfo)
				} else {	// Default to a node
					def imageInfo = new HVImageInfo()
					aggregateImagesInfo.hypervisorImages << imageInfo
					return nodeNativeTranslator.createReport(attrs, imageInfo)
				}
			}
		}
		return []
	}
	
	public List<NodeReport> getNodes(AggregateImagesInfo aggregateImagesInfo) {
		def url = getConfigKey("getNodes")?.toURL()
		if (!url)
			return []
		def result = readURL(url)
		if (!hasError(result, true)) {
			return NativeUtils.parseWiki(result.content).collect { Map attrs ->
				def imageInfo = new HVImageInfo()
				aggregateImagesInfo.hypervisorImages << imageInfo
				nodeNativeTranslator.createReport(attrs, imageInfo)
			}
		}
		return []
	}

	public List<VirtualMachineReport> getVirtualMachines(AggregateImagesInfo aggregateImagesInfo) {
		def url = getConfigKey("getVirtualMachines")?.toURL()
		if (!url)
			return []
		def result = readURL(url)
		if (!hasError(result, true)) {
			return NativeUtils.parseWiki(result.content).collect { Map attrs ->
				def imageInfo = new VMImageInfo()
				aggregateImagesInfo.vmImages << imageInfo
				virtualMachineNativeTranslator.createReport(attrs, imageInfo)
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

    // Account for workload queries which have an attribute called "ERROR"
		if (canBeEmpty) {
			return result.exitCode!=0 || result.content==null || 
        (result.content.size()!=0 && result.content[0].contains("ERROR") && !result.content[0].contains("ERROR="))
    }
		return result.exitCode!=0 || !result.content || 
      (result.content[0].contains("ERROR") && !result.content[0].contains("ERROR="))
	}
}
