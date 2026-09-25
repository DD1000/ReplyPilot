const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // All phone permissions, contacts and SMS threads are local fictional fixtures.
  await page.addInitScript(()=>{
   const interval=window.setInterval;
   window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.contactReplies=[];window.permissionReplies=[];window.composeReplies=[];window.holdCompose=false;window.failCompose=false;window.injected=0;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:false,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:[{thread_id:1,name:'Existing friend',address:'+12025550100',body:'Coffee tomorrow?',date:1000,type:1}]};
   const profile={body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300};
   window.chats={1:{base:41,history:[{_id:41,type:1,body:'Coffee tomorrow?',date:1000}],hasMore:false,before:{date:1000,id:41},draft:{body:'existing draft',engine:'Local fixture',alternatives:'[]'},relationship:{...profile}}};
   window.contactRows=[{id:'101',name:'Alex Rivera',number:'+12025550111',label:'Mobile'},{id:'102',name:'Alex Rivera',number:'+12025550112',label:'Work'},{id:'103',name:'<svg onload="window.injected=1">',number:'+12025550113',label:'Mobile <img src=x onerror="window.injected=1">'}];
   window.resolveContacts=(query,contacts=contactRows,error=null,allowed=true,hasMore=false)=>{
    const index=contactReplies.findIndex(reply=>reply.query===query);if(index<0)throw new Error(`No pending contacts query: ${query}`);
    contactReplies.splice(index,1)[0].finish({allowed,contacts,hasMore},error);
   };
   window.resolvePermission=(allowed,blocked=false)=>{fixture.contactsAllowed=allowed;const reply=permissionReplies.shift();if(!reply)throw new Error('No pending permission request');reply({allowed,blocked});};
   window.releaseCompose=()=>composeReplies.splice(0).forEach(reply=>reply());
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});
    const finish=(value,error=null)=>nativeResult(id,clone(value),error);
    if(action==='contacts'){
     if(!fixture.contactsAllowed)setTimeout(()=>finish({allowed:false,contacts:[],hasMore:false}),0);
     else contactReplies.push({query:p.query||'',finish});
     return;
    }
    if(action==='requestContacts'){permissionReplies.push(finish);return;}
    const reply=()=>{
     if(action==='snapshot')finish(fixture);
     else if(action==='conversation')finish(chats[p.thread]);
     else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});}
     else if(action==='compose'){
      if(failCompose){finish(null,'The conversation could not be opened. Please try again.');return;}
      const thread=100+calls.filter(call=>call.action==='compose').length;
      const match=contactRows.find(contact=>contact.number===p.address);
      chats[thread]={base:0,history:[],hasMore:false,before:null,draft:null,relationship:{...profile}};
      finish({thread,name:match?.name||p.address});
     }else if(action==='media')finish([]);
     else finish({});
    };
    if(action==='compose'&&holdCompose)composeReplies.push(reply);else setTimeout(reply,0);
   }};
  });
  const nav=name=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name,exact:true});
  const recipient=()=>page.getByRole('textbox',{name:'Name or phone number',exact:true});
  const pickerBack=()=>page.locator('[data-action=close-contacts]');
  const choices=()=>page.locator('[data-action=choose-contact]');
  const start=()=>page.getByRole('button',{name:'Start conversation',exact:true});
  const openPicker=async()=>{await page.getByRole('button',{name:'New text message',exact:true}).click();await recipient().waitFor();const bounds=await page.locator('.recipient-search').boundingBox();assert(bounds.height>=40&&bounds.height<=64,`Keep recipient search compact, saw ${bounds.height}px`);};
  const waitQuery=query=>page.waitForFunction(query=>contactReplies.some(reply=>reply.query===query),query);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const noSend=async()=>assert.equal(await page.evaluate(()=>calls.some(call=>['sendNow','approve','generate','testOpenAI','testNano'].includes(call.action))),false,'Choosing a recipient never sends or generates a reply');
  const reset=async()=>{await page.goto(process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769');await page.locator('.row').waitFor();};

  phase='permission is optional, explicit, and does not block manual SMS';
  await reset();await openPicker();
  assert(await page.getByRole('button',{name:'Allow contacts',exact:true}).isVisible());
  assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='requestContacts').length),0);
  assert(await start().isDisabled());
  await recipient().fill('Alex');assert(await start().isDisabled(),'A name must not be sent to the SMS number API');
  await page.getByRole('button',{name:'Allow contacts',exact:true}).click();
  await page.waitForFunction(()=>permissionReplies.length===1);
  assert(await page.locator('[data-action=request-contacts]').isDisabled());
  await page.evaluate(()=>resolvePermission(false));
  await page.waitForFunction(()=>!document.querySelector('[data-action=request-contacts]')?.disabled);
  assert.equal(await recipient().inputValue(),'Alex');assert.equal(await choices().count(),0);
  await page.getByRole('button',{name:'Allow contacts',exact:true}).click();await page.waitForFunction(()=>permissionReplies.length===1);
  await page.evaluate(()=>resolvePermission(false,true));
  await page.getByRole('button',{name:'Open app settings',exact:true}).click();
  assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='appSettings').length),1);
  await recipient().fill('+1 (202) 555-0199');assert(await start().isEnabled());await start().click();
  await page.locator('#draft').waitFor();
  const manual=await page.evaluate(()=>calls.find(call=>call.action==='compose').p.address);
  assert.equal(manual.replace(/[\s().-]/g,''),'+12025550199');assert.equal(await page.locator('#draft').inputValue(),'');await noSend();

  phase='allowing contacts loads without replacing the active search field';
  await reset();await openPicker();await page.getByRole('button',{name:'Allow contacts',exact:true}).click();
  await page.waitForFunction(()=>permissionReplies.length===1);await page.evaluate(()=>resolvePermission(true));await waitQuery('');
  await page.getByRole('status').filter({hasText:'Loading contacts…'}).waitFor();
  await recipient().focus();await page.evaluate(()=>{window.savedRecipient=document.querySelector('#recipient');savedRecipient.setSelectionRange(0,0);resolveContacts('');});
  await page.waitForFunction(()=>document.querySelectorAll('[data-action=choose-contact]').length===3);
  assert.equal(await page.evaluate(()=>document.querySelector('#recipient')===savedRecipient&&document.activeElement===savedRecipient),true);
  assert((await choices().nth(0).innerText()).includes('+12025550111'));
  assert((await choices().nth(1).innerText()).includes('+12025550112'));
  assert((await choices().nth(0).innerText()).includes('Mobile'));assert((await choices().nth(1).innerText()).includes('Work'));
  assert((await choices().nth(2).innerText()).includes('<svg onload="window.injected=1">'));
  assert((await choices().nth(2).innerText()).includes('<img src=x onerror="window.injected=1">'));
  assert.equal(await page.locator('#contact-results [onload],#contact-results [onerror],#contact-results script').count(),0);assert.equal(await page.evaluate(()=>injected),0);
  await page.evaluate(()=>onNativeResume());await waitQuery('');
  await page.evaluate(()=>resolveContacts('',contactRows.map((contact,index)=>index===2?{...contact,name:'Casey Morgan',label:'Home'}:contact)));
  await choices().filter({hasText:'Casey Morgan'}).waitFor();
  await page.screenshot({path:'dist/contacts-picker-preview.png'});

  phase='debounced searches preserve the query, caret and field identity';
  const searchesBefore=await page.evaluate(()=>calls.filter(call=>call.action==='contacts').length);
  await page.evaluate(()=>{for(const query of ['A','Al','Alex']){savedRecipient.value=query;savedRecipient.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText'}));}savedRecipient.setSelectionRange(2,2);});
  await waitQuery('Alex');assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='contacts').length)-searchesBefore,1);
  await page.evaluate(()=>resolveContacts('Alex',contactRows.slice(0,2)));await page.waitForFunction(()=>document.querySelectorAll('[data-action=choose-contact]').length===2);
  assert.equal(await page.evaluate(()=>document.querySelector('#recipient')===savedRecipient&&document.activeElement===savedRecipient&&savedRecipient.value==='Alex'&&savedRecipient.selectionStart===2),true);

  phase='stale query results cannot replace the latest matching contacts';
  await recipient().fill('Old');await waitQuery('Old');await recipient().fill('New');await waitQuery('New');
  await page.evaluate(()=>resolveContacts('New',[{id:'201',name:'Newest match',number:'+12025550201',label:'Mobile'}]));
  await choices().filter({hasText:'Newest match'}).waitFor();
  await page.evaluate(()=>resolveContacts('Old',[{id:'202',name:'Stale match',number:'+12025550202',label:'Old'}]));await settle();
  assert.equal(await choices().filter({hasText:'Newest match'}).count(),1);assert.equal(await choices().filter({hasText:'Stale match'}).count(),0);

  phase='search failure retries the same query and distinguishes an empty result';
  await recipient().fill('Retry me');await waitQuery('Retry me');await page.evaluate(()=>resolveContacts('Retry me',[], 'Contacts are temporarily unavailable.'));
  await page.getByRole('button',{name:'Retry contacts',exact:true}).waitFor();
  assert((await page.locator('#contact-results').innerText()).includes('Contacts are temporarily unavailable.'));
  await page.getByRole('button',{name:'Retry contacts',exact:true}).click();await waitQuery('Retry me');await page.evaluate(()=>resolveContacts('Retry me',[]));
  await page.getByRole('heading',{name:'No matching contacts',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Retry contacts',exact:true}).count(),0);
  assert.equal(await choices().count(),0);assert.equal(await recipient().inputValue(),'Retry me');assert(await start().isDisabled());

  phase='revoking permission removes cached contacts and ignores an older allowed response';
  await recipient().fill('Alex');await waitQuery('Alex');await page.evaluate(()=>resolveContacts('Alex',contactRows.slice(0,2)));await page.waitForFunction(()=>document.querySelectorAll('[data-action=choose-contact]').length===2);
  await recipient().fill('Late');await waitQuery('Late');
  await page.evaluate(async()=>{fixture.contactsAllowed=false;await onNativeResume();});
  await page.getByRole('button',{name:'Allow contacts',exact:true}).waitFor();assert.equal(await choices().count(),0);
  await page.evaluate(()=>resolveContacts('Late',contactRows));await settle();assert.equal(await choices().count(),0);assert(await page.getByRole('button',{name:'Allow contacts',exact:true}).isVisible());

  phase='navigation rejects late contact responses';
  await page.evaluate(async()=>{fixture.contactsAllowed=true;await onNativeResume();});await waitQuery('Late');
  await nav('Media').click();await page.getByRole('heading',{name:'A little more than words.',exact:true}).waitFor();
  await page.evaluate(()=>resolveContacts('Late',contactRows));await settle();
  assert.equal(await nav('Media').getAttribute('aria-current'),'page');assert.equal(await recipient().count(),0);

  phase='each number opens the intended blank conversation without sending';
  await reset();await page.evaluate(async()=>{fixture.contactsAllowed=true;await onNativeResume();});
  await page.locator('.row').click();await page.locator('#draft').fill('keep my original reply');await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  await openPicker();await waitQuery('');await page.evaluate(()=>resolveContacts('',contactRows.slice(0,2)));await page.waitForFunction(()=>document.querySelectorAll('[data-action=choose-contact]').length===2);
  const beforeBusySelection=await page.evaluate(()=>calls.filter(call=>call.action==='compose').length);
  await page.evaluate(()=>{busy=true;});await choices().filter({hasText:'+12025550112'}).click();
  assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='compose').length),beforeBusySelection,'Selecting while an operation is busy must not open another thread');
  assert(await recipient().isVisible());await page.evaluate(()=>{busy=false;});
  await page.evaluate(()=>{failCompose=true;});await choices().filter({hasText:'+12025550112'}).click();
  await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('could not be opened'));
  assert(await recipient().isEnabled());assert(await choices().filter({hasText:'+12025550112'}).isEnabled());
  assert.equal(await page.locator('#draft').count(),0);assert.equal(await recipient().inputValue(),'');await noSend();
  await page.evaluate(()=>{failCompose=false;});
  await choices().filter({hasText:'+12025550112'}).click();await page.locator('#draft').waitFor();
  assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='compose').at(-1).p.address),'+12025550112');
  assert.equal(await page.locator('.chat-person h2').innerText(),'Alex Rivera');assert.equal(await page.locator('#draft').inputValue(),'');await noSend();
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();
  assert.equal(await page.locator('#draft').inputValue(),'keep my original reply');
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await openPicker();await waitQuery('');await page.evaluate(()=>resolveContacts('',contactRows.slice(0,2)));await choices().filter({hasText:'+12025550111'}).waitFor();
  await page.setViewportSize({width:320,height:640});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert(await recipient().isVisible());assert(await pickerBack().isVisible());
  await choices().filter({hasText:'+12025550111'}).click();await page.locator('#draft').waitFor();
  assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='compose').at(-1).p.address),'+12025550111');await noSend();

  phase='a delayed conversation lookup cannot reopen the picker after navigation';
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await openPicker();await waitQuery('');
  await page.evaluate(()=>resolveContacts('',contactRows.slice(0,2)));await choices().filter({hasText:'+12025550112'}).waitFor();
  await page.evaluate(()=>{holdCompose=true;});await choices().filter({hasText:'+12025550112'}).click();
  await page.waitForFunction(()=>composeReplies.length===1);assert(await recipient().isDisabled());assert(await choices().nth(0).isDisabled());
  const pendingComposeCount=await page.evaluate(()=>calls.filter(call=>call.action==='compose').length);
  await choices().nth(0).evaluate(el=>el.click());assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='compose').length),pendingComposeCount);
  await nav('Settings').click();await page.getByRole('heading',{name:'Settings',exact:true}).waitFor();
  await page.evaluate(()=>{holdCompose=false;releaseCompose();});await settle();
  assert.equal(await nav('Settings').getAttribute('aria-current'),'page');assert.equal(await page.locator('#draft:visible,#recipient:visible').count(),0);await noSend();
  assert.deepEqual(errors,[]);
  console.log('PASS: optional/granted/denied/blocked/revoked contacts access; manual SMS numbers; delayed/debounced/stale searches; query focus/caret preservation; escaped labels; retry/empty states; late navigation/compose responses; busy selection guard; failed compose recovery; multi-number selection without sending; existing drafts retained; compact search and narrow phone layout. Fictional bridge only, no real contacts/SMS/AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
