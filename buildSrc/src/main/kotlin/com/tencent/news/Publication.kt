package com.tencent.news

import isOhosBuild
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import java.net.URI

fun Project.getPublicationVersion(): String {
    val baseVersion = "1.0.2"
    return if (isOhosBuild()) {
        "$baseVersion-ohos"
    } else {
        baseVersion
    }
}

fun getPublicationGroup(): String {
    return "com.tencent.news"
}

private val publicationsToArtifacts = mapOf(
    "kotlinMultiplatform" to "markdown-render",
    "androidRelease" to "markdown-render-android",
    "ohosArm64" to "markdown-render-ohosArm64",
    "iosX64" to "markdown-render-iosx64",
    "iosArm64" to "markdown-render-iosarm64",
    "iosSimulatorArm64" to "markdown-render-iossimulatorarm64",
)

fun Project.registerPublicationsFromKotlinPlugin() {
    for ((publication, artifact) in publicationsToArtifacts) {
        registerPublicationFromKotlinPlugin(publication, artifact)
    }
    configureSonatypePublicationIfNecessary()
}

private fun Project.registerPublicationFromKotlinPlugin(publicationName: String, artifactId: String) {
    configure<PublishingExtension> {
        publications {
            (findByName(publicationName) as? MavenPublication)?.apply {
                this.artifactId = artifactId
                artifact(tasks["javadocJar"])
                println("register publication $this - $publicationName - $artifactId")
                configurePom()
            }
        }
    }
}

private fun Project.configureSonatypePublicationIfNecessary() {
    configure<PublishingExtension> {
        repositories {
            maven {
                group = getPublicationGroup()
                version = getPublicationVersion()
                url = if (version.toString().contains("SNAPSHOT")) {
                    URI("https://mirrors.tencent.com/repository/maven/tencent_public_snapshots")
                } else {
                    URI("https://mirrors.tencent.com/repository/maven/tencent_public")
                }
                credentials {
                    username = System.getenv("mavenUserName")
                    password = System.getenv("mavenPassword")
                }
            }
        }
    }
}

private fun MavenPublication.configurePom() {
    pom {
        name.set("markdown-render")
        description.set("Markdown Render in Kuikly Compose")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        url.set("https://git.woa.com/QQNews_Android/markdown-compose-render")
        scm {
            url.set("https://git.woa.com/QQNews_Android/markdown-compose-render")
            connection.set("scm:https://git.woa.com/QQNews_Android/markdown-compose-render.git")
        }
        developers {
            developer {
                id.set("fitzwu")
                name.set("吴有雄")
                email.set("fitzu@tencent.com")
                organization.set("Tencent")
            }
        }
    }
}
