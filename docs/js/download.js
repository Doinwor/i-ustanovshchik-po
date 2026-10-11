/* ============================================================
   i. установщик по — скачивание через капчу
   Перехватывает кнопки загрузки, показывает капчу студии
   (doinwor.github.io/captcha) и после успешного прохождения
   автоматически запускает скачивание файла.
   ============================================================ */
(function () {
  "use strict";

  var CAP_ORIGIN = "https://doinwor.github.io";
  var CAP_URL = CAP_ORIGIN + "/captcha/";

  var modal = document.getElementById("capModal");
  var frame = document.getElementById("capFrame");
  var closeBtn = document.getElementById("capClose");
  if (!modal || !frame) return;

  var pendingUrl = null;

  function askHeight() {
    if (frame.contentWindow) {
      frame.contentWindow.postMessage({ type: "captcha-height-request" }, CAP_ORIGIN);
    }
  }

  function openCaptcha(url) {
    pendingUrl = url;
    modal.hidden = false;
    document.documentElement.classList.add("cap-open");
    /* уникальный запрос — сбрасываем состояние капчи на каждый показ */
    frame.setAttribute("src", CAP_URL + "?t=" + Date.now());
    if (closeBtn) closeBtn.focus();
  }

  function closeCaptcha() {
    modal.hidden = true;
    document.documentElement.classList.remove("cap-open");
    pendingUrl = null;
  }

  function startDownload(url) {
    var a = document.createElement("a");
    a.href = url;
    a.rel = "noopener";
    a.style.display = "none";
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  /* Перехват кнопки загрузки (капча перед скачиванием) */
  var links = document.querySelectorAll("a[data-captcha-download]");
  Array.prototype.forEach.call(links, function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      openCaptcha(link.href);
    });
  });

  if (closeBtn) closeBtn.addEventListener("click", closeCaptcha);

  modal.addEventListener("click", function (e) {
    if (e.target === modal) closeCaptcha();
  });

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !modal.hidden) closeCaptcha();
  });

  frame.addEventListener("load", function () {
    var src = frame.getAttribute("src") || "";
    if (src.indexOf("/captcha/") >= 0) askHeight();
  });

  window.addEventListener("message", function (e) {
    if (e.origin !== CAP_ORIGIN) return;
    var d = e.data || {};

    if (d.type === "captcha-resize" && typeof d.height === "number") {
      frame.style.height = Math.max(120, d.height) + "px";
    }

    if (d.type === "captcha-success" && d.source === "i-studio") {
      var url = pendingUrl;
      closeCaptcha();
      if (url) startDownload(url);
    }
  });
})();
