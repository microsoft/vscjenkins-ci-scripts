def projects = ['azure-vm-agents-plugin', 'azure-credentials-plugin',
    'windows-azure-storage-plugin', 'azure-app-service-plugin', 'azure-commons-plugin',
    'azure-container-agents-plugin', 'azure-function-plugin', 'azure-acs-plugin',
    'kubernetes-cd-plugin', 'azure-ad-plugin']

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
    def pattern = 'target/(*).hpi'
    if (it == 'azure-commons-plugin') {
        pattern = 'azure-commons-plugin/' + pattern
    }
    def tsParam = '${BUILD_TIMESTAMP}'
    def specJson = """\
{
    "files": [{
        "pattern": "${pattern}",
        "target": "nightly-builds/${ -> it}/{1}-NIGHTLY-${tsParam}.hpi",
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
                branch('*/dev')
            }
        }
        triggers {
       	    scm('H 1 * * *') {
                ignorePostCommitHooks()
            }
    	}
        steps {
            maven("clean verify")
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