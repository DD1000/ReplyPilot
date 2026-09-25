const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/part/**',route=>route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180"><rect width="320" height="180" fill="#34475f"/><circle cx="250" cy="43" r="20" fill="#e9be94"/><path d="M0 150 85 56 167 150 233 83 320 165V180H0Z" fill="#739294"/><text x="18" y="163" font-family="sans-serif" font-size="16" fill="white">Fictional test photo</text></svg>'}));
  // Native requests terminate in this local fixture. No device messages or media are read.
  await page.addInitScript(()=>{
   const interval=window.setInterval;
   window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.mediaReplies=[];window.saveReplies=[];window.holdSaves=false;window.injected=0;
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:[{thread_id:1,name:'Test friend',address:'+12025550147',body:'Coffee tomorrow?',date:1000,type:1},{thread_id:2,name:'Coworker',address:'+12025550148',body:'Monday works',date:1000,type:1}]};
   window.chat={history:[{_id:41,type:1,body:'Coffee tomorrow?',date:1000}],hasMore:false,before:{date:1000,id:41},base:41,draft:{body:'sure, what time?',engine:'Local fixture',alternatives:'[]'},relationship:{body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300}};
   window.resolveMedia=(value=[],error=null)=>{const reply=mediaReplies.shift();if(!reply)throw new Error('No pending fixture media request');reply(value,error);};
   window.releaseSaves=()=>saveReplies.splice(0).forEach(reply=>reply());
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});
    const finish=(value,error=null)=>nativeResult(id,JSON.parse(JSON.stringify(value)),error);
    if(action==='media'){mediaReplies.push(finish);return;}
    const reply=()=>{
     if(action==='snapshot')finish(fixture);
     else if(action==='conversation')finish(chat);
     else if(action==='saveDraft'){chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});}
     else finish({});
    };
    if(action==='saveDraft'&&holdSaves)saveReplies.push(reply);else setTimeout(reply,0);
   }};
  });
  const nav=name=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name,exact:true});
  const search=()=>page.getByRole('textbox',{name:'Find a conversation',exact:true});
  const mediaHeading=()=>page.getByRole('heading',{name:'A little more than words.',exact:true});
  const mediaCount=()=>page.evaluate(()=>calls.filter(call=>call.action==='media').length);
  const searchPaint=()=>search().evaluate(el=>{const input=getComputedStyle(el),shell=getComputedStyle(el.closest('.search'));return {outline:input.outline,boxShadow:input.boxShadow,shellOutline:shell.outline,shellShadow:shell.boxShadow,shellBorder:shell.borderColor};});
  const searchBlurred=async query=>{
   assert.equal(await search().inputValue(),query);
   assert.equal(await search().evaluate(el=>document.activeElement===el||el.closest('.search').matches(':focus-within')),false);
  };
  const open=async()=>{await page.goto(url);await search().waitFor();};

  phase='search leaves focus while preserving the query';
  await open();const restingPaint=await searchPaint();
  await search().fill('friend');assert.equal(await page.locator('.row').count(),1);
  await page.getByRole('heading',{name:'Messages',exact:true}).click();await searchBlurred('friend');
  assert.deepEqual(await searchPaint(),restingPaint);
  await search().focus();await page.keyboard.press('Escape');await searchBlurred('friend');
  assert.deepEqual(await searchPaint(),restingPaint);
  await search().focus();await page.keyboard.press('Enter');await searchBlurred('friend');
  await search().focus();await page.evaluate(()=>onKeyboardHidden());await searchBlurred('friend');
  await search().focus();await nav('Settings').click();await page.getByRole('heading',{name:'Settings',exact:true}).waitFor();
  await nav('Messages').click();await searchBlurred('friend');assert.deepEqual(await searchPaint(),restingPaint);
  await search().focus();await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await searchBlurred('friend');
  assert.deepEqual(await searchPaint(),restingPaint);
  await page.getByRole('button',{name:'New text message',exact:true}).focus();await page.keyboard.press('Tab');
  assert.equal(await search().evaluate(el=>document.activeElement===el),true,'Search remains reachable by keyboard');
  assert.notDeepEqual(await searchPaint(),restingPaint,'Keyboard focus must have a visible indicator');
  await page.keyboard.press('Escape');await searchBlurred('friend');

  phase='Media renders before its request and pending draft save resolve';
  await page.setViewportSize({width:1280,height:900});await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();
  await page.evaluate(()=>{holdSaves=true;});await page.locator('#draft').fill('preserve my in-progress reply');
  await page.waitForFunction(()=>saveReplies.length>0);
  await nav('Media').click();await mediaHeading().waitFor({timeout:1500});
  assert.equal(await nav('Media').getAttribute('aria-current'),'page');
  assert.equal(await page.evaluate(()=>saveReplies.length>0),true,'The route must not wait for a draft save');
  await page.waitForFunction(()=>mediaReplies.length===1);
  assert.equal(await page.getByRole('heading',{name:'No attachments yet.',exact:true}).count(),0,'Loading is not an empty result');
  await page.locator('.media-loading').waitFor();
  assert.equal(await page.locator('#media-content').getAttribute('aria-busy'),'true');
  assert.equal(await page.locator('.media-loading').getAttribute('role'),'status');
  assert.equal(await page.locator('.pilot-loader').count(),1);
  await page.screenshot({path:'dist/media-loading-preview.png'});
  await page.setViewportSize({width:412,height:915});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:'dist/media-phone-loading-preview.png'});
  await page.setViewportSize({width:1280,height:900});
  await nav('Media').click();await page.getByRole('button',{name:'Refresh multimedia',exact:true}).evaluate(el=>el.click());
  assert.equal(await mediaCount(),1,'Repeated loading actions share one in-flight request');
  await page.evaluate(()=>{holdSaves=false;releaseSaves();resolveMedia([]);});
  await page.getByRole('heading',{name:'No attachments yet.',exact:true}).waitFor();
  assert.equal(await page.locator('.media-loading').count(),0);
  await nav('Messages').click();await page.locator('#draft').waitFor();assert.equal(await page.locator('#draft').inputValue(),'preserve my in-progress reply');
  await nav('Media').click();await mediaHeading().waitFor();assert.equal(await mediaCount(),1,'Returning uses the completed empty result');

  phase='successful refresh escapes text and loads images lazily';
  await page.getByRole('button',{name:'Refresh multimedia',exact:true}).click();await page.locator('.media-loading').waitFor();
  await page.evaluate(()=>resolveMedia([{_id:50,name:'<svg onload="window.injected=1">',date:1000,m_type:132,parts:[{_id:57,ct:'text/plain',text:'<img src=x onerror="window.injected=1">'},{_id:58,ct:'image/gif'},{_id:59,ct:'application/<script>window.injected=1</script>'}]}]));
  await page.getByRole('heading',{name:'<svg onload="window.injected=1">',exact:true}).waitFor();
  assert.equal(await page.locator('.media-image').getAttribute('loading'),'lazy');
  assert.equal(await page.locator('.media-image').getAttribute('decoding'),'async');
  assert.equal(await page.locator('.media-image').getAttribute('src'),'/part/58');
  assert((await page.locator('main').innerText()).includes('<img src=x onerror="window.injected=1">'));
  assert(!(await page.locator('main').innerText()).includes('application/<script>window.injected=1</script>'),'Invalid MIME parts are omitted');
  assert.equal(await page.locator('[onload],[onerror],main script').count(),0);assert.equal(await page.evaluate(()=>injected),0);
  await nav('Settings').click();await nav('Media').click();assert.equal(await mediaCount(),2);

  phase='failure shows an honest retry, then clears after success';
  await page.getByRole('button',{name:'Refresh multimedia',exact:true}).click();await page.waitForFunction(()=>mediaReplies.length===1);
  await page.evaluate(()=>resolveMedia(null,'Media is temporarily unavailable.'));
  await page.getByRole('button',{name:'Retry media',exact:true}).waitFor();
  assert.equal(await page.locator('#media-error').getAttribute('role'),'alert');
  assert((await page.locator('main').innerText()).includes('Media is temporarily unavailable.'));
  assert.equal(await page.locator('.media-loading').count(),0);
  await page.getByRole('button',{name:'Retry media',exact:true}).click();await page.locator('.media-loading').waitFor();
  assert.equal(await mediaCount(),4);await page.evaluate(()=>resolveMedia([]));
  await page.getByRole('heading',{name:'No attachments yet.',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Retry media',exact:true}).count(),0);

  phase='late response caches without navigating away from the current screen';
  await page.getByRole('button',{name:'Refresh multimedia',exact:true}).click();await page.waitForFunction(()=>mediaReplies.length===1);
  await nav('Messages').click();await page.locator('#draft').waitFor();await page.locator('#draft').focus();
  await page.evaluate(()=>onKeyboardHidden());
  assert.equal(await page.locator('#draft').evaluate(el=>document.activeElement===el),true,'Keyboard dismissal callback must not blur the reply editor');
  await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(4,4);resolveMedia([{_id:60,name:'A later fictional photo',date:2000,m_type:132,parts:[{_id:61,ct:'image/gif'}]}]);});
  await page.waitForFunction(()=>mediaReplies.length===0);
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  assert.equal(await nav('Messages').getAttribute('aria-current'),'page');
  assert.equal(await mediaHeading().count(),0);
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===savedEditor&&document.activeElement===savedEditor&&savedEditor.selectionStart===4),true,'Late media results must not redraw the active reply editor');
  await nav('Media').click();await page.getByRole('heading',{name:'A later fictional photo',exact:true}).waitFor();assert.equal(await mediaCount(),5);
  phase='only actual attachment rows enter the gallery, including media after many text-only MMS';
  await page.getByRole('button',{name:'Refresh multimedia',exact:true}).click();await page.waitForFunction(()=>mediaReplies.length===1);
  await page.evaluate(()=>{
   const row=(id,name,parts,m_type=132)=>({_id:id,name,date:2000,m_type,parts});
   const textRows=Array.from({length:160},(_,i)=>row(1000+i,'Text only '+i,[{_id:2000+i,ct:' Text/Plain; charset=UTF-8 ',text:'Ordinary text '+i}]));
   const invalidTypes=['text/html','APPLICATION/SMIL','application/smil+xml; charset=utf-8','','image','image/','/jpeg','image/jpeg/extra','image /jpeg','image/*','multipart/mixed','application/<script>'];
   const invalidIds=[0,-1,1.5,'not-an-id','1/evil',9007199254740992,null];
   resolveMedia([...textRows,null,{name:'Malformed parts',parts:{}},
    ...invalidTypes.map((ct,i)=>row(3000+i,'Not an attachment '+i,[{_id:4000+i,ct,text:'Not gallery content'}])),
    ...invalidIds.map((_id,i)=>row(3100+i,'Invalid attachment ID '+i,[{_id,ct:'image/jpeg'}])),
    row(3200,'Pending carrier notification',[],130),row(3201,'Pending with untrusted parts',[{_id:4201,ct:'image/jpeg'}],130),
    row(3202,'Protocol report',[{_id:4202,ct:'image/jpeg'}],134),{...row(3203,'Unsent draft',[{_id:4203,ct:'image/jpeg'}],128),msg_box:3},
    row(50,'Fictional photo',[{_id:57,ct:' TEXT/PLAIN; charset=UTF-8 ',text:'Photo caption <not markup>'},{_id:58,ct:' Image/GIF; name=photo.gif '},{_id:59,ct:'application/smil+xml',text:'Hidden slideshow control'}]),
    row(51,'Fictional audio',[{_id:61,ct:' AUDIO/MPEG; codecs=mp3 '}]),
    row(52,'Fictional video',[{_id:62,ct:' VIDEO/MP4; codecs=avc1 '}]),
    row(53,'Fictional document',[{_id:63,ct:' APPLICATION/PDF; name=example.pdf '}]),
    row(54,'Fictional contact card',[{_id:'64',ct:' Text/VCard; version=4.0 '}]),
    {...row(55,'Fictional file',[{_id:65,ct:'application/octet-stream'}],128),msg_box:2}]);
  });
  await page.getByRole('heading',{name:'Fictional photo',exact:true}).waitFor();
  assert.equal(await page.locator('.media-card').count(),6);
  assert.deepEqual(await page.evaluate(()=>media.map(row=>row._id)),[50,51,52,53,54,55],'Text-only messages must not enter the cached gallery state');
  assert.deepEqual(await page.locator('.media-card [data-action=media-context]').evaluateAll(buttons=>buttons.map(button=>button.dataset.mediaId)),['50','51','52','53','54','55']);
  assert.equal(await page.locator('.media-card img').getAttribute('src'),'/part/58');
  assert.equal(await page.locator('.media-card audio').getAttribute('src'),'/part/61');
  assert.equal(await page.locator('.media-card video').getAttribute('src'),'/part/62');
  assert.deepEqual(await page.locator('.media-card [data-action=open-attachment]').evaluateAll(buttons=>buttons.map(button=>button.dataset.partId)),['63','64','65']);
  assert.match(await page.locator('.media-card').first().innerText(),/Photo caption <not markup>/);
  assert.doesNotMatch(await page.locator('#media-cards').innerText(),/Ordinary text|Text only|Not an attachment|Hidden slideshow|Invalid attachment|Pending/);
  assert.equal(await page.locator('.media-card [data-action=retry-mms]').count(),0);
  await page.setViewportSize({width:412,height:915});await page.screenshot({path:'dist/media-attachments-0.9.6.png'});

  phase='text-only and pending MMS stay visible in their chat, without gallery cards';
  await nav('Messages').click();await page.locator('#draft').waitFor();
  await page.evaluate(async()=>{chat.history.push({kind:'mms',key:'mms:77',_id:77,type:1,date:3000,m_type:132,parts:[{_id:177,ct:'text/plain',text:'Ordinary MMS stays in this conversation'}]},{kind:'mms',key:'mms:78',_id:78,type:1,date:4000,m_type:130,body:'Pending MMS stays in this conversation',parts:[],download:{status:'failed',retryAllowed:true}});await onMessagesChanged();});
  assert.match(await page.locator('[data-message-key="sms:41"]').innerText(),/Coffee tomorrow/);
  assert.match(await page.locator('[data-message-key="mms:77"]').innerText(),/Ordinary MMS stays in this conversation/);
  assert.match(await page.locator('[data-message-key="mms:78"]').innerText(),/Pending MMS stays in this conversation/);
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  await nav('Media').click();await page.getByRole('heading',{name:'Fictional photo',exact:true}).waitFor();
  await page.getByRole('button',{name:'Refresh multimedia',exact:true}).click();await page.waitForFunction(()=>mediaReplies.length===1);
  await page.evaluate(()=>resolveMedia([{_id:77,name:'Ordinary MMS',date:3000,m_type:132,parts:[{_id:177,ct:'text/plain',text:'Ordinary MMS stays in this conversation'}]},{_id:78,name:'Pending MMS',date:4000,m_type:130,parts:[]} ]));
  await page.getByRole('heading',{name:'No attachments yet.',exact:true}).waitFor();
  assert.equal(await page.locator('.media-card').count(),0);assert.equal(await page.evaluate(()=>media.length),0);
  assert.match(await page.locator('#media-feedback').innerText(),/Photos, videos, audio and other attachments will appear here/);
  await page.setViewportSize({width:320,height:640});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.equal(await page.evaluate(()=>calls.some(call=>['sendNow','approve','generate','testOpenAI','testNano'].includes(call.action))),false);
  assert.deepEqual(errors,[]);
  console.log('PASS: responsive Media route; attachment-only cache/rendering with media after 160 text-only rows; MIME case/parameters, malformed types/IDs and pending notifications; captions/image/audio/video/PDF/vCard/files; text-only MMS stays in chat; empty/error/retry, lazy images, navigation/editor preservation and search focus. Synthetic local messages/media only.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
