export type RecurrenceType = 'WEEKLY' | 'EVEN_WEEK' | 'ODD_WEEK' | 'ONCE';

export type SubjectColorId =
  | 'sage'
  | 'sky'
  | 'lavender'
  | 'peach'
  | 'lemon'
  | 'rose'
  | 'mint'
  | 'sand';

export type AppThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK';

export type DynamicSeedId = 'teal' | 'indigo' | 'sage' | 'terracotta';

export interface SubjectColorPalette {
  id: SubjectColorId;
  title: string;
  lightBg: string;
  darkBg: string;
  accent: string;
}

export interface Lesson {
  id: number;
  subject: string;
  teacher: string;
  room: string;
  dayOfWeek: number; // 1 = Пн ... 7 = Вс
  startTime: string; // "HH:mm"
  endTime: string; // "HH:mm"
  colorId: SubjectColorId;
  recurrence: RecurrenceType;
  specificDate?: string; // "YYYY-MM-DD"
  notes: string;
}

export interface HomeworkTask {
  id: number;
  lessonId: number;
  title: string;
  description: string;
  dueDate: string; // "YYYY-MM-DD"
  isCompleted: boolean;
}

export interface UserPreferences {
  themeMode: AppThemeMode;
  dynamicColor: boolean;
  dynamicSeed: DynamicSeedId;
  notificationsEnabled: boolean;
  notificationMinutesBefore: number;
  firstDayOfWeek: 1 | 7; // 1 = Пн, 7 = Вс
}

export type CurrentLessonStatus =
  | {
      kind: 'active';
      currentLesson: Lesson;
      progress: number;
      minutesRemaining: number;
      nextLesson: Lesson | null;
      minutesUntilNext: number | null;
    }
  | {
      kind: 'upcoming';
      nextLesson: Lesson;
      minutesUntilNext: number;
    }
  | { kind: 'finished' }
  | { kind: 'empty' };
