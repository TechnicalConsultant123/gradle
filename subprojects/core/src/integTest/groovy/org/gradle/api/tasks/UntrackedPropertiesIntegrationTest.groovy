/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class UntrackedPropertiesIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def "task with untracked #properties is not up-to-date"() {
        buildFile("""
            abstract class MyTask extends DefaultTask {
                ${properties == "inputs" ? "@Untracked" : ""}
                @InputFile
                abstract RegularFileProperty getInputFile()
                ${properties == "outputs" ? "@Untracked" : ""}
                @OutputFile
                abstract RegularFileProperty getOutputFile()
                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = inputFile.get().asFile.text
                }
            }
            tasks.register("myTask", MyTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)
        file("input.txt").text = "input"

        when:
        run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Task ':myTask' is not up-to-date because:")
        outputContains("Task has untracked properties.")

        when:
        run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Task ':myTask' is not up-to-date because:")
        outputContains("Task has untracked properties.")

        where:
        properties << ["inputs", "outputs"]
    }

    def "task with untracked #properties is not cached"() {
        buildFile("""
            @CacheableTask
            abstract class MyTask extends DefaultTask {
                ${properties == "inputs" ? "@Untracked" : ""}
                @InputFile
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract RegularFileProperty getInputFile()
                ${properties == "outputs" ? "@Untracked" : ""}
                @OutputFile
                abstract RegularFileProperty getOutputFile()
                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = inputFile.get().asFile.text
                }
            }
            tasks.register("myTask", MyTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)
        file("input.txt").text = "input"

        when:
        withBuildCache().run("myTask", "--info")
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Caching disabled for task ':myTask' because:")
        outputContains(expectedMessage)

        where:
        properties | expectedMessage
        "inputs"   | "Input property 'inputFile' is untracked"
        "outputs"  | "Output property 'outputFile' is untracked"
    }

    def "tasks can produce and consume unreadable content via untracked properties"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")

        buildFile generateProducerTask(true)
        buildFile generateConsumerTask(true)

        buildFile("""
            def producer = tasks.register("producer", Producer) {
                outputDir = project.layout.buildDirectory.dir("root")
            }
            tasks.register("consumer", Consumer) {
                inputDir = producer.flatMap { it.outputDir }
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)

        expect:
        succeeds("consumer", "--info")

        cleanup:
        unreadableDir.setReadable(true)
    }

    def "task producing unreadable content via tracked property is not stored in execution history"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")

        buildFile generateProducerTask(false)

        buildFile("""
            tasks.register("producer", Producer) {
                outputDir = project.layout.buildDirectory.dir("root")
            }
        """)

        when:
        executer.expectDeprecationWarning("Accessing unreadable input or output files has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the input or output property as untracked.")
        run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Failed to snapshot unreadable input or output, treating task as untracked: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        when:
        unreadableDir.setReadable(true)
        executer.expectDeprecationWarning("Accessing unreadable input or output files has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the input or output property as untracked.")
        run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Failed to snapshot unreadable input or output, treating task as untracked: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")
        outputContains("Task ':producer' is not up-to-date because:")
        outputContains("No history is available.")

        cleanup:
        unreadableDir.setReadable(true)
    }

    def "task producing unreadable content via tracked property is not stored in cache"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")

        buildFile generateProducerTask(false)

        buildFile("""
            apply plugin: "base"

            tasks.register("producer", Producer) {
                outputs.cacheIf { true }
                outputDir = project.layout.buildDirectory.dir("root")
            }
        """)

        when:
        executer.expectDeprecationWarning("Accessing unreadable input or output files has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the input or output property as untracked.")
        withBuildCache().run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Failed to snapshot unreadable input or output, treating task as untracked: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        when:
        unreadableDir.setReadable(true)
        run("clean")

        executer.expectDeprecationWarning("Accessing unreadable input or output files has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the input or output property as untracked.")
        withBuildCache().run "producer", "--info"
        then:
        executedAndNotSkipped(":producer")
        outputContains("Failed to snapshot unreadable input or output, treating task as untracked: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")

        cleanup:
        unreadableDir.setReadable(true)
    }

    def "task consuming unreadable content via tracked property is not tracked"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")
        assert unreadableDir.mkdirs()
        assert unreadableDir.setReadable(false)

        buildFile generateConsumerTask(false)

        buildFile("""
            tasks.register("consumer", Consumer) {
                inputDir = project.layout.buildDirectory.dir("root")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)

        when:
        executer.expectDeprecationWarning("Accessing unreadable input or output files has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the input or output property as untracked.")
        run "consumer", "--info"
        then:
        executedAndNotSkipped(":consumer")
        outputContains("Failed to snapshot unreadable input or output, treating task as untracked: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")
        outputContains("Task ':consumer' is not up-to-date because:")
        outputContains("Change tracking is disabled.")

        cleanup:
        unreadableDir.setReadable(true)
    }

    def "task consuming unreadable content via tracked property is not stored in cache"() {
        def rootDir = file("build/root")
        def unreadableDir = rootDir.file("unreadable")
        assert unreadableDir.mkdirs()
        assert unreadableDir.setReadable(false)

        buildFile generateConsumerTask(false)

        buildFile("""
            tasks.register("consumer", Consumer) {
                outputs.cacheIf { true }
                inputDir = project.layout.buildDirectory.dir("root")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)

        when:
        executer.expectDeprecationWarning("Accessing unreadable input or output files has been deprecated. " +
            "This will fail with an error in Gradle 8.0. " +
            "Declare the input or output property as untracked.")
        withBuildCache().run "consumer", "--info"
        then:
        executedAndNotSkipped(":consumer")
        outputContains("Failed to snapshot unreadable input or output, treating task as untracked: java.nio.file.AccessDeniedException: ${unreadableDir.absolutePath}")
        outputContains("Task ':consumer' is not up-to-date because:")
        outputContains("Change tracking is disabled.")
        outputContains("Caching disabled for task ':consumer' because:")
        outputContains("Cacheability was not determined")

        cleanup:
        unreadableDir.setReadable(true)
    }

    static generateProducerTask(boolean untracked) {
        """
            abstract class Producer extends DefaultTask {
                ${untracked ? "@Untracked" : ""}
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void execute() {
                    def unreadableDir = outputDir.get().dir("unreadable").asFile
                    unreadableDir.mkdirs()
                    assert unreadableDir.setReadable(false)
                    assert !unreadableDir.canRead()
                }
            }
        """
    }

    static generateConsumerTask(boolean untracked) {
        """
            abstract class Consumer extends DefaultTask {
                ${untracked ? "@Untracked" : ""}
                @InputDirectory
                abstract DirectoryProperty getInputDir()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute() {
                    def unreadableDir = inputDir.get().dir("unreadable").asFile
                    assert !unreadableDir.canRead()
                    outputFile.get().asFile << "Executed"
                }
            }
        """
    }
}
