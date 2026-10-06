@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------
@if "%DEBUG%"=="" @echo off
@setlocal

set MAVEN_HOME=%USERPROFILE%\.m2
set WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%WRAPPER_JAR%'"
    if not exist "%WRAPPER_JAR%" (
        echo Error: Failed to download maven-wrapper.jar
        exit /b 1
    )
)

@REM Remove trailing backslash from %~dp0 for proper -D parameter handling
set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

set MVN_CMD=java "-Dmaven.multiModuleProjectDirectory=%PROJECT_DIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain
%MVN_CMD% %*
