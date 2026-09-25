const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>{
   if(new URL(route.request().url()).origin!=='http://127.0.0.1:8769'){external.push(route.request().url());return route.abort();}
   return route.continue();
  });
  // A simulated native bridge supplies hostile strings. No phone, AI or carrier call occurs.
  await page.addInitScript(()=>{
   window.attack='</textarea><img src="https://example.invalid/leak" onerror="window.injected=true"><script>window.injected=true</script><iframe srcdoc="bad"></iframe>';
   window.calls=[];
   window.Native={call(id,action,raw){
    calls.push({action,p:JSON.parse(raw)});let result={};
    if(action==='snapshot')result={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Test SIM'}],sub:1,inbox:[{thread_id:1,name:attack,address:'+12025550147',body:attack,type:1,date:1}],jobs:[{_id:1,thread:1,address:'+12025550147',body:attack,status:'paused',note:attack,due:1}],nano:'unchecked',tone:'Natural',delay:60,autoDraft:false,matchMyStyle:true,cloud:{configured:true}};
    if(action==='conversation')result={history:[{_id:1,type:1,body:attack,date:1}],base:1,draft:{body:attack,engine:attack},relationship:{body:attack,samples:attack,cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300}};
    if(action==='media')result=[{name:attack,date:1,m_type:132,parts:[{ct:'text/plain',text:attack}]}];
    if(action==='testOpenAI')result={body:attack,engine:'Mock only',elapsedMs:1};
    setTimeout(()=>nativeResult(id,result,null),0);
   }};
  });
  const safe=async()=>{
   assert.equal(await page.locator('#app script, #app iframe, #app img').count(),0);
   assert.equal(await page.evaluate(()=>window.injected),undefined);
   assert.deepEqual(external,[]);
  };
  await page.goto('http://127.0.0.1:8769');await page.locator('.row').waitFor();await safe();
  await page.locator('.row').click();await page.locator('#draft').waitFor();
  const attack=await page.evaluate(()=>window.attack);
  assert.equal(await page.locator('.bubble').textContent(),attack);
  assert.equal(await page.locator('#draft').inputValue(),attack);
  await page.locator('#reply-setup').click();
  assert.equal(await page.locator('#relationship-context').inputValue(),attack);
  await page.locator('#profile-samples > summary').click();
  assert.equal(await page.locator('#relationship-samples').inputValue(),attack);await safe();
  await page.getByRole('button',{name:'Close reply setup',exact:true}).click();
  await page.getByRole('button',{name:'Back to conversations'}).click();
  await page.getByRole('button',{name:'Queue',exact:true}).click();await safe();
  assert.equal(await page.locator('.queue-card > p').textContent(),attack);
  await page.getByRole('button',{name:'Media',exact:true}).click();await safe();
  assert.equal(await page.locator('.panel > p').textContent(),attack);
  await page.getByRole('button',{name:'Try AI',exact:true}).click();
  await page.locator('#test-message').fill('Synthetic test');
  await page.getByRole('button',{name:'Generate test reply',exact:true}).click();
  await page.locator('.test-reply').waitFor();await safe();
  assert((await page.locator('.test-reply').textContent()).includes(attack));
  assert(!(await page.evaluate(()=>calls.some(c=>['approve','saveDraft','saveProfile','generate'].includes(c.action)))));
  assert.deepEqual(errors,[]);
  console.log('PASS: hostile contact, SMS, MMS, draft, queue, profile and AI text stays inert; no external requests or send/save calls. Simulated bridge only.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
