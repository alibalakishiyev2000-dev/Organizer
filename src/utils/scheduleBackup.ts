import type {
  HomeworkTask,
  Lesson,
  RecurrenceType,
  SubjectColorId,
} from '../types/organizer';

export interface ParsedScheduleBackup {
  lessons: Lesson[];
  homework: HomeworkTask[];
}

const VALID_COLORS: Set<SubjectColorId> = new Set([
  'sage',
  'sky',
  'lavender',
  'peach',
  'lemon',
  'rose',
  'mint',
  'sand',
]);

const VALID_RECURRENCE: Set<RecurrenceType> = new Set([
  'WEEKLY',
  'EVEN_WEEK',
  'ODD_WEEK',
  'ONCE',
]);

const COLOR_CYCLE: SubjectColorId[] = [
  'sky',
  'sage',
  'peach',
  'lavender',
  'mint',
  'sand',
  'rose',
  'lemon',
];

export function exportScheduleToJson(lessons: Lesson[], homework: HomeworkTask[]): string {
  const payload = {
    version: 1,
    exportedAt: new Date().toISOString(),
    lessons,
    homework,
  };
  return JSON.stringify(payload, null, 2);
}

export function downloadFile(content: string, filename: string, mimeType: string): void {
  const blob = new Blob([content], { type: `${mimeType};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function parseScheduleBackupFile(rawContent: string): ParsedScheduleBackup {
  const trimmed = rawContent.replace(/\uFEFF/g, '').trim();
  if (!trimmed) {
    throw new Error('Файл пуст');
  }
  if (/^BEGIN:VCALENDAR/i.test(trimmed)) {
    return parseIcsBackup(trimmed);
  }
  return parseJsonBackup(trimmed);
}

function safeString(value: unknown, fallback = ''): string {
  if (typeof value !== 'string') return fallback;
  return value.toLowerCase() === 'null' ? fallback : value;
}

function parseDayOfWeek(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.min(7, Math.max(1, Math.trunc(value)));
  }
  if (typeof value === 'string') {
    const clean = value.trim().toUpperCase();
    const num = Number(clean);
    if (Number.isFinite(num) && clean !== '') {
      return Math.min(7, Math.max(1, Math.trunc(num)));
    }
    const map: Record<string, number> = {
      MONDAY: 1,
      MON: 1,
      MO: 1,
      ПН: 1,
      ПОНЕДЕЛЬНИК: 1,
      TUESDAY: 2,
      TUE: 2,
      TU: 2,
      ВТ: 2,
      ВТОРНИК: 2,
      WEDNESDAY: 3,
      WED: 3,
      WE: 3,
      СР: 3,
      СРЕДА: 3,
      THURSDAY: 4,
      THU: 4,
      TH: 4,
      ЧТ: 4,
      ЧЕТВЕРГ: 4,
      FRIDAY: 5,
      FRI: 5,
      FR: 5,
      ПТ: 5,
      ПЯТНИЦА: 5,
      SATURDAY: 6,
      SAT: 6,
      SA: 6,
      СБ: 6,
      СУББОТА: 6,
      SUNDAY: 7,
      SUN: 7,
      SU: 7,
      ВС: 7,
      ВОСКРЕСЕНЬЕ: 7,
    };
    if (map[clean]) return map[clean];
  }
  return 1;
}

function parseJsonBackup(raw: string): ParsedScheduleBackup {
  let root: unknown;
  try {
    root = JSON.parse(raw);
  } catch {
    throw new Error('Некорректный JSON-файл расписания');
  }

  const rootObj = (typeof root === 'object' && root !== null ? root : {}) as Record<string, unknown>;
  const rawLessons = Array.isArray(rootObj.lessons)
    ? rootObj.lessons
    : Array.isArray(rootObj.schedule)
      ? rootObj.schedule
      : Array.isArray(rootObj.items)
        ? rootObj.items
        : Array.isArray(root)
          ? root
          : null;

  if (!rawLessons) {
    throw new Error('В файле отсутствует список уроков');
  }

  const lessons: Lesson[] = [];
  rawLessons.forEach((item, index) => {
    if (typeof item !== 'object' || item === null) return;
    const obj = item as Record<string, unknown>;
    const subject = safeString(obj.subject).trim();
    if (!subject) return;

    const rawColor = safeString(obj.colorId ?? obj.color).trim().toLowerCase() as SubjectColorId;
    const colorId: SubjectColorId = VALID_COLORS.has(rawColor)
      ? rawColor
      : COLOR_CYCLE[index % COLOR_CYCLE.length];

    const rawRecurrence = safeString(obj.recurrence, 'WEEKLY').trim().toUpperCase() as RecurrenceType;
    const recurrence: RecurrenceType = VALID_RECURRENCE.has(rawRecurrence)
      ? rawRecurrence
      : 'WEEKLY';

    const dayOfWeek = parseDayOfWeek(obj.dayOfWeek);
    const startTime = normalizeTime(obj.startTime, '09:00');
    const endTime = normalizeTime(obj.endTime, '10:30');
    const rawSpecificDate = safeString(obj.specificDate).trim();
    const specificDate =
      /^\d{4}-\d{2}-\d{2}/.test(rawSpecificDate)
        ? rawSpecificDate.slice(0, 10)
        : recurrence === 'ONCE'
          ? new Date().toISOString().slice(0, 10)
          : undefined;

    lessons.push({
      id: typeof obj.id === 'number' && Number.isFinite(obj.id) ? obj.id : index + 1,
      subject,
      teacher: safeString(obj.teacher).trim(),
      room: safeString(obj.room).trim(),
      dayOfWeek,
      startTime,
      endTime,
      colorId,
      recurrence,
      specificDate,
      notes: safeString(obj.notes).trim(),
    });
  });

  if (lessons.length === 0) {
    throw new Error('В файле не найдено ни одного урока');
  }

  const rawHw = Array.isArray(rootObj.homework) ? rootObj.homework : [];
  const homework: HomeworkTask[] = [];
  rawHw.forEach((item, index) => {
    if (typeof item !== 'object' || item === null) return;
    const obj = item as Record<string, unknown>;
    const title = safeString(obj.title).trim();
    const lessonId = Number(obj.lessonId);
    if (!title || !Number.isFinite(lessonId)) return;

    const rawDue = safeString(obj.dueDate).trim();
    homework.push({
      id: typeof obj.id === 'number' && Number.isFinite(obj.id) ? obj.id : index + 1,
      lessonId,
      title,
      description: safeString(obj.description).trim(),
      dueDate: /^\d{4}-\d{2}-\d{2}/.test(rawDue)
        ? rawDue.slice(0, 10)
        : new Date().toISOString().slice(0, 10),
      isCompleted: Boolean(obj.isCompleted),
    });
  });

  return { lessons, homework };
}

function parseIcsBackup(raw: string): ParsedScheduleBackup {
  const unfolded = raw
    .replace(/\r\n[ \t]/g, '')
    .replace(/\n[ \t]/g, '');
  const lines = unfolded.split(/\r?\n/);

  const lessons: Lesson[] = [];
  let inEvent = false;
  let props: Record<string, string> = {};

  for (const line of lines) {
    if (line === 'BEGIN:VEVENT') {
      inEvent = true;
      props = {};
      continue;
    }
    if (line === 'END:VEVENT') {
      if (inEvent) {
        const parsed = buildLessonFromIcs(props, lessons.length);
        if (parsed) lessons.push(parsed);
      }
      inEvent = false;
      continue;
    }
    if (!inEvent) continue;
    const colonIdx = line.indexOf(':');
    if (colonIdx <= 0) continue;
    const rawKey = line.slice(0, colonIdx).split(';')[0].trim().toUpperCase();
    const val = line.slice(colonIdx + 1).trim();
    props[rawKey] = val;
  }

  if (lessons.length === 0) {
    throw new Error('В файле .ics не найдено событий расписания');
  }

  return { lessons, homework: [] };
}

function buildLessonFromIcs(props: Record<string, string>, index: number): Lesson | null {
  const summary = unescapeIcs(props.SUMMARY ?? '').trim();
  if (!summary) return null;

  const startDt = parseIcsDateTime(props.DTSTART);
  const endDt = parseIcsDateTime(props.DTEND);
  if (!startDt) return null;

  const rrule = props.RRULE ?? '';
  let recurrence: RecurrenceType = 'ONCE';
  if (/FREQ=WEEKLY/i.test(rrule)) {
    if (/INTERVAL=2/i.test(rrule)) {
      recurrence = getIsoWeek(startDt) % 2 === 0 ? 'EVEN_WEEK' : 'ODD_WEEK';
    } else {
      recurrence = 'WEEKLY';
    }
  }

  const byDayMatch = rrule.match(/BYDAY=([A-Z]{2})/);
  const dayMap: Record<string, number> = {
    MO: 1,
    TU: 2,
    WE: 3,
    TH: 4,
    FR: 5,
    SA: 6,
    SU: 7,
  };
  const jsDay = startDt.getDay();
  const isoFromStart = jsDay === 0 ? 7 : jsDay;
  const dayOfWeek = byDayMatch ? (dayMap[byDayMatch[1]] ?? isoFromStart) : isoFromStart;

  const startTime = `${String(startDt.getHours()).padStart(2, '0')}:${String(
    startDt.getMinutes()
  ).padStart(2, '0')}`;
  const fallbackEnd = new Date(startDt.getTime() + 90 * 60000);
  const endObj = endDt ?? fallbackEnd;
  const endTime = `${String(endObj.getHours()).padStart(2, '0')}:${String(
    endObj.getMinutes()
  ).padStart(2, '0')}`;

  const location = unescapeIcs(props.LOCATION ?? '').replace(/^Каб\.\s*/i, '').trim();
  const description = unescapeIcs(props.DESCRIPTION ?? '');
  const descLines = description
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);

  let teacher = '';
  const notesLines: string[] = [];
  for (const dl of descLines) {
    if (dl.startsWith('Преподаватель:')) {
      teacher = dl.replace('Преподаватель:', '').trim();
    } else if (!dl.startsWith('Кабинет:') && !dl.startsWith('Повторение:')) {
      notesLines.push(dl);
    }
  }

  const specificDate =
    recurrence === 'ONCE'
      ? `${startDt.getFullYear()}-${String(startDt.getMonth() + 1).padStart(2, '0')}-${String(
          startDt.getDate()
        ).padStart(2, '0')}`
      : undefined;

  return {
    id: index + 1,
    subject: summary,
    teacher,
    room: location,
    dayOfWeek,
    startTime,
    endTime,
    colorId: COLOR_CYCLE[index % COLOR_CYCLE.length],
    recurrence,
    specificDate,
    notes: notesLines.join('\n'),
  };
}

function parseIcsDateTime(raw?: string): Date | null {
  if (!raw) return null;
  const clean = raw.trim();
  const m = clean.match(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})?(Z)?$/);
  if (m) {
    const [, y, mo, d, hh, mm, ss = '00', isUtc] = m;
    if (isUtc) {
      return new Date(
        Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(hh), Number(mm), Number(ss))
      );
    }
    return new Date(
      Number(y),
      Number(mo) - 1,
      Number(d),
      Number(hh),
      Number(mm),
      Number(ss)
    );
  }
  const dateOnly = clean.match(/^(\d{4})(\d{2})(\d{2})$/);
  if (dateOnly) {
    const [, y, mo, d] = dateOnly;
    return new Date(Number(y), Number(mo) - 1, Number(d), 9, 0, 0);
  }
  return null;
}

function getIsoWeek(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

function normalizeTime(val: unknown, fallback: string): string {
  if (typeof val === 'string') {
    const match = val.trim().match(/^(\d{1,2}):(\d{2})/);
    if (match) {
      const h = Math.min(23, Math.max(0, Number(match[1])));
      const m = Math.min(59, Math.max(0, Number(match[2])));
      return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
    }
  }
  return fallback;
}

function unescapeIcs(text: string): string {
  return text
    .replace(/\\n/g, '\n')
    .replace(/\\,/g, ',')
    .replace(/\\;/g, ';')
    .replace(/\\\\/g, '\\');
}
