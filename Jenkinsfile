pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 24' 
	}

	stages {
		stage ('Nodal Core') {
			parallel {
			    /*
                 * Deploy cross platform libraries
                 */
                stage ('Cross-platform Nodal Core Libraries') {
                    agent {
                        label 'linux'
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
                                sh '''
                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
                                    -U clean deploy -DperformRelease=true
                                '''
                            }
                        }
                    }
                }
                
				/*
				 * Linux Installers and Packages
				 */
				stage ('Linux 64 bit Nodal Core') {
					agent {
						label 'linux && x86_64'
					}
					steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
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
					 		  	sh 'mvn -U -P native-image clean package'
					 		}
        				}
        				
        				tar file: 'target/nodal-core-tools-linux-x64-' + env.FULL_VERSION + '.tar.gz',
        				    glob: 'ndl*',  exclude: '*.*', overwrite: true,
        				    compress: true, dir: 'tools/target'
                        
                        tar file: 'target/nodal-core-library-linux-x64-' + env.FULL_VERSION + '.tar.gz',
                            glob: '*.so,*.h,*.txt,LICENSE',  exclude: 'libawt*,libjvm*,libjava*', overwrite: true,
                            compress: true
                
                        s3Upload(
                            consoleLogLevel: 'INFO', 
                            dontSetBuildResultOnFailure: false, 
                            dontWaitForConcurrentBuildCompletion: false, 
                            entries: [[
                                bucket: 'sshtools-public/nodal-core/' + env.FULL_VERSION, 
                                noUploadOnFailure: true, 
                                selectedRegion: 'eu-west-1', 
                                sourceFile: 'target/*', 
                                storageClass: 'STANDARD', 
                                useServerSideEncryption: false]], 
                            pluginFailureResultConstraint: 'FAILURE', 
                            profileName: 'JADAPTIVE Buckets', 
                            userMetadata: []
                        )
					}
				}
                
                /*
                 * Linux Installers and Packages
                 */
                stage ('Linux Arm 64 bit Nodal Core') {
                    agent {
                        label 'linux && aarch64'
                    }
                    steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
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
                                sh 'mvn -U -P native-image clean package'
                            }
                        }
                        
                        tar file: 'target/nodal-core-tools-linux-aarch64-' + env.FULL_VERSION + '.tar.gz',
                            glob: 'ndl*',  exclude: '*.*', overwrite: true,
                            compress: true, dir: 'tools/target'
                        
                        tar file: 'target/nodal-core-library-aarch64-x64-' + env.FULL_VERSION + '.tar.gz',
                            glob: '*.so,*.h,*.txt,LICENSE',  exclude: 'libawt*,libjvm*,libjava*', overwrite: true,
                            compress: true
                
                        s3Upload(
                            consoleLogLevel: 'INFO', 
                            dontSetBuildResultOnFailure: false, 
                            dontWaitForConcurrentBuildCompletion: false, 
                            entries: [[
                                bucket: 'sshtools-public/nodal-core/' + env.FULL_VERSION, 
                                noUploadOnFailure: true, 
                                selectedRegion: 'eu-west-1', 
                                sourceFile: 'target/*', 
                                storageClass: 'STANDARD', 
                                useServerSideEncryption: false]], 
                            pluginFailureResultConstraint: 'FAILURE', 
                            profileName: 'JADAPTIVE Buckets', 
                            userMetadata: []
                        )
                    }
                }
				
				/*
				 * MacOS installers
				 */
				stage ('Intel MacOS JADAPTIVE') {
					agent {
						label 'macos && x86_64'
					}
					steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
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
                                sh 'mvn -U -P native-image clean package'
					 		}
        				}
                        
                        tar file: 'target/nodal-core-tools-macos-x64-' + env.FULL_VERSION + '.tar.gz',
                            glob: 'ndl*',  exclude: '*.*', overwrite: true,
                            compress: true, dir: 'tools/target'
                            
                        tar file: 'target/nodal-core-library-macos-x64-' + env.FULL_VERSION + '.tar.gz',
                            glob: '*.dylib,*.h,*.txt,LICENSE',  exclude: 'libawt*,libjvm*,libjava*', overwrite: true,
                            compress: true
                
                        s3Upload(
                            consoleLogLevel: 'INFO', 
                            dontSetBuildResultOnFailure: false, 
                            dontWaitForConcurrentBuildCompletion: false, 
                            entries: [[
                                bucket: 'sshtools-public/nodal-core/' + env.FULL_VERSION, 
                                noUploadOnFailure: true, 
                                selectedRegion: 'eu-west-1', 
                                sourceFile: 'target/*', 
                                storageClass: 'STANDARD', 
                                useServerSideEncryption: false]], 
                            pluginFailureResultConstraint: 'FAILURE', 
                            profileName: 'JADAPTIVE Buckets', 
                            userMetadata: []
                        )
					}
				}
                
                /*
                 * Arm MacOS installers
                 */
                stage ('Arm MacOS JADAPTIVE') {
                    agent {
                        label 'macos && aarch64'
                    }
                    steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
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
                                sh 'mvn -U -P native-image clean package'
                            }
                        }
                        
                        tar file: 'target/nodal-core-tools-macos-aarch64-' + env.FULL_VERSION + '.tar.gz',
                            glob: 'ndl*',  exclude: '*.*', overwrite: true,
                            compress: true, dir: 'tools/target'
                            
                        tar file: 'target/nodal-core-library-macos-aarch64-' + env.FULL_VERSION + '.tar.gz',
                            glob: '*.dylib,*.h,*.txt,LICENSE',  exclude: 'reports,libawt*,libjvm*,libjava*', overwrite: true,
                            compress: true
                
                        s3Upload(
                            consoleLogLevel: 'INFO', 
                            dontSetBuildResultOnFailure: false, 
                            dontWaitForConcurrentBuildCompletion: false, 
                            entries: [[
                                bucket: 'sshtools-public/nodal-core/' + env.FULL_VERSION, 
                                noUploadOnFailure: true, 
                                selectedRegion: 'eu-west-1', 
                                sourceFile: 'target/*', 
                                storageClass: 'STANDARD', 
                                useServerSideEncryption: false]], 
                            pluginFailureResultConstraint: 'FAILURE', 
                            profileName: 'JADAPTIVE Buckets', 
                            userMetadata: []
                        )
                    }
                }
				
				/*
				 * Windows installers
				 */
				stage ('Windows Nodal Core') {
				
				    /* TEMPORARY */
				    /* when { expression { false } } */
				    
					agent {
						label 'windows && x86_64'
					}
					steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()                            
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
						configFileProvider([
					 			configFile(
                                    fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
					 				replaceTokens: true,
					 				targetLocation: 'jadaptive.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
								mavenOpts: '--add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED',
					 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
					 		) {
					 		  	bat 'mvn -U -P native-image clean package -Dbuild.projectProperties=%BUILD_PROPERTIES%'
					 		}
        				}
                        
                        zip zipFile: 'target/nodal-core-tools-windows-x64-' + env.FULL_VERSION + '.zip',
                            glob: 'ndl*',  overwrite: true,
                            dir: 'tools/target'
                            
                        zip zipFile: 'target/nodal-core-library-windows-x64-' + env.FULL_VERSION + '.zip',
                            glob: 'ndl*.dll,*.h',  exclude: 'reports,libawt*,libjvm*,libjava*', overwrite: true,
                            dir: '.'
                
                        s3Upload(
                            consoleLogLevel: 'INFO', 
                            dontSetBuildResultOnFailure: false, 
                            dontWaitForConcurrentBuildCompletion: false, 
                            entries: [[
                                bucket: 'sshtools-public/nodal-core/' + env.FULL_VERSION, 
                                noUploadOnFailure: true, 
                                selectedRegion: 'eu-west-1', 
                                sourceFile: 'target/*', 
                                storageClass: 'STANDARD', 
                                useServerSideEncryption: false]], 
                            pluginFailureResultConstraint: 'FAILURE', 
                            profileName: 'JADAPTIVE Buckets', 
                            userMetadata: []
                        )
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