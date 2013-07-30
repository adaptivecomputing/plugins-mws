package com.adaptc.mws.plugins.natives

/**
 * @author bsaville
 */
public class AggregateImagesInfo {
	List<HVImageInfo> hypervisorImages = new ArrayList<HVImageInfo>()
	List<VMImageInfo> vmImages = new ArrayList<VMImageInfo>()

}

class HVImageInfo {
	String name
	String nodeName
	String hypervisorType
	List<String> vmImageNames = new ArrayList<String>()
}

class VMImageInfo {
	String name
}