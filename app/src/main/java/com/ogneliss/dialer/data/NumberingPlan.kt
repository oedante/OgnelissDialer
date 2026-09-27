package com.ogneliss.dialer.data

import android.content.Context
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ОПРЕДЕЛЕНИЕ ОПЕРАТОРА И РЕГИОНА ПО РЕЕСТРУ РОССВЯЗИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЭТО ВМЕСТО ОЦЕНКИ РИСКА
 * ══════════════════════════════════════════════════════════════════════════
 * «Вероятно спам» — догадка, которую нечем подтвердить: те же признаки
 * даёт и коллектор, и жена с чужого телефона. «МТС, Краснодарский край» —
 * факт из официальной выгрузки российской системы и плана нумерации.
 *
 * Человек по такой подписи решает сам, и решает точнее любой эвристики:
 * незнакомый номер своего города в рабочее время — это, скорее всего,
 * доставка; тот же номер с другого конца страны в девять вечера — нет.
 *
 * ⚠️ Для мобильного номера регион — это регион ВЫДАЧИ, а не место,
 * откуда звонят: с переносом номера человек уезжает вместе с ним.
 * Поэтому подпись в интерфейсе не должна говорить «звонит из».
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ФАЙЛ, А НЕ БАЗА ДАННЫХ
 * ══════════════════════════════════════════════════════════════════════════
 * Данных 419 551 диапазон. Замеры на них же:
 *
 *     SQLite с индексом      15.9 МБ, около 5.8 МБ в архиве приложения
 *     этот формат             4.2 МБ, около 1.5 МБ в архиве
 *
 * Четыре мегабайта разницы для приложения, которое целиком меньше.
 * Скорость при этом не хуже: записи фиксированной длины, внутри кода
 * идёт двоичный поиск, без разбора строк и без SQL. Room дал бы
 * миграции и запросы — всё то, что базе только для чтения не нужно.
 *
 * ── Почему копия в файлах, а не чтение прямо из assets ────────────────────
 * Ресурс в архиве лежит сжатым, и произвольный доступ к нему невозможен:
 * пришлось бы разжимать поток с начала на каждый поиск. Разжатая копия
 * в каталоге приложения отображается в память (mmap) целиком, и чтение
 * становится обращением по смещению.
 *
 * Цена — 4.2 МБ в хранилище устройства и однократное копирование
 * при первом обращении после установки или обновления.
 *
 * Формат описан в scripts/build_numbering.py; там же сборка из выгрузок.
 */
@Singleton
class NumberingPlan @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Что известно о диапазоне, в который попал номер. */
    data class Entry(val operator: String, val region: String)

    companion object {

        /**
         * НАЗВАНИЕ ОПЕРАТОРА ДЛЯ ЭКРАНА.
         *
         * В реестре стоят юридические названия: «ООО "Т2 МОБАЙЛ"»,
         * «ПАО "ВЫМПЕЛКОМ"». В строке журнала это и длинно, и непонятно:
         * человек знает эти компании как Tele2 и Билайн.
         *
         * Таблица только для тех, чьё юридическое имя не совпадает
         * с торговым. Остальным достаточно общего правила: убрать
         * форму собственности и кавычки, привести КАПС к обычному виду.
         *
         * Незнакомый оператор при этом не пропадает и не искажается —
         * «ООО "СВЯЗЬ СЕРВИС"» станет «Связь сервис», и это ровно то,
         * что написано в реестре.
         */
        private val BRANDS = mapOf(
            "ВЫМПЕЛКОМ" to "Билайн",
            /*
             * В реестре «Вымпел-Коммуникации» — именно так, через дефис,
             * и это одно из десяти самых частых названий (12 033
             * диапазона). Без этой строки «Билайн» подставлялся
             * не всегда, а разница на экране незаметна: человек видит
             * то одно, то другое написание и не понимает, почему.
             */
            "ВЫМПЕЛ-КОММУНИКАЦИИ" to "Билайн",
            "Т2 МОБАЙЛ" to "Tele2",
            "СКАРТЕЛ" to "Yota",
            "МЕГАФОН" to "МегаФон",
            "МТС" to "МТС",
            /*
             * Полное имя из реестра — «МОБИЛЬНЫЕ ТЕЛЕСИСТЕМЫ». Оно
             * занимает почти всю ширину строки журнала и вытесняет
             * регион, ради которого строка и существует.
             *
             * Сокращение общеизвестно: под «МТС» этого оператора знают
             * все, под полным названием — почти никто.
             *
             * ⚠️ В реестре встречаются оба написания — через дефис
             * и без, — поэтому перечислены оба: таблица сравнивает
             * строки целиком, а не по вхождению.
             */
            "МОБИЛЬНЫЕ ТЕЛЕСИСТЕМЫ" to "МТС",
            "МОБИЛЬНЫЕ ТЕЛЕ-СИСТЕМЫ" to "МТС",
            /*
             * Остальные частые из реестра. Отобраны по числу диапазонов:
             * эти названия человек встречает чаще прочих, и их длина
             * съедает строку журнала.
             *
             * ТрансТелеКом и ТТК-Связь сведены к «ТТК»: это одна группа
             * компаний, и на экране различать их незачем.
             */
            "КОМПАНИЯ ТРАНСТЕЛЕКОМ" to "ТТК",
            "ТТК-СВЯЗЬ" to "ТТК",
            "ЭР-ТЕЛЕКОМ ХОЛДИНГ" to "Дом.ru",
            "ВЕСТ КОЛЛ ЛТД" to "Вест Колл",
            "РОСТЕЛЕКОМ" to "Ростелеком",
            "МТТ" to "МТТ",
            "МОТИВ" to "Мотив"
        )

        private val LEGAL_FORMS = listOf(
            "ПАО", "ОАО", "ЗАО", "ООО", "АО", "ИП", "ФГУП", "ГУП", "МУП", "НАО"
        )

        fun shortOperatorName(raw: String): String {
            var name = raw.trim()
            LEGAL_FORMS.forEach { form ->
                if (name.startsWith("$form ", ignoreCase = true)) {
                    name = name.removeRange(0, form.length + 1)
                }
            }
            name = name.trim().trim('"', '«', '»').trim()

            BRANDS[name.uppercase()]?.let { return it }

            // Реестр пишет названия капсом; читать их так тяжело,
            // а сокращения из двух-трёх букв (МТТ, ТТК) капсом и пишутся
            return if (name.length > 4 && name == name.uppercase()) {
                name.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                name
            }
        }
        const val ASSET = "numbering_plan.bin"

        /**
         * Имя копии содержит версию формата.
         *
         * При смене формата имя меняется, старая копия удаляется, новая
         * распаковывается. Без версии в имени пришлось бы держать
         * отдельный файл-признак и следить за его согласованностью.
         */
        const val FORMAT_VERSION = 1
        const val COPY_NAME = "numbering_plan_v$FORMAT_VERSION.bin"

        const val SIGNATURE = 0x4F474E50 // "OGNP"
        const val DIRECTORY_ENTRY_SIZE = 10
        const val RANGE_SIZE = 10

        const val TAG = "NumberingPlan"

    }

    private class Loaded(
        val buffer: ByteBuffer,
        /** Коды АВС/DEF по возрастанию — по ним идёт двоичный поиск. */
        val codes: IntArray,
        val offsets: IntArray,
        val counts: IntArray,
        val operators: Array<String>,
        val regions: Array<String>
    )

    @Volatile
    private var loaded: Loaded? = null

    @Volatile
    private var failed = false

    /**
     * Оператор и регион номера или null.
     *
     * null означает «не нашли»: номер зарубежный, короткий, скрытый,
     * либо диапазона нет в выгрузке. Это штатный ответ, а не сбой.
     *
     * Вызывать из фонового потока: первое обращение после установки
     * распаковывает файл на 4 МБ.
     */
    fun lookup(e164: String): Entry? {
        val plan = ensureLoaded() ?: return null

        /*
         * ТОЛЬКО НОМЕРА ЗОНЫ +7 И ДЕСЯТИ ЦИФР.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПРОВЕРЕНО, А НЕ ПРЕДПОЛОЖЕНО
         * ══════════════════════════════════════════════════════════════════
         * Номера США (+1), Великобритании (+44), Германии (+49)
         * и Франции (+33) отсеиваются здесь же: у них другой код страны,
         * и до поиска дело не доходит.
         *
         * Зону +7 делят Россия и Казахстан, поэтому важно, что лежит
         * в самом файле. Разбор выгрузки, сентябрь 2026: 189 кодов,
         * 419 551 диапазон; казахстанских мобильных кодов (701, 702,
         * 705, 707, 708, 747, 771, 775–778) нет ни одного, абхазского
         * 940 тоже нет. Реестр Россвязи их и не содержит — это чужие
         * планы нумерации.
         *
         * ⚠️ Одно исключение: код 997. В выгрузке он есть — два
         * диапазона московского оператора, — и в Казахстане тот же код
         * тоже используется. Совпадение возможно только для номера,
         * который попадёт ровно в эти два диапазона по сто номеров.
         * Если такое встретится, правильный ответ — сузить проверку
         * по коду страны SIM, а не гадать здесь.
         */
        val digits = e164.removePrefix("+").takeIf { it.startsWith("7") }
            ?.drop(1)
            ?.takeIf { it.length == 10 && it.all(Char::isDigit) }
            ?: return null

        val code = digits.take(3).toInt()
        val local = digits.drop(3).toInt()

        val codeIndex = plan.codes.binarySearch(code)
        if (codeIndex < 0) return null

        return findRange(plan, codeIndex, local)
    }

    /**
     * Двоичный поиск диапазона внутри блока одного кода.
     *
     * Диапазоны упорядочены по началу и не пересекаются, поэтому ищем
     * последний, начало которого не больше номера, и проверяем, что номер
     * не вышел за его конец. Проверка обязательна: между диапазонами
     * бывают дыры — номера, которые не выданы никому.
     */
    private fun findRange(plan: Loaded, codeIndex: Int, local: Int): Entry? {
        var low = 0
        var high = plan.counts[codeIndex] - 1
        val base = plan.offsets[codeIndex]
        var found = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val begin = readUInt24(plan.buffer, base + mid * RANGE_SIZE)
            if (begin <= local) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (found < 0) return null

        val at = base + found * RANGE_SIZE
        val begin = readUInt24(plan.buffer, at)
        val end = begin + readUInt24(plan.buffer, at + 3)
        if (local > end) return null

        val operator = plan.buffer.getShort(at + 6).toInt() and 0xFFFF
        val region = plan.buffer.getShort(at + 8).toInt() and 0xFFFF
        return Entry(
            operator = plan.operators.getOrElse(operator) { "" },
            region = plan.regions.getOrElse(region) { "" }
        )
    }

    private fun readUInt24(buffer: ByteBuffer, at: Int): Int =
        ((buffer.get(at).toInt() and 0xFF) shl 16) or
            ((buffer.get(at + 1).toInt() and 0xFF) shl 8) or
            (buffer.get(at + 2).toInt() and 0xFF)

    // ── Загрузка ──────────────────────────────────────────────────────────

    @Synchronized
    private fun ensureLoaded(): Loaded? {
        loaded?.let { return it }
        /*
         * Одна неудача выключает определение до перезапуска.
         *
         * Если файла нет или он повреждён, повторные попытки ничего
         * не изменят, а копирование четырёх мегабайт на каждый номер
         * в журнале заморозило бы список.
         */
        if (failed) return null

        return runCatching { load() }
            .onFailure {
                failed = true
                Logger.w(TAG, "База нумерации недоступна: ${it.message}")
            }
            .getOrNull()
            ?.also { loaded = it }
    }

    private fun load(): Loaded {
        val file = File(context.filesDir, COPY_NAME)
        if (!file.exists()) {
            extract(file)
        }

        val buffer = RandomAccessFile(file, "r").use { raf ->
            raf.channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
        }

        require(buffer.getInt(0) == SIGNATURE) { "чужой файл" }
        require(buffer.get(4).toInt() == FORMAT_VERSION) { "другая версия формата" }

        val codeCount = buffer.getShort(5).toInt() and 0xFFFF
        val codes = IntArray(codeCount)
        val offsets = IntArray(codeCount)
        val counts = IntArray(codeCount)
        var at = 7
        for (i in 0 until codeCount) {
            codes[i] = buffer.getShort(at).toInt() and 0xFFFF
            offsets[i] = buffer.getInt(at + 2)
            counts[i] = buffer.getInt(at + 6)
            at += DIRECTORY_ENTRY_SIZE
        }

        val (operators, afterOperators) = readStrings(buffer, at)
        val (regions, _) = readStrings(buffer, afterOperators)

        return Loaded(buffer, codes, offsets, counts, operators, regions)
    }

    /**
     * Таблица строк: количество, затем длина и содержимое каждой.
     *
     * Названия операторов и регионов повторяются в сотнях тысяч диапазонов,
     * поэтому в записи лежит номер в таблице, а сами строки — один раз.
     * Обе таблицы вместе — около 230 КБ.
     */
    private fun readStrings(buffer: ByteBuffer, start: Int): Pair<Array<String>, Int> {
        val count = buffer.getInt(start)
        var at = start + 4
        val out = Array(count) { "" }
        val bytes = ByteArray(512)
        for (i in 0 until count) {
            val length = buffer.getShort(at).toInt() and 0xFFFF
            at += 2
            val target = if (length <= bytes.size) bytes else ByteArray(length)
            for (j in 0 until length) target[j] = buffer.get(at + j)
            out[i] = String(target, 0, length, Charsets.UTF_8)
            at += length
        }
        return out to at
    }

    /**
     * Распаковка ресурса в каталог приложения.
     *
     * Сначала во временный файл, потом переименование: если копирование
     * прервётся на середине (не хватило места, приложение убили),
     * на месте не окажется обрезанного файла, который потом молча
     * давал бы неверные ответы.
     */
    private fun extract(target: File) {
        // Старые версии формата больше не нужны
        context.filesDir.listFiles { f -> f.name.startsWith("numbering_plan_v") }
            ?.forEach { it.delete() }

        val temp = File(target.parentFile, "${target.name}.part")
        context.assets.open(ASSET).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        check(temp.renameTo(target)) { "не удалось переименовать ${temp.name}" }
        Logger.i(TAG, "База нумерации распакована: ${target.length() / 1024} КБ")
    }
}
