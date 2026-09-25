const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let page,phase='setup';
 try{
  page=await browser.newPage({viewport:{width:412,height:915}});const errors=[];page.on('pageerror',error=>errors.push(error.message));await localAssets(page,url);
  await page.route('**/part/**',route=>route.fulfill({status:404,body:''}));
  await page.addInitScript(()=>{
   const clone=value=>JSON.parse(JSON.stringify(value)),interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.holdText=false;window.heldText=[];
   window.textMms=(id,text)=>({_id:id,thread_id:1,kind:'mms',type:1,m_type:132,msg_box:1,date:id*1000,body:text,parts:[{_id:id*10,ct:'Application/SMIL; charset=utf-8',text:'<smil />'},{_id:id*10+1,ct:' Text/Plain ; charset=utf-8 ',text}]});
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],sub:-1,inboxComplete:true,inbox:[{thread_id:1,_id:71,name:'Fictional friend',address:'+12025550101',kind:'mms',type:1,body:'How you been',date:71000}],jobs:[],cloud:{configured:true},autoDraft:false,inAppSuggestions:false,theme:'midnight',delay:300};
   const sms={_id:50,thread_id:1,kind:'sms',type:2,body:'Earlier SMS',date:50000};
   window.chat={thread:1,address:fixture.inbox[0].address,name:fixture.inbox[0].name,base:50,smsLatest:sms,history:[sms,textMms(70,'Yo'),textMms(71,'How you been')],latest:{key:'mms:71',kind:'mms',id:71,date:71000},latestIncomingTextMmsId:71,latestIncomingMediaId:0,hasMore:false,draft:null,relationship:{cloudEnabled:true,autoDraft:false,autoSend:false,engagement:'natural',body:'We are friends.',importantDetails:'',samples:'',revision:1},replyEligibility:{eligible:true,total:40,owner:20,incoming:20},attachments:{items:[],sending:false,enabled:true,maxItems:4}};
   const access={readSms:true,defaultSms:true,contacts:true};
   window.resultFor=(id,body='pretty good, how about you?')=>({draftState:true,thread:1,base:chat.base,textMmsId:id,draft:{body,source_mms:id,engine:'OpenAI · awaiting your review',alternatives:JSON.stringify([body])},replyDecision:null,replyHold:null,replyWaiting:false,waiting:false});
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=chat;
    else if(action==='replyProfile')result={thread:1,address:chat.address,name:chat.name,relationship:chat.relationship,profileRevision:1,replyEligibility:chat.replyEligibility,access};
    else if(action==='launchInbox'||action==='cacheInbox')result={inbox:[],hasMore:false,access,cacheOnly:true,readOnly:true};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access};
    else if(action==='draftMmsText'){result=resultFor(p.textMmsId);if(holdText){heldText.push({id,result:clone(result)});return;}chat.draft=result.draft;}
    else if(action==='saveDraft')chat.draft=p.body?{body:p.body,engine:'Edited by you',alternatives:'[]'}:null;
    else if(action==='analyzeMedia')result={mediaId:p.mediaId,summary:'A fictional photo.',intent:'Sharing a picture.',limitation:'Intent is uncertain.',suggestion:'nice picture',reason:'reply_needed'};
    else if(['generate','suggestReply','sendNow','approve','sendMms'].includes(action))error='This fixture must not use SMS drafting or send a message.';
    const value=clone(result);setTimeout(()=>nativeResult(id,value,error),0);
   }};
  });
  const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
  await page.goto(url);await page.locator('.row[data-thread="1"]').click();await page.waitForFunction(()=>chatReady());
  phase='ordinary MMS and structural SMIL render as plain conversation text';
  assert.match(await page.locator('[data-message-key="mms:70"] .bubble').innerText(),/^Yo$/);
  assert.match(await page.locator('[data-message-key="mms:71"] .bubble').innerText(),/^How you been$/);
  assert.equal(await page.locator('[data-action=analyze-media]').count(),0);assert.equal(await page.locator('[data-message-key="mms:70"].multimedia-bubble').count(),0);assert.doesNotMatch(await page.locator('.timeline').innerText(),/SMIL|Attachment:|attachment.*unavailable/i);
  await page.evaluate(()=>{for(const id of [70,71])mediaInsights.set(mediaInsightKey(1,id),{base:50,context:mediaContextKey(),result:{summary:'An attachment is unavailable.',suggestion:'old hallucinated media response'}});updateTimeline();});
  assert.equal(await page.locator('.media-understanding,.media-insight').count(),0);
  phase='manual Draft uses exact text MMS while keeping independent SMS base';
  assert(await page.locator('[data-action=generate]').isEnabled());await page.locator('[data-action=generate]').click();await page.waitForFunction(()=>!busy&&document.getElementById('draft').value==='pretty good, how about you?');
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>call.action==='draftMmsText').at(-1).p),{thread:1,base:50,textMmsId:71,inApp:false});assert.equal(await count('analyzeMedia'),0);assert.equal(await count('generate'),0);
  phase='a new MMS clears only a revoked generated draft on the unchanged SMS base';
  await page.evaluate(async()=>{chat.draft=null;chat.history.push(textMms(72,'Another incoming message'));chat.latest={key:'mms:72'};chat.latestIncomingTextMmsId=72;await refresh(true);});assert.equal(await page.locator('#draft').inputValue(),'');assert.equal(await page.evaluate(()=>conversation.base),50);
  await page.locator('#draft').fill('My typed reply stays when another MMS arrives');
  await page.evaluate(async()=>{chat.draft=null;chat.history.push(textMms(73,'A follow-up'));chat.latest={key:'mms:73'};chat.latestIncomingTextMmsId=73;await refresh(true);});assert.equal(await page.locator('#draft').inputValue(),'My typed reply stays when another MMS arrives');
  await page.evaluate(()=>{draft='';dirty=false;draftEdits.clear();conversation.draft=null;syncDraftField();});
  phase='text-only MMS can be drafted without any SMS history';
  await page.evaluate(async()=>{chat.base=0;chat.smsLatest=null;chat.draft=null;chat.history=[textMms(72,'Another text-only message')];chat.latest={key:'mms:72'};chat.latestIncomingTextMmsId=72;await refresh(true);});
  assert(await page.locator('[data-action=generate]').isEnabled());await page.locator('[data-action=generate]').click();await page.waitForFunction(()=>!busy&&document.getElementById('draft').value==='pretty good, how about you?');assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='draftMmsText').at(-1).p.base),0);
  const decline=async()=>{
   await page.evaluate(()=>{chat.draft=null;draft='';dirty=false;draftEdits.clear();conversation.draft=null;syncDraftField();syncLiveComposer();holdText=true;});
   await page.locator('[data-action=generate]').click();await page.waitForFunction(()=>heldText.length===1);
   await page.evaluate(()=>{const late=heldText.shift();holdText=false;nativeResult(late.id,{...late.result,draft:null,replyDecision:{decision:'no_reply',reason:'needs_review',textMmsId:72}},null);});await page.waitForFunction(()=>!busy);await page.evaluate(()=>refresh(true));
   assert.equal(await page.evaluate(()=>conversation.replyDecision?.reason),'needs_review');
  };
  phase='message-bound no-reply reason survives ordinary hydration';await decline();await page.evaluate(()=>refresh(true));assert.equal(await page.evaluate(()=>conversation.replyDecision?.textMmsId),72);
  phase='edited incoming content drops an old no-reply reason';await page.evaluate(async()=>{chat.history[0].body='Updated incoming meaning';chat.history[0].parts[1].text='Updated incoming meaning';await refresh(true);});assert.equal(await page.evaluate(()=>conversation.replyDecision??null),null);
  phase='profile changes drop an old no-reply reason';await decline();await page.evaluate(async()=>{chat.relationship={...chat.relationship,revision:2,importantDetails:'New contact context'};await refresh(true);});assert.equal(await page.evaluate(()=>conversation.replyDecision??null),null);
  phase='owner typing does not preserve an old no-reply reason';await decline();await page.locator('#draft').fill('I will handle this myself');await page.evaluate(()=>refresh(true));assert.equal(await page.evaluate(()=>conversation.replyDecision??null),null);assert.equal(await page.locator('#draft').inputValue(),'I will handle this myself');
  phase='another MMS on the same SMS base makes a late draft unusable';
  await page.evaluate(()=>{holdText=true;chat.draft=null;draft='';dirty=false;draftEdits.clear();conversation.draft=null;syncDraftField();syncLiveComposer();});await page.locator('[data-action=generate]').click();await page.waitForFunction(()=>heldText.length===1);
  await page.evaluate(()=>{chat.history.push(textMms(73,'Newer incoming text'));chat.latest={key:'mms:73'};chat.latestIncomingTextMmsId=73;conversation=mergeConversation(1,structuredClone(chat));updateTimeline();syncLiveComposer();const late=heldText.shift();holdText=false;nativeResult(late.id,late.result,null);});await page.waitForFunction(()=>!busy);
  assert.equal(await page.locator('#draft').inputValue(),'');assert.equal(await page.evaluate(()=>conversation.base),0);
  phase='in-app text MMS suggestion respects typing and uses the latest target once';
  await page.evaluate(async()=>{if(refreshing)await refreshing;holdText=true;fixture.inAppSuggestions=true;chat.replyDecision={decision:'no_reply',reason:'needs_review'};await refresh(true);});await page.waitForFunction(()=>heldText.length===1);
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>call.action==='draftMmsText').at(-1).p),{thread:1,base:0,textMmsId:73,inApp:true});
  await page.locator('#draft').fill('My own answer as the suggestion runs');
  await page.evaluate(()=>{const late=heldText.shift();holdText=false;nativeResult(late.id,late.result,null);});await page.waitForFunction(()=>!suggestionRequest);
  assert.equal(await page.locator('#draft').inputValue(),'My own answer as the suggestion runs');const attempts=await count('draftMmsText');await page.evaluate(()=>refresh(true));assert.equal(await count('draftMmsText'),attempts);
  phase='actual attachments retain their separate analysis action';
  await page.evaluate(async()=>{if(refreshing)await refreshing;fixture.inAppSuggestions=false;chat.replyDecision=null;chat.draft=null;draft='';dirty=false;draftEdits.clear();chat.history.push({...textMms(80,'Photo caption'),parts:[{_id:800,ct:'image/jpeg'},{_id:801,ct:'text/plain',text:'Photo caption'}]});chat.latest={key:'mms:80'};chat.latestIncomingTextMmsId=0;chat.latestIncomingMediaId=80;await refresh(true);syncDraftField();});
  assert(await page.locator('[data-action=generate]').isDisabled());assert.equal(await page.locator('[data-action=analyze-media][data-media-id="80"]').count(),1);await page.locator('[data-action=analyze-media][data-media-id="80"]').click();await page.locator('[data-media-insight="80"] .media-understanding').waitFor();assert.equal(await count('analyzeMedia'),1);
  phase='unconfirmed, pending, HTML and truncated targets never unlock text drafting';
  for(const [id,row] of [[81,{...await page.evaluate(()=>textMms(81,'Waiting')),m_type:130,download:{status:'pending',retryAllowed:true},parts:[]}],[82,{...await page.evaluate(()=>textMms(82,'Unknown')),parts:[{_id:820,ct:'application/x-unknown'}]}],[83,{...await page.evaluate(()=>textMms(83,'HTML text')),parts:[{_id:830,ct:'text/html',text:'<b>Literal HTML</b>'}]}],[84,{...await page.evaluate(()=>textMms(84,'Shortened')),truncated:true}]]){
   await page.evaluate(async({id,row})=>{chat.history.push(row);chat.latest={key:`mms:${id}`};chat.latestIncomingTextMmsId=0;chat.latestIncomingMediaId=id;await refresh(true);},{id,row});assert(await page.locator('[data-action=generate]').isDisabled());
  }
  assert.equal(await page.locator('[data-message-key="mms:83"] .bubble b').count(),0);
  phase='plan and stop holds still prevent foreground suggestions';
  const previous=await count('draftMmsText');await page.evaluate(async()=>{if(refreshing)await refreshing;fixture.inAppSuggestions=true;chat.history.push(textMms(85,'Are you free tomorrow?'));chat.latest={key:'mms:85'};chat.latestIncomingTextMmsId=85;chat.latestIncomingMediaId=0;chat.replyHold={reason:'plans_need_input'};chat.draft=null;draft='';dirty=false;draftEdits.clear();await refresh(true);await maybeSuggestReply();});assert.equal(await count('draftMmsText'),previous);
  phase='compact text-only chat remains clean';await page.evaluate(async()=>{if(refreshing)await refreshing;fixture.inAppSuggestions=false;chat.replyHold=null;chat.history=[textMms(70,'Yo'),textMms(71,'How you been')];chat.latest={key:'mms:71'};chat.latestIncomingTextMmsId=71;await refresh(true);});await page.setViewportSize({width:320,height:640});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.locator('#toast.show').waitFor({state:'hidden'});await page.screenshot({path:'dist/text-mms-320.png'});
  assert.equal(await page.evaluate(()=>calls.filter(call=>['sendNow','approve','sendMms','generate','suggestReply'].includes(call.action)).length),0);assert.deepEqual(errors,[]);
  console.log('PASS: plain MMS/SMIL rendering, old media insights hidden, exact native text target and SMS-base preservation, MMS-only draft, stale same-base response discarded, safe foreground suggestions, attachments unchanged, conservative unknown targets, planning hold, and narrow layout. Fictional bridge only.');
 }catch(error){if(page){await page.screenshot({path:'dist/text-mms-failure.png'});console.log(await page.evaluate(()=>({calls:calls.slice(-10),conditions:{busy,dirty,draft,suggestion:suggestionRequest,allowed:suggestionAllowed(true),profileDirty:profileDirty(relationshipEdit()),target:latestTextMms()?._id,hold:replyHold(),attempts:[...suggestionAttempts]},text:document.body.innerText.slice(-2200)})));}throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
