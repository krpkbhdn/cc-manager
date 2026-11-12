/* Module 20: annotation pins laid over the page, setAnnotations and focusAnnotation. */
var safe = C.safe, send = C.send, shadow = C.shadow, schedule = C.schedule;
var PIN_SIZE = 20, PIN_GAP = 2, PINS_PER_ROW = 6;
var COLORS = { open: '#F2B93B', sent: '#D97757', done: '#3DD68C' };
var pins = [];            // {ann, el, node, box, rect}
var selectedId = null;
var hoveredId = null;

// Pins sharing a corner position (one element, or parent and child) fan out along the top edge, so their count is visible.
C.layer('pins', function () {
  var seen = new Map();   // pins sharing a rounded corner position, whatever their element
  pins.forEach(function (p) {
    var n = p.node, b = p.box, x, y, w = 0, h = 0, lost = false;
    if (p.el && p.el.isConnected) {
      var r = p.el.getBoundingClientRect();
      x = r.left; y = r.top; w = r.width; h = r.height;
      if (r.width === 0 && r.height === 0) lost = true;
    } else lost = true;
    if (lost) {
      var a = p.ann.anchor && p.ann.anchor.rect;
      if (!a) { n.style.display = 'none'; b.style.display = 'none'; return; }
      x = a.x - W.scrollX; y = a.y - W.scrollY; w = a.w; h = a.h;
    }
    p.rect = { x: x, y: y, w: w, h: h };
    var sel = p.ann.id === selectedId;
    b.style.display = lost || w <= 0 || h <= 0 ? 'none' : 'block';
    b.style.cssText += ';left:' + x + 'px;top:' + y + 'px;width:' + w + 'px;height:' + h + 'px;outline:' + (sel ? '2px solid ' : '1px solid ') + p.color + ';outline-offset:' + (sel ? '1px' : '0');
    var radii = lost ? null : C.radiiOf(p.el, w, h);   // the outline follows the element's corners (cached)
    b.style.borderRadius = radii ? radii.map(function (c) { return c.x + 'px'; }).join(' ') + ' / ' + radii.map(function (c) { return c.y + 'px'; }).join(' ') : '';
    b.style.background = sel ? p.color + '33' : 'transparent';
    n.style.transform = sel ? 'scale(1.3)' : '';
    n.style.boxShadow = sel ? '0 0 0 3px ' + p.color + ', 0 1px 4px rgba(0,0,0,.5)' : '';
    n.classList.toggle('lost', lost);
    var top = h > 0 && y < 0 && y + h > 0 ? 0 : y;   // a tall element scrolled past its top keeps its pins at the viewport edge
    var key = Math.round(x) + '|' + Math.round(top), i = seen.get(key) || 0, step = PIN_SIZE + PIN_GAP, col = i % PINS_PER_ROW;
    seen.set(key, i + 1);
    var atRight = x - PIN_SIZE / 2 >= W.innerWidth - PIN_SIZE;   // fan leftwards from the edge so clamped pins do not stack
    var left = atRight ? W.innerWidth - PIN_SIZE - col * step : x - PIN_SIZE / 2 + col * step;
    var pinTop = top - PIN_SIZE / 2 + Math.floor(i / PINS_PER_ROW) * step;
    var visible = left >= -PIN_SIZE && left <= W.innerWidth && pinTop >= -PIN_SIZE && pinTop <= W.innerHeight && (!atRight || x <= W.innerWidth);
    n.style.display = visible ? 'block' : 'none';
    n.style.left = Math.max(0, Math.min(W.innerWidth - PIN_SIZE, left)) + 'px';
    n.style.top = Math.max(0, Math.min(W.innerHeight - PIN_SIZE, pinTop)) + 'px';
  });
}, true);

var RETRY_MIN_MS = 300, RETRY_MAX_MS = 10000;
/** Resolves pin anchors: all of them when force, otherwise only the lost ones, each with its own doubling backoff. */
C.reanchor = function (force) {
  C.mount();
  var now = Date.now();
  pins.forEach(function (p) {
    if (!force && p.el && p.el.isConnected) return;
    if (!force && now < p.retryAt) return;
    p.el = safe(C.resolveAnchor, null)(p.ann.anchor);
    p.retryDelay = p.el ? RETRY_MIN_MS : Math.min(RETRY_MAX_MS, (p.retryDelay || RETRY_MIN_MS / 2) * 2);
    p.retryAt = p.el ? 0 : now + p.retryDelay;
  });
  schedule();
};

C.api('setAnnotations', function (list) {
  C.mount();
  pins.forEach(function (p) { p.node.remove(); p.box.remove(); });
  hoveredId = null;
  pins = (Array.isArray(list) ? list : []).map(function (ann) {
    var node = document.createElement('div');
    node.className = 'pin';
    node.textContent = String(ann.n);
    var color = COLORS[String(ann.status).toLowerCase()] || COLORS.open;
    node.style.background = color;
    var box = document.createElement('div');
    box.style.cssText = 'position:fixed;pointer-events:none;display:none;box-sizing:border-box';
    var p = { ann: ann, el: null, node: node, box: box, color: color, rect: null, retryAt: 0, retryDelay: 0 };
    node.addEventListener('click', function (e) { e.stopPropagation(); send({ type: 'annotationClick', id: ann.id }); });
    node.addEventListener('mouseenter', function () {
      hoveredId = ann.id;
      send({ type: 'annotationHover', id: ann.id, rect: p.rect });
    });
    node.addEventListener('mouseleave', function () {
      if (hoveredId === ann.id) { hoveredId = null; send({ type: 'annotationLeave' }); }
    });
    shadow.appendChild(box);
    shadow.appendChild(node);
    return p;
  });
  C.reanchor(true);
});

C.api('focusAnnotation', function (id) {
  var p = pins.filter(function (x) { return x.ann.id === id; })[0];
  var el = p && (p.el && p.el.isConnected ? p.el : C.resolveAnchor(p.ann.anchor));
  if (!el) return false;
  el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
  requestAnimationFrame(safe(function () {
    var r = el.getBoundingClientRect(), f = document.createElement('div');
    f.className = 'flash';
    f.style.cssText = 'left:' + r.left + 'px;top:' + r.top + 'px;width:' + r.width + 'px;height:' + r.height + 'px';
    shadow.appendChild(f);
    setTimeout(function () { f.remove(); }, 1300);
    schedule();
  }));
  return true;
});

C.api('selectAnnotation', function (id) {
  selectedId = id ? String(id) : null;
  schedule();
});
