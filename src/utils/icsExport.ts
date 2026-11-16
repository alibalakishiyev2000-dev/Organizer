import { RECURRENCE_LABELS, addDays, getJsDayToIso } from '../data/initialData';
import type { Lesson } from '../types/organizer';

const ICS_DAY_CODES: Record<number, string> = {
  1: 'MO',
  2: 'TU',
  3: 'WE',
  4: 'TH',
  5: 'FR',
  6: 'SA',
  7: 'SU',
};

function getIsoWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

function isDateEvenWeek(date: Date, todayEvenOverride?: boolean): boolean {
  if (todayEvenOverride !== undefined) {
    const today = new Date();
    const todayWeek = getIsoWeekNumber(today);
    const targetWeek = getIsoWeekNumber(date);
    const diffWeeks = targetWeek - todayWeek;
    return Math.abs(diffWeeks) % 2 === 0 ? todayEvenOverride : !todayEvenOverride;
  }
  return getIsoWeekNumber(date) % 2 === 0;
}

function formatUtcStamp(date: Date): string {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  const ss = String(date.getUTCSeconds()).padStart(2, '0');
  return `${y}${m}${d}T${hh}${mm}${ss}Z`;
}

function escapeIcsText(text: string): string {
  return text
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r?\n/g, '\\n');
}

function resolveFirstOccurrence(
  lesson: Lesson,
  referenceDate: Date,
  currentWeekEven: boolean
): Date {
  if (lesson.recurrence === 'ONCE' && lesson.specificDate) {
    const [y, m, d] = lesson.specificDate.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  const refIso = getJsDayToIso(referenceDate);
  const daysUntil = (lesson.dayOfWeek - refIso + 7) % 7;
  let candidate = addDays(referenceDate, daysUntil);

  if (lesson.recurrence === 'EVEN_WEEK' && !isDateEvenWeek(candidate, currentWeekEven)) {
    candidate = addDays(candidate, 7);
  } else if (lesson.recurrence === 'ODD_WEEK' && isDateEvenWeek(candidate, currentWeekEven)) {
    candidate = addDays(candidate, 7);
  }
  return candidate;
}

export function generateScheduleIcs(
  lessons: Lesson[],
  referenceDate: Date,
  currentWeekEven: boolean
): string {
  const nowStamp = formatUtcStamp(new Date());
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const lines: string[] = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Organizer//Schedule App//RU',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'X-WR-CALNAME:Органайзер — Расписание',
    `X-WR-TIMEZONE:${timeZone}`,
  ];

  for (const lesson of lessons) {
    const firstDate = resolveFirstOccurrence(lesson, referenceDate, currentWeekEven);
    const [sh, sm] = lesson.startTime.split(':').map(Number);
    const [eh, em] = lesson.endTime.split(':').map(Number);

    const startDt = new Date(
      firstDate.getFullYear(),
      firstDate.getMonth(),
      firstDate.getDate(),
      sh || 9,
      sm || 0,
      0
    );
    const endDt = new Date(
      firstDate.getFullYear(),
      firstDate.getMonth(),
      firstDate.getDate(),
      eh || 10,
      em || 30,
      0
    );

    lines.push('BEGIN:VEVENT');
    lines.push(`UID:organizer-lesson-${lesson.id}@organizer.schedule`);
    lines.push(`DTSTAMP:${nowStamp}`);
    lines.push(`DTSTART:${formatUtcStamp(startDt)}`);
    lines.push(`DTEND:${formatUtcStamp(endDt)}`);
    lines.push(`SUMMARY:${escapeIcsText(lesson.subject)}`);

    if (lesson.room.trim()) {
      lines.push(`LOCATION:${escapeIcsText(`Каб. ${lesson.room.trim()}`)}`);
    }

    const descParts: string[] = [];
    if (lesson.teacher.trim()) descParts.push(`Преподаватель: ${lesson.teacher.trim()}`);
    if (lesson.room.trim()) descParts.push(`Кабинет: ${lesson.room.trim()}`);
    descParts.push(`Повторение: ${RECURRENCE_LABELS[lesson.recurrence]}`);
    if (lesson.notes.trim()) descParts.push(lesson.notes.trim());

    if (descParts.length > 0) {
      lines.push(`DESCRIPTION:${escapeIcsText(descParts.join('\n'))}`);
    }

    const byDay = ICS_DAY_CODES[lesson.dayOfWeek] ?? 'MO';
    if (lesson.recurrence === 'WEEKLY') {
      lines.push(`RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=${byDay}`);
    } else if (lesson.recurrence === 'EVEN_WEEK' || lesson.recurrence === 'ODD_WEEK') {
      lines.push(`RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=${byDay}`);
    }

    lines.push('END:VEVENT');
  }

  lines.push('END:VCALENDAR');
  return lines.join('\r\n');
}

export function downloadIcsFile(content: string, filename = 'organizer_schedule.ics'): void {
  const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
