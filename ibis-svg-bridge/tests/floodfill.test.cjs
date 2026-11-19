const assert = require("node:assert/strict");
const test = require("node:test");
const {extract} = require("../app/src/main/assets/vector-fill.js");

function scene(width,height,background=[255,255,255,255]) {
 const data = new Uint8ClampedArray(width*height*4);
 for(let k=0;k<width*height;k++)data.set(background,k*4);
 return {
  image:{data}, width,height,
  paint(x,y,color){
   data.set(color,(y*width+x)*4);
  },
  fill(x,y,tol=0) {
   return extract({data},width,height,x,y,tol,{x:0,y:0,width,height});
  }
 };
}

test("a filled rectangle is traced as one vector path",()=>{
 const s=scene(8,8);
 for(let y=2;y<=5;y++)for(let x=2;x<=5;x++){
  if(x===2||x===5||y===2||y===5)s.paint(x,y,[10,10,10,255]);
 }
 const r=s.fill(3,3);
 assert.equal(r.pixels,4);
 assert.equal(r.contours,1);
 assert.equal(r.geometry,"vector");
 assert.match(r.path,/^M .+ Z$/);
 assert.ok(r.points>=4);
});

test("external fill includes an interior hole, using multiple contours",()=>{
 const s=scene(8,8);
 for(let y=2;y<=5;y++)for(let x=2;x<=5;x++){
  if(x===2||x===5||y===2||y===5)s.paint(x,y,[10,10,10,255]);
 }
 const r=s.fill(0,0);
 assert.equal(r.pixels,48);
 assert.equal(r.contours,2);
 assert.equal((r.path.match(/ Z/g)||[]).length,2);
});

test("sampling treats fully transparent pixels as one region",()=>{
 const s=scene(5,5,[0,0,0,0]);
 s.paint(2,2,[200,20,20,255]);
 const r=s.fill(0,0);
 assert.equal(r.pixels,24);
 assert.equal(r.contours,2);
});

test("four-neighbor region does not connect diagonal islands",()=>{
 const s=scene(3,3,[0,0,0,255]);
 s.paint(0,0,[255,0,0,255]);
 s.paint(1,1,[255,0,0,255]);
 const r=s.fill(0,0);
 assert.equal(r.pixels,1);
 assert.equal(r.contours,1);
});

test("color tolerance expands only into similar pixels",()=>{
 const s=scene(3,1,[100,100,100,255]);
 s.paint(1,0,[112,105,101,255]);
 s.paint(2,0,[160,100,100,255]);
 assert.equal(s.fill(0,0,20).pixels,2);
 assert.equal(s.fill(0,0,5).pixels,1);
});
