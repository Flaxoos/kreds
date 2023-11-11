import org.gradle.internal.impldep.org.bouncycastle.cms.RecipientId.password

/*
 *  Copyright (C) 2021 Abhijith Shivaswamy
 *   See the notice.md file distributed with this work for additional
 *   information regarding copyright ownership.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

println("============================")
println("Gradle Running on Java: ${JavaVersion.current()}")
println("============================")

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.22.0")
    }
}
apply(plugin = "kotlinx-atomicfu")

plugins {
    kotlin("multiplatform") version "1.9.0"
    id("org.jetbrains.dokka") version "1.8.20"
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    `java-library`
    `maven-publish`
    signing
    id("com.dorongold.task-tree") version "2.1.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    jacoco
    id("io.kotest.multiplatform") version "5.6.2"
}

group = "io.github.crackthecodeabhi"
version = "0.9.0"

repositories {
    mavenCentral()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots") }
}

jacoco {
    toolVersion = "0.8.7"
    reportsDirectory.set(layout.buildDirectory.dir("reports/jacoco"))
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(false)
        xml.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getProperty("SONATYPE_USERNAME"))
            password.set(System.getProperty("SONATYPE_PASSWORD"))
        }
    }
}
kotlin {

    targets {
        jvm()
        listOf(linuxX64(), /*linuxArm64(),*/ macosX64(), macosArm64()).forEach {
            it.apply {
                binaries {
                    sharedLib {
                        baseName = "ktor"
                    }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("io.github.microutils:kotlin-logging:3.0.4")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.0")
                implementation("io.ktor:ktor-network:2.3.5")
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("com.ionspin.kotlin:bignum:0.3.9-SNAPSHOT")

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
//                detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-framework-engine:5.6.2")
                implementation("io.kotest:kotest-assertions-core:5.5.4")
                implementation("net.swiftzer.semver:semver:1.2.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.netty:netty-codec-redis:4.1.86.Final")
                implementation("io.netty:netty-handler:4.1.91.Final")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
                implementation("ch.qos.logback:logback-classic:1.4.7")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
            }
        }
    }

    jvm {
        withJava()
    }

    explicitApi()

    jvmToolchain(11)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
    systemProperty("REDIS_PORT", System.getProperty("REDIS_PORT") ?: "6379")
}

tasks.withType(JavaCompile::class) {
    targetCompatibility = "11"
    sourceCompatibility = "17"
}

tasks {
    register<Jar>("dokkaJar") {
        from(dokkaHtml)
        dependsOn(dokkaHtml)
        archiveClassifier.set("javadoc")
    }
    withType<Jar> {
        metaInf.with(
            copySpec {
                from("${project.rootDir}/LICENSE")
            }
        )
    }
    afterEvaluate {
        check {
            dependsOn(withType<io.gitlab.arturbosch.detekt.Detekt>())
        }
    }

    /*withType<PublishToMavenRepository>{
        doFirst {
            val sonaTypeUsername = System.getProperty("SONATYPE_USERNAME")
            val sonaTypePassword = System.getProperty("SONATYPE_PASSWORD")
            if(sonaTypeUsername == null || sonaTypePassword == null || sonaTypeUsername.isBlank() || sonaTypePassword.isBlank())
                throw GradleException("Did you forget to provide SONATYPE_USERNAME || SONATYPE_PASSWORD as System property?")
            println("sonaTypeUsername Length = ${sonaTypeUsername.length}, sonaTypePassword Length =  ${sonaTypePassword.length} ")

            val privateKey = System.getProperty("GPG_PRIVATE_KEY")
            val privatePassword = System.getProperty("GPG_PRIVATE_PASSWORD")
            if(privateKey == null || privatePassword == null || privateKey.isBlank() || privatePassword.isBlank())
                throw GradleException("Did you forget to provide GPG_PRIVATE_KEY || GPG_PRIVATE_PASSWORD as System property?")
            println("GPG_PRIVATE_KEY Length = ${privateKey.length}, GPG_PRIVATE_PASSWORD Length = ${privatePassword.length}")
        }
    }*/
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("Kreds")
                description.set("A Non-blocking Redis client for Kotlin based on coroutines.")
                url.set("https://github.com/crackthecodeabhi/kreds")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("abhi")
                        name.set("Abhijith Shivaswamy")
                        email.set("abs@abhijith.page")
                        url.set("abhijith.page")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/crackthecodeabhi/kreds.git")
                    developerConnection.set("scm:git:ssh://github.com/crackthecodeabhi/kreds.git")
                    url.set("https://github.com/crackthecodeabhi/kreds/tree/master")
                }
            }
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            from(components["java"])
            artifacts {
                artifact(tasks["dokkaJar"])
//                artifact(tasks.kotlinSourcesJar) {
//                    classifier = "sources"
//                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        System.getProperty("GPG_PRIVATE_KEY"),
        System.getProperty("GPG_PRIVATE_PASSWORD")
    )
    sign(publishing.publications)
}

detekt {
    buildUponDefaultConfig = true
    config = files(rootDir.resolve("detekt.yml"))
    parallel = true
    ignoreFailures = true
    reports {
        html.enabled = false
        txt.enabled = false
    }
}
