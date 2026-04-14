@echo off
setlocal
if not exist out mkdir out
set SRCS=
for /r "src\main\java" %%f in (*.java) do set SRCS=!SRCS! "%%f"
setlocal enabledelayedexpansion
set SRCS=
for /r "src\main\java" %%f in (*.java) do set SRCS=!SRCS! "%%f"
set CP=target\lib\zxing-core.jar;target\lib\zxing-javase.jar;target\lib\webcam-capture.jar;target\lib\slf4j-api.jar;target\lib\bridj.jar
javac -cp "%CP%" -d out %SRCS% 2>&1
if %ERRORLEVEL% EQU 0 (echo BUILD_SUCCESS) else (echo BUILD_FAILED)
endlocal
