const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({channel:'chrome',headless:true});
 const page = await browser.newPage({viewport:{width:412,height:915}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:8769');
 await page.locator('#draft').waitFor();await page.getByRole('button',{name:'Back to conversations'}).click();
 await page.getByRole('button',{name:'Try AI',exact:true}).click();
 await page.getByRole('button',{name:'Work',exact:true}).click();
 assert((await page.locator('#test-message').inputValue()).includes('shift'));
 assert(await page.getByRole('button',{name:'Run on your Pixel',exact:true}).isDisabled());
 assert.equal(await page.locator('.test-reply').count(),0);
 assert((await page.locator('.notice').textContent()).includes('never fabricates a test reply'));
 await page.screenshot({path:'dist/nano-test-preview.png',fullPage:true});
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await page.close();
 // Exercise the native-bridge UI contract with explicitly fake responses; this does not test Nano.
 const phone=await browser.newPage({viewport:{width:412,height:915}});
 phone.on('pageerror',e=>errors.push(e.message));
 await phone.addInitScript(()=>{
  window.bridgeCalls=[];window.failNext=false;
  window.Native={call(id,action,raw){
   const p=JSON.parse(raw);window.bridgeCalls.push({action,p});
   let result={},error=null;
   if(action==='snapshot')result={defaultSms:false,permissions:false,notifications:false,exact:false,sims:[],sub:-1,inbox:[],jobs:[],nano:'ready',tone:'Natural',delay:60,autoDraft:true,matchMyStyle:true};
   if(action==='checkModel')result={status:'ready'};
   if(action==='testNano'){if(window.failNext){error='Gemini Nano is not ready.';window.failNext=false;}else result={body:'UI test stub <script>do not execute</script>',elapsedMs:1234,engine:'Test stub only'};}
   setTimeout(()=>window.nativeResult(id,result,error),50);
  }};
 });
 await phone.goto('http://127.0.0.1:8769');
 await phone.getByRole('button',{name:'Try AI',exact:true}).click();
 await phone.locator('#test-provider').selectOption('nano');
 const run=phone.getByRole('button',{name:'Generate test reply',exact:true});
 assert(await run.isDisabled());
 await phone.locator('#test-message').fill('Can you cover my shift?');
 await phone.locator('#test-relationship').fill('My coworker; do not promise availability.');
 await phone.locator('#test-examples').fill('yeah sounds good');
 await phone.locator('#test-tone').selectOption('Professional');
 await phone.getByRole('button',{name:'Settings',exact:true}).click();
 await phone.getByRole('button',{name:'Try AI',exact:true}).click();
 await phone.locator('#test-provider').selectOption('nano');
 assert.equal(await phone.locator('#test-message').inputValue(),'Can you cover my shift?');
 await run.click();await phone.locator('.test-reply').waitFor();
 const calls=await phone.evaluate(()=>window.bridgeCalls);
 const request=calls.find(c=>c.action==='testNano').p;
 assert.equal(request.relationship,'My coworker; do not promise availability.');
 assert.equal(request.tone,'Professional');assert(!('thread' in request));assert(!('address' in request));
 assert(!calls.some(c=>['approve','saveDraft','saveRelationship','compose','quick','generate'].includes(c.action)));
 assert.equal(await phone.locator('.test-results script').count(),0);
 await phone.evaluate(()=>window.failNext=true);await run.click();
 await phone.waitForFunction(()=>document.querySelector('#test-error').textContent.includes('not ready'));
 assert.equal(await phone.locator('.test-reply').count(),1,'Failure must not fabricate a reply');
 for(let i=0;i<3;i++){await run.click();await phone.waitForFunction(()=>!document.querySelector('[data-action="test-run"]').disabled);}
 assert.equal(await phone.locator('.test-reply').count(),3);
 await phone.getByRole('button',{name:'Clear test session',exact:true}).click();
 assert.equal(await phone.locator('#test-message').inputValue(),'');assert.equal(await phone.locator('.test-reply').count(),0);
 assert(await phone.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 assert.deepEqual(errors,[]);await browser.close();
 console.log('PASS: no fake browser Nano output; test form, bridge request, explicit failure, escaped replies, comparison limit, session clearing, no send/draft calls. Model inference remains untested.');
})().catch(e=>{console.error(e);process.exit(1);});
