@echo off
setlocal
set "APP_DIR=%~dp0"
set "WAR_PATH=%APP_DIR%target\billsoft-0.0.1-SNAPSHOT.war"

if not exist "%WAR_PATH%" (
  call "%APP_DIR%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

java -jar "%WAR_PATH%"
