const assert=require("node:assert/strict");
const {test}=require("node:test");
const {resizeFromCorner}=require("../vendor/desktop-transform.js");
const base={x:100,y:200,scale:2,baseScale:2,width:100,height:50};
test("SE handle doubles size and leaves NW fixed",()=>{
 const n=resizeFromCorner(base,"se",{x:500,y:400});
 assert.equal(n.scale,4);assert.equal(n.x,100);assert.equal(n.y,200);
 assert.equal(n.width*n.scale,400);assert.equal(n.height*n.scale,200);
});
test("NW handle doubles size while opposite SE stays fixed",()=>{
 const n=resizeFromCorner(base,"nw",{x:-100,y:100});
 assert.equal(n.scale,4);assert.equal(n.x,-100);assert.equal(n.y,100);
 assert.equal(n.x+n.width*n.scale,300);
 assert.equal(n.y+n.height*n.scale,300);
});
test("NE handle maintains width/height ratio and SW anchor",()=>{
 const n=resizeFromCorner(base,"ne",{x:500,y:100});
 assert.equal(n.scale,4);assert.equal(n.x,100);assert.equal(n.y,100);
 assert.equal(n.y+n.height*n.scale,300);
});
test("SW handle maintains width/height ratio and NE anchor",()=>{
 const n=resizeFromCorner(base,"sw",{x:-100,y:400});
 assert.equal(n.scale,4);assert.equal(n.x,-100);assert.equal(n.y,200);
 assert.equal(n.x+n.width*n.scale,300);
});
test("limits scaling without mirroring the SVG",()=>{
 const large=resizeFromCorner(base,"se",{x:999999,y:999999});
 assert.equal(large.scale,20);
 const small=resizeFromCorner(base,"se",{x:-9999,y:-9999});
 assert.equal(small.scale,.1);
});
