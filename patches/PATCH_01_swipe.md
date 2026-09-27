# Правка 1–2, 8: панель свайпа

## Файл: `ui/components/swipe/SwipeRevealRow.kt`

### 1.1. Панель не закрывается после нажатия на кнопку

**Найти** (около строки 336, внутри `ActionPanel`):

```kotlin
                ActionCell(action, actionWidth) {
                    action.onClick()
                    onAction()
                }
```

**Заменить на:**

```kotlin
                ActionCell(action, actionWidth) {
                    /*
                     * Порядок важен: сначала закрываем панель, потом
                     * выполняем действие.
                     *
                     * При обратном порядке действие успевает открыть
                     * другой экран, композиция уходит с экрана вместе
                     * с незавершённой анимацией — и вернувшись, человек
                     * видит строку по-прежнему сдвинутой.
                     */
                    onAction()
                    action.onClick()
                }
```

### 1.2. Закрытие должно возвращать строку на место

**Найти** (около строки 236):

```kotlin
    val closeRow: () -> Unit = { controller.close() }
```

**Заменить на:**

```kotlin
    /*
     * Закрытие панели: и снятие отметки в координаторе, и возврат
     * строки анимацией.
     *
     * Раньше вызывался только координатор — он снимал признак «открыто»,
     * но сама строка оставалась сдвинутой, потому что положением
     * управляет dragState, а он о координаторе не знает.
     */
    val scope = rememberCoroutineScope()
    val closeRow: () -> Unit = {
        controller.close()
        scope.launch { dragState.animateTo(RevealState.CLOSED) }
    }
```

Добавить импорты:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

### 8. При прямом свайпе не видно пиктограммы

**Найти** (около строки 259):

```kotlin
        if (offset > 0 && hasStartPanel) {
```

**Заменить весь блок отрисовки фона на:**

```kotlin
        // Панель с кнопками — когда действий несколько
        if (offset > 0 && hasStartPanel) {
            ActionPanel(
                actions = startActions,
                actionWidth = actionWidth,
                alignment = Alignment.CenterStart,
                onAction = closeRow,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.Start)
                    .width(with(density) { offset.toDp() })
            )
        }

        /*
         * ПИКТОГРАММА ПРЯМОГО ДЕЙСТВИЯ.
         *
         * При одном действии панели нет, и раньше из-под строки
         * выезжало пустое цветное поле: понять, что произойдёт,
         * было нельзя — приходилось доводить жест вслепую.
         *
         * Теперь под строкой видна та же пиктограмма, что была бы
         * на кнопке. Прозрачность растёт по мере сдвига: у порога
         * значок полностью непрозрачен, и это подсказывает, что жест
         * вот-вот сработает.
         */
        if (offset > 0 && hasStartDirect && directStartAction != null) {
            DirectActionHint(
                action = directStartAction,
                progress = (offset / directTriggerPx).coerceIn(0f, 1f),
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.Start)
                    .width(with(density) { offset.toDp() })
            )
        }
```

Аналогично для левой стороны с `hasEndDirect`, `directEndAction`
и `Alignment.CenterEnd`.

### 8.1. Новые параметры компонента

**Найти** сигнатуру `SwipeRevealRow` и добавить:

```kotlin
    /**
     * Пиктограмма и цвет прямого действия.
     *
     * Нужны, чтобы показать под строкой, что произойдёт. Раньше
     * компонент знал только обработчик, и рисовать было нечего.
     */
    directStartAction: RevealAction? = null,
    directEndAction: RevealAction? = null,
```

### 8.2. Новый компонент подсказки

Добавить в конец файла:

```kotlin
/**
 * Подсказка прямого действия под строкой.
 *
 * Показывает пиктограмму и цвет действия, которое сработает,
 * если довести жест. Прозрачность растёт по мере сдвига: это
 * единственный способ понять, засчитается ли жест, не отпуская палец.
 */
@Composable
private fun DirectActionHint(
    action: RevealAction,
    progress: Float,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.background(action.background.copy(alpha = 0.35f + 0.65f * progress)),
        contentAlignment = alignment
    ) {
        Icon(
            action.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f + 0.6f * progress),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .size(24.dp)
        )
    }
}
```
