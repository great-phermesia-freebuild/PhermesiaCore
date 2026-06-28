@echo off
set JAVA_HOME=C:\Users\erick\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10
call mvnw.cmd clean package -DskipTests
pause