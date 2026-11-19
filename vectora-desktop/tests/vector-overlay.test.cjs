const assert=require("node:assert/strict");
const {test}=require("node:test");
const api=require("../vendor/vector-overlay.js");
test("fit scales SVG uniformly and centers it",()=>{
 const result=api.fit({x:0,y:0,width:1000,height:600},{width:300,height:300},.6);
 assert.equal(result.scale,1.2);
 assert.equal(result.x,320);
 assert.equal(result.y,120);
 assert.equal(result.baseScale,1.2);
});
test("extremely wide viewBoxes are fitted without distortion",()=>{
 const state=api.fit({x:-50,y:25,width:200,height:100},{width:2000,height:10},.8);
 assert.ok(Math.abs(state.scale-.08)<1e-10);
 assert.equal(state.x,-30);
 assert.ok(Math.abs(state.y-74.6)<1e-9);
 assert.ok(state.width*state.scale<=200);
});
test("zoom keeps the selected anchor fixed and uses one scale",()=>{
 const state={x:10,y:20,scale:2,baseScale:2,width:20,height:10};
 const next=api.zoomAt(state,3,{x:30,y:50});
 assert.equal(next.x,0);
 assert.equal(next.y,5);
 assert.equal(next.scale,3);
 assert.equal(next.x+next.scale*10,30);
 assert.equal(next.y+next.scale*15,50);
});
test("pinch pans midpoint and uniformly expands the entire SVG",()=>{
 const state={x:10,y:20,scale:2,baseScale:2,width:20,height:10};
 const result=api.pinch(state,{x:30,y:40},{x:40,y:50},2);
 assert.equal(result.x,0);
 assert.equal(result.y,10);
 assert.equal(result.scale,4);
 assert.ok(api.format(result).includes("scale(4.00000000)"));
});
test("pinch scale clamped to 5%-1000% base",()=>{
 const state={x:0,y:0,scale:1,baseScale:1,width:1,height:1};
 assert.equal(api.pinch(state,{x:0,y:0},{x:0,y:0},100).scale,10);
 assert.equal(api.pinch(state,{x:0,y:0},{x:0,y:0},.0001).scale,.05);
});
