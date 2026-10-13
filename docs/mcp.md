# MCP: серверы, клиенты и инструменты

MCP в проекте реализован вручную: JDK-класс `com.sun.net.httpserver.HttpServer`
принимает JSON-RPC 2.0 запросы по `POST` на путь `/mcp`. После метода
`initialize` сервер возвращает заголовок `Mcp-Session-Id`, который клиент обязан
передавать в `tools/list` и `tools/call`. Версия протокола — `2024-11-05`.

## MCP-серверы и порты

| День | Класс | Порт | Инструменты |
|---|---|---|---|
| 16 | `Day16McpServer` | 8090 | `day16_sum`, `day16_upper` |
| 17 | `Day17McpServer` | 9090 | `tracker_create_task`, `tracker_list_tasks`, `tracker_add_comment` |
| 18 | `Day18McpServer` | 9091 | инструменты планировщика |
| 19 | `Day19McpServer` | 9092 | инструменты рыночных данных |
| 20 | `Day20McpServer` | 9093 | `orchestrator_servers`, `orchestrator_tools`, `orchestrator_route`, `orchestrator_call`, `orchestrator_run_flow` |
| 21 | `Day21McpServer` | 9094 | `index_status`, `index_ingest`, `index_search`, `index_compare`, `index_chunks` |
| 31 | `Day31McpServer` | 9095 | `git_branch`, `git_status`, `git_files`, `git_diff`, `git_log` |

## MCP-клиенты

Клиенты (`Day16McpClient` … `Day19McpClient`, `Day31McpClient`) работают по HTTP:
`connect()` → `initialize`, `listTools(connection)` → `tools/list`,
`callTool(connection, name, arguments)` → `tools/call`. Сессия хранится в
record-объекте `Day17Connection` (protocolVersion, serverName, serverVersion,
sessionId).

## Инструменты дня 31 (git)

- `git_branch` — текущая git-ветка, короткий хэш коммита и признак изменений.
- `git_status` — сводка `git status` (изменённые/новые файлы).
- `git_files` — список отслеживаемых файлов с фильтром по префиксу и лимитом.
- `git_diff` — diff рабочего дерева (статистика или полный, обрезается по лимиту).
- `git_log` — последние коммиты.

Инструменты выполняются через `ProcessBuilder` (`Day31GitTool`) в каталоге
репозитория с таймаутом; вывод обрезается, чтобы не переполнять контекст.
Ассистент дня 31 получает проект-контекст (ветку и статус) именно через MCP.
