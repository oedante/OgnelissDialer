package com.ogneliss.dialer.concurrency

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * ОПЕРАТОРЫ ПОТОКОВ ДЛЯ ГОРЯЧИХ СОБЫТИЙ.
 */
object FlowTiming {

    /**
     * Задержка перед реакцией на изменение журнала вызовов.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * ContentObserver на CallLog срабатывает не один раз за звонок, а
     * несколько: система создаёт запись при начале вызова, обновляет
     * длительность при завершении, потом дописывает кэшированное имя
     * и фото контакта. Плюс на некоторых прошивках добавляются свои правки.
     *
     * Без задержки каждое из этих событий запускало бы полное перечитывание
     * журнала и пересчёт группировки — четыре-пять раз подряд за секунду,
     * прямо в момент, когда пользователь смотрит на экран завершения вызова.
     *
     * 400 мс подобраны так, чтобы правки успели прийти пачкой, но список
     * обновился раньше, чем пользователь заметит задержку.
     */
    const val CONTENT_CHANGE_DEBOUNCE_MS = 400L

    /**
     * Задержка перед поиском по мере ввода.
     * Ноль означало бы полный перебор на каждое нажатие цифры.
     * 120 мс — примерно интервал между быстрыми нажатиями, поэтому
     * при наборе подряд лишние проходы не запускаются вовсе.
     */
    const val SEARCH_DEBOUNCE_MS = 120L
}

@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounceContentChanges(): Flow<T> =
    this.debounce(FlowTiming.CONTENT_CHANGE_DEBOUNCE_MS)

@OptIn(FlowPreview::class)
fun Flow<String>.debounceSearchQuery(): Flow<String> =
    this.debounce(FlowTiming.SEARCH_DEBOUNCE_MS).distinctUntilChanged()
