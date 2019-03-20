#!groovy
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

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

// return the installed buildno
// if no valid driver is installed or any other failure : return "Error"
def getInstalledVersion(apiHost) {
    def apiURL = "https://" + apiHost + "/api/v1"
    def result = ""
    try {
        result = sh(script: "curl --silent ${apiURL}", returnStdout: true)
        def resultObj = jsonParse(result)
        def buildNo = resultObj.buildno
        return buildNo
    }
    catch (Exception e) {
        println (" Failed to query installed version. result = " + result)
        return "Error"
    }
}

def installPrereqs(Map prereqsMap) {
    if (prereqsMap.containsKey("ntp") && prereqsMap.get("ntp")) {
        tool 'NTP'
        sh "sudo ntpdate -v 0.pool.ntp.org || sudo ntpdate -v 1.pool.ntp.org || sudo ntpdate -v 2.pool.ntp.org || sudo ntpdate -v time.nist.gov"
        prereqsMap.remove("ntp")
    }
    if (prereqsMap.containsKey("python2") && prereqsMap.get("python2")) {
        tool 'Python2_7_12'
        prereqsMap.remove("python2")
    }
    /*if (prereqsMap.containsKey("json-schema") && prereqsMap.get("json-schema")) {
        tool 'JSON Schema'
        prereqsMap.remove("json-schema")
    }*/
    /*if (prereqsMap.containsKey("jdk8") && prereqsMap.get("jdk8")) {
        env.JAVA_HOME = "${tool 'JDK8u101'}"
        env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
        prereqsMap.remove("jdk8")
    }*/
    /*if (prereqsMap.containsKey("ansible") && prereqsMap.get("ansible")) {
        tool 'Ansible'
        prereqsMap.remove("ansible")
    }*/
    /*if (prereqsMap.containsKey("python-couchdb") && prereqsMap.get("python-couchdb")) {
        tool 'Python couchdb'
        prereqsMap.remove("python-couchdb")
    }*/
    if (prereqsMap.containsKey("npm") && prereqsMap.get("npm")) {
        tool 'npm'
        prereqsMap.remove("npm")
    }
    if (prereqsMap.keySet().size() != 0) {
        return false
    }
    return true
}

def getLastStableBuildNumber(String jobName) {
    def job = Jenkins.instance.getItemByFullName(jobName)
    def buildNumber = job.getLastStableBuild()
    if (buildNumber == null) {
        return null
    }
    return buildNumber.getNumber()
}

def getJobUrl(String jobName, int jobNumber) {
    def job = Jenkins.instance.getItemByFullName(jobName)
    return Jenkins.instance.getRootUrl() + job.getUrl() + jobNumber
}

def getBuildUrl(job) {
    return Jenkins.instance.getRootUrl() + job.build().getUrl()
}

@NonCPS
def getCurrentTestResult() {
    def build = manager.build
    if (build != null) {
        return build.getAction(hudson.tasks.junit.TestResultAction.class)
    }
    return null
}

@NonCPS
def getFailedTestCount() {
    int failedTestCount = -1
    def testResult = getCurrentTestResult()
    if (testResult != null) {
        failedTestCount = testResult.getFailCount()
    }
    return failedTestCount
}

@NonCPS
def getFailedTests() {
    def failedTests = ""
    def testResult = getCurrentTestResult()
    if (testResult != null) {
        failedTestsList = testResult.getFailedTests()
        failedTestsList.each {
            failed -> failedTests = failedTests + failed.getFullDisplayName() + '; '
        }
    } else {
        failedTests = "None"
    }
    return failedTests
}

def getUCDProcessLink() {
    def matcher = manager.getLogMatcher("Link to process: (.*)")
    if(matcher?.matches()) {
        return matcher.group(1)
    }
    return ""
}

def prepareWhiskProperties(String apiHost, String ansibleEnv, String ansibleParam = "") {
    vaultEnv = "VAULT_YS0"
    if (env.ENV_PREFIX) {
        vaultEnv = "VAULT_" + env.ENV_PREFIX
        if (vaultEnv == "VAULT_YP_FRA") {
            // remove vault credential access to allow health jobs to be run in an non eu environment
            dir("blue/ansible/environments/bluemix/yp-fra/red/group_vars") {
                sh "sed -i -e 's/{{.*lookup(.vault.*}}//' all"
            }
            dir("blue/ansible/environments/bluemix/yp-fra/blue/group_vars") {
                sh "sed -i -e 's/{{.*lookup(.vault.*}}//' all"
            }
        }
    }

    println ("Vault credentials used: ${vaultEnv}")

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: vaultEnv,
                usernameVariable: 'ANSIBLE_HASHICORP_VAULT_ROLE_ID', passwordVariable: 'ANSIBLE_HASHICORP_VAULT_SECRET_ID']]) {
        withEnv(["VAULT_ADDR=https://vserv-eu.sos.ibm.com:8200/"]) {
            dir("blue/ansible") {
                sh "ansible-playbook -i ${ansibleEnv} setup.yml ${ansibleParam}"
                sh "ansible-playbook -i ${ansibleEnv} properties.yml ${ansibleParam}"
            }
        }
    }
    sh "sed -i -r 's/router.host=(.*)/router.host=${apiHost}/g' blue/whisk.properties"
    sh "sed -i -r 's/whisk.api.host.name=(.*)/whisk.api.host.name=${apiHost}/g' blue/whisk.properties"
    sh "sed -i -r 's/use.cli.download=false/use.cli.download=true/g' blue/whisk.properties"
    sh "cp blue/whisk.properties open/whisk.properties"
}

def installCLI(String apiHost) {
    sh "rm -f ~/.local/bin/wsk"
    sh "mkdir -p ~/.local/bin"
    sh "wget --no-check-certificate https://${apiHost}/cli/go/download/linux/386/wsk -O ~/.local/bin/wsk"
    sh "chmod +x ~/.local/bin/wsk"
    sh  "~/.local/bin/wsk property set --apihost ${apiHost}"
}

def downloadBluemixCLI(String cliRepoHost) {
    sh "rm -f ~/.local/bin/bluemix"
    sh "mkdir -p ~/.local/bin"
    sh "wget --no-check-certificate ${cliRepoHost}/download/bluemix-cli/latest/linux64 -O ~/.local/bin/bmx.tar.gz"
    sh "tar xvf ~/.local/bin/bmx.tar.gz -C ~/.local/bin"
    sh "chmod +x ~/.local/bin/Bluemix_CLI/bin/ibmcloud"
    sh "chmod +x ~/.local/bin/Bluemix_CLI/bin/cfcli/cf"
    sh "~/.local/bin/Bluemix_CLI/bin/ibmcloud"
}

def downloadBluemixCLIPlugin(String username, String password, String version) {
    sh "rm -f ~/.local/bin/bmx-wsk-plugin-linux-amd64"
    sh "mkdir -p ~/.local/bin"
    sh "wget --no-check-certificate --user ${username} --password ${password} https://na.artifactory.swg-devops.com/artifactory/openwhisk-virtual/bluemix-openwhisk-cli-plugin/${version}/bluemix_openwhisk_cli_bx_plugin-linux-amd64-${version}.tgz -O ~/.local/bin/plugin.tgz"
    sh "tar xvf ~/.local/bin/plugin.tgz -C ~/.local/bin"
    sh "mv ~/.local/bin/bmx-wsk-plugin-linux-amd64-${version} ~/.local/bin/bmx-wsk-plugin-linux-amd64"
}

def getPackageVersionPath() {
    return "blue/ansible/files/package-versions.ini"
}
def getRuntimesVersionPath() {
    return "blue/ansible/files/runtimes-versions.ini"
}

def getINIEntry(String path, String key, String value) {
    return sh(returnStdout: true, script: "cat ${path} | grep ${key} -A2 | grep ${value}= | cut -f2 -d \"=\"").trim()
}

def markINIEntryTested(String path, String key, String originalValue, String newValue) {
    def theVal = sh(returnStdout: true, script: "cat ${path} | grep ${key} --context=1 | grep ${originalValue} | cut -f2 -d \"=\"").trim()
    sh(returnStdout: false, script: """sed -i '/^\\[${key}]\$/, /^\\[/ s/^${newValue}=.*/${newValue}=${theVal}/' ${path}""")
}

// Remove those method after promotion of getINIEntry() and getProviderGitHash() to ensure provider health jobs still work
def getProviderGitHash(String provider) {
    return sh(returnStdout: true, script: "cat blue/ansible/files/package-versions.ini | grep " + provider + " --context=1 | grep git_tag | cut -f2 -d \"=\"").trim()
}

def getLatestVersionOnUcd() {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'UCD_LOGIN',
            usernameVariable: 'UCD_USERNAME', passwordVariable: 'UCD_PASSWORD']]) {
        return sh(script: "python blue/tools/jenkins/pipeline/getLatestVersionOnUcd.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD}", returnStdout: true).trim()
    }
}

def getVersionOfActiveEnvironment(String environment) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'UCD_LOGIN',
            usernameVariable: 'UCD_USERNAME', passwordVariable: 'UCD_PASSWORD']]) {
        return sh(script: "python blue/tools/jenkins/pipeline/getVersionOfActiveEnvironment.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD} ${environment}", returnStdout: true).trim()
    }
}

def deployWithUCD(String environment, String tagToDeploy) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'UCD_LOGIN',
            usernameVariable: 'UCD_USERNAME', passwordVariable: 'UCD_PASSWORD']]) {
        sh "python blue/tools/jenkins/pipeline/deployOnUcd.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD} ${environment} ${tagToDeploy}"
    }
}

def prepareAndDeploy(String environment, String version = '',String deploy = 'false', String updateRouter = 'false', String force = 'false', String url = '') {
    String flags = ""
    if (version != '') { flags = flags + "-version=${version}"}
    if (deploy == 'true') { flags = flags + ' -deploy'}
    if (updateRouter == 'true') { flags = flags + ' -updateRouter'}
    if (force == 'true') { flags = flags + ' -force'}
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'UCD_LOGIN',
            usernameVariable: 'UCD_USERNAME', passwordVariable: 'UCD_PASSWORD']]) {
        if (TARGET_ENVIRONMENT == 'yp-fra') {
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'VAULT_YP_FRA',
                    usernameVariable: 'ANSIBLE_HASHICORP_VAULT_ROLE_ID', passwordVariable: 'ANSIBLE_HASHICORP_VAULT_SECRET_ID']]) {
                sh "python blue/tools/jenkins/pipeline/prepareAndDeploy.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD} ${environment} -secretId=${env.ANSIBLE_HASHICORP_VAULT_SECRET_ID} -url=${url} ${flags}"
            }
        } else {
            sh "python blue/tools/jenkins/pipeline/prepareAndDeploy.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD} ${environment} -url=${url} ${flags}"
        }
    }
}

def runWithUCD(String environment, String processToRun) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'UCD_LOGIN',
            usernameVariable: 'UCD_USERNAME', passwordVariable: 'UCD_PASSWORD']]) {
        sh "python blue/tools/jenkins/pipeline/deployOnUcd.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD} ${environment} latest whisk ${processToRun}"
    }
}

def getLogsFromUCD(String environment, String buildNumber) {
    println ("getLogsFromUCD environment=${environment} buildNumber=${buildNumber}")
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'UCD_LOGIN',
        usernameVariable: 'UCD_USERNAME', passwordVariable: 'UCD_PASSWORD']]) {
        println ("getLogsFromUCD calling getLogs.py")
        sh "python blue/tools/jenkins/pipeline/getLogs.py ${env.UCD_USERNAME} ${env.UCD_PASSWORD} ${environment} ${buildNumber}"
    }
    println ("getLogsFromUCD finished")
}

def notifySlack(String slackChannel, String currentBuildResult, String jobName, String buildNumber, String buildUrl, boolean notifyOnAbort = true, boolean notifyOnSuccess = true) {
    if (currentBuildResult == 'UNSTABLE') {
        println("Sending a message to Slack: Jenkins: ${jobName} - #${buildNumber} Unstable (${buildUrl})")
        slackSend channel: slackChannel, color: 'warning', message: "Jenkins: ${jobName} - #${buildNumber} Unstable (<${buildUrl}|Open>)"
    } else if (currentBuildResult == 'FAILURE') {
        println("Sending a message to Slack: Jenkins: ${jobName} - #${buildNumber} Failure (${buildUrl})")
        slackSend channel: slackChannel, color: 'danger', message: "Jenkins: ${jobName} - #${buildNumber} Failure (<${buildUrl}|Open>)"
    } else if (currentBuildResult == 'ABORTED' && notifyOnAbort) {
        println("Sending a message to Slack: Jenkins: ${jobName} - #${buildNumber} Aborted (${buildUrl})")
        slackSend channel: slackChannel, color: 'danger', message: "Jenkins: ${jobName} - #${buildNumber} Aborted (<${buildUrl}|Open>)"
    } else if (notifyOnSuccess) {
        println("Sending a message to Slack: Jenkins: ${jobName} - #${buildNumber} Stable (${buildUrl})")
        slackSend channel: slackChannel, color: 'good', message: "Jenkins: ${jobName} - #${buildNumber} Stable (<${buildUrl}|Open>)"
    }
}

def createPagerDutyIncident(pdEndpoint, pdRequest) {
    println("Creating a PagerDuty incident: ${pdRequest['description']}")
    httpRequest consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: groovy.json.JsonOutput.toJson(pdRequest), url: pdEndpoint
}

def extractBuildNumber(String tagName) {
    if (tagName != null && tagName.contains("-")) {
        String[] parts = tagName.split("-")
        return parts[parts.length - 1]
    }
    return 0
}

def getWhiskVersion(host) {
    def json = new groovy.json.JsonSlurper()
    def info = new URL("https://${host}/api/v1").getText()
    return json.parseText(info).buildno
}

def generateVarzUrl(apiHost, thresholdQueueSize, thresholdUserQueueSize, thresholdInvokersDown) {
    return "https://${apiHost}/bluemix/v1/varz?thresholdQueueSize=${thresholdQueueSize}&thresholdUserQueueSize=${thresholdUserQueueSize}&thresholdInvokersDown=${thresholdInvokersDown}"
}

def addBuildDescription(build, description) {
    if (build.getDescription() == null) {
        currentBuild.setDescription(description)
    } else {
        currentBuild.setDescription(currentBuild.getDescription() + " - " + description)
    }
}

def retry(func, retries) {
    try {
        func()
    } catch (e) {
        if (retries > 1) {
            retry(func, retries-1)
        }
        else {
            throw e
        }
    }
}

@NonCPS
def getField(content, field) {
    def json = new groovy.json.JsonSlurper()
    return json.parseText(content)[field]
}

return this
