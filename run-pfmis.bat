@echo off
setlocal

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    )
)

if defined MAVEN_HOME (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        set "PATH=%MAVEN_HOME%\bin;%PATH%"
    )
)

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install JDK 21 or newer, or set JAVA_HOME.
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven was not found. Install Maven, add it to PATH, or set MAVEN_HOME.
    exit /b 1
)

mvn compile javafx:run
