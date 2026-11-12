/* Module 10: hover, pick mode, box model overlay and the element tip. */
var safe = C.safe, send = C.send, opts = C.opts, state = C.state, host = C.host;
var describe = C.describe, deepElementAt = C.deepElementAt, sides = C.sides, tip = C.tip, schedule = C.schedule;
var MAX_DRAWN = 3000, MAX_SCANNED = C.MAX_VISITED * 4;
var HOVER_SEND_MS = 50;
var lastHoverSent = 0, lastHoverEl = null, hoverTimer = 0;

function collectElements(root, out) {
  var list = root.querySelectorAll('*');
  for (var i = 0; i < list.length && out.scanned < MAX_SCANNED && out.visited < C.MAX_VISITED && out.els.length < MAX_DRAWN; i++) {
    var el = list[i];
    out.scanned++;
    if (el === host) continue;
    var r = el.getBoundingClientRect();
    // only elements in the viewport use the visit budget, so a deep scrolled page still draws what is visible
    if (r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < W.innerHeight && r.left < W.innerWidth) { out.visited++; out.els.push([el, r]); }
    if (el.shadowRoot) collectElements(el.shadowRoot, out);
  }
}
// One hue per nesting depth, cycling; outlines only, so the page keeps its own colours.
var DEPTH_HUES = [210, 12, 150, 275, 40, 330, 180, 90, 245, 25];
var LINE_ALPHA = 0.9;
function depthOf(el, cache) {
  var hit = cache.get(el);
  if (hit !== undefined) return hit;
  var parent = el.parentNode || el.host, d = parent ? depthOf(parent, cache) + 1 : 0;
  cache.set(el, d);
  return d;
}
C.layer('all', function (ctx) {
  if (!opts.showAll) return;
  var out = { els: [], visited: 0, scanned: 0 }, depths = new WeakMap();
  collectElements(document, out);
  var line = 1 / (W.devicePixelRatio || 1), byHue = {};
  out.els.forEach(function (e) { var h = DEPTH_HUES[depthOf(e[0], depths) % DEPTH_HUES.length]; (byHue[h] || (byHue[h] = [])).push(e); });
  ctx.lineWidth = line;
  Object.keys(byHue).forEach(function (h) {
    ctx.beginPath();
    byHue[h].forEach(function (e) { C.elementBoxPath(ctx, e[0], e[1], -line / 2); });
    ctx.strokeStyle = 'hsla(' + h + ',75%,45%,' + LINE_ALPHA + ')';
    ctx.stroke();
  });
  if (opts.showLabels) {
    ctx.font = '10px ui-monospace,Consolas,monospace';
    ctx.fillStyle = 'rgba(190,24,93,.9)';
    var drawn = 0;
    for (var i = 0; i < out.els.length && drawn < 300; i++) {
      var r = out.els[i][1];
      if (r.width > 50 && r.height > 14) { ctx.fillText(out.els[i][0].localName, r.left + 2, r.top + 10); drawn++; }
    }
  }
});

var RING_ALPHA = 0.35;
function ring(ctx, el, r, outer, inner, color) {
  ctx.fillStyle = color;
  ctx.beginPath();
  C.elementBoxPath(ctx, el, r, outer);
  C.elementBoxPath(ctx, el, r, inner);
  ctx.fill('evenodd');
}
function neg(s) { return { t: -s.t, r: -s.r, b: -s.b, l: -s.l }; }
function plus(a, b) { return { t: a.t + b.t, r: a.r + b.r, b: a.b + b.b, l: a.l + b.l }; }
function labelOf(el, r) {
  var cls = Array.prototype.slice.call(el.classList || [], 0, 3).join('.');
  return el.localName + (cls ? '.' + cls : '') + ' ' + Math.round(r.width) + 'x' + Math.round(r.height);
}
C.tipLine(function (el, r) { return opts.pick ? labelOf(el, r) : null; });
// Labels without pick mode are drawn on the canvas, so they work with or without the box rings.
function drawLabel(ctx, el, r) {
  ctx.font = '11px ui-monospace,Consolas,monospace';
  var text = labelOf(el, r), w = ctx.measureText(text).width + 8, y = r.top - 18 < 2 ? r.bottom + 2 : r.top - 18;
  var x = Math.max(2, Math.min(r.left, W.innerWidth - w - 2));
  ctx.fillStyle = '#1e3a8a';
  ctx.fillRect(x, y, w, 16);
  ctx.fillStyle = '#fff';
  ctx.fillText(text, x + 4, y + 12);
}
C.layer('hover', function (ctx) {
  tip.style.display = 'none';
  var el = state.hoverEl;
  if (!el || !el.isConnected || !(opts.pick || opts.showBox || opts.showLabels)) return;
  var r = el.getBoundingClientRect();
  if (opts.showBox) {
    var cs = getComputedStyle(el);
    var m = sides(cs, 'margin', ''), b = neg(sides(cs, 'border', '-width')), p = neg(sides(cs, 'padding', '')), zero = 0;
    var padBox = b, content = plus(b, p);
    ring(ctx, el, r, m, zero, 'rgba(246,178,107,' + RING_ALPHA + ')');
    ring(ctx, el, r, zero, padBox, 'rgba(255,229,153,' + RING_ALPHA + ')');
    ring(ctx, el, r, padBox, content, 'rgba(147,196,125,' + RING_ALPHA + ')');
    ctx.fillStyle = 'rgba(111,168,220,' + RING_ALPHA + ')';
    ctx.beginPath();
    C.elementBoxPath(ctx, el, r, content);
    ctx.fill();
  } else if (opts.pick) {
    ctx.fillStyle = 'rgba(37,99,235,.2)';
    ctx.beginPath();
    C.elementBoxPath(ctx, el, r, 0);
    ctx.fill();
  }
  ctx.strokeStyle = '#2563eb';
  ctx.lineWidth = 1;
  ctx.beginPath();
  C.elementBoxPath(ctx, el, r, -.5);
  ctx.stroke();
  if (opts.showLabels && !opts.pick) drawLabel(ctx, el, r);
  var lines = C.tipFns.map(function (fn) { return safe(fn, null)(el, r); }).filter(Boolean);
  if (lines.length) {
    tip.textContent = lines.join(' ');
    tip.style.display = 'block';
    var ty = r.top - 22;
    tip.style.top = (ty < 2 ? Math.min(W.innerHeight - 22, r.bottom + 4) : ty) + 'px';
    tip.style.left = Math.max(2, Math.min(r.left, W.innerWidth - 200)) + 'px';
  }
});

// ---------- pick mode ----------
var cursorSheet = null;
function setCursor(on) {
  try {
    if (!cursorSheet) { cursorSheet = new CSSStyleSheet(); cursorSheet.replaceSync('html,html *{cursor:crosshair!important}'); }
    var cur = document.adoptedStyleSheets, has = cur.indexOf(cursorSheet) >= 0;
    if (on && !has) document.adoptedStyleSheets = cur.concat([cursorSheet]);
    else if (!on && has) document.adoptedStyleSheets = cur.filter(function (s) { return s !== cursorSheet; });
  } catch (e) { /* old engine: no crosshair */ }
}
function fromUs(e) {
  var path = e.composedPath ? e.composedPath() : [];
  return path.indexOf(host) >= 0;
}
// The full descriptor (computed styles, selector) is sent once per hovered element, not per mouse move; a trailing timer covers the throttle.
function sendHover() {
  hoverTimer = 0;
  var el = state.hoverEl;
  if (!opts.pick || !el || !el.isConnected || el === lastHoverEl) return;
  var wait = HOVER_SEND_MS - (Date.now() - lastHoverSent);
  if (wait > 0) { hoverTimer = setTimeout(safe(sendHover), wait); return; }
  lastHoverSent = Date.now(); lastHoverEl = el;
  send({ type: 'hover', target: describe(el) });
}
function tracksHover() { return opts.pick || opts.showBox || opts.showLabels || opts.similar; }
function updateHover(x, y) {
  var el = deepElementAt(x, y);
  if (el !== state.hoverEl) { state.hoverEl = el; schedule('hover'); schedule('similar'); }
  if (opts.pick && !hoverTimer) sendHover();
}
var SWALLOWED = ['mousedown', 'mouseup', 'click', 'dblclick', 'pointerdown', 'pointerup', 'touchstart', 'touchend', 'contextmenu'];
// Moves and enters are swallowed in pick mode only so page hover menus do not fire; they never pick.
var SWALLOWED_MOVES = ['pointermove', 'mouseover', 'pointerover', 'pointerenter', 'mouseenter'];
function swallow(e) {
  e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
}
var onMove = safe(function (e) {
  if (!e.isTrusted) return;
  if (!tracksHover()) return;
  if (opts.pick && fromUs(e)) return;
  if (opts.pick) swallow(e);
  updateHover(e.clientX, e.clientY);
});
var onSwallowed = safe(function (e) {
  if (!e.isTrusted || !opts.pick || fromUs(e)) return;
  swallow(e);
  var t = e.type;
  if (t === 'click' || t === 'touchstart') {
    var pt = e.touches ? e.touches[0] : e;
    var el = pt ? deepElementAt(pt.clientX, pt.clientY) : null;
    if (!el && e.composedPath) el = e.composedPath()[0];
    state.chosenSimilar = null;
    C.schedule('similar');
    if (el && el.nodeType === 1) send({ type: 'pick', target: describe(el) });
  }
});
var onKey = safe(function (e) {
  if (e.isTrusted && opts.pick && e.key === 'Escape') {
    swallow(e);
    C.setOptions({ pick: false });
    send({ type: 'pickCancel' });
  }
});
var cap = { capture: true, passive: false };
W.addEventListener('mousemove', onMove, cap);
SWALLOWED.concat(SWALLOWED_MOVES).forEach(function (t) { W.addEventListener(t, onSwallowed, cap); });
W.addEventListener('keydown', onKey, cap);
document.addEventListener('mouseleave', safe(function () { state.hoverEl = null; schedule(); }), true);
C.onOptions(function () {
  setCursor(opts.pick);
  if (!tracksHover()) state.hoverEl = null;
  if (!opts.pick) lastHoverEl = null;
});
