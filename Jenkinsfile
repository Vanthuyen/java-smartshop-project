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

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $DOCKER_IMAGE:latest .'
            }
        }

        stage('Run Container') {
            steps {
                sh 'docker stop spring-app || true && docker rm spring-app || true'
                sh 'docker run -d -p 8081:8080 --name spring-app $DOCKER_IMAGE:latest'
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
