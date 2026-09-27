# Публикация документов на ogneliss.ru

## Зачем это нужно

Google Play требует политику конфиденциальности **по публичному URL**
и проверяет ссылку автоматически. Копии внутри приложения недостаточно:
консоль её не видит.

Остальные документы выкладываются заодно — на них ссылается EULA,
и ссылка должна вести куда-то реальное.

---

## Что получится

| Адрес | Что |
|---|---|
| `ogneliss.ru/dialer/privacy` | Политика конфиденциальности — **этот адрес в консоль Play** |
| `ogneliss.ru/dialer/eula` | Лицензионное соглашение |
| `ogneliss.ru/dialer/terms` | Условия использования |
| `ogneliss.ru/dialer/licenses` | Открытые компоненты |
| `ogneliss.ru/dialer/` | Страница приложения со ссылками |

---

## Порядок действий

### 1. Собрать страницы

```bash
apt install pandoc
./docs/www/build-pages.sh
```

Результат в `docs/www/dist/dialer/`. Проверить локально:

```bash
python3 -m http.server 8000 --directory docs/www/dist
# открыть http://localhost:8000/dialer/privacy.html
```

### 2. Скопировать на сервер

```bash
rsync -av --delete docs/www/dist/ user@ogneliss.ru:/var/www/ogneliss/
```

### 3. Настроить nginx

```bash
scp docs/www/nginx-ogneliss.conf user@ogneliss.ru:/tmp/
ssh user@ogneliss.ru

sudo mv /tmp/nginx-ogneliss.conf /etc/nginx/sites-available/ogneliss.ru
sudo ln -s /etc/nginx/sites-available/ogneliss.ru /etc/nginx/sites-enabled/
sudo nginx -t
```

⚠️ **`nginx -t` упадёт**, пока нет сертификата — это ожидаемо.
Сначала получите его.

### 4. Сертификат

```bash
sudo mkdir -p /var/www/certbot
sudo certbot --nginx -d ogneliss.ru -d www.ogneliss.ru
sudo nginx -t && sudo systemctl reload nginx
```

Certbot сам пропишет пути к сертификату. Обновление он ставит
в расписание — раз в 90 дней вручную ничего делать не нужно.

### 5. Проверить

```bash
curl -I https://ogneliss.ru/dialer/privacy
```

Должно быть `200 OK` и `content-type: text/html`.

---

## Что указать в Google Play Console

**Политика конфиденциальности:**

```
https://ogneliss.ru/dialer/privacy
```

Поле находится в разделе **Правила → Контент приложения → Политика
конфиденциальности**.

⚠️ **Адрес менять нельзя после публикации.** Он попадает в текст EULA
внутри установленного приложения, и у пользователей старых версий
останется ссылка на прежний путь. Поэтому адреса без расширения
и без версии в пути.

---

## При изменении документов

```bash
./docs/www/build-pages.sh
rsync -av --delete docs/www/dist/ user@ogneliss.ru:/var/www/ogneliss/
```

Страницы собираются из тех же `legal/*.md`, что идут в приложение, —
разойтись они не могут. Ручная правка HTML на сервере это свойство
сломает: следующая сборка её затрёт.

---

## Про кэширование

Документы кэшируются на **час**, а не на сутки. При изменении редакции
посетитель не должен неделю видеть старую — а час достаточно, чтобы
снять нагрузку от повторных заходов.

Стили и картинки кэшируются на 30 дней: они меняются редко.

## Главная ogneliss.ru

Страница-витрина приложений лежит файлом `home.html` и выкладывается
вместе со страницами Dialer (`build-pages.sh` её копирует). В nginx —
блоки из `nginx-landing.conf`: они заменяют прежний `location /`
со страницей внутри `return 200 '...'`.

Страницу внутри конфигурации держать больше нельзя: у nginx предел
длины параметра около 4 КБ, и страница с карточками приложений в него
не помещается — nginx отказывается загружать конфигурацию.

Порядок выкладки:

1. `./build-pages.sh` — собрать и выложить статику, включая `home.html`
   и `app-icon.png`.
2. Заменить в конфигурации nginx блоки Dialer и прежний `location /`
   содержимым `nginx-landing.conf`.
3. `nginx -t` — проверить; только потом `nginx -s reload`.

Логотип на главной — те же пути, что в `logo.svg`. Если меняется логотип,
пересобрать страницу: `python3 build-landing.py`.
