(function(){
  "use strict";
  const API="https://api.github.com/repos/Ketsuneban/Yeta";
  const localChanges={
    "v0.3.0":{
      additions:[
        "Детёныши дельфинов, кальмаров, светящихся кальмаров, лосося, трески и тропических рыб",
        "Окрашивание шалкеров красителями по нажатию ПКМ",
        "Босс-бар, притяжение лучом и ближняя атака древнего стража",
        "Сокровища монумента: 80% золота, 10% изумрудов и 10% алмазов",
        "Отскок стрел, трезубцев и поплавков от границы мира",
        "Посадка железных големов в вагонетки",
        "Звуки обычного спавнера из новых версий Minecraft"
      ],
      changes:[
        "Усилены вторая фаза и разрушение блоков визером, добавлен взрыв после смерти",
        "Скелеты и их варианты стреляют с упреждением по движущейся цели",
        "Пронзатель действует на любую мокрую цель, включая водяной котёл",
        "Полностью согласованы зелья, красители, бутылки, стрелы и вёдра в котлах",
        "Снег накапливается, покрывает растения и восстанавливает их после удаления",
        "Любая листва под снегом белеет без полной перезагрузки чанков",
        "Маркер игрока за пределами карты указывает в сторону карты",
        "Мобы свободно проходят рельсы, ковры и слои снега",
        "Жест голема с маком длится около 40 секунд и имеет паузу 60–80 секунд",
        "Обновлены яйца призыва, знамёна, редстоун, поршни и стержень ифрита",
        "Обновлены пропорции детских моделей животных"
      ],
      fixes:[
        "Исправлен спавн визера из блоков",
        "Исправлены геометрия и коллизия кровати",
        "Кровать работает с порталом Края над любой половиной",
        "Исправлена левая рука и переключение поз стойки для брони",
        "Устранено дублирование содержимого при перемещении контейнеров поршнем",
        "Печи, коптильни и плавильные печи сохраняют прогресс обработки",
        "Головы игроков в чате поддерживают прозрачные пиксели",
        "Остриженная овца jeb_ переливается и всегда роняет белую шерсть",
        "Исправлено падение снега на мягкие растения"
      ]
    },
    "v0.2.0":{additions:["Рецепты кольчужной брони из цепей","Лошади в лодках и позы стоек для брони","Новые текстуры карт и локаторной панели"],changes:["Усилены визер и Эндер-дракон","Снег накапливается до восьми слоёв","Листва постепенно белеет во время снегопада","Расширена дальность луча маяка"],fixes:["Исправлены головы игроков и отступы чата","Исправлена окраска овец и жест голема с маком","Сохранение содержимого хранилищ при движении поршнем"]}
  };
  const escapeHtml=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  const formatBytes=bytes=>{if(!bytes)return "—";const units=["Б","КБ","МБ","ГБ"];let i=0,n=bytes;while(n>=1024&&i<units.length-1){n/=1024;i++}return `${n.toFixed(i?1:0)} ${units[i]}`};
  const formatDate=value=>new Intl.DateTimeFormat("ru-RU",{day:"numeric",month:"long",year:"numeric"}).format(new Date(value));
  function openPage(name){document.querySelectorAll(".page").forEach(page=>page.classList.toggle("active",page.dataset.page===name));document.querySelectorAll(".nav-item").forEach(item=>item.classList.toggle("active",item.dataset.pageTarget===name));document.querySelector(".workspace").scrollIntoView({behavior:"smooth",block:"start"});if(name==="source"&&!document.querySelector(".repo-entry"))loadRepository("")}
  document.addEventListener("click",event=>{const target=event.target.closest("[data-page-target]");if(target){event.preventDefault();openPage(target.dataset.pageTarget)}});
  function fallbackChanges(body){const bullets=String(body||"").split("\n").map(s=>s.trim()).filter(s=>s.startsWith("- ")).slice(0,12).map(s=>s.slice(2));return{additions:bullets.slice(0,4),changes:bullets.slice(4,8),fixes:bullets.slice(8,12)}}
  function changeGroup(title,items){return `<div class="change-group"><h4>${title}</h4><ul>${(items.length?items:["Подробности опубликованы в GitHub Release"]).map(x=>`<li>${escapeHtml(x)}</li>`).join("")}</ul></div>`}
  function renderUpdates(releases){const feed=document.getElementById("updates-feed");if(!releases.length){feed.innerHTML='<div class="loading-card">Опубликованных релизов пока нет.</div>';return}feed.innerHTML=releases.map((release,index)=>{const asset=(release.assets||[]).find(a=>a.name.endsWith(".jar"));const details=localChanges[release.tag_name]||fallbackChanges(release.body);const cleanName=(release.name||"Yeta").replace(/v?\d+\.\d+\.\d+.*/,"").trim()||"Yeta";return `<article class="release-card"><div class="release-card-head"><div class="release-title">${escapeHtml(cleanName)} <span>· ${escapeHtml(release.tag_name.replace(/^v/,""))}</span></div>${index===0?'<span class="release-badge">Актуально</span>':""}</div><div class="release-sections">${changeGroup("Добавления",details.additions)}${changeGroup("Изменения",details.changes)}${changeGroup("Исправления",details.fixes)}</div><div class="release-meta"><span>${formatDate(release.published_at||release.created_at)}</span><span>${formatBytes(asset&&asset.size)}</span><span>${asset?`${asset.download_count} загрузок`:"Исходный код"}</span><a href="${escapeHtml(release.html_url)}" target="_blank" rel="noopener">Полный релиз ↗</a></div></article>`}).join("")}
  function renderVersions(releases){const list=document.getElementById("versions-list");const entries=[];releases.forEach(release=>(release.assets||[]).filter(a=>a.name.endsWith(".jar")).forEach(asset=>entries.push({release,asset})));if(!entries.length){list.innerHTML='<div class="loading-card">JAR-файлы пока не опубликованы.</div>';return}list.innerHTML=entries.map(({release,asset})=>`<article class="version-row"><div class="version-main"><div class="version-icon">Y</div><div><div class="version-name">Yeta ${escapeHtml(release.tag_name)}</div><div class="version-tags"><span>Minecraft 1.21.1</span><span>·</span><span>NeoForge</span><span>·</span><span>${release.prerelease?"Предварительная":"Стабильная"}</span></div></div></div><div class="version-side"><div class="version-stats"><div>${formatBytes(asset.size)}</div><div>${asset.download_count} загрузок · ${formatDate(release.published_at)}</div></div><a class="download-mini" href="${escapeHtml(asset.browser_download_url)}" title="Скачать ${escapeHtml(asset.name)}"><svg><use href="#i-download"/></svg></a></div></article>`).join("");const newest=entries[0];if(newest){document.getElementById("hero-download").href=newest.asset.browser_download_url;document.getElementById("hero-download").querySelector("span").textContent=`Скачать Yeta ${newest.release.tag_name.replace(/^v/,"")}`}}
  async function loadReleases(){try{const response=await fetch(`${API}/releases?per_page=20`,{headers:{Accept:"application/vnd.github+json"}});if(!response.ok)throw new Error(response.status);const releases=await response.json();renderUpdates(releases);renderVersions(releases)}catch(error){document.getElementById("updates-feed").innerHTML='<div class="loading-card">Не удалось получить обновления. <a href="https://github.com/Ketsuneban/Yeta/releases">Открыть GitHub Releases</a></div>';document.getElementById("versions-list").innerHTML='<div class="loading-card">GitHub временно недоступен. Используйте страницу релизов.</div>'}}
  function renderBreadcrumbs(path){const root=document.getElementById("repo-breadcrumbs");const parts=path?path.split("/"):[];let current="";root.innerHTML='<button type="button" data-repo-path="">Yeta</button>'+parts.map(part=>{current=current?`${current}/${part}`:part;return `<span>/</span><button type="button" data-repo-path="${escapeHtml(current)}">${escapeHtml(part)}</button>`}).join("")}
  async function loadRepository(path){renderBreadcrumbs(path);const files=document.getElementById("repo-files");files.innerHTML='<div class="repo-loading">Загружаем содержимое…</div>';try{const response=await fetch(`${API}/contents/${path}?ref=main`,{headers:{Accept:"application/vnd.github+json"}});if(!response.ok)throw new Error(response.status);const items=await response.json();items.sort((a,b)=>a.type===b.type?a.name.localeCompare(b.name):a.type==="dir"?-1:1);files.innerHTML=items.map(item=>`<div class="repo-entry"><div class="repo-entry-name"><svg><use href="#${item.type==="dir"?"i-folder":"i-file"}"/></svg>${item.type==="dir"?`<button type="button" data-repo-path="${escapeHtml(item.path)}">${escapeHtml(item.name)}</button>`:`<button type="button" data-file-url="${escapeHtml(item.html_url)}">${escapeHtml(item.name)}</button>`}</div><span>${item.type==="dir"?"Папка":formatBytes(item.size)}</span>${item.type==="file"?`<a href="${escapeHtml(item.download_url||item.html_url)}" target="_blank" rel="noopener">Файл ↗</a>`:"<span>—</span>"}</div>`).join("")}catch(error){files.innerHTML='<div class="repo-loading">Не удалось загрузить дерево. <a href="https://github.com/Ketsuneban/Yeta">Открыть GitHub</a></div>'}}
  document.getElementById("repo-breadcrumbs").addEventListener("click",event=>{const button=event.target.closest("[data-repo-path]");if(button)loadRepository(button.dataset.repoPath)});
  document.getElementById("repo-files").addEventListener("click",event=>{const folder=event.target.closest("[data-repo-path]");if(folder)loadRepository(folder.dataset.repoPath);const file=event.target.closest("[data-file-url]");if(file)window.open(file.dataset.fileUrl,"_blank","noopener")});
  loadReleases();
})();
