const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

// Exercise the actual browser preview's profile/timer rules without a server or AI call.
async function checkDemoContract(){
 const saved=new Map(),window={},context={window,localStorage:{getItem:key=>saved.get(key)||null,setItem:(key,value)=>saved.set(key,value)},setTimeout,clearTimeout,confirm:()=>false};
 vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/demo.js'),'utf8'),context);
 const samples=Array.from({length:24},(_,i)=>`${i%2?'Me':'Them'}: Distinct fictional training example ${i}`).join('\n');
 const profile={thread:1,body:'We are partners. Keep replies warm and honest.',importantDetails:'I work early on Tuesdays.',samples,cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:1,autoDelayMode:'fixed',autoDelayMin:300,autoDelayMax:1800,engagement:'girlfriend',shareLocation:false};
 let result=await window.demoApi('saveProfile',profile);assert.equal(result.planHandling,'ask_me');assert.equal(result.importantDetails,profile.importantDetails);assert.equal(result.autoDelay,1);assert.equal(result.engagement,'girlfriend');
 result=await window.demoApi('saveProfile',{...profile,autoDelay:0,planHandling:'delay_answer'});assert.equal(result.autoDelay,0);assert.equal(result.engagement,'girlfriend');assert.equal(result.planHandling,'delay_answer');
 result=await window.demoApi('saveProfile',{...profile,autoDelayMode:'range'});assert.equal(result.autoDelayMode,'range');assert.equal(result.engagement,'girlfriend');
 result=await window.demoApi('replyProfile',{thread:1});assert.equal(result.relationship.importantDetails,profile.importantDetails);assert.equal(result.relationship.engagement,'girlfriend');
 await assert.rejects(window.demoApi('saveProfile',{...profile,importantDetails:'x'.repeat(2001)}),/2,000/);
 await assert.rejects(window.demoApi('saveProfile',{...profile,planHandling:'make_plans'}),/plans/);
 await assert.rejects(window.demoApi('saveProfile',{...profile,autoDelay:2}),/1 second/);
 await assert.rejects(window.demoApi('saveProfile',{...profile,autoDelayMode:'range',autoDelayMin:1}),/whole minutes/);
 await assert.rejects(window.demoApi('generate',{thread:1,base:10}),/OpenAI/);
 const snapshot=await window.demoApi('snapshot');assert.equal(Object.hasOwn(snapshot,'nano'),false);
}

(async()=>{
 await checkDemoContract();
 const browser=await chromium.launch({channel:'chrome',headless:true});let page,phase='setup';
 try{
  page=await browser.newPage({viewport:{width:412,height:915}});const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);const clone=value=>JSON.parse(JSON.stringify(value));window.calls=[];
   window.profile={body:'Original relationship notes',samples:'',cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:300,autoDelayMode:'fixed',autoDelayMin:300,autoDelayMax:1800,engagement:'natural',shareLocation:false,relationshipKind:'Partner',tone:'Warm',humorLevel:3,insideJokes:'An old saved joke',revision:1};
   const access={readSms:true,defaultSms:true,contacts:true};
   const ready={available:true,eligible:true,total:60,owner:30,incoming:30,scanComplete:true};
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,inboxComplete:true,inbox:[{thread_id:1,_id:60,address:'+12025550147',name:'Fictional partner',body:'That was funny',kind:'sms',type:1,read:1,date:60000}],jobs:[],cloud:{configured:true,url:'https://example.invalid'},theme:'midnight',autoDraft:true,inAppSuggestions:false,matchMyStyle:true,delay:300,delayMode:'fixed',delayMin:300,delayMax:1800};
   const history=Array.from({length:60},(_,i)=>({_id:i+1,thread_id:1,kind:'sms',type:i%2?1:2,read:1,date:(i+1)*1000,body:`Fictional message ${i+1}`}));
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='launchInbox')result={inbox:[],access};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access};
    else if(action==='cacheInbox')result={inbox:[],hasMore:false,access,cacheOnly:true,readOnly:true};
    else if(action==='conversation')result={thread:1,address:fixture.inbox[0].address,name:fixture.inbox[0].name,history,base:60,smsLatest:history.at(-1),hasOlder:false,hasMore:false,before:null,relationship:profile,profileRevision:profile.revision,replyEligibility:ready,attachments:{items:[],sending:false,enabled:true},draft:{body:'My existing unsent draft',engine:'Edited by you',alternatives:'[]'}};
    else if(action==='replyProfile')result={thread:1,address:fixture.inbox[0].address,name:fixture.inbox[0].name,relationship:profile,profileRevision:profile.revision,readOnly:false,replyEligibility:ready,access};
    else if(action==='saveProfile'){if(p.expectedRevision!==profile.revision)error='Reply settings changed.';else{profile={...profile,...p,revision:profile.revision+1};result={...profile,replyEligibility:ready};}}
    else if(action==='settings')Object.assign(fixture,p);
    else if(action==='saveDraft')result={};
    else if(['generate','approve','sendNow','testOpenAI'].includes(action))error='This test never sends messages or calls AI.';
    const value=clone(result);setTimeout(()=>nativeResult(id,value,error),0);
   }};
  });
  await page.goto(url);await page.locator('.row[data-thread="1"]').click();await page.waitForFunction(()=>chatReady());await page.locator('#reply-setup').click();
  phase='simplified relationship inputs';
  assert.equal((await page.locator('label[for=relationship-context]').innerText()).trim(),'Relationship Dynamic');
  assert.equal(await page.locator('#relationship-important').getAttribute('maxlength'),'2000');
  assert.equal(await page.locator('[name=relationship-kind],[name=person-tone],#person-humor-level,#person-inside-jokes').count(),0);
  assert(await page.locator('[name=person-plans][value=ask_me]').isChecked());
  await page.locator('#relationship-context').fill('We are partners. Keep replies warm and honest.');
  await page.locator('#relationship-important').fill('I work early on Tuesdays. Never claim I am available without checking.');
  await page.locator('[name=person-plans][value=delay_answer]').check();
  phase='girlfriend mode keeps its one-second timer';
  await page.locator('[name=person-mode][value=girlfriend]').check();
  assert.equal(await page.locator('[name=person-engagement]:enabled').count(),0,'Girlfriend mode should not expose a competing editable conversation style');
  assert(await page.locator('#auto-delay option[value="1"]').count());
  await page.locator('#auto-delay').selectOption('1');
  assert(await page.locator('[name=person-mode][value=girlfriend]').isChecked(),'Changing delay must not switch the girlfriend mode radio');
  assert.equal(await page.locator('[name=person-engagement]:enabled').count(),0);
  await page.getByRole('button',{name:'Save profile',exact:true}).click();
  await page.waitForFunction(()=>!busy&&relationshipEdit().saved.autoDelay===1&&profile.engagement==='girlfriend'&&profile.planHandling==='delay_answer');
  assert.equal(await page.evaluate(()=>profile.importantDetails),'I work early on Tuesdays. Never claim I am available without checking.');
  assert(await page.locator('[name=person-mode][value=girlfriend]').isChecked());
  assert.equal(await page.locator('#auto-delay').inputValue(),'1');
  assert.equal(await page.locator('#draft').inputValue(),'My existing unsent draft');
  phase='girlfriend range persists across reload';
  await page.locator('#auto-delay').selectOption('range');
  await page.locator('#person-range-min').fill('5');await page.locator('#person-range-max').fill('30');
  assert(await page.locator('[name=person-mode][value=girlfriend]').isChecked());
  await page.getByRole('button',{name:'Save profile',exact:true}).click();
  await page.waitForFunction(()=>!busy&&relationshipEdit().saved.autoDelayMode==='range'&&profile.engagement==='girlfriend');
  await page.locator('[data-action=close-setup]').click();await page.locator('#reply-setup').click();
  assert(await page.locator('[name=person-mode][value=girlfriend]').isChecked());assert.equal(await page.locator('#auto-delay').inputValue(),'range');
  assert.equal(await page.locator('#person-range-min').inputValue(),'5');assert.equal(await page.locator('#person-range-max').inputValue(),'30');
  phase='instant remains available without altering girlfriend mode';
  await page.locator('#auto-delay').selectOption('0');assert(await page.locator('[name=person-mode][value=girlfriend]').isChecked());
  await page.getByRole('button',{name:'Save profile',exact:true}).click();await page.waitForFunction(()=>!busy&&relationshipEdit().saved.autoDelay===0&&profile.engagement==='girlfriend');
  phase='compact phone layout';
  await page.setViewportSize({width:320,height:640});await page.locator('.relationship-body').evaluate(node=>node.scrollTop=0);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.screenshot({path:'dist/reply-controls-320.png'});
  await page.locator('#relationship-important').scrollIntoViewIfNeeded();
  await page.screenshot({path:'dist/reply-controls-notes-320.png'});
  phase='cloud-only settings and test screen';
  await page.locator('[data-action=close-setup]').click();
  assert.equal(await page.locator('[data-action=quick],.local-quick').count(),0);
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  await page.getByRole('button',{name:'Settings',exact:true}).click();
  assert.equal(await page.locator('[data-action=checkModel],[data-action=download]').count(),0);
  assert.doesNotMatch(await page.locator('body').innerText(),/Gemini Nano|Prepare local model|Optional on-device AI/);
  await page.getByRole('button',{name:'Try AI',exact:true}).click();
  assert.equal(await page.locator('#test-provider option[value=nano],[data-action=download]').count(),0);
  assert.doesNotMatch(await page.locator('body').innerText(),/Gemini Nano|Prepare local model/);
  assert.deepEqual(errors,[]);
  assert.equal(await page.evaluate(()=>calls.filter(call=>['generate','approve','sendNow','testOpenAI','quick','testNano','checkModel','download'].includes(call.action)).length),0,'Editing reply settings must not generate or send anything');
  console.log('PASS: simplified per-contact details and plan choices, persistent girlfriend mode with1s/range/instant delays, cloud-only UI/demo, compact layout, and no implicitAI/send calls. Fictional bridge and preview only.');
 }catch(error){if(page){await page.screenshot({path:'dist/reply-controls-failure.png'});console.log(await page.evaluate(()=>({calls:window.calls?.slice(-8),text:document.body.innerText.slice(-1800)})));}throw new Error(`${phase}: ${error.message}`,{cause:error});}
 finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
