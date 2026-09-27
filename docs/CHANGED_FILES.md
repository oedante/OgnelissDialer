# rc50 — что и где менять

## Заменить (4 файла)

```
app/src/main/java/com/ogneliss/dialer/data/db/AppDatabase.kt
app/src/main/java/com/ogneliss/dialer/util/NotificationPermission.kt
app/src/main/res/values-v31/splash.xml
benchmark/build.gradle.kts
scripts/prebuild_check.py
```

---

## Что было не так

### 1. `AppDatabase.kt` — аннотация посреди цепочки

**Было** (строка ~140):

```kotlin
            @Suppress("DEPRECATION")
            .fallbackToDestructiveMigrationOnDowngrade()
```

Аннотация перед строкой, начинающейся с точки, недопустима: компилятор
ждёт продолжение выражения, а получает объявление. Отсюда восемь
сообщений «Expecting member declaration» подряд на одну ошибку.

**Стало** — `@Suppress` перенесён на функцию `provideDatabase` целиком.

### 2. `NotificationPermission.kt` — потерялось объявление функции

**Было** (строка ~41):

```kotlin
    }
        isGranted(context) && NotificationManagerCompat.from(context)...
```

Осталось только тело: строка `fun canNotify(context: Context): Boolean =`
исчезла при одной из моих правок. Восстановлена.

### 3. `values-v31/splash.xml` — нужен префикс `android:`

**Было:**

```xml
<item name="windowSplashScreenBrandingImage">@drawable/splash_branding</item>
```

**Стало:**

```xml
<item name="android:windowSplashScreenBrandingImage">@drawable/splash_branding</item>
```

Это атрибут **платформы**, а не библиотеки `core-splashscreen`.
Библиотека даёт совместимые версии фона и иконки, но брендирования
среди них нет: на старых версиях его показать нечем.

Без префикса сборка ищет атрибут среди атрибутов приложения, не находит
и падает — на этапе сборки ресурсов, до компиляции кода.

### 4. `benchmark/build.gradle.kts` — блок `warnings` убран

```kotlin
warnings {
    maxAgpVersion = false
}
```

Блок был в ранних версиях плагина baselineprofile, в текущих его нет.
Предупреждение о непроверенной версии AGP информационное — если сборка
идёт, его можно игнорировать.

---

## Новая проверка

`prebuild_check.py` теперь ловит аннотации посреди цепочек вызовов.
Ошибка коварна тем, что даёт восемь сообщений на одну причину,
и понять по ним, где именно проблема, трудно.

---

## Про предупреждения KSP

```
w: [ksp] No dependencies reported for generated source
   DialerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.java
```

Это **предупреждение, а не ошибка** — известная особенность Hilt при
работе с KSP. Влияет только на инкрементальную сборку: она будет
чуть медленнее. Исправлять нечего, ждём обновления библиотеки.
