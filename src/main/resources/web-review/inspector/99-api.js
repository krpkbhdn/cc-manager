/* Module 99: style preview, public W.__ccm and page lifecycle. Runs last so every module has registered its api. */
var safe = C.safe, send = C.send, schedule = C.schedule;
var preview = new Map();  // element -> {prop: {value, priority}}

function toKebab(p) { return /[A-Z]/.test(p) ? p.replace(/[A-Z]/g, function (c) { return '-' + c.toLowerCase(); }) : p; }
C.api('setOptions', C.setOptions);
C.api('previewStyle', function (spec) {
  var el = spec && C.resolveSelector(spec.selector);
  if (!el) return false;
  var saved = preview.get(el) || {};
  Object.keys(spec.props || {}).forEach(function (k) {
    var prop = toKebab(k);
    if (!(prop in saved)) saved[prop] = { value: el.style.getPropertyValue(prop), priority: el.style.getPropertyPriority(prop) };
    el.style.setProperty(prop, String(spec.props[k]), 'important');
  });
  preview.set(el, saved);
  schedule();
  return true;
});
C.api('clearPreview', function () {
  preview.forEach(function (saved, el) {
    Object.keys(saved).forEach(function (prop) {
      if (saved[prop].value) el.style.setProperty(prop, saved[prop].value, saved[prop].priority);
      else el.style.removeProperty(prop);
    });
  });
  preview.clear();
  schedule();
});
C.api('describeAt', function (x, y) {
  var el = C.deepElementAt(x, y);
  return el ? C.describe(el) : null;
});
C.api('stats', function () { return JSON.parse(JSON.stringify(C.stats)); });
C.api('resolveSelector', C.resolveSelector);   // additive: lets the host/tests resolve a selector string

var FALLBACKS = { focusAnnotation: false, previewStyle: false, describeAt: null, stats: null, resolveSelector: null };
W.__ccm = { version: C.VERSION };
Object.keys(C.apis).forEach(function (name) {
  W.__ccm[name] = safe(C.apis[name], FALLBACKS[name]);
});

// ---------- page lifecycle ----------
var debounce = 0;
// force: navigation or load, where every pin is resolved again; a mutation only retries lost pins with backoff
function reanchor(force) { (C.reanchor || schedule)(force === true); }
function redraw() { schedule(); }
function reanchorLater() {
  clearTimeout(debounce);
  debounce = setTimeout(safe(function () { reanchor(false); }), 150);
}
new MutationObserver(reanchorLater).observe(document, { childList: true, subtree: true, attributes: true, characterData: true });
W.addEventListener('scroll', safe(redraw), { capture: true, passive: true });
W.addEventListener('resize', safe(redraw), { passive: true });
W.addEventListener('transitionend', safe(reanchorLater), true);

// Holding Space pans the page; without this the browser would also page-scroll. The page still sees the event.
var TEXT_TARGET = 'input,textarea,select,button,a[href],[contenteditable],[role=textbox]';
W.addEventListener('keydown', safe(function (e) {
  if (!e.isTrusted || (e.key !== ' ' && e.code !== 'Space')) return;
  var t = document.activeElement && document.activeElement.closest ? document.activeElement : e.target;
  var deep = e.composedPath ? e.composedPath()[0] : e.target;
  if ([t, deep].some(function (n) { return n && n.closest && n.closest(TEXT_TARGET); })) return;
  e.preventDefault();
}), { capture: true, passive: false });

function navigated() { C.state.chosenSimilar = null; send({ type: 'navigated', url: location.href, title: document.title }); reanchor(true); }
var navLater = function () { setTimeout(safe(navigated), 0); };
['pushState', 'replaceState'].forEach(function (m) {
  var orig = history[m];
  history[m] = function () { var r = orig.apply(this, arguments); navLater(); return r; };
});
W.addEventListener('popstate', navLater);
W.addEventListener('hashchange', navLater);
if (document.readyState !== 'complete') W.addEventListener('load', navLater);

C.mount();
send({ type: 'ready', url: location.href, title: document.title });
