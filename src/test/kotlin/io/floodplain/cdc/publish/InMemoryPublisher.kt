package io.floodplain.cdc.publish

import io.floodplain.hapi.cdc.publish.Message
import io.floodplain.hapi.cdc.publish.MessagePublisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.PostConstruct

@Service
class InMemoryPublisher : MessagePublisher {
    private var logger: Logger = LoggerFactory.getLogger(InMemoryPublisher::class.java)

    val messages = mutableListOf<Message>()
    private val updateCounter = AtomicLong(0)
    private val deleteCounter = AtomicLong(0)

    @PostConstruct
    fun initialize() {
        logger.info("Initialize Noop Publisher")
    }

    override fun updateCount(): Long {
        return updateCounter.get()
    }

    override fun deleteCount(): Long {
        return deleteCounter.get()
    }

    override fun publish(topic: String, key: String, payload: ByteArray) {
        messages.add(Message(topic, key, payload))
        updateCounter.incrementAndGet()
    }

    override fun publishAll(messages: List<Message>) {
        this.messages.addAll(messages)
        updateCounter.addAndGet(messages.size.toLong())
    }

    override fun delete(topic: String, key: String) {
        this.messages.add(Message(topic, key, null))
        deleteCounter.incrementAndGet()
    }
}
