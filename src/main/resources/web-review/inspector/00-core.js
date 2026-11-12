/* Claude Code Manager web-review inspector, module 00: core. Shared closure object C (see InspectorBridge.bootstrapScript). */
var VERSION = '1.1.0';
var HOST_ID = '__ccm-root';
var MAX_VISITED = 12000;

var MAX_ERROR_REPORTS = 10, REFILL_MS = 10000, errorBudget = MAX_ERROR_REPORTS, lastRefill = Date.now();
/** Wraps fn so it never throws; failures are reported to the host as {type:'moduleError', module, message}, rate limited (burst 10, one more per 10 s). */
function safe(fn, fallback, module) {
  return function () {
    try { return fn.apply(this, arguments); } catch (e) {
      var msg = String(e && e.message || e);
      // a page-supplied selector that does not parse is an expected miss, not a module bug
      var pageSelector = e && e.name === 'SyntaxError' && /selector/i.test(msg);
      var now = Date.now();
      errorBudget = Math.min(MAX_ERROR_REPORTS, errorBudget + Math.floor((now - lastRefill) / REFILL_MS));
      lastRefill = now - ((now - lastRefill) % REFILL_MS);
      if (errorBudget > 0 && !pageSelector) {
        errorBudget--;
        send({ type: 'moduleError', module: module || fn.name || 'runtime', message: msg.slice(0, 200) });
      }
      return fallback;
    }
  };
}

// ---------- host messaging ----------
var queue = [];
var MAX_SELECTOR = 1000;   // the host's limit; it would truncate a longer selector, which could name another element
function tooLong(s) { return typeof s === 'string' && s.length > MAX_SELECTOR; }
function hasLongSelector(msg) {
  var t = msg && msg.target, items = msg && msg.items || [];
  return !!(t && (tooLong(t.selector) || tooLong(t.xpath))) || items.some(function (i) { return tooLong(i.selector); });
}
function send(msg) {
  try {
    if (hasLongSelector(msg)) return;   // fail closed: no message beats one that points at another element
    if (typeof W.__ccmHost !== 'function') {
      if (queue.length < 50) queue.push(msg);
      return;
    }
    while (queue.length) W.__ccmHost(JSON.stringify(queue.shift()));
    W.__ccmHost(JSON.stringify(msg));
  } catch (e) { /* host must never break the page */ }
}

// ---------- state and registry ----------
// opts is a generic map: a module adds a toggle by listing a default in C.opts; setOptions keeps the type of the default.
var opts = { pick: false, showAll: false, showBox: false, showLabels: false, contrast: true };
var state = { hoverEl: null };
var layers = {};      // name -> {draw, canvas, raf}
var tipFns = [];      // fn(el, rect) -> string | null
var optionFns = [];   // fn(opts)
var apis = {};        // name -> fn, merged into W.__ccm by 99-api

// ---------- shadow root UI ----------
var host = document.createElement('div');
host.id = HOST_ID;
host.style.cssText = 'all:initial!important;position:fixed!important;left:0!important;top:0!important;' +
  'width:100vw!important;height:100vh!important;pointer-events:none!important;z-index:2147483647!important;' +
  'display:block!important;margin:0!important;padding:0!important;border:0!important;overflow:visible!important;';
var shadow = host.attachShadow({ mode: 'closed' });
var CSS_TEXT =
  ':host{all:initial}*{box-sizing:border-box}' +
  'canvas{position:fixed;left:0;top:0;pointer-events:none}' +
  '.pin{position:fixed;width:20px;height:20px;border-radius:50%;border:2px solid #fff;color:#fff;' +
  'font:600 11px/16px system-ui,sans-serif;text-align:center;pointer-events:auto;cursor:pointer;' +
  'box-shadow:0 1px 4px rgba(0,0,0,.5);user-select:none}' +
  '.pin.lost{border-style:dashed;opacity:.65}' +
  '.tip{position:fixed;display:none;pointer-events:none;background:#1f2937;color:#fff;padding:2px 6px;' +
  'border-radius:3px;font:11px/16px ui-monospace,Consolas,monospace;white-space:nowrap;max-width:60vw;overflow:hidden}' +
  '.flash{position:fixed;pointer-events:none;outline:3px solid #f43f5e;background:rgba(244,63,94,.2);animation:f 1.2s ease-out forwards}' +
  '@keyframes f{0%{opacity:1}100%{opacity:0}}';
try {
  var sheet = new CSSStyleSheet();
  sheet.replaceSync(CSS_TEXT);
  shadow.adoptedStyleSheets = [sheet];
} catch (e) {
  var st = document.createElement('style');
  st.textContent = CSS_TEXT;
  shadow.appendChild(st);
}
var tip = document.createElement('div');
tip.className = 'tip';
shadow.appendChild(tip);

function mount() {
  if (!host.isConnected && document.documentElement) document.documentElement.appendChild(host);
}

function sizeCanvas(c) {
  var dpr = W.devicePixelRatio || 1;
  var w = Math.round(W.innerWidth * dpr), h = Math.round(W.innerHeight * dpr);
  if (c.width !== w || c.height !== h) {
    c.width = w; c.height = h;
    c.style.width = W.innerWidth + 'px'; c.style.height = W.innerHeight + 'px';
  }
  var ctx = c.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, W.innerWidth, W.innerHeight);
  return ctx;
}

// A layer only owns a full-viewport canvas while it paints: draw runs once against a recorder, and only a draw that paints
// (an empty path does not) allocates the canvas and replays the recording onto it.
var RECORDED_PROPS = ['fillStyle', 'strokeStyle', 'lineWidth', 'font', 'textAlign', 'textBaseline', 'globalAlpha'];
var RECORDED_CALLS = ['setLineDash', 'beginPath', 'closePath', 'moveTo', 'lineTo', 'rect', 'arc', 'arcTo', 'ellipse', 'quadraticCurveTo', 'bezierCurveTo', 'roundRect', 'save', 'restore', 'translate', 'clip'];
var PATH_SHAPES = ['lineTo', 'rect', 'arc', 'arcTo', 'ellipse', 'quadraticCurveTo', 'bezierCurveTo', 'roundRect'];
var PAINT_CALLS = ['fillRect', 'strokeRect', 'fillText', 'strokeText', 'drawImage'];
var probe = null;
function recorder() {
  if (!probe) { probe = document.createElement('canvas'); probe.width = probe.height = 1; }
  var pc = probe.getContext('2d'), log = [], pathShapes = 0;
  var rec = { painted: false, ctx: { measureText: function (t) { return pc.measureText(t); } } };
  RECORDED_PROPS.forEach(function (p) {
    Object.defineProperty(rec.ctx, p, { get: function () { return pc[p]; }, set: function (v) { pc[p] = v; log.push([p, v]); } });
  });
  function call(m, paints) {
    rec.ctx[m] = function () {
      log.push([m, Array.prototype.slice.call(arguments)]);
      if (m === 'beginPath') pathShapes = 0;
      if (PATH_SHAPES.indexOf(m) >= 0) pathShapes++;
      if (paints && (paints === true || pathShapes > 0)) rec.painted = true;
    };
  }
  RECORDED_CALLS.forEach(function (m) { call(m, false); });
  PAINT_CALLS.forEach(function (m) { call(m, true); });
  ['fill', 'stroke'].forEach(function (m) { call(m, 'path'); });
  rec.replay = function (ctx) {
    log.forEach(function (e) { if (typeof ctx[e[0]] === 'function') ctx[e[0]].apply(ctx, e[1]); else ctx[e[0]] = e[1]; });
  };
  return rec;
}
function releaseCanvas(l) {
  if (!l.canvas) return;
  l.canvas.width = l.canvas.height = 0;
  l.canvas.remove();
  l.canvas = null;
}
function attachCanvas(name, l) {
  l.canvas = document.createElement('canvas');
  var names = Object.keys(layers), next = null;
  for (var i = names.indexOf(name) + 1; i < names.length && !next; i++) next = layers[names[i]].canvas;
  shadow.insertBefore(l.canvas, next || tip);
}
function paint(name, l) {
  var rec = recorder();
  l.draw(rec.ctx);
  if (!rec.painted) return releaseCanvas(l);
  if (!l.canvas) attachCanvas(name, l);
  rec.replay(sizeCanvas(l.canvas));
}

// ---------- registry ----------
/** A layer is redrawn with drawFn(ctx) by schedule(name); its canvas exists only while drawFn paints. noCanvas gives drawFn(null). */
function layer(name, drawFn, noCanvas) {
  layers[name] = { draw: drawFn, canvas: null, raf: 0, bare: !!noCanvas };
}
var stats = {};       // layer -> {draws, ms}, read through __ccm.stats()
function schedule(name) {
  Object.keys(layers).forEach(function (n) {
    var l = layers[n];
    if ((typeof name === 'string' && name !== n) || l.raf) return;
    l.raf = requestAnimationFrame(safe(function () {
      l.raf = 0;
      var t0 = performance.now(), st = stats[n] || (stats[n] = { draws: 0, ms: 0 });
      try { if (l.bare) l.draw(null); else paint(n, l); } finally { st.draws++; st.ms += performance.now() - t0; }
    }, undefined, n));
  });
}
function tipLine(fn) { tipFns.push(fn); }
function onOptions(fn) { optionFns.push(fn); }
function api(name, fn) { apis[name] = fn; }
var describeFns = []; // fn(el, target): a module adds its fields to describe()
function onDescribe(fn) { describeFns.push(fn); }

function setOptions(o) {
  if (o) Object.keys(o).forEach(function (k) { opts[k] = typeof opts[k] === 'boolean' ? !!o[k] : o[k]; });
  optionFns.forEach(function (fn) { safe(fn)(opts); });
  schedule();
}

// ---------- text, selector, xpath ----------
var MAX_TEXT_SCAN = 400, MAX_TEXT_CANDIDATES = 1000, MAX_TEXT_NODES = 200;
// Text nodes through a TreeWalker, not innerText (forces layout) or textContent (includes style/script text); stops at MAX_TEXT_SCAN.
var NON_TEXT_TAGS = { script: 1, style: 1, noscript: 1 };
function textOf(el) {
  var walker = document.createTreeWalker(el, 4, function (n) {
    return n.parentNode && NON_TEXT_TAGS[n.parentNode.localName] ? 2 : 1;
  });
  var out = '', visited = 0;
  for (var n = walker.nextNode(); n && out.length < MAX_TEXT_SCAN && visited++ < MAX_TEXT_NODES; n = walker.nextNode()) {
    out = (out + n.data.slice(0, MAX_TEXT_SCAN)).replace(/\s+/g, ' ');
  }
  return out.trim().slice(0, 80);
}
function ownTextOf(el) {
  var out = '';
  for (var n = el.firstChild; n && out.length < MAX_TEXT_SCAN; n = n.nextSibling) {
    if (n.nodeType === 3) out = (out + n.data.slice(0, MAX_TEXT_SCAN)).replace(/\s+/g, ' ');
  }
  return out.trim().slice(0, 80);
}
// Control and format characters (the host rejects Cc, Cf, U+2028/2029) become CSS hex escapes so a generated selector stays on one line, parses and carries none raw.
var CONTROLS = '[\\p{Cc}\\p{Cf}\\u2028\\u2029]';
function hexEscapeControls(v) {
  return v.replace(new RegExp(CONTROLS, 'gu'), function (c) { return '\\' + c.codePointAt(0).toString(16) + ' '; });
}
function attrEsc(v) { return hexEscapeControls(v.replace(/\\/g, '\\\\').replace(/"/g, '\\"')); }
var CONTROLS_RE = new RegExp(CONTROLS, 'u');
/** Raw ids and class names reach the host as tokens it rejects when they hold such characters; they are dropped here instead of losing the whole message. */
function plainToken(v) { return CONTROLS_RE.test(v) ? '' : v; }
function plainTokens(list) { return list.filter(function (v) { return !CONTROLS_RE.test(v); }); }
function cssIdent(v) { return hexEscapeControls(CSS.escape(v)); }
function unique(scope, sel, el) {
  try {
    var l = scope.querySelectorAll(sel);
    return l.length === 1 && l[0] === el;
  } catch (e) { return false; }
}
var UNSTABLE_ID = /\d{3,}|^[a-f0-9]{8,}$|^:r|^(react|ember|radix|headlessui|mui)-?/i;
// Generated class names (CSS-in-JS, CSS modules, hashed suffixes); the one shared definition, extend here.
var GENERATED_CLASS = [
  /^(css|sc|jsx|emotion|svelte|styled|tw|chakra|mantine|ng)-/,
  /^_/,
  /^[A-Za-z]\w*__(?=[\w-]*\d)[\w-]{5,}$/,
  /-(?=[a-f0-9]*\d)[a-f0-9]{5,}$/,
  /^[a-z0-9]{1,2}$/
];
var MAX_CLASS_DIGITS = 3, MAX_CLASS_DIGIT_SHARE = 0.3, MIN_DIGIT_SHARE_LENGTH = 8;
function isStableClass(c) {
  if (GENERATED_CLASS.some(function (re) { return re.test(c); })) return false;
  var digits = (c.match(/\d/g) || []).length;
  return digits < MAX_CLASS_DIGITS && (c.length < MIN_DIGIT_SHARE_LENGTH || digits / c.length <= MAX_CLASS_DIGIT_SHARE);
}
var STABLE_ATTRS = ['data-testid', 'data-test', 'data-cy', 'aria-label', 'name'];

function segmentFor(el, scope) {
  var tag = el.localName, id = el.id;
  if (id && !UNSTABLE_ID.test(id) && unique(scope, '#' + cssIdent(id), el)) return { seg: '#' + cssIdent(id), abs: true };
  var seg = null, i, v;
  for (i = 0; i < STABLE_ATTRS.length && !seg; i++) {
    v = el.getAttribute(STABLE_ATTRS[i]);
    if (v && v.length <= 60) seg = tag + '[' + STABLE_ATTRS[i] + '="' + attrEsc(v) + '"]';
  }
  if (!seg) {
    var cls = Array.prototype.filter.call(el.classList || [], isStableClass).slice(0, 2);
    seg = tag + cls.map(function (c) { return '.' + cssIdent(c); }).join('');
  }
  var parent = el.parentNode, same = 0, idx = 0, matches = 0;
  if (parent && parent.children) {
    Array.prototype.forEach.call(parent.children, function (s) {
      if (s.localName === tag) { same++; if (s === el) idx = same; }
      if (s.matches(seg)) matches++;
    });
  }
  if (matches > 1) seg += ':nth-of-type(' + idx + ')';
  return { seg: seg, abs: false };
}

function selectorIn(el, scope) {
  var parts = [], cur = el;
  while (cur && cur.nodeType === 1 && parts.length < 25) {
    var s = segmentFor(cur, scope);
    parts.unshift(s.seg);
    var sel = parts.join(' > ');
    if (s.abs || unique(scope, sel, el)) return sel;
    cur = cur.parentElement;
  }
  return parts.join(' > ');
}
function xpathIn(el) {
  var parts = [];
  for (var cur = el; cur && cur.nodeType === 1; cur = cur.parentElement) {
    var n = 1;
    for (var s = cur.previousElementSibling; s; s = s.previousElementSibling) if (s.localName === cur.localName) n++;
    parts.unshift(cur.localName + '[' + n + ']');
  }
  return '/' + parts.join('/');
}
function selectorOf(el) {
  var root = el.getRootNode();
  if (root && root.host) return selectorOf(root.host) + ' >>> ' + selectorIn(el, root);
  return selectorIn(el, document);
}
function xpathOf(el) {
  var root = el.getRootNode();
  if (root && root.host) return xpathOf(root.host) + ' >>> ' + xpathIn(el);
  return xpathIn(el);
}
function resolveSelector(sel) {
  var parts = String(sel).split(' >>> '), node = document, el = null;
  for (var i = 0; i < parts.length; i++) {
    el = node.querySelector(parts[i]);
    if (!el) return null;
    if (i < parts.length - 1) { node = el.shadowRoot; if (!node) return null; }
  }
  return el;
}
function resolveXPath(xp) {
  if (String(xp).indexOf(' >>> ') >= 0) return null;
  return document.evaluate(xp, document, null, 9, null).singleNodeValue;
}
function resolveAnchor(a) {
  if (!a) return null;
  var el = null;
  if (a.selector) el = safe(resolveSelector, null)(a.selector);
  if (!el && a.xpath) el = safe(resolveXPath, null)(a.xpath);
  if (!el && a.tag && a.text) {
    var list = document.querySelectorAll(a.tag), n = Math.min(list.length, MAX_TEXT_CANDIDATES);
    // anchors store the whole-subtree text, so that match goes first; own text is only the fallback
    [textOf, ownTextOf].forEach(function (textFn) {
      for (var i = 0; i < n && !el; i++) if (textFn(list[i]) === a.text) el = list[i];
    });
  }
  return el && el.isConnected ? el : null;
}

// ---------- source hints ----------
function firstFrame(stack) {
  var m = /(?:\(|at\s+)?((?:https?|file|webpack[^:]*):[^\s)]+?|\/[^\s):]+):(\d+):\d+\)?/.exec(String(stack || ''));
  return m ? { file: m[1], line: +m[2] } : null;
}
function reactHint(el) {
  var key = Object.keys(el).filter(function (k) { return k.indexOf('__reactFiber$') === 0; })[0];
  if (!key) return null;
  var comp = null, file = null, line = null;
  for (var f = el[key], n = 0; f && n < 80; f = f.return, n++) {
    var t = f.type;
    if (!comp && t && (typeof t === 'function' || typeof t === 'object')) {
      var name = t.displayName || t.name || (t.render && (t.render.displayName || t.render.name));
      if (name) comp = name;
    }
    if (!file) {
      if (f._debugSource) { file = f._debugSource.fileName; line = f._debugSource.lineNumber; }
      else if (f._debugStack) { var fr = firstFrame(f._debugStack.stack || f._debugStack); if (fr) { file = fr.file; line = fr.line; } }
    }
    if (comp && file) break;
  }
  return comp || file ? { component: comp, file: file, line: line } : null;
}
function sourceHintOf(el) {
  try {
    var h = reactHint(el);
    if (h) return h;
    var vc = el.__vueParentComponent;
    if (vc && vc.type) return { component: vc.type.name || vc.type.__name || null, file: vc.type.__file || null, line: null };
    var v2 = el.__vue__;
    if (v2 && v2.$options) return { component: v2.$options.name || null, file: v2.$options.__file || null, line: null };
    var sv = el.__svelte_meta;
    if (sv && sv.loc) return { component: String(sv.loc.file || '').split(/[\\/]/).pop() || null, file: sv.loc.file, line: sv.loc.line };
  } catch (e) { /* ignore */ }
  return null;
}

// ---------- target descriptor ----------
var COMPUTED = ['display', 'position', 'width', 'height',
  'margin-top', 'margin-right', 'margin-bottom', 'margin-left',
  'padding-top', 'padding-right', 'padding-bottom', 'padding-left',
  'border-top-width', 'border-right-width', 'border-bottom-width', 'border-left-width',
  'color', 'background-color', 'font-family', 'font-size', 'font-weight', 'line-height',
  'opacity', 'z-index', 'overflow', 'flex-direction', 'gap'];
function px(cs, p) { return parseFloat(cs.getPropertyValue(p)) || 0; }
function sides(cs, prefix, suffix) {
  var g = function (s) { return px(cs, prefix + '-' + s + suffix); };
  return { t: g('top'), r: g('right'), b: g('bottom'), l: g('left') };
}
function frameName() {
  if (W === W.top) return 'top';
  try {
    var path = [];
    for (var w = W; w !== w.parent; w = w.parent) {
      var fs = w.parent.frames;
      for (var i = 0; i < fs.length; i++) if (fs[i] === w) path.unshift(i);
    }
    return 'frame:' + path.join('/');
  } catch (e) { return 'frame:?'; }
}
function describe(el) {
  var r = el.getBoundingClientRect(), cs = getComputedStyle(el);
  var computed = {};
  COMPUTED.forEach(function (p) { computed[p] = cs.getPropertyValue(p); });
  var b = sides(cs, 'border', '-width'), p = sides(cs, 'padding', ''), m = sides(cs, 'margin', '');
  var target = {
    selector: selectorOf(el), xpath: xpathOf(el), tag: el.localName, id: plainToken(el.id || ''),
    classes: plainTokens(Array.prototype.slice.call(el.classList || [])),
    text: textOf(el), outerHtml: (el.outerHTML || '').slice(0, 600),
    rect: { x: r.x + W.scrollX, y: r.y + W.scrollY, w: r.width, h: r.height },
    viewportRect: { x: r.x, y: r.y, w: r.width, h: r.height },
    computed: computed,
    box: { margin: m, border: b, padding: p,
      content: { w: Math.max(0, r.width - b.l - b.r - p.l - p.r), h: Math.max(0, r.height - b.t - b.b - p.t - p.b) } },
    sourceHint: sourceHintOf(el), frame: frameName(), pageUrl: location.href
  };
  describeFns.forEach(function (fn) { safe(fn)(el, target); });
  return target;
}

// ---------- element lookup ----------
function deepElementAt(x, y) {
  var el = document.elementFromPoint(x, y);
  if (el === host) el = document.elementsFromPoint(x, y).filter(function (e) { return e !== host; })[0] || null;
  for (var n = 0; el && el.shadowRoot && n < 20; n++) {
    var inner = el.shadowRoot.elementFromPoint(x, y);
    if (!inner || inner === el) break;
    el = inner;
  }
  return el;
}

// ---------- element box paths (corner radii) ----------
// One computed token: "10px" or "50%" (of the box side); anything else (calc(), keywords) is unparsable and counts as 0.
function radiusToken(t, side) {
  var n = parseFloat(t);
  if (!(n >= 0)) return 0;
  if (/^[\d.]+px$/.test(t)) return n;
  return /^[\d.]+%$/.test(t) ? n / 100 * side : 0;
}
// "10px 20px" is the horizontal and vertical radius; one value means a circular corner.
function cornerRadius(value, w, h) {
  var parts = String(value).trim().split(/\s+/);
  return { x: radiusToken(parts[0], w), y: radiusToken(parts[1] || parts[0], h) };
}
function fitRatio(len, sum) { return sum > 0 ? len / sum : Infinity; }
// CSS corner overlap rule: f = min(1, side length / sum of the two radii on that side); every radius is multiplied by f.
function clampedRadii(cs, w, h) {
  if (!(w > 0 && h > 0)) return null;
  var r = ['TopLeft', 'TopRight', 'BottomRight', 'BottomLeft'].map(function (c) { return cornerRadius(cs['border' + c + 'Radius'], w, h); });
  if (r.every(function (c) { return c.x === 0 && c.y === 0; })) return null;
  var f = Math.min(1, fitRatio(w, r[0].x + r[1].x), fitRatio(w, r[3].x + r[2].x), fitRatio(h, r[0].y + r[3].y), fitRatio(h, r[1].y + r[2].y));
  return r.map(function (c) { return { x: c.x * f, y: c.y * f }; });
}
var radiiCache = new WeakMap();
// shortcut: radii are cached per element and size, so a style change without a resize shows after the next option toggle.
function radiiOf(el, w, h) {
  var hit = radiiCache.get(el);
  if (hit && hit.w === w && hit.h === h) return hit.radii;
  var cs = getComputedStyle(el);
  // a transformed box is not the border box the radii belong to: a rectangle beats wrong corners
  var radii = cs.transform === 'none' || cs.transform === 'matrix(1, 0, 0, 1, 0, 0)' ? clampedRadii(cs, w, h) : null;
  radiiCache.set(el, { w: w, h: h, radii: radii });
  return radii;
}
onOptions(function () { radiiCache = new WeakMap(); });
W.addEventListener('resize', function () { radiiCache = new WeakMap(); });
var CORNER_SIDES = [['l', 't'], ['r', 't'], ['r', 'b'], ['l', 'b']];   // per corner TL, TR, BR, BL: the sides whose offset moves its x and y radius
function growRadius(c, d) { return c > 0 ? Math.max(0, c + d) : 0; }   // CSS: an outer edge keeps r + offset, a square corner stays square
/**
 * Adds the rounded box of el to the current path. rect is its border box, o the per-side offset from it (number or {t,r,b,l}):
 * positive grows (margin box, outer radius r + margin), negative shrinks (padding box, inner radius max(0, r - border)).
 */
function elementBoxPath(ctx, el, rect, o) {
  var d = typeof o === 'number' ? { t: o, r: o, b: o, l: o } : o;
  var x = rect.left - d.l, y = rect.top - d.t, w = Math.max(0, rect.width + d.l + d.r), h = Math.max(0, rect.height + d.t + d.b);
  var radii = radiiOf(el, rect.width, rect.height);
  if (!radii) { ctx.rect(x, y, w, h); return; }
  ctx.roundRect(x, y, w, h, radii.map(function (c, i) {
    return { x: growRadius(c.x, d[CORNER_SIDES[i][0]]), y: growRadius(c.y, d[CORNER_SIDES[i][1]]) };
  }));
}

C.elementBoxPath = elementBoxPath; C.radiiOf = radiiOf;
C.VERSION = VERSION; C.MAX_VISITED = MAX_VISITED;
C.safe = safe; C.send = send; C.opts = opts; C.state = state; C.apis = apis; C.stats = stats;
C.tipFns = tipFns;
C.host = host; C.shadow = shadow; C.tip = tip; C.mount = mount;
C.layer = layer; C.schedule = schedule; C.tipLine = tipLine; C.onOptions = onOptions; C.api = api; C.onDescribe = onDescribe;
C.setOptions = setOptions; C.isStableClass = isStableClass; C.plainToken = plainToken; C.plainTokens = plainTokens;
C.textOf = textOf; C.selectorOf = selectorOf; C.xpathOf = xpathOf;
C.resolveSelector = resolveSelector; C.resolveAnchor = resolveAnchor;
C.px = px; C.sides = sides; C.describe = describe; C.deepElementAt = deepElementAt;
