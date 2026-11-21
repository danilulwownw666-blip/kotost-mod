# 🥭 Kotost Mod — TikTok Client (Mango Edition)

<p align="center">
  <b>Лёгкий Android-клиент для веб-версии TikTok с обходом блокировок и встроенным скачиванием видео</b>
  <br>
  <i>Built with Kotlin, Jetpack Compose Material 3 & AndroidX WebKit ProxyController</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/Language-Kotlin_1.9.22-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Proxy-SOCKS5_%7C_Xray_Core-FF8500" alt="Proxy">
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License">
</p>

---

## 🌟 Основные возможности

* **🛡️ Обход блокировок без системного VPN:**
  * Внутри приложения поднимается локальное SOCKS5-ядро на порту `127.0.0.1:10808`.
  * Весь трафик WebView маршрутизируется строго через локальный прокси с помощью `androidx.webkit.ProxyController`.
  * **Не требуется системных прав VPN**: другие приложения и мессенджеры на телефоне работают в обычном режиме без расхода батареи на глобальный VPN-туннель.

* **🔑 Поддержка популярных протоколов:**
  * **VLESS** (включая TLS, XTLS, Reality, WebSocket)
  * **Trojan**
  * **Shadowsocks**
  * **SOCKS5 / HTTP**

* **🥭 Mango Готовые Сервера в 1 клик:**
  * Быстрые пресеты для подключения без ручного ввода конфигов:
    * 🇺🇸 **Mango US** (США)
    * 🇳🇱 **Mango NL** (Нидерланды)
    * 🇩🇪 **Mango DE** (Германия)
    * 🇰🇿 **Mango KZ** (Казахстан)

* **🌍 Обход геоблока рекомендаций TikTok:**
  * Автоматический спуфинг мобильного **User-Agent** на Google Pixel 8 Pro (Chrome 124 Mobile).
  * Принудительное внедрение региональных токенов и кук (`country_code`, `my_country_code`, `store-country-code`).
  * Переключение страны рекомендаций в один тап.

* **📥 Скачивание видео в память телефона:**
  * Плавающая оранжевая кнопка **«🥭 Скачать видео»** в правом нижнем углу.
  * Находит прямой медиапоток активного ролика и загружает `.mp4` файл напрямую в системную папку `Downloads`.

---

## 📱 Установка

1. Перейдите в раздел [Releases](https://github.com/danilulwownw666-blip/kotost-mod/releases) (или скачайте готовый `Kotost_Mod_Mango.apk`).
2. Установите `.apk` на свой смартфон (разрешите установку из неизвестных источников при запросе).
3. Запустите **Kotost Mod**:
   * Выберите готовый сервер в блоке **«🥭 Mango Готовые Сервера»** либо вставьте свою ссылку (VLESS, Trojan, SS, SOCKS5).
   * Нажмите **«🥭 Подключить Mango Обход»**.
   * Нажмите **«Открыть TikTok»** и наслаждайтесь свободным просмотром и скачиванием видео!

---

## 🛠️ Сборка из исходников

### Требования
* **JDK 17** (Temurin или OpenJDK)
* **Android SDK** (API 34, Build-Tools 34.0.0)

### Команда сборки
```bash
./gradlew assembleDebug
```
Собранный файл появится по пути: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 Лицензия

Распространяется под свободной лицензией MIT.
Разработано исключительно в образовательных и исследовательских целях.
