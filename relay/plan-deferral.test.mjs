import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,validateInput} from './core.mjs';
import {planDeferralFallback,planDeferralInstructions,repeatsEarlier} from './autopilot.mjs';
import {planReason} from './plan-safety.mjs';
const token='test-only-plan-deferral-token-12345678901234',authorization=`Bearer ${token}`;
const persona={writingStyle:'Lowercase, short, says "lol".',relationship:'Close friend.',context:'',avoid:'',examples:[],trainedMessages:200};
const history=[{speaker:'them',text:'what are we doing this weekend'},{speaker:'me',text:"I'll let you know in a bit."},{speaker:'them',text:'are you free saturday?'}];
const request=(patch={})=>({requestId:crypto.randomUUID(),autopilot:true,automatic:true,automationReady:true,matchStyle:true,styleMode:'learned',engagement:'always_reply',history:history.map(t=>({...t})),persona,...patch});
const response=value=>new Response(JSON.stringify({status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:JSON.stringify(value)}]}]}));
const reply=body=>({decision:'reply',reason:'reply_needed',body,attentionNeeded:false,attentionReason:''});
const send=(relay,body)=>relay({method:'POST',path:'/draft',authorization,body});

test('without planDeferral, plan pushes keep the old fixed deferral and never call OpenAI',async()=>{
 const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{throw Error('must not call');}});
 const result=await send(relay,request());
 assert.equal(result.body,'Let me get back to you on that.');assert.equal(result.attentionReason,'plans');
});
test('with planDeferral the model writes the deferral in the owner voice and attention is forced to plans',async()=>{
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response(reply('lol idk yet, gimme a bit'));}});
 const first=await send(relay,request({planDeferral:{count:0}}));
 assert.equal(first.body,'lol idk yet, gimme a bit');assert.equal(first.attentionNeeded,true);assert.equal(first.attentionReason,'plans');
 assert.ok(sent.instructions.includes('Plan deferral:'));assert.ok(sent.instructions.includes('persona.writingStyle'));assert.ok(!sent.instructions.includes('already put this off once'));
 const input=JSON.parse(sent.input);assert.equal(input.planDeferral,undefined);assert.equal(input.locationContext,undefined);assert.deepEqual(input.persona.writingStyle,persona.writingStyle);
 await send(relay,request({planDeferral:{count:1}}));
 assert.ok(sent.instructions.includes('already put this off once'));
});
test('commitments, repeats, whereabouts and bad output become a varied safe fallback',async()=>{
 for(const [count,body] of [[0,"I'm free saturday"],[0,"I'll let you know in a bit."],[1,"i'm at work rn"],[1,'x'.repeat(361)],[0,"sounds good, see you at 7"]]){
  const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>response(reply(body))});
  const result=await send(relay,request({planDeferral:{count}}));
  assert.equal(result.attentionReason,'plans',body);assert.equal(result.attentionNeeded,true);
  assert.equal(result.body,planDeferralFallback(count,history.filter(t=>t.speaker==='me').map(t=>t.text)).body,body);
 }
 const broken=createRelay({token,apiKey:'test-key',fetchImpl:async()=>new Response('{}',{status:500})});
 const failed=await send(broken,request({planDeferral:{count:1}}));
 assert.equal(failed.body,'Still figuring it out, give me a little bit.');assert.equal(failed.attentionReason,'plans');
});
test('fallbacks differ by count, skip anything already sent and are never commitments',()=>{
 const first=planDeferralFallback(0).body,second=planDeferralFallback(1).body;
 assert.notEqual(first,second);assert.notEqual(first,"I'll let you know in a bit.");
 assert.notEqual(planDeferralFallback(0,[first]).body,first);assert.notEqual(planDeferralFallback(1,[second]).body,second);
 for(const count of [0,1])for(let skip=0;skip<4;skip++){
  const earlier=[];let body;for(let i=0;i<=skip;i++){body=planDeferralFallback(count,earlier).body;earlier.push(body);}
  assert.equal(planReason([],body),null,body);
 }
 assert.ok(repeatsEarlier('Not sure yet, let me get back to you!',['not sure yet let me get back to you']));
 assert.ok(planDeferralInstructions(0).includes('Never repeat'));
});
test('planDeferral is strictly validated',()=>{
 for(const value of [{count:2},{count:-1},{count:'0'},{count:0,extra:1},[],null,'0'])assert.throws(()=>validateInput(request({planDeferral:value})),/plan deferral/,JSON.stringify(value));
 assert.deepEqual(validateInput(request({planDeferral:{count:1}})).planDeferral,{count:1});
});
test('planDeferral does nothing for messages that are not about plans',async()=>{
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response(reply('haha yeah'));}});
 const result=await send(relay,request({planDeferral:{count:0},history:[{speaker:'them',text:'lol that was so funny'}]}));
 assert.equal(result.body,'haha yeah');assert.equal(result.attentionNeeded,false);assert.ok(!sent.instructions.includes('Plan deferral:'));
});
test('an untrained chat still cannot use Autopilot for plans',async()=>{
 const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{throw Error('must not call');}});
 const result=await send(relay,request({planDeferral:{count:0},automationReady:false,persona:undefined}));
 assert.equal(result.decision,'no_reply');assert.equal(result.reason,'insufficient_history');
});
