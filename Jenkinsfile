pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 21' 
	}

	stages {
		stage ('Nodal Core') {
			parallel {
			    /*
                 * Deploy cross platform libraries
                 */
                stage ('Cross-platform Nodal Core Libraries') {
                    agent {
                        label 'any'
                    }
                    steps {
                        configFileProvider([
                                configFile(
                                    fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
                                    replaceTokens: true,
                                    targetLocation: 'jadaptive.build.properties',
                                    variable: 'BUILD_PROPERTIES'
                                )
                            ]) {
                            withMaven(
                                globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                            ) {
                                sh 'mvn -U clean deploy'
                            }
                        }
                    }
                }
            }
		}
	}
}

/* Create full version number from Maven POM version and the build number
 *
 * TODO make into a reusable library - https://stackoverflow.com/questions/47628248/how-to-create-methods-in-jenkins-declarative-pipeline
 */
String getFullVersion() {
    def pom = readMavenPom file: "pom.xml"
    pom_version_array = pom.version.split('\\.')
    suffix_array = pom_version_array[2].split('-')
    return pom_version_array[0] + '.' + pom_version_array[1] + "." + suffix_array[0] + "-${BUILD_NUMBER}"
}