import React, { useMemo, useState } from 'react';
import { Check, Copy, FileCode, FolderOpen, Search } from 'lucide-react';
import { ANDROID_PROJECT_FILES, type AndroidProjectFile } from '../data/androidProjectFiles';

const LAYER_LABELS: Record<AndroidProjectFile['layer'] | 'all', string> = {
  all: 'Все файлы',
  config: 'Gradle и Manifest',
  app: 'Точка входа',
  domain: 'Domain (модели и UseCase)',
  data: 'Data (Room и DataStore)',
  di: 'Hilt DI',
  notification: 'Уведомления (AlarmManager)',
  widget: 'Виджет (Glance)',
  ui: 'UI (Compose & ViewModel)',
};

export const CodeExplorer: React.FC = () => {
  const [layerFilter, setLayerFilter] = useState<AndroidProjectFile['layer'] | 'all'>('all');
  const [query, setQuery] = useState('');
  const [selectedPath, setSelectedPath] = useState<string>(
    ANDROID_PROJECT_FILES.find((f) => f.path.endsWith('app/build.gradle.kts'))?.path ??
      ANDROID_PROJECT_FILES[0]?.path ??
      ''
  );
  const [copied, setCopied] = useState(false);

  const visibleFiles = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ANDROID_PROJECT_FILES.filter((f) => {
      if (layerFilter !== 'all' && f.layer !== layerFilter) return false;
      if (q && !f.path.toLowerCase().includes(q) && !f.content.toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [layerFilter, query]);

  const activeFile = useMemo(
    () =>
      ANDROID_PROJECT_FILES.find((f) => f.path === selectedPath) ??
      visibleFiles[0] ??
      ANDROID_PROJECT_FILES[0],
    [selectedPath, visibleFiles]
  );

  const handleCopy = async () => {
    if (!activeFile) return;
    await navigator.clipboard.writeText(activeFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <div className="space-y-5">
      {/* Layer filter segmented row */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-1.5">
          {(Object.keys(LAYER_LABELS) as (AndroidProjectFile['layer'] | 'all')[]).map((layer) => {
            const active = layerFilter === layer;
            return (
              <button
                key={layer}
                type="button"
                onClick={() => setLayerFilter(layer)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
                  active
                    ? 'bg-slate-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                    : 'bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 text-slate-600 dark:text-zinc-300 hover:bg-slate-100 dark:hover:bg-zinc-800'
                }`}
              >
                {LAYER_LABELS[layer]}
              </button>
            );
          })}
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Фильтр файлов..."
            className="w-full pl-8 pr-3 py-1.5 rounded-lg text-xs bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 outline-none focus:border-slate-400"
          />
        </div>
      </div>

      {/* Split Explorer */}
      <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-5 items-start">
        <div className="rounded-2xl bg-white dark:bg-zinc-900 border border-slate-200 dark:border-zinc-800 overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-200 dark:border-zinc-800 flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-700 dark:text-zinc-200 flex items-center gap-2">
              <FolderOpen className="w-4 h-4" />
              Структура проекта
            </span>
            <span className="text-xs text-slate-500 tabular-nums">{visibleFiles.length}</span>
          </div>
          <div className="max-h-[640px] overflow-y-auto divide-y divide-slate-100 dark:divide-zinc-800/70">
            {visibleFiles.map((file) => {
              const shortName = file.path.split('/').pop() ?? file.path;
              const dirPath = file.path.replace(/^android\//, '').replace(`/${shortName}`, '');
              const selected = activeFile?.path === file.path;
              return (
                <button
                  key={file.path}
                  type="button"
                  onClick={() => setSelectedPath(file.path)}
                  className={`w-full text-left px-3.5 py-2.5 flex items-start gap-2.5 transition-colors ${
                    selected
                      ? 'bg-slate-100 dark:bg-zinc-800'
                      : 'hover:bg-slate-50 dark:hover:bg-zinc-800/40'
                  }`}
                >
                  <FileCode className="w-4 h-4 mt-0.5 shrink-0 text-slate-500 dark:text-zinc-400" />
                  <div className="min-w-0">
                    <div className="text-xs font-semibold text-slate-900 dark:text-zinc-100 truncate">
                      {shortName}
                    </div>
                    <div className="text-[11px] text-slate-500 dark:text-zinc-400 truncate font-mono">
                      {dirPath}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        <div className="rounded-2xl bg-slate-950 text-slate-100 border border-slate-800 overflow-hidden">
          <div className="px-4 py-3 bg-slate-900 border-b border-slate-800 flex items-center justify-between gap-3">
            <span className="text-xs font-mono text-slate-300 truncate">
              {activeFile?.path}
            </span>
            <button
              type="button"
              onClick={handleCopy}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-100 transition-colors whitespace-nowrap shrink-0"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Скопировано' : 'Копировать файл'}
            </button>
          </div>
          <pre className="p-4 text-xs leading-relaxed font-mono overflow-x-auto max-h-[640px] overflow-y-auto text-slate-200">
            <code>{activeFile?.content}</code>
          </pre>
        </div>
      </div>
    </div>
  );
};
