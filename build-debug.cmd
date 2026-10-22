@echo off
setlocal
rem Use Android Studio JBR when no JAVA_HOME is configured.
if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    )
)
pushd "%~dp0" || exit /b 1
if "%~1"=="" (
    call "%~dp0gradlew.bat" assembleDebug
) else (
    call "%~dp0gradlew.bat" %*
)
set "HERU_BUILD_EXIT=%ERRORLEVEL%"
popd
exit /b %HERU_BUILD_EXIT%
