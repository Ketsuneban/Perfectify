const test=require("node:test");
const assert=require("node:assert/strict");
const layers=require("../app/src/main/assets/vector-layers.js");
class Node {
 constructor(name,attributes={}){this.localName=name;this.attrs={...attributes};this.children=[];this.parentNode=null;}
 getAttribute(key){return Object.prototype.hasOwnProperty.call(this.attrs,key)?this.attrs[key]:null;}
 setAttribute(key,val){this.attrs[key]=String(val);}
 appendChild(child){return this.insertBefore(child,null);}
 insertBefore(child,before){
  if(child.parentNode){
   let index=child.parentNode.children.indexOf(child);
   if(index!==-1)child.parentNode.children.splice(index,1);
  }
  child.parentNode=this;
  const i=before===null?-1:this.children.indexOf(before);
  if(before!==null&&i<0)throw Error("Reference node not found");
  if(i===-1)this.children.push(child);
  else this.children.splice(i,0,child);
  return child;
 }
}
function root(){return new Node("svg");}
function defs(){return new Node("defs");}
function base(){return new Node("g",{"data-vectora-content":"true","mask":"url(#eraser)"});}
function importSvg(){return new Node("g",{"data-vectora-overlay":"true","transform":"translate(1 2) scale(0.4)"});}
function factory(){return new Node("g");}
test("v0.4.0 legacy: imported SVG is removed from masked base and drawn on top",()=>{
 const svg=root(),d=svg.appendChild(defs()),g=svg.appendChild(base());
 const painting=g.appendChild(new Node("path")),overlay=g.appendChild(importSvg());
 const count=layers.repair(svg,g);
 assert.equal(count,1);
 assert.deepEqual(svg.children,[d,g,overlay]);
 assert.deepEqual(g.children,[painting]);
 assert.equal(g.getAttribute("mask"),"url(#eraser)");
 assert.equal(overlay.getAttribute("transform"),"translate(1 2) scale(0.4)");
});
test("when erasing after overlays imported, base wraps only non-overlay children",()=>{
 const svg=root(),d=svg.appendChild(defs()),painting=svg.appendChild(new Node("path"));
 const overlay=svg.appendChild(importSvg());
 const g=layers.ensureBase(svg,null,factory);
 assert.deepEqual(svg.children,[d,g,overlay]);
 assert.deepEqual(g.children,[painting]);
 assert.equal(overlay.parentNode,svg);
});
test("when mask exists before importing, overlay never enters masked content",()=>{
 const svg=root(),g=svg.appendChild(base());
 g.appendChild(new Node("path"));
 const overlay=svg.appendChild(importSvg());
 const result=layers.ensureBase(svg,g,factory);
 assert.equal(result,g);assert.equal(overlay.parentNode,svg);
 assert.equal(svg.children.at(-1),overlay);
});
test("repair preserves stacking order of multiple overlays and raises them above later paths",()=>{
 const svg=root(),a=svg.appendChild(importSvg()),p=svg.appendChild(new Node("path"));
 const b=svg.appendChild(importSvg()),c=svg.appendChild(new Node("rect"));
 layers.repair(svg,null);
 assert.deepEqual(svg.children,[p,c,a,b]);
});
test("repeated repair is idempotent for saved docs",()=>{
 const svg=root(),g=svg.appendChild(base()),a=g.appendChild(importSvg());
 layers.repair(svg,g);
 const original=svg.children.slice();
 layers.repair(svg,g);
 assert.deepEqual(svg.children,original);
 assert.equal(a.parentNode,svg);
});
