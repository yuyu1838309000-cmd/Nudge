#!/bin/sh
#
# Gradle start up script
##############################################################################
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD="maximum"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
# Increase the maximum file descriptors
if [ "$(uname)" = "Darwin" ] && [ "$MAX_FD" = "maximum" ]; then
    MAX_FD_LIMIT=$(ulimit -H -n)
    if [ $? -eq 0 ]; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ]; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
    fi
fi
# Split up the JVM_OPTS and GRADLE_OPTS values
eval splitJvmOpts() { eval "$1"='$($JAVACMD $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "'"-Dorg.gradle.appname=$APP_BASE_NAME"'" -classpath "'"$CLASSPATH"'" org.gradle.wrapper.GradleWrapperMain "$@")'; }
eval splitJvmOpts GRADLE_OPTS
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
