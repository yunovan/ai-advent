# Сборка, запуск и тестирование

## Требования

- JDK 21 (в CI используется JBR из IntelliJ IDEA).
- Gradle через wrapper `gradlew.bat` (Windows) или `./gradlew`.
- Локальная LLM: Ollama с моделью `qwen2.5:3b` на `http://localhost:11434`.

## Запуск

```bash
./gradlew bootRun --args="--day=31"
# веб-интерфейс: http://localhost:8080/day31.html
```

Флаг `--day=NN` выбирает день. При запуске поднимаются все MCP-серверы
(порты 8090, 9090–9095) и веб-сервер на 8080.

## CLI

У многих дней есть текстовый режим: `--check`, `--ask="..."`, `--stress` и
опция `--cli`, которая завершает процесс после вывода результата. Например:

```bash
./gradlew bootRun --args="--day=31 --check --cli"
./gradlew bootRun --args="--day=31 --help=\"Как устроен RAG?\" --cli"
./gradlew bootRun --args="--day=31 --git=branch --cli"
```

## Тестирование

```bash
./gradlew test --offline
```

- Модульные тесты: `*ServiceTest`, `*GitToolTest`.
- Веб-слой: `@WebMvcTest(controllers = {..., ApiExceptionHandler})`.
- CLI: перехват `System.out` + `DefaultApplicationArguments`.
- MCP: поднятие сервера на порту `0` и обмен JSON-RPC с клиентом.
- Реальные тесты (`*LocalLlmRealTest`) включаются, только если Ollama доступна
  (`Assumptions.assumeTrue`), иначе помечаются как пропущенные.
- Оффлайн-хелпер `Day26MockOllama` подменяет Ollama в тестах.

## Переменные окружения

Параметры переопределяются через `DAYNN_*` (например `DAY31_SERVER_PORT`,
`DAY26_ENDPOINT`, `DAY21_CORPUS_DIR`). Значения по умолчанию и список полей
описаны в `application.yml` и в `*Properties`-records. Секреты (ключ облачной
LLM) хранятся в `.env` (в репозиторий не коммитится, есть шаблон `.env.example`).
