const { chromium }=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const page=await browser.newPage({viewport:{width:412,height:915}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const openSetup=async()=>{await page.locator('#reply-setup').waitFor();if(await page.locator('#reply-setup').getAttribute('aria-expanded')!=='true')await page.locator('#reply-setup').click();await page.locator('#relationship-panel').waitFor();};
 const closeSetup=async()=>{if(await page.locator('#relationship-panel').count())await page.getByRole('button',{name:'Close reply setup',exact:true}).click();};
 const openSamples=async()=>{if(!await page.locator('#profile-samples').evaluate(el=>el.open))await page.locator('#profile-samples > summary').click();};
 await page.goto('http://127.0.0.1:8769');await page.locator('#draft').waitFor();
 await openSetup();
 assert(await page.locator('[name=person-mode][value=off]').isChecked());
 await openSamples();
 const samples='Them: how’s it going?\nMe: pretty good <script>bad()</script>';
 await page.locator('#relationship-samples').fill(samples);await page.locator('#relationship-context').fill('Old friend; no flirting.');
 await page.locator('[name=person-mode][value=automatic]').check();
 await page.locator('[name=relationship-kind][value=Friend]').check();assert.equal(await page.locator('#relationship-samples').inputValue(),samples);
 const before=await page.locator('#draft').inputValue();await page.getByRole('button',{name:'Save profile',exact:true}).click();
 await page.waitForFunction(async()=>(await demoApi('conversation',{thread:1})).relationship.cloudEnabled);
 assert.equal(await page.evaluate(async()=>(await demoApi('conversation',{thread:1})).relationship.autoSend),false,'Preparing drafts must not enable unreviewed sending');
 assert.equal(await page.locator('#draft').inputValue(),before);assert.equal(await page.evaluate(async()=>(await demoApi('snapshot')).jobs.length),0);
 assert.equal(await page.locator('.relationship script').count(),0);
 await page.locator('#relationship-panel').screenshot({path:'dist/openai-profile-preview.png'});
 await closeSetup();await page.getByRole('button',{name:'Back to conversations'}).click();await page.locator('.row').filter({hasText:'Jordan Ellis'}).click();
 await page.waitForFunction(()=>!document.querySelector('#draft').disabled);await openSetup();
 assert.equal(await page.locator('#relationship-samples').inputValue(),'');assert(await page.locator('[name=person-mode][value=off]').isChecked());
 await closeSetup();await page.getByRole('button',{name:'Back to conversations'}).click();await page.locator('.row').filter({hasText:'Maya Chen'}).click();await openSetup();
 assert.equal(await page.locator('#relationship-samples').inputValue(),samples);
 await page.locator('[name=person-mode][value=off]').check();await page.getByRole('button',{name:'Save profile',exact:true}).click();
 await page.waitForFunction(async()=>!(await demoApi('conversation',{thread:1})).relationship.cloudEnabled);await page.reload();await openSetup();
 assert(await page.locator('[name=person-mode][value=off]').isChecked());assert.equal(await page.locator('#relationship-samples').inputValue(),samples);
 await openSamples();await page.locator('#relationship-samples').fill('');await page.getByRole('button',{name:'Save profile',exact:true}).click();await page.waitForFunction(async()=>(await demoApi('conversation',{thread:1})).relationship.samples==='');await closeSetup();
 await page.getByRole('button',{name:'Back to conversations'}).click();await page.getByRole('button',{name:'Try AI',exact:true}).click();assert.equal(await page.locator('#test-provider').inputValue(),'openai');await page.locator('#test-message').fill('Coffee?');assert(await page.locator('[data-action="test-run"]').isDisabled());assert.equal(await page.locator('.test-reply').count(),0);
 await page.close();
 // Explicit native UI mock: no device, API, or carrier call occurs here.
 const phone=await browser.newPage({viewport:{width:412,height:915}});phone.on('pageerror',e=>errors.push(e.message));
 await phone.addInitScript(()=>{
  window.calls=[];window.connected=false;window.failNext=false;
  window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
   if(action==='snapshot')result={defaultSms:false,permissions:false,notifications:false,exact:false,sims:[],sub:-1,inbox:[],jobs:[],nano:'unchecked',tone:'Natural',delay:60,autoDraft:true,matchMyStyle:true,cloud:{configured:connected,url:connected?'https://example.invalid':''}};
   if(action==='connectCloud'){connected=true;result={configured:true,url:'https://example.invalid'};}
   if(action==='disconnectCloud')connected=false;
   if(action==='checkCloud')result={ready:true};
   if(action==='testOpenAI'){if(failNext){error='Check API billing';failNext=false;}else result={body:'UI mock only <img src=x>',engine:'OpenAI mock',elapsedMs:750};}
   setTimeout(()=>nativeResult(id,result,error),20);
  }};
 });
 await phone.goto('http://127.0.0.1:8769');await phone.getByRole('button',{name:'Try AI',exact:true}).click();await phone.locator('#test-message').fill('Can you cover my shift?');
 assert(await phone.locator('[data-action="test-run"]').isDisabled());
 await phone.getByRole('button',{name:'Connect OpenAI',exact:true}).click();await phone.locator('#pairing-code').fill('{"url":"https://example.invalid","token":"ui-test-token-only-123456789012345678"}');await phone.getByRole('button',{name:'Save connection',exact:true}).click();
 await phone.waitForFunction(()=>document.querySelector('#cloud-status').textContent.includes('saved'));assert.equal(await phone.locator('#pairing-code').inputValue(),'');
 await phone.getByRole('button',{name:'Check connection',exact:true}).click();await phone.waitForFunction(()=>document.querySelector('#cloud-status').textContent.includes('will verify'));
 await phone.getByRole('button',{name:'Try AI',exact:true}).click();await phone.locator('#test-relationship').fill('My coworker. Do not promise availability.');await phone.locator('#test-examples').fill('Them: thanks\nMe: no problem');
 await phone.locator('#test-tone').selectOption('Use AI intuition');
 await phone.getByRole('button',{name:'Generate test reply',exact:true}).click();await phone.locator('.test-reply').waitFor();assert.equal(await phone.locator('.test-results img').count(),0);
 const calls=await phone.evaluate(()=>window.calls),req=calls.find(c=>c.action==='testOpenAI').p;assert.equal(req.message,'Can you cover my shift?');assert.equal(req.tone,'Use AI intuition');assert.equal(req.examples,'Them: thanks\nMe: no problem');assert(!('thread' in req));assert(!('address' in req));assert(!calls.some(c=>['approve','saveDraft','saveProfile','generate','testNano'].includes(c.action)));
 await phone.evaluate(()=>window.failNext=true);await phone.getByRole('button',{name:'Generate test reply',exact:true}).click();await phone.waitForFunction(()=>document.querySelector('#test-error').textContent.includes('billing'));assert.equal(await phone.locator('.test-reply').count(),1);
 await phone.getByRole('button',{name:'Settings',exact:true}).click();await phone.getByRole('button',{name:'Disconnect phone',exact:true}).click();await phone.waitForFunction(()=>document.querySelector('#cloud-status')?.textContent.includes('Phone disconnected')); await phone.getByRole('button',{name:'Try AI',exact:true}).click();assert(await phone.locator('[data-action="test-run"]').isDisabled());
 assert(await phone.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);await browser.close();console.log('PASS: cloud opt-in, separate saved samples, disabled automation, preserved drafts, no fake inference, pairing UI, redacted connection state, OpenAI request flow, failure and disconnect. Mocked UI only.');
})().catch(e=>{console.error(e);process.exit(1);});
