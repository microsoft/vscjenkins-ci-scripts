#!/usr/bin/env groovy
import com.microsoft.azure.devops.ci.utils

def call() {
    try {
        def ciUtils = new com.microsoft.azure.devops.ci.utils()
        testResultFilePatterns = ciUtils.getTestResultFilePatterns()
        ciUtils.loadJobProperties()

        timeout(60) {
            stage('Build') {
                parallel failFast: true,
                Windows: {
                    if ( params.run_windows_build_step ) {
                        node('win2016-dev') {
                            checkout scm
                            bat 'mvn clean install'
                        }
                    }
                },
                Linux: {
                    node('ubuntu') {
                        checkout scm
                        sh 'mvn clean install'

                        stash includes: testResultFilePatterns.surefire + ', ' + testResultFilePatterns.findBugs, name: 'test_results'
                        archiveArtifacts '**/target/*.hpi'
                    }
                }
            }

            if ( params.run_integration_tests ) {
                stage('Integration Tests') {
                    ciUtils.runIntegrationTests([
                        [
                            label: "Global Azure (Linux)",
                            node_name: "ubuntu",
                            environment: "az_test_env"
                        ]/*,
                        [
                            label: "Global Azure (Windows)",
                            node_name: "win2016-dev",
                            environment: "az_test_env2"
                        ]*//*,
                        [
                            label: "Mooncake",
                            node_name: "linux-mooncake",
                            environment: "az_test_mooncake"
                        ]*/
                    ])
                }
            }

            node('master') {
               stage ('Publish Test Results') {
                    dir('tests') {
                        unstash 'test_results'
                        if ( params.run_integration_tests ) {
                            unstash 'integration_test_results'
                        }

                        junit healthScaleFactor: 100, testResults: testResultFilePatterns.surefire + ', ' + testResultFilePatterns.failsafe
                        findbugs canComputeNew: false, defaultEncoding: '', excludePattern: '', healthy: '', includePattern: '', pattern: testResultFilePatterns.findBugs, unHealthy: ''
                    }
                }
            }

            if ( params.dogfood) {
                stage('Dogfood') {
                    build job: 'Dogfood',
                        parameters: [
                            string(name: 'plugin_path', value: "${env.JOB_NAME}/${env.BUILD_NUMBER}"),
                            booleanParam(name: 'run', value: true),
                            string(name: 'container_name', value: 'devops-jenkins')
                        ]
                }
            }
        }
    } catch (e) {
        def public_build_url = "$BUILD_URL".replaceAll("$JENKINS_URL" , "$PUBLIC_URL")
        emailext (
            attachLog: true,
            subject: "Jenkins Job '$JOB_NAME' #$BUILD_NUMBER Failed",
            body: public_build_url,
            to: "$TEAM_MAIL_ADDRESS"
        )
        throw e
    }
}
