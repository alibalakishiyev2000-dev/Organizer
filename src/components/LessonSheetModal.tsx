import React, { useState } from 'react';
import { Check, Clock, Trash2, X } from 'lucide-react';
import {
  DAY_SHORT_NAMES,
  RECURRENCE_LABELS,
  SUBJECT_COLORS,
  formatIsoDate,
  minutesToTime,
  timeToMinutes,
} from '../data/initialData';
import type { Lesson, RecurrenceType, SubjectColorId } from '../types/organizer';

interface LessonSheetModalProps {
  initialLesson: Lesson | null;
  defaultDate: Date;
  defaultRecurrence?: RecurrenceType;
  isDark: boolean;
  accentColor: string;
  onClose: () => void;
  onSave: (lesson: Lesson) => void;
  onDelete?: (lessonId: number) => void;
}

export const LessonSheetModal: React.FC<LessonSheetModalProps> = ({
  initialLesson,
  defaultDate,
  defaultRecurrence = 'WEEKLY',
  isDark,
  accentColor,
  onClose,
  onSave,
  onDelete,
}) => {
  const defaultIsoDay = defaultDate.getDay() === 0 ? 7 : defaultDate.getDay();

  const [subject, setSubject] = useState(initialLesson?.subject ?? '');
  const [teacher, setTeacher] = useState(initialLesson?.teacher ?? '');
  const [room, setRoom] = useState(initialLesson?.room ?? '');
  const [notes, setNotes] = useState(initialLesson?.notes ?? '');
  const [dayOfWeek, setDayOfWeek] = useState<number>(initialLesson?.dayOfWeek ?? defaultIsoDay);
  const [startTime, setStartTime] = useState(initialLesson?.startTime ?? '09:00');
  const [endTime, setEndTime] = useState(initialLesson?.endTime ?? '10:30');
  const [colorId, setColorId] = useState<SubjectColorId>(initialLesson?.colorId ?? 'sky');
  const [recurrence, setRecurrence] = useState<RecurrenceType>(
    initialLesson?.recurrence ?? defaultRecurrence
  );
  const [pickingTimeField, setPickingTimeField] = useState<'start' | 'end' | null>(null);
  const [error, setError] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!subject.trim()) {
      setError(true);
      return;
    }
    const startMins = timeToMinutes(startTime);
    const endMins = timeToMinutes(endTime);
    const safeEnd = endMins > startMins ? endTime : minutesToTime(startMins + 45);

    onSave({
      id: initialLesson?.id ?? Date.now(),
      subject: subject.trim(),
      teacher: teacher.trim(),
      room: room.trim(),
      dayOfWeek,
      startTime,
      endTime: safeEnd,
      colorId,
      recurrence,
      specificDate:
        recurrence === 'ONCE'
          ? initialLesson?.specificDate ?? formatIsoDate(defaultDate)
          : undefined,
      notes: notes.trim(),
    });
  };

  return (
    <div
      className="absolute inset-0 z-40 flex items-end bg-black/45 backdrop-blur-[2px]"
      onClick={onClose}
    >
      <div
        className="w-full max-h-[88%] overflow-y-auto rounded-t-3xl bg-white dark:bg-zinc-900 p-5 shadow-2xl border-t border-slate-200 dark:border-zinc-800"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="w-10 h-1 bg-slate-300 dark:bg-zinc-700 rounded-full mx-auto mb-4" />

        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-slate-900 dark:text-zinc-100">
            {initialLesson ? 'Редактировать урок' : 'Новый урок'}
          </h3>
          <div className="flex items-center gap-1">
            {initialLesson && onDelete && (
              <button
                type="button"
                onClick={() => onDelete(initialLesson.id)}
                className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-xl text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition-colors"
                title="Удалить урок"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors"
              aria-label="Закрыть"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3.5">
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1">
              Название предмета *
            </label>
            <input
              type="text"
              value={subject}
              onChange={(e) => {
                setSubject(e.target.value);
                setError(false);
              }}
              placeholder="Например, Дискретная математика"
              className={`w-full px-3.5 py-2.5 rounded-2xl text-sm bg-slate-50 dark:bg-zinc-800/80 border ${
                error
                  ? 'border-rose-500'
                  : 'border-slate-200 dark:border-zinc-700 focus:border-slate-400'
              } text-slate-900 dark:text-zinc-100 outline-none`}
            />
            {error && (
              <p className="text-xs text-rose-500 mt-1">Введите название предмета</p>
            )}
          </div>

          <div className="grid grid-cols-5 gap-2.5">
            <div className="col-span-2">
              <label className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1">
                Номер кабинета
              </label>
              <input
                type="text"
                value={room}
                onChange={(e) => setRoom(e.target.value)}
                placeholder="Напр. 304"
                className="w-full px-3 py-2 rounded-2xl text-sm bg-slate-50 dark:bg-zinc-800/80 border border-slate-200 dark:border-zinc-700 text-slate-900 dark:text-zinc-100 outline-none"
              />
            </div>
            <div className="col-span-3">
              <label className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1">
                Преподаватель
              </label>
              <input
                type="text"
                value={teacher}
                onChange={(e) => setTeacher(e.target.value)}
                placeholder="Соколова Е.А."
                className="w-full px-3 py-2 rounded-2xl text-sm bg-slate-50 dark:bg-zinc-800/80 border border-slate-200 dark:border-zinc-700 text-slate-900 dark:text-zinc-100 outline-none"
              />
            </div>
          </div>

          <div>
            <span className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1.5">
              Время занятия (Material TimePicker)
            </span>
            <div className="grid grid-cols-2 gap-2.5">
              <button
                type="button"
                onClick={() => setPickingTimeField('start')}
                className="flex items-center justify-center gap-2 py-2.5 px-3 rounded-2xl border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800/70 text-xs font-medium text-slate-800 dark:text-zinc-100 tabular-nums hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors"
              >
                <Clock className="w-3.5 h-3.5" />
                Начало: {startTime}
              </button>
              <button
                type="button"
                onClick={() => setPickingTimeField('end')}
                className="flex items-center justify-center gap-2 py-2.5 px-3 rounded-2xl border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800/70 text-xs font-medium text-slate-800 dark:text-zinc-100 tabular-nums hover:bg-slate-100 dark:hover:bg-zinc-800 transition-colors"
              >
                <Clock className="w-3.5 h-3.5" />
                Конец: {endTime}
              </button>
            </div>
          </div>

          <div>
            <span className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1.5">
              День недели
            </span>
            <div className="grid grid-cols-7 gap-1">
              {[1, 2, 3, 4, 5, 6, 7].map((day) => {
                const active = dayOfWeek === day;
                return (
                  <button
                    key={day}
                    type="button"
                    onClick={() => setDayOfWeek(day)}
                    style={active ? { backgroundColor: accentColor } : undefined}
                    className={`py-2 rounded-xl text-xs font-medium transition-colors ${
                      active
                        ? 'text-white'
                        : 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300'
                    }`}
                  >
                    {DAY_SHORT_NAMES[day]}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <span className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1.5">
              Повторение
            </span>
            <div className="grid grid-cols-2 gap-1.5">
              {(Object.keys(RECURRENCE_LABELS) as RecurrenceType[]).map((type) => {
                const active = recurrence === type;
                return (
                  <button
                    key={type}
                    type="button"
                    onClick={() => setRecurrence(type)}
                    style={active ? { backgroundColor: accentColor } : undefined}
                    className={`py-2 px-2.5 rounded-xl text-xs font-medium text-left transition-colors whitespace-nowrap truncate ${
                      active
                        ? 'text-white'
                        : 'bg-slate-100 dark:bg-zinc-800 text-slate-700 dark:text-zinc-300'
                    }`}
                  >
                    {RECURRENCE_LABELS[type]}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <span className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1.5">
              Цвет предмета
            </span>
            <div className="flex items-center justify-between gap-1.5">
              {(Object.keys(SUBJECT_COLORS) as SubjectColorId[]).map((id) => {
                const pal = SUBJECT_COLORS[id];
                const selected = colorId === id;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setColorId(id)}
                    title={pal.title}
                    style={{
                      backgroundColor: isDark ? pal.darkBg : pal.lightBg,
                      borderColor: selected ? pal.accent : `${pal.accent}55`,
                    }}
                    className={`w-8 h-8 rounded-full border-2 flex items-center justify-center transition-transform ${
                      selected ? 'scale-110' : 'hover:scale-105'
                    }`}
                  >
                    {selected && <Check className="w-3.5 h-3.5" style={{ color: pal.accent }} />}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-zinc-400 mb-1">
              Заметка
            </label>
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Аудитория, формат пары или тема"
              className="w-full px-3.5 py-2 rounded-2xl text-sm bg-slate-50 dark:bg-zinc-800/80 border border-slate-200 dark:border-zinc-700 text-slate-900 dark:text-zinc-100 outline-none"
            />
          </div>

          <button
            type="submit"
            style={{ backgroundColor: accentColor }}
            className="w-full py-3 rounded-2xl text-sm font-semibold text-white shadow-sm hover:opacity-95 active:scale-[0.99] transition-all"
          >
            {initialLesson ? 'Сохранить изменения' : 'Добавить в расписание'}
          </button>
        </form>

        {pickingTimeField && (
          <MaterialTimePickerModal
            title={pickingTimeField === 'start' ? 'Время начала' : 'Время окончания'}
            initialValue={pickingTimeField === 'start' ? startTime : endTime}
            accentColor={accentColor}
            onClose={() => setPickingTimeField(null)}
            onConfirm={(val) => {
              if (pickingTimeField === 'start') {
                setStartTime(val);
                if (timeToMinutes(endTime) <= timeToMinutes(val)) {
                  setEndTime(minutesToTime(timeToMinutes(val) + 90));
                }
              } else {
                setEndTime(val);
              }
              setPickingTimeField(null);
            }}
          />
        )}
      </div>
    </div>
  );
};

interface TimePickerModalProps {
  title: string;
  initialValue: string;
  accentColor: string;
  onClose: () => void;
  onConfirm: (hhmm: string) => void;
}

const MaterialTimePickerModal: React.FC<TimePickerModalProps> = ({
  title,
  initialValue,
  accentColor,
  onClose,
  onConfirm,
}) => {
  const [hStr, mStr] = initialValue.split(':');
  const [hours, setHours] = useState(Number(hStr) || 9);
  const [minutes, setMinutes] = useState(Number(mStr) || 0);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-[300px] rounded-3xl bg-white dark:bg-zinc-900 p-5 shadow-2xl border border-slate-200 dark:border-zinc-800"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="text-xs font-medium text-slate-500 dark:text-zinc-400 mb-3">{title}</p>

        <div className="flex items-center justify-center gap-2 mb-4">
          <div className="flex flex-col items-center">
            <button
              type="button"
              onClick={() => setHours((h) => (h + 1) % 24)}
              className="px-3 py-1 text-xs text-slate-500 hover:text-slate-900 dark:hover:text-white"
            >
              +1 ч
            </button>
            <div
              style={{ borderColor: accentColor }}
              className="w-20 h-16 rounded-2xl border-2 flex items-center justify-center text-3xl font-semibold tabular-nums bg-slate-50 dark:bg-zinc-800 text-slate-900 dark:text-zinc-100"
            >
              {String(hours).padStart(2, '0')}
            </div>
            <button
              type="button"
              onClick={() => setHours((h) => (h + 23) % 24)}
              className="px-3 py-1 text-xs text-slate-500 hover:text-slate-900 dark:hover:text-white"
            >
              −1 ч
            </button>
          </div>

          <span className="text-3xl font-semibold text-slate-400 pb-1">:</span>

          <div className="flex flex-col items-center">
            <button
              type="button"
              onClick={() => setMinutes((m) => (m + 5) % 60)}
              className="px-3 py-1 text-xs text-slate-500 hover:text-slate-900 dark:hover:text-white"
            >
              +5 м
            </button>
            <div className="w-20 h-16 rounded-2xl border border-slate-200 dark:border-zinc-700 flex items-center justify-center text-3xl font-semibold tabular-nums bg-slate-50 dark:bg-zinc-800 text-slate-900 dark:text-zinc-100">
              {String(minutes).padStart(2, '0')}
            </div>
            <button
              type="button"
              onClick={() => setMinutes((m) => (m + 55) % 60)}
              className="px-3 py-1 text-xs text-slate-500 hover:text-slate-900 dark:hover:text-white"
            >
              −5 м
            </button>
          </div>
        </div>

        <div className="grid grid-cols-4 gap-1.5 mb-4">
          {[0, 15, 30, 45].map((presetMin) => (
            <button
              key={presetMin}
              type="button"
              onClick={() => setMinutes(presetMin)}
              className={`py-1.5 rounded-xl text-xs font-medium tabular-nums ${
                minutes === presetMin
                  ? 'bg-slate-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                  : 'bg-slate-100 dark:bg-zinc-800 text-slate-600 dark:text-zinc-300'
              }`}
            >
              :{String(presetMin).padStart(2, '0')}
            </button>
          ))}
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-3.5 py-2 rounded-xl text-xs font-medium text-slate-600 dark:text-zinc-300 hover:bg-slate-100 dark:hover:bg-zinc-800"
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={() =>
              onConfirm(`${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`)
            }
            style={{ backgroundColor: accentColor }}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-white"
          >
            Выбрать
          </button>
        </div>
      </div>
    </div>
  );
};
