pipeline {

    agent any

    environment {
        GIT_URL = 'https://github.com/Neueda-Learning/11.102-PayPulse.git'
        BRANCH = 'main'
    }

    stages {

        stage('Checkout Source') {
            steps {
                git branch: "${BRANCH}", url: "${GIT_URL}"
            }
        }

        stage('Build Backend (Maven)') {
            steps {
                dir('backend') {
                    sh 'mvn clean verify'
                }
            }
        }

        stage('Stop Existing Containers') {
            steps {
                sh 'docker-compose down || true'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh 'docker-compose build --no-cache'
            }
        }

        stage('Deploy (all containers incl. Redis, MySQL)') {
            steps {
               sh 'docker-compose up -d'
            }
        }

        stage('Wait for Health') {
            steps {
                sh 'sleep 30'
            }
        }

        stage('Verify Containers Running') {
            steps {
               sh 'docker-compose ps'
               sh 'docker ps --format "table {{.Names}}\\t{{.Status}}"'
            }
        }

        stage('Verify Redis') {
            steps {
                sh 'docker exec paypulse-redis redis-cli ping'
            }
        }
    }

    post {
        always {
           sh 'docker-compose ps'
        }
        failure {
            sh 'docker-compose logs --tail=100'
        }
    }
}