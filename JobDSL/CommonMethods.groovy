/*
*This function is required to untar or extract the JQ tar file from Software folder.
*/
def extract_jq(){
    echo "Extracting the jq software"
    sh "tar -xvf Software/jq-1.0.1.tar ; chmod +x ./jq"
}

/*
*This function is required to download the kube config file of specified deployment from DIT page.
*/
def download_kube_config_file_from_dit(){
    env.kube_config_document_id= sh (script : "curl -4 -s \"http://atvdit.athtem.eei.ericsson.se/api/deployments/?q=name=$environment_name\" |./jq '.[].documents[] | select(.schema_name==\"cloud_native_enm_kube_config\") | .document_id' | sed 's/\"//g'", returnStdout: true).trim()
    env.KUBE_CRED =  sh (script : "curl -4 -s \"https://atvdit.athtem.eei.ericsson.se/api/documents/$kube_config_document_id\" | ./jq '.name' | sed 's/\"//g'", returnStdout: true).trim()

    env.site_information_document_id= sh (script : "curl -4 -s \"http://atvdit.athtem.eei.ericsson.se/api/deployments/?q=name=$environment_name\" |./jq '.[].documents[] | select(.schema_name==\"cENM_site_information\") | .document_id' | sed 's/\"//g'", returnStdout: true).trim()
    sh "curl -4 --location --request GET 'https://atvdit.athtem.eei.ericsson.se/api/documents/$site_information_document_id'>deployment_site_config_information.json"
    env.CLIENT_MACHINE_IP_ADDRESS = sh (script : "./jq '.content.global.client_machine.ipaddress' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
    env.CLIENT_MACHINE_USERNAME = sh (script : "./jq '.content.global.client_machine.username' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
    env.CLIENT_MACHINE_TYPE = sh (script : "./jq '.content.global.client_machine.type' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()

    
    sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' -tt ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'mkdir -p ${Client_HOME}/conf/'"
    sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' -tt ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'rm -rf ${Client_HOME}/conf/${KUBE_CRED}'"
    sh "scp -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${workspace}/Kube-Config-Files/${KUBE_CRED} ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}:${Client_HOME}/conf/${KUBE_CRED}"
    sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' -tt ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'chmod 620 ${Client_HOME}/conf/${KUBE_CRED}'"
}
}


/*
*This function is required to read the values from the integration_value_file and site_information file of specified deployment from DIT page.
*/
def read_site_config_info_from_dit(){

    env.site_information_document_id= sh (script : "curl -4 -s \"http://atvdit.athtem.eei.ericsson.se/api/deployments/?q=name=$environment_name\" |./jq '.[].documents[] | select(.schema_name==\"cENM_site_information\") | .document_id' | sed 's/\"//g'", returnStdout: true).trim()
    env.integration_value_file_document_id= sh (script : "curl -4 -s \"http://atvdit.athtem.eei.ericsson.se/api/deployments/?q=name=$environment_name\" |./jq '.[].documents[] | select(.schema_name==\"cENM_integration_values\") | .document_id' | sed 's/\"//g'", returnStdout: true).trim()

    sh "curl -4 --location --request GET 'https://atvdit.athtem.eei.ericsson.se/api/documents/$site_information_document_id'>deployment_site_config_information.json"
    sh "curl -4 --location --request GET 'https://atvdit.athtem.eei.ericsson.se/api/documents/$integration_value_file_document_id'>deployment_integration_values_file.json"

    env.CLIENT_MACHINE_IP_ADDRESS = sh (script : "./jq '.content.global.client_machine.ipaddress' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
    env.CLIENT_MACHINE_USERNAME = sh (script : "./jq '.content.global.client_machine.username' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
    env.CLIENT_MACHINE_TYPE = sh (script : "./jq '.content.global.client_machine.type' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()

    env.RWX_STORAGE_CLASS = sh (script : "./jq '.content.global.rwx.storageClass' deployment_integration_values_file.json| sed 's/\"//g'", returnStdout: true).trim()
    env.STORAGE_CLASS = sh (script : "./jq '.content.global.persistentVolumeClaim.storageClass' deployment_integration_values_file.json | sed 's/\"//g'", returnStdout: true).trim()
    env.HELM_BINARY = "helm"
    

    env.kubeConfig = "${Client_HOME}/conf/${KUBE_CRED}"
    env.cenmbuildutilities_client= "docker run --rm  -v ${Client_HOME}/conf/${KUBE_CRED}:/root/.kube/config -v ${Client_HOME}:${Client_HOME} -v /root/.docker/config.json:/root/.docker/config.json --workdir ${Client_HOME}/conf ${cenm_utilities_docker_image}"
    env.helm = "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} '${cenmbuildutilities_client} helm'"
    env.kubectl = "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} '${cenmbuildutilities_client} kubectl'"


    env.TARGET_DOCKER_REGISTRY_URL = sh (script : "./jq '.content.global.registry.hostname' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
    def TARGET_DOCKER_REGISTRY_URL_WITH_PORT = TARGET_DOCKER_REGISTRY_URL.tokenize(":");
        if (TARGET_DOCKER_REGISTRY_URL_WITH_PORT.size() == 2) {
           TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT = TARGET_DOCKER_REGISTRY_URL_WITH_PORT[TARGET_DOCKER_REGISTRY_URL_WITH_PORT.size()-2]
        }else{
           TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT = "${TARGET_DOCKER_REGISTRY_URL}"
           println TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT
           println TARGET_DOCKER_REGISTRY_URL
        }
        env.TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT = TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT
        env.CONTAINER_REGISTRY_USERNAME = sh (script : "./jq '.content.global.registry.users.username' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
        env.CONTAINER_REGISTRY_PASSWORD = sh (script : "./jq '.content.global.registry.users.password' deployment_site_config_information.json| sed 's/\"//g'", returnStdout: true).trim()
        env.cenm_build_utilities_docker_image = "docker run --rm -v ${kubeConfig}:/root/.kube/config -v ${WORKSPACE}:${WORKSPACE} -v /home/lciadm100/:/home/lciadm100/ --workdir ${WORKSPACE} ${cenm_utilities_docker_image}"
    

}


/*
 *This function is required to connect to the respective environment through kube-config file.
 */

def set_kube_config_file(){
    sh 'mkdir -p ${PWD}/.kube && chmod 775 ${PWD}/.kube && cp -v ${PWD}/Kube-Config-Files/${KUBE_CRED} ${PWD}/.kube/${KUBE_CRED} && chmod 620 ${PWD}/.kube/${KUBE_CRED}'
}

def client_cleancsar() {
    sh '''
    ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}  'sudo rm -rf '\${Client_HOME}'/CNIV/*||true'
    ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}  'sudo find /tmp/*.log -mtime +30 -exec rm -f {} \\;||true'
    ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}  'mkdir -p '\${Client_HOME}'/CNIV'
    '''
}


def delete_and_pull_utilitiesimage_clientmachine()
{
    images = sh (script: '''ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} docker images|awk '{ print $1":"$2 }' ''', returnStdout: true ).trim()
    if ( images.contains("${cenm_utilities_docker_image}") ){
       sh '''ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} docker rmi -f "${cenm_utilities_docker_image}" '''
       echo "${cenm_utilities_docker_image} image removed from the clinet machine"
       }
       sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} docker pull ${cenm_utilities_docker_image}"
       echo "${cenm_utilities_docker_image} image pulled successfully"
}


/*
 *This function is required to create docker secret.
 */

def create_docker_secret(){
    env.PULLSECRET = 'cniv-secret'
    env.NAMESPACE = 'cniv'
    sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'sudo docker login  ${TARGET_DOCKER_REGISTRY_URL} --username=${CONTAINER_REGISTRY_USERNAME} --password=${CONTAINER_REGISTRY_PASSWORD}' "
    sh "${kubectl} delete secret ${PULLSECRET}  --namespace ${NAMESPACE}|| true "
    sh "${kubectl} create secret generic ${PULLSECRET} --from-file=.dockerconfigjson=/root/.docker/config.json --type=kubernetes.io/dockerconfigjson --namespace ${NAMESPACE}"
}

def download_csar_package_nexus(){
    if(env.csar_package_version != 'null'){
        echo "Download the CSAR package from nexus area"
        sh " ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'sudo curl -4 -s --noproxy \"*\" -L ${nexus_repositoryUrl}/cENM/csar/${csar_package_name}/${csar_package_version}/${csar_package_name}-${csar_package_version}.csar -o ${Client_HOME}/CNIV/${csar_package_name}-${csar_package_version}.csar' "

    }
}

/*
 *This function is required to extract the csar package.
 */

def extract_csar_package(){
    sh "ssh -o ' LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'sudo unzip -o ${Client_HOME}/CNIV/${csar_package_name}-${csar_package_version}.csar -d ${Client_HOME}/CNIV/' "
    
    env.CSAR_PACKAGE="${csar_package_name}-${csar_package_version}.csar"
}


/*
 *This function is required to run the csar_utils.sh script after the extraction of csar with the respective docker registry url.
 */

def csar_utils(){
    sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'bash ${Client_HOME}/CNIV/Scripts/cniv_csar_utils.sh  --docker-registry-url=${TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT}'"
}


def client_integrationvaluepath(){
        sh "mkdir -p ${HOME_DIR}/CNIV/Scripts/"
        sh "scp -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}:${Client_HOME}/CNIV/Scripts/${integration_value_type}* ${HOME_DIR}/CNIV/Scripts/"
        env.integration_values_file_path= sh (script: "ls ${HOME_DIR}/CNIV/Scripts/|grep ${integration_value_type}*", returnStdout: true ).trim()


        sh "scp -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}:${Client_HOME}/CNIV/Scripts/eric-cniv-enm-global-values* ${HOME_DIR}/CNIV/Scripts/"
        env.cniv_global_values_file_path= sh (script: "ls ${HOME_DIR}/CNIV/Scripts/|grep eric-cniv-enm-global-values*", returnStdout: true ).trim()
}

def client_pushfile(){
        sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'sudo chmod 777 ${Client_HOME}/cENM/Scripts/${integration_value_type}*'"
        sh "scp  -o ' LogLevel=error' -o 'StrictHostKeyChecking no' ${HOME_DIR}/cENM/Scripts/${integration_values_file_path} ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}:${Client_HOME}/cENM/Scripts/"
        sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'sudo chmod 664 ${Client_HOME}/cENM/Scripts/${integration_values_file_path}*'"
}

def available_nodes(){
        env.TOTAL_NODES= sh "${kubectl} get nodes --no-headers | wc -l", returnStdout: true ).trim().toInteger()
        env.TAINTED_NODES= sh "${kubectl} get nodes -o jsonpath='{range .items[?(@.spec.taints)]}{.metadata.name}{"\t"}{.spec.taints}{"\n"}{end}' | wc -l'", returnStdout: true ).trim().toInteger()
        def AVAILABLE_NODES = Math.max(env.TOTAL_NODES - env.TAINTED_NODES, 0)
        return AVAILABLE_NODES
}

/*
 * This function is required to Update the integration values.yaml file with site-config properties
 */

def updateIntegrationValues() {
    try {
        def integration_value_map = [
            'global.registry.url':'TARGET_DOCKER_REGISTRY_URL',
            'global.registry.pullSecret':'PULLSECRET',
            'global.persistentVolumeClaim.storageClass':'STORAGE_CLASS',
            'global.rwx.storageClass':'RWX_STORAGE_CLASS',
            'global.nodes':'AVAILABLE_NODES',
            'global.csarVersion':'CSAR_PACKAGE'
            ''
            ]
        def list_all_environment = env.getEnvironment()
        filename ="${HOME_DIR}/CNIV/Scripts/${cniv_global_values_file_path}"
        values = readYaml file: filename
       for (parameter in integration_value_map){
         def value = parameter.value
         def key = parameter.key
         if (integration_values_file_keys_exists( values, key, value)){
               if ( value == "TARGET_DOCKER_REGISTRY_URL"){
                   value="TARGET_DOCKER_REGISTRY_URL_WITH_OUT_PORT"
               }
               value= list_all_environment.get(value)
               if(value=='null') {
                   value=null
              }
              if(value=='true') {
                   value=true
              }
              if(value=='false') {
                   value=false
              }
               update_IntegrationValues_with_values(key,value)
         }
       }
      }
        sh "rm -f ${filename}"
        writeYaml file: filename, data: values
        sh "sed -i \"/port/s/'//g\" ${filename}"
        if ("${ANNOTATIONS}" != null && "${ANNOTATIONS}" != '{}' && "${ANNOTATIONS}" != 'null'){
            sh "sed -i '{/{\$/d}' ${filename}"
            sh "sed -i 's/|-/{/g' ${filename}"
            sh "sed -i 's/\\\\/\"/g' ${filename}"
        }
    } catch( err ) {
        echo "$err"
        sh "exit 1"
    }
}

/*
 * This function will update value of the key in integration values.yaml file.
 */

def update_IntegrationValues_with_values(keys,value){
     def str = keys.tokenize(".");
     if ('annotations' in str && ( value == null  || value == '{}' || value == 'null') ){
     }
     else{
         def val= [:]
         val.putAll(values)
         for (parameter in str) {
                if (val.containsKey(parameter)) {
                val = val[parameter]
                }
             }
             def bindings = [:]
             def map = bindings
             if (val == null || ( val != null && value != null ) ) {
                    for (i = 0; i < str.size(); i++) {
                         def part = str[i];
                         if (!map.containsKey(part)) {
                         map[part] = values.get(part)
                         }
                         if (i == str.size() - 1) {
                              map[part] = value
                         }
                         else {
                              map = map[part]
                         }
                   }
         values.putAll(bindings)
         }
      }
}



/*
 * create an environment variable at the start of the cENM UG and II pipeline
 */

def start_time_deployment(){
     env.start_time = sh (script: "date +\'%F %T\'", returnStdout: true ).trim()
     echo "start_time :${start_time}"
}

/*
 * create an environment variable at the end of the cENM UG and II pipeline
 */

def end_time_deployment(){
    env.end_time = sh (script: "date +\'%F %T\'", returnStdout: true ).trim()
    echo "end_time :${end_time}"
}


/*
 *This stateless_integration_chart function performs the install or upgrade based on its deploy type.i
 */

def cniv_integration_chart(deploy_type,timeout){

        env.revision = sh (script : "${helm} list -n ${NAMESPACE} | grep eric-cniv-enm-integration-chart | awk \'{print \$(NF-7)}\'", returnStdout: true).trim()
        if (env.revision == null){
             env.revision = 0
             }
            echo "${revision}"
        sh("ssh -o LogLevel=error -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'docker run --rm -d -v ${kubeConfig}:/root/.kube/config -v ${Client_HOME}:${Client_HOME} --workdir ${Client_HOME}/conf armdocker.rnd.ericsson.se/proj-enm/cenm-build-utilities:latest  helm install eric-cniv-enm-integration-chart-${csar_package_version} -f ${Client_HOME}/CNIV/Scripts/${integration_values_file_path} -f ${Client_HOME}/CNIV/Scripts/${cniv_global_values_file_path} -n ${NAMESPACE} '")
        sh "sleep 300s"
        sh '''
              while [[ true ]];
                  do
                if [[ "$(ssh -o LogLevel=error -o 'StrictHostKeyChecking no' "${CLIENT_MACHINE_USERNAME}"@"${CLIENT_MACHINE_IP_ADDRESS}" ''\${cenmbuildutilities_client}' helm list -n '\${NAMESPACE}' | grep eric-enm-stateless-integration-'\${NAMESPACE}' | awk \"{print \\\$(NF-7)}\" ')" == \$((revision+1)) && "$(ssh -o LogLevel=error -o 'StrictHostKeyChecking no' "${CLIENT_MACHINE_USERNAME}"@"${CLIENT_MACHINE_IP_ADDRESS}" ''\${cenmbuildutilities_client}' helm list --all -n '\${NAMESPACE}'|grep eric-cniv-enm-integration-chart-'\${NAMESPACE}'')" == *"deployed"* ]]; then
                         break
                elif [[ "$(ssh -o LogLevel=error -o 'StrictHostKeyChecking no' "${CLIENT_MACHINE_USERNAME}"@"${CLIENT_MACHINE_IP_ADDRESS}" ''\${cenmbuildutilities_client}' helm list -n '\${NAMESPACE}' | grep eric-enm-stateless-integration-'\${NAMESPACE}' | awk \"{print \\\$(NF-7)}\" ')" == \$((revision+1)) && "$(ssh -o LogLevel=error -o 'StrictHostKeyChecking no' "${CLIENT_MACHINE_USERNAME}"@"${CLIENT_MACHINE_IP_ADDRESS}" ''\${cenmbuildutilities_client}' helm list --all -n '\${NAMESPACE}'|grep eric-cniv-enm-integration-chart-'\${NAMESPACE}'')" == *"failed"* ]]; then

                         exit 1
                else
                         logger "Waiting for stateless to get to deployed ...";
                         echo "${revision}"
                         sleep 300s ;
                fi
             done
           '''
}


def checking_error_pods(){
        def PodStatus = sh(script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}\' kubectl -n ${NAMESPACE} get pods | egrep \"ImagePullBackOff|CrashLoopBackOff|Error|Pending|ErrImagePull\" | awk \" {print \\\$1} {print \\\$3} \" ||  true '", returnStdout: true).trim()
        echo " Pod status ${PodStatus}"
        if(PodStatus == ""){
            echo "All pods are up and running"
        }
        else{
            echo "Below pods are not up and running"
            def report = sh(script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}\' kubectl -n ${NAMESPACE} get pods | egrep \"ImagePullBackOff|CrashLoopBackOff|Error|Pending|ErrImagePull\" || true '", returnStdout: true).trim()
                echo "${report}"
                sh "exit 1"
        }
    }

}

/*
* This function is required to capture the status of running pods
 */

def checkrunningpods(){
    {
        sh'''
        ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} "${cenmbuildutilities_client}" kubectl get pods -n ${NAMESPACE} | grep Running | awk '{split($2,a,"/"); sum1+=a[1]; sum2+=a[2]} END {print sum1; print sum2; if(sum1==sum2){print "all pods are up and running"} else{print "all pods are not up and running, sleeping for 60 seconds..."; system("sleep 60"); exit 1}}'
        '''
    }
}

def cnivReport(){
    {
        sh "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'bash ${Client_HOME}/CNIV/Scripts/getlogs.sh -n CNIV -h "
        sh "scp -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS}:${Client_HOME}/CNIV/Scripts/logs/index.html ${HOME_DIR}/CNIV/Scripts/${environment_name}_report.html"
    }

}

/*
 * This function is required to capture the status of healthy pods
 */


def checkHealthyPods(){
    {
        desiredStatefulset = sh ( script: "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get sts |  grep -v 'NAME' | awk -F\" \" \" {stssum += \\\$2} END {print stssum}\" '", returnStdout: true ).trim()
        echo "${desiredStatefulset}"
        desiredDeployment = sh ( script: "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get deploy |  grep -v 'NAME' | awk -F\" \" \" {depsum += \\\$2} END {print depsum}\" '", returnStdout: true ).trim()
        echo "${desiredDeployment}"
        currentHealthyPods = sh ( script: "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get pods   | grep -v 'NAME' | grep 'Running' | awk -F\" \" \"{print \\\$2}\" | awk -F\"/\" \"{podsum += \\\$1} END {print podsum}\" '", returnStdout: true ).trim()
        echo "${currentHealthyPods}"
    }

    desiredDeploy=desiredDeployment.toInteger()
    currentHealthy=currentHealthyPods.toInteger()
    desiredStateful=desiredStatefulset.toInteger()
    if ( desiredStateful + desiredDeploy <= currentHealthy ) {
        echo "Desired:Statefulset + Desired:Deployment is lesser than or equal to Current: Running Healthy Pods"
    } else {
        echo "Desired:Statefulset + Desired:Deployment is not matching Current: Running Healthy Pods"
        sh "sleep 60"
        sh "exit 1"
    }
}

def cniv_uninstall(){
    sh("ssh -o LogLevel=error -o 'StrictHostKeyChecking no' ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} 'docker run --rm -d -v ${kubeConfig}:/root/.kube/config -v ${Client_HOME}:${Client_HOME} --workdir ${Client_HOME}/conf armdocker.rnd.ericsson.se/proj-enm/cenm-build-utilities:latest  ${helm} uninstall eric-cniv-enm-integration-chart-${csar_package_version} -n ${NAMESPACE} || true '")
    sh '''
        while [[ "$(${kubectl} get pods -n ${NAMESPACE})" == *"Terminating"* ]];
        do
            logger "Waiting for pods to terminate ...";
            sleep 30 ;
        done
       '''
    

    sh "${kubectl} delete pvc pg-data-postgres-0 -n ${NAMESPACE} || true"
    sh "${kubectl} delete pvc pg-data-postgres-1 -n ${NAMESPACE} || true"
    sh "${kubectl} delete pvc elasticsearch-data-0 -n ${NAMESPACE} || true"
    sh "${kubectl} delete pvc elasticsearch-data-1 -n ${NAMESPACE} || true"
    sh "${kubectl} delete pvc elasticsearch-master-0 -n ${NAMESPACE} || true"
    sh "${kubectl} delete pvc elasticsearch-master-1 -n ${NAMESPACE} || true"
    sh "${kubectl} delete pvc elasticsearch-master-2 -n ${NAMESPACE} || true"
    sh "${kubectl} delete jobs eric-enm-update-software-version-history-job -n ${NAMESPACE} || true"

}

/*
 *This function is required for generating the artifact properties.
 */

def generate_artifact_file(){
    archiveArtifacts "${HOME_DIR}/CNIV/Scripts/${environment_name}_report.html"
}

/*
 * This function will captures the report of the deployment and sends the report through mail
 */

def emailReport(content){

    def subject = "${env.JOB_NAME} - Build #${env.BUILD_NUMBER} - ${currentBuild.currentResult}!"

    try {
        emailext(body: content, mimeType: 'text/html', from: 'jenkins_monitoring@ericsson.com',
            subject: subject,
            to: "${EMAIL_LIST}" )
    } catch( err ){
        echo "$err"
    }
}

/* 
 * This function will generates report of the deployment.
 */

def reportHeading(status){
        def deployResult
        def color
        def investigation

        if(status == 'Success'){
                deployResult = "Success"
                color = "#9ACD32"
                investigation = "NA"
          } else if(status == 'Failed'){
                deployResult = "Failed"
                color = "#DC143C"
                investigation = "(If applicable), will be included in a follow up email"
          } else if(status == 'Aborted'){
                deployResult = "Aborted"
                color = "#95A5A6"
                investigation = "(If applicable), will be included in a follow up email"
          }
        def report = "Hi All,\n\n <h2>${environment_name} Deployment Result: <span style=\"background-color:${color};\">${status}</span></h2>"
        return report
}

/* 
 * This function will print the list of unstable pods after Upgrade
 */

def printPods(){
      {
        env.RestartPods = sh (script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get pods --sort-by='.status.containerStatuses[0].restartCount' --no-headers  | awk \"\\\$4>0 {print \\\$0}\"'", returnStdout: true)
        env.failedState = sh (script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get pods | egrep \"NAME|Running\" | egrep \"0/|NAME\" '" , returnStdout: true)
        env.initState = sh (script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get pods | grep -v Running |grep -v Completed || true '", returnStdout: true)
        env.sidecarhttpd= sh (script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get pods | egrep \"NAME|Running\" | egrep \"1/2|NAME\" '" , returnStdout: true)
        env.sidecarmonitoring= sh (script : "ssh -o 'LogLevel=error' -o 'StrictHostKeyChecking no'  ${CLIENT_MACHINE_USERNAME}@${CLIENT_MACHINE_IP_ADDRESS} ''\${cenmbuildutilities_client}' kubectl -n ${NAMESPACE} get pods | egrep \"NAME|Running\" | egrep \"2/3|NAME\" '" , returnStdout: true)
       }

        def report = "<ul><li><strong>Please find the Restart Pods in Deployment:</strong> \n<pre>${RestartPods}</pre> \n\n\n #kubectl -n ${NAMESPACE} get pods | egrep \'NAME|Running\' | egrep \'0/|NAME\' \n<pre>${failedState}</pre> \n\n\n #kubectl -n ${NAMESPACE} get pods | grep -v Running \n<pre>${initState}</pre> \n\n\n #kubectl -n ${NAMESPACE} get pods | egrep \'NAME|Running\' | egrep \'1/2|NAME\' \n<pre>${sidecarhttpd}</pre> \n\n\n #kubectl -n ${NAMESPACE} get pods | egrep \'NAME|Running\' | egrep \'2/3|NAME\' \n<pre>${sidecarmonitoring}</pre>"
        echo "${report}"
        report = report.replace("\n","</br>")
        report += "<style> pre {display: block;font-family: Lucida Console, Monaco, monospace; white-space: pre;} </style>";
        return report
}