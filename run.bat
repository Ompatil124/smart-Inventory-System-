@echo off
title Smart Inventory System

:: ── Step 1: Compile all Java sources into out\ ───────────────────────────────
echo Compiling...

set JAVA_HOME=C:\progamming language\java
set CP_LIBS=target\lib\zxing-core.jar;target\lib\zxing-javase.jar;target\lib\webcam-capture.jar;target\lib\slf4j-api.jar;target\lib\bridj.jar

if not exist out mkdir out

:: Collect all .java files into a temporary list
dir /s /b "src\main\java\*.java" > _sources.txt

"%JAVA_HOME%\bin\javac.exe" -cp "%CP_LIBS%" -d out "@_sources.txt" 2>&1
del _sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo !! Compilation failed. See errors above.
    pause
    exit /b 1
)

:: ── Step 2: Run ───────────────────────────────────────────────────────────────
echo Starting Smart Inventory System...
"%JAVA_HOME%\bin\java.exe" -cp "out;%CP_LIBS%" com.inventory.Main

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited with error. Check the console above.
    pause
)
