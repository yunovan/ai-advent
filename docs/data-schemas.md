# Схемы данных и хранилища

Все состояния хранятся в каталоге `data/` в виде JSON-файлов; внешней БД нет.

## Индекс документов (дни 21, 28, 29, 31)

- Каталог индекса: `data/day21-index/`, файлы `index-fixed.json` и
  `index-structural.json` (красивый JSON).
- Формат: `Day21IndexFile` — `strategy`, `documents`, `corpusChars`,
  `entries[]`.
- Каждая запись `Day21IndexEntry` = `chunk` + `vector` (float[] длиной 512).
- `Day21Chunk`: `strategy`, `source`, `fileName`, `title`, `section`,
  `chunkId` (вида `source#fixed#0001`), `startOffset`, `charCount`, `text`.
- Корпус дня 21: `data/day21-corpus/` (markdown, код, PDF).
- Корпус дня 31: `README.md` + каталог `docs/` (документация проекта).

## Трекер задач (день 17)

- Файл `data/day17-tasks/tracker.json`.
- Задачи: id, title, description, assignee, status, comments[].

## Планировщик (день 18)

- Каталог `data/day18-scheduler/`, файл `scheduler.json`.
- Записи планировщика: id, время срабатывания, cron/поллинг, состояние джоба,
  история тиков (тик по умолчанию 500 мс).

## Прочие хранилища

- Диалоги: `data/day7-dialogs`, `day8-dialogs` … `day12-dialogs`.
- Профили: `data/day12-profiles`.
- Задачи и инварианты: `data/day13-tasks`, `day14-invariants`, `day15-tasks`.
- Локальный ассистент: `data/day27-assistant`.
- Дни 28–31 состояния не хранят (индекс дня 31 строится в памяти при старте).

## Настройки

Все параметры читаются из `application.yml` по префиксам `dayNN` и могут быть
переопределены переменными окружения вида `DAYNN_*`. Значения по умолчанию
описаны в каждом `*Properties`-record.
