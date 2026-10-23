# Лицензия / License

**Ogneliss Dialer**
Copyright © 2026 Ермишин Олег Андреевич / Ermishin Oleg
Контакт / Contact: **oedante@gmail.com**

*English version below.*

## Лицензия приложения — Apache 2.0

С версии 1.5.7 (22.10.2026) Ogneliss Dialer — программа с открытым
исходным кодом. Полный текст лицензии — в файле [`LICENSE`](../LICENSE)
в корне репозитория; юридическую силу имеет английский текст.

**Можно:**
- пользоваться приложением бесплатно и для любых законных целей — дома,
  на работе, в бизнесе, в организации;
- распространять приложение, в том числе выкладывать APK на форумах;
- изучать и изменять код, собирать и распространять свои версии,
  в том числе за плату.

**Если вы распространяете код или свою версию приложения:**
- приложите текст лицензии Apache 2.0;
- сохраните уведомления об авторском праве и текст файла
  [`NOTICE`](../NOTICE) — в файле NOTICE, в документации или на экране
  лицензий вашего приложения;
- в изменённых файлах отметьте, что вы их изменили.

Текст NOTICE:

> Ogneliss Dialer
> Copyright 2026 Ermishin Oleg (Ермишин Олег Андреевич)
> https://github.com/oedante/OgnelissDialer
>
> С использованием открытого проекта Ogneliss Dialer от Ермишина Олега Андреевича.
> Uses the open source project Ogneliss Dialer by Oleg Ermishin.

**Без гарантий:** программа предоставляется «как есть» (разделы 7 и 8
лицензии). Об экстренных вызовах и блокировке звонков —
в [Условиях использования](TERMS_OF_USE.md).

До 1.5.7 приложение распространялось по проприетарной лицензии
(«только личное некоммерческое использование», запрет изменять
и распространять). Эти ограничения сняты.

### Почему Apache 2.0, а не MIT

Обе лицензии разрешают всё, включая коммерческое использование. Apache 2.0
выбрана, потому что требует сохранять текст NOTICE: кто строит свою
версию на этом коде, указывает, что использован Ogneliss Dialer. Своё
условие вроде «укажите автора, если взяли больше половины кода» сделало
бы лицензию нестандартной и спорной в применении — NOTICE решает ту же
задачу стандартным способом. Кроме того, Apache 2.0 прямо оговаривает,
что прав на товарные знаки не даёт, и включает патентную лицензию.

## Название и значок

Лицензия не передаёт прав на название «Ogneliss» и значок приложения.
Неизменённое приложение можно распространять под его названием;
изменённые версии — под своим названием и со своим значком.
Подробно — [`TRADEMARKS.md`](../TRADEMARKS.md).

Android, Google Play и связанные обозначения — товарные знаки Google LLC.

## Сторонние компоненты

Приложение использует библиотеки с открытым исходным кодом. Их лицензии
требуют указания авторства — оно приведено ниже и продублировано
в приложении («О приложении» → «Лицензии открытого ПО»).

### Apache License 2.0

- **AndroidX** (Core, AppCompat, Lifecycle, Activity, Navigation, Room,
  DataStore, WorkManager, Window, SplashScreen, ProfileInstaller, Compose)
  — © The Android Open Source Project
- **Kotlin и Kotlin Coroutines** — © JetBrains s.r.o.
- **Dagger Hilt** — © Google LLC
- **libphonenumber** (с geocoder и carrier) — © Google LLC
- **Coil** — © Coil Contributors
- **Accompanist** — © The Android Open Source Project
- **ZXing** (QR-коды) — © ZXing authors
- **Material Symbols** (иконки) — © Google LLC

Полный текст лицензии: https://www.apache.org/licenses/LICENSE-2.0

Изменений в исходный код библиотек не вносилось; уведомления о лицензиях
сохранены. Библиотеки распространяются на условиях своих лицензий.

### Данные

Справочник операторов и регионов (`app/src/main/assets/numbering_plan.bin`)
собран скриптом `scripts/build_numbering.py` из открытых выгрузок Реестра
российской системы и плана нумерации (Россвязь).

---

## English

### App license — Apache 2.0

Since version 1.5.7 (22 Oct 2026) Ogneliss Dialer is open source software.
The full license text is in [`LICENSE`](../LICENSE) at the repository root.

You may use the app free of charge for any lawful purpose (personal, work,
business), distribute it, and study, change and redistribute the code,
including for a fee. If you distribute the code or a version based on it,
include the license text, keep the copyright notices and the
[`NOTICE`](../NOTICE) text (in a NOTICE file, in the documentation or on the
licenses screen of your app) and mark the files you changed. The software is
provided "as is"; see the [Terms of Use](TERMS_OF_USE.md) about emergency
calls and call blocking.

Before 1.5.7 the app was proprietary freeware (personal non-commercial use
only, no modification or redistribution). Those restrictions are lifted.

### Name and icon

The license does not grant rights to the name "Ogneliss" or to the app icon.
The unmodified app may be shared under its name; modified versions should
use their own name and icon. See [`TRADEMARKS.md`](../TRADEMARKS.md).

### Third-party components

Apache License 2.0: AndroidX (incl. Compose, Room, DataStore, WorkManager,
Navigation, Window), Kotlin and Kotlin Coroutines, Dagger Hilt,
libphonenumber, Coil, Accompanist, ZXing, Material Symbols —
https://www.apache.org/licenses/LICENSE-2.0

The operator and region directory is built from the open exports of the
Russian numbering plan registry (Rossvyaz).
