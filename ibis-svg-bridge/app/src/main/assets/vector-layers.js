/* Vectora SVG — deterministic z-ordering for base drawing, eraser mask, and imported SVGs.
 * The eraser's mask may only apply to the base group, never an imported overlay.
 * DOM operations do not rasterize or rewrite imported SVG geometry.
 */
(function(root,factory) {
 "use strict";
 var api=factory();
 if(typeof module!=="undefined"&&module.exports)module.exports=api;
 if(root)root.VectorLayers=api;
})(typeof window!=="undefined"?window:null,function(){
 "use strict";
 function isOverlay(node){
  return !!(node&&node.getAttribute&&node.getAttribute("data-vectora-overlay")==="true");
 }
 function isDefs(node){return !!(node&&node.localName==="defs");}
 function repair(root,base){
  if(!root)throw new Error("SVG root is required");
  // Migrate existing v0.4.0 documents: overlays accidentally placed inside
  // the base group inherited its eraser mask and were incorrectly erased.
  if(base&&base.parentNode===root){
   Array.from(base.children).forEach(function(node){
    if(isOverlay(node))root.appendChild(node);
   });
  }
  // SVG painter's algorithm: direct children rendered last appear on top.
  // Preserve relative overlay order by moving them to the end in sequence.
  var overlayNodes=Array.from(root.children).filter(isOverlay);
  overlayNodes.forEach(function(node){root.appendChild(node);});
  return overlayNodes.length;
 }
 function ensureBase(root,base,createGroup){
  if(!root||typeof createGroup!=="function")throw new Error("SVG group factory required");
  if(base&&base.parentNode===root){
   repair(root,base);
   return base;
  }
  var original=Array.from(root.children);
  var firstOverlay=original.find(isOverlay)||null;
  var newGroup=createGroup();
  newGroup.setAttribute("data-vectora-content","true");
  root.insertBefore(newGroup,firstOverlay);
  original.forEach(function(node){
   if(node!==newGroup&&!isDefs(node)&&!isOverlay(node))newGroup.appendChild(node);
  });
  repair(root,newGroup);
  return newGroup;
 }
 return {isOverlay:isOverlay,repair:repair,ensureBase:ensureBase};
});
