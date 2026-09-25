// Browser-only interactive preview. It cannot access or send real messages.
if(!window.Native){
 const now=Date.now();let jobs=[],prefs={sub:1,autoDraft:true,tone:'Natural',delay:300,delayMode:'fixed',delayMin:300,delayMax:1800,matchMyStyle:true,theme:'midnight',lockScreenPreviews:true,inAppSuggestions:true};
 try{prefs={...prefs,...JSON.parse(localStorage.getItem('reply-pilot-demo-prefs')||'{}')};if(!Number.isInteger(prefs.delay)||(prefs.delay!==1&&(prefs.delay<60||prefs.delay>604800||prefs.delay%60)))prefs.delay=300;}catch{}
 try{const theme=localStorage.getItem('reply-pilot-demo-theme');if(['forest','ocean','lavender','rose','sunset','slate','midnight','mocha','mint','plum'].includes(theme))prefs.theme=theme;}catch{}
 let sleepSession={mode:'off',until:0,delay:300,delayMode:'fixed',delayMin:300,delayMax:1800,revision:0};try{const saved=JSON.parse(localStorage.getItem('reply-pilot-demo-sleep')||'null');if(saved&&['off','active','paused'].includes(saved.mode)&&Number.isInteger(saved.delay)&&(saved.delay===1||saved.delay>=60&&saved.delay<=604800&&saved.delay%60===0))sleepSession={...sleepSession,...saved};}catch{}
 let batteryUnrestricted=false; // A phone that still limits background work.
 let ownerViews='';
 function saveSleep(){localStorage.setItem('reply-pilot-demo-sleep',JSON.stringify(sleepSession));}
 function pauseAutomaticTimers(){jobs.forEach(j=>{if(j.auto_send&&j.status==='scheduled'){j.status='paused';j.note='Sleep schedule changed. Review before starting another timer.';}});}
 function expireSleep(){if(sleepSession.mode==='active'&&Date.now()>=sleepSession.until){sleepSession={...sleepSession,mode:'paused',revision:sleepSession.revision+1,reason:'cutoff'};pauseAutomaticTimers();saveSleep();}}
 let aboutMe={about:'',voice:'',humor:'',avoid:'',examples:''};try{aboutMe={...aboutMe,...JSON.parse(localStorage.getItem('reply-pilot-demo-about-me')||'{}')};}catch{}
 let relationships={};try{relationships=JSON.parse(localStorage.getItem('reply-pilot-demo-relationships')||'{}');}catch{}
 let pinnedThreads=[];try{const saved=JSON.parse(localStorage.getItem('reply-pilot-demo-pins')||'[]');if(Array.isArray(saved))pinnedThreads=saved.filter(id=>Number.isSafeInteger(id)&&id>0);}catch{}
 const contacts=[['Maya Chen','+1 202 555 0147','Hey! Want to grab coffee tomorrow morning? There’s a new place on Oak Street.'],['Jordan Ellis','+1 202 555 0185','Made it home. Thanks again for tonight!'],['Mom','+1 202 555 0122','Let me know when you have a minute to talk ❤️'],['Sam Rivera','+1 202 555 0179','Just sent over the playlist we were talking about.']];
 const inbox=contacts.map(([name,address,body],i)=>({_id:10+i,thread_id:i+1,name,address,body,date:now-i*1000000,type:1,read:i?1:0,sub_id:1}));
 const histories={1:[{_id:8,type:1,body:'We should catch up soon. It’s been a while!',date:now-600000},{_id:9,type:2,body:"yeah it's been a minute! this week flew by",date:now-540000},{_id:10,type:1,body:contacts[0][2],date:now-20000}]};
 const drafts={1:{base:10,body:'what time were you thinking?',alternatives:'["what time were you thinking?"]',engine:'Preview · sample reply'}};
 const manualRequests=new Map();

 // Fictional browser preview only. Android owns the strict file/parser policy.
 function demoTurns(samples){const turns=[];for(const line of String(samples||'').split(/\r?\n/)){if(!line.trim())continue;const m=line.match(/^(Me|You|Them):\s*(.+)$/i);if(!m)return [];turns.push({speaker:m[1].toLowerCase()==='them'?'them':'me',text:m[2].trim()});}return turns;}
 const demoPersonas={};
function demoPersonaStatus(thread){const trained=demoPersonas[thread];return trained?{thread,state:'ready',trained:true,trainedAt:trained.trainedAt,trainedMessages:trained.trainedMessages,newMessages:0,suggestRetrain:false,thin:trained.trainedMessages<100,model:'preview',persona:trained.persona,error:''}:{thread,state:'untrained',trained:false,error:''};}
function demoEligibility(thread){const trained=demoPersonas[thread];return{available:true,eligible:!!trained,total:trained?.trainedMessages||0,owner:0,incoming:0,scanComplete:true,message:trained?'Autopilot is trained for this chat.':'Train Autopilot for this chat before turning it on.'};}
 function demoDelay(p,prefix='delay'){
  const mode=p[prefix+'Mode']==='range'?'range':'fixed',fixed=p[prefix],min=p[prefix+'Min']??300,max=p[prefix+'Max']??1800;
  const minutes=n=>Number.isInteger(n)&&n>=60&&n<=604800&&n%60===0,validFixed=n=>n===1||prefix==='autoDelay'&&n===0||minutes(n);
  if(mode==='range'&&(!minutes(min)||!minutes(max)||min>max)||mode==='fixed'&&!validFixed(fixed))throw new Error('Choose 1 second or whole minutes from 1 to 10,080. Ranges use whole minutes with From no later than To.');
  return{[prefix]:mode==='range'&&!validFixed(fixed)?300:fixed,[prefix+'Mode']:mode,[prefix+'Min']:minutes(min)&&minutes(max)&&min<=max?min:300,[prefix+'Max']:minutes(min)&&minutes(max)&&min<=max?max:1800};
 }
 function demoProfile(thread){const stored=relationships[thread]||{};return{...stored,body:typeof stored.body==='string'?stored.body:'',importantDetails:typeof stored.importantDetails==='string'?stored.importantDetails:'',planHandling:'delay_answer',cloudEnabled:!!(stored.cloudEnabled&&stored.autoDraft&&stored.autoSend),autoDraft:!!(stored.cloudEnabled&&stored.autoDraft&&stored.autoSend),autoSend:!!(stored.cloudEnabled&&stored.autoDraft&&stored.autoSend),autoDelay:stored.autoDelayMode!=='range'&&[0,60,300].includes(stored.autoDelay)?stored.autoDelay:0,autoDelayMode:'fixed',engagement:'always_reply',revision:stored.revision||0};}
 window.demoApi=async(action,p={})=>{switch(action){
 case 'trainPersona':{const row=inbox.find(item=>item.thread_id===p.thread);if(!row)throw new Error('Choose an existing conversation.');await new Promise(r=>setTimeout(r,600));const history=(histories[p.thread]||[]).filter(m=>[1,2].includes(m.type));demoPersonas[p.thread]={trainedAt:Date.now(),trainedMessages:history.length||12,persona:{writingStyle:'Preview persona: short, lowercase replies with the occasional "lol".',relationship:'Fictional preview contact.',context:'Preview only. Train on your Pixel to learn from real texts.',avoid:'',examples:history.filter(m=>m.type===2).slice(-3).map(m=>({incoming:'',reply:m.body}))}};return{persona:demoPersonaStatus(p.thread),replyEligibility:demoEligibility(p.thread)};}
 case 'forgetPersona':{delete demoPersonas[p.thread];return{persona:demoPersonaStatus(p.thread),replyEligibility:demoEligibility(p.thread)};}
 case 'appSettings':throw new Error('Contact permissions are managed on your Pixel.');
 case 'requestContacts':return {allowed:true,blocked:false};
 case 'contacts':{const query=(p.query||'').toLowerCase().replace(/[ ()-]/g,'');return {allowed:true,contacts:contacts.map(([name,number],i)=>({id:String(i+1),name,number:number.replace(/ /g,''),label:'Mobile'})).filter(c=>(c.name+c.number).toLowerCase().replace(/[ ()-]/g,'').includes(query)),hasMore:false};}
 case 'battery':batteryUnrestricted=true;return{};
 case 'saveOwnerViews':{const text=String(p.text??'').trim();if(text.length>1200)throw new Error('Keep your views within 1,200 characters.');ownerViews=text;return{ownerViews};}
 case 'notificationSettings':throw new Error('Notification sound and lock-screen settings are available on your Pixel.');
 case 'defaultSettings':throw new Error('Open Android default apps on your phone. This browser is a preview.');
 case 'smsRoleStatus':return {defaultSms:true,permissions:true,pending:false};
 case 'role':return {defaultSms:true,message:'Browser preview only. Choose the default app on your Pixel.'};
 case 'testOpenAI':throw new Error('OpenAI tests run in the Android app after pairing your service.');
 case 'connectCloud':case 'checkCloud':throw new Error('Connect from the Android app. This browser is a preview.');
 case 'linkPreview':return {url:p.url,available:false};
 case 'openLink':throw new Error('Open links from Reply Pilot on your Pixel. This browser is a preview.');
 case 'launchHistories':return {conversations:inbox.slice().sort((a,b)=>b.date-a.date).slice(0,10).map(row=>({thread:row.thread_id,page:{thread:row.thread_id,address:row.address,name:row.name,readOnly:true,history:(histories[row.thread_id]||[row]).slice(-30).map(message=>({...message,kind:'sms',key:'sms:'+message._id,thread_id:row.thread_id})),hasOlder:true,hasMore:true}})),savedAt:Date.now(),revision:1,access:{readSms:true,defaultSms:true,contacts:true}};
 case 'launchInbox':return {inbox:inbox.slice().sort((a,b)=>b.date-a.date).slice(0,20),savedAt:Date.now(),revision:1,access:{readSms:true,defaultSms:true,contacts:true}};
 case 'snapshot':expireSleep();return {aboutMe:{...aboutMe},location:{enabled:false,permission:false,backgroundPermission:false,refreshing:false,label:'',updatedAt:0,fresh:false,homeSet:false,homeAddress:'',status:'Location is available on your Pixel.'},sleep:{...sleepSession},contactsAllowed:true,defaultSms:true,permissions:true,notifications:true,exact:true,batteryUnrestricted,ownerViews,sims:[{id:1,name:'Personal SIM'}],cloud:{configured:false},inbox:inbox.map(row=>({...row,pinned:pinnedThreads.includes(row.thread_id)})).sort((a,b)=>Number(b.pinned)-Number(a.pinned)||b.date-a.date),jobs:[...jobs],...prefs};
 case 'cancelHomeCandidates':return {};
 case 'setTheme':{if(!['forest','ocean','lavender','rose','sunset','slate','midnight','mocha','mint','plum'].includes(p.theme))throw new Error('Choose an available theme.');const next={...prefs,theme:p.theme};localStorage.setItem('reply-pilot-demo-prefs',JSON.stringify(next));prefs=next;return{theme:p.theme};}
 case 'pinChat':{if(!Number.isSafeInteger(p.thread)||p.thread<=0||typeof p.pinned!=='boolean'||!inbox.some(row=>row.thread_id===p.thread))throw new Error('Choose an existing conversation.');const next=pinnedThreads.filter(thread=>thread!==p.thread);if(p.pinned)next.push(p.thread);localStorage.setItem('reply-pilot-demo-pins',JSON.stringify(next));pinnedThreads=next;return{thread:p.thread,pinned:p.pinned};}
 case 'prefetchHistory':return {conversations:(p.threads||[]).slice(0,6).map(thread=>({thread,page:{history:histories[thread]||inbox.filter(row=>row.thread_id===thread),hasOlder:false,before:null}}))};
 case 'replyProfile':{const row=inbox.find(item=>item.thread_id===p.thread);if(!row)throw new Error('Choose an existing conversation.');const relationship=demoProfile(p.thread);return{thread:p.thread,address:row.address,name:row.name,readOnly:false,relationship,profileRevision:relationship.revision,replyEligibility:demoEligibility(p.thread),persona:demoPersonaStatus(p.thread),approvedLearning:{count:0,limit:12},access:{readSms:true,defaultSms:true,contacts:true}};}
 case 'conversation':{const history=histories[p.thread]||inbox.filter(x=>x.thread_id===p.thread);return{history,attachments:{items:[],sending:false,enabled:true,maxItems:6,maxBytes:1048576},approvedLearning:{count:0,limit:12},smsLatest:history.at(-1)||null,replyEligibility:demoEligibility(p.thread),relationship:demoProfile(p.thread),styleSamples:Math.min(6,history.filter(m=>m.type===2).length),base:history.at(-1)?._id||0,draft:drafts[p.thread]};}
 case 'saveAboutMe':{for(const [key,max] of [['about',1200],['voice',800],['humor',800],['avoid',800],['examples',2400]])if(typeof p[key]!=='string'||p[key].length>max)throw new Error('Check the profile field limits.');localStorage.setItem('reply-pilot-demo-about-me',JSON.stringify(p));aboutMe={...p};return{...aboutMe};}
 case 'saveLocation':case 'requestLocation':case 'locationSettings':case 'refreshLocation':case 'setHome':case 'clearHome':case 'homeCandidates':case 'confirmHomeCandidate':throw new Error('Location is available on your Pixel. This browser does not read your location.');
 case 'clearApprovedLearning':return{count:0,limit:12};
 case 'suggestReply':case 'analyzeMedia':throw new Error('Real AI suggestions and media understanding run in the paired Android app.');
 case 'pickAttachments':throw new Error('Choose attachments in Reply Pilot on your Pixel. This browser is a preview.');
 case 'removeAttachment':return{items:[],sending:false,enabled:true,maxItems:6,maxBytes:1048576};
 case 'sendMms':case 'retryMms':case 'openAttachment':throw new Error('Carrier attachments are available on your Pixel. No real message was sent.');
 case 'mediaConversation':throw new Error('Open a received photo on your Pixel to browse its conversation.');
 case 'saveProfile':{
  const important=p.importantDetails??'',planHandling=p.planHandling??'delay_answer';
  if(!Number.isInteger(p.thread)||p.thread<=0||typeof p.body!=='string'||p.body.length>1500||typeof p.samples!=='string'||p.samples.length>8000)throw new Error('Use up to 1,500 relationship and 8,000 sample characters.');
  if(typeof important!=='string'||important.length>2000)throw new Error('Keep important details within 2,000 characters.');
  if(!['ask_me','delay_answer'].includes(planHandling))throw new Error('Choose how Pilot should handle plans.');
  const readiness=demoEligibility(p.thread,p.samples);if(p.autoDraft&&!readiness.eligible)throw new Error('Train Autopilot for this chat before turning it on.');
  // Legacy choices stay saved for compatibility but no longer control new replies.
  const saved={...relationships[p.thread],replyEligibility:readiness,body:p.body.trim(),importantDetails:important.trim(),planHandling,samples:p.samples.trim(),cloudEnabled:!!p.cloudEnabled,autoDraft:!!p.cloudEnabled&&!!p.autoDraft,autoSend:!!p.cloudEnabled&&!!p.autoDraft&&!!p.autoSend,...demoDelay(p,'autoDelay'),engagement:'always_reply',shareLocation:p.shareLocation===true,revision:(relationships[p.thread]?.revision||0)+1};
  const next={...relationships,[p.thread]:saved};localStorage.setItem('reply-pilot-demo-relationships',JSON.stringify(next));relationships=next;
  jobs.forEach(j=>{if(j.thread===p.thread&&j.auto_send&&j.status==='scheduled'){j.status='paused';j.note='Reply settings changed. Review before starting another timer.';}});return saved;
 }
 case 'analyzeChatLog':{const raw=String(p.body||'');if(raw.length>262144)throw new Error('Use a smaller log.');const original=demoTurns(raw);if(raw.trim()&&(!original.length||!original.some(t=>t.speaker==='me')||!original.some(t=>t.speaker==='them')))throw new Error('In this browser preview, use Me: and Them: labels. Named exports can be imported on your Pixel.');const kept=original.slice(-50);let samples=kept.map(t=>(t.speaker==='me'?'Me: ':'Them: ')+t.text).join('\n');while(samples.length>8000&&kept.length){kept.shift();samples=kept.map(t=>(t.speaker==='me'?'Me: ':'Them: ')+t.text).join('\n');}return{samples,messageCount:kept.length,ownerCount:kept.filter(t=>t.speaker==='me').length,incomingCount:kept.filter(t=>t.speaker==='them').length,truncated:kept.length<original.length,replyEligibility:demoEligibility(p.thread,samples)};}
 case 'saveRelationship':{if(!Number.isInteger(p.thread)||p.thread<=0||typeof p.body!=='string'||p.body.length>1500)throw new Error('Use a valid conversation and up to 1,500 characters.');const saved={...relationships[p.thread],body:p.body.trim(),revision:(relationships[p.thread]?.revision||0)+1};const next={...relationships,[p.thread]:saved};localStorage.setItem('reply-pilot-demo-relationships',JSON.stringify(next));relationships=next;return saved;}
 case 'compose':{if(!/^\+?[0-9][0-9 ()-]{2,24}$/.test(p.address))throw new Error('Enter a valid phone number.');let row=inbox.find(x=>x.address.replace(/ /g,'')===p.address.replace(/ /g,''));if(!row){row={thread_id:inbox.length+1,address:p.address,name:p.address,body:'New conversation',date:Date.now()};inbox.unshift(row);histories[row.thread_id]=[];}return{thread:row.thread_id,name:row.name};}
 case 'generate':throw new Error('AI replies are generated with OpenAI in the paired Android app. This browser shows sample conversations only.');
 case 'sendNow':{
  if(!p.body?.trim()||p.body.length>1600)throw new Error('Write a reply between 1 and 1,600 characters.');
  if(!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(p.requestId||''))throw new Error('Start this send again from the conversation.');
  const identity=JSON.stringify([p.thread,p.address,p.body,p.sub]),previous=manualRequests.get(p.requestId);
  if(previous){if(previous.identity!==identity)throw new Error('This send request was already used.');return previous.result;}
  const row=inbox.find(x=>x.thread_id===p.thread);if(!row||row.address!==p.address)throw new Error('Open the correct conversation before sending.');
  const history=histories[p.thread]||inbox.filter(x=>x.thread_id===p.thread);
  const base=history.at(-1)?._id||0;
  jobs.forEach(job=>{if(job.thread===p.thread&&(job.auto_send||job.attention_kind==='delay')&&['scheduled','awaiting_alert'].includes(job.status))job.status='paused';});
  const id=Math.max(Date.now(),base+1),message={_id:id,type:2,body:p.body,date:Date.now(),address:p.address};
  histories[p.thread]=[...history,message];delete drafts[p.thread];
  Object.assign(row,{_id:id,body:p.body,type:2,date:message.date});
  const job={_id:id,thread:p.thread,address:p.address,body:p.body,base,sub:p.sub,due:Date.now(),status:'sent',auto_send:0,note:'Demo only. No text was sent.'};jobs.unshift(job);
  const result={id,status:'sent',base,latestBase:id,job};manualRequests.set(p.requestId,{identity,result});return result;
 }
 case 'approve':{const policy=demoDelay(p);if(!p.body?.trim()||p.body.length>1600)throw new Error('Write a reply between 1 and 1,600 characters.');if(jobs.some(j=>j.thread===p.thread&&['scheduled','sending'].includes(j.status)))throw new Error('Cancel the existing timer first.');const seconds=policy.delayMode==='range'?policy.delayMin+Math.floor(Math.random()*(policy.delayMax-policy.delayMin+1)):policy.delay;const range=policy.delayMode==='range'?`Range: ${policy.delayMin/60}–${policy.delayMax/60} min\n`:'';if(!confirm(`Demo only — no real message will be sent.\n\nTo: ${p.address}\n${p.body}\n\n${range}Chosen delay: ${Math.floor(seconds/60)} min ${seconds%60} sec after you confirm.\nAccept and start this timer?`))return null;const id=Date.now();jobs.unshift({_id:id,thread:p.thread,address:p.address,body:p.body,base:p.base,sub:p.sub,due:Date.now()+seconds*1000,status:'scheduled',auto_send:0,note:'Demo only. No text was sent.'});setTimeout(()=>{const j=jobs.find(x=>x._id===id);if(j?.status==='scheduled'){j.status='sent';j.note='Demo only. No text was sent.'}},seconds*1000);return{id};}
 case 'saveDraft':jobs.forEach(j=>{if(j.thread===p.thread&&j.auto_send&&j.status==='scheduled'){j.status='cancelled';j.note='Automatic timer cancelled because you edited the reply.';}});drafts[p.thread]={base:p.base,body:p.body,alternatives:'[]',engine:'Edited by you'};return{};
 case 'cancel':{const j=jobs.find(x=>x._id===p.id);if(j?.status==='scheduled'){j.status='cancelled';j.note='Cancelled before the timer finished.';}return{};}
 case 'sleep':{expireSleep();let next;if(p.action==='start'){if(!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(p.time))throw new Error('Choose a valid time and a delay of 1 second or 1 to 10,080 whole minutes.');const [hours,minutes]=p.time.split(':').map(Number),until=new Date();until.setHours(hours,minutes,0,0);if(until.getTime()<=Date.now())until.setDate(until.getDate()+1);next={mode:'active',until:until.getTime(),...demoDelay(p),reason:''};}else if(p.action==='pause')next={mode:'paused',reason:'manual'};else if(p.action==='resume')next={mode:'off',reason:''};else throw new Error('Choose a sleep action.');sleepSession={...sleepSession,...next,revision:sleepSession.revision+1};pauseAutomaticTimers();saveSleep();return {...sleepSession};}
 case 'settings':{const timer=Object.hasOwn(p,'delay')||Object.hasOwn(p,'delayMode')||Object.hasOwn(p,'delayMin')||Object.hasOwn(p,'delayMax')?demoDelay({...prefs,...p}):{};const next={...prefs,...p,...timer};if((prefs.autoDraft&&!next.autoDraft)||prefs.sub!==next.sub)jobs.forEach(j=>{if(j.auto_send&&j.status==='scheduled'){j.status='paused';j.note='Automatic replies paused by your settings.';}});prefs=next;localStorage.setItem('reply-pilot-demo-prefs',JSON.stringify(prefs));return{};}
 case 'media':return[];
 default:return{};
 }};
}
