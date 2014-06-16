package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.IMoabRestService
import com.adaptc.mws.plugins.IPluginEventService
import com.adaptc.mws.plugins.MoabRestResponse
import com.adaptc.mws.plugins.testing.TestFor
import spock.lang.*

/**
 * @author bsaville
 */
@Unroll
@TestFor(ImageNativeTranslator)
class ImageNativeTranslatorSpec extends Specification {
	def "Update images"() {
		given:
		AggregateImagesInfo imagesInfo = new AggregateImagesInfo()
		imagesInfo.vmImages << new VMImageInfo(name:"vmImage")
		imagesInfo.hypervisorImages << new HVImageInfo(name:"hvImage", vmImageNames:["hvVmImage1","hvVmImage2"])

		and:
		translator.metaClass.updateVMImages = { String pluginId, List<String> vmImages ->
			assert vmImages.size()==3
			assert vmImages[0]=="vmImage"
			assert vmImages[1]=="hvVmImage1"
			assert vmImages[2]=="hvVmImage2"
			assert pluginId=="pluginId"
		}
		translator.metaClass.updateHypervisorImages = { String pluginId, List<HVImageInfo> hvImages ->
			assert hvImages.size()==1
			assert hvImages[0].name=="hvImage"
			assert pluginId=="pluginId"
		}

		when:
		translator.updateImages("pluginId", imagesInfo)

		then:
		notThrown(Exception)
	}

	def "Notifications"() {
		given:
		IPluginEventService pluginEventService = Mock()
		translator.pluginEventService = pluginEventService

		when:
		translator.updateNotificationWarn("message", "name")

		then:
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, "message",
				{ it.id=="name" && it.type=="Image" }, null)
		0 * _._

		when:
		translator.updateNotificationWarn("message", "name", "Node")

		then:
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, "message",
				{ it.id=="name" && it.type=="Node" }, null)
		0 * _._

		when:
		translator.updateNotificationWarn("message", null)

		then:
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, "message",
				null, null)
		0 * _._

		when:
		translator.updateNotificationError("message", "name")

		then:
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, "message",
				{ it.id=="name" && it.type=="Image" }, null)
		0 * _._

		when:
		translator.updateNotificationError("message", "name", "Node")

		then:
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, "message",
				{ it.id=="name" && it.type=="Node" }, null)
		0 * _._

		when:
		translator.updateNotificationError("message", null)

		then:
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN, "message",
				null, null)
		0 * _._
	}

	def "Update VM images"() {
		given:
		IMoabRestService moabRestService = Mock()
		translator.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		translator.pluginEventService = pluginEventService

		when: "Fail to get /rest/images"
		translator.updateVMImages("plugin1", [new VMImageInfo(name:"os1"), new VMImageInfo(name:"os2")])

		then:
		1 * moabRestService.get([params:[query:'{extensions.native: {$ne: null}, hypervisor: false}',fields:"name,extensions.native", 'api-version': 3]],
					"/rest/images") >>
				new MoabRestResponse(null, [messages:["message1","message2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.vm.images", null, null)
		0 * _._

		when:
		translator.updateVMImages("plugin1", [
				null, // null value to see what happens
				"addImageFail",
				"addImageSucceed",
				"updateImageAddOwner",
				"noopImage",
		])

		then:
		1 * moabRestService.get([params:[query:'{extensions.native: {$ne: null}, hypervisor: false}',fields:"name,extensions.native", 'api-version': 3]],
				"/rest/images") >>
				new MoabRestResponse(null, [results:[
						[name:"deleteImageFailGetVM",extensions:[native:[owners:["plugin1"]]]],
						[name:"deleteImageFailGetHV",extensions:[native:[owners:["plugin1"]]]],
						[name:"deleteImageFailUpdate",extensions:[native:[owners:["plugin1"]]]],
						[name:"deleteImageSucceed",extensions:[native:[owners:["plugin1"]]]],
						[name:"updateImageRemoveOwnerFailGetVM",extensions:[native:[owners:["plugin2","plugin1"]]]],
						[name:"updateImageRemoveOwnerFailUpdate",extensions:[native:[owners:["plugin2","plugin1"]]]],
						[name:"updateImageRemoveOwnerSucceed",extensions:[native:[owners:["plugin2","plugin1"]]]],
						[name:"updateImageAddOwner",extensions:[native:[owners:["plugin2"]]]],
						[name:"noopImage",extensions:[native:[owners:["plugin1"]]]],
				]], true)

		then: "Fail to add image"
		1 * moabRestService.post("/rest/images", params: ['api-version': 3], {
			def data = it.call()
			if (data.name!="addImageFail")
				return false
			assert data.active==true
			assert data.extensions?.native?.owners?.size()==1
			assert data.extensions?.native?.owners[0]=="plugin1"
			assert data.hypervisor==false
			assert data.hypervisorType==null
			assert data.osType==ImageNativeTranslator.IMAGE_OS_TYPE
			assert data.supportsPhysicalMachine==false
			assert data.supportsVirtualMachine==true
			assert data.templateName=="addImageFail"
			assert data.type==ImageNativeTranslator.IMAGE_TYPE
			return true
		}) >> new MoabRestResponse(null, [messages:["message1","message2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.post.vm.image", {it.type=="Image" && it.id=="addImageFail"}, null)

		then: "Succeed adding image"
		1 * moabRestService.post("/rest/images", params: ['api-version': 3], {
			def data = it.call()
			if (data.name!="addImageSucceed")
				return false
			assert data.active==true
			assert data.extensions?.native?.owners?.size()==1
			assert data.extensions?.native?.owners[0]=="plugin1"
			assert data.hypervisor==false
			assert data.hypervisorType==null
			assert data.osType==ImageNativeTranslator.IMAGE_OS_TYPE
			assert data.supportsPhysicalMachine==false
			assert data.supportsVirtualMachine==true
			assert data.templateName=="addImageSucceed"
			assert data.type==ImageNativeTranslator.IMAGE_TYPE
			return true
		}) >> new MoabRestResponse(null, null, true)

		then: "Delete fail to get VM"
		1 * moabRestService.get([params:[fields:"id", 'api-version': 3]], "/rest/images/deleteImageFailGetVM") >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.vm.image", {it.type=="Image" && it.id=="deleteImageFailGetVM"}, null)

		then: "Delete fail to get hypervisors"
		1 * moabRestService.get([params:[fields:"id", 'api-version': 3]], "/rest/images/deleteImageFailGetHV") >>
				new MoabRestResponse(null, [id:"id1"], true)
		1 * moabRestService.get([params:[query:'{virtualizedImages.id: "id1"}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.hv.images", null, null)

		then: "Delete other failures"
		1 * moabRestService.get([params:[fields:"id", 'api-version': 3]], "/rest/images/deleteImageFailUpdate") >>
				new MoabRestResponse(null, [id:"id2"], true)
		1 * moabRestService.get([params:[query:'{virtualizedImages.id: "id2"}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [results:[
						[name:"hvFail",virtualizedImages:[[id:"bogusId"],[id:"anotherId"],[id:"id2"]]],
						[name:"hvSucceed",virtualizedImages:[[id:"id2"]]],
				]], true)
		1 * moabRestService.put("/rest/images/hvFail", params:['api-version': 3], {
			def data = it.call()
			assert data.name=="hvFail"
			assert data.virtualizedImages?.size()==2
			assert data.virtualizedImages[0].id=="bogusId"
			assert data.virtualizedImages[1].id=="anotherId"
			return true
		}) >> new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.put.hv.image", {it.type=="Image" && it.id=="hvFail"}, null)
		1 * moabRestService.put("/rest/images/hvSucceed", params:['api-version': 3], {
			def data = it.call()
			assert data.name=="hvSucceed"
			assert data.virtualizedImages?.size()==0
			return true
		}) >> new MoabRestResponse(null, null, true)
		1 * moabRestService.delete("/rest/images/deleteImageFailUpdate", params:['api-version': 3]) >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.delete.vm.image", {it.type=="Image" && it.id=="deleteImageFailUpdate"}, null)

		then: "Delete succeed"
		1 * moabRestService.get([params:[fields:"id", 'api-version': 3]], "/rest/images/deleteImageSucceed") >>
				new MoabRestResponse(null, [id:"id3"], true)
		1 * moabRestService.get([params:[query:'{virtualizedImages.id: "id3"}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [results:[
						[name:"hvSucceed2",virtualizedImages:[[id:"id3"],[id:"id1"]]],
				]], true)
		1 * moabRestService.put("/rest/images/hvSucceed2", params:['api-version': 3], {
			def data = it.call()
			assert data.name=="hvSucceed2"
			assert data.virtualizedImages?.size()==1
			assert data.virtualizedImages[0]==[id:"id1"]
			return true
		}) >> new MoabRestResponse(null, null, true)
		1 * moabRestService.delete("/rest/images/deleteImageSucceed", params:['api-version': 3]) >>
				new MoabRestResponse(null, null, true)

		then: "Update image to remove owner failure to get VM"
		1 * moabRestService.get("/rest/images/updateImageRemoveOwnerFailGetVM", params:['api-version': 3]) >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.vm.image", {it.type=="Image" && it.id=="updateImageRemoveOwnerFailGetVM"}, null)

		then: "Update image to remove owner failure to update"
		1 * moabRestService.get("/rest/images/updateImageRemoveOwnerFailUpdate", params:['api-version': 3]) >>
				new MoabRestResponse(null, [name:"updateImageRemoveOwnerFailUpdate",extensions:[native:[owners:null]]], true)
		1 * moabRestService.put("/rest/images/updateImageRemoveOwnerFailUpdate", params:['api-version': 3], {
			def data = it.call()
			assert data.name=="updateImageRemoveOwnerFailUpdate"
			assert data.extensions?.native?.owners?.size()==0
			return true
		}) >> new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.put.vm.image", {it.type=="Image" && it.id=="updateImageRemoveOwnerFailUpdate"}, null)

		then: "Update image to remove owner succeed"
		1 * moabRestService.get("/rest/images/updateImageRemoveOwnerSucceed", params:['api-version': 3]) >>
				new MoabRestResponse(null, [name:"updateImageRemoveOwnerSucceed",extensions:[native:[owners:["plugin2", "plugin1"]]]], true)
		1 * moabRestService.put("/rest/images/updateImageRemoveOwnerSucceed", params:['api-version': 3], {
			def data = it.call()
			assert data.name=="updateImageRemoveOwnerSucceed"
			assert data.extensions?.native?.owners?.size()==1
			assert data.extensions?.native?.owners[0]=="plugin2"
			return true
		}) >> new MoabRestResponse(null, null, true)

		then: "Update image to add owner succeed"
		1 * moabRestService.get("/rest/images/updateImageAddOwner", params:['api-version': 3]) >>
				new MoabRestResponse(null, [name:"updateImageAddOwner",extensions:[native:[owners:["plugin2"]]]], true)
		1 * moabRestService.put("/rest/images/updateImageAddOwner", params:['api-version': 3], {
			def data = it.call()
			assert data.name=="updateImageAddOwner"
			assert data.extensions?.native?.owners?.size()==2
			assert data.extensions?.native?.owners[0]=="plugin2"
			assert data.extensions?.native?.owners[1]=="plugin1"
			return true
		}) >> new MoabRestResponse(null, null, true)

		then: "No-op image has nothing done to it"
		0 * _._
	}

	def "Update hypervisor images"() {
		given:
		IMoabRestService moabRestService = Mock()
		translator.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		translator.pluginEventService = pluginEventService

		and:
		translator.metaClass.updateVirtualizedImages = { List storedHypervisorImages, List<HVImageInfo> hypervisorImages ->
			assert storedHypervisorImages.size()==1
			assert storedHypervisorImages[0].name=="storedHVImageList"
			assert hypervisorImages.size()==5
			assert hypervisorImages.any { it.name==null }
			assert hypervisorImages.any { it.name=="addImageFail" }
			assert hypervisorImages.any { it.name=="addImageSucceedCachedHT" }
			assert hypervisorImages.any { it.name=="addImageSucceedDefaultHT" }
			assert hypervisorImages.any { it.name=="noopImage" }
		}

		when: "Fail to get /rest/images"
		translator.updateHypervisorImages("plugin1", [new HVImageInfo(name:"os1")])

		then:
		1 * moabRestService.get([params:[query:'{extensions.native.owners: ["plugin1"], hypervisor:true}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [messages:["message1","message2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.hv.images", null, null)
		0 * _._

		when:
		translator.updateHypervisorImages("plugin1", [
				new HVImageInfo(), // null name value to see what happens
				new HVImageInfo(name:"addImageFail", hypervisorType:"esx"),
				new HVImageInfo(name:"addImageSucceedCachedHT", hypervisorType:"esx"), // uses cached value of hypervisor type
				new HVImageInfo(name:"addImageSucceedDefaultHT"),	// no hypervisor type
				new HVImageInfo(name:"noopImage"),
		])

		then:
		1 * moabRestService.get([params:[query:'{extensions.native.owners: ["plugin1"], hypervisor:true}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [results:[
						[name:"deleteImageFail",extensions:[native:[owners:["plugin1"]]]],
						[name:"deleteImageSucceed",extensions:[native:[owners:["plugin1"]]]],
						[name:"noopImage",extensions:[native:[owners:["plugin1"]]]],
				]], true)

		then: "Add image fail"
		1 * moabRestService.post("/rest/images", params:['api-version': 3], {
			def data = it.call()
			if (data.name!="addImageFail")
				return
			assert data.active==true
			assert data.extensions?.native?.owners?.size()==1
			assert data.extensions?.native?.owners[0]=="plugin1"
			assert data.hypervisor==true
			assert data.hypervisorType=="esx"
			assert data.osType==ImageNativeTranslator.IMAGE_OS_TYPE
			assert data.supportsPhysicalMachine==true
			assert data.supportsVirtualMachine==false
			assert data.templateName==null
			assert data.type==null
			return true
		}) >> new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.post.hv.image", {it.type=="Image" && it.id=="addImageFail"}, null)

		then: "Add image succeed cached hypervisor type"
		1 * moabRestService.post("/rest/images", params:['api-version': 3], {
			def data = it.call()
			if (data.name!="addImageSucceedCachedHT")
				return
			assert data.active==true
			assert data.extensions?.native?.owners?.size()==1
			assert data.extensions?.native?.owners[0]=="plugin1"
			assert data.hypervisor==true
			assert data.hypervisorType=="esx"
			assert data.osType==ImageNativeTranslator.IMAGE_OS_TYPE
			assert data.supportsPhysicalMachine==true
			assert data.supportsVirtualMachine==false
			assert data.templateName==null
			assert data.type==null
			return true
		}) >> new MoabRestResponse(null, null, true)

		then: "Add image succeed default hypervisor type"
		1 * moabRestService.post("/rest/images", params:['api-version': 3], {
			def data = it.call()
			if (data.name!="addImageSucceedDefaultHT")
				return
			assert data.active==true
			assert data.extensions?.native?.owners?.size()==1
			assert data.extensions?.native?.owners[0]=="plugin1"
			assert data.hypervisor==true
			assert data.hypervisorType==ImageNativeTranslator.DEFAULT_HYPERVISOR_TYPE
			assert data.osType==ImageNativeTranslator.IMAGE_OS_TYPE
			assert data.supportsPhysicalMachine==true
			assert data.supportsVirtualMachine==false
			assert data.templateName==null
			assert data.type==null
			return true
		}) >> new MoabRestResponse(null, null, true)

		then: "Delete image fail"
		1 * moabRestService.delete("/rest/images/deleteImageFail", params:['api-version': 3]) >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.delete.hv.image", {it.type=="Image" && it.id=="deleteImageFail"}, null)

		then: "Delete image fail"
		1 * moabRestService.delete("/rest/images/deleteImageSucceed", params:['api-version': 3]) >>
				new MoabRestResponse(null, null, true)

		then: "Get images again from MWS for updating virtualized images list (see metaClass override in given block)"
		1 * moabRestService.get([params:[query:'{extensions.native.owners: ["plugin1"], hypervisor:true}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [results:[[name:"storedHVImageList"]]], true)

		then: "No-op image does nothing"
		0 * _._
	}

	def "Update hypervisor images failure to get images for VI list"() {
		given:
		IMoabRestService moabRestService = Mock()
		translator.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		translator.pluginEventService = pluginEventService

		when:
		translator.updateHypervisorImages("plugin1", [
				new HVImageInfo(name:"addImage"),
		])

		then:
		1 * moabRestService.get([params:[query:'{extensions.native.owners: ["plugin1"], hypervisor:true}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [results:[]], true)

		then: "Add image"
		1 * moabRestService.post("/rest/images", params:['api-version': 3], _ as Closure) >> new MoabRestResponse(null, null, true)

		then: "Get images again from MWS fails"
		1 * moabRestService.get([params:[query:'{extensions.native.owners: ["plugin1"], hypervisor:true}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.hv.images", null, null)
		0 * _._
	}

	def "Update hypervisor images no operations"() {
		given:
		IMoabRestService moabRestService = Mock()
		translator.moabRestService = moabRestService

		and:
		translator.metaClass.updateVirtualizedImages = { List storedHypervisorImages, List<HVImageInfo> hypervisorImages ->
			assert storedHypervisorImages.size()==0
			assert hypervisorImages.size()==0
		}

		when:
		translator.updateHypervisorImages("plugin1", [])

		then:
		1 * moabRestService.get([params:[query:'{extensions.native.owners: ["plugin1"], hypervisor:true}', 'api-version': 3]], "/rest/images") >>
				new MoabRestResponse(null, [results:[]], true)
		0 * _._
	}

	def "Update virtualized images"() {
		given:
		IMoabRestService moabRestService = Mock()
		translator.moabRestService = moabRestService
		IPluginEventService pluginEventService = Mock()
		translator.pluginEventService = pluginEventService

		when: "Blank case"
		translator.updateVirtualizedImages([], [])

		then:
		true

		when: "All other cases"
		translator.updateVirtualizedImages(
				[
						[name:"mismatchedAndQueryFail", virtualizedImages:[]],
						[name:"noVMImages", virtualizedImages:[]],
						[name:"noUpdate", virtualizedImages:[[id:"id1"],[id:"id2"]]],
						[name:"fail", virtualizedImages:[[id:"id1"],[id:"id2"]]],
						[name:"succeed", virtualizedImages:[[id:"id1"],[id:"id2"]]],
				], [
				   		new HVImageInfo(name:"mismatchedAndQueryFail", nodeName:"node1", vmImageNames: ["vmOs1","vmOs2"]),
				   		new HVImageInfo(name:"mismatchedAndQueryFail", nodeName:"node2", vmImageNames: ["vmOs3","vmOs4"]),
				   		new HVImageInfo(name:"noUpdate", vmImageNames: ["vmOs4","vmOs5"]),
				   		new HVImageInfo(name:"noUpdate", vmImageNames: ["vmOs5","vmOs4"]), // out of order should not affect anything
				   		new HVImageInfo(name:"fail", vmImageNames: ["vmOs6"]),
				   		new HVImageInfo(name:"succeed", vmImageNames: ["vmOs7"]),
				])

		then: "Mismatched VM images"
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.mismatched.vm.image.sets", {it.type=="Node" && it.id=="node2"}, null)

		then: "Query fails"
		1 * moabRestService.get(params:[query:'{extensions.native: {$ne: null}, name:{$in: ["vmOs1", "vmOs2"]}}',fields:"id", 'api-version': 3], "/rest/images") >>
				new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.get.vm.images", null, null)

		then: "No update needed"
		1 * moabRestService.get(params:[query:'{extensions.native: {$ne: null}, name:{$in: ["vmOs4", "vmOs5"]}}',fields:"id", 'api-version': 3], "/rest/images") >>
				new MoabRestResponse(null, [results:[[id:"id2"],[id:"id1"]]], true)	// out of order should have no effect

		then: "Failure"
		1 * moabRestService.get(params:[query:'{extensions.native: {$ne: null}, name:{$in: ["vmOs6"]}}',fields:"id", 'api-version': 3], "/rest/images") >>
				new MoabRestResponse(null, [results:[[id:"id6"],[id:"id7"]]], true)
		1 * moabRestService.put("/rest/images/fail", params:['api-version': 3], {
			def data = it.call()
			assert data.size()==2
			assert data.name=="fail"
			assert data.virtualizedImages.size()==2
			assert data.virtualizedImages.any { it.id=="id6" }
			assert data.virtualizedImages.any { it.id=="id7" }
			return true
		}) >> new MoabRestResponse(null, [messages:["m1", "m2"]], false)
		1 * pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				"imageNativeTranslator.put.hv.image", {it.type=="Image" && it.id=="fail"}, null)

		then: "Success"
		1 * moabRestService.get(params:[query:'{extensions.native: {$ne: null}, name:{$in: ["vmOs7"]}}',fields:"id", 'api-version': 3], "/rest/images") >>
				new MoabRestResponse(null, [results:[[id:"id7"],[id:"id8"]]], true)
		1 * moabRestService.put("/rest/images/succeed", params:['api-version': 3], {
			def data = it.call()
			assert data.size()==2
			assert data.name=="succeed"
			assert data.virtualizedImages.size()==2
			assert data.virtualizedImages.any { it.id=="id7" }
			assert data.virtualizedImages.any { it.id=="id8" }
			return true
		}) >> new MoabRestResponse(null, null, true)

		then:
		0 * _._
	}
}
