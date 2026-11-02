// RIVO service worker — só cuida do RIVO (rivo.html e rivo-icons); não mexe em nenhuma outra página do site
var C='rivo-cache-v1';
var ARQ=['./rivo.html','./rivo.webmanifest','./rivo-icons/icon-192.png','./rivo-icons/icon-512.png'];
function meu(u){return u.origin===location.origin&&(/\/rivo\.html$/.test(u.pathname)||/\/rivo\.webmanifest$/.test(u.pathname)||/\/rivo-icons\//.test(u.pathname))}
self.addEventListener('install',function(e){e.waitUntil(caches.open(C).then(function(c){return c.addAll(ARQ)}).then(function(){return self.skipWaiting()}))});
self.addEventListener('activate',function(e){e.waitUntil(caches.keys().then(function(ks){return Promise.all(ks.filter(function(k){return k.indexOf('rivo-cache-')===0&&k!==C}).map(function(k){return caches.delete(k)}))}).then(function(){return self.clients.claim()}))});
self.addEventListener('fetch',function(e){
 var r=e.request;if(r.method!=='GET')return;
 if(!meu(new URL(r.url)))return;
 e.respondWith(fetch(r).then(function(x){var cp=x.clone();caches.open(C).then(function(c){c.put(r,cp)});return x}).catch(function(){return caches.match(r)}))});
