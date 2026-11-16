# Органайзер — Школьное и студенческое расписание

Android-приложение на **Kotlin + Jetpack Compose + Material 3 (Dynamic Color + DayNight OLED) + Room + Hilt + Jetpack Glance**, а также интерактивный веб-симулятор.

## Структура репозитория

- `android/` — исходный код нативного Android-приложения (`minSdk 26`, `targetSdk 35`)
  - `domain/` — доменные модели, контракты и UseCase (включая экспорт `.ics`)
  - `data/` — база данных Room, DAO, DataStore настроек, начальные данные
  - `ui/` — экраны Jetpack Compose, ViewModel, тема Material 3 (`DayNight` + OLED)
  - `notification/` — уведомления за 15 минут до урока (`AlarmManager` + каналы API 26+)
  - `widget/` — виджет рабочего стола «Уроки на сегодня» на Jetpack Glance
- `src/` — интерактивный веб-симулятор и обозреватель Kotlin-кода
- `.github/workflows/build-apk.yml` — автоматическая сборка `.apk` в GitHub Actions
- `apk/` — папка для собранного `Organizer-debug.apk`

## Автоматическая сборка APK

После отправки изменений в ветку `main` workflow **Build Android APK** (`.github/workflows/build-apk.yml`) автоматически:
1. Собирает `android/app/build/outputs/apk/debug/app-debug.apk`.
2. Сохраняет готовый файл в `apk/Organizer-debug.apk` и загружает его в артефакты сборки.

Запуск вручную в GitHub: откройте вкладку **Actions → Build Android APK → Run workflow**.
