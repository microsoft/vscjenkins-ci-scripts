def projects = ['azure-vm-agents-plugin', 'azure-credentials-plugin',
    'windows-azure-storage-plugin', 'azure-app-service-plugin', 'azure-commons-plugin',
    'azure-container-agents-plugin', 'azure-function-plugin', 'azure-acs-plugin']

projects.each {
    def gitUrl = "https://github.com/jenkinsci/${it}.git"

    // generate pipeline job
    multibranchPipelineJob("Plugins/${it}") {
        branchSources {
            git {
                remote(gitUrl)
            }
        }
        orphanedItemStrategy {
            discardOldItems {
                numToKeep(20)
            }
        }
    }

    // generate nightly build job
    def tsParam = '${BUILD_TIMESTAMP}'
    def specJson = """\
{
    "files": [{
        "pattern": "target/(*).hpi",
        "target": "${ -> it}-nightly/{1}-NIGHTLY-${tsParam}.hpi",
        "recursive": false
    }]
}"""

    job("Nightly Builds/${it} nightly") {
        label('linux-dev')
        properties {
            zenTimestamp('yyyyMMdd')
        }
        scm {
            git {
                remote {
                    url(gitUrl)
                }
                branch('*/master')
            }
        }
        triggers {
       	    scm('H 1 * * *') {
                ignorePostCommitHooks()
            }
    	}
        steps {
            maven("verify hpi:hpi")
        }
        configure { project ->
            project / 'buildWrappers' / 'org.jfrog.hudson.generic.ArtifactoryGenericConfigurator' {
                details {
                    artifactoryName('vscjenkins-repo')
                    artifactoryUrl('http://repo-vscjenkins.southeastasia.cloudapp.azure.com')
                }
                resolverDetails {
                    artifactoryName('vscjenkins-repo')
                    artifactoryUrl('http://repo-vscjenkins.southeastasia.cloudapp.azure.com')
                }
                useSpecs(true)
                uploadSpec {
                    spec(specJson)
                }
                downloadSpec {
                    spec()
                }
                deployBuildInfo(true)
            }
        }
    }
}