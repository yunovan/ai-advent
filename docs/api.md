# HTTP API проекта

Основной веб-сервер слушает `http://localhost:8080`. Каждый день публикует
REST-эндпоинты под `/api/dayNN` и веб-страницу `static/dayNN.html`.
Ошибки отдаются JSON-объектом `{"error": "..."}` через `ApiExceptionHandler`:
пустой ввод → `400`, недоступная LLM → `502`, отсутствие ключа → `401`,
превышение лимита → `429`.

## Ключевые эндпоинты по дням

- `POST /api/day1/chat` — первый запрос к облачной LLM.
- Дни 2–5 — сравнение моделей и форматов ответа (`/api/dayN/compare`).
- `POST /api/day13/tasks`, `/api/day14/advise`, `/api/day15/tasks` — диалоговые переходы.
- MCP-дни 16–21:
  - `GET /api/day16/health`, `GET /api/day16/tools`, `POST /api/day16/call`;
  - `GET /api/day21/health`, `GET /api/day21/strategies`, `POST /api/day21/ingest`,
    `POST /api/day21/search`, `POST /api/day21/compare`, `GET /api/day21/chunks`.
- RAG-дни 22–25:
  - `GET /api/day22/questions`, `POST /api/day22/ask`, `POST /api/day22/evaluate`;
  - `POST /api/day23/rewrite` (переписывание запроса), `POST /api/day23/compare`;
  - `POST /api/day24/answer`, `POST /api/day24/evaluate` (цитаты и опора на источник);
  - `POST /api/day25/ask` (чат с памятью).
- `GET /api/day26/health`, `POST /api/day26/ask`, `POST /api/day26/run` — локальная LLM.
- `POST /api/day27/...` — локальный ассистент.
- `GET /api/day28/health`, `POST /api/day28/ask`, `POST /api/day28/compare`,
  `POST /api/day28/evaluate` — локальный RAG против облака.
- `GET /api/day29/health`, `POST /api/day29/ask`, `POST /api/day29/run` — оптимизация.
- `GET /api/day30/health`, `POST /api/day30/chat`, `POST /api/day30/stress` —
  приватный сервис на локальной LLM.
- `GET /api/day31/health`, `GET /api/day31/docs`, `GET /api/day31/tools`,
  `POST /api/day31/git`, `POST /api/day31/help` — ассистент разработчика.

## MCP-транспорт

MCP-серверы поднимают отдельные HTTP-порты (см. `docs/mcp.md`). Обмен — JSON-RPC 2.0
по `POST` на путь `/mcp`, обязательный заголовок сессии `Mcp-Session-Id` после
`initialize`. Методы: `initialize`, `notifications/initialized`, `tools/list`,
`tools/call`. Параметры вызова инструмента — `params.name` и `params.arguments`.

## Ответы

Эндпоинты возвращают Java-records в JSON (например `Day28AnswerResponse`,
`Day30ChatResponse`, `Day31HelpResponse`). Метрики включают задержку в мс,
входные/выходные токены, токены в секунду, список источников и вердикты.
