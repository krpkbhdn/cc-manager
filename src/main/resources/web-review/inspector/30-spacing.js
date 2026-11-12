/* Module 30: spacing audit layer. Margin, padding and flex/grid gap rectangles for every visible element, with authored-unit labels (opts.spacing). */
var opts = C.opts, host = C.host, schedule = C.schedule;
var MAX_ELEMENTS = 3000, MAX_LABELLED = 200, MAX_KIDS = 60, MIN_LABEL_BOX = 14;
var REPLACED_TAG = /^(img|svg|canvas|video|audio|iframe|input|select|textarea|button|object|embed|meter|progress)$/;
var SPACING_HUES = { MARGIN: '246,178,107', PADDING: '147,196,125', GAP: '167,139,250' };
var FILL_ALPHA = .13, EDGE_ALPHA = .5;   // light fill so the page shows through, a thin edge keeps the regions apart
var SIDES = ['top', 'right', 'bottom', 'left'];
var PROPS = ['margin', 'padding'].reduce(function (acc, k) {
  return acc.concat(SIDES.map(function (s) { return k + '-' + s; }));
}, ['row-gap', 'column-gap']);
var SHORTHAND = { margin: 'margin', padding: 'padding', 'row-gap': 'gap', 'column-gap': 'gap' };

opts.spacing = [];

// ---------- authored rule index (rebuilt only when the stylesheets change) and per-element authored values ----------
var authoredCache = new WeakMap();
function invalidate() { authoredCache = new WeakMap(); }
function cs(el) { return getComputedStyle(el); }

var rules = [], ruleSig = null;
function sheetsNow() {
  return Array.prototype.slice.call(document.styleSheets).concat(Array.prototype.slice.call(document.adoptedStyleSheets || []));
}
function sheetsSignature(sheets) {
  // shortcut: an in-place edit that keeps both the rule count and the declaration count of every top-level rule goes unnoticed until the next sheet change
  return sheets.map(function (s) {
    try {
      var n = 0;
      for (var i = 0; i < s.cssRules.length; i++) n += s.cssRules[i].style ? s.cssRules[i].style.length : 1;
      return s.cssRules.length + ':' + n;
    } catch (e) { return -1; }
  }).join(',') + '|' + sheets.length;
}
function shorthandOf(prop) { return SHORTHAND[prop.replace(/-(top|right|bottom|left)$/, '')] || SHORTHAND[prop]; }
/** The declared value of a longhand in a CSSStyleDeclaration; a var() shorthand has empty longhands, so its single token counts. */
function declared(style, prop) {
  var v = style.getPropertyValue(prop), from = prop;
  if (!v) {
    from = shorthandOf(prop);
    var sv = from ? style.getPropertyValue(from) : '';
    if (!/var\(/.test(sv) || sv.trim().split(/\s+/).length !== 1) return null;
    v = sv;
  }
  return { v: v.trim(), imp: style.getPropertyPriority(from) === 'important' };
}
function walkRules(list, out) {
  for (var i = 0; i < list.length; i++) {
    var r = list[i];
    if (r.selectorText && r.style) {
      var decl = {}, any = false;
      PROPS.forEach(function (p) { var d = declared(r.style, p); if (d) { decl[p] = d; any = true; } });
      if (any) out.push({ sel: r.selectorText, decl: decl });
    } else if (r.cssRules) {
      var ok = true;
      try { ok = !r.media || W.matchMedia(r.media.mediaText).matches; } catch (e) { /* keep */ }
      if (ok) walkRules(r.cssRules, out);
    }
  }
}
function indexRules() {
  var sheets = sheetsNow(), sig = sheetsSignature(sheets);
  if (sig === ruleSig) return;
  ruleSig = sig; rules = []; invalidate();
  sheets.forEach(function (s) {
    try { walkRules(s.cssRules, rules); } catch (e) { /* cross-origin sheet: px fallback */ }
  });
}
/** inline !important > rule !important > inline > rule; the last match wins a tie. shortcut: no selector specificity, only source order. */
function authoredValue(el, prop) {
  var cached = authoredCache.get(el);
  if (!cached) { cached = {}; authoredCache.set(el, cached); }
  if (!(prop in cached)) cached[prop] = findAuthored(el, prop);
  return cached[prop];
}
function findAuthored(el, prop) {
  var best = null, bestScore = -1;
  function offer(d, score) { if (d && score >= bestScore) { best = d.v; bestScore = score; } }
  rules.forEach(function (r) {
    var d = r.decl[prop];
    if (!d) return;
    var hit = false;
    try { hit = el.matches(r.sel); } catch (e) { /* skip */ }
    if (hit) offer(d, d.imp ? 2 : 0);
  });
  var inl = declared(el.style, prop);
  if (inl) offer(inl, inl.imp ? 3 : 1);
  return best;
}

// ---------- labels ----------
function containingWidth(el) {
  var p = el.parentElement;
  if (!p) return W.innerWidth;
  var c = cs(p);
  return Math.max(0, p.clientWidth - C.px(c, 'padding-left') - C.px(c, 'padding-right'));
}
function fmt(n) { return String(Math.round(n * 10) / 10); }
function labelFor(el, prop, computedPx) {
  var raw = authoredValue(el, prop);
  var asPx = fmt(computedPx) + 'px';
  if (!raw) return asPx;
  var token = /^var\(\s*(--[^\s,)]+)/.exec(raw);
  if (token) return token[1];
  var m = /^(-?[\d.]+)(px|rem|em|%|vw|vh)$/.exec(raw);
  if (!m) return asPx;
  var n = parseFloat(m[1]), unit = m[2], conv;
  if (unit === 'px') conv = n;
  else if (unit === 'rem') conv = n * parseFloat(getComputedStyle(document.documentElement).fontSize);
  else if (unit === 'em') conv = n * C.px(cs(el), 'font-size');
  else if (unit === '%') conv = n / 100 * containingWidth(el);
  else if (unit === 'vw') conv = n / 100 * W.innerWidth;
  else conv = n / 100 * W.innerHeight;
  return Math.abs(conv - computedPx) < 0.5 ? raw : asPx;
}

// ---------- regions ----------
/** Shared with module 40: visible-element walk over the document and open shadow roots; keep(el, rect) says whether the element counts. out.truncated marks a cap hit. */
function collectKept(root, out, keep) {
  var list = root.querySelectorAll('*');
  for (var i = 0; i < list.length; i++) {
    if (out.visited >= C.MAX_VISITED || out.els.length >= MAX_ELEMENTS) { out.truncated = true; return; }
    var el = list[i];
    out.visited++;
    if (el === host) continue;
    var item = keep(el, el.getBoundingClientRect());
    if (item) out.els.push(item);
    if (el.shadowRoot) collectKept(el.shadowRoot, out, keep);
  }
}
function keepSpacing(el, r) {
  return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < W.innerHeight && r.left < W.innerWidth ? [el, r] : null;
}
function boxRegions(el, r, c, want, out, rings) {
  if (c.transform !== 'none') return;   // shortcut: transformed elements are skipped, their box is not the bounding rect
  var flatInline = c.display === 'inline' && !REPLACED_TAG.test(el.localName);
  var m = C.sides(c, 'margin', ''), b = C.sides(c, 'border', '-width'), p = C.sides(c, 'padding', '');
  var ix = r.left + b.l, iy = r.top + b.t, iw = r.width - b.l - b.r, ih = r.height - b.t - b.b;
  function add(kind, side, v, x, y, w, h) {
    if (v > 0 && w > 0 && h > 0) out.push({ el: el, kind: kind, side: side, prop: kind.toLowerCase() + '-' + side, px: v, x: x, y: y, w: w, h: h });
  }
  var zero = { t: 0, r: 0, b: 0, l: 0 };
  function ring(kind, outer, inner) {
    if (SIDES.some(function (k, i) { return outer['trbl'[i]] !== inner['trbl'[i]]; })) rings.push({ kind: kind, el: el, r: r, outer: outer, inner: inner });
  }
  if (want.MARGIN) {
    ring('MARGIN', flatInline ? { t: 0, r: m.r, b: 0, l: m.l } : m, zero);
    if (!flatInline) {
      add('MARGIN', 'top', m.t, r.left, r.top - m.t, r.width, m.t);
      add('MARGIN', 'bottom', m.b, r.left, r.bottom, r.width, m.b);
    }
    add('MARGIN', 'left', m.l, r.left - m.l, r.top, m.l, r.height);
    add('MARGIN', 'right', m.r, r.right, r.top, m.r, r.height);
  }
  if (want.PADDING) {
    if (flatInline) { p.t = p.b = 0; }
    ring('PADDING', { t: -b.t, r: -b.r, b: -b.b, l: -b.l }, { t: -b.t - p.t, r: -b.r - p.r, b: -b.b - p.b, l: -b.l - p.l });
    add('PADDING', 'top', p.t, ix, iy, iw, p.t);
    add('PADDING', 'bottom', p.b, ix, iy + ih - p.b, iw, p.b);
    add('PADDING', 'left', p.l, ix, iy + p.t, p.l, ih - p.t - p.b);
    add('PADDING', 'right', p.r, ix + iw - p.r, iy + p.t, p.r, ih - p.t - p.b);
  }
}
function gapRegions(el, c, out) {
  if (!/flex|grid/.test(c.display)) return;
  var cg = C.px(c, 'column-gap'), rg = C.px(c, 'row-gap');
  if (!(cg > 0 || rg > 0)) return;
  var kids = [];
  for (var i = 0; i < el.children.length && kids.length < MAX_KIDS; i++) {
    var k = el.children[i], kc = cs(k);
    if (kc.display === 'none' || kc.position === 'absolute' || kc.position === 'fixed') continue;
    var kr = k.getBoundingClientRect();
    if (kr.width > 0 && kr.height > 0) kids.push(kr);
  }
  var seen = {};
  function push(g) {
    var key = [g.prop, Math.round(g.x), Math.round(g.y), Math.round(g.w), Math.round(g.h)].join();
    if (!seen[key]) { seen[key] = true; out.push(g); }
  }
  kids.forEach(function (a) {
    kids.forEach(function (b) {
      var top = Math.max(a.top, b.top), left = Math.max(a.left, b.left);
      var vOverlap = Math.min(a.bottom, b.bottom) - top, hOverlap = Math.min(a.right, b.right) - left;
      if (cg > 0 && vOverlap > 0 && Math.abs(b.left - a.right - cg) < 1) {
        push({ el: el, kind: 'GAP', side: 'column', prop: 'column-gap', px: cg, x: a.right, y: top, w: cg, h: vOverlap });
      }
      if (rg > 0 && hOverlap > 0 && Math.abs(b.top - a.bottom - rg) < 1) {
        push({ el: el, kind: 'GAP', side: 'row', prop: 'row-gap', px: rg, x: left, y: a.bottom, w: hOverlap, h: rg });
      }
    });
  });
}
function compute() {
  var on = opts.spacing;
  if (!Array.isArray(on) || !on.length) return null;
  var want = { MARGIN: on.indexOf('MARGIN') >= 0, PADDING: on.indexOf('PADDING') >= 0, GAP: on.indexOf('GAP') >= 0 };
  var values = on.indexOf('VALUES') >= 0;
  if (!(want.MARGIN || want.PADDING || want.GAP)) return null;
  if (values) indexRules();
  var found = { els: [], visited: 0, truncated: false }, regions = [], rings = [], labelled = 0;
  collectKept(document, found, keepSpacing);
  found.els.forEach(function (e) {
    var from = regions.length, c = cs(e[0]);
    boxRegions(e[0], e[1], c, want, regions, rings);
    if (want.GAP) gapRegions(e[0], c, regions);
    if (!values || regions.length === from || labelled >= MAX_LABELLED) return;
    labelled++;
    for (var i = from; i < regions.length; i++) {
      var g = regions[i];
      if (g.w >= MIN_LABEL_BOX && g.h >= MIN_LABEL_BOX) g.label = labelFor(e[0], g.prop, g.px);
    }
  });
  return { regions: regions, rings: rings, elements: found.els.length, labelled: labelled, truncated: found.truncated,
    total: found.truncated ? document.querySelectorAll('*').length : found.els.length };
}

/** Also used by module 40. */
function drawTruncation(ctx, text, y) {
  ctx.font = '11px ui-monospace,Consolas,monospace';
  ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
  var w = ctx.measureText(text).width + 8;
  ctx.fillStyle = 'rgba(31,41,55,.9)'; ctx.fillRect(2, y, w, 16);
  ctx.fillStyle = '#fff'; ctx.fillText(text, 6, y + 12);
}

// Modules sit in separate try blocks (strict mode), so function declarations are shared through C.
C.collectKept = collectKept; C.drawTruncation = drawTruncation; C.SIDES = SIDES;

C.layer('spacing', function (ctx) {
  var res = compute();
  if (!res) return;
  // margin and padding follow the element's corner radii (ring = outer box minus inner box); gaps stay rectangles
  Object.keys(SPACING_HUES).forEach(function (kind) {
    ctx.fillStyle = 'rgba(' + SPACING_HUES[kind] + ',' + FILL_ALPHA + ')';
    ctx.strokeStyle = 'rgba(' + SPACING_HUES[kind] + ',' + EDGE_ALPHA + ')';
    ctx.lineWidth = 1;
    var kindRings = res.rings.filter(function (g) { return g.kind === kind; });
    // fills stay per ring: one evenodd path would cancel the overlap of collapsed sibling margins; the edges batch into one stroke
    kindRings.forEach(function (g) {
      ctx.beginPath();
      C.elementBoxPath(ctx, g.el, g.r, g.outer);
      C.elementBoxPath(ctx, g.el, g.r, g.inner);
      ctx.fill('evenodd');
    });
    ctx.beginPath();
    kindRings.forEach(function (g) {
      C.elementBoxPath(ctx, g.el, g.r, g.outer);
      C.elementBoxPath(ctx, g.el, g.r, g.inner);
    });
    ctx.stroke();
    ctx.beginPath();
    res.regions.forEach(function (g) { if (g.kind === kind && kind === 'GAP') ctx.rect(g.x, g.y, g.w, g.h); });
    ctx.fill();
    ctx.stroke();
  });
  ctx.font = '10px ui-monospace,Consolas,monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillStyle = 'rgba(17,24,39,.9)';
  res.regions.forEach(function (g) { if (g.label) ctx.fillText(g.label, g.x + g.w / 2, g.y + g.h / 2); });
  if (res.truncated) drawTruncation(ctx, 'spacing: ' + res.elements + ' of ' + res.total + ' elements shown', 2);
});

C.onOptions(function () { invalidate(); });
W.addEventListener('resize', C.safe(invalidate), { passive: true });
new MutationObserver(C.safe(function () { if (Array.isArray(opts.spacing) && opts.spacing.length) { invalidate(); schedule('spacing'); } }))
  .observe(document, { childList: true, subtree: true, attributes: true });

C.api('spacingDebug', function () {
  invalidate();
  var res = compute();
  if (!res) return { elements: 0, labelled: 0, truncated: false, total: 0, counts: {}, regions: [] };
  var counts = {};
  res.regions.forEach(function (g) { counts[g.kind] = (counts[g.kind] || 0) + 1; });
  return {
    elements: res.elements, labelled: res.labelled, truncated: res.truncated, total: res.total, counts: counts,
    regions: res.regions.map(function (g) { return { id: g.el.id, kind: g.kind, side: g.side, px: Math.round(g.px * 10) / 10, label: g.label || null }; })
  };
});
