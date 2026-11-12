/* Module 50: similar elements (same tag and same class set): hover outline, tip, count in describe(), showSimilar api. One grouping function serves them all. */
var isStableClass = C.isStableClass;
var opts = C.opts, state = C.state;   // state.chosenSimilar: element whose similar ones stay highlighted regardless of hover; other modules clear it
opts.similar = false;
var MAX_COUNT = 500;

function stableClassesOf(el) {
  return Array.prototype.filter.call(el.classList || [], isStableClass).sort();
}
/** The class set that defines the group: the stable classes if there are any, else all classes (generated ones included); empty means no group. */
function matchClasses(el) {
  var stable = stableClassesOf(el);
  return stable.length ? stable : Array.prototype.slice.call(el.classList || []).sort();
}
function signature(el) { return el.localName + '.' + matchClasses(el).join('.'); }

var memo = { el: null, list: null, capped: false };
function invalidateSimilar() { memo.el = null; memo.list = null; }
// shortcut: the memo drops on option changes, mutations and resize; a class change inside an open shadow root shows after the next of them.
C.onOptions(invalidateSimilar);
new MutationObserver(invalidateSimilar).observe(document, { childList: true, subtree: true, attributes: true });
W.addEventListener('resize', invalidateSimilar);
/** Elements in the same tree with the identical signature, the element itself included; an element without classes is alone. */
function similarTo(el) {
  if (memo.el !== el) { (C.stats.similarScans || (C.stats.similarScans = { draws: 0, ms: 0 })).draws++; memo.list = scanSimilar(el); memo.capped = memo.list.length > MAX_COUNT; memo.el = el; }
  return memo.list;
}
function scanSimilar(el) {
  var cls = matchClasses(el);
  if (!cls.length) return [el];
  var sig = signature(el), out = [];
  var list = el.getRootNode().querySelectorAll(el.localName + cls.map(function (c) { return '.' + CSS.escape(c); }).join(''));
  for (var i = 0; i < list.length && out.length <= MAX_COUNT; i++) {
    if (signature(list[i]) === sig) out.push(list[i]);
  }
  return out;
}
/** Matches besides the element, at most MAX_COUNT (a larger group shows as "500+" in the tip). */
function othersOf(el) { return Math.min(MAX_COUNT, Math.max(0, similarTo(el).length - 1)); }

C.layer('similar', function (ctx) {
  var chosen = state.chosenSimilar;
  if (chosen && !chosen.isConnected) chosen = state.chosenSimilar = null;
  var el = chosen || state.hoverEl;
  if ((!opts.similar && !chosen) || !el || !el.isConnected) return;
  var list = similarTo(el);
  if (list.length < 2) return;
  ctx.strokeStyle = 'rgba(217,70,239,.9)';
  ctx.fillStyle = 'rgba(217,70,239,.12)';
  ctx.lineWidth = 1;
  ctx.setLineDash([4, 2]);
  list.forEach(function (e) {
    var r = e.getBoundingClientRect();
    if (e === el || r.width <= 0 || r.height <= 0) return;
    ctx.beginPath();
    C.elementBoxPath(ctx, e, r, 0);
    ctx.fill();
    ctx.beginPath();
    C.elementBoxPath(ctx, e, r, -.5);
    ctx.stroke();
  });
});
C.onOptions(function () { C.schedule('similar'); });

C.tipLine(function (el) {
  if (!opts.similar) return null;
  var n = othersOf(el);
  return n > 0 ? n + (memo.capped ? '+' : '') + ' similar' : null;
});

C.onDescribe(function (el, target) {
  target.stableClasses = C.plainTokens(stableClassesOf(el));
  target.similarCount = othersOf(el);
});

/** Keeps the similar elements of the element at selector highlighted (falsy selector clears); returns how many there are besides it. */
C.api('showSimilar', function (selector) {
  var chosen = state.chosenSimilar = selector ? C.resolveSelector(selector) : null;
  C.schedule('similar');
  return chosen ? othersOf(chosen) : 0;
});
