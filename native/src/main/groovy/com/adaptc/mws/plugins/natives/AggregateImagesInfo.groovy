package com.adaptc.mws.plugins.natives

/**
 * @author bsaville
 */
public class AggregateImagesInfo {
	List<HVImageInfo> hypervisorImages = new ArrayList<HVImageInfo>()
	List<VMImageInfo> vmImages = new ArrayList<VMImageInfo>()

	public String toString() {
		return "Aggregate Images: ${hypervisorImages+vmImages}"
	}
}

class HVImageInfo {
	String name
	String nodeName
	String hypervisorType
	List<String> vmImageNames = new ArrayList<String>()

	public String toString() {
		return "Hypervisor Image ${name} for ${nodeName} (${hypervisorType})"
	}
}

class VMImageInfo {
	String name

	public String toString() {
		return "VM Image ${name}"
	}
}