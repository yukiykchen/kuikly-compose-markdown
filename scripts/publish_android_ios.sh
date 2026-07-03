export QN_COMPAT_BUILD_TYPE=android
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :markdown:publishAllPublicationsToMavenRepository --stacktrace