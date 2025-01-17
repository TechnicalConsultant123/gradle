/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.jvm.toolchain.internal


import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.internal.task.ReportableToolchain
import org.gradle.jvm.toolchain.internal.task.ToolchainReportRenderer
import spock.lang.Specification
import spock.lang.TempDir

class ToolchainReportRendererTest extends Specification {

    @TempDir
    File temporaryFolder

    InstallationLocation installation = Mock(InstallationLocation)

    def "jre is rendered properly"() {
        given:
        def metadata = JvmInstallationMetadata.from(
            new File("path"),
            "1.8.0",
            "1.8.0-b01",
            "25.292-b01",
            "vendorName",
            "")
        installation.source >> "SourceSupplier"

        expect:
        assertOutput(metadata, """{identifier} + vendorName JRE 1.8.0-b01{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Vendor:             {description}vendorName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}SourceSupplier{normal}

""")
    }

    def "jdk is rendered properly"() {
        given:
        File javaHome = new File(temporaryFolder, "javahome").tap { mkdirs() }
        def metadata = JvmInstallationMetadata.from(
            javaHome,
            "1.8.0",
            "1.8.0-b01",
            "25.292-b01",
            "adoptopenjdk",
            "")
        installation.source >> "SourceSupplier"

        def binDir = new File(javaHome, "bin")
        if (binDir.mkdir()) {
            File javac = new File(binDir, OperatingSystem.current().getExecutableName('javac'))
            javac << 'dummy'
        }

        expect:
        assertOutput(metadata, """{identifier} + AdoptOpenJDK 1.8.0-b01{normal}
     | Location:           {description}$javaHome{normal}
     | Language Version:   {description}8{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Is JDK:             {description}true{normal}
     | Detected by:        {description}SourceSupplier{normal}

""")
    }

    void assertOutput(JvmInstallationMetadata metadata, String expectedOutput) {
        def renderer = new ToolchainReportRenderer()
        def output = new TestStyledTextOutput()
        renderer.output = output
        renderer.printToolchain(new ReportableToolchain(metadata, installation))
        assert output.value == expectedOutput
    }
}
