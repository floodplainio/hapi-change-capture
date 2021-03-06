package hapi;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.DeleteCascadeModeEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.provider.HashMapResourceProvider;
import ca.uhn.fhir.test.utilities.JettyUtil;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE;

public class TestDeletePointCut {

    int deleted = 0;
    int updated = 0;
    int created = 0;

    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_DELETED)
    public void resourceDeleted(RequestDetails request, IBaseResource resource) {
        deleted++;
    }

    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
    public void resourceUpdated(RequestDetails request, IBaseResource oldResource, IBaseResource newResource) {
        updated++;
    }

    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
    public void resourceCreated(RequestDetails request, IBaseResource resource) {
        created++;
    }

    @Test
    public void testDeleted() throws Exception {
        Server server = new Server(0);
        FhirContext ourCtx = FhirContext.forR4();
        ServletHandler proxyHandler = new ServletHandler();
        RestfulServer servlet = new RestfulServer(ourCtx);

        IResourceProvider patientProvider = new HashMapResourceProvider(ourCtx, Patient.class);
        servlet.setResourceProviders(patientProvider);
        servlet.registerInterceptor(this);

        ServletHolder servletHolder = new ServletHolder(servlet);
        proxyHandler.addServletWithMapping(servletHolder, "/*");
        server.setHandler(proxyHandler);
        JettyUtil.startServer(server);
        int ourPort = JettyUtil.getPortForStartedServer(server);

        ourCtx.getRestfulClientFactory().setSocketTimeout(240 * 1000);
        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        IGenericClient client = ourCtx.newRestfulGenericClient("http://localhost:" + ourPort);

        Resource patient = new Patient().addName(new HumanName().addGiven("John").setFamily("Doe")).setGender(MALE).setId("123");
        MethodOutcome createResult = client.create().resource(patient).execute();
        IIdType createdId = createResult.getId();
//        Bundle b = client.search().forResource(Patient.class).returnBundle(Bundle.class).execute();
//
//        String id = b.getEntry().get(0).getId();

        Resource updatedPatient = new Patient().addName(new HumanName().addGiven("Jane").setFamily("Doe")).setGender(FEMALE).setIdElement((IdType) createdId);
        MethodOutcome updateResult = client.update().resource(updatedPatient).execute();

        client.delete()
                .resourceById(createdId)
                .cascade(DeleteCascadeModeEnum.DELETE)
                .execute();

        System.err.println("created: " + created);
        System.err.println("updated: " + updated);
        System.err.println("deleted: " + deleted);

        Assertions.assertEquals(1, created);
        Assertions.assertEquals(1, updated);
        // ignored for now, filed ticket: https://github.com/hapifhir/hapi-fhir/issues/3204
        // It is only an issue for in-memory servers, does work on servers backed by a database.
        // TODO file merge request to HAPI
//        Assertions.assertEquals(1,deleted);


    }
}
