@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Mini Chat Server
call "%~dp0findjava.bat"
if errorlevel 1 (pause & exit /b 1)
if not exist out\chat\server\ChatServer.class (echo Chua bien dich, hay chay build.bat truoc & pause & exit /b 1)
"%JBIN%java" -Dstdout.encoding=UTF-8 -cp out chat.server.ChatServer 5000 users.txt
pause
