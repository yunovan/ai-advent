# AI Advent

Челлендж: каждый день — новая задача по построению собственных ИИ-агентов.

Стек на весь челлендж:

- Java 21+ (JDK 25 в IntelliJ подходит)
- Spring Boot 4.1
- Gradle (wrapper)
- тесты на каждую задачу
- README с сценарием видео

---

## День 1. Первый запрос к LLM через API

Минимальный код, который:

1. отправляет запрос в LLM через HTTP API;
2. получает ответ;
3. выводит его в консоль (CLI) и в простой веб-интерфейс.

Провайдер по умолчанию — [OpenRouter](https://openrouter.ai/) (`https://openrouter.ai/api/v1/chat/completions`). Формат тот же, что у OpenAI, поэтому модель можно сменить строкой `LLM_MODEL`.

### Что показать на видео

Цель ролика: за 2–3 минуты видно, что это **свой код на Java/Spring Boot**, а не скрипт в Playground.

1. **Задача дня**  
   «День 1: первый запрос к LLM через API. Отправляем промпт, получаем ответ, печатаем его.»

2. **Стек**  
   Открыть `build.gradle.kts`: Java 21+, Spring Boot, Gradle.

3. **Код запроса**  
   Открыть `LlmClient`: `POST {baseUrl}/chat/completions`, заголовок `Authorization: Bearer …`, тело с `model` и `messages`.

4. **Запуск**  
   Показать, что ключ берётся из переменной окружения, не из репозитория. Запустить приложение.

5. **Результат**  
   Два варианта — достаточно одного, второй можно мельком:
   - CLI: ответ появляется в консоли;
   - браузер `http://localhost:8080`: ввести промпт, нажать «Отправить», показать ответ модели.

6. **Тесты**  
   Коротко: `./gradlew test` — HTTP к модели мокается, живой ключ для тестов не нужен.

### Как запустить

Нужен JDK 21 или новее. Сборку делает тот JDK, которым запущена Gradle, а не отдельный JDK 21.

В IntelliJ IDEA:

1. **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
2. **Gradle JVM** = ваш Project SDK (`openjdk-25`)
3. Gradle-окно → кнопка **Reload All Gradle Projects**
4. Запуск: `AiAdventApplication` или задача `bootRun`

Если видите `Cannot find a Java installation ... languageVersion=21` — Gradle ищет именно JDK 21. Этот репозиторий так больше не настроен: достаточно JDK 25.

Ключ API **не коммитим**. Любой из способов:

1. Файл `.env` в корне проекта (уже в `.gitignore`):

```bash
copy .env.example .env
```

Откройте `.env` и впишите ключ OpenRouter:

```
LLM_API_KEY=sk-or-v1-your-key
```

2. В IntelliJ: **Run → Edit Configurations → AiAdventApplication → Environment variables**  
   `LLM_API_KEY=sk-or-v1-your-key`  
   Это пишется в `.idea/workspace.xml`, он тоже не в git.

3. Переменная окружения в терминале:

```bash
export LLM_API_KEY=sk-or-v1-your-key
# либо
export OPENROUTER_API_KEY=sk-or-v1-your-key
```

Модель по желанию (каталог: https://openrouter.ai/models):

```
LLM_MODEL=openai/gpt-4o-mini
# дешевле / бесплатный роутер:
# LLM_MODEL=openrouter/free
```

Тесты:

```bash
./gradlew test
```

Веб (после старта откройте [http://localhost:8080](http://localhost:8080)):

```bash
./gradlew bootRun
```

CLI — запрос сразу в консоль, процесс завершится:

```bash
./gradlew bootRun --args='--prompt=Привет! Кто ты? --cli'
```

Или уже собранный jar:

```bash
./gradlew bootJar
java -jar build/libs/ai-advent-0.1.0-SNAPSHOT.jar --prompt='Say hello in one sentence' --cli
```

Проверка API без браузера:

```bash
curl -s http://localhost:8080/api/day1/chat \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Назови три идеи для ИИ-агента"}'
```

### Как устроен код

| Файл | Роль |
|---|---|
| `llm/LlmClient.java` | HTTP-запрос к LLM и разбор ответа |
| `llm/LlmProperties.java` | `LLM_API_KEY` / `OPENROUTER_API_KEY`, URL и модель |
| `day01/Day01CliRunner.java` | вывод в консоль при `--prompt` |
| `day01/Day01ChatController.java` | `GET/POST /api/day1/chat` |
| `static/index.html` | простая форма в браузере |

Ключ в git не кладём. Шаблон переменных: `.env.example`.

### Тесты дня 1

- разбор ответа `chat/completions`;
- `LlmClient` ходит на мок HTTP-сервер;
- REST-контроллер: успешный ответ, пустой промпт, ошибка API;
- CLI печатает ответ и молчит, если промпта нет;
- контекст Spring поднимается.

Дальше каждый день — новый пакет `dayNN`, тесты и секция в этом README со сценарием видео.
