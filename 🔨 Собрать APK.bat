@echo off
chcp 65001 >nul
title Сборка Kotost Mod APK

echo ========================================================
echo       Сборка Android-приложения Kotost Mod (TikTok)
echo ========================================================
echo.

set JAVA_HOME=C:\Users\daily\AppData\Local\Programs\jdk-17\jdk-17.0.10+7
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "%~dp0"

echo Запуск Gradle сборки...
call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  [УСПЕХ] APK успешно собран!
    echo ========================================================
    echo.
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        copy /y "app\build\outputs\apk\debug\app-debug.apk" "Kotost_Mod_Debug.apk" >nul
        echo Файл скопирован в: %~dp0Kotost_Mod_Debug.apk
    )
) else (
    echo.
    echo ========================================================
    echo  [ОШИБКА] Сборка завершилась с ошибкой.
    echo ========================================================
)

echo.
pause
