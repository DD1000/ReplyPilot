const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';try{
 const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/part/*',route=>route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="260" height="180"><rect width="260" height="180" fill="#3f518c"/><circle cx="130" cy="90" r="42" fill="#c8d3ff"/></svg>'}));
 await page.addInitScript(()=>{
  const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
  const clone=value=>JSON.parse(JSON.stringify(value));window.calls=[];window.contextReplies=[];window.holdContext=false;window.failContext=false;
  window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{thread_id:1,name:'Alex Rivera',address:'+12025550101',body:'Current SMS',date:30000,type:1}]};
  window.chat={base:90,history:[{_id:90,type:1,body:'Current SMS',date:30000}],hasMore:false,draft:{body:'Saved current reply',engine:'Edited by you',alternatives:'[]'},relationship:{body:'Saved profile',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:300},replyEligibility:{eligible:true,total:20,owner:10,incoming:10}};
  const sms=(id,date,body)=>({key:'sms:'+id,kind:'sms',_id:id,thread_id:1,date,type:1,body,address:'+12025550101'});
  const mms=(id,date)=>({key:'mms:'+id,kind:'mms',_id:id,thread_id:1,date,type:1,body:'Photo',address:'+12025550101',parts:[{_id:700+id,ct:'image/png'},{_id:800+id,ct:'text/plain',text:'Fictional photo caption'}],m_type:132,msg_box:1,truncated:id===7});
  const point=row=>({date:row.date,kind:row.kind,id:row._id});
  const base={thread:1,address:'+12025550101',name:'Alex Rivera',readOnly:false,anchor:'mms:7'};
  window.respondContext=()=>contextReplies.shift()();
  window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const done=(value,error=null)=>nativeResult(id,clone(value),error);const reply=()=>{
   if(action==='snapshot')done(fixture);else if(action==='conversation')done(chat);else if(action==='saveDraft'){chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};done({});}
   else if(action==='media')done([{_id:7,thread_id:1,name:'Alex Rivera',date:10,m_type:132,parts:[{_id:707,ct:'image/png'}]},{_id:8,thread_id:99,name:'Fictional group',date:20,m_type:132,parts:[{_id:708,ct:'image/png'}]}]);
   else if(action==='mediaConversation'){
    if(failContext){done(null,'This media is temporarily unavailable.');return;}
    const history=p.direction==='older'?[sms(2,2000,'Earlier context'),sms(3,3000,'A little later')]:p.direction==='newer'?[sms(8,11000,'Later context'),sms(9,12000,'End of history')]:[sms(6,9000,'Before the photo'),sms(7,10000,'Same time, different SMS id'),mms(p.mediaId,10000)];
    done({...base,...(p.mediaId===8?{thread:99,address:'',name:'Fictional group',readOnly:true,anchor:'mms:8'}:{}),history,before:point(history[0]),after:point(history.at(-1)),hasOlder:p.direction!=='older',hasNewer:p.direction!=='newer'});
   }else done({});};if(action==='mediaConversation'&&holdContext)contextReplies.push(reply);else setTimeout(reply,0);
  }};
 });
 const nav=name=>page.locator(`[data-action=nav][aria-label="${name}"]`);
 const jump=id=>page.locator(`[data-action=media-context][data-media-id="${id}"]`);
 const noWrites=async()=>assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['sendNow','approve','generate','quick','saveProfile','testOpenAI','testNano'].includes(call.action))),[]);
 await page.goto(url);await page.locator('.row').click();await page.locator('#draft').waitFor();await page.locator('#draft').fill('My current unsent draft');await page.waitForFunction(()=>chat.draft.body==='My current unsent draft');
 await nav('Media').evaluate(node=>node.click());await jump(7).waitFor();await page.evaluate(()=>{window.before={thread:current.thread_id,address:current.address,base:conversation.base,draft,profile:JSON.stringify(conversation.relationship),jobs:JSON.stringify(state.jobs)};});
 phase='exact historical MMS anchor with real part rendering and distinct SMS identity';
 await jump(7).click();await page.locator('.media-anchor[data-media-key="mms:7"]').waitFor();assert.equal(await page.locator('.media-anchor img').getAttribute('src'),'/part/707');
 await page.waitForFunction(()=>document.querySelector('.media-anchor img').complete&&document.querySelector('.media-anchor img').naturalWidth>0);
 await page.screenshot({path:'dist/media-conversation-preview.png'});
 assert.deepEqual(await page.locator('.media-history-row').evaluateAll(rows=>rows.map(row=>row.dataset.mediaKey)),['sms:6','sms:7','mms:7']);
 assert.equal(await page.locator('.media-anchor .media-truncated').innerText(),'Some message content is shortened.');assert.equal(await page.locator('#draft,#accept,[data-action=generate]').count(),0);
 assert.equal(await page.evaluate(()=>current.thread_id===before.thread&&current.address===before.address&&conversation.base===before.base&&draft===before.draft&&JSON.stringify(conversation.relationship)===before.profile&&JSON.stringify(state.jobs)===before.jobs),true);
 await page.evaluate(()=>onMessagesChanged());assert.equal(await page.locator('.media-anchor').count(),1);await noWrites();
 phase='older and newer paging preserves both boundaries and selected media';
 await page.locator('[data-action=media-context-older]').click();await page.locator('[data-media-key="sms:2"]').waitFor();await page.locator('[data-action=media-context-newer]').click();await page.locator('[data-media-key="sms:9"]').waitFor();
 assert.deepEqual(await page.locator('.media-history-row').evaluateAll(rows=>rows.map(row=>row.dataset.mediaKey)),['sms:2','sms:3','sms:6','sms:7','mms:7','sms:8','sms:9']);
 assert.equal(await page.locator('[data-action=media-context-older],[data-action=media-context-newer]').count(),0);
 assert.deepEqual(await page.evaluate(()=>calls.filter(call=>call.action==='mediaConversation').map(call=>call.p)),[{mediaId:7},{mediaId:7,direction:'older',cursor:{date:9000,kind:'sms',id:6}},{mediaId:7,direction:'newer',cursor:{date:10000,kind:'mms',id:7}}]);
 phase='latest conversation uses current SMS state, never historical MMS as base';
 await page.locator('[data-action=media-context-latest]').click();await page.locator('#draft').waitFor();assert.equal(await page.locator('#draft').inputValue(),'My current unsent draft');assert.equal(await page.evaluate(()=>conversation.base),90);await noWrites();
 phase='read-only group history has no send entry';
 await nav('Media').evaluate(node=>node.click());await jump(8).waitFor();await jump(8).click();await page.locator('.media-anchor[data-media-key="mms:8"]').waitFor();assert.equal(await page.locator('[data-action=media-context-latest],#draft,#accept').count(),0);
 await page.setViewportSize({width:320,height:470});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);assert(await page.locator('[data-action=media-context-back]').isVisible());await page.evaluate(()=>goBack());await jump(7).waitFor();
 phase='load failure is recoverable and navigation discards delayed responses';
 await page.evaluate(()=>{failContext=true;});await jump(7).click();await page.locator('[data-action=media-context-retry]').waitFor();assert(await page.locator('[data-action=media-context-back]').isVisible());
 await page.evaluate(()=>{failContext=false;});await page.locator('[data-action=media-context-retry]').click();await page.locator('.media-anchor').waitFor();await page.locator('[data-action=media-context-back]').click();await jump(7).waitFor();
 await page.evaluate(()=>{holdContext=true;});await jump(7).click();await page.waitForFunction(()=>contextReplies.length===1);await page.locator('[data-action=media-context-back]').click();await page.evaluate(()=>respondContext());await jump(7).waitFor();assert.equal(await page.locator('.media-browse-card').count(),0);await noWrites();assert.deepEqual(errors,[]);
 console.log('PASS: exact media anchor and photo, mixed SMS/MMS identity and native tie order, older/newer cursors, read-only groups, preserved current recipient/base/draft/profile/timers, explicit latest without generation, retry, stale-navigation guards and narrow layout. Fictional bridge/fixture image only.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}})().catch(error=>{console.error(error);process.exit(1);});
