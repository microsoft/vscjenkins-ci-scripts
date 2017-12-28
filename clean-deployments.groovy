node('quickstart-template') {
    try {
        properties([
            pipelineTriggers([cron('@daily')]),
            buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '14', numToKeepStr: ''))
        ])

        checkout scm

        stage('Delete deployments') {
            def script_path = 'scripts/clean-deployments.sh'
            sh 'chmod +x ' + script_path
            withCredentials([usernamePassword(credentialsId: 'AzDevOpsTestingSP', passwordVariable: 'app_key', usernameVariable: 'app_id')]) {
                sh script_path + ' -ai ' + env.app_id + ' -ak ' + env.app_key
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
    } finally {
      sh 'az logout'
    }
}