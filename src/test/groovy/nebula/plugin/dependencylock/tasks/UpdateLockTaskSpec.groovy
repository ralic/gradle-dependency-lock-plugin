/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.dependencylock.tasks

import nebula.plugin.dependencylock.dependencyfixture.Fixture
import nebula.plugin.dependencylock.util.LockGenerator
import nebula.test.ProjectSpec

class UpdateLockTaskSpec extends ProjectSpec {
    final String taskName = 'updateLock'

    def setupSpec() {
        Fixture.createFixtureIfNotCreated()
    }

    def setup() {
        project.apply plugin: 'java'
        project.repositories { maven { url Fixture.repo } }
    }

    UpdateLockTask createTask() {
        def task = project.tasks.create(taskName, UpdateLockTask)
        task.dependenciesLock = new File(project.buildDir, 'dependencies.lock')
        task.configurationNames = [ 'compile', 'default', 'runtime', 'testCompile', 'testRuntime' ]
        task
    }

    def 'transitives are automatically updated'() {
        project.dependencies {
            compile 'test.example:bar:1.+'
            compile 'test.example:qux:1.0.0'
        }

        def lockFile = new File(project.projectDir, 'dependencies.lock')
        def lockText = LockGenerator.duplicateIntoConfigs(
                '''\
                    "test.example:bar": {
                        "locked": "1.0.0",
                        "requested": "1.+"
                    },
                    "test.example:qux": {
                        "locked": "1.0.0",
                        "requested": "1.0.0"
                    },
                    "test.example:foo": {
                        "locked": "1.0.0",
                        "transitive": [
                            "test.example:bar",
                            "test.example:qux"
                        ]
                    }'''.stripIndent()
        )
        lockFile.text = lockText

        def task = createTask()
        task.includeTransitives = true

        def updatedLock = LockGenerator.duplicateIntoConfigs(
                '''\
                    "test.example:bar": {
                        "locked": "1.1.0",
                        "requested": "1.+"
                    },
                    "test.example:foo": {
                        "locked": "1.0.1",
                        "transitive": [
                            "test.example:bar",
                            "test.example:qux"
                        ]
                    },
                    "test.example:qux": {
                        "locked": "1.0.0",
                        "requested": "1.0.0"
                    }'''.stripIndent()
        )

        when:
        task.execute()

        then:
        task.dependenciesLock.text == updatedLock
    }
}
