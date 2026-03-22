#!/bin/sh
# Gradle wrapper script (Unix)
# See https://docs.gradle.org/current/userguide/gradle_wrapper.html

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine Java executable
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
