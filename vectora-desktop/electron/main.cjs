"use strict";
const fs=require("node:fs/promises");
const path=require("node:path");
const {app,BrowserWindow,dialog,ipcMain,Menu}=require("electron");

const LIMIT_BYTES=8*1024*1024;
const PNG_BYTES=28*1024*1024;
let win;
function safeSvg(input){
 if(typeof input!=="string"||Buffer.byteLength(input,"utf8")>LIMIT_BYTES||!input.includes("<svg"))
  throw new Error("SVG отсутствует или превышает 8 МиБ");
 if(/<!DOCTYPE|<!ENTITY/i.test(input))throw new Error("DTD и XML-сущности не поддерживаются");
 return input;
}
async function openSvg(){
 const result=await dialog.showOpenDialog(win,{title:"Открыть SVG",properties:["openFile"],
  filters:[{name:"SVG",extensions:["svg"]},{name:"Все файлы",extensions:["*"]}]});
 if(result.canceled||!result.filePaths[0])return null;
 const file=result.filePaths[0];
 const stat=await fs.stat(file);
 if(stat.size>LIMIT_BYTES)throw new Error("SVG превышает 8 МиБ");
 return {name:path.basename(file),source:safeSvg(await fs.readFile(file,"utf8"))};
}
function autosavePath(){return path.join(app.getPath("userData"),"vectora-autosave.svg");}
async function saveSvg(_event,source){
 safeSvg(source);
 const result=await dialog.showSaveDialog(win,{title:"Сохранить векторный SVG",defaultPath:"Vectora-drawing.svg",
  filters:[{name:"Векторный SVG",extensions:["svg"]}]});
 if(result.canceled||!result.filePath)return null;
 await fs.writeFile(result.filePath,source,"utf8");
 return result.filePath;
}
async function exportPng(_event,dataUrl){
 const prefix="data:image/png;base64,";
 if(typeof dataUrl!=="string"||!dataUrl.startsWith(prefix)||dataUrl.length>PNG_BYTES*1.4)throw new Error("Неправильный формат PNG");
 const raw=Buffer.from(dataUrl.slice(prefix.length),"base64");
 if(raw.length<8||raw.length>PNG_BYTES||!raw.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))
  throw new Error("Не удалось проверить PNG");
 const result=await dialog.showSaveDialog(win,{title:"Экспортировать PNG",defaultPath:"Vectora-drawing.png",
  filters:[{name:"PNG",extensions:["png"]}]});
 if(result.canceled||!result.filePath)return null;
 await fs.writeFile(result.filePath,raw);
 return result.filePath;
}
async function storeAutosave(_event,source){
 safeSvg(source);
 const dest=autosavePath(),temp=dest+".tmp";
 await fs.mkdir(path.dirname(dest),{recursive:true});
 await fs.writeFile(temp,source,"utf8");
 await fs.rename(temp,dest);
 return true;
}
async function restore(){
 try{return safeSvg(await fs.readFile(autosavePath(),"utf8"));}
 catch(error){if(error.code==="ENOENT")return null;throw error;}
}
function createWindow(){
 win=new BrowserWindow({
   width:1510,height:940,minWidth:1080,minHeight:690,show:false,
   backgroundColor:"#0d1320",title:"Vectora SVG",
   webPreferences:{
     preload:path.join(__dirname,"preload.cjs"),
     contextIsolation:true,nodeIntegration:false,sandbox:true,
     webSecurity:true,devTools:process.env.VECTORA_DEVTOOLS==="1"
   }
 });
 Menu.setApplicationMenu(null);
 win.once("ready-to-show",()=>win.show());
 win.webContents.setWindowOpenHandler(()=>({action:"deny"}));
 win.webContents.on("will-navigate",event=>event.preventDefault());
 win.loadFile(path.join(__dirname,"..","renderer","index.html"));
}
app.whenReady().then(()=>{
 ipcMain.handle("vectora:open",openSvg);
 ipcMain.handle("vectora:save",saveSvg);
 ipcMain.handle("vectora:png",exportPng);
 ipcMain.handle("vectora:autosave",storeAutosave);
 ipcMain.handle("vectora:restore",restore);
 createWindow();
 app.on("activate",()=>{if(BrowserWindow.getAllWindows().length===0)createWindow();});
});
app.on("window-all-closed",()=>{if(process.platform!=="darwin")app.quit();});
