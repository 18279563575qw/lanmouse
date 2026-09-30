@echo off
setlocal
set SCRIPT_DIR=%~dp0
set WORKSPACE_ROOT=%SCRIPT_DIR%..\..
set GRADLE_USER_HOME=%WORKSPACE_ROOT%\.gradle\user-home
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%bootstrap-gradle.ps1"
if errorlevel 1 exit /b %errorlevel%
call "%WORKSPACE_ROOT%\.gradle\lanmouse-gradle\gradle-8.9\bin\gradle.bat" %*
exit /b %errorlevel%