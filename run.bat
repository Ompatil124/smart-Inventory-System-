@echo off
title Smart Inventory System

:: Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [System] Found Maven. Running with 'mvn exec:java'...
    mvn clean compile exec:java
    goto end
)

:: Fallback if Maven isn't found - assumes Java is in PATH and target/lib exists
echo [System] Maven not found. Attempting manual run...

if not exist out mkdir out

:: Collect all .java files
dir /s /b "src\main\java\*.java" > _sources.txt

:: CP_LIBS target folder created by Maven previously
set CP_LIBS=target\lib\zxing-core.jar;target\lib\zxing-javase.jar;target\lib\webcam-capture.jar;target\lib\slf4j-api.jar;target\lib\bridj.jar

echo Compiling...
javac -cp "%CP_LIBS%" -d out "@_sources.txt"
del _sources.txt

echo Starting...
java -cp "out;%CP_LIBS%" com.inventory.Main

:end
if %ERRORLEVEL% NEQ 0 pause
