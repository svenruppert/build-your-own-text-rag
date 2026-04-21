// Intercepts in-page anchor clicks (href starting with "#") inside the
// Glossary view, scrolls the target entry into view smoothly, and
// flashes it with the .glossary-highlight class for two seconds.
//
// Idempotent: the hook binds once per page load via the
// window.__glossaryAnchorsBound flag. Loaded by GlossaryView through
// UI.getCurrent().getPage().addJavaScript() on view attach; a second
// attach (view re-enter) is a no-op.
(function () {
  if (window.__glossaryAnchorsBound) return;
  window.__glossaryAnchorsBound = true;

  document.addEventListener('click', function (e) {
    var target = e.target;
    while (target && target !== document && target.tagName !== 'A') {
      target = target.parentNode;
    }
    if (!target || target.tagName !== 'A') return;

    var href = target.getAttribute('href');
    if (!href || href.charAt(0) !== '#' || href.length < 2) return;

    var id = href.slice(1);
    var el = document.getElementById(id);
    if (!el) return;

    e.preventDefault();
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    el.classList.add('glossary-highlight');
    setTimeout(function () {
      el.classList.remove('glossary-highlight');
    }, 2000);
  });
})();
