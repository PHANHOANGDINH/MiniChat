@echo off
rem Tim JDK: JAVA_HOME -> PATH -> Android Studio JBR -> %USERPROFILE%\.jdks
set "JBIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JBIN=%JAVA_HOME%\bin\"
if defined JBIN exit /b 0
where javac >nul 2>nul && exit /b 0
for %%D in (
  "%USERPROFILE%\Downloads\android-studio\jbr"
  "%ProgramFiles%\Android\Android Studio\jbr"
  "%LOCALAPPDATA%\Programs\Android Studio\jbr"
) do if not defined JBIN if exist "%%~D\bin\javac.exe" set "JBIN=%%~D\bin\"
if defined JBIN exit /b 0
for /d %%D in ("%USERPROFILE%\.jdks\*" "%ProgramFiles%\Java\jdk*" "%ProgramFiles%\Eclipse Adoptium\jdk*") do if not defined JBIN if exist "%%~D\bin\javac.exe" set "JBIN=%%~D\bin\"
if defined JBIN exit /b 0
echo Khong tim thay JDK 17+. Hay cai JDK va dat bien JAVA_HOME.
exit /b 1
