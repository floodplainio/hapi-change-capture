package io.floodplain.hapi.sample

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Patient
import java.io.File

class CreateSampleData

fun main(args: Array<String>) {
    listPatients()
}

fun listPatients(): List<Patient> {
    val context = FhirContext.forR4()
    val parser: IParser = context.newJsonParser()
    var baseUrl = "http://localhost:8080/fhir"
    val client = context.restfulClientFactory.newGenericClient(baseUrl)
    File("/Users/f.lyaruu/Downloads/fhir/").walkTopDown()
        .filter { it.isFile }
        .forEach {
            val resource = parser.parseResource(it.inputStream())
            val bundle = resource as Bundle
            bundle.entry.forEach {
                println("type: ${it.resource.resourceType.name}")
                client.create().resource(it.resource).execute()
                //            it.resource
            }
            println(it)
        }
    return listOf()
}