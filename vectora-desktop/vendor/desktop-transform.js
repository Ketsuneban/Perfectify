/* Pure uniform-resize math: no SVG rasterization, independent of Electron. */
(function(root,factory){
 "use strict";
 var api=factory();
 if(typeof module!=="undefined"&&module.exports)module.exports=api;
 if(root)root.DesktopTransform=api;
})(typeof window!=="undefined"?window:null,function(){
 "use strict";
 const corners=new Set(["nw","ne","sw","se"]);
 function clamp(n,min,max){return Math.min(max,Math.max(min,n));}
 function resizeFromCorner(state,corner,pointer){
  if(!corners.has(corner))throw new Error("Unknown resize corner");
  if(!state||!Number.isFinite(state.width)||!Number.isFinite(state.height)||
    !Number.isFinite(state.scale)||!Number.isFinite(state.baseScale)||
    state.width<=0||state.height<=0||state.baseScale<=0)
   throw new Error("Invalid original SVG geometry");
  if(!pointer||!Number.isFinite(pointer.x)||!Number.isFinite(pointer.y))
   throw new Error("Invalid pointer position");
  var anchor={
   x:state.x+(corner.includes("w")?state.width*state.scale:0),
   y:state.y+(corner.includes("n")?state.height*state.scale:0)
  };
  var vx=(corner.includes("e")?1:-1)*state.width,
      vy=(corner.includes("s")?1:-1)*state.height;
  var projection=((pointer.x-anchor.x)*vx+(pointer.y-anchor.y)*vy)/
      (state.width*state.width+state.height*state.height);
  var scale=clamp(projection,state.baseScale*.05,state.baseScale*10);
  return Object.assign({},state,{
   x:anchor.x-(corner.includes("w")?state.width*scale:0),
   y:anchor.y-(corner.includes("n")?state.height*scale:0),
   scale:scale
  });
 }
 return {resizeFromCorner:resizeFromCorner};
});
