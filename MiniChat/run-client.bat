@echo off
cd /d "%~dp0"
call "%~dp0findjava.bat"
if errorlevel 1 (pause & exit /b 1)
if not exist out\chat\client\ChatClient.class (echo Chua bien dich, hay chay build.bat truoc & pause & exit /b 1)
start "" "%JBIN%javaw" -cp out chat.client.ChatClient
