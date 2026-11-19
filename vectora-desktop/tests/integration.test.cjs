const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const root=path.join(__dirname,"..");
const html=fs.readFileSync(path.join(root,"renderer","index.html"),"utf8");
const js=fs.readFileSync(path.join(root,"renderer","editor.js"),"utf8");
test("all direct DOM references used by the editor exist in desktop HTML",()=>{
 const ids=new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]));
 const requested=new Set([...js.matchAll(/\$\("([a-zA-Z0-9_-]+)"\)/g)].map(m=>m[1]));
 const missing=[...requested].filter(id=>!ids.has(id));
 assert.deepEqual(missing,[]);
});
test("bundled vector libraries are local",()=>{
 for(const file of ["vector-fill.js","vector-overlay.js","vector-layers.js","desktop-transform.js"])
  assert.ok(html.includes("../vendor/"+file));
 assert.equal(html.includes('src="https://'),false);
});
test("native dialogs, auto-save and transform interactions exist",()=>{
 const main=fs.readFileSync(path.join(root,"electron","main.cjs"),"utf8");
 const preload=fs.readFileSync(path.join(root,"electron","preload.cjs"),"utf8");
 assert.match(main,/showOpenDialog/);
 assert.match(main,/showSaveDialog/);
 assert.match(main,/vectora-autosave/);
 assert.match(main,/contextIsolation:true,nodeIntegration:false,sandbox:true/);
 assert.match(preload,/contextBridge\.exposeInMainWorld/);
 assert.match(js,/function importOverlaySvg\(source,label\)/);
 assert.match(js,/function handleResizeMove/);
 assert.match(js,/DesktopTransform\.resizeFromCorner/);
 assert.match(js,/function zoomAt\(/);
});
