# i. установщик по

Графическая утилита для Windows на Java (Swing), которая ставит выбранные программы
через **winget** одной кнопкой. Список программ с галочками, поиск, фильтр по категориям,
иконки, тёмная/светлая тема, тихая установка, прогресс-бар и понятный журнал.

## Скачать

* **Windows (EXE, Java уже внутри):**
  [i-ustanovshchik-po-1.0.0-windows-x64.zip](https://github.com/Doinwor/i-ustanovshchik-po/releases/latest/download/i-ustanovshchik-po-1.0.0-windows-x64.zip)
  — распаковать и запустить `i-ustanovshchik-po.exe`, устанавливать Java не нужно.
* **Портативный JAR (нужен Java 21):**
  [installer.jar](https://github.com/Doinwor/i-ustanovshchik-po/releases/latest/download/installer.jar)
  — запуск командой `java -jar installer.jar`.
* **Сайт программы:** https://doinwor.github.io/i-ustanovshchik-po/

Связаться с автором: **olehmudruk@yanex.by**

## Требования

* Windows 10/11 с установленным `winget` (приложение **App Installer** из Microsoft Store).
* Для EXE-сборки ничего дополнительно не нужно — Java встроена.
* Для портативного `installer.jar` — JRE 21. Для сборки из исходников — JDK 21.

## Сборка jar

```
build.bat
```

Скрипт ищет Java в таком порядке: `JAVA_HOME` → `%LOCALAPPDATA%\jdk-21\jdk-*` → `PATH`.
Результат: `dist\installer.jar` и `dist\apps.json`.

## Запуск

```
run.bat
```

или

```
java -jar dist\installer.jar
```

Файл `apps.json` ищется в текущей папке, на уровень выше и рядом с jar-файлом.

## Сборка EXE

Из готового jar собирается нативное приложение `i-ustanovshchik-po.exe` со встроенной
Java (`jpackage` из JDK 21, плюс `jlink` для компактного рантайма):

```
jlink --add-modules java.base,java.desktop,java.prefs,java.logging,java.datatransfer,java.xml,jdk.unsupported ^
      --strip-debug --no-header-files --no-man-pages --output build\runtime

jpackage --type app-image --name i-ustanovshchik-po --input build\pkg-input ^
      --main-jar installer.jar --main-class installer.Main --app-version 1.0.0 ^
      --vendor "studio i." --runtime-image build\runtime --dest build\exe
```

Готовый комплект лежит в `build\exe\i-ustanovshchik-po\` и запускается файлом
`i-ustanovshchik-po.exe`.

## Как пользоваться

1. Отметьте галочками нужные программы (помогут поиск и фильтр по категории).
2. Нажмите **«Установить выбранное»**.
3. Следите за журналом и прогресс-баром. Статус каждой программы виден в таблице.
4. По завершении появится окно с итогом. Журнал дублируется в `installer.log`.

Кнопка **«Отмена»** останавливает установку после текущего пакета.

### Поиск и категории

Рядом со строкой поиска есть выпадающий список категорий. Фильтры работают вместе:
можно выбрать «Мультимедиа» и ввести `vlc` — останется только VLC. Поиск ищет по названию,
winget ID, категории и описанию.

Кнопки **«Выбрать все»** / **«Снять все»** действуют по видимым строкам: если включён
фильтр, отметятся только показанные программы, иначе — все.

### Настройки

Кнопка **«Настройки»** в правом верхнем углу открывает окно с двумя вкладками:

* **Темы** — светлая и тёмная тема. По умолчанию включена тёмная; выбранная тема
  применяется сразу и запоминается между запусками.
* **О программе** — название, версия, автор, число программ и категорий, версия `winget`
  и путь к файлу со списком.

## Как расширить список программ

Откройте `apps.json` и добавьте объект:

```json
{
  "id": "Mozilla.Firefox",
  "name": "Mozilla Firefox",
  "category": "Браузеры",
  "description": "Открытый браузер.",
  "domain": "mozilla.org"
}
```

`id` — это точный идентификатор пакета. Найти его можно так:

```
winget search название_программы
```

Поле `category` используется только для отображения и поиска.

### Иконки программ

У каждой программы в списке есть иконка. Порядок получения:

1. `icons\<id>.png` рядом с `apps.json` — если файл есть, берётся он (можно положить свою картинку).
2. Поле `"icon"` — прямая ссылка (`https://…`) или путь к файлу с иконкой.
3. Поле `"domain"` — favicon сайта (пробуются Google и Яндекс).
4. Если ничего не удалось — рисуется цветной квадрат с первой буквой названия (работает без интернета).

Скачанные иконки кэшируются в папку `icons`, поэтому повторные запуски работают без сети.

## Команда установки

Для каждой программы выполняется:

```
winget install --id <ID> -e --source winget --accept-package-agreements --accept-source-agreements --disable-interactivity [--silent]
```

Флажок **«Тихая установка»** добавляет `--silent`.

> `--source winget` указан намеренно. Без него winget опрашивает ещё и источник
> `msstore`, который часто падает с ошибкой сертификата (`0x8A15005E`), после чего
> установка срывается с сообщением «Please specify one of them using the --source option».

Если пакет уже установлен, winget возвращает ненулевой код — приложение распознаёт это
и показывает статус **«Уже установлено»** вместо ошибки.

## Журнал

Вывод winget разбирается с учётом символа возврата каретки (`\r`), которым winget
перерисовывает строку прогресса на месте. Поэтому «крутящаяся палка» и полосы загрузки
не засоряют журнал: в него попадают только осмысленные строки, а прогресс показывается
отдельной подписью под прогресс-баром.

## Сайт

Каталог сайта публикуется через **GitHub Pages** из папки `docs/`:

* исходники страниц — `docs/index.html`, `docs/css/style.css`, `docs/js/app.js`;
* список программ для сайта — `docs/js/programs.js` (генерируется из `apps.json`);
* иконки — `docs/icons/`.

## Структура проекта

```
apps.json                       список программ
icons/                          кэш иконок программ
boykisser-spin.gif              анимация рядом с прогресс-баром
build.bat / run.bat             сборка и запуск
lib/flatlaf-3.5.4.jar           современное оформление
src/installer/Main.java         точка входа
src/installer/MainWindow.java   главное окно
src/installer/AppEntry.java     модель программы
src/installer/AppStore.java     чтение apps.json
src/installer/AppIcons.java     иконки программ (загрузка и кэш)
src/installer/ProcessOutput.java разбор вывода winget (\r, ANSI)
src/installer/Winget.java       вызовы winget
src/installer/GifSpinner.java   анимация GIF в интерфейсе
src/installer/Json.java         парсер JSON без внешних зависимостей
docs/                           сайт для GitHub Pages
```
