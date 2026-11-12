/* Module 40: layout audit (C.opts.spacing: BORDERS, PHANTOMS, MISALIGN), drawn without hovering across the visible elements. */
var safe = C.safe, opts = C.opts, px = C.px, isStableClass = C.isStableClass;
var MAX_CLUSTER = 60, SIDE_KEYS = 'trbl';
var SIDES = C.SIDES, collectKept = C.collectKept, drawTruncation = C.drawTruncation;   // module 30
var NOT_PHANTOM_TAG = /^(img|svg|canvas|video|audio|iframe|input|select|textarea|button|hr|br|wbr|picture|object|embed|meter|progress)$/;
var XHTML = 'http://www.w3.org/1999/xhtml';
var ALIGN_NEUTRAL = /^(normal|stretch|flex-start|start|self-start|left)$/;
var styles = new WeakMap();   // element -> computed-style facts (rects are never cached); cleared on mutation, resize, transitions

if (!opts.spacing) opts.spacing = [];
function enabled(kind) { return Array.isArray(opts.spacing) && opts.spacing.indexOf(kind) >= 0; }
function clearStyles() { styles = new WeakMap(); }
new MutationObserver(safe(clearStyles)).observe(document, { childList: true, subtree: true, attributes: true });
W.addEventListener('resize', safe(clearStyles), { passive: true });
W.addEventListener('transitionend', safe(clearStyles), true);

function hasContent(c) { return c !== 'none' && c !== 'normal' && c !== ''; }
function visibleColor(c) { return c !== 'transparent' && !/(,|\/)\s*0\)$/.test(c); }
function styleOf(el) {
  var s = styles.get(el);
  if (s) return s;
  var cs = getComputedStyle(el), m = C.sides(cs, 'margin', ''), bsig = '', i;
  for (i = 0; i < 4; i++) {
    var side = SIDES[i];
    if (px(cs, 'border-' + side + '-width') > 0 && visibleColor(cs.getPropertyValue('border-' + side + '-color'))) bsig += SIDE_KEYS[i];
  }
  var before = getComputedStyle(el, '::before').content, after = getComputedStyle(el, '::after').content;
  s = {
    display: cs.display, position: cs.position, flexDir: cs.flexDirection, alignItems: cs.alignItems,
    justifyItems: cs.justifyItems, alignSelf: cs.alignSelf, opacity: parseFloat(cs.opacity),
    hidden: cs.visibility === 'hidden', bsig: bsig, margin: m, marginSum: m.t + m.r + m.b + m.l,
    painted: visibleColor(cs.backgroundColor) || cs.backgroundImage !== 'none' || cs.boxShadow !== 'none' ||
      cs.outlineStyle !== 'none' || hasContent(before) || hasContent(after)
  };
  styles.set(el, s);
  return s;
}

function keepAudit(el, r) {
  if (!(r.bottom >= 0 && r.right >= 0 && r.top <= W.innerHeight && r.left <= W.innerWidth)) return null;
  var s = styleOf(el);
  return s.display !== 'none' && s.display !== 'contents' ? { el: el, r: r, s: s } : null;
}

function structureSignature(el) {
  var cls = Array.prototype.filter.call(el.classList || [], isStableClass).sort().join('.');
  return !cls && /^(div|span)$/.test(el.localName) ? null : el.localName + '.' + cls;   // classless div/span groups are layout noise
}
function inFlow(e) { return e.r.width > 0 && e.r.height > 0 && e.s.position !== 'absolute' && e.s.position !== 'fixed'; }
function byParent(entries) {
  var map = new Map();
  entries.forEach(function (e) {
    var p = e.el.parentNode;
    if (!p || !inFlow(e)) return;
    if (!map.has(p)) map.set(p, []);
    map.get(p).push(e);
  });
  return map;
}
function modeOf(vals) {
  var best = null, bestN = 0;
  vals.slice().sort(function (a, b) { return a - b; }).forEach(function (v) {
    var n = vals.filter(function (x) { return Math.abs(x - v) <= 0.5; }).length;
    if (n > bestN) { best = v; bestN = n; }
  });
  return best;
}
var NO_OFFSET = { t: 0, r: 0, b: 0, l: 0 };
/** Dashed outline of the element's box grown by margin (rounded like the element); label optional. */
function dashedBox(ctx, color, e, margin, label) {
  var r = e.r, o = { t: margin.t - .5, r: margin.r - .5, b: margin.b - .5, l: margin.l - .5 };
  ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = 1;
  ctx.setLineDash([4, 3]);
  ctx.beginPath();
  C.elementBoxPath(ctx, e.el, r, o);
  ctx.stroke();
  ctx.setLineDash([]);
  if (label) ctx.fillText(label, r.left - margin.l + 2, r.height + margin.t + margin.b >= 14 ? r.top - margin.t + 10 : Math.max(9, r.top - margin.t - 2));
}

// ---------- BORDERS ----------
function drawBorders(ctx, e) {
  var r = e.r, sig = e.s.bsig, rounded = !!C.radiiOf(e.el, r.width, r.height);
  ctx.strokeStyle = 'rgba(5,150,105,.9)'; ctx.lineWidth = 1;
  ctx.beginPath();
  if (rounded && sig.length === 4) { C.elementBoxPath(ctx, e.el, r, -.5); ctx.stroke(); return; }
  if (rounded) { ctx.save(); C.elementBoxPath(ctx, e.el, r, 0); ctx.clip(); ctx.beginPath(); }   // partial borders follow the corners by clipping
  if (sig.indexOf('t') >= 0) { ctx.moveTo(r.left, r.top + .5); ctx.lineTo(r.right, r.top + .5); }
  if (sig.indexOf('b') >= 0) { ctx.moveTo(r.left, r.bottom - .5); ctx.lineTo(r.right, r.bottom - .5); }
  if (sig.indexOf('l') >= 0) { ctx.moveTo(r.left + .5, r.top); ctx.lineTo(r.left + .5, r.bottom); }
  if (sig.indexOf('r') >= 0) { ctx.moveTo(r.right - .5, r.top); ctx.lineTo(r.right - .5, r.bottom); }
  ctx.stroke();
  if (rounded) ctx.restore();
}
function oddBorders(entries, res) {
  byParent(entries).forEach(function (kids) {
    var groups = {};
    kids.forEach(function (e) { var k = structureSignature(e.el); if (k) (groups[k] = groups[k] || []).push(e); });
    Object.keys(groups).forEach(function (k) {
      var g = groups[k], counts = {};
      g.forEach(function (e) { counts[e.s.bsig] = (counts[e.s.bsig] || 0) + 1; });
      var sigs = Object.keys(counts);
      if (g.length < 2 || sigs.length < 2) return;
      sigs.sort(function (a, b) { return counts[b] - counts[a] || b.length - a.length; });
      var odd = g.filter(function (e) { return e.s.bsig !== sigs[0]; });
      // shortcut: a lone first/last odd item is a usual "no divider after the last" design, not flagged
      if (odd.length === 1 && g.length >= 3 && (odd[0] === g[0] || odd[0] === g[g.length - 1])) return;
      odd.forEach(function (e) {
        res.findings.push({ kind: 'BORDER_ODD', el: e.el,
          detail: 'border sides "' + e.s.bsig + '" while siblings have "' + sigs[0] + '"',
          draw: function (ctx) { dashedBox(ctx, '#dc2626', e, NO_OFFSET, 'border?'); } });
      });
    });
  });
}

// ---------- PHANTOMS ----------
function phantomReason(e) {
  var s = e.s, el = e.el;
  if (s.position === 'absolute' || s.position === 'fixed' || el.namespaceURI !== XHTML) return null;
  if (!((e.r.width > 0 && e.r.height > 0) || s.marginSum > 0)) return null;
  var parent = el.parentElement, ps = parent && styleOf(parent);
  if ((s.opacity === 0 && !(ps && ps.opacity === 0)) || (s.hidden && !(ps && ps.hidden))) return 'invisible but takes space';
  var empty = el.children.length === 0 && el.textContent.trim() === '' && !NOT_PHANTOM_TAG.test(el.localName);
  return empty && !s.painted && !s.bsig ? 'empty, takes space' : null;
}
function phantoms(entries, res) {
  entries.forEach(function (e) {
    var why = phantomReason(e);
    if (!why) return;
    var m = e.s.margin;
    res.findings.push({ kind: 'PHANTOM', el: e.el, detail: why,
      draw: function (ctx) { dashedBox(ctx, '#d97706', e, m, 'phantom'); } });
  });
}

// ---------- MISALIGN ----------
function clusters(list, lo, hi) {
  var out = [], cur = null;
  list.slice().sort(function (a, b) { return lo(a.r) - lo(b.r); }).forEach(function (e) {
    if (cur && lo(e.r) < cur.end - .5) { cur.items.push(e); cur.end = Math.max(cur.end, hi(e.r)); }
    else { cur = { items: [e], end: hi(e.r) }; out.push(cur); }
  });
  return out;
}
function misaligned(items, key, label, res) {
  if (items.length < 2 || items.length > MAX_CLUSTER) return;
  var ref = modeOf(items.map(function (e) { return e.r[key]; }));
  items.forEach(function (e) {
    var d = e.r[key] - ref;
    if (Math.abs(d) < 1 || Math.abs(d) > 3) return;
    var text = (d > 0 ? '+' : '') + Math.round(d * 10) / 10 + 'px';
    res.findings.push({ kind: 'MISALIGN', el: e.el, detail: label + ' edge ' + text + ' off its line',
      draw: function (ctx) {
        ctx.strokeStyle = ctx.fillStyle = '#dc2626'; ctx.lineWidth = 2;
        ctx.beginPath();
        if (key === 'top') { ctx.moveTo(e.r.left, e.r.top); ctx.lineTo(e.r.right, e.r.top); }
        else { ctx.moveTo(e.r.left, e.r.top); ctx.lineTo(e.r.left, e.r.bottom); }
        ctx.stroke();
        ctx.fillText(text, e.r.left + 3, e.r.top + 10);
      } });
  });
}
function alignOk(ps, prop, e) { return ALIGN_NEUTRAL.test(ps[prop]) && (e.s.alignSelf === 'auto' || ALIGN_NEUTRAL.test(e.s.alignSelf)); }
function misalignedEdges(entries, res) {
  byParent(entries).forEach(function (kids, parent) {
    if (!parent.nodeType || parent.nodeType !== 1) return;
    var ps = styleOf(parent), grid = /grid$/.test(ps.display), flex = /flex$/.test(ps.display);
    if (!grid && !flex) return;
    var column = flex && /^column/.test(ps.flexDir);
    var topsFree = column || kids.every(function (e) { return alignOk(ps, 'alignItems', e); });
    var leftsFree = (!grid && !column) || kids.every(function (e) { return alignOk(ps, grid ? 'justifyItems' : 'alignItems', e); });
    if (topsFree) clusters(kids, function (r) { return r.top; }, function (r) { return r.bottom; }).forEach(function (c) { misaligned(c.items, 'top', 'top', res); });
    if (leftsFree) clusters(kids, function (r) { return r.left; }, function (r) { return r.right; }).forEach(function (c) { misaligned(c.items, 'left', 'left', res); });
  });
}
function overlap(a0, a1, b0, b1) { return Math.min(a1, b1) - Math.max(a0, b0) > 0; }
function pairGap(a, b) {
  if (overlap(a.r.top, a.r.bottom, b.r.top, b.r.bottom) && b.r.left >= a.r.right - .5) {
    return { gap: b.r.left - a.r.right, x: a.r.right, y: Math.max(a.r.top, b.r.top), w: b.r.left - a.r.right, h: Math.min(a.r.bottom, b.r.bottom) - Math.max(a.r.top, b.r.top), axis: 'h' };
  }
  if (overlap(a.r.left, a.r.right, b.r.left, b.r.right) && b.r.top >= a.r.bottom - .5) {
    return { gap: b.r.top - a.r.bottom, x: Math.max(a.r.left, b.r.left), y: a.r.bottom, w: Math.min(a.r.right, b.r.right) - Math.max(a.r.left, b.r.left), h: b.r.top - a.r.bottom, axis: 'v' };
  }
  return null;
}
function unevenGaps(entries, res) {
  byParent(entries).forEach(function (kids) {
    var groups = {};
    kids.forEach(function (e) { var k = structureSignature(e.el); if (k) (groups[k] = groups[k] || []).push(e); });
    Object.keys(groups).forEach(function (k) {
      var g = groups[k], byAxis = { h: [], v: [] };
      for (var i = 1; i < g.length; i++) {
        var p = pairGap(g[i - 1], g[i]);
        if (p) { p.el = g[i].el; byAxis[p.axis].push(p); }
      }
      ['h', 'v'].forEach(function (axis) {
        var list = byAxis[axis];
        if (list.length < 2) return;
        var ref = modeOf(list.map(function (p) { return p.gap; }));
        list.forEach(function (p) {
          var d = p.gap - ref;
          if (Math.abs(d) < 1) return;
          var text = (d > 0 ? '+' : '') + Math.round(d * 10) / 10;
          res.findings.push({ kind: 'UNEVEN_GAP', el: p.el, detail: 'gap ' + Math.round(p.gap * 10) / 10 + 'px vs ' + Math.round(ref * 10) / 10 + 'px (' + text + ')',
            draw: function (ctx) {
              ctx.fillStyle = 'rgba(220,38,38,.25)'; ctx.fillRect(p.x, p.y, p.w, p.h);
              ctx.fillStyle = '#dc2626'; ctx.fillText(text, p.x + 1, p.y + 10);
            } });
        });
      });
    });
  });
}

function audit() {
  var res = { borders: [], findings: [], truncated: false }, out = { els: [], visited: 0, truncated: false };
  if (!enabled('BORDERS') && !enabled('PHANTOMS') && !enabled('MISALIGN')) return res;
  collectKept(document, out, keepAudit);
  res.truncated = out.truncated;
  res.shown = out.els.length;
  if (enabled('BORDERS')) { res.borders = out.els.filter(function (e) { return e.s.bsig && e.r.width > 0 && e.r.height > 0; }); oddBorders(out.els, res); }
  if (enabled('PHANTOMS')) phantoms(out.els, res);
  if (enabled('MISALIGN')) { misalignedEdges(out.els, res); unevenGaps(out.els, res); }
  return res;
}

C.layer('audit', function (ctx) {
  var res = audit();
  ctx.font = '10px ui-monospace,Consolas,monospace';
  res.borders.forEach(function (e) { drawBorders(ctx, e); });
  res.findings.forEach(function (f) { f.draw(ctx); });
  if (res.truncated) drawTruncation(ctx, 'audit: ' + res.shown + ' elements shown, page has more', 20);
});

C.api('auditTruncated', function () { return audit().truncated; });
C.api('auditDebug', function () {
  return audit().findings.map(function (f) { return { kind: f.kind, selector: C.selectorOf(f.el), detail: f.detail }; });
});
