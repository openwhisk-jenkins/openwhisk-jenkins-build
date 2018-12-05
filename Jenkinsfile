#!/usr/bin/env groovy

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

timeout(time: 4, unit: 'HOURS') {

    // This node name will be changed to openwhisk1, when it is applied to the Apache environment.
    node("openwhisk1") {
        deleteDir()
        //stage('Checkout') {
        //    echo 'Checking out the source code.'
        //    checkout([$class: 'GitSCM',
        //              branches: [[name: "*/${Branch}"]],
        //              doGenerateSubmoduleConfigurations: false,
        //              extensions: [
        //                      [$class: 'CloneOption', noTags: true, reference: '', shallow: true],
        //                     [$class: 'CleanBeforeCheckout']
        //              ],
        //              submoduleCfg: [],
        //              userRemoteConfigs: [[url: "https://github.com/${Fork}/${RepoName}.git"]]
        //    ])
        //}
        checkout scm
        //sh 'sudo gpasswd -a travis docker'
        //sh './tools/ubuntu-setup/all.sh'
        /*def pipelineUtils = load 'tools/jenkins/apache/pipelineUtils.groovy'
        def isPrereqInstalled = pipelineUtils.installPrereqs(["ansible": true, "json-schema": true, "jdk8": true, "python-couchdb": true])
        if (!isPrereqInstalled) {
            println("Prereq installation is failed.")
            return -1
        }*/
        //sh 'export OPENWHISK_HOME=$(pwd)'
        //sh 'echo ${OPENWHISK_HOME}'

        stage('Build') {
            //sh './gradlew'
            echo 'Building....'
            //sh 'docker ps'
            echo  'The current username at openwhisk1 is '
            sh 'whoami'
            //sh 'sudo su - openwhisk'
            sh 'java -version'
            echo 'The current OS is '
            sh 'lsb_release -a'
            //sh 'ansible --version'
            //sh './gradlew distDocker'
        }

        stage('Deploy') {
            echo 'Deploying....'
            dir("ansible") {
                //sh 'ansible-playbook -i environments/local setup.yml'
                //sh 'ansible-playbook -i environments/local couchdb.yml'
                //sh 'ansible-playbook -i environments/local initdb.yml'
                //sh 'ansible-playbook -i environments/local wipe.yml'
                //sh 'ansible-playbook -i environments/local apigateway.yml'
                //sh 'ansible-playbook -i environments/local openwhisk.yml'
                //sh 'ansible-playbook -i environments/local properties.yml'
            }
        }

        stage('Test') {
            echo 'Testing....'
            //sh './gradlew tests:test -Dtest.single=*WskRestBasicTests*'
        }
        sh 'pwd'
        sh 'ls'
        sh 'java -version'
        //sh 'cat whisk.properties'
    }

    // There are totally 3 VMs available for OpenWhisk in apache. The other two will be used later.
    /*node("openwhisk2") {
        deleteDir()
        checkout scm
        stage('Build') {
            //sh './gradlew'
            echo 'Building....'
            //sh 'docker ps'
            echo  'The current username at openwhisk2 is '
            sh 'whoami'
            echo 'The current OS is '
            sh 'lsb_release -a'
            //sh 'ansible --version'
        }
    }

    node("openwhisk3") {
        deleteDir()
        checkout scm
        stage('Build') {
            //sh './gradlew'
            echo 'Building....'
            //sh 'docker ps'
            echo  'The current username at openwhisk3 is '
            sh 'whoami'
            echo 'The current OS is '
            sh 'lsb_release -a'
            //sh 'ansible --version'
        }
    }*/

}
