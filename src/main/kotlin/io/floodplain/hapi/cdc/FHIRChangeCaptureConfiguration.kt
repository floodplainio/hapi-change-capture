package io.floodplain.hapi.cdc

import io.floodplain.hapi.cdc.impl.FHIRCDCChangeListener
import io.floodplain.hapi.cdc.publish.MessagePublisher
import io.floodplain.hapi.cdc.publish.kafka.KafkaPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class FHIRChangeCaptureConfiguration {

    @Bean
    fun createCDCListener(): FHIRCDCChangeListener {
        return FHIRCDCChangeListener()
    }

    @Bean
    fun createPublisher(): MessagePublisher {
        return KafkaPublisher()
    }
}