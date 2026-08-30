import java.io.DataInputStream
import java.security.MessageDigest
import java.util.jar.JarFile

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val horizonwrightVersion = providers.gradleProperty("modVersion").get()
extra["modVersion"] = horizonwrightVersion
version = horizonwrightVersion

abstract class VerifyPinnedArtifacts : DefaultTask() {

    @get:InputDirectory
    abstract val artifactDirectory: DirectoryProperty

    @get:InputFile
    abstract val checksumManifest: RegularFileProperty

    @get:Input
    abstract val expectedHashes: MapProperty<String, String>

    @get:Input
    abstract val normalizedTextArtifacts: ListProperty<String>

    @TaskAction
    fun verify() {
        val expected = expectedHashes.get().toSortedMap()
        val normalizedText = normalizedTextArtifacts.get().toSet()
        val directory = artifactDirectory.get().asFile

        expected.forEach { (name, expectedHash) ->
            val artifact = directory.resolve(name)
            if (!artifact.isFile) {
                throw GradleException("Missing pinned artifact: ${artifact.absolutePath}")
            }

            val actualHash = sha256(artifact, name in normalizedText)
            if (actualHash != expectedHash) {
                throw GradleException(
                    "SHA-256 mismatch for $name: expected $expectedHash but found $actualHash"
                )
            }
        }

        val expectedManifest = expected.entries.joinToString(separator = "\n", postfix = "\n") {
            "${it.value}  ${it.key}"
        }
        val actualManifest = checksumManifest.get().asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        if (actualManifest != expectedManifest) {
            throw GradleException(
                "${checksumManifest.get().asFile.name} does not match the hashes pinned in build.gradle.kts"
            )
        }
    }

    private fun sha256(file: java.io.File, normalizeLineEndings: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        if (normalizeLineEndings) {
            val canonicalText = file.readText(Charsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            digest.update(canonicalText.toByteArray(Charsets.UTF_8))
        } else {
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

abstract class VerifyBuildJvm : DefaultTask() {

    @get:Input
    abstract val expectedJavaVersion: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val expectedRuntimeVersion: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val expectedVendor: org.gradle.api.provider.Property<String>

    @TaskAction
    fun verify() {
        val actualJavaVersion = System.getProperty("java.version")
        val actualRuntimeVersion = System.getProperty("java.runtime.version")
        val actualVendor = System.getProperty("java.vendor")
        if (actualJavaVersion != expectedJavaVersion.get()
            || actualRuntimeVersion != expectedRuntimeVersion.get()
            || actualVendor != expectedVendor.get()
        ) {
            throw GradleException(
                "Build JVM mismatch: expected ${expectedVendor.get()} " +
                    "${expectedRuntimeVersion.get()} (java.version ${expectedJavaVersion.get()}) " +
                    "but found $actualVendor $actualRuntimeVersion (java.version $actualJavaVersion)"
            )
        }
    }
}

abstract class VerifyProductionArtifactIsolation : DefaultTask() {

    @get:InputFile
    abstract val productionJar: RegularFileProperty

    @TaskAction
    fun verify() {
        val artifact = productionJar.get().asFile
        if (!artifact.isFile) {
            throw GradleException("Missing production artifact: ${artifact.absolutePath}")
        }
        JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().toList()
            val forbidden = entries.map { it.name }.filter { entry ->
                entry.startsWith("baritone/")
                    || entry == "META-INF/services/baritone.api.IBaritoneProvider"
            }.toList()
            if (forbidden.isNotEmpty()) {
                throw GradleException(
                    "Production Horizonwright JAR embeds forbidden Baritone entries: " +
                        forbidden.take(10).joinToString()
                )
            }
            val tooNew = entries.filter { !it.isDirectory && it.name.endsWith(".class") }.mapNotNull { entry ->
                jar.getInputStream(entry).use { input ->
                    val header = ByteArray(8)
                    DataInputStream(input).readFully(header)
                    val major = ((header[6].toInt() and 0xff) shl 8) or (header[7].toInt() and 0xff)
                    if (major > 52) "${entry.name} (major $major)" else null
                }
            }
            if (tooNew.isNotEmpty()) {
                throw GradleException(
                    "Production Horizonwright JAR contains class files newer than Java 8: " +
                        tooNew.take(10).joinToString()
                )
            }
        }
    }
}

val pinnedBaritoneHashes = linkedMapOf(
    "COPYING-GPL-3.0" to "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986",
    "LICENSE-LGPL-3.0-or-later" to "a5681bf9b05db14d86776930017c647ad9e6e56ff6bbcfdf21e5848288dfaf1b",
    "LICENSE-Part-2.jpg" to "e3ba782078d7a75fa36f57d2fb1df31d03d361f0bc2daef60612dd6098775400",
    "LICENSE-fastutil-Apache-2.0" to "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
    "baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty-sources.jar" to
        "09e503b929c7d5d0ea6f298f0284ee7891aabd2ff2c7405c170b01449e47e700",
    "baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar" to
        "cc24115b0b61c14678e3634e9257e1e155e1eb6ca570accb7d10622f9d4fff0e",
)

val verifyBaritoneArtifacts by tasks.registering(VerifyPinnedArtifacts::class) {
    group = "verification"
    description = "Verifies the exact vendored Baritone binary, source, and license artifacts."
    artifactDirectory.set(layout.projectDirectory.dir("vendor/baritone"))
    checksumManifest.set(layout.projectDirectory.file("vendor/baritone/SHA256SUMS"))
    expectedHashes.set(pinnedBaritoneHashes)
    normalizedTextArtifacts.set(
        listOf(
            "COPYING-GPL-3.0",
            "LICENSE-LGPL-3.0-or-later",
            "LICENSE-fastutil-Apache-2.0",
        )
    )
}

val verifyBuildJvm by tasks.registering(VerifyBuildJvm::class) {
    group = "verification"
    description = "Verifies the exact Temurin build used by the Gradle daemon."
    expectedJavaVersion.set("25.0.4.1")
    expectedRuntimeVersion.set("25.0.4.1+1-LTS")
    expectedVendor.set("Eclipse Adoptium")
}

val verifyProductionArtifactIsolation by tasks.registering(VerifyProductionArtifactIsolation::class) {
    group = "verification"
    description = "Verifies that the production Horizonwright JAR does not embed Baritone."
    dependsOn(tasks.named("assemble"))
    productionJar.set(layout.buildDirectory.file("libs/horizonwright-$horizonwrightVersion.jar"))
}

tasks.named("assemble") {
    dependsOn(verifyBaritoneArtifacts, verifyBuildJvm)
}

tasks.named("check") {
    dependsOn(verifyBaritoneArtifacts, verifyBuildJvm, verifyProductionArtifactIsolation)
}
