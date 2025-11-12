pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "jenkins/jenkins:lts-jdk17"
    }

    tools {
        maven 'Maven-3.9.9'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/Vanthuyen/java-smartshop-project.git'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Login to Docker Hub') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'SmartShop1234',
                                                 usernameVariable: 'DOCKER_USER',
                                                 passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                sh 'docker push $DOCKER_IMAGE:latest'
            }
        }

        stage('Run Container') {
            steps {
                sh 'docker stop spring-app || true && docker rm spring-app || true'
                sh 'docker run -d -p 8082:8082 --name spring-app $DOCKER_IMAGE:latest'
            }
        }
    }

    post {
        success {
            echo '✅ Deploy thành công!'
        }
        failure {
            echo '❌ Build thất bại!'
        }
    }
}
