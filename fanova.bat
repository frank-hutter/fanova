@echo off
set SMACMEM=128
IF NOT "%SMAC_MEMORY%"=="" (set SMACMEM=%SMAC_MEMORY%)
set DIR=%~dp0
set EXEC=net.aclib.fanova.FAnovaExecutor
set jarconcat=
SETLOCAL ENABLEDELAYEDEXPANSION
for /F "delims=" %%a IN ('dir /b /s "%DIR%\*.jar"') do set jarconcat=%%a;!jarconcat!
echo Starting fanova with %SMACMEM% MB of RAM
java -Xmx%SMACMEM%m -cp "%DIR%conf\;%DIR%patches\;%jarconcat%%DIR%patches\ " %EXEC% %*
