# Google Play: декларация разрешений SMS — версия 2.0.0

Что заполнить в Play Console перед выпуском 2.0.0 (54); для 2.0.1 (55), 2.0.2 (56) и 2.1.0 (57) — то же, разрешения не менялись. В 2.0.0 появились
четыре новых разрешения: `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`.
Без обновлённой декларации Google отклонит выпуск.

Тексты для формы — на английском (их читают проверяющие Google), каждый
не длиннее 500 знаков. Пояснения для автора — на русском. Подробности
политики — `docs/PLAY_COMPLIANCE.md`, раздел 1а.

---

## 1. Где заполнять

**Play Console** → приложение → **Policy and programs** → **App content** →
**Sensitive app permissions** → **SMS and Call Log permissions** →
**Manage** (декларация для журнала вызовов уже есть — её нужно
**обновить**, а не создавать новую).

Порядок:
1. Загрузить AAB 2.0.0 (54) в нужный трек.
2. Обновить декларацию (этот файл).
3. Обновить **Data safety**, если решите (раздел 6).
4. Отправить выпуск на проверку.

---

## 2. Основная функция (Core functionality)

Отметить **оба** пункта:

- [x] **Default Phone handler** (and any other core functionality usage while default handler) — уже было
- [x] **Default SMS handler** (and any other core functionality usage while default handler) — **новое**

Какие разрешения покрывает каждый пункт (по политике Google Play):

| Пункт | Разрешения |
|---|---|
| Default Phone handler | `READ_CALL_LOG`, `WRITE_CALL_LOG`, `SEND_SMS` |
| Default SMS handler | `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS` |

Условие политики: приложение должно **уже быть** приложением по умолчанию,
прежде чем просить разрешения SMS. У нас так и есть:
- `SEND_SMS` запрашивается только когда диалер — телефон или SMS
  по умолчанию (`SmsSettings.composeHere`);
- `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS` выдаёт сама система вместе
  с ролью SMS — отдельного запроса нет.

---

## 3. Описание основной функции — вставить в форму

**Поле: «Describe your app's core functionality» / «Explain how your app uses the permissions»**

```text
Ogneliss Dialer is a phone app (dialer, call log, contacts, in-call screen) that can also be the default SMS app. When the user sets it as the default SMS handler, it shows SMS/MMS conversations in its SMS section, receives SMS and MMS and sends messages. All processing is on the device; the app has no INTERNET permission and sends no data to any server.
```
(396 знаков)

---

## 4. Зачем каждое разрешение — если форма спрашивает отдельно

**SEND_SMS**
```text
Sends an SMS only when the user taps Send in a conversation, replies from a message notification, or uses "Reply with message" on an incoming call. Requested only while the app is the default Phone or default SMS handler.
```

**READ_SMS**
```text
Shows the user's SMS/MMS conversations in the app's SMS section. Used only while the app is the default SMS handler; when the user picks another SMS app, the app stops reading messages immediately.
```

**RECEIVE_SMS**
```text
As the default SMS handler, the app receives incoming SMS (SMS_DELIVER), saves them to the system message store and shows a notification with reply and mark-as-read actions.
```

**RECEIVE_MMS**
```text
As the default SMS handler, the app receives MMS notifications (WAP_PUSH_DELIVER) and downloads MMS through the system SmsManager so pictures appear in the conversation.
```

**Если спросят про коды подтверждения:**
```text
Verification codes in incoming SMS are detected on the device. The app only shows a "Copy" button; the code is copied to the clipboard (marked sensitive) when the user taps it. Nothing is uploaded, and the app does not autofill codes into other apps.
```

---

## 5. Видео для проверки

Google просит короткое видео (обычно до 2 минут), где видно основную
функцию, окно выбора приложения по умолчанию и работу разрешений.
Ссылка — **YouTube «Доступ по ссылке»** или Google Диск с доступом
по ссылке.

**Поле: «Video instructions» / ссылка на видео**

Что снять (телефон с SIM, Android 13+; в кадре не должно быть реальных
номеров и имён — используйте тестовый контакт и второй свой номер):

1. **0:00** — запуск, журнал вызовов, исходящий вызов (основная функция «Телефон»).
2. **0:15** — Настройки → Журнал → Сообщения → «Приложение SMS
   по умолчанию» → системное окно → выбрать Ogneliss Dialer.
3. **0:30** — входящее SMS → уведомление → «Ответить» из шторки.
4. **0:45** — касание уведомления → переписка: история, отправка SMS,
   отправка картинки (MMS).
5. **1:05** — раздел «SMS» на нижней панели: список переписок, свайп.
6. **1:15** — SMS с кодом → «Копировать …» в уведомлении.
7. **1:25** — входящий вызов → «Ответить сообщением» (`SEND_SMS`).
8. **1:35** — Настройки Android → Приложения по умолчанию → SMS →
   другое приложение. Вернуться в раздел «SMS»: переписок нет, только
   «Сделать приложением SMS» (чтение прекращено без роли).

Подпись к видео в форме:
```text
0:00 main phone features; 0:15 user sets the app as default SMS handler via the system dialog; 0:30 incoming SMS and reply from notification; 0:45 conversation, sending SMS and MMS; 1:05 the SMS section with conversations; 1:15 copying a verification code; 1:25 reply with message on an incoming call; 1:35 user switches to another SMS app and the app stops showing messages.
```

---

## 6. Data safety

Решать вам — я не юрист. Как это выглядит с точки зрения правил:

- **Собирает ли приложение SMS?** По определению Google «сбор» — это
  передача данных **с устройства** разработчику или третьим лицам.
  Приложение ничего никуда не передаёт (нет разрешения `INTERNET`),
  сообщения обрабатываются только на устройстве. Данные, которые
  обрабатываются только на устройстве, в Data safety указывать
  не нужно.
- **Отправка SMS получателю** — действие самого человека (он нажал
  «Отправить»). Это не «передача третьим лицам» в смысле Data safety.
- Поэтому ответы **не меняются**: «Data collected — No», «Data shared —
  No». Если раньше вы отмечали иначе — сверьте с политикой
  конфиденциальности 1.2 (раздел о SMS и MMS).

---

## 7. Остальное в App content

- **Privacy policy** — та же ссылка; на сайте уже версия 1.2
  с разделом о SMS и MMS (`docs/www/privacy.html`). Сайт нужно выложить
  **до** отправки на проверку.
- **Описание в магазине** — политика требует, чтобы основная функция
  была видна в описании. Раздел «Сообщения» уже добавлен в
  `store/LISTING_RU/EN/DE/FR.md` — обновите описание в Play Console.
- **Full-screen intent** (Android 14+) — без изменений с прошлых версий.
- **Что нового** — `store/whats_new_2.0.0.txt` (27 языков).

---

## 8. Если Google отклонит

Частые причины и ответ:

- **«Приложение не является приложением по умолчанию / не просит роль»** —
  показать в видео шаг 2 крупнее, приложить скриншот системного окна
  выбора SMS-приложения.
- **«Основная функция не соответствует»** — указать в апелляции:
  приложение — полноценный обработчик SMS (приём SMS_DELIVER,
  WAP_PUSH_DELIVER, служба RESPOND_VIA_MESSAGE, экран SENDTO — всё
  объявлено в манифесте), а не просто использует SMS как вспомогательную
  функцию.
- **Нужна срочность** — в поле **APK Exceptions** ничего не указывать;
  исключения нужны только для старых версий.

---

## 9. Доступ к контактам (READ_CONTACTS) — до 27.01.2027

Новое правило Google Play для приложений с targetSdk 37 (у нас 37):
`READ_CONTACTS` можно оставить, только если системного окна выбора
контактов (Contact Picker, Android 17) недостаточно. Нужна декларация.
Видео не требуется.

**Где:** Policy and programs → App content → **Доступ к контактам**
(Contacts permissions) → отправить декларацию.

### Шаг 1. «Функции, доступные пользователям»

Отметить четыре пункта:

- [x] **Управление контактами** (Contact management) — вкладка «Контакты»,
  избранное, карточка, создание и правка
- [x] **Звонки или отправка SMS** (Dialing or SMS sending) — поиск
  по имени при наборе, имена в переписке SMS
- [x] **Журнал звонков** (Call history) — имена и фото у каждого вызова
- [x] **Управление входящими вызовами** (Call screening) — экран
  входящего с именем, незнакомые номера отдельно (спам, тихие часы)

Не отмечать: «Автозамена и автозаполнение» (это для клавиатур),
«Выбор контакта на устройстве, инициированный пользователем» (это как
раз то, что покрывает окно выбора).

### Шаг 2. «Укажите причину, по которой вашему приложению требуется разрешение» (до 500)

```text
Ogneliss Dialer is a phone app (default Phone handler) and optional default SMS app. Contacts are its core data: the Contacts tab with search, favorites and contact cards; caller names and photos in the call log and on the incoming-call screen; search by name while dialing; names in SMS conversations and notifications; separate handling of unknown numbers (spam warnings, quiet hours). All on the device: the app has no INTERNET permission.
```
(442 знаков)

### Шаг 3. «Почему инструмента выбора контактов Android недостаточно» (до 500)

```text
The Contact Picker returns only contacts the user selects one at a time. A dialer must match every incoming and logged call against the whole address book automatically, without user action: to show who is calling before answering, to name each call log entry, to find contacts while the user types digits on the dialpad and to tell known callers from unknown ones for spam protection. None of this works with manually picked contacts.
```
(435 знаков)

**Чего не писать** (Google прямо называет неподходящими основаниями):
«у нас своё окно выбора контактов», «пригласить друга», «поделиться».

### Дальше: WRITE_CONTACTS и MANAGE_CONTACTS (лето 2027)

Сейчас ничего делать не нужно.

- **С targetSdk 38** `WRITE_CONTACTS` перестанет давать право создавать
  и править контакты: запись будет падать с SecurityException.
- **Новое разрешение `MANAGE_CONTACTS`** дают приложениям, чья основная
  функция — управление контактами. Своё оформление редактора
  основанием не считается.
- **Декларации** для `MANAGE_CONTACTS` Google начнёт спрашивать летом
  2027, обязательны с августа 2027.
- **Когда будем поднимать targetSdk до 38**, выбрать одно из двух:
  - создание и правка — через системные `ACTION_INSERT` / `ACTION_EDIT`;
  - декларация `MANAGE_CONTACTS` с категорией «Contact management».

Записано в `docs/PLAY_COMPLIANCE.md`.

