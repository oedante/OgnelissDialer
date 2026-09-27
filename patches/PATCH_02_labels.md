# Правка 2: убрать окончание номера с кнопки вызова

## Файл: `ui/components/swipe/AndroidRevealStrings.kt`

**Найти:**

```kotlin
    override fun callTo(number: String): String =
        context.getString(R.string.action_call) + " " + NumberDisplay.lastFour(number)

    override fun callWithSim(number: String, simLabel: String): String =
        context.getString(
            R.string.a11y_call_with_sim,
            simLabel,
            NumberDisplay.lastFour(number)
        )

    override fun smsTo(number: String): String =
        context.getString(R.string.action_sms) + " " + NumberDisplay.lastFour(number)

    override fun messengerTo(number: String): String =
        context.getString(R.string.action_whatsapp) + " " + NumberDisplay.lastFour(number)
```

**Заменить на:**

```kotlin
    /*
     * ОКОНЧАНИЕ НОМЕРА УБРАНО С ПОДПИСЕЙ.
     *
     * Куда пойдёт вызов, видно из строки, по которой сделан свайп.
     * Дублировать цифры на узкой кнопке значит тратить место
     * на сведения, которые человек уже прочитал.
     *
     * Исключение — когда у абонента несколько номеров: тогда кнопки
     * действительно нужно различать. Этот случай обрабатывается
     * отдельно в RevealActionFactory, где известно их количество.
     *
     * Если решим вернуть окончание, достаточно раскомментировать
     * прежний вариант ниже.
     */
    override fun callTo(number: String): String =
        context.getString(R.string.action_call)
    //  context.getString(R.string.action_call) + " " + NumberDisplay.lastFour(number)

    override fun callWithSim(number: String, simLabel: String): String =
        context.getString(R.string.a11y_call_with_sim_only, simLabel)
    //  context.getString(R.string.a11y_call_with_sim, simLabel,
    //      NumberDisplay.lastFour(number))

    override fun smsTo(number: String): String =
        context.getString(R.string.action_sms)
    //  context.getString(R.string.action_sms) + " " + NumberDisplay.lastFour(number)

    override fun messengerTo(number: String): String =
        context.getString(R.string.action_whatsapp)
    //  context.getString(R.string.action_whatsapp) + " " + NumberDisplay.lastFour(number)
```

## Файл: `ui/components/swipe/RevealActionFactory.kt`

Там, где строится список кнопок для **нескольких номеров одного
абонента**, окончание оставить — иначе кнопки неразличимы:

```kotlin
            // Несколько номеров: без окончания кнопки неразличимы,
            // и выбор превращается в угадывание
            numbers.mapIndexed { i, number ->
                RevealAction(
                    id = "call_$i",
                    icon = Icons.Default.Call,
                    label = if (numbers.size > 1) NumberDisplay.lastFour(number) else "",
                    description = strings.callTo(number),
                    background = CallGreen,
                    onClick = { onCall(number) }
                )
            }
```
