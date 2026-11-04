package com.ogneliss.dialer.di

import com.ogneliss.dialer.data.CallLogSource
import com.ogneliss.dialer.data.GroupingSettings
import com.ogneliss.dialer.data.SimInfoSource
import com.ogneliss.dialer.data.SimRepository
import com.ogneliss.dialer.data.RulesRepository
import com.ogneliss.dialer.data.RulesSource
import com.ogneliss.dialer.data.ContactQuickActions
import com.ogneliss.dialer.data.ContactWriter
import com.ogneliss.dialer.data.ContactEditor
import com.ogneliss.dialer.data.ContactEditorLauncher
import com.ogneliss.dialer.data.AndroidShortcutSink
import com.ogneliss.dialer.data.ShortcutSink
import com.ogneliss.dialer.settings.Prefs
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * СВЯЗЫВАНИЕ ИНТЕРФЕЙСОВ С РЕАЛИЗАЦИЯМИ.
 *
 * ── Почему @Binds, а не @Provides ──────────────────────────────────────────
 * `@Binds` говорит Hilt: «когда просят интерфейс, дай эту реализацию».
 * Он не создаёт объект и не порождает лишнего кода — реализация уже
 * умеет собираться сама через `@Inject constructor`.
 *
 * `@Provides` потребовал бы написать фабрику вручную и повторить в ней
 * список зависимостей: при добавлении нового параметра в конструктор
 * пришлось бы править два места, и однажды забыли бы одно.
 *
 * ── Про имена методов ──────────────────────────────────────────────────────
 * Префикс `bind` — не стиль, а защита: Hilt превращает имя метода
 * в имя генерируемого Java-метода, и совпадение с ключевым словом Java
 * ломает сборку невнятной ошибкой KSP. Мы это уже проходили с `default()`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    // Журнал — вызовы и сообщения одной лентой (1.10.0); без роли SMS — только вызовы
    abstract fun bindCallLogSource(impl: com.ogneliss.dialer.data.JournalSource): CallLogSource

    @Binds
    @Singleton
    abstract fun bindSimInfoSource(impl: SimRepository): SimInfoSource

    @Binds
    @Singleton
    abstract fun bindGroupingSettings(impl: Prefs): GroupingSettings

    @Binds
    @Singleton
    abstract fun bindRulesSource(impl: RulesRepository): RulesSource

    @Binds
    @Singleton
    abstract fun bindContactWriter(impl: ContactQuickActions): ContactWriter

    @Binds
    @Singleton
    abstract fun bindContactEditorLauncher(impl: ContactEditor): ContactEditorLauncher

    @Binds
    @Singleton
    abstract fun bindShortcutSink(impl: AndroidShortcutSink): ShortcutSink
}
