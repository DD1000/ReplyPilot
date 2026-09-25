const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 412, height: 915 } });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('http://127.0.0.1:8769');
  await page.locator('#draft').waitFor();
  assert.equal(await page.evaluate(async()=>(await demoApi('conversation',{thread:1})).styleSamples),1);
  await page.getByRole('button', { name: 'Back to conversations' }).click();
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  const matching = page.getByRole('checkbox', { name: 'Match my recent texts', exact: true });
  assert(await matching.isChecked());
  await matching.uncheck();
  await page.getByRole('button', { name: 'Save preferences' }).click();
  await page.waitForFunction(async () => (await demoApi('snapshot')).matchMyStyle === false);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button', { name: 'Messages', exact: true }).click();
  await page.locator('.row').filter({hasText:'Maya Chen'}).click();
  await page.getByRole('button', { name: 'Redraft', exact: true }).click();
  await page.waitForFunction(()=>document.querySelector('#engine').textContent==='Demo draft · sample text');
  await page.getByRole('button', { name: 'Back to conversations' }).click();
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await matching.check();
  await page.getByRole('button', { name: 'Save preferences' }).click();
  await page.waitForFunction(async () => (await demoApi('snapshot')).matchMyStyle === true);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button', { name: 'Messages', exact: true }).click();
  await page.locator('.row').filter({hasText:'Maya Chen'}).click();

  // An unselected person must not trigger automatic drafting; manual refinement still works.
  await page.evaluate(async () => {
    await demoApi('quick', { thread: 1, base: 10, tone: 'Natural' });
    window.styleCalls = [];
    const original = window.demoApi;
    window.demoApi = async (action, p) => { window.styleCalls.push(action); return original(action, p); };
  });
  await page.getByRole('button', { name: 'Back to conversations' }).click();
  await page.locator('.row').filter({ hasText: 'Maya Chen' }).click();
  assert(!(await page.evaluate(() => window.styleCalls.includes('generate'))));
  await page.getByRole('button',{name:'Redraft',exact:true}).click();
  await page.waitForFunction(async () => (await demoApi('conversation', {thread: 1})).draft.engine === 'Demo · matching sample texts');
  assert(await page.evaluate(() => window.styleCalls.includes('generate')));
  await page.waitForFunction(() => !document.querySelector('#draft').disabled);
  await page.locator('#draft').fill('my own wording, keep this');
  await page.getByRole('button', { name: 'Back to conversations' }).click();
  await page.evaluate(() => { window.styleCalls = []; });
  await page.locator('.row').filter({ hasText: 'Maya Chen' }).click();
  assert.equal(await page.locator('#draft').inputValue(), 'my own wording, keep this');
  assert(!(await page.evaluate(() => window.styleCalls.includes('generate'))));
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  assert.deepEqual(errors, []);
  await browser.close();
  console.log('PASS: style matching defaults on, preferences persist, sample count, opt-in-only automatic drafting, manual refinement, user edits preserved, mobile layout.');
})().catch(e => { console.error(e); process.exit(1); });
