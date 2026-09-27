/*
 * ВЫБОР ЯЗЫКА НА СТРАНИЦАХ САЙТА — ВЫПАДАЮЩИЙ СПИСОК С ФЛАГАМИ.
 *
 * ══════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ОБЩИЙ ФАЙЛ, А НЕ СКРИПТ В КАЖДОЙ СТРАНИЦЕ
 * ══════════════════════════════════════════════════════════════════════
 * Раньше один и тот же скрипт переключения стоял внутри всех пяти
 * страниц — побайтно одинаковый. Правка одной копии и забытые четыре
 * дали бы страницы, которые переключаются по-разному. Теперь он один,
 * а страницы только подключают его.
 *
 * ── Что делает ─────────────────────────────────────────────────────────
 * 1. Строит кнопку с флагом и названием текущего языка в контейнере
 *    .lang-switcher. По нажатию — список из четырёх языков.
 * 2. Показывает блок id="lang-XX" выбранного языка, прячет остальные.
 * 3. Язык по умолчанию — из браузера (первые две буквы navigator.language:
 *    «de-AT» → «de»), русский — если язык браузера не из наших четырёх.
 * 4. Выбор запоминается на сессию: документы читают вперемежку, и без
 *    этого каждая следующая страница открывалась бы снова на языке
 *    браузера.
 *
 * ── Почему флаги нарисованы, а не эмодзи ───────────────────────────────
 * Эмодзи-флаги Windows не показывает вовсе — вместо флага там две буквы
 * «RU» в квадратиках. Рисунок в SVG одинаков везде.
 *
 * ── Если скрипт не загрузился ──────────────────────────────────────────
 * Видны все четыре языковых блока подряд. Некрасиво, но текст доступен —
 * это важнее для документов, которые читает проверяющий из Google Play.
 */
(function () {
  'use strict';

  /*
   * Подпись возврата к списку документов.
   *
   * Шапка стоит вне языковых блоков — одна на страницу, — поэтому её
   * текст переводится здесь, а не четырьмя копиями в разметке.
   */
  var BACK = {
    ru: 'Назад к документам',
    en: 'Back to documents',
    de: 'Zurück zu den Dokumenten',
    fr: 'Retour aux documents'
  };

  var LANGS = [
    { code: 'ru', name: 'Русский',  flag:
      '<rect width="30" height="7" fill="#fff"/>' +
      '<rect y="7" width="30" height="6" fill="#0039A6"/>' +
      '<rect y="13" width="30" height="7" fill="#D52B1E"/>' },
    { code: 'en', name: 'English',  flag:
      // Флаг Великобритании в упрощённом виде: на 20 пикселях мелкие
      // смещения диагоналей всё равно не видны
      '<rect width="30" height="20" fill="#012169"/>' +
      '<path d="M0 0L30 20M30 0L0 20" stroke="#fff" stroke-width="4"/>' +
      '<path d="M0 0L30 20M30 0L0 20" stroke="#C8102E" stroke-width="1.6"/>' +
      '<path d="M15 0V20M0 10H30" stroke="#fff" stroke-width="6"/>' +
      '<path d="M15 0V20M0 10H30" stroke="#C8102E" stroke-width="3.4"/>' },
    { code: 'de', name: 'Deutsch',  flag:
      '<rect width="30" height="7" fill="#000"/>' +
      '<rect y="7" width="30" height="6" fill="#DD0000"/>' +
      '<rect y="13" width="30" height="7" fill="#FFCE00"/>' },
    { code: 'fr', name: 'Français', flag:
      '<rect width="10" height="20" fill="#0055A4"/>' +
      '<rect x="10" width="10" height="20" fill="#fff"/>' +
      '<rect x="20" width="10" height="20" fill="#EF4135"/>' }
  ];

  var KEY = 'ogneliss-lang';

  function flagSvg(lang) {
    return '<svg class="lang-flag" viewBox="0 0 30 20" aria-hidden="true">' + lang.flag + '</svg>';
  }

  function find(code) {
    for (var i = 0; i < LANGS.length; i++) if (LANGS[i].code === code) return LANGS[i];
    return LANGS[0];
  }

  function apply(code) {
    LANGS.forEach(function (l) {
      var block = document.getElementById('lang-' + l.code);
      if (block) block.style.display = (l.code === code) ? 'block' : 'none';
    });
    document.documentElement.setAttribute('lang', code);

    var backLabel = document.querySelector('.back-label');
    if (backLabel) backLabel.textContent = BACK[code] || BACK.ru;
    try { sessionStorage.setItem(KEY, code); } catch (e) {}
  }

  function build(container, current) {
    var cur = find(current);

    container.innerHTML =
      '<button type="button" class="lang-button" aria-haspopup="listbox" aria-expanded="false">' +
        flagSvg(cur) +
        '<span class="lang-name">' + cur.name + '</span>' +
        '<svg class="lang-chevron" viewBox="0 0 24 24" aria-hidden="true">' +
          '<path d="M7 10l5 5 5-5" fill="none" stroke="currentColor" stroke-width="2"/></svg>' +
      '</button>' +
      '<ul class="lang-list" role="listbox" hidden>' +
        LANGS.map(function (l) {
          return '<li role="option" tabindex="-1" data-lang="' + l.code + '"' +
                 (l.code === cur.code ? ' aria-selected="true"' : '') + '>' +
                 flagSvg(l) + '<span>' + l.name + '</span></li>';
        }).join('') +
      '</ul>';

    var button = container.querySelector('.lang-button');
    var list = container.querySelector('.lang-list');

    function open(state) {
      list.hidden = !state;
      button.setAttribute('aria-expanded', String(state));
      if (state) {
        var sel = list.querySelector('[aria-selected="true"]') || list.firstChild;
        sel.focus();
      }
    }

    function choose(code) {
      apply(code);
      build(container, code);
      container.querySelector('.lang-button').focus();
    }

    button.addEventListener('click', function () { open(list.hidden); });

    list.addEventListener('click', function (e) {
      var li = e.target.closest('li[data-lang]');
      if (li) choose(li.getAttribute('data-lang'));
    });

    // Клавиатура: стрелки ходят по списку, Enter выбирает, Esc закрывает —
    // так же, как у обычного выпадающего списка браузера
    list.addEventListener('keydown', function (e) {
      var items = Array.prototype.slice.call(list.querySelectorAll('li'));
      var i = items.indexOf(document.activeElement);
      if (e.key === 'ArrowDown') { items[(i + 1) % items.length].focus(); e.preventDefault(); }
      else if (e.key === 'ArrowUp') { items[(i - 1 + items.length) % items.length].focus(); e.preventDefault(); }
      else if (e.key === 'Enter' || e.key === ' ') {
        if (i >= 0) choose(items[i].getAttribute('data-lang'));
        e.preventDefault();
      }
      else if (e.key === 'Escape') { open(false); button.focus(); }
    });

  }

  function start() {
    var saved = null;
    try { saved = sessionStorage.getItem(KEY); } catch (e) {}

    var browser = (navigator.language || navigator.userLanguage || '').slice(0, 2).toLowerCase();
    var known = LANGS.some(function (l) { return l.code === browser; });
    var code = saved || (known ? browser : 'ru');

    apply(code);

    var container = document.querySelector('.lang-switcher');
    if (!container) return;
    build(container, code);

    /*
     * Касание мимо списка закрывает его — иначе на телефоне он висел бы
     * поверх текста, пока не выберешь что-нибудь.
     *
     * Подписка одна, здесь, а не в build(): build() вызывается заново при
     * каждой смене языка, и подписка внутри него копилась бы — по одной
     * лишней на каждое переключение.
     */
    document.addEventListener('click', function (e) {
      if (container.contains(e.target)) return;
      var list = container.querySelector('.lang-list');
      var button = container.querySelector('.lang-button');
      if (list && !list.hidden) {
        list.hidden = true;
        button.setAttribute('aria-expanded', 'false');
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
