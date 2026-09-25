import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
const token='http-test-token-not-a-real-secret-1234567890';
test('HTTP boundary authenticates, rejects browser traffic and bounds input without logging bodies',async()=>{
 const server=spawn(process.execPath,['server.mjs'],{cwd:new URL('.',import.meta.url),env:{PATH:process.env.PATH,PORT:'0',HOST:'127.0.0.1',REPLY_PILOT_TOKEN:token,OPENAI_API_KEY:''},stdio:['ignore','pipe','pipe']});
 let logs='';server.stdout.on('data',x=>logs+=x);server.stderr.on('data',x=>logs+=x);
 try{
  const base=await new Promise((resolve,reject)=>{let timer=setTimeout(()=>reject(new Error('Server start timeout')),5000);server.stdout.on('data',()=>{const m=logs.match(/port (\d+)/);if(m){clearTimeout(timer);resolve(`http://127.0.0.1:${m[1]}`);}});server.once('exit',()=>{clearTimeout(timer);reject(new Error('Server failed to start'));});});
  assert.equal((await fetch(base+'/status')).status,200);
  assert.equal((await fetch(base+'/health')).status,401);
  const health=await fetch(base+'/health',{headers:{Authorization:`Bearer ${token}`}});assert.equal(health.status,200);assert.equal(health.headers.get('cache-control'),'no-store');const info=await health.json();assert.equal(info.ready,false);assert.equal(info.contextLimit,50);assert.equal(info.contextVersion,2);assert.equal(info.replyDecisionVersion,1);assert.equal(info.replySafetyVersion,1);assert.equal(info.planSafetyVersion,1);assert.equal(info.personalizationVersion,1);assert.equal(info.locationVersion,1);assert.equal(info.girlfriendModeVersion,1);assert.equal(info.attentionActionsVersion,1);assert.equal(info.contactHumorVersion,1);assert.equal(info.mediaAnalysisVersion,1);assert.equal(info.approvedLearningVersion,1);assert.equal(info.pilotTrainingVersion,1);assert.equal(info.messageMeaningsVersion,1);
  assert.equal((await fetch(base+'/health',{headers:{Authorization:`Bearer ${token}`,Origin:'https://malicious.invalid'}})).status,403);
  assert.equal((await fetch(base+'/draft',{method:'POST',headers:{Authorization:`Bearer ${token}`},body:'sensitive-message'})).status,415);
  assert.equal((await fetch(base+'/draft',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'{broken'})).status,400);
  assert.equal((await fetch(base+'/draft',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'x'.repeat(262145)})).status,413);
  assert.equal((await fetch(base+'/media-analysis',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).status,401);
  assert.equal((await fetch(base+'/media-analysis',{method:'POST',headers:{Authorization:`Bearer ${token}`,Origin:'https://malicious.invalid','Content-Type':'application/json'},body:'{}'})).status,403);
  assert.equal((await fetch(base+'/media-analysis',{method:'POST',headers:{Authorization:`Bearer ${token}`},body:'private-media-caption'})).status,415);
  assert.equal((await fetch(base+'/media-analysis',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'{broken'})).status,400);
  // Media has its own 1-MiB HTTP envelope; valid JSON above the draft limit
  // reaches the missing-key check without any model call.
  assert.equal((await fetch(base+'/media-analysis',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({padding:'x'.repeat(262145)})})).status,503);
  assert.equal((await fetch(base+'/media-analysis',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'x'.repeat(1048577)})).status,413);
  assert.equal((await fetch(base+'/train',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).status,401);
  assert.equal((await fetch(base+'/train',{method:'POST',headers:{Authorization:`Bearer ${token}`,Origin:'https://malicious.invalid','Content-Type':'application/json'},body:'{}'})).status,403);
  assert.equal((await fetch(base+'/train',{method:'POST',headers:{Authorization:`Bearer ${token}`},body:'private-practice-message'})).status,415);
  assert.equal((await fetch(base+'/train',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'{broken'})).status,400);
  assert.equal((await fetch(base+'/train',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({history:[]})})).status,503);
  assert.equal((await fetch(base+'/train',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'x'.repeat(262145)})).status,413);
  assert(!logs.includes('private-practice-message'));
  assert.equal((await fetch(base+'/send',{method:'POST',headers:{Authorization:`Bearer ${token}`}})).status,404);
  assert(!logs.includes(token));assert(!logs.includes('sensitive-message'));assert(!logs.includes('private-media-caption'));
 }finally{server.kill();}
});
