import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,validateInput} from './core.mjs';
import {validateAutopilotResult} from './autopilot.mjs';
const token='test-only-autopilot-token-1234567890123456',authorization=`Bearer ${token}`;
const normal={decision:'reply',reason:'reply_needed',body:'haha yeah',attentionNeeded:false,attentionReason:''};
const request=(patch={})=>({requestId:'f9a80ac9-040f-43ad-998f-a049e99d9e01',autopilot:true,automatic:true,automationReady:true,matchStyle:true,history:[{speaker:'me',text:'haha same'},{speaker:'them',text:'okay'}],historyMemory:{writingStyle:'Short informal replies.',historicalContext:'Historically discussed games.'},...patch});
const response=value=>new Response(JSON.stringify({status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:JSON.stringify(value)}]}]}));
const send=(relay,body=request(),path='/draft')=>relay({method:'POST',path,authorization,body});
test('new Autopilot responds to acknowledgments with separate instructions and bounded private memory',async()=>{
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response(normal);}});
 const result=await send(relay,request({pilotTraining:[{incoming:'hi',reply:'yo'}],engagement:'girlfriend'}));
 assert.equal(result.body,'haha yeah');assert.equal(result.attentionNeeded,false);assert(sent.instructions.startsWith('Write one short personal text'));assert(!sent.instructions.includes('Silence hands this conversation'));assert.equal(sent.store,false);assert.equal(sent.tools,undefined);
 const input=JSON.parse(sent.input);for(const key of ['tone','engagement','humorLevel','insideJokes','pilotTraining','messageMeanings'])assert.equal(input[key],undefined);assert.deepEqual(input.historyMemory,request().historyMemory);
});
test('plans, absent location and assistant work receive noncommittal replies with attention',async()=>{
 let calls=0;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{calls++;return response(normal);}});
 for(const [text,reason] of [['Want to meet tomorrow at 7?','plans'],['Where are you?','personal_info'],['Explain how to code in Java','uncertain']]){
  const result=await send(relay,request({requestId:crypto.randomUUID(),history:[{speaker:'them',text}]}));assert.equal(result.decision,'reply');assert.equal(result.body,'Let me get back to you on that.');assert.equal(result.attentionNeeded,true);assert.equal(result.attentionReason,reason);
 }assert.equal(calls,0);
});
test('insufficient history remains a sending gate and does not call OpenAI',async()=>{
 const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{throw Error('must not call');}});const r=await send(relay,request({automationReady:false}));assert.equal(r.decision,'no_reply');assert.equal(r.reason,'insufficient_history');
});
test('model commitments and malformed output fall back instead of scheduling plans or showing approval holds',async()=>{
 for(const output of [{...normal,body:"I'll be there at 7."},{...normal,body:'x'.repeat(361)},{decision:'no_reply',reason:'needs_review',body:''}]){
  const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>response(output)});const r=await send(relay);assert.equal(r.decision,'reply');assert.equal(r.body,'Let me get back to you on that.');assert.equal(r.attentionNeeded,true);
 }
});
test('provider failure yields a marked safe deferral without leaking provider errors',async()=>{
 const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>new Response('PRIVATE_PROVIDER_ERROR',{status:500})});const r=await send(relay);assert.equal(r.attentionReason,'model_unavailable');assert(!JSON.stringify(r).includes('PRIVATE_PROVIDER_ERROR'));
});
test('Autopilot does not bypass authentication, request identity, quota or malformed input',async()=>{
 const relay=createRelay({token,apiKey:'test-key',maxDaily:1,fetchImpl:async()=>response(normal)});
 await assert.rejects(relay({method:'POST',path:'/draft',body:request()}),e=>e.status===401);
 assert.throws(()=>validateInput(request({autopilot:'true'})));assert.throws(()=>validateInput(request({historyMemory:{writingStyle:'x'.repeat(1801),historicalContext:''}})));
 await send(relay);await assert.rejects(send(relay,request({samples:'different'})),e=>e.status===409);await assert.rejects(send(relay,request({requestId:crypto.randomUUID()})),e=>e.status===429);
});
test('strict attention result requires consistent bool/reason and preserves short safe replies',()=>{
 assert.deepEqual(validateAutopilotResult(normal),normal);for(const value of [{...normal,attentionNeeded:true},{...normal,attentionReason:'plans'},{...normal,body:'```java'}])assert.throws(()=>validateAutopilotResult(value));
});
const batch=()=>({requestId:crypto.randomUUID(),previous:{writingStyle:'',historicalContext:''},history:[{speaker:'them',text:'Want to meet tomorrow?',continuation:false},{speaker:'me',text:'let me check',continuation:false}],finalBatch:true});
test('full-history endpoint analyzes plans as data, returns memory, caches and never generates a reply',async()=>{
 let calls=0,sent;const memory={writingStyle:'Brief lowercase wording.',historicalContext:'Past planning discussions; not current availability.'};
 const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{calls++;sent=JSON.parse(options.body);return response({memory});}}),b=batch();
 assert.deepEqual(await send(relay,b,'/history-analysis'),{memory});assert.deepEqual(await send(relay,b,'/history-analysis'),{memory});assert.equal(calls,1);assert.equal(sent.store,false);assert(sent.instructions.includes('not permission to send anything'));assert.equal(sent.text.format.name,'history_memory');assert.deepEqual(JSON.parse(sent.input).history,b.history);assert.equal(JSON.parse(sent.input).requestId,undefined);
 const health=await relay({method:'GET',path:'/health',authorization});assert.equal(health.autopilotVersion,1);assert.equal(health.historyAnalysisVersion,1);
});
test('failed history batch can retry same ID while completed batches stay idempotent',async()=>{
 let calls=0;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{if(++calls===1)return response({memory:null});return response({memory:{writingStyle:'',historicalContext:''}});}}),b=batch();
 await assert.rejects(send(relay,b,'/history-analysis'),e=>e.status===502);assert.deepEqual(await send(relay,b,'/history-analysis'),{memory:{writingStyle:'',historicalContext:''}});assert.equal(calls,2);
});
test('full-history route rejects extra identity and oversized batch before provider access',async()=>{
 let calls=0;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{calls++;return response({});}});
 for(const b of [{...batch(),phone:'+12025550100'},{...batch(),history:[{speaker:'me',text:'x'.repeat(4001),continuation:false}]}])await assert.rejects(send(relay,b,'/history-analysis'),e=>e.status===400);assert.equal(calls,0);
});
