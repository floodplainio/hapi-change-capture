package io.floodplain.hapi.cdc.impl

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor
import ca.uhn.fhir.util.BundleUtil
import io.floodplain.hapi.cdc.publish.Message
import io.floodplain.hapi.cdc.publish.MessagePublisher
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.net.URL
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.Path

@Service
@Path("/snapshot")
@RestController
class FHIRSnapshot :
    HttpServlet() {
    private var logger: Logger = LoggerFactory.getLogger(FHIRSnapshot::class.java)

    @Autowired
    lateinit var fhirContext: FhirContext

    @Autowired
    lateinit var messagePublisher: MessagePublisher

    @Autowired
    lateinit var cdcChangeListener: FHIRCDCChangeListener

    enum class ResourceTypes {
        Appointment,
        Account,
        Invoice,
        CatalogEntry,
        EventDefinition,
        DocumentManifest,
        MessageDefinition,
        Goal,
        MedicinalProductPackaged,
        Endpoint,
        EnrollmentRequest,
        Consent,
        CapabilityStatement,
        Measure,
        Medication,
        ResearchSubject,
        Subscription,
        DocumentReference,
        GraphDefinition,
        Parameters,
        CoverageEligibilityResponse,
        MeasureReport,
        PractitionerRole,
        SubstanceReferenceInformation,
        RelatedPerson,
        ServiceRequest,
        SupplyRequest,
        Practitioner,
        VerificationResult,
        SubstanceProtein,
        BodyStructure,
        Slot,
        Contract,
        Person,
        RiskAssessment,
        Group,
        PaymentNotice,
        ResearchDefinition,
        MedicinalProductManufactured,
        Organization,
        CareTeam,
        ImplementationGuide,
        ImagingStudy,
        FamilyMemberHistory,
        ChargeItem,
        ResearchElementDefinition,
        ObservationDefinition,
        Encounter,
        Substance,
        SubstanceSpecification,
        SearchParameter,
        ActivityDefinition,
        Communication,
        InsurancePlan,
        Linkage,
        SubstanceSourceMaterial,
        ImmunizationEvaluation,
        DeviceUseStatement,
        RequestGroup,
        DeviceRequest,
        MessageHeader,
        ImmunizationRecommendation,
        Provenance,
        Task,
        Questionnaire,
        ExplanationOfBenefit,
        MedicinalProductPharmaceutical,
        ResearchStudy,
        Specimen,
        AllergyIntolerance,
        CarePlan,
        StructureDefinition,
        ChargeItemDefinition,
        EpisodeOfCare,
        OperationOutcome,
        Procedure,
        ConceptMap,
        OperationDefinition,
        ValueSet,
        Immunization,
        MedicationRequest,
        EffectEvidenceSynthesis,
        BiologicallyDerivedProduct,
        Device,
        VisionPrescription,
        Media,
        MedicinalProductContraindication,
        EvidenceVariable,
        MolecularSequence,
        MedicinalProduct,
        DeviceMetric,
        CodeSystem,
        Flag,
        SubstanceNucleicAcid,
        RiskEvidenceSynthesis,
        AppointmentResponse,
        StructureMap,
        AdverseEvent,
        GuidanceResponse,
        Observation,
        MedicationAdministration,
        EnrollmentResponse,
        Binary,
        Library,
        MedicinalProductInteraction,
        MedicationStatement,
        CommunicationRequest,
        TestScript,
        Basic,
        SubstancePolymer,
        TestReport,
        ClaimResponse,
        MedicationDispense,
        DiagnosticReport,
        OrganizationAffiliation,
        HealthcareService,
        MedicinalProductIndication,
        NutritionOrder,
        TerminologyCapabilities,
        Evidence,
        AuditEvent,
        PaymentReconciliation,
        Condition,
        SpecimenDefinition,
        Composition,
        DetectedIssue,
        Bundle,
        CompartmentDefinition,
        MedicationKnowledge,
        MedicinalProductIngredient,
        Patient,
        Coverage,
        QuestionnaireResponse,
        CoverageEligibilityRequest,
        NamingSystem,
        MedicinalProductUndesirableEffect,
        ExampleScenario,
        Schedule,
        SupplyDelivery,
        ClinicalImpression,
        DeviceDefinition,
        PlanDefinition,
        MedicinalProductAuthorization,
        Claim,
        Location,
    }

    override fun doPut(req: HttpServletRequest, resp: HttpServletResponse) {
        val auth: String? = req.getHeader("Authorization")
        val fromName = req.getParameter("fromName")
        // strip first char (/)
        val resourceType = req.pathInfo?.substring(1)
        listResourceType(auth, resourceType, req, fromName)
        resp.writer.write("ok")
    }

    @PutMapping("/snapshot/{resourceType}", "/snapshot")
    fun listResourceType(
        @RequestHeader("Authorization", required = false) authHeader: String?,
        @PathVariable resourceType: String?,
        request: HttpServletRequest,
        @RequestParam(required = false) fromName: String?
    ): String {
        val uri = URL(request.requestURL.toString())
        val incomingPort = uri.port
        val resourceTypes = resourcesByName(resourceType, fromName)
        fhirContext.restfulClientFactory.socketTimeout = 30 * 1000
        val clnt = fhirContext.newRestfulGenericClient("http://localhost:$incomingPort/fhir")
        // Attach the current token to the request
        authHeader?.let {
            val token = it.split(" ")[1]
            val authInterceptor = BearerTokenAuthInterceptor(token)
            clnt.registerInterceptor(authInterceptor)
        }

        // Create a parser to serialize the message, pretty print could be turned off to save space but makes
        // Kafka dumps so much more readable
        val parser: IParser = fhirContext.newJsonParser()
        parser.setPrettyPrint(true)

        val resourceTypeCount = resourceTypes.size
        var count = 1
        resourceTypes.forEach {
            logger.debug("Publishing all items for type: ${it.name} type # ${count++}/$resourceTypeCount")
            publishAllForResource(it, clnt, parser)
            logger.debug("Publishing complete for type ${it.name}")
        }
        return "ok"
    }

    @Suppress("UNCHECKED_CAST")
    private fun resourcesByName(resourceTypePath: String?, fromName: String?): List<Class<IBaseResource>> {
        // treat empty string as null
        var resourceType = if (resourceTypePath == "") null else resourceTypePath
        if (resourceType != null) {
            return listOf(Class.forName("org.hl7.fhir.r4.model.$resourceType") as Class<IBaseResource>)
        }
        return ResourceTypes.values()
            .filter { it.name > (fromName ?: "") }
            .map {
                Class.forName("org.hl7.fhir.r4.model.${it.name}") as Class<IBaseResource>
            }.toList()
    }

    private fun publishAllForResource(resourceType: Class<IBaseResource>, client: IGenericClient, parser: IParser) {
        var bundle = client.search<IBaseBundle>()
            .forResource(resourceType)
            .returnBundle(Bundle::class.java)
            .execute()
        publishCDCBundle(bundle, parser)
        var nextPageLink = bundle.getLink(IBaseBundle.LINK_NEXT)
        while (nextPageLink != null) {
            bundle = client
                .loadPage()
                .next(bundle)
                .execute()
            publishCDCBundle(bundle, parser)
            nextPageLink = bundle.getLink(IBaseBundle.LINK_NEXT)
        }
    }

    private fun publishCDCBundle(bundle: Bundle, parser: IParser) {
        val resourceList = BundleUtil.toListOfResources(fhirContext, bundle)
        logger.debug("Number of resources for page: ${resourceList.size}")
        val messages = resourceList.flatMap {
            val key = it.idElement.idPart
            val fhirType = it.idElement.resourceType
            listOf(
                cdcChangeListener.createCDCMessage(
                    cdcChangeListener.cdcTopicName(fhirType),
                    key,
                    null,
                    parser.encodeResourceToString(it).toByteArray(),
                    ChangeMode.SNAPSHOT
                ),
                Message(
                    cdcChangeListener.cdcTopicName(fhirType),
                    key,
                    parser.encodeResourceToString(it).toByteArray()
                )
            )
        }
        messagePublisher.publishAll(messages)
    }
}
