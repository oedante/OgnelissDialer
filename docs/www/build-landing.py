import io, re

# Логотип с двумя ушами — пути берутся из готового logo.svg, чтобы не было
# двух расходящихся копий
logo_src = io.open('/home/claude/ogneliss-dialer/docs/www/logo.svg', encoding='utf-8').read()
ear = re.search(r'<mask id="fox-ear".*?<path d="([^"]+)"', logo_src, re.S).group(1)
paths = re.findall(r'<path fill="#FEBD04"(?: mask="url\(#fox-ear\)")? d="([^"]+)"', logo_src)
assert len(paths) == 4, len(paths)
main, inner_r, inner_l, eye = paths

html = f'''<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Огнелисс</title>
<meta name="description" content="Огнелисс — приложения без рекламы и без лишнего доступа к вашим данным.">
<link rel="icon" href="/dialer/favicon.svg" type="image/svg+xml">
<link rel="alternate icon" href="/dialer/favicon-32.png" sizes="32x32">
<link rel="apple-touch-icon" href="/dialer/apple-touch-icon.png">
<style>
html,body{{margin:0;width:100%;min-height:100%}}
body{{
    display:flex;
    flex-direction:column;
    justify-content:center;
    align-items:center;
    min-height:100vh;
    padding:2rem 1rem;
    box-sizing:border-box;
    background:#1F2329;
    font-family:"Segoe UI",Arial,sans-serif;
    color:#E2E2E2;
}}
.logo{{display:flex;align-items:center;gap:26px}}
.logo svg{{width:120px;height:auto;filter:drop-shadow(0 8px 18px rgba(0,0,0,.35))}}
.title{{font-size:64px;font-weight:700;line-height:1;letter-spacing:.5px}}
.fire{{color:#FDBF03}}
.gray{{color:#E2E2E2}}
.subtitle{{margin-top:10px;font-size:15px;color:#8F98A3;letter-spacing:6px}}

.products{{
    display:grid;
    grid-template-columns:repeat(auto-fit,minmax(260px,1fr));
    gap:16px;
    width:100%;
    max-width:620px;
    margin-top:48px;
}}
.card{{
    display:flex;
    align-items:center;
    gap:16px;
    padding:18px 20px;
    border-radius:18px;
    background:rgba(255,255,255,.05);
    border:1px solid rgba(255,255,255,.08);
    color:inherit;
    text-decoration:none;
    transition:border-color .2s ease,transform .2s ease;
}}
a.card:hover,a.card:focus-visible{{border-color:#FDBF03;transform:translateY(-2px);outline:none}}
.card img,.card .icon{{width:56px;height:56px;border-radius:14px;flex-shrink:0}}
.card .icon{{display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,.06)}}
.card .icon svg{{width:30px;height:30px}}
.card h2{{margin:0;font-size:18px;font-weight:600;color:#E2E2E2}}
.card p{{margin:4px 0 0;font-size:14px;line-height:1.4;color:#8F98A3}}
.badge{{
    display:inline-block;
    margin-top:6px;
    padding:2px 10px;
    border-radius:999px;
    font-size:12px;
    color:#FDBF03;
    border:1px solid rgba(253,191,3,.45);
}}
.card.soon{{opacity:.75}}

@media (max-width:600px){{
    .logo{{gap:16px}}
    .logo svg{{width:84px}}
    .title{{font-size:44px}}
    .subtitle{{font-size:12px;letter-spacing:4px}}
    .products{{margin-top:36px}}
}}
</style>
</head>
<body>

<div class="logo">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 150 150" width="120" height="120" aria-label="Огнелисс">
<defs>
<mask id="fox-ear" maskUnits="userSpaceOnUse" x="0" y="0" width="150" height="150">
<rect width="150" height="150" fill="#fff"/>
<path d="{ear}" fill="#000"/>
</mask>
</defs>
<path fill="#FEBD04" mask="url(#fox-ear)" d="{main}"/>
<path fill="#FEBD04" d="{inner_r}"/>
<path fill="#FEBD04" d="{inner_l}"/>
<path fill="#FEBD04" d="{eye}"/>
</svg>
<div>
<div class="title"><span class="fire">огне</span><span class="gray">лисс</span></div>
<div class="subtitle">ПРИЛОЖЕНИЯ БЕЗ ЛИШНЕГО</div>
</div>
</div>

<nav class="products" aria-label="Приложения">

<a class="card" href="/dialer/">
<img src="/dialer/app-icon.png" width="56" height="56" alt="">
<div>
<h2>Огнелисс Dialer</h2>
<p>Телефон для Android: журнал, контакты, заметки и напоминания. Без рекламы и без доступа к интернету.</p>
</div>
</a>

<div class="card soon" aria-disabled="true">
<div class="icon" aria-hidden="true">
<svg viewBox="0 0 24 24" fill="none" stroke="#FDBF03" stroke-width="1.8" stroke-linejoin="round">
<rect x="3" y="5" width="18" height="14" rx="2.5"/>
<path d="M4 7l8 6 8-6"/>
</svg>
</div>
<div>
<h2>Огнелисс Mail</h2>
<p>Умный почтовый клиент.</p>
<span class="badge">В разработке</span>
</div>
</div>

</nav>

</body>
</html>'''

# nginx: строка в одинарных кавычках — внутри не должно быть ни одинарных
# кавычек, ни знака доллара (его nginx примет за переменную)
assert "'" not in html, 'в странице есть одинарная кавычка'
assert '$' not in html, 'в странице есть знак доллара'

io.open('/home/claude/ogneliss-dialer/docs/www/home.html', 'w', encoding='utf-8').write(html)

conf = """# ============================================
# Ogneliss Dialer — явные правила для каждой страницы
# ============================================

# Редирект
location = /dialer {
    return 301 /dialer/;
}

# Главная
location = /dialer/ {
    proxy_pass http://dialer-static/index.html;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# Страницы документов — без .html в URL
location = /dialer/privacy  { proxy_pass http://dialer-static/privacy.html;  proxy_set_header Host $host; }
location = /dialer/eula     { proxy_pass http://dialer-static/eula.html;     proxy_set_header Host $host; }
location = /dialer/terms    { proxy_pass http://dialer-static/terms.html;    proxy_set_header Host $host; }
location = /dialer/licenses { proxy_pass http://dialer-static/licenses.html; proxy_set_header Host $host; }

# ✅ ВСЯ СТАТИКА (css, js, svg, png) — одним правилом
location /dialer/ {
    proxy_pass http://dialer-static/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# ============================================
# Главная ogneliss.ru — витрина приложений
# ============================================
#
# ══════════════════════════════════════════════════════════════════════
# ПОЧЕМУ ФАЙЛ, А НЕ СТРАНИЦА ВНУТРИ return 200 '...'
# ══════════════════════════════════════════════════════════════════════
# У nginx предел длины одного параметра — около 4 КБ. Прежняя страница
# в него укладывалась, новая — с карточками приложений — нет: nginx
# отказывается загружать конфигурацию («too long parameter»).
# Проверено настоящим nginx -t, а не на глаз.
#
# Поэтому страница лежит файлом home.html рядом со страницами Dialer
# и отдаётся тем же статическим сервером. Заодно её можно править,
# не трогая конфигурацию nginx.

location = / {
    proxy_pass http://dialer-static/home.html;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# Прежде любой неизвестный адрес показывал главную. Так и осталось,
# но переадресацией: адресная строка покажет /, а не выдуманный путь
location / {
    return 302 /;
}
"""

io.open('/home/claude/ogneliss-dialer/docs/www/nginx-landing.conf', 'w', encoding='utf-8').write(conf)
print('home.html и nginx-landing.conf записаны')
