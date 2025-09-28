#!/usr/bin/groovy
@Library(value = 'cicd-shared-libs@develop', changelog = false) _

def REPO_URL = 'https://github.com/stevenjsmin/stevenlab-springboot-helloworld.git'
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
    // tools {
    //     jdk 'java-21'
    //     maven 'maven-3'
    // }
    environment {
        APP_NAME = 'springboot-helloworld'
        REGISTRY = 'trialqdcy13.jfrog.io'
        DOCKER_REPO = 'stevenlab-docker-local'
    }

    stages {
        stage('Seed Parameters') {
            steps {
                script {
                    properties([parameters([
                            string(name: 'VERSION', defaultValue: "1.0.${env.BUILD_NUMBER}", description: 'App version(E.G: 1.0.123)'),
                            choice(name: 'BRANCH', choices: branches.join('\n'), description: 'Choose a branch to checkout'),
                            booleanParam(name: 'SKIP_TESTS', defaultValue: true, description: 'Check this option to skip testing phase'),
                            booleanParam(name: 'MULTI_ARCH', defaultValue: false, description: 'Build/Push for multiple architecture(amd64,arm64)'),
                    ])])
                }
            }
        }

        stage('Checkout Source') {
            steps {
                // Jenkins에 등록된 Git credentials를 사용하려면 credentialsId 지정
                git branch: "${param.BRANCH}", url: 'https://github.com/stevenjsmin/stevenlab-springboot-helloworld.git'
            }
        }

        stage('Build Artifact') {
            steps {
                sh "mvn -B versions:set -DnewVersion=${params.VERSION} versions:commit"
                sh 'mvn clean package'
            }
        }

        stage('Publish Artifact') {
            steps {
                sh "mvn -B deploy ${params.SKIP_TESTS ? '-DskipTests' : ''}"
            }
        }

        stage('Docker Build & Push') {
            steps {
                script {
                    DOCKER_IMAGE = "${REGISTRY}/${DOCKER_REPO}/${APP_NAME}:${params.VERSION ?: env.BUILD_NUMBER}"

                    docker.withRegistry("https://${env.REGISTRY}", 'jfrog-docker') {
                        if (params.MULTI_ARCH) {
                            sh """
                                docker buildx create --use --name jx || docker buildx use jx
                                docker buildx build --platform linux/amd64,linux/arm64 \
                                  --build-arg VERSION=${params.VERSION ?: env.BUILD_NUMBER} \
                                  -t ${DOCKER_IMAGE} \
                                  --push \
                                  .
                              """
                        } else {
                            def img = docker.build("${DOCKER_IMAGE}", "--build-arg VERSION=${params.VERSION ?: env.BUILD_NUMBER} .")
                            img.push()
                        }
                    }

                }
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