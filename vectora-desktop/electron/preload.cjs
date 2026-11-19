"use strict";
const {contextBridge,ipcRenderer}=require("electron");
contextBridge.exposeInMainWorld("vectoraDesktop",Object.freeze({
  openSvg:()=>ipcRenderer.invoke("vectora:open"),
  importSvg:()=>ipcRenderer.invoke("vectora:open"),
  saveSvg:(source)=>ipcRenderer.invoke("vectora:save",source),
  exportPng:(dataUrl)=>ipcRenderer.invoke("vectora:png",dataUrl),
  autosave:(source)=>ipcRenderer.invoke("vectora:autosave",source),
  restore:()=>ipcRenderer.invoke("vectora:restore"),
  platform:process.platform,
  version:"0.5.0"
}));
