pipeline {
  agent any
  options { disableConcurrentBuilds(); timeout(time: 45, unit: 'MINUTES') }
  environment {
    CVFORGE_DEPLOY_HOST = '172.19.0.1'
    CVFORGE_KNOWN_HOSTS = '/var/jenkins_home/cvforge-known_hosts'
  }
  stages {
    stage('Validate') { steps { cvforgeRemote('validate') } }
    stage('Build, test, deploy, health') { steps { cvforgeRemote('deploy') } }
  }
  post {
    success { echo 'CVForge deploy completed; API and frontend health checks passed.' }
    failure { echo 'CVForge deploy failed; the host wrapper attempted rollback if deployment started.' }
  }
}
def cvforgeRemote(String action) {
  if (!(action in ['validate', 'deploy'])) error('Unsupported CVForge action.')
  withCredentials([sshUserPrivateKey(credentialsId: 'cvforge-deploy-key',
      keyFileVariable: 'CVFORGE_DEPLOY_KEY', usernameVariable: 'CVFORGE_DEPLOY_USER')]) {
    sh 'ssh -F /dev/null -i "$CVFORGE_DEPLOY_KEY" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$CVFORGE_KNOWN_HOSTS" -o ConnectTimeout=10 "$CVFORGE_DEPLOY_USER@$CVFORGE_DEPLOY_HOST" "' + action + '"'
  }
}
