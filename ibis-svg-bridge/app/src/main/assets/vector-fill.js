/* Vectora SVG: contiguous-region raster sampling -> SVG vector contours.
 * Raster image is used only to detect boundaries; output is path geometry.
 * No third-party dependencies; deterministic for tests / Android WebView.
 */
(function (root, factory) {
  "use strict";
  var api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root) root.VectorFill = api;
})(typeof window !== "undefined" ? window : null, function () {
  "use strict";
  function extract(imageData, width, height, x, y, tolerance, vb) {
    width = width | 0; height = height | 0;
    x = Math.floor(x); y = Math.floor(y);
    if (!imageData || !imageData.data || width < 1 || height < 1 ||
        width * height > 2100000 || x < 0 || y < 0 || x >= width || y >= height)
      throw new Error("Недопустимые размеры или координаты заливки");
    var pixels = imageData.data;
    if (pixels.length < width * height * 4) throw new Error("Данные рисунка повреждены");
    var alpha = Math.max(0, Math.min(80, Number(tolerance) || 0));
    var start = (y * width + x) * 4;
    var rr = pixels[start], gg = pixels[start+1], bb = pixels[start+2], aa = pixels[start+3];
    var transparent = aa <= 8;
    function near(idx) {
      var q = idx * 4, a = pixels[q+3];
      if (transparent) return a <= Math.max(10, alpha);
      if (Math.abs(a - aa) > alpha) return false;
      if (a < 8) return false;
      return Math.max(Math.abs(pixels[q]-rr), Math.abs(pixels[q+1]-gg),
          Math.abs(pixels[q+2]-bb)) <= alpha;
    }
    var count = width * height;
    var selected = new Uint8Array(count);
    var queue = new Int32Array(count), head = 0, tail = 0;
    var rootIndex = y * width + x;
    selected[rootIndex] = 1; queue[tail++] = rootIndex;
    while (head < tail) {
      var n = queue[head++], col = n % width, row = (n / width) | 0, m;
      if (col > 0) {
        m = n - 1; if (!selected[m] && near(m)) { selected[m] = 1; queue[tail++] = m; }
      }
      if (col + 1 < width) {
        m = n + 1; if (!selected[m] && near(m)) { selected[m] = 1; queue[tail++] = m; }
      }
      if (row > 0) {
        m = n - width; if (!selected[m] && near(m)) { selected[m] = 1; queue[tail++] = m; }
      }
      if (row + 1 < height) {
        m = n + width; if (!selected[m] && near(m)) { selected[m] = 1; queue[tail++] = m; }
      }
    }
    var edgeCap = 150000, edges = [], outgoing = new Map();
    function edge(x0,y0,x1,y1,dir) {
      if (edges.length >= edgeCap) throw new Error("Область слишком сложная для векторизации");
      var index=edges.length, key=y0*(width+1)+x0;
      edges.push({x0:x0,y0:y0,x1:x1,y1:y1,end:y1*(width+1)+x1,dir:dir});
      var arr=outgoing.get(key); if (!arr) { arr=[]; outgoing.set(key,arr); } arr.push(index);
    }
    for (var j=0;j<tail;j++) {
      var k=queue[j], px=k%width, py=(k/width)|0;
      if (py===0 || !selected[k-width]) edge(px,py,px+1,py,0);
      if (px+1===width || !selected[k+1]) edge(px+1,py,px+1,py+1,1);
      if (py+1===height || !selected[k+width]) edge(px+1,py+1,px,py+1,2);
      if (px===0 || !selected[k-1]) edge(px,py+1,px,py,3);
    }
    var visited = new Uint8Array(edges.length);
    var view=vb||{x:0,y:0,width:width,height:height};
    var sx=view.width/width, sy=view.height/height;
    function point(p) {
      return (view.x + p.x0*sx).toFixed(3)+" "+(view.y+p.y0*sy).toFixed(3);
    }
    var contours = [], totalPoints = 0;
    for (var i=0;i<edges.length;i++) {
      if (visited[i]) continue;
      var ring=[], current=i, beginKey=edges[i].y0*(width+1)+edges[i].x0, guard=0;
      while (!visited[current]) {
        var e=edges[current]; visited[current]=1;
        ring.push(e); guard++;
        if (e.end===beginKey) break;
        var choices=outgoing.get(e.end)||[], best=-1, rank=99;
        for (var t=0;t<choices.length;t++) {
          var candidate=choices[t];
          if (visited[candidate]) continue;
          var turn=(edges[candidate].dir-e.dir+4)%4;
          var order=turn===1?0:turn===0?1:turn===3?2:3;
          if (order<rank){best=candidate;rank=order;}
        }
        if (best<0 || guard>edgeCap) throw new Error("Не удалось замкнуть контур");
        current=best;
      }
      if(ring.length<3)continue;
      // Remove collinear grid points to keep output vector paths reasonably small.
      var simplified=[];
      for (var a=0;a<ring.length;a++) {
        var p=ring[(a+ring.length-1)%ring.length], q=ring[a], r=ring[(a+1)%ring.length];
        var dx1=q.x0-p.x0,dy1=q.y0-p.y0,dx2=r.x0-q.x0,dy2=r.y0-q.y0;
        if(dx1*dy2!==dy1*dx2)simplified.push(q);
      }
      if(simplified.length<3)continue;
      totalPoints+=simplified.length;
      if(totalPoints>60000)throw new Error("Слишком много точек в заливке");
      contours.push("M "+simplified.map(point).join(" L ")+" Z");
    }
    if(!contours.length)throw new Error("Не удалось создать контур заливки");
    return {path:contours.join(" "),pixels:tail,contours:contours.length,
            points:totalPoints,geometry:"vector",tolerance:alpha};
  }
  return {extract:extract};
});
