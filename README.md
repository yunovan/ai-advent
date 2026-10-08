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

Клиент к OpenRouter ходит по HTTP/1.1, ждёт ответ до 120 секунд и до 3 раз повторяет обрыв соединения. Если снова увидите `I/O error ...: null`, перезапустите приложение после обновления.

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

---

## День 2. Формат ответа

Один и тот же пользовательский запрос отправляется дважды:

1. **без ограничений** — только `messages` с ролью `user`;
2. **с контролем ответа**:
   - явное описание формата (system prompt: ровно 3 нумерованных пункта);
   - ограничение длины (`max_tokens=80` + «не больше 40 слов»);
   - условие завершения: инструкция и stop sequence `<<<END>>>`.

Сравните длину, структуру и `finish_reason`.

### Что показать на видео

1. **Задача дня**  
   «День 2: один промпт — два вызова API. Сначала свободный ответ, потом формат, лимит и stop.»

2. **Код контроля**  
   Открыть `Day02Constraints` и `LlmClient.complete(CompletionCommand)`: в JSON уходят `max_tokens` и `stop`.

3. **Сравнение**  
   Браузер http://localhost:8080/day2.html → один промпт → «Сравнить ответы». Слева длинный текст, справа три коротких пункта.

4. **CLI (по желанию)**  

```bash
./gradlew bootRun --args="--day=2 --prompt=Расскажи, что такое искусственный интеллект --cli"
```

5. **Тесты**  
   `./gradlew test` — проверяется, что во второй вызов реально попадают system / max_tokens / stop.

### Запуск дня 2

Веб: http://localhost:8080/day2.html (ссылка «День 2» на главной).

API:

```bash
curl -s http://localhost:8080/api/day2/compare \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Расскажи, что такое искусственный интеллект и зачем он нужен"}'
```

### Как устроен код дня 2

| Файл | Роль |
|---|---|
| `day02/Day02Constraints.java` | формат, max_tokens, stop sequence |
| `day02/Day02CompareService.java` | два вызова с одним промптом |
| `day02/Day02CompareController.java` | `GET/POST /api/day2/compare` |
| `static/day2.html` | два столбца: без / с ограничениями |
| `llm/CompletionCommand.java` | параметры запроса к LLM |

---

## День 3. Разные способы рассуждения

Одна логическая задача решается через API четырьмя способами:

1. **прямой ответ** — только текст задачи, без инструкций;
2. **«решай пошагово»** — та же задача плюс явная инструкция;
3. **сначала промпт** — модель составляет промпт, затем этим промптом решает задачу (два вызова);
4. **группа экспертов** — в одном промпте аналитик, инженер и критик, каждый даёт решение.

Для видео по умолчанию стоит задача-ловушка: бита и мяч. Правильный ответ — **5 рублей**, импульс часто даёт 10.

### Что показать на видео

1. **Задача дня**  
   «День 3: одна задача, четыре способа рассуждения. Сравним, где модель ошибается.»

2. **Код способов**  
   Открыть `Day03Prompts` и `Day03ReasoningService`: четыре (на самом деле пять) вызовов `LlmClient`.

3. **Сравнение**  
   http://localhost:8080/day3.html → «Решить четырьмя способами».  
   Смотрим: совпадают ли ответы, кто назвал 5 рублей.

4. **CLI**

```bash
./gradlew bootRun --args="--day=3 --cli"
```

5. **Тесты**  
   `./gradlew test` — проверяется, что уходят разные промпты: голый, «пошагово», генерация промпта, эксперты.

### Запуск дня 3

Веб: http://localhost:8080/day3.html

API:

```bash
curl -s http://localhost:8080/api/day3/compare \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Бита и мяч вместе стоят 110 рублей. Бита стоит на 100 рублей дороже мяча. Сколько стоит мяч?"}'
```

Это 5 запросов к LLM, ответ приходит не сразу.

### Как устроен код дня 3

| Файл | Роль |
|---|---|
| `day03/Day03Prompts.java` | тексты четырёх способов |
| `day03/Day03ReasoningService.java` | 5 вызовов API на одну задачу |
| `day03/Day03CompareController.java` | `GET/POST /api/day3/compare` |
| `static/day3.html` | четыре карточки сравнения |

---

## День 4. Температура

Один и тот же промпт уходит в API трижды:

- `temperature = 0`
- `temperature = 0.7`
- `temperature = 1.2`

Сравните ответы по точности, креативности и разнообразию. Для видео по умолчанию: «столица Франции» (точность) + слоган (креативность).

Выводы, которые можно озвучить:

- **0** — факты, код, классификация; мало разнообразия
- **0.7** — обычный диалог и объяснения
- **1.2** — идеи и слоганы; факты лучше перепроверить

### Что показать на видео

1. **Задача дня**  
   «День 4: один промпт, три температуры. Смотрим, где модель точная, а где изобретает.»

2. **Код**  
   `CompletionCommand.withTemperature` и поле `temperature` в `ChatCompletionRequest`.

3. **Сравнение**  
   http://localhost:8080/day4.html → «Сравнить температуры». Три столбца и блок выводов внизу.

4. **CLI**

```bash
./gradlew bootRun --args="--day=4 --cli"
```

5. **Тесты**  
   Проверяется, что в API уходят 0 / 0.7 / 1.2.

### Запуск дня 4

Веб: http://localhost:8080/day4.html

API:

```bash
curl -s http://localhost:8080/api/day4/compare \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Назови столицу Франции. Затем придумай необычный слоган для туристического плаката этого города."}'
```

Три запроса к LLM.

### Как устроен код дня 4

| Файл | Роль |
|---|---|
| `day04/Day04Temperatures.java` | значения 0 / 0.7 / 1.2 и выводы |
| `day04/Day04TemperatureService.java` | три вызова с разной temperature |
| `day04/Day04TemperatureController.java` | `GET/POST /api/day4/compare` |
| `static/day4.html` | три столбца + выводы |

---

## День 5. Версии моделей

Один и тот же промпт уходит в три модели разной «силы» — по аналогии с началом, серединой и концом [списка Hugging Face](https://huggingface.co/models?sort=downloads): маленькая, средняя, большая.

По умолчанию (через OpenRouter, веса на Hugging Face):

| Класс | OpenRouter id | Параметры | Карточка |
|---|---|---|---|
| Слабая | `meta-llama/llama-3.2-3b-instruct` | 3B | [HF](https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct) · [OpenRouter](https://openrouter.ai/meta-llama/llama-3.2-3b-instruct) |
| Средняя | `qwen/qwen-2.5-7b-instruct` | 7B | [HF](https://huggingface.co/Qwen/Qwen2.5-7B-Instruct) · [OpenRouter](https://openrouter.ai/qwen/qwen-2.5-7b-instruct) |
| Сильная | `meta-llama/llama-3.3-70b-instruct` | 70B | [HF](https://huggingface.co/meta-llama/Llama-3.3-70B-Instruct) · [OpenRouter](https://openrouter.ai/meta-llama/llama-3.3-70b-instruct) |

Каталоги: [OpenRouter models](https://openrouter.ai/models), [Hugging Face models](https://huggingface.co/models?sort=downloads).

Для каждого ответа приложение показывает:

- время (мс, включая ретраи HTTP);
- токены (`prompt` / `completion` / `total` из `usage`);
- стоимость USD (`usage.cost` у OpenRouter, если провайдер её вернул).

Выводы, которые можно озвучить:

- **слабая** — быстрее и дешевле, ответ короче, чаще промахи в фактах и арифметике;
- **средняя** — баланс качества, скорости и цены;
- **сильная** — обычно точнее и полнее, дольше и дороже (больше параметров и токенов).

Модели можно сменить в `.env`: `DAY5_WEAK_MODEL`, `DAY5_MEDIUM_MODEL`, `DAY5_STRONG_MODEL`.

### Что показать на видео

1. **Задача дня**  
   «День 5: один промпт на слабой, средней и сильной модели. Сравниваем качество, скорость и цену.»

2. **Код**  
   `CompletionCommand.withModel` — в `chat/completions` уходит разный `model`. `LlmReply` читает `usage` и засекает время.

3. **Сравнение**  
   http://localhost:8080/day5.html → «Сравнить модели». Три столбца: ответ + мс / токены / $. Внизу вывод и ссылки на карточки моделей.

4. **CLI**

```bash
./gradlew bootRun --args="--day=5 --cli"
```

5. **Тесты**  
   Проверяется, что в API уходят три разных `model`, а `usage` и время попадают в ответ.

### Запуск дня 5

Веб: http://localhost:8080/day5.html

API:

```bash
curl -s http://localhost:8080/api/day5/compare \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Объясни, почему у самолёта крыло имеет профиль (сверху изогнуто), а не плоскую пластину. Затем посчитай 17 × 24. Ответ на 5–7 предложений."}'
```

Три запроса к LLM, ответ приходит не сразу.

### Как устроен код дня 5

| Файл | Роль |
|---|---|
| `day05/Day5Models.java` | id моделей по умолчанию, ссылки, текст вывода |
| `day05/Day05CompareService.java` | три вызова с разным `model` |
| `day05/Day05CompareController.java` | `GET/POST /api/day5/compare` |
| `static/day5.html` | три столбца + замеры + ссылки |

---

## День 6. Первый агент

Минимальный агент как **отдельная сущность**, а не один HTTP-запрос в `LlmClient`:

1. принимает запрос пользователя;
2. сам отправляет его в LLM через API;
3. получает ответ;
4. возвращает результат — консоль (CLI) или простой веб-интерфейс.

Вся логика «запрос → LLM → ответ» инкапсулирована в агенте (`ask`). Контроллер и CLI только передают строку запроса и показывают результат — они не знают, как агент устроен внутри.

### Что показать на видео

1. **Задача дня**  
   «День 6: агент как отдельная сущность. Принимает запрос, сам вызывает LLM через API, возвращает ответ.»

2. **Код**  
   Интерфейс `Agent` с одним методом `AgentReply ask(String userRequest)`. Реализация `ChatAgent` формирует `CompletionCommand` (с системным промптом агента), отдаёт его в `LlmClient` и оборачивает `LlmReply` в `AgentReply` (ответ + модель + токены + стоимость + время).

3. **Веб**  
   http://localhost:8080/day6.html → «Спросить агента». Видно, что запрос уходит в `/api/day6/chat`, а ответ приходит с метаданными (мс / токены / $).

4. **CLI**

```bash
./gradlew bootRun --args="--day=6 --prompt=\"Привет! Кто ты?\" --cli"
```

5. **Тесты**  
   Проверяется, что `ChatAgent` строит команду с системным промптом и отдаёт правильный `AgentReply`, а контроллер корректно обрабатывает пустой запрос и ошибку LLM.

### Запуск дня 6

Веб: http://localhost:8080/day6.html

API:

```bash
curl -s http://localhost:8080/api/day6/chat \
  -H 'Content-Type: application/json' \
  -d '{"request":"Объясни, кто ты и что умеешь, одним предложением."}'
```

### Как устроен код дня 6

| Файл | Роль |
|---|---|
| `agent/Agent.java` | интерфейс агента: `AgentReply ask(String userRequest)` |
| `agent/AgentReply.java` | результат агента: содержание, модель, токены, цена, время |
| `agent/ChatAgent.java` | реализация: системный промпт + вызов `LlmClient` через `CompletionCommand` |
| `day06/Day06AgentController.java` | `GET/POST /api/day6/chat` |
| `day06/Day06CliRunner.java` | CLI-режим `--day=6` |
| `static/day6.html` | простой чат с метаданными ответа |

---

## День 7. Диалоги и память

Агент общается **диалогами**, и каждый диалог завершается осознанно:

1. **Начать диалог** — создаётся новый диалог с уникальным `id` (JSON-файл на диске);
2. пользователь пишет сообщения — агент помнит **всю переписку текущего диалога** и отправляет её в LLM целиком (без ограничений);
3. **Завершить диалог** — агент подводит **краткий итог** (LLM, если есть ключ, иначе локальная выжимка) и сохраняет его в долговременную память;
4. следующий диалог автоматически **видит итоги всех завершённых диалогов** (от новых к старым) через системный промпт и может на них ссылаться.

Проверка: в диалоге 1 спросить «Почему небо синее?» → «Завершить диалог» → «Начать диалог» → спросить «О чём мы говорили раньше?» — агент вспомнит по итогу первого диалога.

Формат файла (`data/day7-dialogs/<id>.json`):

```json
{
  "id": "852a...",
  "createdAt": "2026-09-08T10:00:00Z",
  "finishedAt": "2026-09-08T10:05:30Z",
  "summary": "Пользователь спрашивал, почему небо синее. Отвечено про рассеяние света.",
  "messages": [
    { "role": "user", "content": "Почему небо синее?" },
    { "role": "assistant", "content": "Из-за рассеяния света Рэлея." }
  ]
}
```

### Что показать на видео

1. **Задача дня**  
   «День 7: память о прошлых диалогах. Завершили диалог → начали новый → агент помнит, о чём говорили.»

2. **Код**  
   `agent/dialog/Dialog` (сам диалог с итогом), `DialogStore`/`FileDialogStore` (JSON на диске), `DialogContext` (системный промпт из итогов завершённых диалогов — это и есть память), `DialogSummarizer` (итог при завершении). `Day07DialogService.start/chat/finish`.

3. **Веб**  
   http://localhost:8080/day7.html → «Начать диалог» → «Почему небо синее?» → «Завершить диалог» (появляется итог) → «Начать диалог» → «О чём мы говорили раньше?» — агент вспомнит. В блоке «Память агента» видны итоги прошлых диалогов.

4. **CLI**

```bash
# новый диалог
./gradlew bootRun --args="--day=7 --start --cli"

# вопрос в диалоге
./gradlew bootRun --args="--day=7 --dialog=<id> --prompt="Почему небо синее?" --cli"

# завершить диалог — итог уходит в память
./gradlew bootRun --args="--day=7 --dialog=<id> --finish --cli"

# новый диалог: агент помнит итог прошлого
./gradlew bootRun --args="--day=7 --start --cli"
./gradlew bootRun --args="--day=7 --dialog=<id> --prompt="О чём мы говорили раньше?" --cli"

# список завершённых диалогов (память)
./gradlew bootRun --args="--day=7 --list --cli"
```

5. **Тесты**  
   Хранилище (создать/сохранить/прочитать, из списка завершённых возвращаются только завершённые), `DialogContext` (итоги попадают в системный промпт), `DialogSummarizer` (LLM с фолбэком на локальную выжимку), сервис (старт, чат с памятью, запрет чата в завершённом диалоге, идемпотентное завершение, 404 на неизвестный диалог), контроллер и CLI.

### Запуск дня 7

Веб: http://localhost:8080/day7.html

API:

```bash
# новый диалог → возвращает dialogId
curl -s -X POST http://localhost:8080/api/day7/dialogs

# вопрос в диалоге
curl -s -X POST http://localhost:8080/api/day7/dialogs/<id>/chat \
  -H 'Content-Type: application/json' \
  -d '{"request":"Почему небо синее?"}'

# завершить диалог → итог попадёт в память следующих
curl -s -X POST http://localhost:8080/api/day7/dialogs/<id>/finish

# завершённые диалоги (память) и конкретный диалог
curl -s http://localhost:8080/api/day7/dialogs
curl -s http://localhost:8080/api/day7/dialogs/<id>
```

### Как устроен код дня 7

| Файл | Роль |
|---|---|
| `agent/ConversationMessage.java` | сообщение: `role` + `content` |
| `agent/dialog/Dialog.java` | диалог: `id`, `createdAt/finishedAt`, `summary`, сообщения |
| `agent/dialog/DialogStore.java` | интерфейс хранилища: создать / загрузить / сохранить / список завершённых |
| `agent/dialog/FileDialogStore.java` | JSON-хранилище на диске (Jackson), по файлу на диалог |
| `agent/dialog/DialogContext.java` | системный промпт: базовый + итоги завершённых диалогов |
| `agent/dialog/DialogSummarizer.java` | итог при завершении: LLM (с фолбэком) или локальная выжимка |
| `agent/dialog/DialogStoreConfig.java` | бины хранилищ дня 7 и дня 8 (`day7.dialog-dir`/`day8.dialog-dir`) |
| `day07/Day7Properties.java` | `day7.dialog-dir` |
| `day07/Day07DialogService.java` | старт / чат / завершение + память о прошлых диалогах |
| `day07/Day07DialogController.java` | `/api/day7/dialogs`, `/dialogs/{id}/chat`, `/dialogs/{id}/finish` |
| `day07/Day07CliRunner.java` | CLI: `--day=7 --start/--dialog/--finish/--list` |
| `static/day7.html` | окно диалога с кнопками «Завершить диалог» и «Начать диалог» |

---

## День 8. Диалоги, память и токены

День 8 = день 7 (диалоги с памятью) **плюс учёт токенов**:

1. **память-контекст** (`contextTokens`) — токены системного промпта с итогами прошлых завершённых диалогов;
2. **запрос** (`requestTokens`) — токены текущего вопроса;
3. **история** (`historyTokens`) — вся переписка текущего диалога;
4. **промпт** (`promptTokens`) = память + история + запрос — то, что реально уходит в LLM;
5. **ответ** (`responseTokens`) — токены ответа модели.

Считается три набора чисел:

- **оценка** — локальный `TokenEstimator` без вызова API (эвристика: ~4 латинских символа на токен, ~1.6 кириллицы). Оценка нужна, чтобы мгновенно и офлайн показывать рост;
- **факт от провайдера** — `realPromptTokens` / `realCompletionTokens` из поля `usage` ответа LLM (приходят вместе с каждым ответом);
- **стоимость** — цена хода и накопленная цена диалога, считается из токенов и цен `day8.input-price` / `day8.output-price` (USD за 1 млн токенов).

Порядок работы: загружаем диалог → собираем системный промпт с памятью → считаем `promptTokens` → **если он больше контекстного окна `day8.context-limit`, LLM не вызывается** — вместо этого возвращается ответ с флагом `exceeded: true` и объяснением. Так же поступила бы и сама модель (ошибка `context_length_exceeded`), но агент ловит переполнение заранее, не тратя запрос.

### Что показать на видео

1. **Задача дня**  
   «Агент считает токены запроса, истории и ответа, помнит прошлые диалоги и заранее ловит переполнение контекста.»

2. **Короткий диалог**  
   http://localhost:8080/day8.html → «Начать диалог» → «Привет! Меня зовут Ася.» → в метрике видно `память ~N · запрос ~M · история ~0 · промпт ~K · ответ ~P`. Полоса лимита почти пустая.

3. **Длинный диалог**  
   Задайте 5–10 длинных вопросов в одном диалоге → промпт и полоса использования лимита растут. Откройте **«Рост токенов/цены»** — таблица, где с каждым ходом `промпт, tok`, `сумма tok` и `сумма, $` монотонно растут: история пересылается целиком на каждом ходу, поэтому диалог дорожает. Ниже — таблица **«Прошлые диалоги — для сравнения»** с суммарными токенами и стоимостью каждого завершённого диалога (посчитанными с той памятью, что он видел): видно, как итоги предыдущих диалогов утяжеляют промпт следующих.

4. **Переполнение**  
   Поставьте в поле «Контекстный лимит» маленькое значение (например `60`) и задавайте вопросы. Агент дойдёт до лимита и ответит, что диалог **превысил контекстное окно**; LLM при этом не вызывается (красный баннер, `exceeded: true`).

5. **Память**  
   «Почему небо синее?» → «Завершить диалог» → «Начать диалог» → «О чём мы говорили раньше?» — агент вспомнит, и память учтена в токенах (`contextTokens`).

6. **CLI**

```bash
# новый диалог
./gradlew bootRun --args="--day=8 --start --cli"

# спросить и увидеть метрики токенов
./gradlew bootRun --args="--day=8 --dialog=<id> --prompt="Привет, меня зовут Ася" --cli"

# таблица роста токенов/цены по диалогу
./gradlew bootRun --args="--day=8 --dialog=<id> --table --cli"

# показать переполнение лимита (маленький лимит)
./gradlew bootRun --args="--day=8 --dialog=<id> --prompt="Длинное сообщение..." --limit=60 --cli"

# завершить диалог — итог в память
./gradlew bootRun --args="--day=8 --dialog=<id> --finish --cli"
```

### Запуск дня 8

Веб: http://localhost:8080/day8.html

API:

```bash
# новый диалог
curl -s -X POST http://localhost:8080/api/day8/dialogs

# чат с метриками токенов; contextLimit можно указать, чтобы продемонстрировать переполнение
curl -s -X POST http://localhost:8080/api/day8/dialogs/<id>/chat \
  -H 'Content-Type: application/json' \
  -d '{"request":"Привет, меня зовут Ася","contextLimit":128000}'

# таблица роста токенов и стоимости по диалогу
curl -s http://localhost:8080/api/day8/dialogs/<id>/metrics

# завершить диалог
curl -s -X POST http://localhost:8080/api/day8/dialogs/<id>/finish
```

### Как устроен код дня 8

| Файл | Роль |
|---|---|
| `day08/TokenEstimator.java` | офлайн-оценка токенов: ~4 лат. символа / ~1.6 кириллицы на токен |
| `day08/Day08DialogService.java` | сервис: чат с памятью + токены/цена + защита от превышения лимита, `metrics()` — таблица роста |
| `day08/Day8Properties.java` | `day8.dialog-dir`, `day8.context-limit`, `day8.input-price`, `day8.output-price` |
| `day08/Day08DialogController.java` | `/api/day8/dialogs`, `/dialogs/{id}/chat`, `/metrics`, `/finish` |
| `day08/Day08CliRunner.java` | CLI: `--day=8 --start/--dialog/--limit/--table/--finish` |
| `static/day8.html` | окно диалога + метрики, полоса лимита, таблица роста |

Общую инфраструктуру диалогов (пакет `agent/dialog/`) поставляет день 7: `Dialog`, `DialogStore`, `FileDialogStore`, `DialogContext`, `DialogSummarizer` и `DialogStoreConfig` с бинами `day7DialogStore`/`day8DialogStore`/`day9DialogStore`.

Настройки:

```bash
DAY8_DIALOG_DIR=data/day8-dialogs   # где лежат диалоги дня 8
DAY8_CONTEXT_LIMIT=128000           # контекстное окно модели в токенах (уменьшать — чтобы показать переполнение)
DAY8_INPUT_PRICE=0.15               # цена за 1 млн входных токенов, USD
DAY8_OUTPUT_PRICE=0.60              # цена за 1 млн выходных токенов, USD
```

## День 9. Управление контекстом — сжатие истории

Продолжение дня 8 с той же математикой токенов, но история больше не растёт безгранично: последние `recent-messages` сообщений отправляются в модель как есть, а всё, что старше, складывается в chunks по `chunk-size` сообщений и превращается в rolling summary, который вставляется в системный промпт баннером «Сжатая история» и пересчитывается по мере роста диалога.

### Что мы измеряем

В отличие от дней 7–8, промпт больше не растёт линейно с каждым ходом:

- **Без сжатия** — промпт растёт линейно: на ~X-м ходе он дойдёт до лимита контекста (`context_limit`), и запрос перестанет проходить.
- **Со сжатием** — после первых `recent + chunk` сообщений лишние chunk'и уходят из отправляемой истории, их место занимает короткое резюме. Промпт стабилизируется: рост почти прекращается.
- Сэкономленные токены и доллары считаются как разница «полный промпт, если бы сжатия не было» минус «реальный отправленный промпт» (колонки `без сжатия` vs `сжато` в таблице роста и в метриках хода).

Важно: на коротких диалогах сжатие может не окупаться — баннер сам занимает токены, поэтому экономия видна только после того, как скопилось несколько chunk'ов. Это честный результат, и его хорошо видно в таблице роста.

### Запуск

```bash
./gradlew bootRun --args="--day=9"
# UI: http://localhost:8080/day9.html

# CLI
./gradlew bootRun --args="--day=9 --list"
./gradlew bootRun --args="--day=9 --start"
./gradlew bootRun --args="--day=9 --dialog=<id> --prompt=\"Расскажи про себя\""
./gradlew bootRun --args="--day=9 --dialog=<id> --prompt=\"...\" --no-compress"   # контрольный замер без сжатия
./gradlew bootRun --args="--day=9 --dialog=<id> --table --limit=1000"             # лимит/таблица роста
./gradlew bootRun --args="--day=9 --dialog=<id> --finish"
```

### Как устроен код дня 9

| Файл | Роль |
|---|---|
| `day09/Day09HistoryCompressor.java` | сжатие chunk'а: LLM с локальным запасным вариантом, если нет ключа/ошибка |
| `day09/Day09DialogService.java` | сервис: `compressHistory()` — намотка chunk'ов сверх окна `recent`, вставка summary в системный промпт, сравнение «сжато/без сжатия», `metrics()` — таблица роста с повтором бюджета |
| `day09/Day9Properties.java` | `day9.dialog-dir`, `day9.context-limit`, `day9.input-price`, `day9.output-price`, `day9.recent-messages`, `day9.chunk-size` |
| `day09/Day09DialogController.java` | `/api/day9/dialogs`, `/dialogs/{id}/chat?compression=...`, `/metrics`, `/finish` |
| `day09/Day09CliRunner.java` | CLI: `--day=9 --start/--dialog/--list/--table/--finish/--no-compress/--limit` |
| `static/day9.html` | окно диалога + чекбокс «Сжимать историю» + таблица роста с колонками сжато/без сжатия |

В `Dialog` добавлены поля `historySummary` и `historySummaryCount`: это rolling summary и число сообщений, которые он уже покрыл. `compressHistory()` по очереди складывает все chunk'и, целиком помещающиеся «до» окна из `recent-messages` сообщений, и конкатенирует их резюме. При `compression=false` summary по-прежнему поддерживается, но в промпт не вставляется — это «контрольная группа» для сравнения.

Настройки:

```bash
DAY9_DIALOG_DIR=data/day9-dialogs   # где лежат диалоги дня 9
DAY9_CONTEXT_LIMIT=128000           # контекстное окно модели в токенах (уменьшать — чтобы показать переполнение)
DAY9_INPUT_PRICE=0.15               # цена за 1 млн входных токенов, USD
DAY9_OUTPUT_PRICE=0.60              # цена за 1 млн выходных токенов, USD
DAY9_RECENT_MESSAGES=10             # последние N сообщений всегда отправляются полностью
DAY9_CHUNK_SIZE=10                  # сколько сообщений умирается в один chunk для сжатия
```

---

## День 10. Управление контекстом — три стратегии

Три способа держать диалог в пределах контекстного окна:

1. **Sliding Window** — в модель уходят только последние N сообщений, всё остальное отбрасывается. UI позволяет менять N для каждого диалога.
2. **Sticky Facts** — в системный промпт добавляется блок ключ-значение, который обновляется после каждого сообщения. В модель идут facts + последние N сообщений.
3. **Branching** — диалог можно разделить ветками: сохраняется checkpoint, создаётся ветка, каждая живёт независимо. В модель идёт вся история ветки.

**Важно:** полный транскрипт (все сообщения, все факты) всегда хранится в JSON-файле целиком. Отбрасывается **только то, что уходит в модель** — это нужно, чтобы метрики (суммарные токены и стоимость) были честными и сравнимыми со сценарием «без стратегии».

После каждого сообщения в режиме Facts — `Day10FactExtractor` (LLM или локальный парсер строк `ключ: значение`) извлекает факты и обновляет блок. Тумблеры активности facts доступны на UI.

### Запуск

```bash
./gradlew bootRun --args="--day=10"
# UI: http://localhost:8080/day10.html

# CLI
./gradlew bootRun --args="--day=10 --list"
./gradlew bootRun --args="--day=10 --start --strategy=sliding --window=6"
./gradlew bootRun --args="--day=10 --dialog=<id> --prompt=\"Соберём ТЗ\""
./gradlew bootRun --args="--day=10 --dialog=<id> --fact=\"Цель: собрать ТЗ\""
./gradlew bootRun --args="--day=10 --dialog=<id> --checkpoint --branch"
./gradlew bootRun --args="--day=10 --dialog=<id> --switch=b2 --prompt=\"Продолжение\""
./gradlew bootRun --args="--day=10 --dialog=<id> --table"
./gradlew bootRun --args="--day=10 --dialog=<id> --finish"
```

### Как устроен код дня 10

| Файл | Роль |
|---|---|
| `day10/Day10Strategy.java` | enum: `sliding`, `facts`, `branching` (с `@JsonValue` на `key()`) |
| `day10/Day10Fact.java` | ключ-значение + флаг `active`, `usable()`, `display()` |
| `day10/Day10Branch.java` | ветка диалога: `id`, `name`, `parentId`, `checkpointAt`, `messages`; `main()` и `fork()` |
| `day10/Day10Dialog.java` | диалог: `strategy`, `facts`, `branches`, `activeBranchId`; `activeMessages()`, `withBranchMessages()`, `withWindow()`, `withFacts()`, `withCheckpoint()`, `withNewBranch()`, `withActiveBranch()` |
| `day10/Day10FileDialogStore.java` | JSON-хранилище: `create(strategy, window)`, `load`, `save`, `finishedDialogs`, `allDialogs` (с учётом facts/branches) |
| `day10/Day10FactExtractor.java` | извлечение facts из текста: LLM (если ключ есть) или локальный парсер `ключ: значение` |
| `day10/Day10Properties.java` | `day10.dialog-dir`, `day10.context-limit`, `day10.input-price`, `day10.output-price`, `day10.default-window` |
| `day10/Day10DialogService.java` | сервис: `start`, `chat`, `addFact`, `checkpoint`, `createBranch`, `switchBranch`, `metrics`, `dialogs`, `finish`; budgets replay, переполнение |
| `day10/Day10DialogController.java` | `/api/day10/dialogs`, `chat`, `facts`, `checkpoint`, `branches`, `branches/{id}/activate`, `metrics`, `finish` |
| `day10/Day10CliRunner.java` | CLI: `--day=10 --start/--strategy/--window/--dialog/--prompt/--fact/--checkpoint/--branch/--switch/--table/--finish` |
| `static/day10.html` | UI: выбор стратегии, панель facts (тумблеры + добавление), панель веток (checkpoint/create/switch), транскрипт, метрики, список диалогов |

Настройки:

```bash
DAY10_DIALOG_DIR=data/day10-dialogs
DAY10_CONTEXT_LIMIT=128000
DAY10_INPUT_PRICE=0.15
DAY10_OUTPUT_PRICE=0.60
DAY10_DEFAULT_WINDOW=8
```

## День 11. Модель памяти агента

Три явных слоя памяти, каждый хранится отдельно, и решение «что и куда сохраняется» принимается вручную:

1. **Краткосрочная (short-term)** — текущий диалог: в модель уходят только последние N сообщений (окно по умолчанию 10). Из каждого сообщения `Day11FactExtractor` извлекает кандидатов `ключ: значение`, которые появляются как темноватые записи и **ждут вашего решения** — остаются ли они в краткосрочной или переносятся в рабочий отдел.
2. **Рабочая (working)** — данные текущей задачи: цель, требования, бюджет, сроки, стек. В модель попадают каждый запрос и живут, пока выполняется задача.
3. **Долговременная (long-term)** — профиль, принятые решения и накопленные знания: живут между задачами и между диалогами. Пополняется кнопками «→ Долговременная», формой ручного `remember`, фиксацией решений (`decide`) и автоматически итогом завершённого диалога (`finish` → `итог:<id>`).

Слои лежат в разных директориях (`short-term/`, `working/`, `long-term/`), так что «какие данные попадают в каждый слой» видно по файловой системе. `Day11MemoryRules.suggestLayer` подсказывает дефолт (профильное → долговременная, задачное → рабочая), но выбор всегда можно переопределить явно.

### Запуск

```bash
./gradlew bootRun --args="--day=11"
# UI: http://localhost:8080/day11.html

# CLI
./gradlew bootRun --args="--day=11 --list"
./gradlew bootRun --args="--day=11 --start --cli"
./gradlew bootRun --args="--day=11 --dialog=<id> --prompt=\"Соберём ТЗ, я Ася, бюджет 10 000$\" --cli"
./gradlew bootRun --args="--day=11 --dialog=<id> --promote=\"Имя\" --target=long-term --cli"
./gradlew bootRun --args="--day=11 --dialog=<id> --remember=\"Стек: Java 21\" --layer=working --cli"
./gradlew bootRun --args="--day=11 --dialog=<id> --decide=\"Берём Java 21\" --cli"
./gradlew bootRun --args="--day=11 --dialog=<id> --table --cli"
./gradlew bootRun --args="--day=11 --dialog=<id> --finish --cli"
```

### Как устроен код дня 11

| Файл | Роль |
|---|---|
| `day11/Day11MemoryLayer.java` | enum слоёв: `short-term`, `working`, `long-term` (с `@JsonValue` на `key()`); `from(value)` со списком доступных |
| `day11/Day11MemoryEntry.java` | запись памяти: ключ, значение, слой, источник, флаг `pending`; `withLayer()`, `hardened()`, `usable()`, `isCandidate()`, `display()` |
| `day11/Day11MemoryRules.java` | `suggestLayer(key)`: маркеры профиля → долговременная, маркеры задачи → рабочая, иначе краткосрочная |
| `day11/Day11FileMemoryStore.java` | файловое хранилище по слоям: `save`, `find`, `delete`, `all`, `all(layer)`; sanitize ключей под имена файлов |
| `day11/Day11FactExtractor.java` | извлечение кандидатов из текста: LLM (если есть ключ) или локальный парсер `ключ: значение`; записи → `SHORT_TERM`, `pending=true` |
| `day11/Day11Properties.java` | `day11.dialog-dir`, `day11.memory-dir`, `day11.context-limit`, `day11.input-price`, `day11.output-price`, `day11.short-term-window` |
| `day11/Day11DialogService.java` | сервис: `start`, `chat`, `remember`, `promote`, `decide`, `forget`, `metrics`, `finish`, `dialogs`, `get`; блок «Слой памяти: …» в системном промпте, окно краткосрочной, переполнение |
| `day11/Day11DialogController.java` | `/api/day11/dialogs`, `chat`, `remember`, `promote`, `decide`, `forget`, `metrics`, `finish` |
| `day11/Day11CliRunner.java` | CLI: `--day=11 --start/--list/--dialog/--prompt/--remember/--promote/--decide/--forget/--table/--finish/--cli` |
| `static/day11.html` | UI: панели краткосрочной (кандидаты с кнопками переноса), рабочей и долговременной памяти, решения, метрики роста токенов/цены |

Настройки:

```bash
DAY11_DIALOG_DIR=data/day11-dialogs
DAY11_MEMORY_DIR=data/day11-memory
DAY11_CONTEXT_LIMIT=128000
DAY11_INPUT_PRICE=0.15
DAY11_OUTPUT_PRICE=0.60
DAY11_SHORT_TERM_WINDOW=10
```

---

## День 12. Персонализация ассистента

Профиль пользователя поверх модели памяти дня 11: стиль, формат и ограничения подключаются к **каждому** запросу автоматически, поэтому один и тот же ассистент отвечает по-разному для разных пользователей.

Профиль (`Day12Profile`) описывает:

- **Имя** — как обращаться и подписывать ответы;
- **Стиль** — «кратко и по делу», «развёрнуто и формально», «точно и с деталями»;
- **Формат** — «списки, шаги 1-2-3», «отчёт с заголовками и таблицей сроков», «код, термины, минимум воды»;
- **Ограничения** — «без жаргона», «без эмодзи», «только русский», «без повторов» и т.п.;
- **Заметки** — роль, контекст, пожелания.

`Day12Personalizer.block(profile)` собирает из профиля текстовый блок, который добавляется в системный промпт первым (после базы `DialogContext`): «Профиль пользователя: … Учитывай этот профиль в каждом ответе». Профиль привязан к диалогу через `Day12DialogProfileStore` (dialogId → profileId), переключается в любой момент (кнопкой/`POST /profile`), и подключается к каждому последующему запросу — проверка «что ассистент учитывает автоматически».

При старте по умолчанию создаются 3 профиля-примера: **Ася** (кратко, списки), **Менеджер** (отчёт, сроки), **Разработчик** (точно, без воды). Если их нет в `data/day12-profiles`, `seedIfEmpty()` создаст при первом обращении.

### Запуск

```bash
./gradlew bootRun --args="--day=12"
# UI: http://localhost:8080/day12.html

# CLI
./gradlew bootRun --args="--day=12 --profiles"
./gradlew bootRun --args="--day=12 --create-profile=\"Имя:Тестировщик;Стиль:аккуратно;Формат:чек-листы;Ограничения:без абстракций;Заметки:QA-инженер\""
./gradlew bootRun --args="--day=12 --start --profile=Менеджер --cli"
./gradlew bootRun --args="--day=12 --list"
./gradlew bootRun --args="--day=12 --dialog=<id> --prompt=\"Какие риски у перехода на Java 21?\" --cli"
./gradlew bootRun --args="--day=12 --dialog=<id> --profile=dev --cli"
./gradlew bootRun --args="--day=12 --dialog=<id> --remember=\"Бюджет: 10 000$\" --layer=working --cli"
./gradlew bootRun --args="--day=12 --dialog=<id> --finish --cli"
```

### Как устроен код дня 12

| Файл | Роль |
|---|---|
| `day12/Day12Profile.java` | запись профиля: id, name, style, format, restrictions, notes, createdAt; `usable()`, `display()` |
| `day12/Day12Personalizer.java` | `block(profile)` — текстовый блок «Профиль пользователя» для системного промпта; `defaultRestrictions(name)` |
| `day12/Day12ProfileStore.java` | файловое хранилище профилей: `create`, `find`, `findByName`, `all`, `delete`, `seedIfEmpty()` (3 примера) |
| `day12/Day12DialogProfileStore.java` | связь диалог → профиль: `assign`, `profileIdFor` (JSON в `profileDir/links/`) |
| `day12/Day12DialogStore.java` | файловое хранилище диалогов дня 12 (реализация `DialogStore`) + `allDialogs()` |
| `day12/Day12MemoryStoreConfig.java` | бины: `day12MemoryStore`, `day12DialogStore`, `day12ProfileStore`, `day12DialogProfileStore` |
| `day12/Day12DialogService.java` | сервис: `start(profile)`, `chat`, `setProfile`, `remember`, `createProfile`, `profiles`, `get`, `dialogs`, `finish`; системный промпт = база + блок профиля + блок памяти (рабочая/долговременная); лимит контекста и метрики токенов |
| `day12/Day12DialogController.java` | `/api/day12/dialogs` (POST/GET), `chat`, `profile`, `remember`, `finish`, `/api/day12/profiles` (GET/POST), `profiles/{id}` |
| `day12/Day12CliRunner.java` | CLI: `--day=12 --profiles/--create-profile/--start/--profile/--list/--dialog/--prompt/--remember/--finish/--cli` |
| `static/day12.html` | UI: карточки профилей (выбор и переключение), чат с тегом профиля, панели рабочей и долговременной памяти, форма создания профиля, прошлые диалоги |

Настройки:

```bash
DAY12_DIALOG_DIR=data/day12-dialogs
DAY12_PROFILE_DIR=data/day12-profiles
DAY12_MEMORY_DIR=data/day12-memory
DAY12_CONTEXT_LIMIT=128000
DAY12_INPUT_PRICE=0.15
DAY12_OUTPUT_PRICE=0.60
DAY12_SHORT_TERM_WINDOW=10
```

---

## День 13. Состояние задачи (state machine)

Агент с формализованным состоянием задачи как конечным автоматом:

- **этап задачи** — один из `planning → execution → validation → done` (планирование → выполнение → проверка → готово);
- **текущий шаг** — порядковый номер шага внутри задачи;
- **ожидаемое действие** — что агент должен сделать дальше.

Проверки дня:

- пауза на любом этапе: `--pause` ставит задачу на паузу, `--resume` продолжает — без потери состояния;
- продолжение без повторных объяснений: системный промпт при `continue` содержит блок «Состояние задачи» (этап, шаг, ожидаемое действие) и инструкцию не повторять уже данные объяснения.

### Запуск

```bash
./gradlew bootRun --args="--day=13"
# UI: http://localhost:8080/day13.html

# CLI
./gradlew bootRun --args="--day=13 --create=\"Переезд на Java 21\" --cli"
./gradlew bootRun --args="--day=13 --list"
./gradlew bootRun --args="--day=13 --task=<id> --advance --cli"
./gradlew bootRun --args="--day=13 --task=<id> --step=3 --cli"
./gradlew bootRun --args="--day=13 --task=<id> --expected-action=\"Проверить сборку\" --cli"
./gradlew bootRun --args="--day=13 --task=<id> --note=\"План согласован\" --cli"
./gradlew bootRun --args="--day=13 --task=<id> --pause --cli"
./gradlew bootRun --args="--day=13 --task=<id> --resume --cli"
./gradlew bootRun --args="--day=13 --task=<id> --prompt=\"Выполняй следующий шаг\" --cli"
```

### Как устроен код дня 13

| Файл | Роль |
|---|---|
| `day13/Day13Stage.java` | этапы конечного автомата: планирование, выполнение, проверка, готово |
| `day13/Day13StateMachine.java` | правила переходов `planning → execution → validation → done`, `canTransition`, `next`, терминальный этап |
| `day13/Day13Task.java` | задача: id, название, этап, шаг, ожидаемое действие, пауза, заметки, история, метки времени |
| `day13/Day13TaskState.java` | срез состояния задачи для API |
| `day13/Day13TaskStore.java` | файловое хранилище задач (JSON) |
| `day13/Day13TaskPrompt.java` | блок «Состояние задачи» для системного промпта + инструкция не повторять объяснения |
| `day13/Day13TaskService.java` | сервис: `create`, `get`, `list`, `state`, `advance` (по автомату), `setStep`, `setExpectedAction`, `addNote`, `pause`, `resume`, `continueTask` (LLM, лимит контекста, пауза блокирует продолжение) |
| `day13/Day13TaskController.java` | `/api/day13/tasks` (POST/GET), `tasks/{id}`, `state`, `advance`, `step`, `expected-action`, `note`, `pause`, `resume`, `continue` |
| `day13/Day13CliRunner.java` | CLI: `--day=13 --create/--list/--task/--advance/--step/--expected-action/--note/--pause/--resume/--prompt/--cli` |
| `static/day13.html` | UI: создание задачи, карточка состояния с цепочкой этапов, управление этим автоматом, история продолжения |

Настройки:

```bash
DAY13_TASK_DIR=data/day13-tasks
DAY13_CONTEXT_LIMIT=128000
DAY13_INPUT_PRICE=0.15
DAY13_OUTPUT_PRICE=0.60
DAY13_SHORT_TERM_WINDOW=10
```

---

## День 14. Инварианты и ограничения состояния

Проектные инварианты (архитектурные решения, стек, бизнес-правила), хранящиеся отдельно от диалога и принудительно учитываемые ассистентом:

- инварианты хранятся в `data/day14-invariants/` как JSON-файлы;
- при каждом запросе блок инвариантов встраивается в системный промпт с инструкцией «ОТКАЖИСЬ, если запрос нарушает инвариант»;
- ассистент явно объясняет отказ и указывает, какой инвариант нарушен.

Проверки дня:

- конфликт запроса и инварианта: «Заменим базу на MySQL?» при инварианте «только PostgreSQL» → отказ с объяснением;
- объяснение отказа: системный промпт содержит `[стек] База данных: только PostgreSQL` и требование «ОТКАЖИСЬ»;
- выключенные инварианты не попадают в промпт.

### Запуск

```bash
./gradlew bootRun --args="--day=14"
# UI: http://localhost:8080/day14.html

# CLI
./gradlew bootRun --args="--day=14 --category=стек --title=\"База данных\" --description=\"только PostgreSQL\" --cli"
./gradlew bootRun --args="--day=14 --category=архитектура --title=\"Микросервисы\" --description=\"без монолита\" --cli"
./gradlew bootRun --args="--day=14 --invariants"
./gradlew bootRun --args="--day=14 --invariant=<id> --deactivate --cli"
./gradlew bootRun --args="--day=14 --invariant=<id> --delete --cli"
./gradlew bootRun --args="--day=14 --prompt=\"Заменим базу на MySQL?\" --cli"
```

### Как устроен код дня 14

| Файл | Роль |
|---|---|
| `day14/Day14Category.java` | категории инвариантов: архитектура, решение, стек, бизнес |
| `day14/Day14Invariant.java` | инвариант: id, категория, заголовок, описание, активность, дата создания |
| `day14/Day14InvariantStore.java` | файловое хранилище инвариантов (JSON) |
| `day14/Day14InvariantPrompt.java` | блок «Инварианты проекта» для системного промпта + инструкция отказывать |
| `day14/Day14Properties.java` | настройки: `invariantDir`, `contextLimit`, `inputPrice`, `outputPrice`, `shortTermWindow` |
| `day14/Day14Config.java` | бин `Day14InvariantStore` |
| `day14/Day14InvariantService.java` | CRUD инвариантов + `advise(request, contextLimit)` (LLM, лимит контекста, active-только) |
| `day14/Day14InvariantController.java` | `/api/day14/invariants` (POST/GET), `active`, `invariants/{id}`, `deactivate`, `DELETE`, `/api/day14/advise` |
| `day14/Day14CliRunner.java` | CLI: `--day=14 --category/--title/--description/--invariants/--invariant/--deactivate/--delete/--prompt/--cli` |
| `static/day14.html` | UI: добавление инвариантов по категориям, список с выключением, чат-проверка (выделение отказов) |

Настройки:

```bash
DAY14_INVARIANT_DIR=data/day14-invariants
DAY14_CONTEXT_LIMIT=128000
DAY14_INPUT_PRICE=0.15
DAY14_OUTPUT_PRICE=0.60
DAY14_SHORT_TERM_WINDOW=10
```

---

## День 15. Контролируемые переходы состояний

Задача живёт в конечном наборе состояний с явно заданными разрешёнными переходами (управляемый граф, а не «любое движение вперёд»):

- состояния: `планирование → план утверждён → выполнение → проверка → готово`;
- разрешённые переходы — рёбра графа: план утверждается, затем выполняется, затем проверяется, затем завершается; разрешён и контролируемый возврат (план на доработку, реализация на исправление);
- «перепрыгнуть» этап нельзя: реализация невозможна без утверждённого плана, завершение — только после проверки;
- пауза замораживает состояние; после `resume` продолжение и переходы корректны.

Проверки дня:

- попытка перейти в недопустимое состояние (например `планирование → выполнение`) отклоняется с объяснением и списком разрешённых переходов;
- реакция ассистента детерминирована и происходит до вызова модели;
- продолжение после паузы сохраняет все разрешённые переходы текущего состояния.

## День 16. Подключение MCP

Совместно с приложением стартует собственный MCP-сервер (Model Context Protocol) на порту 8090, а приложение подключается к нему как MCP-клиент:

- MCP-сервер поднимается вместе с приложением (`@PostConstruct`) на `http://localhost:8090/mcp`;
- клиент устанавливает MCP-соединение (`initialize` → `initialized`) и получает от сервера список доступных инструментов (`tools/list`);
- регистрируются два демо-инструмента: `day16_sum` (сложение целых чисел) и `day16_upper` (текст в верхний регистр);
- реализован минимальный поднабор протокола MCP поверх JSON-RPC (Streamable HTTP): `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`.

Проверки дня:

- соединение устанавливается: `initialize` возвращает версию протокола, capabilities и `serverInfo`, выдаётся идентификатор сессии;
- список инструментов корректно возвращается: `tools/list` возвращает `day16_sum` и `day16_upper` с описаниями и JSON Schema;
- вызов `tools/call` исполняет инструменты, ошибки (неизвестный инструмент, отсутствующие аргументы) помечаются `isError=true`.

### Запуск

```bash
./gradlew bootRun --args="--day=15"
# UI: http://localhost:8080/day15.html

# CLI
./gradlew bootRun --args="--day=15 --create=\"Переезд на Java 21\" --cli"
./gradlew bootRun --args="--day=15 --list"
./gradlew bootRun --args="--day=15 --task=<id> --transition=выполнение --cli"
#   → Недопустимый переход: 'планирование' → 'выполнение' ...
./gradlew bootRun --args="--day=15 --task=<id> --advance --cli"
#   → планирование → план утверждён
./gradlew bootRun --args="--day=15 --task=<id> --transition=выполнение --cli"
./gradlew bootRun --args="--day=15 --task=<id> --transition=готово --cli"
#   → Недопустимый переход: 'выполнение' → 'готово' ...
./gradlew bootRun --args="--day=15 --task=<id> --advance --cli"
#   → выполнение → проверка
./gradlew bootRun --args="--day=15 --task=<id> --transition=готово --cli"
#   → проверка → готово (завершено)
./gradlew bootRun --args="--day=15 --task=<id> --pause --cli"
./gradlew bootRun --args="--day=15 --task=<id> --resume --cli"
./gradlew bootRun --args="--day=15 --task=<id> --prompt=\"Продолжай работу\" --cli"
```

### Как устроен код дня 15

| Файл | Роль |
|---|---|
| `day15/Day15Stage.java` | состояния задачи: планирование, план утверждён, выполнение, проверка, готово |
| `day15/Day15StateMachine.java` | граф разрешённых переходов (`canTransition`, `transition`, `allowedTargets`, `nextForward`), запрет перепрыгивания, сообщение о недопустимом переходе |
| `day15/Day15Task.java` | задача с состоянием и историей переходов |
| `day15/Day15TaskState.java` | срез состояния + список разрешённых переходов |
| `day15/Day15TaskStore.java` | файловое хранилище задач (JSON) |
| `day15/Day15TaskPrompt.java` | блок состояния с разрешёнными переходами + правила «не перепрыгивай» |
| `day15/Day15TaskService.java` | `create`, `transition(taskId, target)` — детерминированная проверка по графу, `advance`, `setStep`, `setExpectedAction`, `addNote`, `pause`, `resume`, `continueTask` (LLM, лимит контекста, пауза блокирует переходы и продолжение) |
| `day15/Day15TaskController.java` | `/api/day15/tasks` (POST/GET), `state`, `transition`, `advance`, `step`, `expected-action`, `note`, `pause`, `resume`, `continue` |
| `day15/Day15CliRunner.java` | CLI: `--day=15 --create/--list/--task/--transition/--advance/--step/--expected-action/--note/--pause/--resume/--prompt/--cli` |
| `static/day15.html` | UI: карточка состояния с цепочкой этапов, кнопки только разрешённых переходов, пауза/продолжение, история |
| `day15/Day15TransitionRequest.java` и др. | DTO запросов |

### Как устроен код дня 16

| Файл | Роль |
|---|---|
| `day16/Day16Properties.java` | настройки: `serverPort`, `path`, `name`, `version` |
| `day16/Day16Tool.java` | инструмент MCP: имя, описание, JSON Schema, обработчик |
| `day16/Day16Connection.java` | установленное MCP-соединение: протокол, сервер, сессия |
| `day16/Day16ToolInfo.java` | название и описание инструмента из `tools/list` |
| `day16/Day16ToolResult.java` | результат вызова инструмента: текст, признак ошибки |
| `day16/Day16McpException.java` | ошибки MCP (соединение, протокол, HTTP) |
| `day16/Day16McpServer.java` | MCP-сервер на JDK `HttpServer`: JSON-RPC `initialize`/`ping`/`tools/list`/`tools/call`, сессии |
| `day16/Day16McpClient.java` | MCP-клиент на `java.net.http.HttpClient`: подключение, список инструментов, вызов |
| `day16/Day16McpService.java` | сервис: ленивое подключение и переподключение при сбое |
| `day16/Day16McpController.java` | `/api/day16/health`, `/api/day16/tools`, `/api/day16/call` (502 при недоступном MCP) |
| `day16/Day16CliRunner.java` | CLI: `--day=16 --check/--tools/--call=<имя> [--arg=k=v ...]` |
| `static/day16.html` | UI: проверка соединения, список инструментов, вызов инструмента |

Настройки:

```bash
DAY15_TASK_DIR=data/day15-tasks
DAY15_CONTEXT_LIMIT=128000
DAY15_INPUT_PRICE=0.15
DAY15_OUTPUT_PRICE=0.60
DAY15_SHORT_TERM_WINDOW=10
```

```bash
DAY16_SERVER_PORT=8090
DAY16_PATH=/mcp
DAY16_NAME=ai-advent-mcp
DAY16_VERSION=0.1.0
```

## День 17. Первый инструмент MCP

Вместе с приложением стартует собственный MCP-сервер «трекер» (mock Яндекс.Трекера) на порту 9090. Агент разбирает запрос пользователя, сам выбирает инструмент, вызывает его по MCP и формулирует ответ:

- MCP-сервер «трекер» поднимается вместе с приложением (`@PostConstruct`) на `http://localhost:9090/mcp`;
- регистрируются три инструмента с описаниями и JSON Schema параметров: `tracker_create_task`, `tracker_list_tasks`, `tracker_add_comment`;
- инструменты реально работают: создают задачи и комментарии, фильтруют по статусу; данные хранятся в `data/day17-tasks/tracker.json`;
- агент (`Day17AgentService`) детерминированно распознаёт намерение по шаблонам («создай задачу», «покажи задачи», «добавь комментарий»), формирует аргументы, вызывает инструмент через MCP-клиент;
- после вызова инструмента агент передаёт результат LLM для формулировки ответа; если LLM недоступен — возвращает результат инструмента как есть.

Проверки дня:

- `initialize` (`tools/list`) возвращает три инструмента трекера с JSON Schema;
- `tracker_create_task` создаёт задачу, требование указать title;
- `tracker_list_tasks` возвращает задачи с фильтром по статусу: `new`, `in_progress`, `done`;
- `tracker_add_comment` добавляет комментарий к задаче по её id;
- агент по запросу «создай задачу Привезти стол» вызывает `tracker_create_task` и возвращает ответ пользователю.

### Запуск

```bash
./gradlew bootRun --args="--day=17"
# UI: http://localhost:8080/day17.html
# MCP-сервер: http://localhost:9090/mcp

./gradlew bootRun --args="--day=17 --check --cli"
./gradlew bootRun --args="--day=17 --tools --cli"
./gradlew bootRun --args="--day=17 --prompt=\"создай задачу Привезти стол\" --cli"
./gradlew bootRun --args="--day=17 --prompt=\"покажи задачи в работе\" --cli"
./gradlew bootRun --args="--day=17 --prompt=\"добавь комментарий к задаче t-xxxxx: проверил, всё ок\" --cli"
```

### Как устроен код дня 17

| Файл | Роль |
|---|---|
| `day17/Day17Properties.java` | настройки: `serverPort` (9090), `path` (`/mcp`), `name`, `version`, `storeDir` |
| `day17/Day17Ticket.java`, `day17/Day17Comment.java` | модель задачи и комментария трекера |
| `day17/Day17TicketStore.java` | файловое хранилище задач и комментариев (JSON, Jackson) |
| `day17/Day17TrackerApi.java` | интерфейс «внешнего API» трекера |
| `day17/Day17TrackerService.java` | реализация трекера: создать задачу, список с фильтром, добавить комментарий |
| `day17/Day17MockApiController.java` | mock-API трекера для ручного тестирования инструментов: `/api/day17/tracker/tasks` |
| `day17/Day17Tool.java` | инструмент MCP: имя, описание, JSON Schema, обработчик |
| `day17/Day17Connection.java`, `day17/Day17ToolInfo.java`, `day17/Day17ToolResult.java` | DTO MCP-соединения и инструментов |
| `day17/Day17McpException.java` | ошибки MCP |
| `day17/Day17McpServer.java` | MCP-сервер на JDK `HttpServer`: JSON-RPC `initialize`/`tools/list`/`tools/call`, сессии |
| `day17/Day17McpClient.java` | MCP-клиент на `java.net.http.HttpClient`: подключение, список инструментов, вызов |
| `day17/Day17AgentService.java` | агент: распознавание намерения → вызов инструмента через MCP → формулировка ответа LLM |
| `day17/Day17AgentController.java` | `/api/day17/health`, `/api/day17/tools`, `/api/day17/agent` (502 при недоступном MCP) |
| `day17/Day17CliRunner.java` | CLI: `--day=17 --check/--tools/--prompt=<текст>` |
| `static/day17.html` | UI: соединение, список инструментов, запрос к агенту с примерами |

Настройки:

```bash
DAY17_SERVER_PORT=9090
DAY17_PATH=/mcp
DAY17_NAME=ai-advent-tracker-mcp
DAY17_VERSION=0.1.0
DAY17_STORE_DIR=data/day17-tasks
```

## День 18. Планировщик и фоновые задачи

Вместе с приложением стартует MCP-сервер планировщика (порт 9091) и фоновое ядро, которое работает 24/7: задания выполняются по расписанию и переживают перезапуск (состояние хранится в JSON). Есть три вида запланированных инструментов — напоминание (отложенный запуск), периодический сбор данных и регулярная сводка:

- `scheduler_add_reminder(topic, delaySeconds)` — отложенное задание: напоминание сработает один раз через N секунд;
- `scheduler_add_collector(feed, periodSeconds, url, sourceFeed)` — периодическое задание: каждый период происходит замер и сохраняется в поток `feed`; если задан `url` — проверка доступности сайта (пинг), если задан `sourceFeed` — регулярная сводка по ранее собранным данным;
- `scheduler_list_jobs()` — список заданий со статусом, счётчиком запусков и временем следующего запуска;
- `scheduler_summary(feed, sinceSeconds)` — агрегированный результат: сколько событий, первое/последнее, среднее/мин/макс значение, доля успешных проверок, последние записи;
- `scheduler_run_now(jobId)` — мгновенный запуск задания для демонстрации без ожидания расписания;
- `scheduler_stop_process(jobId)` — остановка процесса: конкретное задание по `jobId` или все активные процессы сбора и ожидающие напоминания, если `jobId` не указан; возвращает список остановленных заданий.

Как это устроено:

- фоновый планировщик (`Day18SchedulerService`) тикает каждые 500 мс, находит задания, у которых наступило время запуска, и выполняет их; поток демон, работает с момента старта приложения;
- каждое выполнение сохраняет замер в поток данных, а задание обновляет счётчик запусков и время следующего выполнения; всё состояние сохраняется в `data/day18-scheduler/scheduler.json` и восстанавливается при перезапуске;
- агрегация (`Day18Aggregator`) считает статистику по собранным замерам — это и есть «регулярный summary»;
- агент (`Day18AgentService`) распознаёт намерение («напомни через 10 секунд выпить чай», «собирай данные каждые 3 секунды по events», «пиши сводку каждые 4 секунды по events», «дай сводку по events»), вызывает инструмент планировщика через MCP и формулирует ответ;
- CLI-режим `--live` показывает работу планировщика «вживую»: задания, счётчики и сводки обновляются каждые 2 секунды — агент «работает 24/7».

Проверки дня:

- напоминание срабатывает через заданную задержку ровно один раз и помечается `done`;
- коллектор с периодом 1–2 секунды реально копит замеры по расписанию (без обращения к внешним сервисам в тестах);
- коллектор со `sourceFeed` пишет регулярные сводки по накопленным данным;
- `scheduler_run_now` запускает задание мгновенно;
- `scheduler_stop_process` останавливает задание (один процесс или все), статус становится `stopped`, расписание замирает, а повторный `run_now` оживляет коллектор;
- `scheduler_summary` агрегирует замеры: счётчик, среднее, мин/макс, последняя запись;
- агент по запросам «напомни…», «собирай…», «сводку…», «какие задания…», «останови…» выбирает нужный инструмент и возвращает ответ.

### Запуск

```bash
./gradlew bootRun --args="--day=18"
# UI: http://localhost:8080/day18.html
# MCP-сервер: http://localhost:9091/mcp

./gradlew bootRun --args="--day=18 --check --cli"
./gradlew bootRun --args="--day=18 --tools --cli"
./gradlew bootRun --args="--day=18 --reminder=\"выпить чай\" --delay=10 --cli"
./gradlew bootRun --args="--day=18 --collect --feed=events --period=2 --cli"
./gradlew bootRun --args="--day=18 --collect --feed=digest --period=4 --source=events --cli"
./gradlew bootRun --args="--day=18 --summary --feed=events --cli"
./gradlew bootRun --args="--day=18 --stop --cli"
./gradlew bootRun --args="--day=18 --stop=<id-задания> --cli"
./gradlew bootRun --args="--day=18 --live --seconds=20 --cli"
./gradlew bootRun --args="--day=18 --prompt=\"напомни через 10 секунд выпить чай\" --cli"
./gradlew bootRun --args="--day=18 --prompt=\"дай сводку по events\" --cli"
```

### Как устроен код дня 18

| Файл | Роль |
|---|---|
| `day18/Day18Properties.java` | настройки: `serverPort` (9091), `path` (`/mcp`), `name`, `version`, `storeDir`, `tickMillis` |
| `day18/Day18Job.java`, `day18/Day18Sample.java` | модель задания планировщика и замера данных |
| `day18/Day18Store.java` | файловое хранилище заданий и замеров (JSON, Jackson) |
| `day18/Day18Summary.java`, `day18/Day18Aggregator.java` | агрегированный результат и его расчёт |
| `day18/Day18HttpProbe.java` | проверка доступности URL (пинг) с замером времени ответа |
| `day18/Day18SchedulerApi.java` | интерфейс планировщика для MCP-инструментов |
| `day18/Day18SchedulerService.java` | фоновое ядро 24/7: тик, выполнение задания, персистентность расписания |
| `day18/Day18Tool.java`, `day18/Day18Connection.java`, `day18/Day18ToolInfo.java`, `day18/Day18ToolResult.java` | DTO MCP-инструментов |
| `day18/Day18McpException.java` | ошибки MCP |
| `day18/Day18McpServer.java` | MCP-сервер на JDK `HttpServer`: JSON-RPC `initialize`/`tools/list`/`tools/call`, сессии |
| `day18/Day18McpClient.java` | MCP-клиент на `java.net.http.HttpClient`: подключение, список инструментов, вызов |
| `day18/Day18AgentService.java` | агент: распознавание намерения → вызов инструмента планировщика через MCP → ответ LLM |
| `day18/Day18Controller.java` | `/api/day18/health`, `/tools`, `/agent`, `/jobs`, `/summary`, `/samples`, `/reminder`, `/collector`, `/run`, `/stop` |
| `day18/Day18CliRunner.java` | CLI: `--day=18 --check/--tools/--jobs/--reminder/--collect/--run/--stop/--summary/--live/--prompt` |
| `static/day18.html` | UI: напоминания, периодический сбор, список заданий, остановка процессов, сводка, запрос к агенту |

Настройки:

```bash
DAY18_SERVER_PORT=9091
DAY18_PATH=/mcp
DAY18_NAME=ai-advent-scheduler-mcp
DAY18_VERSION=0.1.0
DAY18_STORE_DIR=data/day18-scheduler
DAY18_TICK_MILLIS=500
```

## День 19. Композиция MCP-инструментов

Вместе с приложением стартует MCP-сервер витрины (порт 9092) с тремя инструментами, которые можно вызывать по отдельности и складывать в автоматический пайплайн «поиск → сводка → файл»:

- `search(query, category, maxResults, sort)` — ищет товары в каталоге магазинов и возвращает JSON-список: название, категория, продавец, цена, рейтинг, ссылка на сайт и ключевые параметры;
- `summarize(query, data, format)` — принимает результат `search` (проверяет его на пустоту и корректность), строит сводную таблицу «Сравнение по запросу» в markdown или csv: колонки «Товар | Продавец | Цена, ₽ | Рейтинг | Ключевые параметры | Ссылка»;
- `saveToFile(data, summary, format, fileName)` — принимает результат `summarize` и складывает файл (`md`, `txt`, `csv` или `json`) в `data/day19-market`; возвращает имя, путь и размер.

Композиция — главная идея дня: результат одного инструмента передаётся в следующий, и каждый шаг проверяет переданные данные (пустой список, отсутствие заголовка таблицы, нулевой размер файла считаются ошибкой передачи). Полный пайплайн выполняется автоматически в `Day19AgentService.pipeline()`.

Как это устроено:

- MCP-сервер (`Day19McpServer`) сам не знает о пайплайне: он предоставляет три независимых инструмента, а их последовательное выполнение и проверка передачи данных — забота клиента (`Day19McpClient`) и оркестратора (`Day19AgentService`);
- три шага пайплайна возвращаются в ответе как `steps` с пометкой успеха и пояснением: «получено N товаров», «таблица готова к сохранению», «файл записан»;
- агент (`Day19AgentService`) распознаёт намерение: «найди ноутбуки» → только `search`, «сравни смартфоны в таблицу» → `search` + `summarize`, «сохрани телевизоры в файл csv» → полный пайплайн; запрос и формат (markdown/csv/json/txt) выделяются из текста;
- каталог — 13 мок-товаров (`Day19CatalogService`), продавцы с сайтами и параметрами, поиск по категориям «ноутбуки», «смартфоны», «телевизоры», «наушники», сортировка по цене и рейтингу;
- CLI-режим `--pipeline=<запрос>` запускает весь конвейер и печатает успех каждого шага и сохранённый файл.

Проверки дня:

- инструмент `search` возвращает корректный JSON и по запросу «ноутбук» находит 5 товаров;
- `summarize` принимает результат `search` и строит markdown/csv таблицу с заголовком «Сравнение по запросу»; на пустом или битом `data` отвечает ошибкой -32602;
- `saveToFile` сохраняет файл в нужном формате и возвращает путь и размер; указание неизвестного формата — ошибка;
- полный пайплайн выполняет три шага автоматически, каждый шаг подтверждает успех, файл реально создаётся;
- МСР-сервер без сессии отвечает 400 (-32600), неизвестный метод — -32601, агент по фразам «найди…», «сравни…», «сохрани … в файл» выбирает нужную композицию.

### Запуск

```bash
./gradlew bootRun --args="--day=19"
# UI: http://localhost:8080/day19.html
# MCP-сервер: http://localhost:9092/mcp

./gradlew bootRun --args="--day=19 --check --cli"
./gradlew bootRun --args="--day=19 --tools --cli"
./gradlew bootRun --args="--day=19 --search=ноутбук --cli"
./gradlew bootRun --args="--day=19 --summarize=ноутбук --format=csv --cli"
./gradlew bootRun --args="--day=19 --save=телевизоры --format=csv --file=телевизоры-2026 --cli"
./gradlew bootRun --args="--day=19 --pipeline=ноутбуки --cli"
./gradlew bootRun --args="--day=19 --prompt=\"сравни ноутбуки в таблицу\" --cli"
./gradlew bootRun --args="--day=19 --prompt=\"сохрани телевизоры в файл csv\" --cli"
./gradlew bootRun --args="--day=19 --files --cli"
```

### Как устроен код дня 19

| Файл | Роль |
|---|---|
| `day19/Day19Properties.java` | настройки: `serverPort` (9092), `path` (`/mcp`), `name`, `version`, `storeDir` |
| `day19/Day19Product.java` | мок-модель товара: id, название, категория, продавец, сайт, цена, рейтинг, параметры |
| `day19/Day19CatalogService.java` | каталог из 13 товаров, поиск по подстроке, категориям, лимиту и сортировке, разбор JSON-списка товаров |
| `day19/Day19TableBuilder.java` | markdown/csv-таблица «Сравнение по запросу» |
| `day19/Day19SaveService.java` | сохранение сводки в файл (`md`/`txt`/`csv`/`json`), список сохранённых файлов |
| `day19/Day19MarketApi.java`, `day19/Day19MarketService.java` | интерфейс и реализация витрины для MCP-инструментов |
| `day19/Day19Tool.java`, `day19/Day19Connection.java`, `day19/Day19ToolInfo.java`, `day19/Day19ToolResult.java` | DTO MCP-инструментов |
| `day19/Day19McpException.java` | ошибки MCP |
| `day19/Day19McpServer.java` | MCP-сервер на JDK `HttpServer`: `initialize`/`tools/list`/`tools/call`, сессии |
| `day19/Day19McpClient.java` | MCP-клиент на `java.net.http.HttpClient`: подключение, список инструментов, вызов |
| `day19/Day19AgentService.java` | агент: распознавание намерения → пайплайн search/summarize/saveToFile через MCP → ответ LLM |
| `day19/Day19Controller.java` | `/api/day19/health`, `/tools`, `/search`, `/summarize`, `/save`, `/pipeline`, `/agent`, `/files` |
| `day19/Day19CliRunner.java` | CLI: `--day=19 --check/--tools/--search/--summarize/--save/--pipeline/--prompt/--files` |
| `static/day19.html` | UI: проверка MCP, поиск, сводная таблица, кнопка полного пайплайна, список файлов, запрос к агенту |

Настройки:

```bash
DAY19_SERVER_PORT=9092
DAY19_PATH=/mcp
DAY19_NAME=ai-advent-pipeline-mcp
DAY19_VERSION=0.1.0
DAY19_STORE_DIR=data/day19-market
```

## День 20. Оркестрация MCP-серверов

Два независимых MCP-сервера дней 18 и 19 живут рядом, и их инструменты можно звать из одной точки: оркестратор (порт 9093) подключается к обоим по протоколу MCP как клиент, держит единый каталог инструментов и сам становится MCP-сервером для внешнего мира. Инструменты размножены по серверам: `scheduler` (день 18, порт 9091) держит `scheduler_*`, `market` (день 19, порт 9092) — `search`, `summarize`, `saveToFile`. Оркестратор сам решает, где запускать инструмент, как обычные приложения решают, на каком хосте выполнить запрос.

Главное — длинный флоу с инструментами разных серверов. Флоу `market-report` четыре шага: `search` → `summarize` → `saveToFile` (всё на сервере `market`), а затем `scheduler_add_reminder` уже на сервере `scheduler` — и в тему напоминания подставляется путь к файлу, созданному двумя шагами раньше. Эстафета данных работает через ссылки `{0}`, `{1}`, `{2}`: результат каждого шага становится аргументом следующего. Регистр ссылок (ключ → значение) при этом не виден извне: сервер `scheduler` получил уже готовый JSON, а не шаблон.

Как это устроено:

- единый реестр (`Day20Registry`) отображает инструмент → сервер: `scheduler_*` → `scheduler`, `search`/`summarize`/`saveToFile` → `market`;
- `Day20Orchestrator` держит ленивые MCP-клиенты к обоим серверам, маршрутизирует одиночный вызов (`orchestrator.route(tool, args)`) и выполняет цепочки шагов (`orchestrator.runFlow`); ошибки MCP любых серверов оборачиваются в `Day20McpException`;
- MCP-сервер оркестратора (`Day20McpServer`) — «коммутатор» из пяти инструментов: `orchestrator_servers`, `orchestrator_tools`, `orchestrator_route`, `orchestrator_call`, `orchestrator_run_flow`;
- агент `Day20AgentService` распознаёт три намерения: «… и сохрани в файл …» → полный флоу `market-report`, «сводку/список джобов/напомни» → сервер `scheduler`, «найди/сравни в таблицу» → сервер `market`; LLM даёт фолбэк-ответ, если запрос не похож на работу с инструментами;
- CLI-режим открывает те же возможности: `--tools`, `--route`, `--call`, `--flow`, `--servers`, `--check`.

Проверки дня:

- оркестратор подключается к обоим серверам и показывает агрегированный каталог (6 + 3 инструмента);
- `route("search")` идёт на сервер `market`, `route("scheduler_summary")` — на `scheduler`; неизвестный инструмент или неизвестный сервер в `call` — ошибка;
- флоу `market-report` выполняет 4 шага в строгом порядке, подставляет `{0}`/`{1}`/`{2}`, файл реально создаётся в `data/day19-market`, а напоминание в шаге 4 содержит путь к этому файлу;
- флоу `scheduler-brief` общается только с сервером `scheduler` (`scheduler_add_collector` → `scheduler_summary`);
- MCP-сервер оркестратора отвечает на все пять `orchestrator_*` инструментов, неизвестный метод — -32601, пустой объект `arguments` — -32602;
- REST: неизвестный инструмент → 400, недоступный сервер → 502;
- агент по фразам «… и сохрани в файл …», «сводку по событиям планировщика», «сравни смартфоны в таблицу» выбирает правильный сервер, инструмент и аргументы.

### Запуск

```bash
./gradlew bootRun --args="--day=20"
# UI: http://localhost:8080/day20.html
# MCP-сервер оркестратора: http://localhost:9093/mcp
# (рядом работают MCP-серверы дней 18 и 19)

./gradlew bootRun --args="--day=20 --check --cli"
./gradlew bootRun --args="--day=20 --servers --cli"
./gradlew bootRun --args="--day=20 --tools --cli"
./gradlew bootRun --args="--day=20 --flows --cli"
./gradlew bootRun --args="--day=20 --route=search --json={\"query\":\"ноутбук\"} --cli"
./gradlew bootRun --args="--day=20 --call=scheduler_summary --server=scheduler --cli"
./gradlew bootRun --args="--day=20 --flow=market-report --json={\"query\":\"ноутбук\",\"format\":\"markdown\",\"fileName\":\"отчёт-2026\"} --cli"
./gradlew bootRun --args="--day=20 --prompt=\"составь полный отчёт по ноутбукам и сохрани в файл csv\" --cli"
./gradlew bootRun --args="--day=20 --prompt=\"сводку по событиям планировщика\" --cli"
./gradlew bootRun --args="--day=20 --prompt=\"сравни смартфоны в таблицу\" --cli"
```

### Как устроен код дня 20

| Файл | Роль |
|---|---|
| `day20/Day20Properties.java` | настройки: `serverPort` (9093), `path` (`/mcp`), `name`, `version` |
| `day20/Day20Registry.java` | реестр: id серверов (`scheduler`, `market`) и отображение инструмент → сервер |
| `day20/Day20Orchestrator.java` | ядро: ленивые MCP-клиенты к дням 18/19, health/каталог/маршрут/вызов/флоу, строковая эстафета `{0}`/`{1}`/`{n}` между шагами |
| `day20/Day20FlowDefinition.java` | описание флоу: ключ, имя, шаги, вшитый словарь входных аргументов |
| `day20/Day20McpException.java` | ошибки оркестрации (оборачивает ошибки MCP-серверов) |
| `day20/Day20McpServer.java` | MCP-сервер-коммутатор на JDK `HttpServer`: 5 инструментов `orchestrator_*`, сессии |
| `day20/Day20AgentService.java` | агент: намерения (флоу/планировщик/витрина) → маршрут или флоу через оркестратор → ответ LLM |
| `day20/Day20Controller.java` | `/api/day20/health`, `/servers`, `/tools`, `/flows`, `/call`, `/route`, `/flow`, `/agent` |
| `day20/Day20CliRunner.java` | CLI: `--day=20 --check/--servers/--tools/--flows/--route/--call/--flow/--prompt` |
| `static/day20.html` | UI: проверка соединения, каталог инструментов, маршрутизация, запуск флоу с шагами и подстановкой, запрос к агенту |

Настройки:

```bash
DAY20_SERVER_PORT=9093
DAY20_PATH=/mcp
DAY20_NAME=ai-advent-orchestrator-mcp
DAY20_VERSION=0.1.0
```

## День 21. Индексация документов

Документы большого объёма невозможно «скормить» модели целиком — поэтому они превращаются в индекс: тексты нарезаются на чанки, каждый чанк получает вектор-эмбеддинг и метаданные, и поиск по корпусу становится поиском векторов. День 21 строит локальный RAG-пайплайн без внешних зависимостей: корпус (README проекта, пять статей, исходный код, PDF-гайд, который генерируется прямо в рантайме — суммарно ~130 тысяч символов, примерно 30+ страниц) индексируется двумя стратегиями чанкинга одновременно, и метрики показывают, какая из них точнее.

Главное — сравнение двух стратегий чанкинга на одном и том же корпусе:

- **fixed** — текст режется фиксированным окном (~600 символов) с перекрытием 80, границы выравниваются по пробелам, а секция каждого чанка узнаётся по ближайшему предыдущему заголовку;
- **structural** — нарезка уважает структуру формата: markdown делится по заголовкам и параграфам, код — по объявлениям классов/интерфейсов, простой текст — по параграфам; размер чанка удерживается паковкой блоков.

Эмбеддинги детерминированные и локальные: слова и n-граммы до трёх слов раскладываются двумя хэшами в вектор размерности 512 с нормализацией, близость считается косинусной. В проде этот генератор без изменения архитектуры заменяется на API-модель эмбеддингов.

Сравнение считает по обеим стратегиям: средний/минимальный/максимальный размер чанка, коэффициент вариации (равномерность нарезки) и покрытие шести пробных запросов — пять заранее известных вопросов обязаны попадать в топ-3, один заведомый промах (рецепт борща по свёкле) обязан не находиться. Вердикт честно сообщает, какая стратегия выиграла и почему.

Как это устроено:

- `Day21CorpusLoader` раскладывает встроенный корпус из classpath в `data/day21-corpus` (если его ещё нет), а PDF-гайд `pdf/guide.pdf` генерирует на лету `Day21PdfWriter`, откуда текст достаёт `Day21PdfText` (разбор потоков `stream`/`BT-ET`/`Tj`/`TJ`, поддержка FlateDecode);
- чанкеры `Day21FixedChunker` и `Day21StructuralChunker` возвращают чанки с метаданными: `source`, `fileName`, `title`, `section`, `chunkId` (`source#стратегия#порядковый_номер`), позиция в тексте и количество символов;
- `Day21IndexFacade` — ядро: health/ingest/search/compare, кэш индекса, встроенные пробы и формулировка вердикта;
- `Day21IndexStore` сохраняет индекс стратегии как JSON (`data/day21-index/index-<стратегия>.json`), и поиск работает после перезапуска без переиндексации;
- MCP-сервер (порт 9094) объявляет пять инструментов: `index_status`, `index_ingest`, `index_search`, `index_compare`, `index_chunks` — локальный индекс становится частью экосистемы MCP дней 16–20;
- агент `Day21AgentService` распознаёт три намерения: «проиндексируй…» → `index_ingest`, «сравни стратегии чанкинга» → `index_compare`, «найди/что такое/где говорится…» → `index_search`; по слову «структурн» переключает стратегию; LLM даёт фолбэк-ответ, если сеть недоступна.

Проверки дня:

- корпус раскладывается и считается: ~130 тысяч символов ≈ 30+ страниц, оба индекса строятся и сохраняются в JSON;
- поиск по запросу «что такое эмбеддинги» попадает в `articles/embeddings.md` с секцией и score, поиск ищет и находит чанк с метаданными;
- индекс переживает перезапуск: новая инстанция фасада читает `index-fixed.json` и отвечает без переиндексации;
- метаданные чанков на месте: источник, заголовок, секция, `chunk_id`, размер; фиксированная и структурная стратегии дают разную нарезку;
- `compare` возвращает метрики обоих стратегий и вердикт; пробы: 5 попаданий в топ-3 и 1 заведомый промах;
- PDF-гайд, сгенерированный в рантайме, читается обратно в текст без потери слов;
- MCP-сервер отвечает на все пять `index_*` инструментов, вызовы без сессии — 400/-32600, неизвестный инструмент — -32601;
- REST: непустой запрос обязателен, пустой — 400; агент по фразам «найди что такое эмбеддинги», «сравни стратегии чанкинга», «проиндексируй корпус» выбирает правильный инструмент.

### Запуск

```bash
./gradlew bootRun --args="--day=21"
# UI: http://localhost:8080/day21.html
# MCP-сервер индекса: http://localhost:9094/mcp

./gradlew bootRun --args="--day=21 --check --cli"
./gradlew bootRun --args="--day=21 --ingest --cli"
./gradlew bootRun --args="--day=21 --search=что такое эмбеддинги --cli"
./gradlew bootRun --args="--day=21 --search=память агента --strategy=structural --cli"
./gradlew bootRun --args="--day=21 --compare --cli"
./gradlew bootRun --args="--day=21 --chunks --cli"
./gradlew bootRun --args="--day=21 --prompt=\"сравни стратегии чанкинга\" --cli"
./gradlew bootRun --args="--day=21 --prompt=\"найди что такое MCP\" --cli"
./gradlew bootRun --args="--day=21 --prompt=\"проиндексируй корпус\" --cli"
```

### Как устроен код дня 21

| Файл | Роль |
|---|---|
| `day21/Day21Properties.java` | настройки: `serverPort` (9094), `path` (`/mcp`), `name`, `store-dir`, `corpus-dir`, `chunk-size`, `chunk-overlap`, `dimensions`, константы стратегий `fixed`/`structural` |
| `day21/Day21Document.java`, `Day21Chunk.java` | документ корпуса и чанк с метаданными (`source`, `title`, `section`, `chunkId`, позиция, размер) |
| `day21/Day21Chunker.java` + `Day21FixedChunker` / `Day21StructuralChunker` | две стратегии чанкинга, общий интерфейс |
| `day21/Day21Embedding.java`, `Day21EmbeddingService.java` | локальные детерминированные эмбеддинги (n-граммы + два хэша, нормализация) и косинусная близость |
| `day21/Day21IndexFile.java`, `Day21IndexEntry.java`, `Day21IndexStore.java` | JSON-хранилище индекса стратегии |
| `day21/Day21PdfWriter.java`, `Day21PdfText.java` | PDF-гайд генерируется в рантайме и извлекается обратно в текст (FlateDecode) |
| `day21/Day21CorpusLoader.java` | встроенный корпус (README, статьи, код) → `data/day21-corpus` + PDF при первом запуске |
| `day21/Day21IndexFacade.java` | ядро: health/ingest/search/compare, кэш, пробные запросы, метрики и вердикт по стратегиям |
| `day21/Day21McpServer.java` | MCP-сервер на JDK `HttpServer`: 5 инструментов `index_*`, сессии |
| `day21/Day21AgentService.java` | агент: намерения (индексация/сравнение/поиск) → инструмент фасада → ответ LLM |
| `day21/Day21Controller.java` | `/api/day21/health`, `/strategies`, `/ingest`, `/search`, `/compare`, `/chunks`, `/agent` |
| `day21/Day21CliRunner.java` | CLI: `--day=21 --check/--ingest/--search/--compare/--chunks/--prompt` |
| `static/day21.html` | UI: состояние индекса, построение, семантический поиск с метаданными, сравнение стратегий, запрос к агенту |

Настройки:

```bash
DAY21_SERVER_PORT=9094
DAY21_PATH=/mcp
DAY21_NAME=ai-advent-index-mcp
DAY21_VERSION=0.1.0
DAY21_STORE_DIR=data/day21-index
DAY21_CORPUS_DIR=data/day21-corpus
DAY21_CHUNK_SIZE=600
DAY21_CHUNK_OVERLAP=80
DAY21_DIMENSIONS=512
```

## День 22. Первый RAG-запрос

Поверх локального индекса дня 21 собирается простейший RAG-цикл: вопрос
превращается в запрос, по нему извлекаются релевантные чанки, чанки подставляются
в промпт как контекст вместе с вопросом, и LLM формулирует ответ, опираясь на
источники. Агент умеет работать в двух режимах — с RAG и без RAG — а их качество
измеряется на десяти контрольных вопросах с известными ожиданиями и источниками.

Retrieval в этом дне гибридный: dense-кандидаты (косинусная близость) из индекса
дня 21 объединяются с классическим лексическим реранкингом BM25 по всем чанкам
выбранной стратегии. Простого признакового вектора недостаточно — на длинном
README редкие слова «размазываются», и BM25 помогает поднять точность. По сути это
схема «retrieve-then-rerank», типичная для RAG-систем.

```bash
./gradlew bootRun --args="--day=22"
# UI: http://localhost:8080/day22.html
```

CLI:

```bash
./gradlew bootRun --args="--day=22 --check --cli"
./gradlew bootRun --args="--day=22 --answer=\"что такое эмбеддинги документов и как они получаются?\" --cli"
./gradlew bootRun --args="--day=22 --answer=\"что такое эмбеддинги документов и как они получаются?\" --mode=plain --cli"
./gradlew bootRun --args="--day=22 --compare=\"что такое эмбеддинги\" --cli"
./gradlew bootRun --args="--day=22 --questions --cli"
./gradlew bootRun --args="--day=22 --evaluate --cli"
./gradlew bootRun --args="--day=22 --prompt=\"какие коды ошибок JSON-RPC определены у MCP-сервера?\" --cli"
```

Проверки дня:

- ответ в режиме `rag` отдаёт вопрос, найденные чанки с источниками, ответ и флаг
  `fallback`; в режиме `plain` найденных чанков нет, модель отвечает по памяти;
- запрос «что такое эмбеддинги документов и как они получаются?» находит
  `articles/embeddings.md` в топ, «как устроено асинхронное логирование в
  QueuedLogger?» — `code/QueuedLogger.java`;
- `compare` возвращает оба ответа рядом, список найденных источников и вердикт;
- 10 контрольных вопросов (`questions`) имеют ожидаемые ключевые слова и источники;
- `evaluate` на 10 вопросах считает recall (попадание ожидаемого источника в
  выдачу), среднее покрытие ключевых слов ответа с RAG и без RAG и разницу;
  в офлайн-демо без LLM модель честно возвращает фрагменты контекста — и они
  содержат нужные термины чаще, чем «ответ по памяти»;
- REST: пустой вопрос — 400, проблема индекса — 500; агент в `ask` сам выбирает
  режим (маркеры «без контекста», «по своей памяти» и т.п. — режим plain).

### Как устроен код дня 22

| Файл | Роль |
|---|---|
| `day22/Day22Properties.java` | настройки: `strategy`, `top-k` (5), `answer-max-tokens` (400) |
| `day22/Day22ControlQuestion.java`, `Day22ControlQuestions.java` | 10 контрольных вопросов: вопрос, ожидаемые ключевые слова и источники |
| `day22/Day22ReRanker.java` | гибридный retrieval: токенизация со стоп-словами, BM25 по всем чанкам + dense-кандидаты из индекса, взвешенная нормализация |
| `day22/Day22RagService.java` | ядро RAG: `answer` (режимы rag/plain), `ask` (авторежим), `compare`, `evaluate`, `questions`, `health`, промпты и фолбэки без LLM, метрика покрытия |
| `day22/Day22Controller.java` | `/api/day22/health`, `/questions`, `/answer`, `/ask`, `/compare`, `/evaluate` |
| `day22/Day22CliRunner.java` | CLI: `--day=22 --check/--answer/--compare/--questions/--evaluate/--prompt` |
| `static/day22.html` | UI: состояние RAG, ответ в двух режимах, сравнение, контрольные вопросы, оценка качества, агент |

Настройки:

```bash
DAY22_STRATEGY=fixed
DAY22_TOP_K=5
DAY22_ANSWER_MAX_TOKENS=400
```

## День 23. Реранкинг и фильтрация

Второй этап после поиска — фильтр релевантности. Пайплайн ответа в этом дне: запрос
(опционально переписывается) → retrieval из индекса дня 21 (dense-кандидаты по
косинусной близости + лексический реранкинг BM25) → отсечение нерелевантных чанков
по порогу score → топ-K после фильтрации → контекст с вопросом к LLM.

Query rewrite — эвристический и детерминированный: вопрос расширяется синонимами и
доменными терминами корпуса (эмбеддинг → вектор/признак/n-грамм, MCP →
протокол/json-rpc, ошибк → коды -32600/-32601/32000 и т.д.). Расширения добавляют
лексические сигналы, по которым BM25 точнее находит нужные чанки.

Качество сравнивается на тех же 10 контрольных вопросах в двух режимах: `base`
(без rewrite и фильтра — как день 22) и `full` (rewrite + фильтр порога).
В офлайн-демо порог по умолчанию 0.5 от лучшего кандидата: на корпусе recall не
падает (10/10 в обоих режимах), фильтр отсекает ~75 «шумных» кандидатов, а среднее
покрытие ключевых слов ответа растёт с ~87% до 100% (+13 п.п.).

```bash
./gradlew bootRun --args="--day=23"
# UI: http://localhost:8080/day23.html
```

CLI:

```bash
./gradlew bootRun --args="--day=23 --check --cli"
./gradlew bootRun --args="--day=23 --rewrite=\"что такое эмбеддинги документов\" --cli"
./gradlew bootRun --args="--day=23 --answer=\"что такое эмбеддинги документов и как они получаются?\" --mode=full --cli"
./gradlew bootRun --args="--day=23 --answer=\"что такое эмбеддинги документов и как они получаются?\" --mode=base --cli"
./gradlew bootRun --args="--day=23 --compare=\"что такое эмбеддинги\" --cli"
./gradlew bootRun --args="--day=23 --questions --cli"
./gradlew bootRun --args="--day=23 --evaluate --cli"
./gradlew bootRun --args="--day=23 --prompt=\"перепиши и найди: как устроена память агента?\" --cli"
```

Проверки дня:

- режимы пайплайна: `base` (без rewrite и фильтра), `filter` (только фильтр по
  порогу), `rewrite` (только переписывание), `full` (rewrite + фильтр, по умолчанию);
- `/api/day23/rewrite` и `--rewrite` показывают исходный и переписанный запрос и
  применённые расширения;
- ответ отдаёт запрос для поиска, число кандидатов до фильтрации, сколько отсеяно
  по порогу, найденные чанки и ответ; при проблемах индекса — 500, пустой вопрос — 400;
- `compare` сравнивает `base` и `full` для одного вопроса: кандидаты до/после,
  отсеянные, источники, вердикт;
- `evaluate` считает recall источника в режимах `base` и `full`, суммарное число
  отсеянных кандидатов, среднее покрытие ключевых слов и прирост coverage;
  на корпусе recall 10/10 в обоих режимах, фильтр отсекает ~75 кандидатов, покрытие
  полного пайплайна выше (100% против ~87%);
- агент в `ask` сам выбирает пайплайн: «без фильтра»/«как есть» → `base`,
  «перепиши» → `rewrite`, «только фильтр» → `filter`, иначе `full`.

### Как устроен код дня 23

| Файл | Роль |
|---|---|
| `day23/Day23Properties.java` | настройки: `strategy`, `top-k-before` (10), `top-k-after` (5), `threshold` (0.5), `rewrite`, `answer-max-tokens` (400) |
| `day23/Day23ControlQuestion.java`, `Day23ControlQuestions.java` | 10 контрольных вопросов с ожиданиями и источниками (те же, что в дне 22) |
| `day23/Day23QueryRewriter.java` | эвристический rewrite: нормализация + синонимические правила, применённые расширения |
| `day23/Day23ReRanker.java` | гибридный реранкер: BM25 по всем чанкам + dense-кандидаты из индекса, нормированный score 0..1 |
| `day23/Day23Pipeline.java` | модель пайплайна: запрос для поиска, флаг rewrite, кандидаты до фильтрации, отсеянные, итоговые чанки |
| `day23/Day23RagService.java` | ядро: `answer` (base/filter/rewrite/full), `rewrite`, `ask`, `compare`, `evaluate`, `questions`, `health`, промпты и фолбэки без LLM |
| `day23/Day23Controller.java` | `/api/day23/health`, `/questions`, `/rewrite`, `/answer`, `/ask`, `/compare`, `/evaluate` |
| `day23/Day23CliRunner.java` | CLI: `--day=23 --check/--rewrite/--answer/--compare/--questions/--evaluate/--prompt` |
| `static/day23.html` | UI: состояние пайплайна, переписывание запроса, ответ в 4 режимах, сравнение base/full, оценка качества, агент |

Настройки:

```bash
DAY23_STRATEGY=fixed
DAY23_TOP_K_BEFORE=10
DAY23_TOP_K_AFTER=5
DAY23_THRESHOLD=0.5
DAY23_REWRITE=true
DAY23_ANSWER_MAX_TOKENS=400
```

## День 24. Цитаты, источники и анти-галлюцинации

Финальное звено RAG: ответ больше не отдаётся «просто текстом». Каждый ответ
сопровождается списком источников (source + секция + chunk_id) и дословными цитатами —
фрагментами из реально найденных чанков. По цитатам же проверяется, что смысл ответа
опирается на контекст. Если релевантности нет, ассистент обязан сказать «не знаю»
и попросить уточнение вместо того, чтобы выдумать правдоподобную выдумку.

Пайплайн дня: запрос → поиск (день 21) → реранк dense+BM25 и фильтр по порогу
(день 23) → извлечение цитат из найденных чанков → сборка ответа только по этим
цитатам → проверка поддержки смысла и режим «не знаю».

Как устроена анти-галлюцинационная логика:

- цитаты извлекаются движком `Day24QuoteEngine` из текста найденных чанков
  (разбиение на предложения, приоритет по совпадению терминов вопроса,
  ограничение `quotes-per-source` и минимальная длина цитаты);
- LLM отвечает только за формулировку — все фактические опоры обязаны прийти из цитат;
  если LLM недоступен, ответ строится дословно из самих цитат (fallback);
- `supportCoverage` считает долю опорных слов ответа, найденных в цитатах;
  поддержка выше `support-threshold` означает, что смысл ответа поддержан источниками;
- режим «не знаю» включается, если выдача пуста, лучший score ниже
  `unknown-threshold`, либо цитаты не находят лексической опоры в чанках — тогда
  ассистент отвечает фиксированной фразой «не знаю» с просьбой уточнить вопрос
  и не вызывает LLM вовсе.

На тех же 10 контрольных вопросах проверяется комплаентность: у каждого ответа есть
источники, есть цитаты, и смысл покрыт цитатами; плюс 2 слабых вопроса вне домена
корпуса, где ассистент обязан уйти в режим «не знаю».

```bash
./gradlew bootRun --args="--day=24"
# UI: http://localhost:8080/day24.html
```

CLI:

```bash
./gradlew bootRun --args="--day=24 --check --cli"
./gradlew bootRun --args="--day=24 --answer=\"что такое эмбеддинги документов и как они получаются?\" --cli"
./gradlew bootRun --args="--day=24 --answer=\"Какая сегодня погода в Москве?\" --cli"
./gradlew bootRun --args="--day=24 --questions --cli"
./gradlew bootRun --args="--day=24 --unknown --cli"
./gradlew bootRun --args="--day=24 --evaluate --cli"
```

Проверки дня:

- ответ всегда содержит источники (source + секция + chunk_id) и цитаты — дословные
  фрагменты из найденных чанков;
- у каждого ответа и каждой цитаты измеряется поддержка смысла (покрытие опорных
  слов): смысл ответа опирается на цитаты;
- на слабом вопросе (нет релевантной выдачи или нет лексической опоры) ассистент
  отвечает «не знаю» и просит уточнить, LLM не вызывается, цитат и источников нет;
- `evaluate` прогоняет 10 контрольных + 2 слабых и считает комплаентность:
  10/10 с источниками и цитатами, поддержка смысла высокая, «не знаю» 2/2;
- при проблемах индекса — 500, пустой вопрос — 400.

### Как устроен код дня 24

| Файл | Роль |
|---|---|
| `day24/Day24Properties.java` | настройки: `strategy`, `top-k-before` (10), `top-k-after` (5), `threshold` (0.5), `unknown-threshold` (0.32), `rewrite`, `quotes-per-source` (3), `quote-min-chars` (24), `support-threshold` (0.35), `answer-max-tokens` (400) |
| `day24/Day24ControlQuestion.java`, `Day24ControlQuestions.java` | 10 контрольных вопросов с ожиданиями и источниками (те же, что в дне 23) |
| `day24/Day24WeakQuestion.java` | слабые вопросы вне домена корпуса для проверки режима «не знаю» |
| `day24/Day24Source.java` | источник: source, section, chunk_id, score, сниппет |
| `day24/Day24Quote.java` | цитата: текст фрагмента, source, section, chunk_id, score, число совпавших терминов |
| `day24/Day24QuoteEngine.java` | извлечение цитат из чанков, стоп-слова, лексическая опора, `supportCoverage` |
| `day24/Day24GroundedResponse.java` | ответ: вопрос, запрос для поиска, кандидаты/отсеяно, источники, цитаты, ответ, bestScore, поддержка, флаги supported/unknown/fallback |
| `day24/Day24Service.java` | ядро: `answer`, `evaluate`, `questions`, `health`, сборка промпта по цитатам, режим «не знаю» и fallback без LLM |
| `day24/Day24Controller.java` | `/api/day24/health`, `/questions`, `/answer`, `/evaluate` |
| `day24/Day24EvalResponse.java`, `Day24EvalItem.java`, `Day24EvalWeakItem.java` | отчёт комплаентности по контрольным и слабым вопросам |
| `day24/Day24CliRunner.java` | CLI: `--day=24 --check/--answer/--questions/--unknown/--evaluate` |
| `static/day24.html` | UI: состояние, ответ с источниками и цитатами + шкала поддержки, слабые вопросы, контрольные, комплаентность |

Настройки:

```bash
DAY24_STRATEGY=fixed
DAY24_TOP_K_BEFORE=10
DAY24_TOP_K_AFTER=5
DAY24_THRESHOLD=0.5
DAY24_UNKNOWN_THRESHOLD=0.32
DAY24_REWRITE=true
DAY24_QUOTES_PER_SOURCE=3
DAY24_QUOTE_MIN_CHARS=24
DAY24_SUPPORT_THRESHOLD=0.35
DAY24_ANSWER_MAX_TOKENS=400
```

## День 25. Мини-чат с RAG и памятью задачи

Финальный день: RAG становится не разовым поиском, а диалогом. Ассистент помнит
историю переписки, держит цель разговора, уточнения, зафиксированные ограничения
и ключевые термины — и подмешивает их в поисковый запрос. Поэтому короткое
уточнение («а как их считают?») работает так же, как развёрнутый вопрос, а контекст
не теряется после 10–15 сообщений подряд.

Пайплайн дня: сообщение + память задачи → переписывание запроса и гибридный реранк
(день 23) → цитаты и источники (день 24) → ответ с опорой на найденное (день 24) →
обновление памяти задачи и истории сессии.

Что именно хранит память задачи:

- `goal` — цель разговора, меняется только по явному маркеру («моя цель», «изучи»,
  «расскажи про…»), поэтому случайное уточнение её не перетирает;
- `clarifications` — короткие уточняющие реплики, добавленные к текущей теме;
- `constraints` — ограничения пользователя («только по документации», «без выдумок»);
- `terms` — ключевые термины темы, которые подставляются в поисковый запрос.

Память и история живут в JSON-хранилище (`data/day25-chat/sessions.json`), переживают
перезапуск и ограничены по числу сессий и длине истории. Если по теме нет релевантной
выдачи, поведение остаётся честным: ассистент уходит в режим «не знаю» из дня 24.

Проверяется не «один удачный вопрос», а два длинных диалога по 12–13 сообщений:
минимум-вопрос, серия уточнений, возврат к исходной теме и смена подтемы. В конце
каждого хода фиксируется, что ответ вышел с источниками и что цель диалога не
потерялась, а ограничения остались в памяти.

```bash
./gradlew bootRun --args="--day=25"
# UI: http://localhost:8080/day25.html
```

CLI:

```bash
./gradlew bootRun --args="--day=25 --check --cli"
./gradlew bootRun --args="--day=25 --say=\"Моя цель: разобраться, как устроены эмбеддинги документов.\" --cli"
./gradlew bootRun --args="--day=25 --chat --session=demo --cli"
./gradlew bootRun --args="--day=25 --history --session=demo --cli"
./gradlew bootRun --args="--day=25 --scenarios --cli"
./gradlew bootRun --args="--day=25 --demo --cli"
./gradlew bootRun --args="--day=25 --evaluate --cli"
./gradlew bootRun --args="--day=25 --say=\"Какая сегодня погода в Москве?\" --cli"
```

Проверки дня:

- на каждый ход диалога ответ сопровождается источниками и цитатами (как в дне 24);
- короткое уточнение без общего контекста работает за счёт памяти задачи;
- цель диалога не теряется на протяжении всего сценария, ограничения фиксируются
  со второго сообщения и держатся до конца;
- `evaluate` прогоняет 2 длинных сценария (25 сообщений) и считает: ответы
  с источниками, ответы с цитатами, удержание цели и ограничений, ходы «не знаю»;
- история и память сессии сохраняются и подхватываются после перезапуска;
- вопрос вне домена корпуса — режим «не знаю» без источников, пустое сообщение — 400.

### Как устроен код дня 25

| Файл | Роль |
|---|---|
| `day25/Day25Properties.java` | настройки дня 24 + `history-limit` (40), `max-sessions` (200), `memory-terms-limit` (8), `store-dir` (`data/day25-chat`) |
| `day25/Day25Message.java` | сообщение истории: роль, текст, номер хода |
| `day25/Day25TaskMemory.java` | память задачи: `goal`, `turn`, `userTurns`, `clarifications`, `constraints`, `terms` |
| `day25/Day25MemoryExtractor.java` | извлечение цели, уточнений, ограничений и терминов + проверки `retainsGoal` / `keepsConstraints` |
| `day25/Day25ChatSessionStore.java` | JSON-хранилище сессий: история, память, лимиты, вытеснение старых сессий |
| `day25/Day25ChatTurn.java` | ход диалога: ответ, запрос для поиска, `rewritten`, источники, цитаты, bestScore, поддержка, флаги `supported`/`unknown`/`fallback`, память и история |
| `day25/Day25ChatService.java` | ядро: `chat`, `history`, `reset`, `evaluate`, `health`, подмешивание памяти в запрос, режим «не знаю» и fallback без LLM |
| `day25/Day25Scenario.java`, `Day25Scenarios.java` | 2 длинных сценария (12 и 13 сообщений) с ожиданиями по цели, ограничениям и источникам |
| `day25/Day25ScenarioTurn.java`, `Day25ScenarioResult.java`, `Day25EvalResponse.java` | отчёт прогона: ходы, источники, цитаты, удержание цели и ограничений, вердикт |
| `day25/Day25ChatRequest.java`, `Day25SessionRequest.java`, `Day25SessionView.java` | вход/выход API и сводка по сессии |
| `day25/Day25HealthResponse.java` | состояние дня: корпус, стратегия, пороги, лимиты истории/памяти, список сценариев |
| `day25/Day25Controller.java` | `/api/day25/health`, `/scenarios`, `/sessions`, `/history`, `/chat`, `/reset`, `/evaluate` |
| `day25/Day25CliRunner.java` | CLI: `--day=25 --check/--say/--chat/--history/--scenarios/--demo/--evaluate/--session` |
| `day25/Day25ChatConfiguration.java` | сборка бинов дня поверх индекса дня 21 |
| `static/day25.html` | UI: чат с историей и источниками, панель памяти задачи, health, сценарии и прогон |

Попутно исправлен баг рерайзера дня 23: запрос с повторяющимися словами ронял
`Set.of` на дубликатах — теперь используется `LinkedHashSet` поверх списка.

Настройки:

```bash
DAY25_STRATEGY=fixed
DAY25_TOP_K_BEFORE=10
DAY25_TOP_K_AFTER=5
DAY25_THRESHOLD=0.5
DAY25_UNKNOWN_THRESHOLD=0.32
DAY25_REWRITE=true
DAY25_QUOTES_PER_SOURCE=3
DAY25_QUOTE_MIN_CHARS=24
DAY25_SUPPORT_THRESHOLD=0.35
DAY25_ANSWER_MAX_TOKENS=400
DAY25_HISTORY_LIMIT=40
DAY25_MAX_SESSIONS=200
DAY25_MEMORY_TERMS_LIMIT=8
DAY25_STORE_DIR=data/day25-chat
```

## День 26. Запуск локальной LLM

День уводит проект от внешнего API к локальной модели. Ollama ставится на Windows
одной командой `winget install --id Ollama.Ollama --silent` (версия 0.35.1, бинарник
`C:\Users\User\AppData\Local\Programs\Ollama\ollama.exe`), автозапуск стоит через
ярлык в Startup, HTTP API слушает `http://localhost:11434`. Модель
`ollama pull qwen2.5:3b` весит 1.9 GB, веса в квантовании Q4 и целиком работают
на CPU — GPU не нужен.

Код дня ходит в нативный API Ollama напрямую, без OpenAI-совместимой обёртки:
`GET /api/version`, `GET /api/tags`, `POST /api/chat` с `stream=false`. Клиент держит
собственные таймауты и разбирает `eval_count`, `prompt_eval_count`, `eval_duration`
в токены и ток/с. Тонкость, которая ломает всё с кириллицей: тела запросов и ответы
нужно кодировать в UTF-8 явно, байтами, иначе русский текст превращается в кракозябры.

Суть проверяется тремя запросами разной сложности — от «кто ты такой» до кода
на Java с объяснением сложности. Замеренные показатели на этой машине (CPU, без GPU):
холодный старт с загрузкой весов — 3.1 с (45 входных + 11 выходных токенов),
12.6 с (56 + 136) и 15.2 с (71 + 164); прогретая модель через CLI `--run` —
1.5 с (41 + 13), 4.3 с (55 + 42) и 5.0 с (89 + 50), итого 10.7 с на три запроса.

Проверяется:

- `curl http://localhost:11434/api/version` → `{"version":"0.35.1"}`;
- `curl http://localhost:11434/api/tags` → список установленных моделей с размером;
- `curl http://localhost:11434/api/generate` или `/api/chat` с телом в UTF-8 → ответ модели;
- `GET /api/day26/health` возвращает endpoint, версию сервера, установлена ли модель
  и список моделей;
- `POST /api/day26/run` прогоняет три запроса и собирает статус, ответ, задержку,
  токены и ток/с; пустой `POST /api/day26/ask` → 400, недоступный LLM → 502.

Установка и проверка API:

```bash
winget install --id Ollama.Ollama --silent
ollama pull qwen2.5:3b
curl http://localhost:11434/api/version
curl http://localhost:11434/api/tags
```

Запуск:

```bash
./gradlew bootRun --args="--day=26"
# UI: http://localhost:8080/day26.html
```

CLI:

```bash
./gradlew bootRun --args="--day=26 --check --cli"
./gradlew bootRun --args="--day=26 --run --cli"
./gradlew bootRun --args="--day=26 --tasks --cli"
./gradlew bootRun --args="--day=26 --ask=\"Сколько будет 17*23?\" --cli"
```

Проверки дня:

- `--check` показывает endpoint, версию сервера и установленную модель, а при
  остановленной службе честно печатает «сервер запущен: НЕТ», а не падает;
- `--run` выполняет три задачи: `simple` «Простой запрос», `medium` «Объяснение
  двух мыслей», `complex` «Код и рассуждение» — с ответом, задержкой, токенами
  и ток/с по каждой;
- вердикт отчёта — «все 3 запросов выполнены: локальная LLM запущена и отвечает»;
- `--tasks` печатает три запроса разной сложности без вызова модели;
- `--ask` задаёт произвольный вопрос; пустой текст отклоняется до вызова модели
  (в HTTP API — с кодом 400, в CLI — сообщением об ошибке);
- `--cli` завершает процесс сразу после вывода результата.

### Что показать на видео

1. **Установка**  
   `winget install --id Ollama.Ollama --silent` → `ollama pull qwen2.5:3b` (1.9 GB).

2. **`--check`**  
   Endpoint, версия `0.35.1`, установленная модель и её размер.

3. **`--run`**  
   Три запроса: ответы, задержки по задаче (прогретая модель ~1.5 / 4.3 / 5.0 с),
   токены и ток/с, итоговый вердикт «все 3 запросов выполнены».

4. **UI**  
   http://localhost:8080/day26.html — та же команда и те же замеры кнопкой в браузере.

5. **Остановка службы**  
   Гасим службу Ollama → тот же `--check` показывает «сервер запущен: НЕТ».

### Как устроен код дня 26

| Файл | Роль |
|---|---|
| `day26/Day26Properties.java` | `endpoint` (`http://localhost:11434`), `model` (`qwen2.5:3b`), `connect-timeout-ms` (3000), `read-timeout-ms` (180000), `temperature` (0.2), `max-tokens` (300) |
| `day26/Day26LocalLlmClient.java` | HTTP-клиент нативного API Ollama: `GET /api/version`, `GET /api/tags`, `POST /api/chat` (`stream=false`), свои таймауты, разбор `eval_count`/`prompt_eval_count`/`eval_duration` в токены и ток/с, кодирование в UTF-8 |
| `day26/Day26Service.java` | `health()`, `tasks()`, `run()`, `ask(prompt)` |
| `day26/Day26Task.java` | три запроса разной сложности: `simple`, `medium`, `complex` |
| `day26/Day26TaskResult.java`, `Day26RunReport.java` | результаты: статус, ответ, задержка, токены, ток/с, вердикт прогона |
| `day26/Day26HealthResponse.java`, `Day26InstalledModel.java` | endpoint, версия сервера, установлена ли модель, список моделей с размером |
| `day26/Day26Answer.java`, `Day26AskRequest.java`, `Day26LlmException.java` | вход/выход `ask` и ошибка недоступного LLM |
| `day26/Day26Controller.java` | `GET /api/day26/health`, `GET /api/day26/tasks`, `POST /api/day26/run`, `POST /api/day26/ask`; пустой запрос → 400, недоступный LLM → 502 |
| `day26/Day26CliRunner.java` | CLI: `--day=26 --check`, `--tasks`, `--run`, `--ask="..."`, опция `--cli` завершает процесс |
| `day26/Day26Configuration.java` | `@EnableConfigurationProperties` |
| `static/day26.html` | UI: health, список задач, прогон и произвольный вопрос |

Клиент и сервис тестируются на поднятом на эфемерном порту mock-HTTP-сервере
(`com.sun.net.httpserver`): версия, список моделей, чат, UTF-8, HTTP-ошибка
и недоступный endpoint. Отдельный тест `Day26LocalLlmRealTest` реально ходит
в Ollama, но через `Assumptions.assumeTrue` пропускается, если сервер не поднят —
поэтому CI и полный offline-прогон не зависят от наличия Ollama.

Настройки:

```bash
DAY26_ENDPOINT=http://localhost:11434
DAY26_MODEL=qwen2.5:3b
DAY26_CONNECT_TIMEOUT_MS=3000
DAY26_READ_TIMEOUT_MS=180000
DAY26_TEMPERATURE=0.2
DAY26_MAX_TOKENS=300
```

## День 27. Локальный ассистент в приложении

День заключает задачу двух дней: локальная модель становится частью реального
приложения, а не стенда для проверки. «Локальный ассистент» — веб-чат на
http://localhost:8080/day27.html и его CLI-режим: приложение отправляет запросы
в Ollama на `localhost:11434`, получает ответы, показывает их с метриками и
работает **без облачных моделей** — ни одного обращения к OpenRouter, API-ключ
не нужен, `usesCloud=false` зашит в health-ответ и проверяется тестом.

Приложение — настоящий мультитоновый диалог, а не одиночные запросы: сессии
(`web` в UI, `cli` в CLI) хранят историю на диске в
`data/day27-assistant/sessions.json` и переживают перезапуск процесса. Перед
каждым ответом в модель уходит вся история сообщениями с ролями
`system`/`user`/`assistant` — не склеенным текстом, поэтому модель помнит
предыдущие ходы. Это стало возможным потому, что HTTP-клиент дня 26 расширен
методом `chat(List<Day26ChatMessage>)`: endpoint, модель, таймауты и разбор
токенов переиспользуются как есть, новый код дня добавляет только приложение
поверх.

Замеренные показатели на этой машине (CPU, без GPU): первый ход «Меня зовут
Аня» — 9.1 с (130 входных + 25 выходных токенов, 10.0 ток/с); второй ход
«Как меня зовут?» — в запрос уже уходят 177 входных токенов с историей, и
модель отвечает «Аня». Третий POST с пустым сообщением → 400, остановленный
Ollama → 502 с честной причиной, UI при этом продолжает открываться.

Проверяется:

- `GET /api/day27/health` → endpoint, версия сервера, установлена ли модель,
  `usesCloud: false`, лимит истории и число сессий;
- `POST /api/day27/chat` → ход диалога с ответом и метриками (задержка,
  токены, ток/с); пустое сообщение → 400, недоступный LLM → 502;
- `GET /api/day27/history?sessionId=web` → все сообщения сессии с ролями,
  номерами ходов и метриками ответов; `GET /api/day27/sessions` → список сессий;
- `POST /api/day27/reset` → очищает сессию;
- приложение стартует и отвечает при полностью отсутствующем `LLM_API_KEY`.

Запуск:

```bash
./gradlew bootRun --args="--day=27"
# UI: http://localhost:8080/day27.html
```

CLI:

```bash
./gradlew bootRun --args="--day=27 --check --cli"
./gradlew bootRun --args="--day=27 --ask=\"Привет! Кто ты?\" --cli"
./gradlew bootRun --args="--day=27 --history --cli"
./gradlew bootRun --args="--day=27 --reset --cli"
```

Проверки дня:

- `--check` показывает endpoint, версию сервера, установленную модель и честно
  пишет «облачные модели: НЕТ (только локальный Ollama)»; при остановленном
  сервере — «сервер запущен: НЕТ» и причину, код выхода 0;
- `--ask` отправляет сообщение в сессию `cli` и печатает ход, ответ, задержку,
  токены и ток/с; повторные `--ask` продолжают тот же диалог — история растёт;
- `--history` выводит все сообщения сессии с метриками, `--reset` очищает её;
- `--cli` завершает процесс сразу после вывода результата.

### Что показать на видео

1. **Старт без ключей**  
   `./gradlew bootRun --args="--day=27"` → http://localhost:8080/day27.html,
   health: «сервер запущен: ДА», «облачные модели: НЕТ».

2. **Диалог с памятью**  
   «Меня зовут Аня. Запомни это имя», затем «Как меня зовут?» — модель
   отвечает «Аня», потому что вся история уходит в локальную LLM.

3. **Метрики**  
   У каждого ответа: задержка (первый ход ~9 с), токены вход/выход и ток/с;
   в health — endpoint и версия `0.35.1`.

4. **Персистентность**  
   «Новый диалог» очищает сессию; перезагрузка страницы восстанавливает
   историю с диска (`GET /api/day27/history`).

5. **Остановка Ollama**  
   Гасим `ollama serve` → отправляем сообщение → 502 «Локальный LLM
   недоступен» в UI и в CLI `--check` — «сервер запущен: НЕТ».

### Как устроен код дня 27

| Файл | Роль |
|---|---|
| `day27/Day27Properties.java` | `system-prompt` (персона ассистента), `history-limit` (40), `max-sessions` (50), `store-dir` (`data/day27-assistant`) |
| `day27/Day27ChatSessionStore.java` | файловое хранилище сессий `sessions.json` с LRU-вытеснением, устойчивое к битому файлу |
| `day27/Day27ChatService.java` | `health()`, `chat(sessionId, message)`, `history()`, `sessions()`, `reset()`; собирает payload `system`+история+новое сообщение и режет историю по лимиту парами ходов |
| `day27/Day27Message.java`, `Day27ChatTurn.java`, `Day27SessionView.java`, `Day27HealthResponse.java` | сообщения с метриками, ответ хода, представление сессии, health с `usesCloud=false` |
| `day27/Day27ChatRequest.java`, `Day27SessionRequest.java`, `Day27Controller.java` | `GET /api/day27/health`, `GET /sessions`, `GET /history`, `POST /chat`, `POST /reset`; пустое сообщение → 400, недоступный LLM → 502 |
| `day27/Day27CliRunner.java` | CLI: `--day=27 --check`, `--ask="..."`, `--history`, `--reset`, опция `--cli` |
| `day27/Day27Configuration.java` | `@EnableConfigurationProperties` и бин хранилища |
| `day26/Day26ChatMessage.java`, `Day26LocalLlmClient.java` | расширение клиента дня 26: `chat(List<Day26ChatMessage>)` — многомессаджный запрос к `/api/chat` |
| `static/day27.html` | UI: чат-лента с пузырями и метриками, health, сессии, «Новый диалог» |

Тесты: сервис гоняется на поднятом mock-Ollama и проверяет, что **тело второго
запроса содержит всю историю** (`system`, `user`, `assistant`, `user`) и что
ответы сохраняются в сессию с метриками; `Day27CloudIndependenceTest`
рефлексией доказывает, что ни один класс дня 27 не ссылается на облачный
пакет `com.yunovan.aiadvent.llm`; отдельный `Day27LocalLlmRealTest` через
`Assumptions.assumeTrue` проверяет на живой Ollama, что модель помнит имя с
предыдущего хода, и пропускается без сервера.

Настройки:

```bash
DAY27_SYSTEM_PROMPT=
DAY27_HISTORY_LIMIT=40
DAY27_MAX_SESSIONS=50
DAY27_STORE_DIR=data/day27-assistant
```

## День 28. Локальная LLM + RAG

День собирает всё, что было построено за неделю 6, в законченную
**полностью локальную RAG-систему**: поиск идёт по индексу дня 21 (n-gram
эмбеддинги + cosine + BM25-переранжирование из дня 23, grounding-оценка из
дня 24) без единого сетевого вызова, а ответ генерирует локальная модель
qwen2.5:3b через клиент дня 26. Облачная модель нужна только для сравнения —
`--compare` и `--evaluate` при наличии `LLM_API_KEY` прогоняют один и тот же
локальный контекст через OpenRouter (gpt-4o-mini), в `--ask` облако не
участвует вообще: `usesCloud=false`.

Пайплайн запроса: `Day23QueryRewriter` расширяет вопрос (синонимы
«эмбеддинг → вектор, признак, n-грамм», маппинги `mcp → json-rpc`, `rag →
пайплайн, поиск, контекст` и т. д.), дальше `Day21IndexFacade.search` берёт
top-k 10 кандидатов, `Day23ReRanker` пересчитывает их (0.45 × cosine + 0.55 ×
BM25), порог 0.5 отсекает слабые совпадения, остаются top-5. Из них
собирается промпт с разметкой `[Источник: …]` и `[Источник: …, раздел: …]`,
модель отвечает, а `Day24QuoteEngine` считает опору ответа на контекст
(процент слов ответа, покрытых цитатами из найденных чанков). Если Ollama
остановлена, запрос не падает: сервис возвращает **фолбэк-ответ из индекса**
(цитаты с источниками и разделами) с `fallback=true` и честной причиной —
HTTP остаётся 200, а не 502.

Замеренные показатели на этой машине (CPU, без GPU): `--ask` «Что такое
эмбеддинги документов и как они получаются?» — 26–36 с, 502 входных + 257
выходных токенов, 8.5–9.8 ток/с, опора на контекст 57%. Сравнение с облаком
на том же вопросе: локальная 27 752 мс (8.9 ток/с, опора 53.7%) против
облачной 4 347 мс (49.2 ток/с, опора 37.2%) — вердикт «Быстрее — облачная
модель (разница 23 405 мс)». Полный `--evaluate` (10 контрольных вопросов ×
2 повтора × 2 движка) занял 524 с: ретривинг нашёл ожидаемый источник в
10 из 10 вопросов (100%), локальная модель ответила на все 20 запросов с
покрытием ключевых слов 76.7% и отклонением ±0.0 п.п. (ответы стабильны),
облачная — 78.3% ±5.0 п.п.; разница качества −1.7 п.п., скорость — 23.1 с
против 3.0 с на ответ.

Проверяется:

- `GET /api/day28/health` → индекс (документы, символы, страницы), стратегия,
  top-k 10→5, порог, rewrite, число повторов, режим поиска, endpoint и версия
  локального сервера, `modelInstalled`, настроен ли облако-ключ и
  `usesCloud: false`;
- `GET /api/day28/questions` → 10 контрольных вопросов с ключевыми словами и
  ожидаемыми источниками;
- `POST /api/day28/ask` → локальный ответ с метриками (задержка, токены,
  ток/с, опора на контекст), источниками и составом поиска (до/после фильтра);
  пустой вопрос → 400; при остановленной Ollama → фолбэк из индекса с
  `fallback=true`;
- `POST /api/day28/compare` → локальная и облачная сторона на общем контексте
  + вердикт (качество, опора, скорость); без ключа или при ошибке API
  облачная сторона помечена недоступной с причиной, локальная сохраняется;
- `POST /api/day28/evaluate` → агрегаты по обоим движкам (ответы, покрытие,
  отклонение, задержка, ток/с), три вердикта и построчная таблица по 10
  вопросам.

Запуск:

```bash
./gradlew bootRun --args="--day=28"
# UI: http://localhost:8080/day28.html
```

CLI:

```bash
./gradlew bootRun --args="--day=28 --check --cli"
./gradlew bootRun --args="--day=28 --ask=\"Что такое эмбеддинги?\" --cli"
./gradlew bootRun --args="--day=28 --compare=\"Что такое эмбеддинги?\" --cli"
./gradlew bootRun --args="--day=28 --evaluate --cli"
```

Проверки дня:

- `--check` печатает индекс, режим поиска, стратегию с параметрами, версию
  локального сервера, состояние облако-ключа и число повторов; при
  остановленном сервере — «Сервер запущен: нет» и причину, код выхода 0;
- `--ask` печатает заголовок «=== Локальный ответ ===», поисковый запрос,
  источники, сам ответ и метрики с опорой на контекст;
- `--compare` печатает обе стороны с метриками и вердикт; недоступная
  облачная сторона выводится как «Недоступна: …»;
- `--evaluate` печатает сводку поиска, обе модели с отклонениями, три вердикта
  и строки вида `q01 | ретрив: да | лок: …% | обл: …%`;
- `--cli` завершает процесс сразу после вывода результата.

### Что показать на видео

1. **Полностью локальный запрос**  
   `./gradlew bootRun --args="--day=28"` → http://localhost:8080/day28.html →
   «Спросить» → ответ с метриками и `engine: local`; в health `usesCloud:
   false`, retrieval «локальный: … без сетевых вызовов».

2. **Сравнение с облаком на общем контексте**  
   Кнопка «Сравнить» (или `--compare`) → обе стороны получают одни и те же
   источники, вердикт сравнивает опору на контекст и скорость: локальная ~28 с
   против облачной ~4 с на той же базе знаний.

3. **Полная оценка**  
   `--evaluate` → за ~9 минут: ретривинг 10/10, покрытие локальной 76.7%
   против облачной 78.3%, стабильность ±0.0 п.п. против ±5.0 п.п. — локальная
   модель отвечает стабильнее, хоть и медленнее.

4. **Остановка Ollama**  
   Гасим `ollama serve` → `--ask` → ответ из индекса с `fallback: true` и
   причиной «Локальный LLM недоступен», HTTP 200; `--check` — «Сервер запущен:
   нет». Поднимаем сервер обратно → тот же запрос снова `fallback: false`.

5. **Контекст в промпте**  
   В `--ask`/`--compare` видно, что модель отвечает по цитатам из
   `articles/embeddings.md` — опора на контекст 50%+ и нулевые обращения к
   индексу за пределами локального поиска.

### Как устроен код дня 28

| Файл | Роль |
|---|---|
| `day28/Day28Properties.java` | стратегия, top-k до/после (10→5), порог (0.5), rewrite, максимум токенов (400), повторы оценки (2) |
| `day28/Day28RagService.java` | `health()`, `questions()`, `ask()`, `compare()`, `evaluate()`; полный конвейер rewrite → search → rerank → filter → prompt → локальная LLM → grounding, фолбэк-ответ и verdicts |
| `day28/Day28Controller.java` | `GET /health`, `GET /questions`, `POST /ask`, `POST /compare`, `POST /evaluate`; пустой вопрос → 400, ошибка LLM → 502 |
| `day28/Day28CliRunner.java` | CLI: `--day=28 --check`, `--ask="..."`, `--compare="..."`, `--evaluate`, опция `--cli` |
| `day28/Day28HealthResponse.java`, `Day28AnswerResponse.java`, `Day28CompareResponse.java` | health (20 полей, `usesCloud=false`), ответ с метриками и фолбэком, сравнение сторон |
| `day28/Day28EvalResponse.java`, `Day28EvalItem.java`, `Day28EngineStats.java`, `Day28AskRequest.java` | агрегаты оценки, построчный результат по вопросу, статистика движка, тело запроса |
| `day21/Day21IndexFacade.java`, `day23/Day23QueryRewriter.java`, `Day23ReRanker.java`, `day24/Day24QuoteEngine.java` | переиспользованный локальный поиск: индекс, переписывание запроса, BM25-переранжирование, grounding |
| `day26/Day26LocalLlmClient.java` | локальная генерация `/api/chat` с разбором токенов и ток/с |
| `llm/LlmClient.java` | облачная сторона (`--compare`/`--evaluate`) через OpenRouter |
| `static/day28.html` | UI: health, поиск, сравнение двух сторон, оценка по 10 вопросам |

Тесты: 41 новый тест в четырёх классах. `Day28RagServiceTest` (16) гоняет
сервис на поднятом mock-Ollama и mock-индексе: тело запроса к модели содержит
вопрос, контекст и источники; фолбэк при остановленном сервере; сравнение с
облаком (включая ошибку 429); оценка с разными комбинациями доступности.
`Day28ControllerTest` (9) и `Day28CliRunnerTest` (13) проверяют HTTP-коды
(200/400/502) и вывод CLI, включая «ОШИБКА:» и строки оценки.
`Day28LocalLlmRealTest` (3) через `Assumptions.assumeTrue` проверяет на живой
Ollama health, ответ модели с реальными источниками и детерминированность
ретривинга, и пропускается без сервера. Всего в проекте 984 теста.

Настройки:

```bash
DAY28_STRATEGY=fixed
DAY28_TOP_K_BEFORE=10
DAY28_TOP_K_AFTER=5
DAY28_THRESHOLD=0.5
DAY28_REWRITE=true
DAY28_ANSWER_MAX_TOKENS=400
DAY28_EVALUATE_RUNS=2
LLM_API_KEY=           # только для сравнения с облаком
```
