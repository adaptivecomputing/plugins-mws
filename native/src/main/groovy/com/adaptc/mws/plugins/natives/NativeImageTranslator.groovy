package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*

/**
 * @author bsaville
 */
class NativeImageTranslator {
	private static final String IMAGES_RESOURCE = "/rest/images"
	private static final String IMAGE_TYPE = "FULL_CLONE"
	private static final String IMAGE_OS_TYPE = "unknown"
	private static final String HYPERVISOR_TYPE = "Native"

	IMoabRestService moabRestService
	IPluginEventService pluginEventService

	public void updateImages(String pluginId, List<NodeReport> nodeReports,
							 List<VirtualMachineReport> vmReports,
							 Map<String, List<String>> hypervisorToVMTable) {
		updateHypervisorImages(pluginId, nodeReports, hypervisorToVMTable)
		updateVMImages(pluginId, vmReports)
	}

	private void updateVMImages(String pluginId, List<VirtualMachineReport> vmReports) {
		final String getAllMyVMImages =
			'{extensions.native: {$ne: null}, type: "' + IMAGE_TYPE + '"}'

		// Get all image names from the current poll.
		Set vmImageNames = vmReports.inject([] as Set) { Set set, VirtualMachineReport vmReport ->
			if (vmReport.image)
				set << vmReport.image
			return set
		}

		// Get all our images from the database.
		MoabRestResponse response = moabRestService.get(IMAGES_RESOURCE,
				params: [
						query: getAllMyVMImages,
						fields: "name,extensions.native"])
		if (response.hasError()) {
			updateNotificationError(message(
					code: "nativeImageTranslator.get.vm.images",
					args: [response.convertedData.messages.join(", ")]), VirtualMachineReport)
			return
		}
		List dataResults = response.convertedData.results
		// collectEntries is not null-safe
		Map<String, List> storedImageOwners = dataResults?.collectEntries { val ->
			return [val.name, val.extensions.native.owners ?: []]
		}
		Set storedImageNames = dataResults.collect([] as Set) {it.name}

		// Get the VM images to add to the database.
		Set addSet = vmImageNames - storedImageNames

		// Get the VM images to delete from the database.
		Set deleteSet = storedImageNames.findAll { storedImageOwners[it].contains(pluginId) } - vmImageNames
		// Get the set that has owners that need to be updated, then remove from deleted.
		def updateOwnersSet = storedImageNames.findAll {
			def owners = storedImageOwners[it]
			return (vmImageNames.contains(it) && !owners.contains(pluginId)) ||
					(!vmImageNames.contains(it) && owners.contains(pluginId) && owners.size() > 1)
		}
		deleteSet -= updateOwnersSet

		// Add as needed.
		addSet.each { String imageName ->
			response = moabRestService.post(IMAGES_RESOURCE) {
				[
						active: true,
						extensions: [
								native: [
										owners: [pluginId]
								]
						],
						hypervisor: false,
						hypervisorType: null,
						name: imageName,
						osType: IMAGE_OS_TYPE,
						supportsPhysicalMachine: false,
						supportsVirtualMachine: true,
						templateName: imageName,
						type: IMAGE_TYPE
				]
			}
			if (response.hasError())
				updateNotificationWarn(message(
						code: "nativeImageTranslator.post.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), VirtualMachineReport, imageName)
		}

		// Delete as needed.
		deleteSet.each { String imageName ->

			// Get the ID of this VM image.
			response = moabRestService.get("$IMAGES_RESOURCE/$imageName",
					params: [fields: "id"])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "nativeImageTranslator.get.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), VirtualMachineReport, imageName)
				return
			}
			String id = response.convertedData.id

			// Find all hypervisor images that refer to this VM image.
			response = moabRestService.get(IMAGES_RESOURCE,
					params: [query: '{virtualizedImages.id: "' + id + '"}'])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "nativeImageTranslator.get.hv.images",
						args: [response.convertedData.messages.join(", ")]), VirtualMachineReport)
				return
			}
			List hypervisorImages = response.convertedData.results

			// Remove the VM image reference from each hypervisor image.
			hypervisorImages.each { hypervisorImage ->
				hypervisorImage.virtualizedImages.removeAll { it.id == id }
				response = moabRestService.put("$IMAGES_RESOURCE/${hypervisorImage.name}") {hypervisorImage}
				if (response.hasError())
					updateNotificationWarn(message(
							code: "nativeImageTranslator.put.hv.image",
							args: [hypervisorImage.name, response.convertedData.messages.join(", ")]),
							VirtualMachineReport, hypervisorImage.name)
			}

			// Delete the VM image.
			response = moabRestService.delete("$IMAGES_RESOURCE/$imageName")
			if (response.hasError())
				updateNotificationWarn(message(
						code: "nativeImageTranslator.delete.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), VirtualMachineReport, imageName)
		}

		// Modify the image's owners list to include or remove this plugin
		updateOwnersSet.each { String imageName ->
			response = moabRestService.get("$IMAGES_RESOURCE/$imageName")
			if (response.hasError()) {
				updateNotificationError(message(
						code: "nativeImageTranslator.get.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), VirtualMachineReport, key)
				return
			}
			Map vmImage = response.convertedData
			// Update owners only if they should be updated.
			if (updateOwnersSet.contains(imageName)) {
				if (!vmImage.extensions.native.owners)
					vmImage.extensions.native.owners = []
				if (vmImageNames.contains(imageName) && !vmImage.extensions.native.owners.contains(pluginId))
					vmImage.extensions.native.owners << pluginId
				else if (!vmImageNames.contains(imageName))
					vmImage.extensions.native.owners.remove(pluginId)
			}
			response = moabRestService.put("$IMAGES_RESOURCE/$imageName") { vmImage }
			if (response.hasError())
				updateNotificationWarn(message(
						code: "nativeImageTranslator.put.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), VirtualMachineReport, imageName)
		}
	}

	private void updateHypervisorImages(String pluginId, List<NodeReport> nodeReports,
										Map<String, List<String>> hypervisorToVMTable) {
		// Get all including those created by other native plugin instances
		final String getAllMyHypervisorImages =
			'{extensions.native.owners: ["' + pluginId + '"], hypervisorType: "' + HYPERVISOR_TYPE + '"}'

		// Get all hypervisor image names from the current poll.
		Set polledHypervisorNames = nodeReports.collect([] as Set) {it.image}

		// Get all our hypervisor images from the database.
		MoabRestResponse response = moabRestService.get(IMAGES_RESOURCE,
				params: [query: getAllMyHypervisorImages])
		if (response.hasError()) {
			updateNotificationError(message(
					code: "nativeImageTranslator.get.hv.images",
					args: [response.convertedData.messages.join(", ")]), NodeReport)
			return
		}
		List storedHypervisorImages = response.convertedData.results
		Set storedHypervisorNames = storedHypervisorImages.collect([] as Set) {it.name}

		// Get the hypervisors to add to the database.
		Set addSet = polledHypervisorNames - storedHypervisorNames

		// Get the hypervisors to delete from the database.
		// (Hypervisor images are unique per plugin instance.)
		Set deleteSet = storedHypervisorNames - polledHypervisorNames

		// Add as needed.
		addSet.each { String imageName ->
			response = moabRestService.post(IMAGES_RESOURCE) {
				[
						active: true,
						extensions: [native: [owners: [pluginId]]],
						hypervisor: true,
						hypervisorType: HYPERVISOR_TYPE,
						name: imageName,
						osType: IMAGE_OS_TYPE,
						supportsPhysicalMachine: true,
						supportsVirtualMachine: false,
						templateName: null,
						type: null
				]
			}
			if (response.hasError())
				updateNotificationWarn(message(
						code: "nativeImageTranslator.post.hv.image",
						args: [imageName, response.convertedData.messages.join(", ")]), NodeReport, imageName)
		}

		// Delete as needed
		deleteSet.each { String imageName ->
			response = moabRestService.delete("$IMAGES_RESOURCE/$imageName")
			if (response.hasError())
				updateNotificationWarn(message(
						code: "nativeImageTranslator.delete.hv.image",
						args: [imageName, response.convertedData.messages.join(", ")]), NodeReport, imageName)
		}

		// Get all our hypervisor image from the database again if necessary.
		if (addSet || deleteSet) {
			response = moabRestService.get(IMAGES_RESOURCE,
					params: [query: getAllMyHypervisorImages])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "nativeImageTranslator.get.hv.images",
						args: [response.convertedData.messages.join(", ")]), NodeReport)
				return
			}
			storedHypervisorImages = response.convertedData.results
		}

		// Update the virtualizedImages set in each hypervisor image.
		updateVirtualizedImages(storedHypervisorImages, hypervisorToVMTable)
	}

	private void updateVirtualizedImages(List storedHypervisorImages, Map<String, List<String>> hypervisorToVMTable) {
		storedHypervisorImages.each { storedHypervisorImage ->
			String storedHypervisorImageName = storedHypervisorImage.name
			Set compatibleImages = hypervisorToVMTable[storedHypervisorImages] as Set
			MoabRestResponse response

			// Check whether we need to update the virtualizedImages set of this hypervisor.
			if (storedHypervisorImage.virtualizedImages?.toSet() == compatibleImages)
				return

			// Do the update.
			storedHypervisorImage.virtualizedImages = compatibleImages
			response = moabRestService.put("$IMAGES_RESOURCE/$storedHypervisorImageName") {storedHypervisorImage}
			if (response.hasError()) {
				updateNotificationWarn(message(
						code: "nativeImageTranslator.put.hv.image",
						args: [storedHypervisorImageName, response.convertedData.messages.join(", ")]),
						NodeReport, storedHypervisorImageName)
			}
		}
	}

	private void updateNotificationWarn(String message, Class reportClass, String objectName=null) {
		log.warn(message)
		updateNotification(message, reportClass, objectName)
	}

	private void updateNotificationError(String message, Class reportClass, String objectName=null) {
		log.error(message)
		updateNotification(message, reportClass, objectName)
	}

	private void updateNotification(String message, Class reportClass, String objectName=null) {
		def objectType = null
		switch(reportClass) {
			case VirtualMachineReport:
				objectType = "VM"
				break
			case NodeReport:
				objectType = "Node"
				break
			default:
				break
		}
		pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				message,
				objectType ? new IPluginEventService.AssociatedObject(type:objectType, id:objectName) : null,
				null)
	}
}
