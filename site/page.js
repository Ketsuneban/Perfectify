(function(){
  let lang=localStorage.getItem("yeta-lang")||"ru";
  function apply(){
    document.documentElement.lang=lang;
    document.querySelectorAll("[data-"+lang+"]").forEach(function(el){el.textContent=el.getAttribute("data-"+lang)});
    document.getElementById("language").textContent=lang==="ru"?"EN":"RU";
  }
  document.getElementById("language").addEventListener("click",function(){lang=lang==="ru"?"en":"ru";localStorage.setItem("yeta-lang",lang);apply()});
  apply();
})();
