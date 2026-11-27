@echo off
chcp 65001 >nul
cd /d "%~dp0"
call "%~dp0findjava.bat"
if errorlevel 1 (pause & exit /b 1)
if not exist out mkdir out
"%JBIN%javac" -encoding UTF-8 -d out src\chat\common\*.java src\chat\server\*.java src\chat\client\*.java
if %errorlevel%==0 (echo Bien dich thanh cong) else (echo Loi bien dich)
pause
