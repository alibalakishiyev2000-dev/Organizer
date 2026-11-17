import type {
  CurrentLessonStatus,
  DynamicSeedId,
  HomeworkTask,
  Lesson,
  RecurrenceType,
  SubjectColorId,
  SubjectColorPalette,
  WeekParityMode,
} from '../types/organizer';

export const SUBJECT_COLORS: Record<SubjectColorId, SubjectColorPalette> = {
  sage: {
    id: 'sage',
    title: 'Шалфейный',
    lightBg: '#DDECE0',
    darkBg: '#25392B',
    accent: '#4F8260',
  },
  sky: {
    id: 'sky',
    title: 'Небесный',
    lightBg: '#D9EBFA',
    darkBg: '#1F3548',
    accent: '#4476A6',
  },
  lavender: {
    id: 'lavender',
    title: 'Лавандовый',
    lightBg: '#E8E0F8',
    darkBg: '#312748',
    accent: '#7156A4',
  },
  peach: {
    id: 'peach',
    title: 'Персиковый',
    lightBg: '#FCE2D4',
    darkBg: '#462B1D',
    accent: '#B56742',
  },
  lemon: {
    id: 'lemon',
    title: 'Лимонный',
    lightBg: '#F7EFC6',
    darkBg: '#3E3719',
    accent: '#978025',
  },
  rose: {
    id: 'rose',
    title: 'Розовый кварц',
    lightBg: '#F9D9E4',
    darkBg: '#462231',
    accent: '#AE4F74',
  },
  mint: {
    id: 'mint',
    title: 'Мятный',
    lightBg: '#D4F1EC',
    darkBg: '#1C3B36',
    accent: '#3A877B',
  },
  sand: {
    id: 'sand',
    title: 'Песочный',
    lightBg: '#EDE2D5',
    darkBg: '#392F24',
    accent: '#896E50',
  },
};

export interface DynamicSeedPalette {
  id: DynamicSeedId;
  label: string;
  primary: string;
  primaryContainerLight: string;
  onPrimaryContainerLight: string;
  primaryContainerDark: string;
  onPrimaryContainerDark: string;
}

export const DYNAMIC_SEEDS: Record<DynamicSeedId, DynamicSeedPalette> = {
  teal: {
    id: 'teal',
    label: 'Морской бриз',
    primary: '#39656B',
    primaryContainerLight: '#BCEBF1',
    onPrimaryContainerLight: '#001F23',
    primaryContainerDark: '#1F4D53',
    onPrimaryContainerDark: '#BCEBF1',
  },
  indigo: {
    id: 'indigo',
    label: 'Черничный',
    primary: '#4A5D9E',
    primaryContainerLight: '#DCE2FF',
    onPrimaryContainerLight: '#0A1C57',
    primaryContainerDark: '#304382',
    onPrimaryContainerDark: '#DCE2FF',
  },
  sage: {
    id: 'sage',
    label: 'Лесной мох',
    primary: '#3D6A4E',
    primaryContainerLight: '#C0EED0',
    onPrimaryContainerLight: '#042111',
    primaryContainerDark: '#245137',
    onPrimaryContainerDark: '#C0EED0',
  },
  terracotta: {
    id: 'terracotta',
    label: 'Тёплая глина',
    primary: '#8B4F3B',
    primaryContainerLight: '#FFDBD0',
    onPrimaryContainerLight: '#360F04',
    primaryContainerDark: '#6D3826',
    onPrimaryContainerDark: '#FFDBD0',
  },
};

export const RECURRENCE_LABELS: Record<RecurrenceType, string> = {
  WEEKLY: 'Каждую неделю',
  EVEN_WEEK: 'Верхняя неделя',
  ODD_WEEK: 'Нижняя неделя',
  ONCE: 'Один раз',
};

export const DAY_SHORT_NAMES: Record<number, string> = {
  1: 'Пн',
  2: 'Вт',
  3: 'Ср',
  4: 'Чт',
  5: 'Пт',
  6: 'Сб',
  7: 'Вс',
};

export const DAY_FULL_NAMES: Record<number, string> = {
  1: 'Понедельник',
  2: 'Вторник',
  3: 'Среда',
  4: 'Четверг',
  5: 'Пятница',
  6: 'Суббота',
  7: 'Воскресенье',
};

export function getJsDayToIso(date: Date): number {
  const day = date.getDay();
  return day === 0 ? 7 : day;
}

export function formatIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function addDays(date: Date, delta: number): Date {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + delta);
  return copy;
}

export function timeToMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number);
  return (h || 0) * 60 + (m || 0);
}

export function minutesToTime(totalMinutes: number): string {
  const clamped = ((totalMinutes % 1440) + 1440) % 1440;
  const h = String(Math.floor(clamped / 60)).padStart(2, '0');
  const m = String(clamped % 60).padStart(2, '0');
  return `${h}:${m}`;
}

export function formatDurationRu(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h > 0 && m > 0) return `${h} ч ${m} мин`;
  if (h > 0) return `${h} ч`;
  return `${m} мин`;
}

export function getIsoWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

export function resolveIsEvenWeek(
  date: Date,
  mode: WeekParityMode = 'AUTO',
  referenceDate: Date = new Date()
): boolean {
  if (mode === 'AUTO') {
    return getIsoWeekNumber(date) % 2 === 0;
  }
  const baseEven = mode === 'EVEN';
  const mondayStart = (d: Date) => {
    const iso = getJsDayToIso(d);
    const utc = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate() - (iso - 1));
    return utc;
  };
  const weekDiff = Math.round((mondayStart(date) - mondayStart(referenceDate)) / (7 * 86400000));
  return Math.abs(weekDiff) % 2 === 0 ? baseEven : !baseEven;
}

export function buildWeekDates(today: Date, firstDayOfWeek: 1 | 7): Date[] {
  const todayIso = getJsDayToIso(today);
  const diff = (todayIso - firstDayOfWeek + 7) % 7;
  const start = addDays(today, -diff);
  return Array.from({ length: 7 }, (_, i) => addDays(start, i));
}

export function filterLessonsForDate(
  lessons: Lesson[],
  targetDate: Date,
  isEvenWeek: boolean
): Lesson[] {
  const targetIsoDay = getJsDayToIso(targetDate);
  const targetIsoStr = formatIsoDate(targetDate);

  return lessons
    .filter((lesson) => {
      if (lesson.recurrence === 'WEEKLY') return lesson.dayOfWeek === targetIsoDay;
      if (lesson.recurrence === 'EVEN_WEEK') {
        return lesson.dayOfWeek === targetIsoDay && isEvenWeek;
      }
      if (lesson.recurrence === 'ODD_WEEK') {
        return lesson.dayOfWeek === targetIsoDay && !isEvenWeek;
      }
      return lesson.specificDate === targetIsoStr;
    })
    .sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime));
}

export function computeCurrentStatus(
  todayLessons: Lesson[],
  nowMinutes: number
): CurrentLessonStatus {
  if (todayLessons.length === 0) return { kind: 'empty' };
  const sorted = [...todayLessons].sort(
    (a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime)
  );

  const active = sorted.find(
    (l) => nowMinutes >= timeToMinutes(l.startTime) && nowMinutes < timeToMinutes(l.endTime)
  );
  const next = sorted.find((l) => timeToMinutes(l.startTime) > nowMinutes) ?? null;

  if (active) {
    const start = timeToMinutes(active.startTime);
    const end = timeToMinutes(active.endTime);
    const total = Math.max(1, end - start);
    const elapsed = Math.max(0, Math.min(total, nowMinutes - start));
    return {
      kind: 'active',
      currentLesson: active,
      progress: elapsed / total,
      minutesRemaining: Math.max(1, end - nowMinutes),
      nextLesson: next,
      minutesUntilNext: next ? Math.max(1, timeToMinutes(next.startTime) - nowMinutes) : null,
    };
  }

  if (next) {
    return {
      kind: 'upcoming',
      nextLesson: next,
      minutesUntilNext: Math.max(1, timeToMinutes(next.startTime) - nowMinutes),
    };
  }

  return { kind: 'finished' };
}

export function createInitialLessons(today: Date): Lesson[] {
  const todayStr = formatIsoDate(today);
  const todayIso = getJsDayToIso(today);

  return [
    {
      id: 1,
      subject: 'Математический анализ',
      teacher: 'проф. Соколова Е.А.',
      room: 'Ауд. 304',
      dayOfWeek: 1,
      startTime: '08:30',
      endTime: '10:00',
      colorId: 'sky',
      recurrence: 'WEEKLY',
      notes: 'Лекция по пределам и производным',
    },
    {
      id: 2,
      subject: 'Алгоритмы и структуры данных',
      teacher: 'доц. Волков Д.С.',
      room: 'Комп. класс 12',
      dayOfWeek: 1,
      startTime: '10:15',
      endTime: '11:45',
      colorId: 'sage',
      recurrence: 'WEEKLY',
      notes: 'Практика на Kotlin: графы и деревья',
    },
    {
      id: 3,
      subject: 'Английский язык',
      teacher: 'Морозова А.В.',
      room: 'Каб. 215',
      dayOfWeek: 1,
      startTime: '12:15',
      endTime: '13:45',
      colorId: 'peach',
      recurrence: 'EVEN_WEEK',
      notes: 'Разговорный клуб и чтение технических статей',
    },
    {
      id: 4,
      subject: 'Физика',
      teacher: 'доц. Громов П.Н.',
      room: 'Лаб. 108',
      dayOfWeek: 2,
      startTime: '09:00',
      endTime: '10:30',
      colorId: 'lavender',
      recurrence: 'WEEKLY',
      notes: 'Лабораторная работа №4',
    },
    {
      id: 5,
      subject: 'Базы данных',
      teacher: 'Орлов И.М.',
      room: 'Ауд. 412',
      dayOfWeek: 2,
      startTime: '10:45',
      endTime: '12:15',
      colorId: 'mint',
      recurrence: 'ODD_WEEK',
      notes: 'Индексы, транзакции и Room SQLite',
    },
    {
      id: 6,
      subject: 'История науки',
      teacher: 'проф. Белова Н.К.',
      room: 'Ауд. 201',
      dayOfWeek: 3,
      startTime: '08:30',
      endTime: '10:00',
      colorId: 'sand',
      recurrence: 'WEEKLY',
      notes: '',
    },
    {
      id: 7,
      subject: 'Мобильная разработка',
      teacher: 'Тихонов А.Р.',
      room: 'Технопарк 5',
      dayOfWeek: 4,
      startTime: '10:15',
      endTime: '11:45',
      colorId: 'rose',
      recurrence: 'WEEKLY',
      notes: 'Jetpack Compose & Material You',
    },
    {
      id: 8,
      subject: 'Линейная алгебра',
      teacher: 'проф. Соколова Е.А.',
      room: 'Ауд. 306',
      dayOfWeek: 5,
      startTime: '09:00',
      endTime: '10:30',
      colorId: 'lemon',
      recurrence: 'WEEKLY',
      notes: 'Матрицы и собственные векторы',
    },
    {
      id: 9,
      subject: 'Проектный семинар',
      teacher: 'Тихонов А.Р.',
      room: 'Коворкинг 2',
      dayOfWeek: todayIso,
      startTime: '14:00',
      endTime: '15:30',
      colorId: 'sky',
      recurrence: 'ONCE',
      specificDate: todayStr,
      notes: 'Разбор архитектуры MVVM + Hilt',
    },
  ];
}

export function createInitialHomework(today: Date): HomeworkTask[] {
  return [
    {
      id: 1,
      lessonId: 1,
      title: 'Решить №14–18 из сборника Демидовича',
      description: 'Тема: вычисление пределов по правилу Лопиталя',
      dueDate: formatIsoDate(addDays(today, 2)),
      isCompleted: false,
    },
    {
      id: 2,
      lessonId: 2,
      title: 'Реализовать алгоритм Дейкстры',
      description: 'С использованием PriorityQueue, покрыть юнит-тестами',
      dueDate: formatIsoDate(addDays(today, 3)),
      isCompleted: false,
    },
    {
      id: 3,
      lessonId: 4,
      title: 'Оформить отчёт по лабораторной №3',
      description: 'Графики погрешностей на миллиметровке',
      dueDate: formatIsoDate(addDays(today, 1)),
      isCompleted: true,
    },
    {
      id: 4,
      lessonId: 7,
      title: 'Сверстать экран расписания на Compose',
      description: 'Добавить HorizontalPager и Material TimePicker',
      dueDate: formatIsoDate(addDays(today, 4)),
      isCompleted: false,
    },
  ];
}
