/* Only the non-private browser-demo theme is read before the stylesheet. Android
   injects its saved theme into this document before it reaches the WebView. */
(()=>{if(window.Native)return;try{const theme=localStorage.getItem('reply-pilot-demo-theme');if(['forest','ocean','lavender','rose','sunset','slate','midnight','mocha','mint','plum'].includes(theme))document.documentElement.dataset.theme=theme;}catch{}})();
