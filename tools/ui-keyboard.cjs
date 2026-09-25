const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  // Synthetic viewport timing only. There is no Android keyboard, SMS or AI call.
  await page.addInitScript(()=>{
   const viewport=new EventTarget();Object.assign(viewport,{height:915,offsetTop:0});
   Object.defineProperty(window,'visualViewport',{configurable:true,value:viewport});
   window.simulateVisualViewport=(height,offsetTop=0)=>{
    Object.assign(viewport,{height,offsetTop});viewport.dispatchEvent(new Event('resize'));
   };
  });
  await page.goto((process.env.REPLY_PILOT_URL||'http://127.0.0.1:8769'));await page.locator('#draft').waitFor();
  const reply='what time were you thinking?';
  await page.locator('#draft').fill(reply);
  await page.evaluate(()=>{window.originalEditor=document.querySelector('#draft');originalEditor.setSelectionRange(8,8);});
  async function fits(height,top=0){
   await page.waitForFunction(({height,top})=>{
    const style=document.documentElement.style;
    return style.getPropertyValue('--viewport-height')===`${height}px`&&style.getPropertyValue('--viewport-top')===`${top}px`;
   },{height,top});
   await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   for(const selector of ['.chat-header','.composer','#draft','#accept']){
    const bounds=await page.locator(selector).boundingBox();
    assert(bounds.y>=top-1&&bounds.y+bounds.height<=top+height+1,`${selector} must stay above the simulated keyboard: ${JSON.stringify(bounds)}`);
   }
   assert(await page.evaluate(()=>document.activeElement===originalEditor&&document.querySelector('#draft')===originalEditor),'Keyboard transitions must retain the focused editor');
   assert.equal(await page.locator('#draft').inputValue(),reply);
   assert.equal(await page.locator('#draft').evaluate(el=>el.selectionStart),8);
  }
  await fits(915);
  // Native view shrinks before visualViewport reports its new bounds.
  await page.setViewportSize({width:412,height:560});await fits(560);
  await page.screenshot({path:'dist/keyboard-safe-preview.png'});
  // A stale scroll offset must not move the whole chat below the resized view.
  await page.evaluate(()=>simulateVisualViewport(915,250));
  await page.setViewportSize({width:412,height:430});await fits(430);
  await page.evaluate(()=>simulateVisualViewport(430,0));await fits(430);
  // Older layout viewport stays full-size while only the visual viewport shrinks.
  await page.setViewportSize({width:412,height:915});await fits(430);
  await page.evaluate(()=>simulateVisualViewport(430,90));await fits(430,90);
  // Keyboard closes, then opens again: no retained blank keyboard gap.
  await page.evaluate(()=>simulateVisualViewport(915,0));await fits(915);
  await page.setViewportSize({width:320,height:320});await fits(320);
  await page.locator('#draft').fill('A longer draft with multiple lines. '.repeat(20));
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const input=await page.locator('#draft').boundingBox();
  assert(input.y>=0&&input.y+input.height<=320,'A long draft must scroll inside the editor above the keyboard');
  assert(await page.evaluate(()=>document.querySelector('#draft').scrollHeight>document.querySelector('#draft').clientHeight));
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  assert.deepEqual(errors,[]);
  console.log('PASS: stale visual height, stale offset, native/visual-only resize, repeated keyboard show/hide, narrow long drafts, editor identity, focus and cursor. Synthetic viewport transitions, not an Android device test.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
