@echo off
rem Porto Aurora - compila e executa (requer JDK 17+, sem dependencias)
setlocal
cd /d "%~dp0"
if not exist gamebuild mkdir gamebuild
dir /s /b src\main\java\*.java > gamebuild\sources.txt
javac -encoding UTF-8 -d gamebuild @gamebuild\sources.txt
if errorlevel 1 (
  echo Falha na compilacao.
  pause
  exit /b 1
)
echo Compilado. Iniciando PORTO AURORA...
java -cp gamebuild ohkt.Main %*
pause
