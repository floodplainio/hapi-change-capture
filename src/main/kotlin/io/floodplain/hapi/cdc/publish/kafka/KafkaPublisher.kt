package io.floodplain.hapi.cdc.publish.kafka

import io.floodplain.hapi.cdc.publish.Message
import io.floodplain.hapi.cdc.publish.MessagePublisher
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.core.env.Environment
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.PostConstruct


@Service
class KafkaPublisher : MessagePublisher {

    private var logger: Logger = LoggerFactory.getLogger(KafkaPublisher::class.java)

    private val updateCounter = AtomicLong(0)
    private val deleteCounter = AtomicLong(0)

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @Autowired
    private lateinit var kafkaProperties: KafkaProperties

    @Autowired
    private val env: Environment? = null

    @Autowired
    private lateinit var kafkaAdmin: KafkaAdmin

    val existingTopics = mutableListOf<String>()

    @PostConstruct
    fun initialize() {
        val consumer = consumerFactory().createConsumer()
        existingTopics.addAll(consumer.listTopics().keys)
    }

    //    @Bean
    fun consumerFactory(): ConsumerFactory<String, ByteArray> {
        return DefaultKafkaConsumerFactory(
            kafkaProperties.buildConsumerProperties(), StringDeserializer(), ByteArrayDeserializer()
        )
    }

    private fun createIfMissing(topic: String) {
        if (!existingTopics.contains(topic)) {
            logger.info("Topic $topic is missing, attempting to create")
            val newTopic = TopicBuilder.name(topic).compact().partitions(1).build()
            kafkaAdmin.createOrModifyTopics(newTopic)
            logger.info("Topic $topic created successfully")
            existingTopics.add(topic)
        }
    }

    override fun updateCount(): Long {
        return updateCounter.get()
    }

    override fun deleteCount(): Long {
        return deleteCounter.incrementAndGet()
    }

    override fun publish(topic: String, key: String, payload: ByteArray) {
        createIfMissing(topic)
        updateCounter.incrementAndGet()
        logger.debug("Publishing to topic $topic and key: $key size: ${payload.size}")
        // block on returned completable
        kafkaTemplate.send(topic, key, payload).completable().get()
        logger.debug("done")
    }

    // More efficient than sending individual messages
    override fun publishAll(messages: List<Message>) {
        updateCounter.addAndGet(messages.size.toLong())
        val result = messages.map { message ->
            createIfMissing(message.topic)
            kafkaTemplate.send(message.topic, message.key, message.body)
        }.lastOrNull()
        // block on last
        result?.get()
        // chatty for now, should reduce later
        logger.debug("successfully sent #${messages.size} messages")
    }

    override fun delete(topic: String, key: String) {
        createIfMissing(topic)
        deleteCounter.incrementAndGet()
        logger.debug("Deleting from topic $topic and key: $key $kafkaTemplate")
        kafkaTemplate.send(topic, key, null).completable().get()
        logger.debug("done")
    }
}
