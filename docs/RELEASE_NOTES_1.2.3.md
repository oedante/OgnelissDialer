# Ogneliss Dialer 1.2.3

```
versionName  1.2.3
versionCode  24       (проставлено в app/build.gradle.kts)
minSdk 26 · compileSdk 36 · targetSdk 36
Сборка: AGP 9.3.1 · Kotlin 2.2.10 · KSP 2.3.6 · Room 2.8.5
В магазине: 1.2.1 · 1.2.2 — только во внутреннем тесте
```

Миграций базы нет. Новых разрешений и зависимостей нет.

Публикуется вместо 1.2.2: в магазин выходит всё, что сделано после 1.2.1, —
разделы 6–11 `RELEASE_NOTES_1.2.1.md`, весь `RELEASE_NOTES_1.2.2.md`
и этот файл.

## 1. После звонка нажатие на запись журнала ничего не делало

Сообщено по логу 30.09.2026: после завершения звонка нажатие на запись
журнала не открывало карточку, всё остальное работало.

**Причина — ошибка с самого начала журнала, проявилась с ключами карточек.**
Строки журнала пересобираются, когда меняется «ключ пересборки». В него
входили размер списка вызовов и его первая запись — но список читался
не текущий, а тот, что был при первом показе экрана: переменная
запоминалась один раз вместе с `derivedStateOf` внутри `remember { }`.
Пересборка случалась только от других составляющих ключа — числа заметок,
контактов, сведений о номерах. После обычного звонка ни одна из них
не менялась, и строки оставались старыми.

Ключ карточки — «абонент#номер последней записи». После звонка номер
последней записи другой, а строка журнала держала старый. Нажатие
открывало карточку по старому ключу, карточка такой группы не находила
и тут же закрывалась — со стороны «ничего не происходит».

**Исправлено в трёх местах:**
- ключ пересборки читает текущий список (`rememberUpdatedState`) и учитывает
  ключи всех групп, а не только первой;
- закреплённые строки, строки без закреплённых и группы по дням
  пересчитываются по самим строкам, а не по их числу и первой записи;
- карточка при устаревшем ключе открывает свежую группу того же абонента
  вместо молчаливого закрытия.

**Журнал приложения** теперь пишет нажатия на записи (какая ветка
сработала, ключ с замаскированным номером) и случаи, когда карточка
не нашла группу, — чтобы подобное было видно по логу сразу.

## 2. 44 языка автоперевода — в выборе языка

В Play Console автоперевод включён для 44 языков (было 10). Все они
перечислены в `locales_config.xml` — их можно выбрать вручную в приложении
и в системной настройке «Язык приложения». Письмо справа налево: арабский,
иврит, персидский, урду — проверить в предпросмотре консоли.

Описание в магазине: «14 языков» → «48 языков».

## Перед публикацией

```bash
./scripts/kotlin_syntax_check.sh   # нужен kotlinc (KOTLINC=...)
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintRelease
./scripts/check_no_internet.sh
# профиль запуска — с ОТКЛЮЧЁННЫМ от компьютера телефоном
./gradlew generateBaselineProfile
./gradlew bundleRelease
./scripts/check_native_symbols.sh
```

### Что проверить глазами

1. **Исправление:** позвонить из журнала, вернуться, нажать на эту запись —
   открывается карточка. Два звонка подряд одному человеку — то же.
2. В логе приложения видны строки «Нажатие: карточка …».
3. Всё из списка проверки 1.2.2 (`RELEASE_NOTES_1.2.2.md`).
4. Выбор языка в приложении — 48 языков; арабский или иврит — экран
   зеркальный, номера читаются.

## Текст «Что нового» для Google Play

Считается от 1.2.1 — последней версии в магазине.

**Русский**

```
• Экран вызова: можно отключить гашение экрана у уха и показывать у контактов тип номера вместо цифр. Кнопки ответа помещаются на узких экранах.
• Все меню — удобными шторками.
• Телефон лёжа: панель справа, под большим пальцем.
• Псевдозвонок — первым в меню значка на рабочем столе.
• 48 языков интерфейса.
• Исправлено: запись журнала могла не открываться после звонка.
```

**English**

```
• Call screen: turn off screen blanking near your ear, and show the number type instead of digits for contacts. Answer buttons fit narrow screens.
• All menus are tidy bottom sheets.
• Phone in landscape: the panel moves to the right, under your thumb.
• Fake call comes first in the home screen icon menu.
• 48 interface languages.
• Fixed: a call log entry could fail to open after a call.
```

**Deutsch** — вычитать носителем.

```
• Anrufbildschirm: Ausschalten des Displays am Ohr abschaltbar, bei Kontakten Nummerntyp statt Ziffern. Annahme-Tasten passen auf schmale Displays.
• Alle Menüs als Leisten von unten.
• Telefon quer: Leiste rechts, unter dem Daumen.
• Scheinanruf zuerst im Symbolmenü des Startbildschirms.
• 48 Sprachen.
• Behoben: Anruflisten-Eintrag öffnete sich nach einem Anruf teils nicht.
```

**Français** — вычитать носителем.

```
• Écran d’appel : veille près de l’oreille désactivable, type de numéro au lieu des chiffres pour les contacts. Boutons de réponse adaptés aux écrans étroits.
• Tous les menus en volets.
• Téléphone à l’horizontale : barre à droite, sous le pouce.
• Faux appel en premier dans le menu de l’icône.
• 48 langues.
• Corrigé : une entrée du journal pouvait ne pas s’ouvrir après un appel.
```
