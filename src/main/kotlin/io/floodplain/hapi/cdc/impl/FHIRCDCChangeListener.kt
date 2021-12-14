package io.floodplain.hapi.cdc.impl

import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.interceptor.ServerOperationInterceptorAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import io.floodplain.hapi.cdc.publish.Message
import io.floodplain.hapi.cdc.publish.MessagePublisher
import org.hl7.fhir.instance.model.api.IBaseResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


enum class ChangeMode {
    SNAPSHOT,
    UPDATE,
    INSERT,
    DELETE;

    override fun toString(): String {
        return when (this) {
            SNAPSHOT -> "r"
            UPDATE -> "u"
            INSERT -> "c"
            DELETE -> "d"
        }
    }
}

/**
 * Intercepts changes, will publish messages to the appropriate endpoints
 */
@Service
@Interceptor
class FHIRCDCChangeListener : ServerOperationInterceptorAdapter() {
    private var logger: Logger = LoggerFactory.getLogger(FHIRCDCChangeListener::class.java)
    private val objectMapper = ObjectMapper()

    @Autowired
    lateinit var messagePublisher: MessagePublisher

    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
    override fun resourceUpdated(request: RequestDetails, oldResource: IBaseResource?, newResource: IBaseResource) {
        logger.info("Updating resource $oldResource, new resource '$newResource'")
        val key = newResource.idElement.idPart
        val beforePayload =
            request.fhirContext.newJsonParser().encodeResourceToString(oldResource).toByteArray(Charsets.UTF_8)
        val afterPayload =
            request.fhirContext.newJsonParser().encodeResourceToString(newResource).toByteArray(Charsets.UTF_8)
        publishCDCMessage(cdcTopicName(newResource.fhirType()), key, beforePayload, afterPayload, ChangeMode.UPDATE)
    }

    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_DELETED)
    override fun resourceDeleted(request: RequestDetails, theResource: IBaseResource?) {
        val resource = theResource!!
        val key = theResource.idElement.idPart
        val cdcTopic = cdcTopicName(theResource.fhirType())
        logger.info("Deleting resource with key: '$key' in the topic '$cdcTopic'")
        val jsonPayload =
            request.fhirContext.newJsonParser().encodeResourceToString(theResource).toByteArray(Charsets.UTF_8)
        publishCDCMessage(cdcTopic, key, jsonPayload, null, ChangeMode.DELETE)
        // write tombstone
        messagePublisher.delete(cdcTopicName(theResource.fhirType()), key)
    }

    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
    override fun resourceCreated(request: RequestDetails, resource: IBaseResource) {
        logger.info("Creating resource: $resource")
        val key = resource.idElement.idPart
        val jsonPayload =
            request.fhirContext.newJsonParser().encodeResourceToString(resource).toByteArray(Charsets.UTF_8)
        publishCDCMessage(cdcTopicName(resource.fhirType()), key, null, jsonPayload, ChangeMode.INSERT)
    }

    fun createCDCMessage(topic: String, key: String, before: ByteArray?, after: ByteArray?, mode: ChangeMode): Message {
        val node = objectMapper.createObjectNode()
        node.replace("before", before?.let { a -> objectMapper.readTree(a) } ?: objectMapper.nullNode())
        node.replace("after", after?.let { a -> objectMapper.readTree(a) } ?: objectMapper.nullNode())
        node.put("op", mode.toString())
        return Message(topic, key, objectMapper.writeValueAsBytes(node))
    }

    private fun publishCDCMessage(topic: String, key: String, before: ByteArray?, after: ByteArray?, mode: ChangeMode) {
        val message = createCDCMessage(topic, key, before, after, mode)
        if (message.body != null) {
            messagePublisher.publish(topic, key, message.body)
        } else {
            messagePublisher.delete(topic, key)
        }
    }

    fun cdcTopicName(resourceType: String): String = "FHIRCDC-$resourceType"
}
