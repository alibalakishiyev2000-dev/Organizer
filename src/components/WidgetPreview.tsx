import React from 'react';
import { SUBJECT_COLORS, timeToMinutes } from '../data/initialData';
import type { DynamicSeedPalette } from '../data/initialData';
import type { Lesson } from '../types/organizer';

interface WidgetPreviewProps {
  today: Date;
  lessons: Lesson[];
  nowMinutes: number;
  isDark: boolean;
  seed: DynamicSeedPalette;
  onSelectLesson?: (lesson: Lesson) => void;
}

export const WidgetPreview: React.FC<WidgetPreviewProps> = ({
  today,
  lessons,
  nowMinutes,
  isDark,
  seed,
  onSelectLesson,
}) => {
  const dateLabel = today.toLocaleDateString('ru-RU', {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
  });
  const formattedDate = dateLabel.charAt(0).toUpperCase() + dateLabel.slice(1);

  return (
    <div
      className={`rounded-3xl p-4 border transition-colors ${
        isDark
          ? 'bg-black border-zinc-800 text-zinc-100'
          : 'bg-white border-slate-200/80 text-slate-900 shadow-sm'
      }`}
    >
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm font-semibold tracking-tight">Сегодня · {formattedDate}</span>
        <span className="text-xs text-slate-500 dark:text-zinc-400">
          Glance 4×2
        </span>
      </div>

      {lessons.length === 0 ? (
        <div className="py-8 text-center text-sm text-slate-500 dark:text-zinc-400">
          На сегодня занятий нет
        </div>
      ) : (
        <div className="space-y-2">
          {lessons.map((lesson) => {
            const start = timeToMinutes(lesson.startTime);
            const end = timeToMinutes(lesson.endTime);
            const isActive = nowMinutes >= start && nowMinutes < end;
            const palette = SUBJECT_COLORS[lesson.colorId];

            return (
              <button
                key={lesson.id}
                type="button"
                onClick={() => onSelectLesson?.(lesson)}
                style={
                  isActive
                    ? {
                        backgroundColor: isDark
                          ? seed.primaryContainerDark
                          : seed.primaryContainerLight,
                      }
                    : undefined
                }
                className={`w-full text-left flex items-center gap-2.5 px-3 py-2.5 rounded-2xl transition-colors ${
                  isActive
                    ? ''
                    : isDark
                      ? 'bg-zinc-800/80 hover:bg-zinc-800'
                      : 'bg-slate-100/80 hover:bg-slate-100'
                }`}
              >
                <span
                  className="w-1 h-7 rounded-full shrink-0"
                  style={{ backgroundColor: palette.accent }}
                />
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-semibold truncate">{lesson.subject}</div>
                  <div className="text-[11px] text-slate-600 dark:text-zinc-400 tabular-nums truncate">
                    {lesson.startTime}–{lesson.endTime}
                    {lesson.room ? ` · ${lesson.room}` : ''}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};
