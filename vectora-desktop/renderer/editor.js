
(function(){"use strict";
var ns="http://www.w3.org/2000/svg";
var $=function(id){return document.getElementById(id);};
var board=$("board"),art=$("art"),selected=null,drag=null,draw=null,mode="select",working=false;
var viewport=$("canvasViewport"),zoom=1,panX=0,panY=0,spaceDown=false,panGesture=null,resizeGesture=null;
var selectionFrame=$("selectionFrame"),raf=0;
var editorName="Без названия.svg",projectDirty=false;
const desktop=window.vectoraDesktop;
if(desktop){
 window.AndroidBridge={
  openSvg:()=>desktop.openSvg().then(function(file){if(file){editorName=file.name||"Документ.svg";loadSvg(file.source);updateDocumentTitle();}}).catch(fail),
  importOverlaySvg:()=>desktop.importSvg().then(function(file){if(file)importOverlaySvg(file.source,file.name);}).catch(fail),
  autosave:(svg)=>desktop.autosave(svg).catch(fail),
  saveSvg:(svg)=>desktop.saveSvg(svg).then(function(dest){if(dest){projectDirty=false;updateDocumentTitle();notify("SVG сохранён: "+dest);}}).catch(fail),
  exportPng:(png)=>desktop.exportPng(png).then(function(dest){if(dest)notify("PNG сохранён: "+dest);}).catch(fail)
 };
}
var undo=[],redo=[],maskId=null,contentGroup=null;
var overlayTouches=new Map(),overlayGesture=null,overlayDrag=null;
var ids={select:"selectTool",brush:"brushTool",bucket:"bucketTool",eyedrop:"eyedropTool",eraser:"eraserTool"};
var allowedTags=new Set(["svg","g","defs","rect","circle","ellipse","line","polyline","polygon","path","text","tspan","linearGradient","radialGradient","stop","clipPath","mask","use","symbol","pattern","filter","feGaussianBlur","feOffset","feBlend","feComposite","feFlood","feColorMatrix","feMerge","feMergeNode","feComponentTransfer","feFuncR","feFuncG","feFuncB","feFuncA","feDropShadow"]);
var allowedAttrs=new Set(["id","viewBox","width","height","x","y","x1","y1","x2","y2","cx","cy","r","rx","ry","d","points","transform","fill","fill-rule","fill-opacity","stroke","stroke-width","stroke-opacity","stroke-linecap","stroke-linejoin","stroke-dasharray","stroke-dashoffset","opacity","font-size","font-weight","font-family","text-anchor","dominant-baseline","offset","stop-color","stop-opacity","gradientUnits","gradientTransform","spreadMethod","clip-path","mask","href","preserveAspectRatio","display","color","vector-effect","role","filter","filterUnits","primitiveUnits","stdDeviation","dx","dy","in","in2","result","values","type","mode","operator","k1","k2","k3","k4","slope","intercept","amplitude","exponent","tableValues","flood-color","flood-opacity","patternUnits","patternContentUnits","patternTransform","refX","refY","maskUnits","maskContentUnits","data-vectora-content","data-mask-id","data-vectora-background","data-vectora-overlay","data-vectora-x","data-vectora-y","data-vectora-scale","data-vectora-base-scale","data-vectora-width","data-vectora-height","data-vectora-label"]);
var styleProps=new Set(["fill","fill-opacity","fill-rule","stroke","stroke-width","stroke-opacity","stroke-linecap","stroke-linejoin","opacity","font-size","font-family","font-weight","text-anchor","stop-color","stop-opacity","display","filter"]);
var editTags=new Set(["rect","circle","ellipse","line","polyline","polygon","path","text","use"]);
function notify(s){$("status").textContent=s;}
function fail(e){notify("Ошибка: "+(e&&e.message?e.message:e));}
function clamp(v,a,b){return Math.min(b,Math.max(a,v));}
function safeValue(value){
 if(/javascript:|data:|@import|expression\s*\(|[<>]/i.test(value))return false;
 var urls=value.match(/url\s*\([^)]*\)/gi)||[];
 return urls.every(function(u){return /^url\s*\(\s*['"]?#[a-zA-Z0-9_.:-]+['"]?\s*\)$/i.test(u);});
}
function sanitize(xml){
 if(typeof xml!=="string"||xml.length>8388608)throw Error("SVG больше 8 МиБ");
 if(/<!DOCTYPE|<!ENTITY/i.test(xml))throw Error("DTD запрещены");
 var doc=new DOMParser().parseFromString(xml,"image/svg+xml");
 var root=doc.documentElement;
 if(doc.querySelector("parsererror")||!root||root.localName!=="svg"||root.namespaceURI!==ns)throw Error("Неверный SVG");
 var count=0;
 function walk(el){
  if(++count>12000)throw Error("Слишком много SVG-объектов");
  Array.from(el.attributes).forEach(function(a){
   var name=a.name,local=a.localName,value=a.value;
   if(name==="xmlns"||name==="xmlns:xlink")return;
   if(name==="style"){
    var safe=value.split(";").map(function(part){
     var i=part.indexOf(":");if(i<1)return "";
     var k=part.slice(0,i).trim().toLowerCase(),v=part.slice(i+1).trim();
     return styleProps.has(k)&&safeValue(v)?k+":"+v:"";
    }).filter(Boolean).join(";");
    if(safe)el.setAttribute("style",safe);else el.removeAttribute("style");
   }else if(!allowedAttrs.has(local)||!safeValue(value)||(local==="href"&&!/^#[a-zA-Z0-9_.:-]+$/.test(value)))el.removeAttribute(name);
  });
  Array.from(el.children).forEach(function(child){
   if(child.namespaceURI!==ns||!allowedTags.has(child.localName)){child.remove();return;}
   walk(child);
  });
 }
 walk(root);
 var result=document.importNode(root,true);
 result.setAttribute("id","art");
 if(!result.hasAttribute("viewBox")){
  var w=parseFloat(result.getAttribute("width"))||1024,h=parseFloat(result.getAttribute("height"))||768;
  result.setAttribute("viewBox","0 0 "+Math.max(1,w)+" "+Math.max(1,h));
 }
 var v=result.viewBox.baseVal;
 if(!v.width||!v.height||v.width>1000000||v.height>1000000)throw Error("Некорректная область SVG");
 return result;
}
function snapshot(){
 var clone=art.cloneNode(true);
 clone.querySelectorAll("[data-picked]").forEach(function(n){n.removeAttribute("data-picked");});
 clone.removeAttribute("data-picked");
 clone.setAttribute("xmlns",ns);
 return new XMLSerializer().serializeToString(clone);
}
var saveTimer=null;
function persist(){
 renderLayers();refreshSelectionFrame();
 if(saveTimer)clearTimeout(saveTimer);
 saveTimer=setTimeout(function(){
  try{var data=snapshot();if(window.AndroidBridge)AndroidBridge.autosave(data);}catch(e){fail(e);}
 },190);
}
function remember(){undo.push(snapshot());if(undo.length>30)undo.shift();redo=[];projectDirty=true;updateDocumentTitle();}
function clearSelection(){
 if(selected&&selected.isConnected)selected.removeAttribute("data-picked");
 selected=null;$("selectedPanel").hidden=true;$("textValue").hidden=true;
 $("overlayControls").hidden=true;$("applyColor").hidden=false;
 $("selectedPlaceholder").hidden=false;refreshSelectionFrame();renderLayers();
}
function pick(el){
 clearSelection();
 if(!el)return;
 selected=el;selected.setAttribute("data-picked","true");
 $("selectedPanel").hidden=false;$("selectedPlaceholder").hidden=true;
 var isOverlay=el.getAttribute("data-vectora-overlay")==="true";
 $("selectedName").textContent=isOverlay?"Наложенный SVG":"Выбрано: "+el.localName;
 $("overlayControls").hidden=!isOverlay;
 $("applyColor").hidden=isOverlay;
 if(isOverlay)updateOverlayScaleUi(el);
 $("textValue").hidden=el.localName!=="text";
 if(el.localName==="text")$("textValue").value=el.textContent;
 refreshSelectionFrame();renderLayers();
}
function loadSvg(xml,reset){
 try{
  var parsed=sanitize(xml);
  board.replaceChild(parsed,art);art=parsed;maskId=null;contentGroup=null;
  var g=Array.from(art.children).find(function(n){return n.getAttribute("data-vectora-content")==="true";});
  if(g){contentGroup=g;maskId=g.getAttribute("data-mask-id");}
  // Upgrade saved v0.4.0 documents: remove overlays from the old masked base.
  if(!window.VectorLayers)throw Error("Модуль слоёв не загружен");
  VectorLayers.repair(art,contentGroup);
  if(Array.from(art.children).some(VectorLayers.isOverlay))ensureBaseLayer();
  clearSelection();drag=null;draw=null;overlayTouches.clear();overlayGesture=null;overlayDrag=null;
  syncCanvas(reset!==false);renderLayers();
  if(reset!==false){undo=[];redo=[];}
  notify("SVG загружен. Редактирование остаётся векторным.");
  persist();
 }catch(e){fail(e);}
}
function newDoc(){
 remember();editorName="Без названия.svg";updateDocumentTitle();loadSvg('<svg xmlns="'+ns+'" viewBox="0 0 1024 768" width="1024" height="768"></svg>',true);
 notify("Создан новый векторный документ 1024 × 768");
}
function setTool(tool){
 if(working)return;
 mode=tool;
 Object.keys(ids).forEach(function(k){$(ids[k]).classList.toggle("active",k===mode);});
 var help={select:"Выбор: нажми на фигуру и перетаскивай.",brush:"Кисть: рисуй пальцем или стилусом; линии — SVG пути.",bucket:"Заливка: нажми внутри замкнутой области; результат — SVG контур.",eyedrop:"Пипетка: нажми на нужный цвет рисунка.",eraser:"Ластик: стирай пальцем; используется векторная маска."};
 notify(help[mode]||"Инструмент готов");
}
function currentHex(){return $("paintColor").value.toLowerCase();}
function currentAlpha(){return clamp(parseInt($("alphaInput").value,10)/100,0,1);}
function setColor(hex,alpha){
 if(!/^#[0-9a-fA-F]{6}$/.test(hex))throw Error("Используй цвет #RRGGBB");
 $("paintColor").value=hex.toLowerCase();
 $("hexInput").value=hex.toUpperCase();
 if(alpha!==undefined)$("alphaInput").value=Math.round(clamp(alpha,0,1)*100);
 $("alphaOut").textContent=$("alphaInput").value+"%";
}
function view(){var v=art.viewBox.baseVal;return{x:v.x,y:v.y,width:v.width,height:v.height};}
function point(ev,element){
 var p=art.createSVGPoint();p.x=ev.clientX;p.y=ev.clientY;
 var matrix=(element||art).getScreenCTM();if(!matrix)throw Error("Ошибка координат");
 return p.matrixTransform(matrix.inverse());
}
function fmt(p){return p.x.toFixed(2)+" "+p.y.toFixed(2);}
function container(){return contentGroup&&contentGroup.isConnected?contentGroup:art;}
function ensureBaseLayer(){
 if(!window.VectorLayers)throw Error("Модуль слоёв не загружен");
 contentGroup=VectorLayers.ensureBase(art,contentGroup,function(){
  return document.createElementNS(ns,"g");
 });
 return contentGroup;
}
function newId(prefix){var i=0,id=prefix;while(document.getElementById(id))id=prefix+"-"+(++i);return id;}
function overlayState(el){
 var state={
  x:Number(el.getAttribute("data-vectora-x")),
  y:Number(el.getAttribute("data-vectora-y")),
  scale:Number(el.getAttribute("data-vectora-scale")),
  baseScale:Number(el.getAttribute("data-vectora-base-scale")),
  width:Number(el.getAttribute("data-vectora-width")),
  height:Number(el.getAttribute("data-vectora-height"))
 };
 if(!Number.isFinite(state.x)||!Number.isFinite(state.y)||
    !Number.isFinite(state.scale)||state.scale<=0||
    !Number.isFinite(state.baseScale)||state.baseScale<=0||
    !Number.isFinite(state.width)||state.width<=0||
    !Number.isFinite(state.height)||state.height<=0)throw Error("Некорректные размеры наложенного SVG");
 return state;
}
function updateOverlayScaleUi(el){
 var state=overlayState(el);
 var pct=Math.round(state.scale/state.baseScale*100);
 $("overlayScale").value=clamp(pct,5,1000);
 $("overlayScaleValue").textContent=pct+"%";
 $("overlayPercent").value=pct;
 $("overlayX").value=Math.round(state.x*100)/100;
 $("overlayY").value=Math.round(state.y*100)/100;
 $("overlayWidth").value=Math.round(state.width*state.scale*100)/100;
 $("overlayHeight").value=Math.round(state.height*state.scale*100)/100;
}
function applyOverlayTransform(el,state){
 if(!window.VectorOverlay)throw Error("Модуль масштабирования SVG недоступен");
 if(!Number.isFinite(state.x)||!Number.isFinite(state.y)||
    !Number.isFinite(state.scale)||state.scale<=0)throw Error("Некорректное преобразование");
 el.setAttribute("data-vectora-x",state.x.toFixed(5));
 el.setAttribute("data-vectora-y",state.y.toFixed(5));
 el.setAttribute("data-vectora-scale",state.scale.toFixed(8));
 el.setAttribute("data-vectora-base-scale",state.baseScale.toFixed(8));
 el.setAttribute("data-vectora-width",state.width);
 el.setAttribute("data-vectora-height",state.height);
 el.setAttribute("transform",VectorOverlay.format(state));
 if(selected===el)updateOverlayScaleUi(el);
 refreshSelectionFrame();
}
function scaleOverlayBy(percent){
 if(!selected||selected.getAttribute("data-vectora-overlay")!=="true")return;
 if(!window.VectorOverlay)return;
 try{
  var old=overlayState(selected);
  var desired=old.baseScale*clamp(percent,5,1000)/100;
  var anchor={x:old.x+old.width*old.scale*.5,y:old.y+old.height*old.scale*.5};
  var next=VectorOverlay.zoomAt(old,desired,anchor);
  applyOverlayTransform(selected,next);persist();
  notify("Масштаб SVG: "+Math.round(next.scale/next.baseScale*100)+"%, без изменения пропорций");
 }catch(e){fail(e);}
}
function renameImportedIds(root,prefix){
 var nodes=[root].concat(Array.from(root.querySelectorAll("*")));
 var mapping=new Map(),counter=0;
 nodes.forEach(function(node){
  var old=node.getAttribute("id");
  if(!old)return;
  var fresh=prefix+"-ref-"+(++counter);
  if(!mapping.has(old))mapping.set(old,fresh);
  node.setAttribute("id",fresh);
 });
 nodes.forEach(function(node){
  Array.from(node.attributes).forEach(function(attr){
   if(attr.name==="id")return;
   var value=attr.value;
   if(attr.localName==="href" && value.charAt(0)==="#" && mapping.has(value.slice(1)))
    value="#"+mapping.get(value.slice(1));
   value=value.replace(/url\s*\(\s*(['"]?)#([^'")]+)\1\s*\)/g,function(match,q,id){
    return mapping.has(id)?"url(#"+mapping.get(id)+")":match;
   });
   if(value!==attr.value)node.setAttribute(attr.name,value);
  });
 });
}
function importOverlaySvg(source,label){
 try{
  if(working)throw Error("Дождись окончания текущего действия");
  if(!window.VectorOverlay)throw Error("Модуль импорта SVG недоступен");
  var imported=sanitize(source),v=view(),iv=imported.viewBox.baseVal;
  var width=iv.width,height=iv.height;
  if(!Number.isFinite(width)||!Number.isFinite(height)||width<=0||height<=0)
   throw Error("Импортированный SVG не имеет допустимого viewBox");
  var initial=VectorOverlay.fit(v,{width:width,height:height},.65);
  var group=document.createElementNS(ns,"g");
  group.id=newId("vectora-import");
  group.setAttribute("data-vectora-overlay","true");
  group.setAttribute("data-vectora-label",String(label||"Импорт SVG").slice(0,120));
  // Keep nested SVG viewport + defs so gradients, masks, filters and strokes remain vector.
  imported.removeAttribute("id");
  renameImportedIds(imported,group.id);
  imported.setAttribute("x","0");imported.setAttribute("y","0");
  imported.setAttribute("width",width);imported.setAttribute("height",height);
  group.appendChild(imported);
  var previousRedo=redo.slice();
  remember();
  ensureBaseLayer();
  applyOverlayTransform(group,initial);
  art.appendChild(group); // Imported SVG is never a child of the masked base layer.
  VectorLayers.repair(art,contentGroup);
  var output=snapshot();
  if(output.length>8388608){
   group.remove();undo.pop();redo=previousRedo;
   throw Error("Общий SVG превышает лимит 8 МиБ");
  }
  setTool("select");
  pick(group);persist();renderLayers();
  notify("SVG наложен поверх рисунка. Перетащи или измени общий масштаб двумя пальцами / ползунком.");
 }catch(e){fail(e);}
}
function eraseMask(){
 // Erasing affects only the base drawing, and cannot cut through imported SVG.
 var base=ensureBaseLayer();
 if(maskId){
  var existing=Array.from(art.querySelectorAll("mask")).find(function(e){return e.id===maskId;});
  if(existing){VectorLayers.repair(art,base);return existing;}
 }
 var defs=Array.from(art.children).find(function(el){return el.localName==="defs";});
 if(!defs){defs=document.createElementNS(ns,"defs");art.insertBefore(defs,art.firstChild);}
 var mask=document.createElementNS(ns,"mask"),v=view();
 maskId=newId("vectora-eraser");mask.id=maskId;
 mask.setAttribute("maskUnits","userSpaceOnUse");
 mask.setAttribute("maskContentUnits","userSpaceOnUse");
 ["x","y","width","height"].forEach(function(k){mask.setAttribute(k,v[k]);});
 var white=document.createElementNS(ns,"rect");
 ["x","y","width","height"].forEach(function(k){white.setAttribute(k,v[k]);});
 white.setAttribute("fill","#fff");mask.appendChild(white);defs.appendChild(mask);
 base.setAttribute("data-mask-id",maskId);
 base.setAttribute("mask","url(#"+maskId+")");
 VectorLayers.repair(art,base);
 return mask;
}
function startInk(ev){
 remember();
 var erase=mode==="eraser",parent=erase?eraseMask():container();
 var el=document.createElementNS(ns,"path"),p=point(ev,art);
 el.setAttribute("fill","none");
 el.setAttribute("stroke",erase?"#000000":currentHex());
 el.setAttribute("stroke-width",$("sizeInput").value);
 el.setAttribute("stroke-opacity",erase?"1":currentAlpha());
 el.setAttribute("stroke-linecap","round");
 el.setAttribute("stroke-linejoin","round");
 el.setAttribute("d","M "+fmt(p)+" l 0.01 0");
 parent.appendChild(el);
 draw={id:ev.pointerId,el:el,points:[p],previous:p};
}
function appendInk(ev){
 if(!draw||ev.pointerId!==draw.id)return;
 var p=point(ev,art),prev=draw.previous,dx=p.x-prev.x,dy=p.y-prev.y;
 if(dx*dx+dy*dy<.25||draw.points.length>=6000)return;
 draw.points.push(p);draw.previous=p;
 var points=draw.points,d="M "+fmt(points[0]);
 // Quadratic interpolation smooths stylus strokes but retains pure SVG geometry.
 if(points.length===2){d+=" L "+fmt(points[1]);}
 else{
  for(var i=1;i<points.length-1;i++){
   var end={x:(points[i].x+points[i+1].x)/2,y:(points[i].y+points[i+1].y)/2};
   d+=" Q "+fmt(points[i])+" "+fmt(end);
  }
  d+=" L "+fmt(points[points.length-1]);
 }
 draw.el.setAttribute("d",d);
}
function stopInk(){if(draw){draw=null;persist();notify(mode==="eraser"?"Стирание записано в SVG-маску.":"Векторная кисть: штрих добавлен.");}}
function drawShape(type){
 if(working)return;
 remember();var v=view(),el=document.createElementNS(ns,type);
 if(type==="rect"){el.setAttribute("x",v.x+v.width*.3);el.setAttribute("y",v.y+v.height*.3);el.setAttribute("width",v.width*.35);el.setAttribute("height",v.height*.25);}
 if(type==="ellipse"){el.setAttribute("cx",v.x+v.width*.5);el.setAttribute("cy",v.y+v.height*.5);el.setAttribute("rx",v.width*.19);el.setAttribute("ry",v.height*.15);}
 if(type==="line"){el.setAttribute("x1",v.x+v.width*.3);el.setAttribute("y1",v.y+v.height*.3);el.setAttribute("x2",v.x+v.width*.7);el.setAttribute("y2",v.y+v.height*.65);}
 if(type==="text"){el.setAttribute("x",v.x+v.width*.3);el.setAttribute("y",v.y+v.height*.5);el.setAttribute("font-size",Math.min(v.width,v.height)*.09);el.textContent="Текст";}
 el.setAttribute("fill",type==="line"?"none":currentHex());
 el.setAttribute("fill-opacity",currentAlpha());
 el.setAttribute("stroke",currentHex());el.setAttribute("stroke-width",Math.min(6,Number($("sizeInput").value)));
 container().appendChild(el);pick(el);persist();setTool("select");
}
function findEditable(target){
 // Treat an imported SVG as one transformable vector group, never isolated children.
 var el=target,insideOverlay=null,shape=null;
 while(el&&el!==art){
  if(el.getAttribute && el.getAttribute("data-vectora-overlay")==="true")insideOverlay=el;
  else if(!shape&&el.localName&&editTags.has(el.localName))shape=el;
  el=el.parentNode;
 }
 return insideOverlay||shape||null;
}
function renderCanvas(maxSide,maxArea){
 return new Promise(function(resolve,reject){
  try{
   var v=view(),scale=Math.min(16,maxSide/Math.max(v.width,v.height),Math.sqrt(maxArea/(v.width*v.height)));
   var w=Math.max(1,Math.round(v.width*scale)),h=Math.max(1,Math.round(v.height*scale));
   var clone=art.cloneNode(true);
   clone.querySelectorAll("[data-picked]").forEach(function(n){n.removeAttribute("data-picked");});
   clone.setAttribute("xmlns",ns);clone.setAttribute("width",w);clone.setAttribute("height",h);
   var xml=new XMLSerializer().serializeToString(clone);
   var url=URL.createObjectURL(new Blob([xml],{type:"image/svg+xml;charset=utf-8"}));
   var img=new Image();
   img.onload=function(){
    URL.revokeObjectURL(url);
    try{
     var canvas=document.createElement("canvas");canvas.width=w;canvas.height=h;
     var context=canvas.getContext("2d",{willReadFrequently:true});
     if(!context)throw Error("Canvas недоступен");
     context.clearRect(0,0,w,h);context.drawImage(img,0,0,w,h);
     resolve({canvas:canvas,ctx:context,v:v,w:w,h:h});
    }catch(e){reject(e);}
   };
   img.onerror=function(){URL.revokeObjectURL(url);reject(Error("SVG не удалось отобразить"));};
   img.src=url;
  }catch(e){reject(e);}
 });
}
function getPixel(p,r){
 var ix=Math.floor((p.x-r.v.x)/r.v.width*r.w),iy=Math.floor((p.y-r.v.y)/r.v.height*r.h);
 return{x:ix,y:iy};
}
function setBusy(value){
 working=!!value;$("busy").hidden=!value;
}
async function fillAt(ev){
 if(working)return;
 var p=point(ev,art);setBusy(true);
 try{
  await new Promise(function(resolve){setTimeout(resolve,10);});
  var r=await renderCanvas(1500,1750000),b=getPixel(p,r);
  if(b.x<0||b.x>=r.w||b.y<0||b.y>=r.h)throw Error("Нажми внутри рисунка");
  var data=r.ctx.getImageData(0,0,r.w,r.h);
  if(!window.VectorFill)throw Error("Модуль векторной заливки не найден");
  var region=window.VectorFill.extract(data,r.w,r.h,b.x,b.y,
    Number($("toleranceInput").value),r.v);
  if(region.path.length>1200000)throw Error("Контур слишком сложный; уменьши допуск");
  remember();
  var el=document.createElementNS(ns,"path");
  el.setAttribute("d",region.path);
  el.setAttribute("fill-rule","evenodd");
  el.setAttribute("fill",currentHex());
  el.setAttribute("fill-opacity",currentAlpha());
  el.setAttribute("stroke","none");
  container().appendChild(el);
  persist();
  notify("Заливка: "+region.pixels+" пикселей распознано, "+region.contours+" векторных контуров.");
 }catch(e){fail(e);}finally{setBusy(false);}
}
async function sampleAt(ev){
 if(working)return;
 var p=point(ev,art);setBusy(true);
 try{
  var r=await renderCanvas(1300,1200000),b=getPixel(p,r);
  if(b.x<0||b.y<0||b.x>=r.w||b.y>=r.h)throw Error("Нажми внутри рисунка");
  var d=r.ctx.getImageData(b.x,b.y,1,1).data;
  var hex="#"+[d[0],d[1],d[2]].map(function(n){return n.toString(16).padStart(2,"0");}).join("");
  setColor(hex,d[3]/255);
  notify("Пипетка: "+hex.toUpperCase()+", альфа "+Math.round(d[3]/255*100)+"%");
 }catch(e){fail(e);}finally{setBusy(false);}
}
function overlayDistance(a,b){return Math.hypot(a.x-b.x,a.y-b.y);}
function overlayMidpoint(a,b){return{x:(a.x+b.x)/2,y:(a.y+b.y)/2};}
board.addEventListener("pointerdown",function(ev){
 if(working)return;
 if(mode==="bucket"){if(ev.isPrimary){fillAt(ev);ev.preventDefault();}return;}
 if(mode==="eyedrop"){if(ev.isPrimary){sampleAt(ev);ev.preventDefault();}return;}
 if(mode==="brush"||mode==="eraser"){
  if(!ev.isPrimary)return;
  clearSelection();startInk(ev);
  try{board.setPointerCapture(ev.pointerId);}catch(e){}
  ev.preventDefault();return;
 }
 var target=findEditable(ev.target);
 var isImported=target&&target.getAttribute("data-vectora-overlay")==="true";
 var continued=overlayTouches.size>0&&selected&&
               selected.getAttribute("data-vectora-overlay")==="true"&&
               (target===selected||target===null);
 if(isImported||continued){
  var group=continued?selected:target;
  if(overlayTouches.size===0){
   if(!ev.isPrimary)return;
   remember();pick(group);
  }else if(selected!==group||overlayTouches.size>=2)return;
  var p=point(ev,art);
  overlayTouches.set(ev.pointerId,p);
  if(overlayTouches.size===1){
   overlayDrag={id:ev.pointerId,start:p,initial:overlayState(group),group:group};
   overlayGesture=null;
  }else{
   var pair=Array.from(overlayTouches.values());
   overlayGesture={
    group:group,initial:overlayState(group),
    midpoint:overlayMidpoint(pair[0],pair[1]),
    distance:Math.max(0.001,overlayDistance(pair[0],pair[1]))
   };
   overlayDrag=null;
  }
  try{board.setPointerCapture(ev.pointerId);}catch(e){}
  ev.preventDefault();return;
 }
 if(!ev.isPrimary || overlayTouches.size)return;
 if(!target){clearSelection();return;}
 pick(target);remember();
 var parent=target.parentNode&&target.parentNode.getScreenCTM?target.parentNode:art;
 drag={id:ev.pointerId,el:target,ref:parent,origin:point(ev,parent),initial:target.getAttribute("transform")||""};
 try{board.setPointerCapture(ev.pointerId);}catch(e){}
 ev.preventDefault();
});
board.addEventListener("pointermove",function(ev){
 if(resizeGesture)return;
 if(overlayTouches.has(ev.pointerId)){
  overlayTouches.set(ev.pointerId,point(ev,art));
  if(overlayTouches.size>=2&&overlayGesture){
   var touches=Array.from(overlayTouches.values());
   var center=overlayMidpoint(touches[0],touches[1]);
   var ratio=overlayDistance(touches[0],touches[1])/overlayGesture.distance;
   try{
    applyOverlayTransform(overlayGesture.group,VectorOverlay.pinch(
       overlayGesture.initial,overlayGesture.midpoint,center,ratio));
   }catch(e){fail(e);}
  }else if(overlayDrag&&overlayDrag.id===ev.pointerId){
   var now=overlayTouches.get(ev.pointerId);
   applyOverlayTransform(overlayDrag.group,VectorOverlay.move(
     overlayDrag.initial,now.x-overlayDrag.start.x,now.y-overlayDrag.start.y));
  }
  return;
 }
 if(draw){if(draw.id===ev.pointerId)appendInk(ev);return;}
 if(!drag||drag.id!==ev.pointerId)return;
 var p=point(ev,drag.ref);
 var dx=p.x-drag.origin.x,dy=p.y-drag.origin.y;
 drag.el.setAttribute("transform",(drag.initial+" translate("+dx.toFixed(2)+" "+dy.toFixed(2)+")").trim());
});
function pointerStop(ev){
 if(overlayTouches.has(ev.pointerId)){
  overlayTouches.delete(ev.pointerId);
  overlayGesture=null;
  if(overlayTouches.size===1&&selected&&selected.getAttribute("data-vectora-overlay")==="true"){
   var still=Array.from(overlayTouches.entries())[0];
   overlayDrag={id:still[0],start:still[1],initial:overlayState(selected),group:selected};
  }else if(overlayTouches.size===0){
   overlayDrag=null;persist();
   notify("SVG перемещён или масштабирован с сохранением пропорций.");
  }
  return;
 }
 if(draw&&draw.id===ev.pointerId)stopInk();
 if(drag&&drag.id===ev.pointerId){drag=null;persist();}
}
board.addEventListener("pointerup",pointerStop);
board.addEventListener("pointercancel",pointerStop);
board.addEventListener("lostpointercapture",pointerStop);
function downloadBlob(blob,name){
 var url=URL.createObjectURL(blob),a=document.createElement("a");
 a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();
 setTimeout(function(){URL.revokeObjectURL(url);},2000);
}
function saveSvg(){
 try{
  var source=snapshot();
  if(source.length>8388608)throw Error("SVG слишком большой для сохранения");
  if(window.AndroidBridge)AndroidBridge.saveSvg(source);
  else downloadBlob(new Blob([source],{type:"image/svg+xml;charset=utf-8"}),"drawing.svg");
  notify("SVG готов к сохранению — векторный формат сохранён.");
 }catch(e){fail(e);}
}
async function exportPng(){
 if(working)return;setBusy(true);
 try{
  var r=await renderCanvas(2600,4000000);
  if(window.AndroidBridge)AndroidBridge.exportPng(r.canvas.toDataURL("image/png"));
  else await new Promise(function(resolve,reject){
   r.canvas.toBlob(function(b){if(!b){reject(Error("PNG не создан"));return;}downloadBlob(b,"drawing.png");resolve();},"image/png");
  });
  notify("PNG готов. SVG-оригинал остаётся векторным.");
 }catch(e){fail(e);}finally{setBusy(false);}
}
$("newDoc").onclick=newDoc;
$("openDoc").onclick=function(){if(window.AndroidBridge)AndroidBridge.openSvg();else $("fileInput").click();};
$("overlayDoc").onclick=function(){if(window.AndroidBridge)AndroidBridge.importOverlaySvg();else $("overlayFileInput").click();};
$("saveDoc").onclick=saveSvg;$("exportPng").onclick=exportPng;
$("fileInput").onchange=function(){
 var file=this.files&&this.files[0];if(!file)return;
 if(file.size>8388608){fail("SVG больше 8 МиБ");return;}
 file.text().then(function(s){loadSvg(s);}).catch(fail);
 this.value="";
};
$("overlayFileInput").onchange=function(){
 var file=this.files&&this.files[0];if(!file)return;
 if(file.size>8388608){fail("SVG больше 8 МиБ");return;}
 file.text().then(importOverlaySvg).catch(fail);this.value="";
};
var sliderEditing=false;
$("overlayScale").onpointerdown=function(){
 if(!sliderEditing&&selected){remember();sliderEditing=true;}
};
$("overlayScale").onkeydown=function(){
 if(!sliderEditing&&selected){remember();sliderEditing=true;}
};
$("overlayScale").oninput=function(){
 if(!sliderEditing&&selected){remember();sliderEditing=true;}
 scaleOverlayBy(Number(this.value));
};
$("overlayScale").onchange=function(){sliderEditing=false;};
$("scaleDown").onclick=function(){
 if(!selected)return;remember();
 var state=overlayState(selected);scaleOverlayBy(Math.round(state.scale/state.baseScale*100/1.25));
};
$("scaleUp").onclick=function(){
 if(!selected)return;remember();
 var state=overlayState(selected);scaleOverlayBy(Math.round(state.scale/state.baseScale*100*1.25));
};
Object.keys(ids).forEach(function(k){$(ids[k]).onclick=function(){setTool(k);};});
$("rectTool").onclick=function(){drawShape("rect");};
$("ellipseTool").onclick=function(){drawShape("ellipse");};
$("lineTool").onclick=function(){drawShape("line");};
$("textTool").onclick=function(){drawShape("text");};
$("undoTool").onclick=function(){if(!undo.length)return;redo.push(snapshot());loadSvg(undo.pop(),false);notify("Действие отменено.");};
$("redoTool").onclick=function(){if(!redo.length)return;undo.push(snapshot());loadSvg(redo.pop(),false);notify("Действие повторено.");};
$("deleteShape").onclick=function(){if(!selected)return;remember();selected.remove();clearSelection();persist();notify("Фигура удалена.");};
$("applyColor").onclick=function(){
 if(!selected)return;remember();
 if(selected.localName==="line"){
  selected.setAttribute("stroke",currentHex());selected.setAttribute("stroke-opacity",currentAlpha());
 }else{selected.setAttribute("fill",currentHex());selected.setAttribute("fill-opacity",currentAlpha());}
 persist();notify("Цвет фигуры изменён.");
};
$("textValue").onchange=function(){
 if(!selected||selected.localName!=="text")return;
 remember();selected.textContent=this.value;persist();notify("Текст изменён.");
};
$("paintColor").oninput=function(){setColor(this.value);};
$("hexInput").onchange=function(){
 var value=this.value.trim();
 if(/^#[0-9a-fA-F]{3}$/.test(value))value="#"+value.slice(1).split("").map(function(c){return c+c;}).join("");
 if(/^#[0-9a-fA-F]{8}$/.test(value)){
  setColor(value.slice(0,7),parseInt(value.slice(7),16)/255);return;
 }
 try{setColor(value);}catch(e){fail(e);$("hexInput").value=currentHex().toUpperCase();}
};
$("alphaInput").oninput=function(){$("alphaOut").textContent=this.value+"%";};
$("sizeInput").oninput=function(){$("sizeOut").textContent=this.value;};
$("toleranceInput").oninput=function(){$("toleranceOut").textContent=this.value;};
["#000000","#ffffff","#e54a4a","#f5ad3f","#f5df69","#4ec785","#489dff","#a875e6","#e879ae","#65798d"].forEach(function(color){
 var el=document.createElement("button");el.className="swatch";el.style.background=color;el.title=color;
 el.setAttribute("aria-label","Выбрать "+color);
 el.onclick=function(){setColor(color,1);};
 $("swatches").appendChild(el);
});
// DESKTOP WORKSPACE
function updateDocumentTitle(){
 $("documentTitle").textContent=editorName+(projectDirty?" •":"");
 document.title=(projectDirty?"● ":"")+editorName+" — Vectora SVG";
}
function syncCanvas(fit){
 var v=view();
 board.style.width=v.width+"px";board.style.height=v.height+"px";
 $("canvasDimensions").textContent=Math.round(v.width)+" × "+Math.round(v.height);
 if(fit)fitCanvas();else applyView();
}
function applyView(){
 var v=view(),cx=viewport.clientWidth/2+panX,cy=viewport.clientHeight/2+panY;
 board.style.width=v.width+"px";board.style.height=v.height+"px";
 board.style.left=(cx-v.width*zoom/2)+"px";
 board.style.top=(cy-v.height*zoom/2)+"px";
 board.style.transform="scale("+zoom+")";
 var pct=Math.round(zoom*100);
 $("zoomLabel").textContent=pct+"%";
 $("zoomSlider").value=clamp(pct,5,400);
 refreshSelectionFrame();
}
function fitCanvas(){
 var v=view();
 if(!v.width||!v.height||!viewport.clientWidth||!viewport.clientHeight)return;
 zoom=clamp(Math.min((viewport.clientWidth-110)/v.width,(viewport.clientHeight-100)/v.height),.01,8);
 panX=0;panY=0;applyView();
}
function zoomAt(clientX,clientY,factor){
 var rect=viewport.getBoundingClientRect();
 var px=clientX-rect.left,py=clientY-rect.top;
 var before=zoom,newZoom=clamp(zoom*factor,.01,12);
 var worldX=(px-rect.width/2-panX)/before;
 var worldY=(py-rect.height/2-panY)/before;
 zoom=newZoom;
 panX=px-rect.width/2-worldX*zoom;
 panY=py-rect.height/2-worldY*zoom;
 applyView();
}
function setZoom(percent){
 var next=clamp(Number(percent)/100,.01,12);
 if(Number.isFinite(next)){zoom=next;applyView();}
}
function refreshSelectionFrame(){
 if(raf)cancelAnimationFrame(raf);
 raf=requestAnimationFrame(function(){
  raf=0;
  var el=selected;
  if(!el||!el.isConnected||el.getAttribute("data-vectora-overlay")!=="true"){
   selectionFrame.hidden=true;return;
  }
  try{
   var st=overlayState(el),m=el.getScreenCTM();
   if(!m){selectionFrame.hidden=true;return;}
   var v=viewport.getBoundingClientRect();
   function convert(x,y){var p=art.createSVGPoint();p.x=x;p.y=y;return p.matrixTransform(m);}
   var a=convert(0,0),b=convert(st.width,st.height);
   var x=Math.min(a.x,b.x)-v.left,y=Math.min(a.y,b.y)-v.top;
   var w=Math.abs(a.x-b.x),h=Math.abs(a.y-b.y);
   if(!Number.isFinite(w)||!Number.isFinite(h)||w<1||h<1){selectionFrame.hidden=true;return;}
   selectionFrame.style.left=x+"px";selectionFrame.style.top=y+"px";
   selectionFrame.style.width=w+"px";selectionFrame.style.height=h+"px";
   selectionFrame.hidden=false;
  }catch(e){selectionFrame.hidden=true;}
 });
}
function applyTransformInput(which,val){
 if(!selected||selected.getAttribute("data-vectora-overlay")!=="true")return;
 var n=Number(val),st=overlayState(selected);
 if(!Number.isFinite(n)){fail("Введите число");return;}
 remember();
 if(which==="x"||which==="y"){
  st[which]=n;
 }else{
  var scale=st.scale;
  if(which==="width"){if(n<=0)return;scale=n/st.width;}
  if(which==="height"){if(n<=0)return;scale=n/st.height;}
  if(which==="percent")scale=st.baseScale*clamp(n,5,1000)/100;
  scale=clamp(scale,st.baseScale*.05,st.baseScale*10);
  var anchor={x:st.x+st.width*st.scale/2,y:st.y+st.height*st.scale/2};
  st=VectorOverlay.zoomAt(st,scale,anchor);
 }
 applyOverlayTransform(selected,st);persist();
}
[["overlayX","x"],["overlayY","y"],["overlayWidth","width"],
 ["overlayHeight","height"],["overlayPercent","percent"]].forEach(function(pair){
 $(pair[0]).onchange=function(){applyTransformInput(pair[1],this.value);};
});
$("resetTransform").onclick=function(){applyTransformInput("percent",100);};
function renderLayers(){
 var list=$("layersList");if(!list)return;
 list.replaceChildren();
 var all=Array.from(art.children).filter(function(n){
  return n.getAttribute&&n.getAttribute("data-vectora-overlay")==="true";
 }).reverse();
 $("layersCount").textContent=String(all.length+1);
 all.forEach(function(layer,index){
  var row=document.createElement("button");row.className="layer-row"+(selected===layer?" chosen":"");
  var icon=document.createElement("span");icon.className="layer-ico";icon.textContent="◈";
  var text=document.createElement("span");text.className="layer-text";
  var name=document.createElement("strong");
  name.textContent=layer.getAttribute("data-vectora-label")||"Импорт SVG";
  var meta=document.createElement("small");meta.textContent="Векторный SVG · слой "+(all.length-index);
  text.append(name,meta);row.append(icon,text);
  row.onclick=function(){setTool("select");pick(layer);};
  list.appendChild(row);
 });
 var baseRow=document.createElement("button");baseRow.className="layer-row"+(selected===null?"":"");
 baseRow.disabled=true;
 var ico=document.createElement("span");ico.className="layer-ico";ico.textContent="✎";
 var nameBox=document.createElement("span");nameBox.className="layer-text";
 var name=document.createElement("strong");name.textContent="Основной рисунок";
 var desc=document.createElement("small");desc.textContent="Кисть · фигуры · ластик";
 nameBox.append(name,desc);baseRow.append(ico,nameBox);list.appendChild(baseRow);
}
function changeOverlayOrder(dir){
 if(!selected||selected.getAttribute("data-vectora-overlay")!=="true")return;
 var siblings=Array.from(art.children).filter(VectorLayers.isOverlay);
 var index=siblings.indexOf(selected);
 if(index<0)return;
 if(dir>0&&index<siblings.length-1){
  remember();art.insertBefore(siblings[index+1],selected);
 }else if(dir<0&&index>0){
  remember();art.insertBefore(selected,siblings[index-1]);
 }else{return;}
 persist();renderLayers();refreshSelectionFrame();
}
$("raiseLayer").onclick=function(){changeOverlayOrder(1);};
$("lowerLayer").onclick=function(){changeOverlayOrder(-1);};
function handleResizeStart(ev){
 if(!selected||selected.getAttribute("data-vectora-overlay")!=="true")return;
 var corner=ev.currentTarget.getAttribute("data-corner");
 var st=overlayState(selected);
 var anch={x:st.x+(corner.includes("w")?st.width*st.scale:0),
           y:st.y+(corner.includes("n")?st.height*st.scale:0)};
 remember();
 resizeGesture={id:ev.pointerId,group:selected,corner:corner,
                state:st,anchor:anch};
 ev.currentTarget.setPointerCapture(ev.pointerId);
 ev.stopPropagation();ev.preventDefault();
}
function handleResizeMove(ev){
 if(!resizeGesture||resizeGesture.id!==ev.pointerId)return;
 try{
  var st=resizeGesture.state,corner=resizeGesture.corner,anchor=resizeGesture.anchor;
  var p=point(ev,art);
  var wDir=corner.includes("e")?st.width:-st.width;
  var hDir=corner.includes("s")?st.height:-st.height;
  var projected=((p.x-anchor.x)*wDir+(p.y-anchor.y)*hDir)/(st.width*st.width+st.height*st.height);
  var scale=clamp(projected,st.baseScale*.05,st.baseScale*10);
  var next=Object.assign({},st,{
   scale:scale,
   x:anchor.x-(corner.includes("w")?st.width*scale:0),
   y:anchor.y-(corner.includes("n")?st.height*scale:0)
  });
  applyOverlayTransform(resizeGesture.group,next);
 }catch(e){fail(e);}
 ev.stopPropagation();ev.preventDefault();
}
function handleResizeEnd(ev){
 if(!resizeGesture||resizeGesture.id!==ev.pointerId)return;
 resizeGesture=null;persist();ev.stopPropagation();
}
document.querySelectorAll(".transform-handle").forEach(function(el){
 el.addEventListener("pointerdown",handleResizeStart);
 el.addEventListener("pointermove",handleResizeMove);
 el.addEventListener("pointerup",handleResizeEnd);
 el.addEventListener("pointercancel",handleResizeEnd);
 el.addEventListener("lostpointercapture",handleResizeEnd);
});
viewport.addEventListener("wheel",function(ev){
 ev.preventDefault();
 zoomAt(ev.clientX,ev.clientY,Math.exp(-ev.deltaY*.0014));
},{passive:false});
viewport.addEventListener("pointerdown",function(ev){
 if(ev.button!==1&&!(spaceDown&&ev.button===0))return;
 panGesture={id:ev.pointerId,originX:ev.clientX,originY:ev.clientY,x:panX,y:panY};
 viewport.setPointerCapture(ev.pointerId);
 viewport.style.cursor="grabbing";
 ev.stopPropagation();ev.preventDefault();
},true);
viewport.addEventListener("pointermove",function(ev){
 if(!panGesture||panGesture.id!==ev.pointerId)return;
 panX=panGesture.x+ev.clientX-panGesture.originX;
 panY=panGesture.y+ev.clientY-panGesture.originY;
 applyView();ev.stopPropagation();ev.preventDefault();
},true);
function stopPan(ev){
 if(!panGesture||panGesture.id!==ev.pointerId)return;
 panGesture=null;viewport.style.cursor=spaceDown?"grab":"default";
 ev.stopPropagation();ev.preventDefault();
}
viewport.addEventListener("pointerup",stopPan,true);
viewport.addEventListener("pointercancel",stopPan,true);
$("fitCanvas").onclick=fitCanvas;
$("centerCanvas").onclick=function(){panX=0;panY=0;applyView();};
$("zoomOut").onclick=function(){setZoom(zoom*100/1.25);};
$("zoomIn").onclick=function(){setZoom(zoom*100*1.25);};
$("zoomSlider").oninput=function(){setZoom(this.value);};
new ResizeObserver(function(){applyView();}).observe(viewport);
function keyboard(ev){
 var key=ev.key.toLowerCase();
 var focused=ev.target&&(/^(input|textarea)$/i).test(ev.target.tagName);
 if(key===" "&&!focused){spaceDown=true;viewport.style.cursor="grab";ev.preventDefault();}
 if(ev.ctrlKey||ev.metaKey){
  if(key==="o"){ev.preventDefault();(ev.shiftKey?$("overlayDoc"):$("openDoc")).click();}
  if(key==="s"){ev.preventDefault();$("saveDoc").click();}
  if(key==="n"){ev.preventDefault();$("newDoc").click();}
  if(key==="z"&&!focused){ev.preventDefault();$("undoTool").click();}
  if(key==="y"&&!focused){ev.preventDefault();$("redoTool").click();}
  return;
 }
 if(focused)return;
 if(key==="delete"||key==="backspace"){ev.preventDefault();$("deleteShape").click();return;}
 if(key==="escape"){clearSelection();return;}
 if(key==="f"){fitCanvas();return;}
 var modes={v:"select",b:"brush",g:"bucket",i:"eyedrop",e:"eraser"};
 if(modes[key]){setTool(modes[key]);return;}
 var shapes={r:"rectTool",o:"ellipseTool",l:"lineTool",t:"textTool"};
 if(shapes[key])$(shapes[key]).click();
}
window.addEventListener("keydown",keyboard);
window.addEventListener("keyup",function(ev){
 if(ev.key===" "){spaceDown=false;viewport.style.cursor="default";}
});
window.addEventListener("blur",function(){spaceDown=false;});
var dragDepth=0;
viewport.addEventListener("dragenter",function(ev){ev.preventDefault();dragDepth++;$("dropHint").hidden=false;});
viewport.addEventListener("dragover",function(ev){ev.preventDefault();});
viewport.addEventListener("dragleave",function(ev){ev.preventDefault();dragDepth=Math.max(0,dragDepth-1);if(!dragDepth)$("dropHint").hidden=true;});
viewport.addEventListener("drop",async function(ev){
 ev.preventDefault();dragDepth=0;$("dropHint").hidden=true;
 var file=ev.dataTransfer&&ev.dataTransfer.files&&ev.dataTransfer.files[0];
 if(!file||!/\.svg$/i.test(file.name)){notify("Перетащи файл .svg для векторного импорта");return;}
 if(file.size>8388608){fail("SVG больше 8 МиБ");return;}
 try{importOverlaySvg(await file.text(),file.name);}catch(e){fail(e);}
});
window.addEventListener("dragover",function(e){e.preventDefault();});
window.addEventListener("drop",function(e){e.preventDefault();});
window.editor={loadSvg:loadSvg,importOverlaySvg:importOverlaySvg,showError:fail};
if(desktop){
 desktop.restore().then(function(source){if(source){loadSvg(source);projectDirty=false;updateDocumentTitle();}else fitCanvas();}).catch(fail);
}else{fitCanvas();}
renderLayers();updateDocumentTitle();

})();
