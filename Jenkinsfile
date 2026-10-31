pipeline {
  agent any
  options {
    skipDefaultCheckout(true)
    disableConcurrentBuilds()
    timeout(time: 45, unit: 'MINUTES')
  }
  environment {
    CVFORGE_DEPLOY_HOST = '172.19.0.1'
    CVFORGE_KNOWN_HOSTS = '/var/jenkins_home/cvforge-known_hosts'
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          if (!(env.GIT_COMMIT ==~ /[0-9a-f]{40}/)) {
            error('The Gitea checkout did not provide a valid commit SHA.')
          }
        }
      }
    }
    stage('Validate') {
      steps { cvforgeRemote('validate') }
    }
    stage('Build, test, deploy, health') {
      steps { cvforgeRemote("deploy:${env.GIT_COMMIT}") }
    }
  }
  post {
    success { echo 'CVForge deploy completed; API and frontend health checks passed.' }
    failure { echo 'CVForge deploy failed; the host wrapper attempted rollback if deployment started.' }
  }
}
def cvforgeRemote(String action) {
  if (action != 'validate' && !(action ==~ /deploy:[0-9a-f]{40}/)) {
    error('Unsupported CVForge action.')
  }
  withCredentials([sshUserPrivateKey(credentialsId: 'cvforge-deploy-key',
      keyFileVariable: 'CVFORGE_DEPLOY_KEY', usernameVariable: 'CVFORGE_DEPLOY_USER')]) {
    sh 'ssh -F /dev/null -i "$CVFORGE_DEPLOY_KEY" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$CVFORGE_KNOWN_HOSTS" -o ConnectTimeout=10 "$CVFORGE_DEPLOY_USER@$CVFORGE_DEPLOY_HOST" "' + action + '"'
  }
}
