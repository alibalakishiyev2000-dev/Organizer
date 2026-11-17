import React, { useEffect, useMemo, useState } from 'react';
import { Clock, Moon, Sun } from 'lucide-react';
import { CodeExplorer } from './components/CodeExplorer';
import { OrganizerLogo } from './components/OrganizerLogo';
import { PhoneSimulator } from './components/PhoneSimulator';
import { WidgetPreview } from './components/WidgetPreview';
import {
  DYNAMIC_SEEDS,
  createInitialHomework,
  createInitialLessons,
  filterLessonsForDate,
  minutesToTime,
  resolveIsEvenWeek,
} from './data/initialData';
import type {
  DynamicSeedId,
  HomeworkTask,
  Lesson,
  UserPreferences,
} from './types/organizer';

type MainSection = 'simulator' | 'code' | 'guide';

export function App() {
  const [section, setSection] = useState<MainSection>('simulator');
  const [today] = useState<Date>(() => new Date());
  const [lessons, setLessons] = useState<Lesson[]>(() => createInitialLessons(new Date()));
  const [homework, setHomework] = useState<HomeworkTask[]>(() =>
    createInitialHomework(new Date())
  );

  // Симулируемое время суток (по умолчанию 09:15 — идёт первый урок) для наглядной демонстрации прогресс-бара
  const [nowMinutes, setNowMinutes] = useState<number>(9 * 60 + 15);
  const [preferences, setPreferences] = useState<UserPreferences>({
    themeMode: 'SYSTEM',
    dynamicColor: true,
    dynamicSeed: 'teal',
    useSystemFont: true,
    highRefreshRate: true,
    notificationsEnabled: true,
    notificationMinutesBefore: 15,
    firstDayOfWeek: 1,
    weekParityMode: 'AUTO',
  });

  const isEvenWeek = useMemo(
    () => resolveIsEvenWeek(today, preferences.weekParityMode, today),
    [today, preferences.weekParityMode]
  );
  const [selectedEvenWeek, setSelectedEvenWeek] = useState<boolean>(() =>
    resolveIsEvenWeek(new Date(), 'AUTO')
  );

  useEffect(() => {
    setSelectedEvenWeek(isEvenWeek);
  }, [isEvenWeek]);

  const [systemPrefersDark, setSystemPrefersDark] = useState<boolean>(() =>
    window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  );

  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!mq) return;
    const handler = (e: MediaQueryListEvent) => setSystemPrefersDark(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  const resolvedDark = useMemo(() => {
    if (preferences.themeMode === 'DARK') return true;
    if (preferences.themeMode === 'LIGHT') return false;
    return systemPrefersDark;
  }, [preferences.themeMode, systemPrefersDark]);

  useEffect(() => {
    const root = document.documentElement;
    if (resolvedDark) root.classList.add('dark');
    else root.classList.remove('dark');
  }, [resolvedDark]);

  const seed = preferences.dynamicColor
    ? DYNAMIC_SEEDS[preferences.dynamicSeed]
    : DYNAMIC_SEEDS.teal;

  const todayLessons = useMemo(
    () => filterLessonsForDate(lessons, today, isEvenWeek),
    [lessons, today, isEvenWeek]
  );

  const handleSaveLesson = (saved: Lesson) => {
    setLessons((prev) => {
      const exists = prev.some((l) => l.id === saved.id);
      if (exists) {
        return prev.map((l) => (l.id === saved.id ? saved : l));
      }
      return [...prev, saved];
    });
  };

  const handleDeleteLesson = (lessonId: number) => {
    setLessons((prev) => prev.filter((l) => l.id !== lessonId));
    setHomework((prev) => prev.filter((t) => t.lessonId !== lessonId));
  };

  const handleToggleHomework = (taskId: number) => {
    setHomework((prev) =>
      prev.map((t) => (t.id === taskId ? { ...t, isCompleted: !t.isCompleted } : t))
    );
  };

  const handleAddHomework = (
    lessonId: number,
    title: string,
    description: string,
    dueDate: string
  ) => {
    setHomework((prev) => [
      {
        id: Date.now(),
        lessonId,
        title,
        description,
        dueDate,
        isCompleted: false,
      },
      ...prev,
    ]);
  };

  const handleDeleteHomework = (taskId: number) => {
    setHomework((prev) => prev.filter((t) => t.id !== taskId));
  };

  const handleUpdatePreferences = (patch: Partial<UserPreferences>) => {
    setPreferences((prev) => ({ ...prev, ...patch }));
  };

  const handleImportSchedule = (
    importedLessons: Lesson[],
    importedHomework: HomeworkTask[],
    replaceExisting: boolean
  ): { lessonsImported: number; homeworkImported: number } => {
    const baseId = Date.now();
    const idMap = new Map<number, number>();
    const remappedLessons = importedLessons.map((lesson, idx) => {
      const nextId = baseId + idx + 1;
      idMap.set(lesson.id, nextId);
      return { ...lesson, id: nextId };
    });

    const remappedHomework = importedHomework
      .map((task, idx) => {
        const mappedLessonId = idMap.get(task.lessonId);
        if (!mappedLessonId) return null;
        return {
          ...task,
          id: baseId + 10000 + idx + 1,
          lessonId: mappedLessonId,
        };
      })
      .filter((t): t is HomeworkTask => t !== null);

    setLessons((prev) => (replaceExisting ? remappedLessons : [...prev, ...remappedLessons]));
    setHomework((prev) => (replaceExisting ? remappedHomework : [...prev, ...remappedHomework]));

    return {
      lessonsImported: remappedLessons.length,
      homeworkImported: remappedHomework.length,
    };
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-zinc-950 text-slate-900 dark:text-zinc-100 flex flex-col">
      {/* Top Bar Contract: 3 zones separated by gap-8 */}
      <header className="sticky top-0 z-30 bg-white/85 dark:bg-zinc-950/85 backdrop-blur-md border-b border-slate-200 dark:border-zinc-800">
        <div className="max-w-7xl mx-auto px-6 py-3.5 flex items-center justify-between gap-8">
          <a
            href="#top"
            onClick={(e) => {
              e.preventDefault();
              setSection('simulator');
            }}
            className="inline-flex items-center gap-2.5 text-lg font-display font-bold tracking-tight text-slate-900 dark:text-zinc-100 whitespace-nowrap shrink-0"
          >
            <OrganizerLogo className="w-7 h-7 rounded-lg shadow-xs shrink-0" />
            <span>Органайзер</span>
          </a>

          <nav className="flex items-center gap-6 text-sm font-medium text-slate-600 dark:text-zinc-400">
            {(
              [
                ['simulator', 'Симулятор'],
                ['code', 'Код проекта'],
                ['guide', 'Запуск в Android Studio'],
              ] as [MainSection, string][]
            ).map(([id, label]) => {
              const active = section === id;
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => setSection(id)}
                  className={`py-1 border-b-2 transition-colors whitespace-nowrap shrink-0 ${
                    active
                      ? 'border-slate-900 dark:border-zinc-100 text-slate-900 dark:text-zinc-100'
                      : 'border-transparent hover:text-slate-900 dark:hover:text-zinc-200'
                  }`}
                >
                  {label}
                </button>
              );
            })}
          </nav>

          <div className="shrink-0">
            <button
              type="button"
              onClick={() =>
                setSection((prev) => (prev === 'code' ? 'simulator' : 'code'))
              }
              style={{ backgroundColor: seed.primary }}
              className="px-4 py-2 rounded-lg text-xs font-semibold text-white hover:opacity-95 transition-opacity whitespace-nowrap shrink-0"
            >
              {section === 'code' ? 'Открыть симулятор' : 'Смотреть Kotlin-код'}
            </button>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-8">
        {section === 'simulator' && (
          <div className="space-y-8">
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
              <div className="space-y-1.5 max-w-2xl">
                <h1 className="text-2xl sm:text-3xl font-display font-bold tracking-tight text-balance">
                  Школьное и студенческое расписание на Jetpack Compose
                </h1>
                <p className="text-sm text-slate-600 dark:text-zinc-400 leading-relaxed">
                  Kotlin · Material 3 Dynamic Color · Room · Hilt · Glance Widget · Android 8.0+
                </p>
              </div>

              {/* Workspace Toolbar */}
              <div className="flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  onClick={() =>
                    handleUpdatePreferences({
                      themeMode: resolvedDark ? 'LIGHT' : 'DARK',
                    })
                  }
                  className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 text-xs font-medium hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors whitespace-nowrap shrink-0"
                >
                  {resolvedDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
                  {resolvedDark ? 'Светлая тема' : 'Тёмная тема'}
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-[430px_1fr] gap-8 items-start">
              {/* Interactive Android Phone Simulator */}
              <PhoneSimulator
                today={today}
                nowMinutes={nowMinutes}
                isCurrentWeekEven={isEvenWeek}
                selectedEvenWeek={selectedEvenWeek}
                onSelectWeekParity={setSelectedEvenWeek}
                lessons={lessons}
                homework={homework}
                preferences={preferences}
                resolvedDark={resolvedDark}
                onSaveLesson={handleSaveLesson}
                onDeleteLesson={handleDeleteLesson}
                onToggleHomework={handleToggleHomework}
                onAddHomework={handleAddHomework}
                onDeleteHomework={handleDeleteHomework}
                onUpdatePreferences={handleUpdatePreferences}
                onImportSchedule={handleImportSchedule}
              />

              {/* Right Column: Live Time Simulator, Dynamic Color Seeds, Home Screen Widget */}
              <div className="space-y-6">
                {/* Live Time & Week Parity Simulator */}
                <section className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 p-6 space-y-4">
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <h2 className="text-base font-semibold">Симуляция времени и недели</h2>
                      <p className="text-xs text-slate-500 dark:text-zinc-400 mt-0.5">
                        Передвигайте ползунок, чтобы проверить прогресс-бар текущего урока и виджет
                      </p>
                    </div>
                    <div className="inline-flex items-center gap-1.5 text-sm font-mono font-semibold tabular-nums">
                      <Clock className="w-4 h-4 text-slate-400" />
                      {minutesToTime(nowMinutes)}
                    </div>
                  </div>

                  <input
                    type="range"
                    min={7 * 60 + 30}
                    max={18 * 60}
                    step={5}
                    value={nowMinutes}
                    onChange={(e) => setNowMinutes(Number(e.target.value))}
                    style={{ accentColor: seed.primary }}
                    className="w-full"
                  />

                  <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                    <div className="flex flex-wrap gap-1.5">
                      {[
                        [8 * 60 + 15, 'До пар'],
                        [9 * 60 + 15, 'Идёт 1-я пара'],
                        [11 * 60, 'Идёт 2-я пара'],
                        [13 * 60 + 45, 'За 15 мин (уведомление)'],
                        [14 * 60 + 30, 'Семинар'],
                        [17 * 60, 'После пар'],
                      ].map(([mins, label]) => (
                        <button
                          key={label}
                          type="button"
                          onClick={() => setNowMinutes(Number(mins))}
                          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
                            nowMinutes === mins
                              ? 'bg-slate-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                              : 'bg-slate-100 dark:bg-zinc-800 text-slate-700 dark:text-zinc-300 hover:bg-slate-200 dark:hover:bg-zinc-700'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>

                    <div className="flex items-center gap-1 p-1 rounded-xl bg-slate-100 dark:bg-zinc-800">
                      {(
                        [
                          ['AUTO', 'Авто'],
                          ['EVEN', 'Верхняя'],
                          ['ODD', 'Нижняя'],
                        ] as const
                      ).map(([mode, label]) => (
                        <button
                          key={mode}
                          type="button"
                          onClick={() => handleUpdatePreferences({ weekParityMode: mode })}
                          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
                            preferences.weekParityMode === mode
                              ? 'bg-white dark:bg-zinc-900 text-slate-900 dark:text-zinc-100 shadow-xs'
                              : 'text-slate-600 dark:text-zinc-400'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>
                </section>

                {/* Material You Dynamic Color Palette Switcher */}
                <section className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 p-6 space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-base font-semibold">Палитра Material You</h2>
                      <p className="text-xs text-slate-500 dark:text-zinc-400 mt-0.5">
                        Динамические цвета системы (Android 12+)
                      </p>
                    </div>
                    <span className="text-xs text-slate-500 dark:text-zinc-400">
                      {seed.label}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                    {(Object.keys(DYNAMIC_SEEDS) as DynamicSeedId[]).map((id) => {
                      const item = DYNAMIC_SEEDS[id];
                      const active = preferences.dynamicSeed === id && preferences.dynamicColor;
                      return (
                        <button
                          key={id}
                          type="button"
                          onClick={() =>
                            handleUpdatePreferences({
                              dynamicColor: true,
                              dynamicSeed: id,
                            })
                          }
                          className={`flex items-center gap-2.5 px-3 py-2.5 rounded-2xl border text-left transition-colors ${
                            active
                              ? 'border-slate-900 dark:border-zinc-100 bg-slate-50 dark:bg-zinc-800'
                              : 'border-slate-200 dark:border-zinc-800 hover:bg-slate-50 dark:hover:bg-zinc-800/50'
                          }`}
                        >
                          <span
                            className="w-5 h-5 rounded-full shrink-0"
                            style={{ backgroundColor: item.primary }}
                          />
                          <span className="text-xs font-medium truncate">{item.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </section>

                {/* Android Glance Home Screen Widget Preview */}
                <section className="space-y-3">
                  <div className="flex items-center justify-between px-1">
                    <h2 className="text-base font-semibold">Виджет на рабочий стол (Jetpack Glance)</h2>
                    <span className="text-xs text-slate-500 dark:text-zinc-400">
                      TodayScheduleWidget.kt
                    </span>
                  </div>
                  <WidgetPreview
                    today={today}
                    lessons={todayLessons}
                    nowMinutes={nowMinutes}
                    isDark={resolvedDark}
                    seed={seed}
                  />
                </section>
              </div>
            </div>
          </div>
        )}

        {section === 'code' && (
          <div className="space-y-6">
            <div>
              <h1 className="text-2xl font-display font-bold tracking-tight">
                Исходный код Android-проекта
              </h1>
              <p className="text-sm text-slate-600 dark:text-zinc-400 mt-1">
                Чистая слоистая архитектура data / domain / ui · MVVM · Hilt · Room · Jetpack Glance
              </p>
            </div>
            <CodeExplorer />
          </div>
        )}

        {section === 'guide' && (
          <div className="max-w-4xl space-y-6">
            <div>
              <h1 className="text-2xl font-display font-bold tracking-tight">
                Инструкция по запуску в Android Studio
              </h1>
              <p className="text-sm text-slate-600 dark:text-zinc-400 mt-1">
                Пошаговая сборка проекта «Органайзер» (minSdk 26 · targetSdk 35 · Kotlin 2.0.21 · KSP)
              </p>
            </div>

            <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 p-6 space-y-5 text-sm leading-relaxed">
              <div className="space-y-1.5">
                <h2 className="text-base font-semibold">01. Требования к окружению</h2>
                <p className="text-slate-600 dark:text-zinc-400">
                  Android Studio Ladybug (2024.2.1) или новее, JDK 17, Android SDK 35 (Android 15) и
                  эмулятор или устройство с Android 8.0 (API 26) и выше.
                </p>
              </div>

              <div className="space-y-1.5">
                <h2 className="text-base font-semibold">02. Создание и перенос файлов</h2>
                <p className="text-slate-600 dark:text-zinc-400">
                  Откройте папку <code className="font-mono text-xs">android/</code> из этого
                  проекта напрямую через <strong>File → Open</strong> в Android Studio. Внутри уже
                  подготовлены <code className="font-mono text-xs">settings.gradle.kts</code>,{' '}
                  <code className="font-mono text-xs">gradle/libs.versions.toml</code>,{' '}
                  <code className="font-mono text-xs">app/build.gradle.kts</code> и все пакеты.
                </p>
              </div>

              <div className="space-y-1.5">
                <h2 className="text-base font-semibold">03. Синхронизация Gradle и запуск</h2>
                <p className="text-slate-600 dark:text-zinc-400">
                  Нажмите <strong>Sync Now</strong> (Gradle загрузит Compose BOM, Room, Hilt,
                  DataStore и Glance). Выберите конфигурацию <strong>app</strong> и нажмите{' '}
                  <strong>Run</strong>. При первом запуске Room автоматически заполнится примерами
                  пар и домашних заданий.
                </p>
              </div>

              <div className="space-y-1.5">
                <h2 className="text-base font-semibold">04. Разрешения и виджет</h2>
                <p className="text-slate-600 dark:text-zinc-400">
                  На Android 13+ приложение запросит разрешение{' '}
                  <code className="font-mono text-xs">POST_NOTIFICATIONS</code> при первом входе.
                  Для добавления виджета зажмите свободное место на рабочем столе → «Виджеты» →
                  «Органайзер» → «Уроки на сегодня».
                </p>
              </div>

              <div className="space-y-1.5">
                <h2 className="text-base font-semibold">05. Автоматическая сборка APK в GitHub</h2>
                <p className="text-slate-600 dark:text-zinc-400">
                  В репозитории добавлен workflow{' '}
                  <code className="font-mono text-xs">.github/workflows/build-apk.yml</code>. После
                  экспорта изменений на GitHub он автоматически соберёт{' '}
                  <code className="font-mono text-xs">apk/Organizer-debug.apk</code> и сохранит его
                  в папку <code className="font-mono text-xs">apk/</code> (а также в артефакты
                  Actions).
                </p>
              </div>
            </div>
          </div>
        )}
      </main>

      <footer className="border-t border-slate-200 dark:border-zinc-800 py-5 px-6 text-xs text-slate-500 dark:text-zinc-400">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Органайзер · Kotlin / Jetpack Compose / Room / Hilt</span>
          <span>Минимальная версия Android 8.0 (API 26)</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
