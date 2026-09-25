const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18772';

// Settings → Your views: the owner types what they think; Autopilot uses it for opinion questions.
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];await localAssets(page,url);
  page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(8000);
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.failSave=false;
   window.fixture={defaultSms:true,permissions:true,phoneAccess:true,contactsAllowed:true,notifications:true,exact:true,batteryUnrestricted:true,ownerViews:'',sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,inAppSuggestions:false,matchMyStyle:true,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{_id:60,thread_id:1,kind:'sms',date:60000,type:1,body:'Fictional latest message',read:1,address:'+12025550101',name:'Fictional Sam'}],delay:300};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='liveInbox')result={inbox:fixture.inbox,inboxComplete:false,contactsAllowed:true,contactPhotoRevision:1};
    else if(action==='saveOwnerViews'){if(failSave)error='Fictional save problem.';else{fixture.ownerViews=p.text.trim();result={ownerViews:fixture.ownerViews};}}
    else if(action==='media')result=[];
    setTimeout(()=>nativeResult(id,error?null:clone(result),error),0);
   }};
  });
  await page.goto(url);await page.locator('.row').first().waitFor();
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Settings',exact:true}).tap();
  const panel=page.locator('#owner-views-panel'),box=page.locator('#owner-views'),save=panel.getByRole('button',{name:'Save views',exact:true});

  phase='an empty panel explains what it is for, and nothing is saved until asked';
  await panel.waitFor();
  assert.match(await panel.innerText(),/When someone asks your opinion, Autopilot answers with these views/);
  assert(await save.isDisabled(),'Nothing to save yet');
  const bounds=await panel.boundingBox();assert(bounds&&bounds.x>=0&&bounds.x+bounds.width<=412,'Panel fits a phone-width screen');

  phase='typing enables Save and survives a background refresh';
  const views="Very pro AI. It'll replace a lot of simple, repetitive tasks, and people who don't use it will fall behind.";
  await box.fill(views);
  assert(!(await save.isDisabled()));assert.match(await panel.innerText(),new RegExp(`${views.length}/1,200`));
  await page.evaluate(()=>window.onNativeResume?.());await page.waitForTimeout(200);
  assert.equal(await box.inputValue(),views,'Unsaved text is kept across refreshes');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='saveOwnerViews').length),0);

  phase='a failed save keeps the text and says why';
  await page.evaluate(()=>{failSave=true;});await save.tap();
  await page.waitForFunction(()=>document.querySelector('#toast')?.textContent.includes('Fictional save problem'));
  assert.equal(await box.inputValue(),views);

  phase='saving sends exactly the text and confirms';
  await page.evaluate(()=>{failSave=false;});await save.tap();
  await page.waitForFunction(()=>document.querySelector('#toast')?.textContent.includes('Your views are saved'));
  const sent=await page.evaluate(()=>calls.filter(c=>c.action==='saveOwnerViews').at(-1).p);
  assert.deepEqual(sent,{text:views});
  assert(await save.isDisabled(),'Saved text has nothing left to save');
  await page.evaluate(()=>window.onNativeResume?.());await page.waitForTimeout(200);
  assert.equal(await box.inputValue(),views,'Saved views load back from the phone');

  phase='an older phone build without the field shows nothing';
  await page.evaluate(()=>{delete fixture.ownerViews;return window.onNativeResume?.();});
  await page.waitForFunction(()=>!document.getElementById('owner-views-panel'));
  assert.deepEqual(errors,[]);
  console.log('PASS: Settings → Your views: explains its use, keeps unsaved text across refreshes, failed save keeps text, save sends exact text and reloads, hidden without the native field. Fictional native bridge only.');
 }catch(error){throw new Error(phase+': '+error.message,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
