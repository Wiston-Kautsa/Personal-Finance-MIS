@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
set "MAVEN_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK not found at %JAVA_HOME%
    exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven not found at %MAVEN_HOME%
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
mvn javafx:run
