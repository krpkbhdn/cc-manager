/* Module 70: layer stack at a point (context menu), temporary outline of a layer and describe-by-selector. */
var isStableClass = C.isStableClass;
var MAX_LAYERS = 20, MAX_DEPTH = 20;
var outlined = null;

function stackCollect(root, x, y, out, seen, depth) {
  var els = root.elementsFromPoint ? root.elementsFromPoint(x, y) : [];
  for (var i = 0; i < els.length && out.length < MAX_LAYERS; i++) {
    var el = els[i];
    if (el === C.host || seen.has(el)) continue;
    seen.add(el);
    out.push(el);
    if (el.shadowRoot && depth < MAX_DEPTH) stackCollect(el.shadowRoot, x, y, out, seen, depth + 1);
  }
}

function lite(el) {
  var r = el.getBoundingClientRect();
  return {
    selector: C.selectorOf(el), tag: el.localName, id: C.plainToken(el.id || ''),
    stableClasses: C.plainTokens(Array.prototype.filter.call(el.classList || [], isStableClass)).slice(0, 5),
    rect: { x: r.x + W.scrollX, y: r.y + W.scrollY, w: r.width, h: r.height }
  };
}

C.layer('stack', function (ctx) {
  if (!outlined || !outlined.isConnected) return;
  var r = outlined.getBoundingClientRect();
  ctx.fillStyle = 'rgba(80,140,255,0.18)';
  ctx.strokeStyle = 'rgba(80,140,255,0.95)';
  ctx.lineWidth = 2;
  ctx.fillRect(r.x, r.y, r.width, r.height);
  ctx.strokeRect(r.x, r.y, r.width, r.height);
});

C.api('stackAt', function (x, y) {
  var els = [];
  stackCollect(document, x, y, els, new Set(), 0);
  C.send({ type: 'stack', x: x, y: y, items: els.map(lite) });
});
C.api('highlight', function (selector) {
  outlined = null; // cleared first so an empty or unparsable selector never leaves the old outline
  if (selector) outlined = C.safe(C.resolveSelector, null)(selector);
  C.schedule('stack');
});
C.api('describeSelector', function (selector, reason) {
  var el = C.resolveSelector(selector);
  if (el) C.send({ type: 'inspect', reason: String(reason || ''), target: C.describe(el) });
});
