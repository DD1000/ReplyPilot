const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // UI contract fixture only. Real parsing/readiness are covered by Java tests.
  // No file picker, contact provider, AI service or carrier is contacted.
  await page.addInitScript(()=>{
   const interval=window.setInterval.bind(window);window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const copy=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.holdImport=false;window.holdAnalysis=false;window.importReplies=[];window.analysisReplies=[];window.cancelImport=false;
   window.low={eligible:false,total:3,owner:1,incoming:2};window.ready={eligible:true,total:23,owner:11,incoming:12};
   window.checkedLog=Array.from({length:20},(_,i)=>`${i%2?'Them':'Me'}: ${i===5?'<img src=x onerror="throw 1"> ':''}Fictional imported message ${i+1}`).join('\n');
   window.namedLog=Array.from({length:20},(_,i)=>`[9/23/26, 10:${String(i).padStart(2,'0')}] ${i%2?'Maya':'Alex'}: ${i===5?'<img src=x onerror="throw 1"> ':''}Fictional imported message ${i+1}`).join('\n');
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,contactsPermission:false,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true,url:'https://example.invalid'},jobs:[],inbox:[1,2].map(id=>({thread_id:id,name:id===1?'Maya Chen':'Jordan Ellis',address:`+1202555014${id}`,body:'A fictional incoming message',date:id*10000,type:1,read:1}))};
   const profile=()=>({body:'',samples:'',cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:300,relationshipKind:'Friend',tone:'Natural'});
   window.chats=Object.fromEntries([1,2].map(id=>[id,{base:100+id,history:[{_id:100+id,type:1,body:'A fictional incoming message',date:id*10000}],hasMore:false,draft:{body:`Current reply for contact ${id}`,engine:'Local fixture',alternatives:'[]'},replyDecision:null,replyEligibility:copy(low),relationship:profile()}]));
   window.releaseImports=()=>{holdImport=false;importReplies.splice(0).forEach(reply=>reply());};
   window.releaseAnalyses=()=>{holdAnalysis=false;analysisReplies.splice(0).forEach(reply=>reply());};
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=chats[p.thread];
    else if(action==='importChatLog')result=cancelImport?{cancelled:true}:{body:namedLog,fileName:'Maya <log>.txt'};
    else if(action==='analyzeChatLog'){
     if(p.body==='')result={samples:'',messageCount:0,ownerCount:0,incomingCount:0,truncated:false,replyEligibility:copy(low)};
     else if(p.body===namedLog&&p.ownerLabel!=='Alex')error='Your name does not match either speaker in this export. Check the spelling.';
     else if(p.body===namedLog||p.body===checkedLog)result={samples:checkedLog,messageCount:20,ownerCount:10,incomingCount:10,truncated:false,replyEligibility:copy(ready)};
     else error='Group chats are not supported. Choose a chat with exactly two people.';
    }else if(action==='saveProfile'){
     const eligibility=p.samples===checkedLog?ready:low;
     if((p.autoDraft||p.autoSend)&&!eligibility.eligible)error='Automatic replies need more history.';
     else{chats[p.thread].relationship={...p,replyEligibility:copy(eligibility)};chats[p.thread].replyEligibility=copy(eligibility);result=chats[p.thread].relationship;}
    }else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};}
    else if(action==='testOpenAI')result={decision:'no_reply',reason:'needs_review',body:'',engine:'OpenAI fixture',elapsedMs:25};
    else if(action==='media')result=[];
    const captured=copy(result),reply=()=>nativeResult(id,captured,error);
    if(action==='importChatLog'&&holdImport)importReplies.push(reply);
    else if(action==='analyzeChatLog'&&holdAnalysis)analysisReplies.push(reply);
    else setTimeout(reply,0);
   }};
  });
  const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
  const openSetup=async()=>{if(await page.locator('#reply-setup').getAttribute('aria-expanded')!=='true')await page.locator('#reply-setup').click();await page.locator('#relationship-panel').waitFor();};
  const openLog=async()=>{await openSetup();if(!await page.locator('#profile-samples').evaluate(element=>element.open))await page.locator('#profile-samples > summary').click();};
  const closeSetup=async()=>{if(await page.locator('#relationship-panel').count())await page.getByRole('button',{name:'Close reply setup',exact:true}).click();};
  const switchContact=async id=>{await closeSetup();await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await page.locator(`.row[data-thread="${id}"]`).click();await page.locator('#draft').waitFor();};
  const automaticModes=()=>page.locator('[name=person-mode][value=automatic],[name=person-mode][value=auto-send],[name=person-mode][value=instant]');
  const assertNoDispatch=async()=>assert.equal(await page.evaluate(()=>calls.some(call=>['generate','quick','sendNow','approve','testNano'].includes(call.action))),false,'Importing/checking history must never generate or send a reply');
  await page.goto('http://127.0.0.1:8769');await page.locator('.row[data-thread="1"]').click();await openLog();

  phase='insufficient history hides automatic modes while preserving manual replies';
  assert.equal(await automaticModes().count(),0);assert.match(await page.locator('#reply-readiness').innerText(),/Automatic replies need more history/);
  assert.match(await page.locator('#reply-readiness').innerText(),/3 usable messages/);
  assert(await page.locator('[name=person-mode][value=manual]').isChecked());
  assert.equal(await page.locator('#relationship-samples').getAttribute('maxlength'),'262144');
  const draft=await page.locator('#draft').inputValue();assert.equal(draft,'Current reply for contact 1');

  phase='import stages the chosen file and requires a correct explicit owner before checking';
  await page.getByRole('button',{name:'Import .txt file',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#relationship-samples')?.value.startsWith('[9/23/26'));
  assert.equal(await count('saveProfile'),0);assert.equal(await count('analyzeChatLog'),0);
  assert.equal(await automaticModes().count(),0);assert(await page.getByRole('button',{name:'Save profile',exact:true}).isDisabled());
  await page.locator('#chat-log-owner').fill('Wrong name');await page.getByRole('button',{name:'Check chat log',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#chat-log-error')?.textContent.includes('does not match'));
  assert.equal(await count('saveProfile'),0);assert.equal(await automaticModes().count(),0);assert.equal(await page.evaluate(()=>chats[1].relationship.samples),'');

  phase='checking normalizes and unlocks choices without opting in or saving';
  await page.locator('#chat-log-owner').fill('Alex');await page.getByRole('button',{name:'Check chat log',exact:true}).click();
  await page.locator('#chat-log-preview').waitFor();assert.equal(await automaticModes().count(),3);
  assert.match(await page.locator('#reply-readiness').innerText(),/Enough history/);assert.match(await page.locator('#chat-log-preview').innerText(),/20 messages ready · 10 yours · 10 theirs/);
  assert.equal(await page.locator('#relationship-samples').inputValue(),await page.evaluate(()=>checkedLog));
  assert.equal(await page.locator('#chat-log-preview img').count(),0,'Imported message text must remain escaped');
  assert(await page.locator('[name=person-mode][value=manual]').isChecked());assert.equal(await count('saveProfile'),0);
  assert.equal(await page.evaluate(()=>chats[1].relationship.autoDraft||chats[1].relationship.autoSend),false);
  assert.equal(await page.locator('#draft').inputValue(),draft);await assertNoDispatch();
  await page.locator('#relationship-panel').screenshot({path:'dist/chat-log-ready-preview.png'});

  phase='only explicit profile saving persists the normalized log for its recipient';
  await page.getByRole('button',{name:'Save profile',exact:true}).click();
  await page.waitForFunction(()=>calls.filter(call=>call.action==='saveProfile').length===1&&!document.querySelector('#relationship-context').disabled);
  const saved=await page.evaluate(()=>calls.find(call=>call.action==='saveProfile').p);
  assert.equal(saved.thread,1);assert.equal(saved.samples,await page.evaluate(()=>checkedLog));assert.equal(saved.autoDraft,false);assert.equal(saved.autoSend,false);
  assert.equal(await page.evaluate(()=>chats[2].relationship.samples),'');await assertNoDispatch();

  phase='removing the log hides automatic modes and clears unsaved automatic choices';
  await page.locator('[name=person-mode][value=instant]').check();
  await page.getByRole('button',{name:'Remove log',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#relationship-samples')?.value==='');
  assert.equal(await automaticModes().count(),0);assert(await page.locator('[name=person-mode][value=manual]').isChecked());
  assert.equal(await count('saveProfile'),1,'Removing is staged until Save profile');
  await page.getByRole('button',{name:'Save profile',exact:true}).click();await page.waitForFunction(()=>calls.filter(call=>call.action==='saveProfile').length===2&&!document.querySelector('#relationship-context').disabled);
  const removed=await page.evaluate(()=>calls.filter(call=>call.action==='saveProfile').at(-1).p);
  assert.equal(removed.samples,'');assert.equal(removed.autoDraft,false);assert.equal(removed.autoSend,false);

  phase='a delayed import result cannot enter another contact';
  await page.evaluate(()=>{holdImport=true;});await page.getByRole('button',{name:'Import .txt file',exact:true}).click();await page.waitForFunction(()=>importReplies.length===1);
  await switchContact(2);await openLog();await page.evaluate(()=>releaseImports());
  assert.equal(await page.locator('#relationship-samples').inputValue(),'');assert.equal(await page.locator('#draft').inputValue(),'Current reply for contact 2');assert.equal(await count('saveProfile'),2);

  phase='a delayed check result also stays with its originating contact';
  await switchContact(1);await openLog();await page.locator('#relationship-samples').fill(await page.evaluate(()=>checkedLog));
  await page.evaluate(()=>{holdAnalysis=true;});await page.getByRole('button',{name:'Check chat log',exact:true}).click();await page.waitForFunction(()=>analysisReplies.length===1);
  await switchContact(2);await openLog();await page.evaluate(()=>releaseAnalyses());
  assert.equal(await page.locator('#relationship-samples').inputValue(),'');assert.equal(await automaticModes().count(),0);assert.equal(await count('saveProfile'),2);
  assert.equal(await page.evaluate(()=>chats[1].relationship.samples),'');assert.equal(await page.evaluate(()=>chats[2].relationship.samples),'');
  await page.evaluate(()=>{cancelImport=true;});await page.getByRole('button',{name:'Import .txt file',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('[data-action=import-chat-log]').disabled);assert.equal(await page.locator('#relationship-samples').inputValue(),'');

  phase='needs_review leaves a clear notice and a writable manual composer';
  await closeSetup();await page.evaluate(async()=>{chats[2].draft=null;chats[2].replyDecision={decision:'no_reply',reason:'needs_review',message:'This needs your judgment. Write a reply yourself.'};await onMessagesChanged();});
  await page.waitForFunction(()=>document.querySelector('#reply-decision strong')?.textContent==='Reply needs you');
  assert.equal(await page.locator('#draft').isDisabled(),false);await page.locator('#draft').fill('I will answer this myself.');
  assert.equal(await page.getByRole('button',{name:'Send message',exact:true}).isDisabled(),false);await assertNoDispatch();
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Try AI',exact:true}).click();
  await page.locator('#test-message').fill('Can you promise to pay for that?');await page.getByRole('button',{name:'Generate test reply',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.test-reply')?.textContent==='Reply needs you');
  assert.equal(await page.locator('#test-error').innerText(),'');assert.equal(await count('testOpenAI'),1);assert.equal(await count('saveProfile'),2);
  await assertNoDispatch();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);
  console.log('PASS: insufficient history hides automatic modes; importing/checking stays local and staged; wrong owner rejects; normalized log unlocks choices without opt-in; explicit save targets only the chosen recipient; removal disables unsaved automatic choices; late import/check results cannot cross contacts; needs_review preserves manual writing and appears correctly in Try AI. Fictional bridge only, no files, contacts, SMS or AI accessed.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
