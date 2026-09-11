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
