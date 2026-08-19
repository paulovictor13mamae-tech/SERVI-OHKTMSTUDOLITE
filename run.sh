#!/usr/bin/env bash
# Porto Aurora — compila e executa (requer JDK 17+, sem dependências)
set -e
cd "$(dirname "$0")"
mkdir -p gamebuild
find src/main/java -name '*.java' > gamebuild/sources.txt
javac -encoding UTF-8 -d gamebuild @gamebuild/sources.txt
echo "Compilado. Iniciando PORTO AURORA..."
java -cp gamebuild ohkt.Main "$@"
