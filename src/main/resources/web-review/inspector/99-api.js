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
// Used while Kotlin does not forward real input (page locked), so no :hover fires; kind is validated here because the page can call __ccm too.
C.api('pointer', function (kind, x, y) {
  x = Number(x); y = Number(y);
  if (!isFinite(x) || !isFinite(y)) return;
  if (kind === 'leave') { C.pinPointer('leave', x, y); C.state.hoverEl = null; schedule(); }
  else if (kind === 'move') { C.pinPointer('move', x, y); if (C.tracksHover()) C.updateHover(x, y); }
  else if (kind === 'click') { if (!C.pinPointer('click', x, y) && C.opts.pick) C.pickAt(x, y, null); }
});
var MAX_SCROLL_STEP = 1e6;
function canScroll(overflow, delta, size, extent, pos) {
  return delta !== 0 && /auto|scroll|overlay/.test(overflow) && extent > size + 1 && (delta > 0 ? pos + size < extent - 1 : pos > 0);
}
// A clipped (overflow hidden) axis is not user-scrollable even though script can move it: only the axes a wheel could move are applied.
function wheelDeltas(el, dx, dy) {
  var s = getComputedStyle(el);
  return {
    left: canScroll(s.overflowX, dx, el.clientWidth, el.scrollWidth, el.scrollLeft) ? dx : 0,
    top: canScroll(s.overflowY, dy, el.clientHeight, el.scrollHeight, el.scrollTop) ? dy : 0,
  };
}
C.api('scrollBy', function (dx, dy, x, y) {
  var clamp = function (v) { v = Number(v); return isFinite(v) ? Math.max(-MAX_SCROLL_STEP, Math.min(MAX_SCROLL_STEP, v)) : 0; };
  dx = clamp(dx); dy = clamp(dy);
  var el = isFinite(Number(x)) && isFinite(Number(y)) ? C.deepElementAt(Number(x), Number(y)) : null;
  for (; el && el !== document.body && el !== document.documentElement; el = el.assignedSlot || el.parentElement || (el.getRootNode && el.getRootNode().host)) {
    var d = wheelDeltas(el, dx, dy);
    if (d.left !== 0 || d.top !== 0) { el.scrollBy({ left: d.left, top: d.top, behavior: 'instant' }); return; }
  }
  var root = getComputedStyle(document.documentElement).overflowX;
  if (root === 'visible' && document.body) root = getComputedStyle(document.body).overflowX;
  W.scrollBy({ left: /hidden|clip/.test(root) ? 0 : dx, top: dy, behavior: 'instant' });   // a clipped axis is not user-scrollable
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
// Space keeps its broader notion: buttons and links must still receive it.
var SPACE_TARGET = 'input,textarea,select,button,a[href],[contenteditable],[role=textbox]';
W.addEventListener('keydown', safe(function (e) {
  if (!e.isTrusted || (e.key !== ' ' && e.code !== 'Space')) return;
  var t = document.activeElement && document.activeElement.closest ? document.activeElement : e.target;
  var deep = e.composedPath ? e.composedPath()[0] : e.target;
  if ([t, deep].some(function (n) { return n && n.closest && n.closest(SPACE_TARGET); })) return;
  e.preventDefault();
}), { capture: true, passive: false });

// Hotkeys die only while the user really types: text inputs, textarea, contenteditable, textbox-like roles; buttons, links and select do not count.
var TEXT_INPUT_TYPE = /^(text|search|email|url|tel|password|number|date|datetime-local|month|week|time)$/;
var TEXT_ROLE = '[contenteditable]:not([contenteditable=false]),[role=textbox],[role=searchbox],[role=combobox]';
function isTextEntry(n) {
  if (!n || !n.closest) return false;
  if (n.tagName === 'INPUT') return TEXT_INPUT_TYPE.test(n.type);
  return n.tagName === 'TEXTAREA' || !!n.closest(TEXT_ROLE);
}
var EDITING_MIN_GAP_MS = 50;   // at most 20 messages a second
var editing = false, sentEditing = false, editingSentAt = 0, editingTimer = 0;
function emitEditing() {
  editingSentAt = Date.now();
  if (editing !== sentEditing) { sentEditing = editing; send({ type: 'editing', on: editing }); }
}
function setEditing(on) {
  editing = on;
  var wait = editingSentAt + EDITING_MIN_GAP_MS - Date.now();
  if (wait <= 0) emitEditing();
  else if (!editingTimer) editingTimer = setTimeout(safe(function () { editingTimer = 0; emitEditing(); }), wait);
}
W.addEventListener('focusin', safe(function (e) {
  setEditing(isTextEntry(e.composedPath ? e.composedPath()[0] : e.target));
}), true);
W.addEventListener('focusout', safe(function () { setEditing(false); }), true);

function navigated() { setEditing(false); C.state.chosenSimilar = null; send({ type: 'navigated', url: location.href, title: document.title }); reanchor(true); }
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
