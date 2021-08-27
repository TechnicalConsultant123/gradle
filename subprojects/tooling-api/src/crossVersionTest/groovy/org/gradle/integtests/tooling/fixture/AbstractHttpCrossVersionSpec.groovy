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

package org.gradle.integtests.tooling.fixture

import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer

abstract class AbstractHttpCrossVersionSpec extends ToolingApiSpecification {

    protected RepositoryHttpServer server

    def setup() {
        server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)
        server.before()
    }

    def cleanup() {
        server.after()
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }

    Modules setupBuildWithArtifactDownloadDuringConfiguration() {
        toolingApi.requireIsolatedUserHome()

        def projectB = mavenHttpRepo.module('group', 'projectB', '1.0').publish()
        def projectC = mavenHttpRepo.module('group', 'projectC', '1.5').publish()
        def projectD = mavenHttpRepo.module('group', 'projectD', '2.0-SNAPSHOT').publish()

        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
        buildFile << """
            allprojects {
                apply plugin:'java-library'
            }
            repositories {
               maven { url '${mavenHttpRepo.uri}' }
            }
            dependencies {
                implementation project(':a')
                implementation "group:projectB:1.0"
                implementation "group:projectC:1.+"
                implementation "group:projectD:2.0-SNAPSHOT"
            }
            configurations.compileClasspath.each { println it }
        """

        def modules = new Modules(projectB, projectC, projectD)
        modules.expectResolved()
        return modules
    }

    Modules setupBuildWithArtifactDownloadDuringTaskExecution() {
        toolingApi.requireIsolatedUserHome()

        def projectB = mavenHttpRepo.module('group', 'projectB', '1.0').publish()
        def projectC = mavenHttpRepo.module('group', 'projectC', '1.5').publish()
        def projectD = mavenHttpRepo.module('group', 'projectD', '2.0-SNAPSHOT').publish()

        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
        buildFile << """
            allprojects {
                apply plugin:'java-library'
            }
            repositories {
               maven { url '${mavenHttpRepo.uri}' }
            }
            dependencies {
                implementation project(':a')
                implementation "group:projectB:1.0"
                implementation "group:projectC:1.+"
                implementation "group:projectD:2.0-SNAPSHOT"
            }
            task resolve {
                def files = configurations.compileClasspath
                inputs.files files
                doFirst {
                    files.forEach { println(it) }
                }
            }
        """

        def modules = new Modules(projectB, projectC, projectD)
        modules.expectResolved()
        return modules
    }

    static class Modules {
        final MavenHttpModule projectB
        final MavenHttpModule projectC
        final MavenHttpModule projectD

        Modules(MavenHttpModule projectB, MavenHttpModule projectC, MavenHttpModule projectD) {
            this.projectB = projectB
            this.projectC = projectC
            this.projectD = projectD
        }

        def expectResolved() {
            projectB.pom.expectGet()
            projectB.artifact.expectGet()
            projectC.rootMetaData.expectGet()
            projectC.pom.expectGet()
            projectC.artifact.expectGet()

            projectD.pom.expectGet()
            projectD.metaData.expectGet()
            projectD.artifact.expectGet()
        }
    }
}
