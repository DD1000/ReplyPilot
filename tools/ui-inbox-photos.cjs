const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let page,phase='setup';
 try{
  page=await browser.newPage({viewport:{width:412,height:915}});
  const errors=[],photoRequests=[];page.on('pageerror',error=>errors.push(error.message));
  // Deliberately illustrated, fictional contact thumbnails. Nothing here reads
  // Android contacts or fetches anyone's actual profile picture.
  await page.route('**/contact-photo/**',async route=>{
   const url=route.request().url();photoRequests.push(url);
   if(url.endsWith('12025550103')){await route.fulfill({status:404,body:''});return;}
   const id=Number(url.slice(-2)),colors=['#619887','#7085a8','#ab798e','#a98d68'],color=colors[id%colors.length];
   const portrait=`<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128"><rect width="128" height="128" fill="${color}"/><circle cx="64" cy="49" r="26" fill="#ecd1b7"/><path d="M19 128v-18a45 45 0 0190 0v18" fill="#27364b"/><path d="M37 48v-9a27 27 0 0154 0v12l-13-18-29 4z" fill="#392e37"/></svg>`;
   await route.fulfill({contentType:'image/svg+xml',headers:{'Cache-Control':'no-store'},body:portrait});
  });
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.snapshots=[];window.archiveReads=[];
   const names=['Maya Chen','Jordan Reed','Niko Lopez','Riley Taylor','Alex Morgan','Sam Parker'];
   window.photoInbox=Array.from({length:20},(_,i)=>({thread_id:i+1,_id:1001+i,kind:'sms',type:i%2?2:1,read:1,
    name:names[i]||`Saved friend ${i+1}`,address:`+1202555${String(101+i).padStart(4,'0')}`,
    photo:`/contact-photo/%2B1202555${String(101+i).padStart(4,'0')}`,pinned:i===0,
    body:['See you soon!','That sounds good','Thanks for the update','I found the photos','Talk later','Absolutely'][i%6],date:Date.now()-i*60000}));
   const access={readSms:true,defaultSms:true,contacts:true};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});
    const finish=value=>nativeResult(id,clone(value),null);
    if(action==='snapshot'){snapshots.push(finish);return;}
    if(action==='cacheInbox'){archiveReads.push(finish);return;}
    if(action==='launchInbox'){setTimeout(()=>finish({inbox:photoInbox,savedAt:Date.now()-5000,revision:4,access}),0);return;}
    if(action==='launchHistories'){setTimeout(()=>finish({conversations:[],access}),0);return;}
    setTimeout(()=>finish({}),0);
   }};
  });
  const row=id=>page.locator(`.row[data-thread="${id}"]`);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const loaded=async id=>page.waitForFunction(id=>{const image=document.querySelector(`.row[data-thread="${id}"] .contact-avatar-photo`);return image?.complete&&image.naturalWidth>0;},id);
  const loadingPolicy=async()=>{
   const attributes=await page.locator('.row .avatar').evaluateAll(avatars=>avatars.map(avatar=>({stored:avatar.dataset.photoLoading,image:avatar.querySelector('img')?.loading})));
   assert.equal(attributes.length,20);
   attributes.forEach((value,index)=>{const expected=index<12?'eager':'lazy';assert.equal(value.stored,expected,`Row ${index+1} retry policy`);if(value.image)assert.equal(value.image,expected,`Row ${index+1} image policy`);});
  };
  phase='launch summaries show pinned and unpinned photos before chat, archive, or live reads';
  await page.goto(baseURL);await page.waitForFunction(()=>document.querySelectorAll('.row').length===20&&snapshots.length===1&&archiveReads.length===1);
  await loaded(1);await loaded(2);
  assert.equal(await row(1).getAttribute('data-pinned'),'true');assert.equal(await row(2).getAttribute('data-pinned'),'false');
  assert.deepEqual(await page.locator('.conversation-group').allTextContents(),['Pinned','All messages']);
  assert.equal(await page.locator('.chat-header').count(),0);
  assert.equal(await page.evaluate(()=>calls.some(call=>call.action==='conversation')),false);
  await loadingPolicy();

  phase='first twelve photos load eagerly; a missing photo retains initials';
  for(let id=1;id<=12;id++)if(id!==3)await loaded(id);
  await page.waitForFunction(()=>!document.querySelector('.row[data-thread="3"] .contact-avatar-photo'));
  assert.equal(await row(3).locator('.avatar-initials').innerText(),'NL');
  assert(photoRequests.some(url=>url.endsWith('12025550103')));
  assert(photoRequests.every(url=>new URL(url).origin===new URL(baseURL).origin&&new URL(url).pathname.startsWith('/contact-photo/')));

  phase='contact-photo invalidation preserves each row loading policy';
  await page.evaluate(()=>onContactPhotosChanged());await loaded(1);await loaded(2);await loadingPolicy();
  await page.waitForFunction(()=>!document.querySelector('.row[data-thread="3"] .contact-avatar-photo'));

  phase='searching and restoring the list keeps the contact photos';
  await page.getByRole('textbox',{name:'Find a conversation'}).fill('Maya');
  assert.equal(await page.locator('.row').count(),1);await loaded(1);
  await page.getByRole('textbox',{name:'Find a conversation'}).fill('');
  assert.equal(await page.locator('.row').count(),20);await loaded(1);await loaded(2);await loadingPolicy();
  await page.getByRole('textbox',{name:'Find a conversation'}).blur();await settle();
  await page.screenshot({path:'dist/inbox-contact-photos-412x915.png'});
  await page.setViewportSize({width:320,height:470});await settle();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:'dist/inbox-contact-photos-320x470.png'});

  phase='revoking contacts removes all photos and prevents subsequent reloads';
  await page.waitForLoadState('networkidle');
  await page.evaluate(()=>onMessageAccessChanged({readSms:true,defaultSms:true,contacts:false}));
  assert.equal(await page.locator('.contact-avatar-photo').count(),0);
  assert.equal(await page.locator('[data-contact-photo]').count(),0);
  const requestsAfterRevocation=photoRequests.length;
  await page.evaluate(()=>onContactPhotosChanged());
  await page.getByRole('textbox',{name:'Find a conversation'}).fill('12025550101');
  await page.getByRole('textbox',{name:'Find a conversation'}).fill('');
  await settle();await page.waitForTimeout(120);
  assert.equal(photoRequests.length,requestsAfterRevocation);
  assert.equal(await page.locator('.contact-avatar-photo').count(),0);
  assert.equal(await row(1).locator('.row-title span').innerText(),'+12025550101');
  const sideEffects=await page.evaluate(()=>calls.filter(call=>['conversation','generate','suggestReply','quick','analyzeMedia','trainPilotStart','trainPilotReply','sendNow','sendMms','approve','saveDraft'].includes(call.action)).map(call=>call.action));
  assert.deepEqual(sideEffects,[]);assert.deepEqual(errors,[]);
  assert.equal(await page.evaluate(()=>snapshots.length),1);assert.equal(await page.evaluate(()=>archiveReads.length),1);
  console.log('PASS: launch-only pinned/unpinned inbox photos before chat or live/archive reads; first 12 eager and remaining lazy; invalidation preserves loading policy; missing-image initials; search rerender; compact layout; permission revocation blocks later photo requests; no messaging or AI actions. Fictional bridge and thumbnails only.');
 }catch(error){if(page)await page.screenshot({path:'dist/inbox-contact-photos-failure.png'});throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
