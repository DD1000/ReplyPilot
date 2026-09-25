const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

// An empty SIM menu must explain itself. Android hides SIMs from apps without
// Phone access, which previously left "Choose SIM" empty with no way forward.
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const copy=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];
   window.fixture={defaultSms:true,permissions:true,phoneAccess:false,notifications:true,exact:true,contactsAllowed:false,sims:[],sub:-1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true,url:'https://example.invalid'},jobs:[],inbox:[{thread_id:1,name:'Maya Chen',address:'+12025550147',body:'Ok',date:42000,type:1,read:1}]};
   window.grantPhone=true;
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw||'{}');calls.push({action,p});
    setTimeout(()=>{
     let result={};
     if(action==='snapshot')result=fixture;
     else if(action==='smsRoleStatus')result={defaultSms:true,permissions:true,phoneAccess:fixture.phoneAccess,pending:false,phase:'idle',outcome:'',message:''};
     else if(action==='permissions'&&grantPhone){fixture.phoneAccess=true;fixture.sims=[{id:7,name:'Fictional SIM'}];fixture.sub=7;}
     else if(action==='settings'&&Number.isInteger(p.sub))fixture.sub=p.sub;
     else if(action==='media')result=[];
     nativeResult(id,copy(result),null);
    },0);
   }};
  });
  await page.goto('http://127.0.0.1:8769');
  const settings=()=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Settings',exact:true}).click();
  const help=page.locator('[data-sim-help]');

  // 1. No Phone access: explain the cause and offer both fixes.
  await settings();await help.waitFor();
  assert.match(await help.innerText(),/needs Phone access/);
  await page.getByRole('button',{name:'Allow Phone access',exact:true}).waitFor();
  await page.getByRole('button',{name:/Open app permissions/}).waitFor();
  const box=await help.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=412,'Help fits a phone-width screen');
  await page.screenshot({path:process.env.SIM_SCREENSHOT||'dist/sim-help-412x915.png',fullPage:false}).catch(()=>{});

  // 2. Allowing Phone access asks Android, refreshes, and the only SIM is selected.
  await page.getByRole('button',{name:'Allow Phone access',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('[data-sim-help]'));
  assert(await page.evaluate(()=>calls.some(call=>call.action==='permissions')),'The native permission request ran');
  assert.equal(await page.locator('#sim').inputValue(),'7');
  assert.equal(await page.locator('#sim option').count(),2);

  // 2b. Picking a SIM saves it at once; sends read the saved SIM, not the unsaved form.
  await page.evaluate(()=>{fixture.sims=[{id:7,name:'Fictional SIM'},{id:8,name:'Second fictional SIM'}];fixture.sub=-1;});
  await page.evaluate(()=>window.onNativeResume?.());
  await page.waitForFunction(()=>document.querySelectorAll('#sim option').length===3);
  await page.locator('#sim').selectOption('8');
  await page.waitForFunction(()=>calls.some(call=>call.action==='settings'&&call.p.sub===8));
  await page.waitForFunction(()=>document.querySelector('#toast')?.textContent.includes('Sending SIM saved'));
  assert.equal(await page.evaluate(()=>fixture.sub),8);
  assert.equal(await page.locator('#sim').inputValue(),'8');

  // 3. Phone access but no active SIM: point to Android's SIM settings instead.
  await page.evaluate(()=>{fixture.sims=[];fixture.sub=-1;});
  await page.evaluate(()=>window.onNativeResume?.());
  await help.waitFor();
  assert.match(await help.innerText(),/No active SIM found/);
  assert.equal(await page.getByRole('button',{name:'Allow Phone access',exact:true}).count(),0);

  // 4. Open app permissions uses the existing App info route.
  await page.evaluate(()=>{fixture.phoneAccess=false;});
  await page.evaluate(()=>window.onNativeResume?.());
  await page.getByRole('button',{name:/Open app permissions/}).click();
  await page.waitForFunction(()=>calls.some(call=>call.action==='appSettings'));

  assert.deepEqual(errors,[]);
  console.log('PASS: empty SIM menu explains Phone access or inactive SIM, Allow Phone access recovers and selects the only SIM, SIM choice saves immediately, app permissions route.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
