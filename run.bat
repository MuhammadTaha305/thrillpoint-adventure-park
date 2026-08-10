@echo off
REM Build and run ThrillPoint Adventure Park on Windows.
REM
REM Works with either JavaFX setup:
REM   * a JDK that bundles JavaFX (Liberica Full, Zulu FX) - no flags needed
REM   * a plain JDK plus the OpenJFX SDK - set JAVAFX_HOME to its lib directory,
REM     or unzip the SDK into .\javafx-sdk next to this script
REM
REM Usage:  run.bat          build and launch the dashboard
REM         run.bat demo     build and run the race condition demo only
REM         run.bat build    build only

setlocal
cd /d "%~dp0"

set FXFLAGS=
if defined JAVAFX_HOME (
    set FXFLAGS=--module-path "%JAVAFX_HOME%" --add-modules javafx.controls
    echo Using JavaFX from JAVAFX_HOME: %JAVAFX_HOME%
) else if exist "javafx-sdk\lib" (
    set FXFLAGS=--module-path "javafx-sdk\lib" --add-modules javafx.controls
    echo Using JavaFX from .\javafx-sdk\lib
) else (
    echo No JAVAFX_HOME set - assuming your JDK bundles JavaFX.
)

if exist bin rmdir /s /q bin
mkdir bin

echo Compiling...
javac %FXFLAGS% -d bin src\*.java
if errorlevel 1 goto :eof
echo Build OK.

if "%1"=="build" goto :eof
if "%1"=="demo" (
    java %FXFLAGS% -cp bin RaceConditionDemo
) else (
    java %FXFLAGS% -cp bin Main
)

endlocal
