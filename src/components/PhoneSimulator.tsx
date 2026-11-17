import React, { useMemo, useRef, useState } from 'react';
import {
  Bell,
  BookOpen,
  Calendar,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Circle,
  Clock,
  Edit3,
  FileDown,
  FileUp,
  MapPin,
  Plus,
  Repeat,
  Search,
  Settings,
  Trash2,
  User,
  X,
} from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { downloadIcsFile, generateScheduleIcs } from '../utils/icsExport';
import {
  downloadFile,
  exportScheduleToJson,
  parseScheduleBackupFile,
  type ParsedScheduleBackup,
} from '../utils/scheduleBackup';
import {
  DAY_SHORT_NAMES,
  DYNAMIC_SEEDS,
  RECURRENCE_LABELS,
  SUBJECT_COLORS,
  addDays,
  buildWeekDates,
  computeCurrentStatus,
  createInitialHomework,
  createInitialLessons,
  filterLessonsForDate,
  formatDurationRu,
  formatIsoDate,
  getIsoWeekNumber,
  getJsDayToIso,
} from '../data/initialData';
import type {
  AppThemeMode,
  DynamicSeedId,
  HomeworkTask,
  Lesson,
  UserPreferences,
  WeekParityMode,
} from '../types/organizer';
import { LessonSheetModal } from './LessonSheetModal';
import { OrganizerLogo } from './OrganizerLogo';

type BottomTab = 'schedule' | 'homework' | 'settings';

interface PhoneSimulatorProps {
  today: Date;
  nowMinutes: number;
  isCurrentWeekEven: boolean;
  selectedEvenWeek: boolean;
  onSelectWeekParity: (isEven: boolean) => void;
  lessons: Lesson[];
  homework: HomeworkTask[];
  preferences: UserPreferences;
  resolvedDark: boolean;
  onSaveLesson: (lesson: Lesson) => void;
  onDeleteLesson: (lessonId: number) => void;
  onToggleHomework: (taskId: number) => void;
  onAddHomework: (lessonId: number, title: string, description: string, dueDate: string) => void;
  onDeleteHomework: (taskId: number) => void;
  onUpdatePreferences: (patch: Partial<UserPreferences>) => void;
  onImportSchedule: (
    lessons: Lesson[],
    homework: HomeworkTask[],
    replaceExisting: boolean
  ) => { lessonsImported: number; homeworkImported: number };
  externalEditorTrigger?: number;
}

export const PhoneSimulator: React.FC<PhoneSimulatorProps> = ({
  today,
  nowMinutes,
  isCurrentWeekEven,
  selectedEvenWeek,
  onSelectWeekParity,
  lessons,
  homework,
  preferences,
  resolvedDark,
  onSaveLesson,
  onDeleteLesson,
  onToggleHomework,
  onAddHomework,
  onDeleteHomework,
  onUpdatePreferences,
  onImportSchedule,
}) => {
  const [activeTab, setActiveTab] = useState<BottomTab>('schedule');
  const weekDays = useMemo(
    () => buildWeekDates(today, preferences.firstDayOfWeek),
    [today, preferences.firstDayOfWeek]
  );
  const todayIsoStr = formatIsoDate(today);
  const [selectedDateStr, setSelectedDateStr] = useState<string>(todayIsoStr);

  const selectedDate = useMemo(() => {
    return weekDays.find((d) => formatIsoDate(d) === selectedDateStr) ?? today;
  }, [weekDays, selectedDateStr, today]);

  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedLessonId, setExpandedLessonId] = useState<number | null>(null);
  const [editorState, setEditorState] = useState<{
    open: boolean;
    lesson: Lesson | null;
  }>({ open: false, lesson: null });
  const [hwModalOpen, setHwModalOpen] = useState(false);
  const [exportNotice, setExportNotice] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [pendingImport, setPendingImport] = useState<ParsedScheduleBackup | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [manualNotification, setManualNotification] = useState<{
    subject: string;
    room: string;
    startTime: string;
    minutesLeft: number;
  } | null>(null);

  const seed = preferences.dynamicColor
    ? DYNAMIC_SEEDS[preferences.dynamicSeed]
    : DYNAMIC_SEEDS.teal;

  const filteredLessons = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return lessons;
    return lessons.filter(
      (l) =>
        l.subject.toLowerCase().includes(q) ||
        l.teacher.toLowerCase().includes(q) ||
        l.room.toLowerCase().includes(q)
    );
  }, [lessons, searchQuery]);

  const selectedDayLessons = useMemo(
    () => filterLessonsForDate(filteredLessons, selectedDate, selectedEvenWeek),
    [filteredLessons, selectedDate, selectedEvenWeek]
  );

  const todayLessonsUnfiltered = useMemo(
    () => filterLessonsForDate(lessons, today, isCurrentWeekEven),
    [lessons, today, isCurrentWeekEven]
  );

  const currentStatus = useMemo(
    () => computeCurrentStatus(todayLessonsUnfiltered, nowMinutes),
    [todayLessonsUnfiltered, nowMinutes]
  );

  const homeworkByLesson = useMemo(() => {
    const map = new Map<number, HomeworkTask[]>();
    for (const task of homework) {
      const arr = map.get(task.lessonId) ?? [];
      arr.push(task);
      map.set(task.lessonId, arr);
    }
    return map;
  }, [homework]);

  const todayFormatted = today.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
  });

  const primaryContainerBg = resolvedDark
    ? seed.primaryContainerDark
    : seed.primaryContainerLight;
  const primaryContainerFg = resolvedDark
    ? seed.onPrimaryContainerDark
    : seed.onPrimaryContainerLight;

  const activeAutoNotification = useMemo(() => {
    if (!preferences.notificationsEnabled) return null;
    if (
      currentStatus.kind === 'upcoming' &&
      currentStatus.minutesUntilNext <= preferences.notificationMinutesBefore
    ) {
      return {
        subject: currentStatus.nextLesson.subject,
        room: currentStatus.nextLesson.room,
        startTime: currentStatus.nextLesson.startTime,
        minutesLeft: currentStatus.minutesUntilNext,
      };
    }
    if (
      currentStatus.kind === 'active' &&
      currentStatus.nextLesson &&
      currentStatus.minutesUntilNext !== null &&
      currentStatus.minutesUntilNext <= preferences.notificationMinutesBefore
    ) {
      return {
        subject: currentStatus.nextLesson.subject,
        room: currentStatus.nextLesson.room,
        startTime: currentStatus.nextLesson.startTime,
        minutesLeft: currentStatus.minutesUntilNext,
      };
    }
    return null;
  }, [preferences.notificationsEnabled, preferences.notificationMinutesBefore, currentStatus]);

  const visibleNotification = manualNotification ?? activeAutoNotification;

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setImportError(null);
    try {
      const text = await file.text();
      const parsed = parseScheduleBackupFile(text);
      setPendingImport(parsed);
    } catch (err) {
      setImportError(
        err instanceof Error ? err.message : 'Не удалось прочитать файл расписания'
      );
    }
  };

  return (
    <div className={resolvedDark ? 'dark' : ''}>
      <div
        style={{
          fontFamily: preferences.useSystemFont
            ? 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
            : '"DM Sans", "Plus Jakarta Sans", sans-serif',
        }}
        className="relative w-full max-w-[410px] mx-auto h-[790px] rounded-[40px] border-[8px] border-slate-900 dark:border-zinc-800 bg-slate-50 dark:bg-black text-slate-900 dark:text-zinc-100 shadow-xl overflow-hidden flex flex-col"
      >
        {/* Android Heads-up Lesson Reminder Notification */}
        <AnimatePresence>
          {visibleNotification && (
            <motion.div
              initial={{ opacity: 0, y: -16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -16 }}
              transition={{ duration: 0.2 }}
              className="absolute top-10 left-3 right-3 z-50 rounded-2xl bg-white dark:bg-[#121517] border border-slate-200 dark:border-zinc-800 shadow-xl p-3 flex items-start gap-2.5"
            >
              <div
                style={{ backgroundColor: primaryContainerBg, color: primaryContainerFg }}
                className="w-8 h-8 rounded-xl flex items-center justify-center shrink-0"
              >
                <Bell className="w-4 h-4" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-semibold truncate">
                  Скоро: {visibleNotification.subject}
                </div>
                <div className="text-[11px] text-slate-500 dark:text-zinc-400">
                  Начало в {visibleNotification.startTime} (через {visibleNotification.minutesLeft} мин)
                  {visibleNotification.room ? ` · ${visibleNotification.room}` : ''}
                </div>
              </div>
              {manualNotification && (
                <button
                  type="button"
                  onClick={() => setManualNotification(null)}
                  className="text-slate-400 hover:text-slate-600 p-1"
                  aria-label="Скрыть уведомление"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </motion.div>
          )}
        </AnimatePresence>

        {/* Android Status Bar */}
        <div className="px-6 pt-3 pb-1.5 flex items-center justify-between text-[11px] font-medium text-slate-600 dark:text-zinc-400 tabular-nums shrink-0">
          <span>
            {String(Math.floor(nowMinutes / 60)).padStart(2, '0')}:
            {String(nowMinutes % 60).padStart(2, '0')}
          </span>
          <div className="w-20 h-4 rounded-full bg-slate-900 dark:bg-zinc-800" />
          <span>5G · 94%</span>
        </div>

        {/* Material 3 TopAppBar */}
        <div className="px-4 py-2.5 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-2.5 min-w-0">
            <OrganizerLogo className="w-8 h-8 rounded-xl shadow-xs shrink-0" />
            <div className="min-w-0">
              <h2 className="text-lg font-semibold leading-tight tracking-tight truncate">
                {activeTab === 'homework'
                  ? 'Домашние задания'
                  : activeTab === 'settings'
                    ? 'Настройки'
                    : selectedEvenWeek
                      ? 'Верхняя неделя'
                      : 'Нижняя неделя'}
              </h2>
              {activeTab === 'schedule' && (
                <p className="text-xs text-slate-500 dark:text-zinc-400">
                  Сегодня, {todayFormatted}
                </p>
              )}
            </div>
          </div>
          {activeTab === 'schedule' && (
            <button
              type="button"
              onClick={() => {
                setSearchOpen((prev) => !prev);
                if (searchOpen) setSearchQuery('');
              }}
              className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-2xl hover:bg-slate-200/70 dark:hover:bg-zinc-800 transition-colors"
              aria-label="Поиск по предметам"
            >
              {searchOpen ? <X className="w-5 h-5" /> : <Search className="w-5 h-5" />}
            </button>
          )}
        </div>

        {/* Main Scrollable Viewport extending behind the floating iOS capsule bar */}
        <div className="flex-1 overflow-y-auto px-3.5 pb-32 overflow-x-hidden">
          <AnimatePresence mode="wait">
            {activeTab === 'schedule' && (
              <motion.div
                key="tab-schedule"
                initial={{ opacity: 0, y: 10, scale: 0.985 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -8, scale: 0.99 }}
                transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
                className="space-y-3"
              >
              {/* Even / Odd Week Switcher for viewing and editing */}
              <div className="grid grid-cols-2 gap-1.5">
                {[
                  {
                    isEven: true,
                    label: isCurrentWeekEven ? 'Верхняя (текущая)' : 'Верхняя неделя',
                  },
                  {
                    isEven: false,
                    label: !isCurrentWeekEven ? 'Нижняя (текущая)' : 'Нижняя неделя',
                  },
                ].map(({ isEven, label }) => {
                  const active = selectedEvenWeek === isEven;
                  return (
                    <button
                      key={String(isEven)}
                      type="button"
                      onClick={() => onSelectWeekParity(isEven)}
                      style={
                        active
                          ? { backgroundColor: primaryContainerBg, color: primaryContainerFg }
                          : undefined
                      }
                      className={`py-2 px-2.5 rounded-2xl text-xs font-medium transition-colors whitespace-nowrap truncate ${
                        !active
                          ? 'bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-800 text-slate-600 dark:text-zinc-300'
                          : ''
                      }`}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>

              {/* Search field */}
              <AnimatePresence>
                {searchOpen && (
                  <motion.div
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                    transition={{ duration: 0.16 }}
                  >
                    <div className="relative">
                      <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                      <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Предмет, кабинет или преподаватель"
                        className="w-full pl-9 pr-8 py-2.5 rounded-2xl text-sm bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 outline-none focus:border-slate-400"
                      />
                      {searchQuery && (
                        <button
                          type="button"
                          onClick={() => setSearchQuery('')}
                          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Mon-Sun Day Strip */}
              <div className="grid grid-cols-7 gap-1 py-1">
                {weekDays.map((date) => {
                  const isoStr = formatIsoDate(date);
                  const isoDay = getJsDayToIso(date);
                  const isToday = isoStr === todayIsoStr;
                  const isSelected = isoStr === formatIsoDate(selectedDate);

                  return (
                    <button
                      key={isoStr}
                      type="button"
                      onClick={() => setSelectedDateStr(isoStr)}
                      style={
                        isSelected
                          ? { backgroundColor: seed.primary, color: '#ffffff' }
                          : isToday
                            ? {
                                backgroundColor: primaryContainerBg,
                                color: primaryContainerFg,
                              }
                            : undefined
                      }
                      className={`flex flex-col items-center justify-center py-2 rounded-2xl transition-colors min-h-[54px] ${
                        !isSelected && !isToday
                          ? 'text-slate-600 dark:text-zinc-400 hover:bg-slate-200/60 dark:hover:bg-zinc-800/70'
                          : ''
                      }`}
                    >
                      <span className="text-[11px] font-medium">{DAY_SHORT_NAMES[isoDay]}</span>
                      <span className="text-sm font-semibold tabular-nums mt-0.5">
                        {date.getDate()}
                      </span>
                    </button>
                  );
                })}
              </div>

              {/* Animated Day Content Switch */}
              <AnimatePresence mode="wait">
                <motion.div
                  key={`${selectedDateStr}-${selectedEvenWeek}`}
                  initial={{ opacity: 0, x: 10 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -10 }}
                  transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
                  className="space-y-3"
                >
              {/* Live Current Lesson Banner (shown on Today when viewing current week parity) */}
              {formatIsoDate(selectedDate) === todayIsoStr &&
                selectedEvenWeek === isCurrentWeekEven && (
                <div
                  style={{
                    backgroundColor: primaryContainerBg,
                    color: primaryContainerFg,
                  }}
                  className="rounded-3xl p-4 shadow-sm"
                >
                  {currentStatus.kind === 'active' && (
                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs font-medium opacity-90">
                        <span>Сейчас идёт урок</span>
                        <span className="tabular-nums">
                          Осталось {currentStatus.minutesRemaining} мин
                        </span>
                      </div>
                      <div className="text-base font-bold leading-snug">
                        {currentStatus.currentLesson.subject}
                      </div>
                      <div className="w-full h-2 rounded-full bg-black/10 dark:bg-white/15 overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-300"
                          style={{
                            width: `${Math.round(currentStatus.progress * 100)}%`,
                            backgroundColor: seed.primary,
                          }}
                        />
                      </div>
                      {currentStatus.nextLesson && (
                        <div className="text-xs opacity-90 pt-0.5">
                          Следующий урок через{' '}
                          <span className="font-semibold tabular-nums">
                            {formatDurationRu(currentStatus.minutesUntilNext ?? 0)}
                          </span>
                          : {currentStatus.nextLesson.subject} ({currentStatus.nextLesson.startTime})
                        </div>
                      )}
                    </div>
                  )}

                  {currentStatus.kind === 'upcoming' && (
                    <div className="space-y-1">
                      <div className="text-xs font-medium opacity-90">
                        Следующий урок через{' '}
                        <span className="font-semibold tabular-nums">
                          {formatDurationRu(currentStatus.minutesUntilNext)}
                        </span>
                      </div>
                      <div className="text-base font-semibold">
                        {currentStatus.nextLesson.subject} · {currentStatus.nextLesson.startTime}
                      </div>
                      {currentStatus.nextLesson.room && (
                        <div className="text-xs opacity-85">{currentStatus.nextLesson.room}</div>
                      )}
                    </div>
                  )}

                  {currentStatus.kind === 'finished' && (
                    <div className="space-y-1">
                      <div className="text-sm font-semibold">Уроки на сегодня завершены</div>
                      <div className="text-xs opacity-85">
                        Проверьте домашние задания на завтра.
                      </div>
                    </div>
                  )}

                  {currentStatus.kind === 'empty' && (
                    <div className="space-y-1">
                      <div className="text-sm font-semibold">Сегодня выходной</div>
                      <div className="text-xs opacity-85">В расписании на этот день нет пар.</div>
                    </div>
                  )}
                </div>
              )}

              {/* Lessons List */}
              {selectedDayLessons.length === 0 ? (
                <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-800 p-7 text-center space-y-1.5">
                  <div className="text-sm font-semibold">
                    {searchQuery ? 'Ничего не найдено' : 'Свободный день'}
                  </div>
                  <p className="text-xs text-slate-500 dark:text-zinc-400">
                    {searchQuery
                      ? 'Попробуйте другой предмет, кабинет или фамилию.'
                      : 'Нажмите «+», чтобы добавить урок на этот день.'}
                  </p>
                </div>
              ) : (
                <div className="space-y-2.5">
                  {selectedDayLessons.map((lesson) => {
                    const pal = SUBJECT_COLORS[lesson.colorId];
                    const expanded = expandedLessonId === lesson.id;
                    const linkedHw = homeworkByLesson.get(lesson.id) ?? [];
                    const isCurrent =
                      formatIsoDate(selectedDate) === todayIsoStr &&
                      selectedEvenWeek === isCurrentWeekEven &&
                      currentStatus.kind === 'active' &&
                      currentStatus.currentLesson.id === lesson.id;

                    return (
                      <motion.div
                        layout
                        key={`${selectedDateStr}-${lesson.id}`}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.18 }}
                        className={`rounded-3xl bg-white dark:bg-zinc-900 border ${
                          isCurrent
                            ? 'border-slate-400 dark:border-zinc-600 shadow-md'
                            : 'border-slate-200/80 dark:border-zinc-800 shadow-xs'
                        } overflow-hidden`}
                      >
                        <div className="flex items-stretch">
                          {/* Цветная полоса слева на карточке урока (по ТЗ Android-дизайна) */}
                          <div
                            className="w-1.5 shrink-0"
                            style={{ backgroundColor: pal.accent }}
                          />
                          <div className="flex-1 p-3.5">
                            <div
                              role="button"
                              tabIndex={0}
                              onClick={() =>
                                setExpandedLessonId(expanded ? null : lesson.id)
                              }
                              onKeyDown={(e) => {
                                if (e.key === 'Enter' || e.key === ' ') {
                                  e.preventDefault();
                                  setExpandedLessonId(expanded ? null : lesson.id);
                                }
                              }}
                              className="w-full text-left cursor-pointer"
                            >
                              <div className="flex items-center justify-between gap-2 mb-1.5">
                                <span
                                  style={{
                                    backgroundColor: resolvedDark ? pal.darkBg : pal.lightBg,
                                  }}
                                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-xl text-[11px] font-medium tabular-nums text-slate-800 dark:text-zinc-100"
                                >
                                  <Clock className="w-3 h-3" style={{ color: pal.accent }} />
                                  {lesson.startTime} – {lesson.endTime}
                                </span>
                                <div className="flex items-center gap-1.5">
                                  {lesson.recurrence !== 'WEEKLY' && (
                                    <span
                                      className="text-[11px] font-medium"
                                      style={{ color: pal.accent }}
                                    >
                                      {RECURRENCE_LABELS[lesson.recurrence]}
                                    </span>
                                  )}
                                  {expanded ? (
                                    <ChevronUp className="w-4 h-4 text-slate-400" />
                                  ) : (
                                    <ChevronDown className="w-4 h-4 text-slate-400" />
                                  )}
                                </div>
                              </div>

                              <div className="text-sm font-semibold text-slate-900 dark:text-zinc-100 leading-snug mb-1.5">
                                {lesson.subject}
                              </div>

                              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500 dark:text-zinc-400">
                                {lesson.room ? (
                                  <span className="inline-flex items-center gap-1">
                                    <MapPin className="w-3.5 h-3.5" />
                                    {lesson.room}
                                  </span>
                                ) : (
                                  <button
                                    type="button"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setEditorState({ open: true, lesson });
                                    }}
                                    style={{ color: seed.primary }}
                                    className="inline-flex items-center gap-1 font-medium hover:underline"
                                  >
                                    <MapPin className="w-3.5 h-3.5" />
                                    Указать кабинет
                                  </button>
                                )}
                                {lesson.teacher && (
                                  <span className="inline-flex items-center gap-1">
                                    <User className="w-3.5 h-3.5" />
                                    {lesson.teacher}
                                  </span>
                                )}
                              </div>
                            </div>

                            <AnimatePresence>
                              {expanded && (
                                <motion.div
                                  initial={{ opacity: 0, height: 0 }}
                                  animate={{ opacity: 1, height: 'auto' }}
                                  exit={{ opacity: 0, height: 0 }}
                                  transition={{ duration: 0.18 }}
                                  className="overflow-hidden"
                                >
                                  <div className="mt-3 pt-3 border-t border-slate-100 dark:border-zinc-800 space-y-2.5">
                                    <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-zinc-400">
                                      <Repeat className="w-3.5 h-3.5" />
                                      <span>{RECURRENCE_LABELS[lesson.recurrence]}</span>
                                    </div>
                                    {lesson.notes && (
                                      <p className="text-xs text-slate-700 dark:text-zinc-300">
                                        {lesson.notes}
                                      </p>
                                    )}
                                    {linkedHw.length > 0 && (
                                      <div className="space-y-1">
                                        <div className="text-xs font-semibold text-slate-700 dark:text-zinc-300">
                                          Домашние задания (
                                          {linkedHw.filter((t) => !t.isCompleted).length} активных)
                                        </div>
                                        {linkedHw.map((task) => (
                                          <button
                                            key={task.id}
                                            type="button"
                                            onClick={() => onToggleHomework(task.id)}
                                            className="w-full flex items-center gap-2 py-1 text-left text-xs"
                                          >
                                            {task.isCompleted ? (
                                              <CheckCircle2
                                                className="w-4 h-4 shrink-0"
                                                style={{ color: seed.primary }}
                                              />
                                            ) : (
                                              <Circle className="w-4 h-4 shrink-0 text-slate-400" />
                                            )}
                                            <span
                                              className={
                                                task.isCompleted
                                                  ? 'line-through text-slate-400'
                                                  : 'text-slate-700 dark:text-zinc-200'
                                              }
                                            >
                                              {task.title}
                                            </span>
                                          </button>
                                        ))}
                                      </div>
                                    )}
                                    <div className="flex justify-end">
                                      <button
                                        type="button"
                                        onClick={() =>
                                          setEditorState({ open: true, lesson })
                                        }
                                        style={{ color: seed.primary }}
                                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors"
                                      >
                                        <Edit3 className="w-3.5 h-3.5" />
                                        Изменить
                                      </button>
                                    </div>
                                  </div>
                                </motion.div>
                              )}
                            </AnimatePresence>
                          </div>
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              )}
                </motion.div>
              </AnimatePresence>
              </motion.div>
            )}

            {/* Tab 2: Homework */}
            {activeTab === 'homework' && (
              <motion.div
                key="tab-homework"
                initial={{ opacity: 0, y: 10, scale: 0.985 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -8, scale: 0.99 }}
                transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
                className="space-y-3 pt-1"
              >
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-semibold">
                    Активных: {homework.filter((t) => !t.isCompleted).length}
                  </div>
                  <div className="text-xs text-slate-500 dark:text-zinc-400">
                    Выполнено: {homework.filter((t) => t.isCompleted).length}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setHwModalOpen(true)}
                  disabled={lessons.length === 0}
                  style={{ borderColor: seed.primary, color: seed.primary }}
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-2xl border text-xs font-semibold hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors disabled:opacity-40"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Добавить ДЗ
                </button>
              </div>

              {homework.length === 0 ? (
                <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 p-7 text-center text-xs text-slate-500">
                  Домашних заданий пока нет.
                </div>
              ) : (
                <div className="space-y-2.5">
                  {[...homework]
                    .sort((a, b) => Number(a.isCompleted) - Number(b.isCompleted))
                    .map((task) => {
                      const lesson = lessons.find((l) => l.id === task.lessonId);
                      const pal = lesson ? SUBJECT_COLORS[lesson.colorId] : SUBJECT_COLORS.sky;
                      return (
                        <div
                          key={task.id}
                          className={`rounded-2xl p-3.5 border transition-colors flex items-start gap-2.5 ${
                            task.isCompleted
                              ? 'bg-slate-100/70 dark:bg-zinc-900/50 border-slate-200/60 dark:border-zinc-800/60'
                              : 'bg-white dark:bg-zinc-900 border-slate-200 dark:border-zinc-800'
                          }`}
                        >
                          <button
                            type="button"
                            onClick={() => onToggleHomework(task.id)}
                            className="mt-0.5 shrink-0"
                            aria-label="Переключить выполнение"
                          >
                            {task.isCompleted ? (
                              <CheckCircle2
                                className="w-5 h-5"
                                style={{ color: seed.primary }}
                              />
                            ) : (
                              <Circle className="w-5 h-5" style={{ color: pal.accent }} />
                            )}
                          </button>
                          <div
                            className="flex-1 min-w-0 cursor-pointer"
                            onClick={() => onToggleHomework(task.id)}
                          >
                            <div className="flex items-center gap-1.5 text-[11px] text-slate-500 dark:text-zinc-400">
                              <span
                                className="w-2 h-2 rounded-full shrink-0"
                                style={{ backgroundColor: pal.accent }}
                              />
                              <span className="truncate">
                                {lesson?.subject ?? 'Предмет'} · до {task.dueDate.slice(5)}
                              </span>
                            </div>
                            <div
                              className={`text-sm font-medium mt-0.5 ${
                                task.isCompleted ? 'line-through text-slate-400' : ''
                              }`}
                            >
                              {task.title}
                            </div>
                            {task.description && (
                              <div className="text-xs text-slate-500 dark:text-zinc-400 mt-0.5">
                                {task.description}
                              </div>
                            )}
                          </div>
                          <button
                            type="button"
                            onClick={() => onDeleteHomework(task.id)}
                            className="p-1.5 text-slate-400 hover:text-rose-500 transition-colors"
                            aria-label="Удалить задание"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      );
                    })}
                </div>
              )}
              </motion.div>
            )}

            {/* Tab 3: Settings */}
            {activeTab === 'settings' && (
              <motion.div
                key="tab-settings"
                initial={{ opacity: 0, y: 10, scale: 0.985 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -8, scale: 0.99 }}
                transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
                className="space-y-3 pt-1"
              >
              <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-800 p-4 space-y-3">
                <div className="text-sm font-semibold">Оформление</div>
                <div className="grid grid-cols-3 gap-1.5">
                  {(
                    [
                      ['SYSTEM', 'Система'],
                      ['LIGHT', 'Светлая'],
                      ['DARK', 'Тёмная'],
                    ] as [AppThemeMode, string][]
                  ).map(([mode, label]) => {
                    const active = preferences.themeMode === mode;
                    return (
                      <button
                        key={mode}
                        type="button"
                        onClick={() => onUpdatePreferences({ themeMode: mode })}
                        style={
                          active
                            ? { backgroundColor: primaryContainerBg, color: primaryContainerFg }
                            : undefined
                        }
                        className={`py-2 px-2 rounded-xl text-xs font-medium transition-colors ${
                          !active
                            ? 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300'
                            : ''
                        }`}
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div>
                    <div className="text-xs font-medium">Системный шрифт</div>
                    <div className="text-[11px] text-slate-500 dark:text-zinc-400">
                      Использовать системный шрифт интерфейса
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      onUpdatePreferences({ useSystemFont: !preferences.useSystemFont })
                    }
                    style={{
                      backgroundColor: preferences.useSystemFont ? seed.primary : undefined,
                    }}
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors ${
                      !preferences.useSystemFont ? 'bg-slate-300 dark:bg-zinc-700' : ''
                    }`}
                    aria-label="Системный шрифт"
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-white transition-transform ${
                        preferences.useSystemFont ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div>
                    <div className="text-xs font-medium">Высокая частота (120 Гц)</div>
                    <div className="text-[11px] text-slate-500 dark:text-zinc-400">
                      Плавный интерфейс и анимации 120 fps
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      onUpdatePreferences({ highRefreshRate: !preferences.highRefreshRate })
                    }
                    style={{
                      backgroundColor: preferences.highRefreshRate ? seed.primary : undefined,
                    }}
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors ${
                      !preferences.highRefreshRate ? 'bg-slate-300 dark:bg-zinc-700' : ''
                    }`}
                    aria-label="Высокая частота 120 Гц"
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-white transition-transform ${
                        preferences.highRefreshRate ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div>
                    <div className="text-xs font-medium">Dynamic Color (Material You)</div>
                    <div className="text-[11px] text-slate-500 dark:text-zinc-400">
                      Палитра под обои Android 12+
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      onUpdatePreferences({ dynamicColor: !preferences.dynamicColor })
                    }
                    style={{
                      backgroundColor: preferences.dynamicColor ? seed.primary : undefined,
                    }}
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors ${
                      !preferences.dynamicColor ? 'bg-slate-300 dark:bg-zinc-700' : ''
                    }`}
                    aria-label="Dynamic Color"
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-white transition-transform ${
                        preferences.dynamicColor ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                {preferences.dynamicColor && (
                  <div className="flex items-center gap-2 pt-1">
                    {(Object.keys(DYNAMIC_SEEDS) as DynamicSeedId[]).map((id) => {
                      const item = DYNAMIC_SEEDS[id];
                      const active = preferences.dynamicSeed === id;
                      return (
                        <button
                          key={id}
                          type="button"
                          onClick={() => onUpdatePreferences({ dynamicSeed: id })}
                          title={item.label}
                          style={{ backgroundColor: item.primary }}
                          className={`w-7 h-7 rounded-full border-2 transition-transform ${
                            active
                              ? 'border-slate-900 dark:border-white scale-110'
                              : 'border-transparent opacity-80'
                          }`}
                        />
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-800 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-semibold">Уведомления об уроках</div>
                    <div className="text-xs text-slate-500 dark:text-zinc-400 tabular-nums">
                      За {preferences.notificationMinutesBefore} мин до начала
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      onUpdatePreferences({
                        notificationsEnabled: !preferences.notificationsEnabled,
                      })
                    }
                    style={{
                      backgroundColor: preferences.notificationsEnabled
                        ? seed.primary
                        : undefined,
                    }}
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors ${
                      !preferences.notificationsEnabled ? 'bg-slate-300 dark:bg-zinc-700' : ''
                    }`}
                    aria-label="Уведомления"
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-white transition-transform ${
                        preferences.notificationsEnabled ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                {preferences.notificationsEnabled && (
                  <div className="space-y-2">
                    <input
                      type="range"
                      min={5}
                      max={60}
                      step={5}
                      value={preferences.notificationMinutesBefore}
                      onChange={(e) =>
                        onUpdatePreferences({
                          notificationMinutesBefore: Number(e.target.value),
                        })
                      }
                      style={{ accentColor: seed.primary }}
                      className="w-full"
                    />
                    <div className="grid grid-cols-4 gap-1.5">
                      {[5, 10, 15, 30].map((mins) => (
                        <button
                          key={mins}
                          type="button"
                          onClick={() =>
                            onUpdatePreferences({ notificationMinutesBefore: mins })
                          }
                          style={
                            preferences.notificationMinutesBefore === mins
                              ? {
                                  backgroundColor: primaryContainerBg,
                                  color: primaryContainerFg,
                                }
                              : undefined
                          }
                          className={`py-1.5 rounded-xl text-xs font-medium tabular-nums ${
                            preferences.notificationMinutesBefore !== mins
                              ? 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300'
                              : ''
                          }`}
                        >
                          {mins} мин
                        </button>
                      ))}
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setManualNotification({
                          subject: todayLessonsUnfiltered[0]?.subject ?? 'Математический анализ',
                          room: todayLessonsUnfiltered[0]?.room ?? 'Ауд. 304',
                          startTime: todayLessonsUnfiltered[0]?.startTime ?? '08:30',
                          minutesLeft: preferences.notificationMinutesBefore,
                        });
                        setTimeout(() => setManualNotification(null), 4000);
                      }}
                      className="w-full py-2 px-3 rounded-xl border border-slate-200 dark:border-zinc-700 text-xs font-medium flex items-center justify-center gap-1.5 hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors"
                    >
                      <Bell className="w-3.5 h-3.5" />
                      Проверить уведомление (за {preferences.notificationMinutesBefore} мин)
                    </button>
                  </div>
                )}
              </div>

              <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-800 p-4 space-y-3">
                <div className="text-sm font-semibold">Календарь</div>
                <div className="space-y-2">
                  <div className="text-xs font-medium text-slate-600 dark:text-zinc-400">
                    Первый день недели
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    {(
                      [
                        [1, 'Понедельник'],
                        [7, 'Воскресенье'],
                      ] as [1 | 7, string][]
                    ).map(([day, label]) => {
                      const active = preferences.firstDayOfWeek === day;
                      return (
                        <button
                          key={day}
                          type="button"
                          onClick={() => onUpdatePreferences({ firstDayOfWeek: day })}
                          style={
                            active
                              ? { backgroundColor: primaryContainerBg, color: primaryContainerFg }
                              : undefined
                          }
                          className={`py-2 px-3 rounded-xl text-xs font-medium transition-colors ${
                            !active
                              ? 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300'
                              : ''
                          }`}
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="space-y-2 pt-1">
                  <div className="text-xs font-medium text-slate-600 dark:text-zinc-400">
                    Текущая неделя
                  </div>
                  <p className="text-[11px] text-slate-500 dark:text-zinc-400 leading-relaxed">
                    {(() => {
                      const weekNum = getIsoWeekNumber(today);
                      const autoEven = weekNum % 2 === 0;
                      if (preferences.weekParityMode === 'AUTO') {
                        return `Автоматически по номеру недели в году (${weekNum}-я): сейчас ${
                          autoEven ? 'Верхняя' : 'Нижняя'
                        }`;
                      }
                      return `Задано вручную: текущая неделя — ${
                        preferences.weekParityMode === 'EVEN' ? 'Верхняя' : 'Нижняя'
                      }`;
                    })()}
                  </p>
                  <div className="grid grid-cols-3 gap-1.5">
                    {(
                      [
                        ['AUTO', 'Авто'],
                        ['EVEN', 'Верхняя'],
                        ['ODD', 'Нижняя'],
                      ] as [WeekParityMode, string][]
                    ).map(([mode, label]) => {
                      const active = preferences.weekParityMode === mode;
                      return (
                        <button
                          key={mode}
                          type="button"
                          onClick={() => onUpdatePreferences({ weekParityMode: mode })}
                          style={
                            active
                              ? { backgroundColor: primaryContainerBg, color: primaryContainerFg }
                              : undefined
                          }
                          className={`py-2 px-2 rounded-xl text-xs font-medium transition-colors ${
                            !active
                              ? 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300'
                              : ''
                          }`}
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>

              <div className="rounded-3xl bg-white dark:bg-zinc-900 border border-slate-200/80 dark:border-zinc-800 p-4 space-y-2.5">
                <div className="text-sm font-semibold">Импорт и экспорт расписания</div>
                <p className="text-xs text-slate-500 dark:text-zinc-400 leading-relaxed">
                  Сохраняйте полную копию (.json), загружайте файл расписания (.json / .ics) или экспортируйте в Google Календарь.
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".json,.ics,application/json,text/calendar"
                  onChange={handleFileChange}
                  className="hidden"
                />
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="py-2.5 px-3 rounded-2xl border border-slate-200 dark:border-zinc-700 text-xs font-semibold flex items-center justify-center gap-1.5 hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors whitespace-nowrap"
                  >
                    <FileUp className="w-4 h-4" />
                    Импорт
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      const json = exportScheduleToJson(lessons, homework);
                      downloadFile(json, 'organizer_backup.json', 'application/json');
                      setImportError(null);
                      setExportNotice(`Сохранено: уроков ${lessons.length}, заданий ${homework.length}`);
                      setTimeout(() => setExportNotice(null), 4000);
                    }}
                    style={{ backgroundColor: seed.primary }}
                    className="py-2.5 px-3 rounded-2xl text-xs font-semibold text-white flex items-center justify-center gap-1.5 hover:opacity-95 transition-opacity whitespace-nowrap"
                  >
                    <FileDown className="w-4 h-4" />
                    Экспорт (.json)
                  </button>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    const baseLessons = createInitialLessons(today);
                    const baseHomework = createInitialHomework(today);
                    const json = exportScheduleToJson(baseLessons, baseHomework);
                    downloadFile(json, 'organizer_base_schedule.json', 'application/json');
                    setImportError(null);
                    setExportNotice(
                      `Базовое расписание сохранено: уроков ${baseLessons.length}, заданий ${baseHomework.length}`
                    );
                    setTimeout(() => setExportNotice(null), 4000);
                  }}
                  className="w-full py-2 px-3 rounded-2xl border border-slate-200 dark:border-zinc-700 text-xs font-medium flex items-center justify-center gap-1.5 hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors"
                >
                  <FileDown className="w-3.5 h-3.5" />
                  Экспорт базового расписания (.json)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const ics = generateScheduleIcs(lessons, today, isCurrentWeekEven);
                    downloadIcsFile(ics, 'organizer_schedule.ics');
                    setImportError(null);
                    setExportNotice(`Экспортировано в .ics: ${lessons.length} уроков`);
                    setTimeout(() => setExportNotice(null), 4000);
                  }}
                  className="w-full py-2 px-3 rounded-2xl border border-slate-200 dark:border-zinc-700 text-xs font-medium flex items-center justify-center gap-1.5 hover:bg-slate-50 dark:hover:bg-zinc-800 transition-colors"
                >
                  <Calendar className="w-3.5 h-3.5" />
                  Экспорт в Google Календарь (.ics)
                </button>
                {exportNotice && (
                  <p className="text-[11px] font-medium text-emerald-600 dark:text-emerald-400">
                    {exportNotice}
                  </p>
                )}
                {importError && (
                  <p className="text-[11px] font-medium text-rose-600 dark:text-rose-400">
                    {importError}
                  </p>
                )}
              </div>

              <div className="pt-2 pb-1 text-center text-xs font-medium text-slate-500 dark:text-zinc-400">
                Над приложением старался Али!
              </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Floating Action Button (+) above iOS capsule */}
        <AnimatePresence>
          {activeTab === 'schedule' && (
            <motion.button
              type="button"
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              transition={{ duration: 0.18 }}
              onClick={() => setEditorState({ open: true, lesson: null })}
              style={{
                backgroundColor: primaryContainerBg,
                color: primaryContainerFg,
                bottom: 'calc(92px + env(safe-area-inset-bottom, 0px))',
              }}
              className="absolute right-4 z-20 w-13 h-13 rounded-full shadow-lg flex items-center justify-center hover:scale-105 active:scale-95 transition-transform"
              aria-label="Добавить урок"
            >
              <Plus className="w-6 h-6" />
            </motion.button>
          )}
        </AnimatePresence>

        {/* Floating iOS-style Bottom Navigation Bar (pill capsule + blur(20px) + sliding pill indicator) */}
        <nav
          aria-label="Навигация"
          style={{
            bottom: 'calc(16px + env(safe-area-inset-bottom, 0px))',
          }}
          className="absolute left-4 right-4 z-30 rounded-full p-1.5 grid grid-cols-3 items-center bg-white/80 dark:bg-zinc-900/78 backdrop-blur-[20px] border border-white/85 dark:border-white/15 shadow-[0_12px_32px_rgba(15,23,42,0.14)] dark:shadow-[0_12px_32px_rgba(0,0,0,0.55)]"
        >
          {(
            [
              { id: 'schedule', label: 'Расписание', Icon: Calendar },
              { id: 'homework', label: 'Задания', Icon: BookOpen },
              { id: 'settings', label: 'Настройки', Icon: Settings },
            ] as const
          ).map(({ id, label, Icon }) => {
            const active = activeTab === id;
            return (
              <button
                key={id}
                type="button"
                onClick={() => setActiveTab(id)}
                className="relative flex flex-col items-center justify-center gap-0.5 py-2 px-2 rounded-full transition-colors"
              >
                {active && (
                  <motion.div
                    layoutId="phoneFloatingIosPill"
                    transition={{
                      type: 'spring',
                      stiffness: preferences.highRefreshRate ? 500 : 360,
                      damping: preferences.highRefreshRate ? 35 : 30,
                    }}
                    style={{
                      backgroundColor: resolvedDark
                        ? `${seed.primary}38`
                        : `${seed.primary}1F`,
                    }}
                    className="absolute inset-0 rounded-full border border-white/40 dark:border-white/10"
                  />
                )}
                <Icon
                  style={active ? { color: seed.primary } : undefined}
                  className={`relative z-10 w-[19px] h-[19px] transition-transform duration-200 ${
                    active
                      ? 'scale-105'
                      : 'text-zinc-800 dark:text-zinc-200'
                  }`}
                />
                <span
                  style={active ? { color: seed.primary } : undefined}
                  className={`relative z-10 text-[10.5px] leading-tight transition-colors ${
                    active
                      ? 'font-semibold'
                      : 'font-medium text-zinc-800 dark:text-zinc-200'
                  }`}
                >
                  {label}
                </span>
              </button>
            );
          })}
        </nav>

        {/* Add/Edit Lesson Modal Bottom Sheet */}
        {editorState.open && (
          <LessonSheetModal
            initialLesson={editorState.lesson}
            defaultDate={selectedDate}
            defaultRecurrence={
              selectedEvenWeek !== isCurrentWeekEven
                ? selectedEvenWeek
                  ? 'EVEN_WEEK'
                  : 'ODD_WEEK'
                : 'WEEKLY'
            }
            isDark={resolvedDark}
            accentColor={seed.primary}
            onClose={() => setEditorState({ open: false, lesson: null })}
            onSave={(saved) => {
              onSaveLesson(saved);
              setEditorState({ open: false, lesson: null });
            }}
            onDelete={(id) => {
              onDeleteLesson(id);
              setEditorState({ open: false, lesson: null });
            }}
          />
        )}

        {/* Quick Add Homework Modal */}
        {hwModalOpen && lessons.length > 0 && (
          <AddHomeworkModal
            lessons={lessons}
            today={today}
            accentColor={seed.primary}
            onClose={() => setHwModalOpen(false)}
            onSave={(lessonId, title, desc, dueDate) => {
              onAddHomework(lessonId, title, desc, dueDate);
              setHwModalOpen(false);
            }}
          />
        )}

        {/* Import Confirmation Modal */}
        {pendingImport && (
          <div
            className="absolute inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
            onClick={() => setPendingImport(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.94 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.16 }}
              className="w-full rounded-3xl bg-white dark:bg-zinc-900 p-5 shadow-2xl border border-slate-200 dark:border-zinc-800 space-y-3"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="text-base font-semibold">Импорт расписания</h3>
              <p className="text-xs text-slate-600 dark:text-zinc-400 leading-relaxed">
                Найдено уроков: {pendingImport.lessons.length}
                {pendingImport.homework.length > 0
                  ? `, заданий: ${pendingImport.homework.length}`
                  : ''}
                . Заменить текущее расписание или добавить к существующему?
              </p>
              <div className="flex items-center justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setPendingImport(null)}
                  className="px-3 py-2 rounded-xl text-xs font-medium text-slate-600 dark:text-zinc-300"
                >
                  Отмена
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const res = onImportSchedule(
                      pendingImport.lessons,
                      pendingImport.homework,
                      false
                    );
                    setPendingImport(null);
                    setExportNotice(
                      `Добавлено: уроков ${res.lessonsImported}, заданий ${res.homeworkImported}`
                    );
                    setTimeout(() => setExportNotice(null), 4000);
                  }}
                  className="px-3 py-2 rounded-xl border border-slate-200 dark:border-zinc-700 text-xs font-semibold hover:bg-slate-50 dark:hover:bg-zinc-800"
                >
                  Добавить
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const res = onImportSchedule(
                      pendingImport.lessons,
                      pendingImport.homework,
                      true
                    );
                    setPendingImport(null);
                    setExportNotice(
                      `Заменено: уроков ${res.lessonsImported}, заданий ${res.homeworkImported}`
                    );
                    setTimeout(() => setExportNotice(null), 4000);
                  }}
                  style={{ backgroundColor: seed.primary }}
                  className="px-3.5 py-2 rounded-xl text-xs font-semibold text-white"
                >
                  Заменить
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </div>
    </div>
  );
};

interface AddHomeworkModalProps {
  lessons: Lesson[];
  today: Date;
  accentColor: string;
  onClose: () => void;
  onSave: (lessonId: number, title: string, desc: string, dueDate: string) => void;
}

const AddHomeworkModal: React.FC<AddHomeworkModalProps> = ({
  lessons,
  today,
  accentColor,
  onClose,
  onSave,
}) => {
  const [lessonId, setLessonId] = useState(lessons[0].id);
  const [title, setTitle] = useState('');
  const [desc, setDesc] = useState('');
  const [offset, setOffset] = useState(1);

  return (
    <div
      className="absolute inset-0 z-40 flex items-center justify-center bg-black/45 p-4"
      onClick={onClose}
    >
      <div
        className="w-full rounded-3xl bg-white dark:bg-zinc-900 p-5 shadow-2xl border border-slate-200 dark:border-zinc-800 space-y-3"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-base font-semibold">Новое задание</h3>
        <div>
          <label className="block text-xs text-slate-500 mb-1">Предмет</label>
          <select
            value={lessonId}
            onChange={(e) => setLessonId(Number(e.target.value))}
            className="w-full px-3 py-2 rounded-xl text-sm bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700"
          >
            {lessons.map((l) => (
              <option key={l.id} value={l.id}>
                {l.subject}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">Задача</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Например, прочитать главу 4"
            className="w-full px-3 py-2 rounded-xl text-sm bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700"
          />
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">Комментарий</label>
          <input
            type="text"
            value={desc}
            onChange={(e) => setDesc(e.target.value)}
            placeholder="Номера задач или заметка"
            className="w-full px-3 py-2 rounded-xl text-sm bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700"
          />
        </div>
        <div className="grid grid-cols-3 gap-1.5">
          {[
            [1, 'Завтра'],
            [2, '+2 дня'],
            [7, 'Неделя'],
          ].map(([d, label]) => (
            <button
              key={d}
              type="button"
              onClick={() => setOffset(Number(d))}
              style={offset === d ? { backgroundColor: accentColor, color: '#fff' } : undefined}
              className={`py-1.5 rounded-xl text-xs font-medium ${
                offset !== d ? 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300' : ''
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            onClick={onClose}
            className="px-3.5 py-2 rounded-xl text-xs font-medium text-slate-600 dark:text-zinc-300"
          >
            Отмена
          </button>
          <button
            type="button"
            disabled={!title.trim()}
            onClick={() =>
              onSave(lessonId, title.trim(), desc.trim(), formatIsoDate(addDays(today, offset)))
            }
            style={{ backgroundColor: accentColor }}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-white disabled:opacity-40"
          >
            Сохранить
          </button>
        </div>
      </div>
    </div>
  );
};
