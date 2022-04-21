import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.5.6"
	id("io.spring.dependency-management") version "1.0.11.RELEASE"
	kotlin("jvm") version "1.5.31"
	kotlin("plugin.spring") version "1.5.31"
	signing
	`maven-publish`
	id("org.jetbrains.dokka") version "1.4.32"
}

group = "io.floodplain"
version = "0.0.14-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

fun isReleaseVersion(): Boolean {
	return !project.version.toString().endsWith("SNAPSHOT")
}

repositories {
	mavenCentral()
	mavenLocal()
	gradlePluginPortal()
}

dependencies {
//	implementation("org.springframework.boot:spring-context")

//	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.apache.commons:commons-compress:1.21")
	implementation("org.springframework:spring-context-support")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.springframework.kafka:spring-kafka:2.8.0")

	implementation("ca.uhn.hapi.fhir:org.hl7.fhir.r4:${project.property("hapiFhirVersion")}")
	implementation("org.slf4j:slf4j-api")
//	implementation("com.fasterxml.jackson.core:jackson-databind:2.8.9")
	testImplementation("org.eclipse.jetty:jetty-server")
	testImplementation("org.eclipse.jetty:jetty-util")

	implementation("ca.uhn.hapi.fhir:hapi-fhir-base:${project.property("hapiFhirVersion")}")
	testImplementation("ca.uhn.hapi.fhir:hapi-fhir-jaxrsserver-base:${project.property("hapiFhirVersion")}")

//	compile 'ca.uhn.hapi.fhir:hapi-fhir-structures-dstu2:${project.version}'
	implementation ("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:${project.property("hapiFhirVersion")}") // only needed for loading
	testImplementation("org.springframework.boot:spring-boot-starter-test")
//	testImplementation("org.springframework.kafka:spring-kafka-test")
	implementation("org.springframework.boot:spring-boot-starter-jersey")
	compileOnly("org.springframework.boot:spring-boot-starter-web")
	testImplementation("org.springframework.boot:spring-boot-starter-jetty")

	implementation("ca.uhn.hapi.fhir:hapi-fhir-test-utilities:${project.property("hapiFhirVersion")}")

}

fun customizePom(publication: MavenPublication) {
	with(publication.pom) {
		withXml {
			val root = asNode()
			root.appendNode("name", "Floodplain")
			root.appendNode("description", "Change Capture for HAPI")
			root.appendNode("url", "https://floodplain.io")
		}
		organization {
			name.set("Floodplain")
			url.set("https://floodplain.io")
		}
		issueManagement {
			system.set("GitHub")
			url.set("https://github.com/floodplainio/hapi-change-capture/issues")
		}
		licenses {
			license {
				name.set("Apache License 2.0")
				url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
				distribution.set("repo")
			}
		}
		developers {
			developer {
				id.set("flyaruu")
				name.set("Frank Lyaruu")
				email.set("flyaruu@gmail.com")
			}
		}
		scm {
			url.set("https://github.com/floodplainio/floodplainio/hapi-change-capture")
			connection.set("scm:git:git://github.com/floodplainio/hapi-change-capture.git")
			developerConnection.set("scm:git:ssh://git@github.com:floodplainio/hapi-change-capture.git")
		}
	}
}

//val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)
tasks.dokkaHtml.configure {
	outputDirectory.set(buildDir.resolve("dokka"))
}

tasks {


	val sourcesJar by creating(Jar::class) {
		archiveClassifier.set("sources")
		from(sourceSets.main.get().allSource)
	}

	val dokkaJar by creating(Jar::class) {
		dependsOn.add(dokkaHtml)
		archiveClassifier.set("dokka")
		from(dokkaHtml.get())
	}

	artifacts {
		archives(sourcesJar)
		archives(dokkaJar)
		archives(jar)
	}
}

//tasks.withType<GenerateMavenPom> {
//	signArchives.dependsOn it
//			signArchives.sign it.outputs.files.singleFile
//}

publishing {
	publications {
		create<MavenPublication>(project.name) {
			customizePom(this@create)
			groupId = "io.floodplain"
			artifactId = project.name
			from(components["java"])
			val sourcesJar by tasks
			val dokkaJar by tasks

			artifact(sourcesJar)
			artifact(dokkaJar)
		}
	}
	repositories {
		maven {
			name = "Snapshots"
			url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
			credentials {
				username = (project.findProperty("gpr.user") ?: System.getenv("CENTRAL_USERNAME") ?: "") as String
				password = (project.findProperty("gpr.key") ?: System.getenv("CENTRAL_PASSWORD") ?: "") as String
			}
		}
		maven {
			name = "Staging"
			url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
			credentials {
				username = (project.findProperty("gpr.user") ?: System.getenv("CENTRAL_USERNAME") ?: "") as String
				password = (project.findProperty("gpr.key") ?: System.getenv("CENTRAL_PASSWORD") ?: "") as String
			}
		}
	}
}

//apply(plugin = "signing")
signing {
	if (isReleaseVersion()) {
		sign(publishing.publications[project.name])
	}
}


tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}



tasks.jar {
	manifest {
		attributes(mapOf("Implementation-Title" to project.name,
			"Implementation-Version" to project.version))
	}
}

