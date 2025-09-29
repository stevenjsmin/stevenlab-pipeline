#!/usr/bin/groovy
@Library(value = 'cicd-shared-libs@develop', changelog = false) _

def REPO_URL = 'https://github.com/stevenjsmin/stevenlab-powershell.git'
// def GIT_CREDENTIALS_ID = 'my-git-creds'

def branches = branchChoices(repoUrl: REPO_URL)

pipeline {
    agent { node { label "master" } }
    options {
        ansiColor('xterm')
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('Seed Parameters') {
            steps {
                script {
                    properties([parameters([
                            string(name: 'VERSION', defaultValue: "1.0.${env.BUILD_NUMBER}", description: 'App version(E.G: 1.0.123)'),
                            choice(name: 'BRANCH', choices: branches.join('\n'), description: 'Choose a branch to checkout'),
                            booleanParam(name: 'RUN_PWS_FILE', defaultValue: true, description: 'To run a PowerShell script as a file.'),
                            booleanParam(name: 'RUN_INLINE', defaultValue: true, description: 'To run a PowerShell script as an in-line.'),
                    ])])
                }
            }
        }

        stage('Checkout Source') {
            steps {
                // Jenkins에 등록된 Git credentials를 사용하려면 credentialsId 지정
                git branch: "${params.BRANCH}", url: 'https://github.com/stevenjsmin/stevenlab-powershell.git'
            }
        }

        stage('Run file') {
            when {
                expression { return params.RUN_PWS_FILE }
            }
            steps {
                sh 'pwsh -File sysinfo.ps1'
            }
        }

        stage('Run in-line') {
            when {
                expression { return params.RUN_INLINE }
            }
            steps {
                sh '''
                pwsh -Command "
                  Write-Host '=== SYSTEM INFO ===';
                  Write-Host 'Host:' $(hostname);
                  Write-Host 'PS Version:' $($PSVersionTable.PSVersion);
                "
                '''
            }
        }
    }

    post {
        success {
            echo "✅ Build & Deploy 성공"
        }
        failure {
            echo "❌ 실패: 콘솔 로그를 확인하세요."
        }
    }
}