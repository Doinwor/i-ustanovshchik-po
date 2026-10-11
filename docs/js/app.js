/* ============================================================
   i. установщик по — лендинг
   Логика повторяет настольную программу: тема запоминается,
   список программ берётся из programs.js, а иконки программ
   при отсутствии заменяются буквенным аватаром (как в
   AppIcons.java: цвет по String.hashCode, первая буква названия).
   ============================================================ */
(function () {
  "use strict";

  var ALL = "Все";
  var PREF_KEY = "installer.theme";

  /* Палитра аватаров — как в AppIcons.java */
  var PALETTE = [
    "#2D7FF9", "#34A853", "#E8710A", "#D93025",
    "#8E24AA", "#00A0B0", "#F09300", "#5F6DE8"
  ];

  var programs = (typeof PROGRAMS !== "undefined" && PROGRAMS) ? PROGRAMS : [];
  var $ = function (id) { return document.getElementById(id); };

  /* ---------- Аватар и иконки (стиль AppIcons.java) ---------- */

  function javaHashCode(str) {
    var h = 0;
    for (var i = 0; i < str.length; i++) {
      h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
    }
    return h;
  }

  function floorMod(a, b) { return ((a % b) + b) % b; }

  function firstLetter(name) {
    for (var i = 0; i < name.length; i++) {
      var ch = name.charAt(i);
      if (/[\p{L}\p{N}]/u.test(ch)) return ch.toUpperCase();
    }
    return "?";
  }

  function iconFilename(id) {
    return id.replace(/[^A-Za-z0-9._-]/g, "_") + ".png";
  }

  function makeIcon(app) {
    var img = document.createElement("img");
    img.alt = "";
    img.loading = "lazy";
    img.decoding = "async";
    img.src = "icons/" + iconFilename(app.id);
    img.addEventListener("error", function () {
      var avatar = document.createElement("span");
      avatar.className = "avatar";
      avatar.style.background = PALETTE[floorMod(javaHashCode(app.id), PALETTE.length)];
      avatar.textContent = firstLetter(app.name);
      img.replaceWith(avatar);
    }, { once: true });
    return img;
  }

  /* ---------- Тема ---------- */

  function preferredDark() {
    try {
      var saved = localStorage.getItem(PREF_KEY);
      if (saved === null) return true;      // по умолчанию — тёмная (как в программе)
      return saved !== "light";
    } catch (e) {
      return true;
    }
  }

  function applyTheme(theme, persist) {
    document.documentElement.setAttribute("data-theme", theme);
    var icon = $("themeIcon");
    if (icon) icon.textContent = theme === "dark" ? "☀" : "🌙";
    var btn = $("themeToggle");
    if (btn) btn.title = theme === "dark" ? "Светлая тема" : "Тёмная тема";
    if (persist) {
      try { localStorage.setItem(PREF_KEY, theme); } catch (e) { /* нет хранилища — не критично */ }
    }
  }

  /* ---------- Список программ ---------- */

  function categories() {
    var set = {};
    programs.forEach(function (app) {
      if (app.category) set[app.category] = true;
    });
    return Object.keys(set).sort();   // порядок как у TreeSet в программе
  }

  var activeCategory = ALL;

  function matches(app, text) {
    if (activeCategory !== ALL && app.category !== activeCategory) return false;
    if (!text) return true;
    var t = text.toLowerCase();
    return (app.name || "").toLowerCase().indexOf(t) >= 0
      || (app.id || "").toLowerCase().indexOf(t) >= 0
      || (app.category || "").toLowerCase().indexOf(t) >= 0
      || (app.description || "").toLowerCase().indexOf(t) >= 0;
  }

  function buildChips(list) {
    var box = $("catalogChips");
    if (!box) return;
    box.innerHTML = "";
    [ALL].concat(list).forEach(function (name) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "chip" + (name === activeCategory ? " active" : "");
      b.textContent = name;
      b.addEventListener("click", function () {
        activeCategory = name;
        buildChips(list);
        renderCatalog();
      });
      box.appendChild(b);
    });
  }

  function renderCatalog() {
    var grid = $("programsGrid");
    var empty = $("catalogEmpty");
    if (!grid) return;

    var input = $("catalogSearch");
    var text = input ? input.value.trim() : "";
    var visible = programs.filter(function (app) { return matches(app, text); });

    grid.innerHTML = "";
    visible.forEach(function (app) {
      var card = document.createElement("div");
      card.className = "program";
      card.title = app.description || app.name;

      card.appendChild(makeIcon(app));

      var info = document.createElement("div");
      info.className = "p-info";

      var name = document.createElement("span");
      name.className = "p-name";
      name.textContent = app.name;

      var cat = document.createElement("span");
      cat.className = "p-cat";
      cat.textContent = app.category || "Без категории";

      info.appendChild(name);
      info.appendChild(cat);
      card.appendChild(info);
      grid.appendChild(card);
    });

    if (empty) empty.classList.toggle("hidden", visible.length > 0);
  }

  /* ---------- Инициализация ---------- */

  function init() {
    applyTheme(preferredDark() ? "dark" : "light", false);

    var cats = categories();

    // Цифры в hero и подписи
    var statPrograms = $("statPrograms");
    var statCategories = $("statCategories");
    if (statPrograms) statPrograms.textContent = programs.length;
    if (statCategories) statCategories.textContent = cats.length;

    var lead = $("programsLead");
    if (lead) {
      lead.textContent = programs.length + " программ в " + cats.length + " категориях — от браузеров до системных компонентов.";
    }

    var year = $("footerYear");
    if (year) year.textContent = new Date().getFullYear();

    buildChips(cats);
    renderCatalog();

    var input = $("catalogSearch");
    if (input) {
      input.addEventListener("input", renderCatalog);
    }

    var toggle = $("themeToggle");
    if (toggle) {
      toggle.addEventListener("click", function () {
        var current = document.documentElement.getAttribute("data-theme");
        applyTheme(current === "dark" ? "light" : "dark", true);
      });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
