package com.adaptc.mws.plugins.natives

import com.adaptc.mws.plugins.*

/**
 * @author bsaville
 */
public class ImageNativeTranslator {
	private static final String IMAGES_RESOURCE = "/rest/images"
	private static final String IMAGE_TYPE = "FULL_CLONE"
	private static final String IMAGE_OS_TYPE = "unknown"
	private static final String DEFAULT_HYPERVISOR_TYPE = "Native"

	IMoabRestService moabRestService
	IPluginEventService pluginEventService

	public void updateImages(String pluginId, AggregateImagesInfo aggregateImagesInfo) {
		// Update all VM images, including those just reported by nodes
		updateVMImages(pluginId, aggregateImagesInfo.vmImages*.name +
				aggregateImagesInfo.hypervisorImages.collectMany { it.vmImageNames })
		updateHypervisorImages(pluginId, aggregateImagesInfo.hypervisorImages)
	}

	private void updateVMImages(String pluginId, List<String> vmImages) {
		final String getAllMyVMImages =
			'{extensions.native: {$ne: null}, hypervisor: false}'

		// Get all VM image names
		Set vmImageNames = vmImages.collect([] as Set) {it} - [null]

		// Get all our images from the database.
		MoabRestResponse response = moabRestService.get(IMAGES_RESOURCE,
				params: [
						query: getAllMyVMImages,
						fields: "name,extensions.native",
						'api-version': 'latest']
		)
		if (response.hasError()) {
			updateNotificationError(message(
					code: "imageNativeTranslator.get.vm.images",
					args: [response.convertedData?.messages?.join(", ")]))
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
			response = moabRestService.post(IMAGES_RESOURCE, params: ['api-version': 'latest']) {
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
						code: "imageNativeTranslator.post.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
		}

		// Delete as needed.
		deleteSet.each { String imageName ->

			// Get the ID of this VM image.
			response = moabRestService.get("$IMAGES_RESOURCE/$imageName",
					params: [fields: "id", 'api-version': 'latest'])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "imageNativeTranslator.get.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
				return
			}
			String id = response.convertedData.id

			// Find all hypervisor images that refer to this VM image.
			response = moabRestService.get(IMAGES_RESOURCE,
					params: [query: '{virtualizedImages.id: "' + id + '"}', 'api-version': 'latest'])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "imageNativeTranslator.get.hv.images",
						args: [response.convertedData.messages.join(", ")]))
				return
			}
			List hypervisorImages = response.convertedData.results

			// Remove the VM image reference from each hypervisor image.
			hypervisorImages.each { hypervisorImage ->
				hypervisorImage.virtualizedImages.removeAll { it.id == id }
				response = moabRestService.put("$IMAGES_RESOURCE/${hypervisorImage.name}", params: ['api-version': 'latest']) {hypervisorImage}
				if (response.hasError())
					updateNotificationWarn(message(
							code: "imageNativeTranslator.put.hv.image",
							args: [hypervisorImage.name, response.convertedData.messages.join(", ")]),
							hypervisorImage.name)
			}

			// Delete the VM image.
			response = moabRestService.delete("$IMAGES_RESOURCE/$imageName", params: ['api-version': 'latest'])
			if (response.hasError())
				updateNotificationWarn(message(
						code: "imageNativeTranslator.delete.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
		}

		// Modify the image's owners list to include or remove this plugin
		updateOwnersSet.each { String imageName ->
			response = moabRestService.get("$IMAGES_RESOURCE/$imageName", params: ['api-version': 'latest'])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "imageNativeTranslator.get.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
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
			response = moabRestService.put("$IMAGES_RESOURCE/$imageName", params: ['api-version': 'latest']) { vmImage }
			if (response.hasError())
				updateNotificationWarn(message(
						code: "imageNativeTranslator.put.vm.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
		}
	}

	private void updateHypervisorImages(String pluginId, List<HVImageInfo> hypervisorImages) {
		final String getAllMyHypervisorImages =
			'{extensions.native.owners: ["' + pluginId + '"], hypervisor:true}'

		// Get all hypervisor image names from the current poll.
		Set polledHypervisorNames = hypervisorImages.collect([] as Set) {it.name} - [null]

		// Get all our hypervisor images from the database.
		MoabRestResponse response = moabRestService.get(IMAGES_RESOURCE,
				params: [query: getAllMyHypervisorImages, 'api-version': 'latest'])
		if (response.hasError()) {
			updateNotificationError(message(
					code: "imageNativeTranslator.get.hv.images",
					args: [response.convertedData.messages.join(", ")]))
			return
		}
		List storedHypervisorImages = response.convertedData.results
		Set storedHypervisorNames = storedHypervisorImages.collect([] as Set) {it.name}

		// Get the hypervisors to add to the database
		Set addSet = polledHypervisorNames - storedHypervisorNames

		// Get the hypervisors to delete from the database.
		// (Hypervisor images are unique per plugin instance.)
		Set deleteSet = storedHypervisorNames - polledHypervisorNames

		// Add as needed
		def hypervisorTypeCache = [:]
		addSet.each { String imageName ->
			// Set hypervisorType from the cache, then from the image info,
			// 	and finally a default hardcoded value if nothing else
			def hypervisorType = hypervisorTypeCache[imageName] ?:
				hypervisorImages.find { it.name==imageName }?.hypervisorType ?:
					DEFAULT_HYPERVISOR_TYPE
			if (!hypervisorTypeCache.containsKey(hypervisorType)) {
				hypervisorTypeCache[imageName] = hypervisorType
				log.debug("Using hypervisor type ${hypervisorType} for image ${imageName}")
			}
			response = moabRestService.post(IMAGES_RESOURCE, params: ['api-version': 'latest']) {
				[
						active: true,
						extensions: [native: [owners: [pluginId]]],
						hypervisor: true,
						hypervisorType: hypervisorType,
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
						code: "imageNativeTranslator.post.hv.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
		}

		// Delete as needed
		deleteSet.each { String imageName ->
			response = moabRestService.delete("$IMAGES_RESOURCE/$imageName", params: ['api-version': 'latest'])
			if (response.hasError())
				updateNotificationWarn(message(
						code: "imageNativeTranslator.delete.hv.image",
						args: [imageName, response.convertedData.messages.join(", ")]), imageName)
		}

		// Get all our hypervisor image from the database again if necessary.
		if (addSet || deleteSet) {
			response = moabRestService.get(IMAGES_RESOURCE,
					params: [query: getAllMyHypervisorImages, 'api-version': 'latest'])
			if (response.hasError()) {
				updateNotificationError(message(
						code: "imageNativeTranslator.get.hv.images",
						args: [response.convertedData.messages.join(", ")]))
				return
			}
			storedHypervisorImages = response.convertedData.results
		}

		// Update the virtualizedImages set in each hypervisor image.
		updateVirtualizedImages(storedHypervisorImages, hypervisorImages)
	}

	private void updateVirtualizedImages(List storedHypervisorImages, List<HVImageInfo> hypervisorImages) {
		storedHypervisorImages.each { storedHypervisorImage ->
			String storedHypervisorImageName = storedHypervisorImage.name

			// Get VM images
			Set compatibleImages	// This is set first to a list of strings and then to a list of virtualized images
			hypervisorImages.each { HVImageInfo imageInfo ->
				if (imageInfo.name!=storedHypervisorImageName)
					return
				if (compatibleImages==null)
					compatibleImages = imageInfo.vmImageNames as Set
				else if (compatibleImages!=(imageInfo.vmImageNames as Set)) {
					updateNotificationWarn(message(code:"imageNativeTranslator.mismatched.vm.image.sets", args:[imageInfo.name]),
						imageInfo.nodeName, "Node")
				}
			}
			if (!compatibleImages)
				compatibleImages = [] as Set

			MoabRestResponse response
			if (compatibleImages) {
				// Build the query to find all our VM image IDs that are compatible with this image
				String query = '{extensions.native: {$ne: null}, ' +
						'name:{$in: ["' + compatibleImages.join('", "') + '"]}}'

				// Run the query.
				response = moabRestService.get(IMAGES_RESOURCE,
						params: [query: query, fields: "id", 'api-version': 'latest'])
				if (response.hasError()) {
					updateNotificationError(message(
							code: "imageNativeTranslator.get.vm.images",
							args: [response.convertedData.messages.join(", ")]))
					return
				}
				compatibleImages = response.convertedData.results
			}

			// Check whether we need to update the virtualizedImages set of this hypervisor
			log.debug("Comparing stored image VIs (${storedHypervisorImage.virtualizedImages}) to compatible images (${compatibleImages?.toSet()})")
			if (storedHypervisorImage.virtualizedImages?.toSet() == compatibleImages?.toSet())
				return

			// Do the update
			storedHypervisorImage.virtualizedImages = compatibleImages
			response = moabRestService.put("$IMAGES_RESOURCE/$storedHypervisorImageName", params: ['api-version': 'latest']) {storedHypervisorImage}
			if (response.hasError()) {
				updateNotificationWarn(message(
						code: "imageNativeTranslator.put.hv.image",
						args: [storedHypervisorImageName, response.convertedData.messages.join(", ")]),
					storedHypervisorImageName)
			}
		}
	}

	private void updateNotificationWarn(String message, String objectName=null, String objectType="Image") {
		log.warn(message)
		updateNotification(message, objectName, objectType)
	}

	private void updateNotificationError(String message, String objectName=null, String objectType="Image") {
		log.error(message)
		updateNotification(message, objectName, objectType)
	}

	private void updateNotification(String message, String objectName, String objectType) {
		pluginEventService.updateNotificationCondition(IPluginEventService.EscalationLevel.ADMIN,
				message,
				objectName==null ? null : new IPluginEventService.AssociatedObject(type:objectType, id:objectName),
				null)
	}
}
