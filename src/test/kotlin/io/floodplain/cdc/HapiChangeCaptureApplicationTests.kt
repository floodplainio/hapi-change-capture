package io.floodplain.cdc

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.RestfulServer
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails
import io.floodplain.cdc.publish.InMemoryPublisher
import io.floodplain.hapi.cdc.impl.FHIRCDCChangeListener
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Patient
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("local")
class HapiChangeCaptureApplicationTests {

    fun setup() {

    }

    @Test // Test direct changes to interceptor
    fun testFHIRChange() {
        val ctx = FhirContext.forR4()
        val publisher = InMemoryPublisher()
        val cdc = FHIRCDCChangeListener()
        cdc.messagePublisher = publisher
        val server = RestfulServer(ctx)
        server.init()
        val request = ServletRequestDetails()
        request.server = server
        val patient = Patient().addName(HumanName().addGiven("John").setFamily("Doe")).setId("123")
        cdc.resourceCreated(request, patient)
//		println(String(publisher.messages.first().body!!))
        cdc.resourceDeleted(request, patient)
//		println(String(publisher.messages[1].body!!))
    }

    @Test // Test direct changes to interceptor
    fun testFHIRChange2() {
        val ctx = FhirContext.forR4()
        val publisher = InMemoryPublisher()
        val cdc = FHIRCDCChangeListener()
        cdc.messagePublisher = publisher
        val server = RestfulServer(ctx)
        server.registerInterceptor(cdc)
        server.init()
        val request = ServletRequestDetails()
        request.server = server
    }

    @Test
    fun contextLoads() {
        val ctx = FhirContext.forR4()
        val e = RestfulServer(ctx)
        val cdc = FHIRCDCChangeListener()
        cdc.messagePublisher = InMemoryPublisher()
        e.registerInterceptor(cdc)
        e.registerInterceptor(ResponseHighlighterInterceptor())
        e.init()
    }

}
