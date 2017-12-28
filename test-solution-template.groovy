#!/usr/bin/env groovy
properties([
    pipelineTriggers([cron('@daily')]),
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '14', numToKeepStr: '')),
    parameters([
        string(defaultValue: 'Azure', description: '', name: 'template_fork'),
        string(defaultValue: 'master', description: '', name: 'template_branch')])
])

def utils_location = "https://raw.githubusercontent.com/Azure/jenkins/master/solution_template/"

def DeployJenkinsSolutionTemplate(scenario_name, options) {
    checkout scm

    def template_base_url = "https://raw.githubusercontent.com/" + params.template_fork + "/jenkins/"+ params.template_branch + "/solution_template/"
    def template_url = template_base_url + "/mainTemplate.json"
    def ssh_command = ""

    def params = [:]
    params['artifactsLocation'] = ['value' : template_base_url]
    params['artifactsLocationSasToken'] = ['value' : '']
    params['publicIPResourceGroup'] = ['value' : scenario_name]
    params['vmName'] = ['value' : (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replaceAll('-', '').take(48) ]
    params['adminUserName'] = ['value' : 'testuser']
    params['adminPassword'] = ['value' : '']
    params['adminSSHPublicKey'] = ['value' : '']
    params['vmSize'] = ['value' : 'Standard_DS1_v2']
    params['storageAccountType'] = ['value' : options.storageType]
    params['publicIPName'] = ['value' : 'jnktst' + UUID.randomUUID().toString().replaceAll('-', '')]
    params['dnsPrefix'] = ['value' : 'jnktst' + UUID.randomUUID().toString().replaceAll('-', '')]
    params['jenkinsReleaseType'] = ['value' : options.jenkinsReleaseType]
    params['vnetResourceGroup'] = ['value' : scenario_name]
    params['vnetName'] = ['value' : params['vmName']['value'] + '-vnet']
    params['vnetAddressPrefix'] = ['value' : '172.18.0.0/16']
    params['subnetName'] = ['value' : 'jnktst']
    params['subnetAddressPrefix'] = ['value' : '172.18.0.0/24']

    if (options.useSSHPublicKey) {
        params['authenticationType'] = ['value' : 'sshPublicKey']
    } else {
        params['authenticationType'] = ['value' : 'password']
        withCredentials([usernamePassword(credentialsId: 'AzDevOpsTestPassword', passwordVariable: 'admin_password', usernameVariable: 'admin_user_ignore')]) {
            params['adminPassword'] = ['value' : env.admin_password]
        }
    }

    if (options.useExistingPublicIP) {
        params['publicIPNewOrExisting'] = ['value' : 'existing']
        def deploy_ip_script_path = 'scripts/deploy-public-ip.sh'
        sh 'chmod +x ' + deploy_ip_script_path
        withCredentials([usernamePassword(credentialsId: 'AzDevOpsTestingSP', passwordVariable: 'app_key', usernameVariable: 'app_id')]) {
            sh deploy_ip_script_path + ' -ip ' + params['publicIPName']['value'] + ' -rg ' + scenario_name + ' -dp ' + params['dnsPrefix']['value'] + ' -ai ' + env.app_id + ' -ak ' + env.app_key
        }
    } else {
        params['publicIPNewOrExisting'] = ['value' : 'new']
    }

    if (options.useExistingVnet) {
        params['vnetNewOrExisting'] = ['value' : 'existing']
        def deploy_vnet_script_path = 'scripts/deploy-vnet.sh'
        sh 'chmod +x ' + deploy_vnet_script_path
        withCredentials([usernamePassword(credentialsId: 'AzDevOpsTestingSP', passwordVariable: 'app_key', usernameVariable: 'app_id')]) {
            sh deploy_vnet_script_path + ' -vn ' + params['vnetName']['value'] + ' -rg ' + scenario_name + ' -vp ' + params['vnetAddressPrefix']['value'] + ' -sn ' + params['subnetName']['value'] + ' -sp ' + params['subnetAddressPrefix']['value'] + ' -ai ' + env.app_id + ' -ak ' + env.app_key
        }
    } else {
        params['vnetNewOrExisting'] = ['value' : 'new']
    }

    def paramsJSON = readJSON text: '{}'
    paramsJSON['parameters'] = params
    writeJSON file: scenario_name + '.json', json: paramsJSON

    def script_path = 'scripts/deploy-arm-template.sh'
    sh 'chmod +x ' + script_path

    withCredentials([usernamePassword(credentialsId: 'AzDevOpsTestingSP', passwordVariable: 'app_key', usernameVariable: 'app_id')]) {
        ssh_command = sh(returnStdout: true, script: script_path + ' -rps yes -tu ' + template_url + ' -vm ' + params['vmName']['value'] + ' -tp ' + scenario_name +'.json -sn ' + scenario_name + ' -ai ' + env.app_id + ' -ak ' + env.app_key).trim()
    }
    return ssh_command
}

def RunSolutionTemplateTests(options) {
    def scenario_name = "st-test" + UUID.randomUUID().toString().replaceAll("-", "")
    def socket = scenario_name + "/ssh-socket"
    node('quickstart-template') {
        try {
            def ssh_command = DeployJenkinsSolutionTemplate(scenario_name, options)

            sh ssh_command + ' -S ' + socket + ' -fNTM -o "StrictHostKeyChecking=no"'

            try {
                runJenkinsTests(sshCommand: ssh_command, utilsLocation: options.utilsLocation)
            } catch(e) {
            } finally {
                sh ssh_command + ' -S ' + socket + ' -O exit'
            }

            // Only clean up the resource group if all previous stages passed (just in case we want to debug a failure)
            // The clean-deployments job will delete it after 2 days
            sh 'az lock delete -g ' + scenario_name + ' -n del-lock'
            sh 'az group delete -n ' + scenario_name + ' --yes'
        } catch (e) {
            print e
            sh 'az lock delete -g ' + scenario_name + ' -n del-lock || true'
            throw e
        } finally {
            sh 'az logout'
            sh 'rm -rf ' + scenario_name
            sh 'rm ' + scenario_name + ".json"
        }
    }
}

try {
    def jenkins_release_type = 'LTS'
    if ( env.JOB_BASE_NAME.contains('weekly') ) {
        jenkins_release_type = 'weekly'
    }

    stage('Run Solution Template Tests') {
        Map tasks = [failFast: false]
        def options = []
        for (publicKey in [true, false]) {
            for (storageTypeStr in ['Standard_LRS', 'Premium_LRS']) {
                for (existingPublicIP in [true, false]) {
                    for (existingVnet in [true, false]) {
                        options.push([
                            name: 'SSH: ' + publicKey + ' Storage Type: ' + storageTypeStr + ' Existing Public IP: ' + existingPublicIP,
                            useSSHPublicKey: publicKey,
                            storageType: storageTypeStr,
                            useExistingPublicIP: existingPublicIP,
                            useExistingVnet: existingVnet,
                            utilsLocation: utils_location,
                            jenkinsReleaseType: jenkins_release_type
                        ])
                    }
                }
            }
        }

        //must iterate like this, the 'a in b' idiom is not supported by the cps plugin
        for (int i = 0; i < options.size(); ++i) {
            def opt = options[i]
            tasks[opt.name] = {
                RunSolutionTemplateTests(opt)
            }
        }

        timeout(60) {
            parallel(tasks)
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
