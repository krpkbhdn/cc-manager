/* Module 60: WCAG contrast of an element's text against its effective background. */
var opts = C.opts;
var LARGE_PX = 24, LARGE_BOLD_PX = 18.66, BOLD_WEIGHT = 700;
var AA = 4.5, AA_LARGE = 3, AAA = 7, AAA_LARGE = 4.5;

function parseColor(s) {
  var m = /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)(?:\s*[,/]\s*([\d.]+%?))?\s*\)$/.exec(String(s).trim());
  if (!m) return null;
  var a = m[4] === undefined ? 1 : (m[4].slice(-1) === '%' ? parseFloat(m[4]) / 100 : parseFloat(m[4]));
  return { r: +m[1], g: +m[2], b: +m[3], a: a };
}
function over(top, base) {
  return { r: top.r * top.a + base.r * (1 - top.a), g: top.g * top.a + base.g * (1 - top.a), b: top.b * top.a + base.b * (1 - top.a), a: 1 };
}
/** Opaque background colour behind el, or null when an image or gradient is involved (shortcut: only the element's own opacity counts, not that of ancestors). */
function backgroundBehind(el) {
  var layers = [];
  for (var cur = el; cur && cur.nodeType === 1; cur = cur.parentNode && cur.parentNode.nodeType === 11 ? cur.parentNode.host : cur.parentElement) {
    var cs = getComputedStyle(cur);
    if (cs.backgroundImage !== 'none') return null;
    var bg = parseColor(cs.backgroundColor);
    if (!bg) return null;
    if (bg.a > 0) layers.push(bg);
    if (bg.a >= 1) break;
  }
  var base = { r: 255, g: 255, b: 255, a: 1 };
  for (var i = layers.length - 1; i >= 0; i--) base = over(layers[i], base);
  return base;
}
function channel(v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
function luminance(c) { return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b); }
function hex(c) {
  return '#' + [c.r, c.g, c.b].map(function (v) { return ('0' + Math.round(v).toString(16)).slice(-2); }).join('');
}
function hasOwnText(el) {
  return Array.prototype.some.call(el.childNodes, function (n) { return n.nodeType === 3 && n.nodeValue.trim(); });
}

function contrastOf(el) {
  if (!hasOwnText(el)) return null;
  var cs = getComputedStyle(el), bg = backgroundBehind(el), fg = parseColor(cs.color);
  if (!bg || !fg) return null;
  fg.a *= parseFloat(cs.opacity);
  fg = over(fg, bg);
  var a = luminance(fg), b = luminance(bg);
  var ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  var size = parseFloat(cs.fontSize), weight = parseInt(cs.fontWeight, 10) || 400;
  var large = size >= LARGE_PX || (size >= LARGE_BOLD_PX && weight >= BOLD_WEIGHT);
  return { ratio: Math.round(ratio * 100) / 100, fg: hex(fg), bg: hex(bg), large: large,
    aa: ratio >= (large ? AA_LARGE : AA), aaa: ratio >= (large ? AAA_LARGE : AAA) };
}

C.tipLine(function (el) {
  var c = opts.contrast ? contrastOf(el) : null;
  return c ? 'Contrast ' + c.ratio + ' AA ' + (c.aa ? '✓' : '✗') + ' AAA ' + (c.aaa ? '✓' : '✗') : null;
});
C.onDescribe(function (el, target) { target.contrast = contrastOf(el); });
