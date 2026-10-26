# Ogneliss Dialer

Приложение для набора номера на Android. Kotlin, Jetpack Compose.
**Работает полностью офлайн: у приложения нет разрешения на доступ
в интернет.** Код открыт по [лицензии Apache 2.0](LICENSE).

- Google Play: https://play.google.com/store/apps/details?id=com.ogneliss.dialer
- Сайт и документы: https://ogneliss.ru/dialer
- Вопросы и предложения: [Issues](https://github.com/oedante/OgnelissDialer/issues)
  или oedante@gmail.com

*English summary below.*

## Что умеет

- **Набор и поиск:** T9 на кириллице и латинице сразу, быстрый набор
  на цифрах 2–9, пауза и ожидание для голосовых меню; поиск буквами —
  по имени и заметке.
- **Журнал и контакты — соседи:** одна нижняя панель, переключатель
  между списками, долгое нажатие — все разделы. Имена — «Имя Фамилия»
  или «Фамилия Имя», сортировка по имени или по фамилии.
- **Журнал вызовов:** группировка по дням, закреплённые абоненты,
  фильтры, свайпы с выбором действий, действия по долгому нажатию
  карточками, удаление с «Отменить».
- **Оператор и регион** незнакомого номера — по официальному плану
  нумерации, без сети (пока для российских номеров).
- **Свой редактор контактов:** имя, номера, почта, работа, день рождения,
  примечание и фото с обрезкой — без перехода в системные «Контакты»;
  выбор аккаунта для нового контакта, «Добавить номер к контакту».
  Записи мессенджеров и SIM не меняются.
- **Заметки и напоминания** к любому номеру, даже не из контактов.
- **Защита от спама:** чёрный и белый списки, маски вида `8800*`,
  метки «спам» и «мошенники», предупреждение о платных номерах,
  **тихие часы для незнакомых** (ночью — без звука, повторный звонок
  за 15 минут — со звуком). Экстренные номера не блокируются никогда.
- **Экран вызова:** живой фон, портрет звонящего, быстрый ответ SMS,
  Bluetooth, **автодозвон** при «Занято», **название организации
  от оператора** («маркировка звонков»), памятка о мошенниках в долгом
  разговоре с незнакомым номером.
- **Две SIM-карты:** выбор карты — долгим нажатием на любую кнопку
  звонка или SMS, с меткой «тот же оператор».
- **Псевдозвонок:** телефон «звонит», чтобы можно было отойти
  от неудобного разговора.
- **Вопросы и ответы** — прямо в приложении: кнопка под ответом
  открывает нужную настройку. Те же ответы на сайте —
  https://ogneliss.ru/dialer/faq.
- Светлая, тёмная и чёрная темы, масштаб под экран, телефоны лёжа,
  планшеты и раскладушки.

Рекламы, подписок, аналитики и отчётов о сбоях нет.

## Приватность — проверяемо

Обещание «данные не покидают устройство» держится на том, что
в приложении нет разрешения `android.permission.INTERNET`. Это проверяет
CI на каждой сборке ([`.github/workflows/android.yml`](.github/workflows/android.yml)):
если зависимость протащит разрешение в итоговый манифест, сборка упадёт.
Номера и имена в журнале диагностики маскируются — это проверяет
автотест.

## Сборка

Нужны Android Studio последней версии и JDK 17 (идёт со Studio).
Gradle wrapper — в репозитории.

```bash
./gradlew assembleDebug          # отладочная сборка
./gradlew testDebugUnitTest      # юнит-тесты
./gradlew lintDebug              # Android Lint
python3 scripts/prebuild_check.py   # быстрые проверки до компиляции
python3 scripts/audit.py            # строки, ресурсы, мёртвый код
```

Подробно: [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md),
порядок выпуска: [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md).

Проверять лучше на **реальном телефоне**: эмулятор не даёт полноценно
проверить роль приложения для звонков и телефонию.

### Релизная сборка

Ключ подписи в репозиторий не попадает (`*.jks`, `*.keystore`,
`keystore.properties` — в `.gitignore`). Шаблон настроек подписи —
[`keystore.properties.example`](keystore.properties.example).

## Структура

```
app/            приложение
benchmark/      замеры запуска и прокрутки (Macrobenchmark)
docs/           архитектура, дизайн-система, заметки о выпусках
docs/www/       сайт с документами (ogneliss.ru/dialer), четыре языка
legal/          лицензия, условия, политика конфиденциальности
store/          значок, тексты и снимки для Google Play
scripts/        проверки перед сборкой, сборка справочника нумерации
```

Комментарии в коде — по-русски и подробно: они объясняют, почему сделано
так, а не иначе, и что пробовали раньше.

## Как помочь

Ошибки и идеи — в [Issues](https://github.com/oedante/OgnelissDialer/issues).
Перед pull request прогоните `scripts/prebuild_check.py`,
`scripts/audit.py` и юнит-тесты. Строк интерфейса в коде нет —
только ресурсы (`app/src/main/res/values*/strings.xml`).

## Лицензия

Код — [Apache 2.0](LICENSE): можно использовать, изменять и распространять,
в том числе в коммерческих целях. Распространяя код или свою версию
приложения, приложите текст лицензии, сохраните уведомления об авторском
праве и текст файла [NOTICE](NOTICE) — в файле, в документации или
на экране лицензий вашего приложения — и отметьте изменённые файлы.

Текст NOTICE: «С использованием открытого проекта Ogneliss Dialer
от Ермишина Олега Андреевича».

Название «Ogneliss» и значок приложения лицензия не передаёт (раздел 6):
изменённые версии выпускайте под своим названием и со своим значком.
Подробно — [TRADEMARKS.md](TRADEMARKS.md).

Сторонние библиотеки — на своих условиях: [legal/LICENSE.md](legal/LICENSE.md).

---

## English

Ogneliss Dialer is a phone dialer for Android built with Kotlin and Jetpack
Compose. **It works fully offline: the app has no internet permission**,
and CI fails the build if a dependency ever adds one.

Features: T9 search (Cyrillic and Latin) and search by name and note,
call log and contacts side by side under one bottom bar, call history with
grouping, filters and swipe actions, offline operator and region lookup,
notes and call-back reminders for any number, spam protection with
block/allow lists and masks, quiet hours for unknown numbers, auto redial,
company names sent by the carrier, a fraud reminder in long calls with
unknown numbers, a calm in-call screen, two SIM cards (long-press any call
or SMS button to pick one), a fake call, in-app questions and answers that
open the right setting, light/dark/black themes. No ads, no subscriptions,
no analytics.

Build with a recent Android Studio and JDK 17: `./gradlew assembleDebug`.

The code is released under the [Apache License 2.0](LICENSE). If you
distribute it or a version based on it, keep the copyright notices and the
[NOTICE](NOTICE) text ("Uses the open source project Ogneliss Dialer by Oleg
Ermishin"). The name "Ogneliss" and the app icon are not covered by the
license — please give modified versions your own name and icon
([TRADEMARKS.md](TRADEMARKS.md)).
