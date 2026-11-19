/* Vectora SVG uniform affine transform helpers (pure JS, independently testable). */
(function(root,factory){
  "use strict";
  var api=factory();
  if(typeof module!=="undefined"&&module.exports)module.exports=api;
  if(root)root.VectorOverlay=api;
})(typeof window!=="undefined"?window:null,function(){
  "use strict";
  function finite(n){return typeof n==="number"&&Number.isFinite(n);}
  function limit(n,lo,hi){return Math.min(hi,Math.max(lo,n));}
  function fit(view,content,fill){
    if(!view||!content||!finite(view.width)||!finite(view.height)||
       !finite(content.width)||!finite(content.height)||
       view.width<=0||view.height<=0||content.width<=0||content.height<=0)
      throw new Error("Invalid SVG canvas dimensions");
    var fraction=limit(finite(fill)?fill:.65,.05,.95);
    var s=Math.min(view.width*fraction/content.width,
                   view.height*fraction/content.height);
    if(!finite(s)||s<=0)throw new Error("Invalid SVG import scale");
    return {
      x:view.x+(view.width-content.width*s)/2,
      y:view.y+(view.height-content.height*s)/2,
      scale:s,baseScale:s,width:content.width,height:content.height
    };
  }
  function move(state,dx,dy){
    return Object.assign({},state,{x:state.x+dx,y:state.y+dy});
  }
  function zoomAt(state,nextScale,anchor){
    if(!finite(nextScale)||nextScale<=0)throw new Error("Invalid uniform scale");
    if(!anchor||!finite(anchor.x)||!finite(anchor.y))throw new Error("Invalid scale anchor");
    var ratio=nextScale/state.scale;
    return Object.assign({},state,{
      x:anchor.x-(anchor.x-state.x)*ratio,
      y:anchor.y-(anchor.y-state.y)*ratio,
      scale:nextScale
    });
  }
  function pinch(state,startMid,nextMid,ratio){
    if(!finite(ratio)||ratio<=0)throw new Error("Invalid pinch factor");
    var scale=limit(state.scale*ratio,state.baseScale*.05,state.baseScale*10);
    var next=zoomAt(state,scale,startMid);
    return move(next,nextMid.x-startMid.x,nextMid.y-startMid.y);
  }
  function format(state){
    return "translate("+state.x.toFixed(5)+" "+state.y.toFixed(5)+") scale("+state.scale.toFixed(8)+")";
  }
  return {fit:fit,move:move,zoomAt:zoomAt,pinch:pinch,format:format};
});
