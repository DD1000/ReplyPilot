const fs=require('node:fs/promises');
const path=require('node:path');
// Isolated browser fixtures read only packaged, allowlisted UI assets. Their
// fictional native bridge remains responsible for every simulated phone action.
module.exports=async function localAssets(page,url){
 const origin=new URL(url).origin;
 await page.route(origin+'/**',async route=>{
  const name=new URL(route.request().url()).pathname.slice(1)||'index.html';
  if(!['index.html','app.js','demo.js','app.css','theme.js'].includes(name))return route.fulfill({status:404,body:''});
  const contentType=name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':'text/html';
  await route.fulfill({contentType,body:await fs.readFile(path.join(__dirname,'../app/src/main/assets',name))});
 });
};
