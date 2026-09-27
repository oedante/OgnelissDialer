package com.ogneliss.dialer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogRowMapper
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.NumberCategory
import com.ogneliss.dialer.data.NumberInfo

/**
 * ЛОКАЛИЗОВАННЫЕ ПОДПИСИ ДЛЯ СПИСКА ЖУРНАЛА.
 *
 * ── Зачем прослойка ────────────────────────────────────────────────────────
 * Маппер строк работает вне композиции — именно поэтому он и быстрый.
 * Но строки ему нужны локализованные, а получить их можно только там,
 * где доступны ресурсы.
 *
 * Решение: собрать все нужные строки заранее, один раз, и передать
 * маппером готовые функции. Ресурсы читаются при смене языка,
 * а не на каждую карточку.
 */
@Composable
fun rememberNumberInfoLabels(): CallLogRowMapper.Labels {
    // Все строки читаются здесь: внутри remember обращаться к ресурсам
    // нельзя, это композиция
    val premium = stringResource(R.string.info_premium)
    val tollFree = stringResource(R.string.info_toll_free)
    val shortCode = stringResource(R.string.info_short_code)
    val international = stringResource(R.string.info_international)

    val incoming = stringResource(R.string.call_incoming)
    val outgoing = stringResource(R.string.call_outgoing)
    val missed = stringResource(R.string.call_missed)
    val rejected = stringResource(R.string.call_rejected)
    val blocked = stringResource(R.string.call_blocked)

    // «Сегодня» и «Вчера»: те же строки, что у заголовков групп журнала,
    // чтобы подпись в строке и заголовок над ней не расходились
    val today = stringResource(R.string.date_group_today)
    val yesterday = stringResource(R.string.date_group_yesterday)

    return remember(
        premium, tollFree, shortCode, international,
        incoming, outgoing, missed, rejected, blocked, today, yesterday
    ) {
        object : CallLogRowMapper.Labels {

            override fun infoSummary(info: NumberInfo?): String? {
                if (info == null) return null

                val category = when (info.category) {
                    NumberCategory.PREMIUM -> premium
                    NumberCategory.TOLL_FREE -> tollFree
                    NumberCategory.SHORT_CODE -> shortCode
                    NumberCategory.INTERNATIONAL -> international
                    else -> null
                }

                /*
                 * ОПЕРАТОР И РЕГИОН ИЗ РЕЕСТРА.
                 *
                 * Раньше в строке был только регион, и у мобильных
                 * номеров он почти всегда оказывался пустым: офлайн-
                 * справочник libphonenumber знает регион у городских,
                 * а у мобильных отдаёт «Россия» или ничего. В журнале
                 * под незнакомым номером стояло «Россия» — сведение,
                 * которое не говорит ничего.
                 *
                 * Теперь оба поля берутся из выгрузки Россвязи, и строка
                 * читается как «Билайн · Москва и Московская область».
                 *
                 * Не больше двух частей: третья не помещается по ширине.
                 * Поэтому категория («платный», «международный») вытесняет
                 * оператора — она важнее для решения, брать ли трубку.
                 */
                /*
                 * ЧТО ПОКАЗЫВАТЬ ЗА ПРЕДЕЛАМИ РОССИИ.
                 *
                 * Реестр Россвязи знает только российские номера,
                 * и у немецкого или французского оператор пуст.
                 * Раньше в этом случае строка оставалась с одним
                 * регионом, а часто и вовсе пустой.
                 *
                 * Порядок поиска второй части такой:
                 *
                 *   регион  — «Bayern», «Île-de-France». Есть у части
                 *             городских номеров любой страны;
                 *   страна  — «Германия», «Allemagne». Есть почти всегда
                 *             и на языке интерфейса.
                 *
                 * Страна — не подробность, а последнее, что мы можем
                 * сказать честно. Для мобильного номера чужой страны
                 * это единственное, что вообще известно.
                 */
                val place = info.region ?: info.country

                return listOfNotNull(category ?: info.operatorHint, place)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" · ")
            }

            override fun today(time: String) = "$today, $time"

            override fun yesterday(time: String) = "$yesterday, $time"

            override fun accessibility(title: String, type: CallType, date: String): String {
                val typeText = when (type) {
                    CallType.OUTGOING -> outgoing
                    CallType.MISSED -> missed
                    CallType.REJECTED -> rejected
                    CallType.BLOCKED -> blocked
                    else -> incoming
                }
                // Одна фраза целиком: иначе TalkBack перебирает имя,
                // номер, тип и счётчики по отдельности
                return "$title, $typeText, $date"
            }
        }
    }
}

