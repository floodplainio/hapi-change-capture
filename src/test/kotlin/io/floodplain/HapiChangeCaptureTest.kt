package hapi.cdc

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.DeleteCascadeModeEnum
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum
import ca.uhn.fhir.rest.server.RestfulServer
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor
import ca.uhn.fhir.rest.server.provider.HashMapResourceProvider
import ca.uhn.fhir.test.utilities.JettyUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.floodplain.hapi.cdc.FHIRCDCChangeListener
import hapi.cdc.publish.InMemoryPublisher
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.test.context.ActiveProfiles

private val ourServer: Server = Server(0)
private val ourPublisher = InMemoryPublisher()
//private var ourClient: CloseableHttpClient? = null
private val ourCtx = FhirContext.forR4()
//private var ourPort = 0
private var ourInterceptor: IServerInterceptor? = null
private var ourFhirClient: IGenericClient? = null

@ActiveProfiles("local")
class HapiChangeCaptureTest {

    private var logger: Logger = LoggerFactory.getLogger(HapiChangeCaptureTest::class.java)

    @BeforeEach
    fun beforeClass() {
        val proxyHandler = ServletHandler()
        val servlet = RestfulServer(ourCtx)
        val patientProvider = HashMapResourceProvider(ourCtx,Patient::class.java)
        servlet.setResourceProviders(patientProvider)
        ourInterceptor = FHIRCDCChangeListener(ourPublisher)
        servlet.registerInterceptor(ourInterceptor)
//        servlet.plainProviders = PlainProvider()
//        servlet.bundleInclusionRule = BundleInclusionRule.BASED_ON_RESOURCE_PRESENCE
//        servlet.defaultResponseEncoding = EncodingEnum.XML
        val servletHolder = ServletHolder(servlet)
        proxyHandler.addServletWithMapping(servletHolder, "/*")
        ourServer.setHandler(proxyHandler)
        JettyUtil.startServer(ourServer)
        val ourPort = JettyUtil.getPortForStartedServer(ourServer)
//        val connectionManager = PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS)
//        val builder = HttpClientBuilder.create()
//        builder.setConnectionManager(connectionManager)
//        ourClient = builder.build()
        ourCtx.getRestfulClientFactory().setSocketTimeout(240 * 1000)
        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER)
        ourFhirClient = ourCtx.newRestfulGenericClient("http://localhost:$ourPort")
    }

    @AfterEach
    fun shutdown() {
        ourServer.stop()
    }

    @Test
    // Test change data by observing change feed and pushing changes
    fun testCDC() {
        val objectMapper = ObjectMapper()
        // no messages yet
        val client = ourFhirClient!!
        assertEquals(0, ourPublisher.messages.size)
        val patient = Patient().addName(HumanName().addGiven("John").setFamily("Doe")).setGender(MALE).setId("123")
        val createResult = client.create().resource(patient).execute()
        val messages = ourPublisher.messages
        val createdId = createResult.resource.idElement


        // There should be a single message, with a null before and a non-null after in an 'insert' message
        assertEquals(1,messages.size)
        val createBody = objectMapper.readTree(messages.first().body) as ObjectNode
        assertTrue(createBody.has("before"))
        assertTrue(createBody.get("before").isNull)
        assertFalse(createBody.get("after").isNull)

        val updatedPatient = Patient().addName(HumanName().addGiven("Jane").setFamily("Doe")).setGender(FEMALE).setId(createdId)
        val updateResult = client.update().resource(updatedPatient).execute()
        assertEquals(2,messages.size)

        val updateBody = objectMapper.readTree(messages[1].body) as ObjectNode
        logger.info(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updateBody))
        assertFalse(updateBody.get("before").isNull)
        assertFalse(updateBody.get("after").isNull)

        val bundle = client.search<Bundle>()
            .forResource(Patient::class.java)
            .returnBundle(Bundle::class.java)
            .execute()
        val foundPatient = bundle
            .entry
            .get(0)
            .resource as Patient

        val foundId = foundPatient.idElement
        val deleteResult = client.delete()
            .resourceById(foundId)
            .cascade(DeleteCascadeModeEnum.DELETE)
            .execute()
//        client.e
//        assertEquals(3,messages.size)
//        val deleteBody = objectMapper.readTree(messages[2].body) as ObjectNode
//        assertFalse(deleteBody.get("before").isNull)
//        assertTrue(deleteBody.get("after").isNull)

//        val result = ourFhirClient?.update()?.resource(patient)?.execute()

        println(createResult)
//        println(ourPublisher.messages.)
        Thread.sleep(1000000)
    }

}