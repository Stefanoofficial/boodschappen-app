@echo off
set DIR=%~dp0
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "New-Item -ItemType Directory -Force '%DIR%gradle\wrapper' | Out-Null; Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%DIR%gradle\wrapper\gradle-wrapper.jar'"
  if errorlevel 1 exit /b 1
)
if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java.exe
)
"%JAVACMD%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
