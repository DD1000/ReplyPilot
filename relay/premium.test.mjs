import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,validateInput} from './core.mjs';
const token='test-only-premium-token-12345678901234567890',authorization=`Bearer ${token}`;
const persona={writingStyle:'Casual.',relationship:'',context:'',avoid:'',examples:[],trainedMessages:300};
const request=(patch={})=>({requestId:crypto.randomUUID(),autopilot:true,automatic:true,automationReady:true,matchStyle:true,styleMode:'learned',engagement:'always_reply',history:[{speaker:'them',text:'lol what are you up to'}],persona,...patch});
const response=value=>new Response(JSON.stringify({status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:JSON.stringify(value)}]}]}));
const reply={decision:'reply',reason:'reply_needed',body:'nm just chilling lol',attentionNeeded:false,attentionReason:''};
const send=(relay,body)=>relay({method:'POST',path:'/draft',authorization,body});

test('a chat set to Astra uses the stronger model with a little reasoning and room for it',async()=>{
 const calls=[];const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{const b=JSON.parse(options.body);calls.push([b.model,b.reasoning.effort,b.max_output_tokens]);return response(reply);}});
 const result=await send(relay,request({premium:true}));
 assert.deepEqual(calls,[['gpt-6-astra','low',2000]]);assert.equal(result.body,reply.body);assert.equal(result.engine,'OpenAI · gpt-6-astra');
 await send(relay,request());
 assert.deepEqual(calls[1],['gpt-6-sol','none',420],'Other chats stay on the standard model');
});
test('without Astra access the same reply falls back to the standard model once',async()=>{
 for(const status of [400,403,404]){
  const calls=[];const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{const b=JSON.parse(options.body);calls.push([b.model,b.reasoning.effort]);return b.model==='gpt-6-astra'?new Response('{}',{status}):response(reply);}});
  const result=await send(relay,request({premium:true}));
  assert.deepEqual(calls,[['gpt-6-astra','low'],['gpt-6-sol','none']],String(status));assert.equal(result.body,reply.body);assert.equal(result.engine,'OpenAI · gpt-6-sol');
 }
 const busy=createRelay({token,apiKey:'test-key',fetchImpl:async()=>new Response('{}',{status:500})});
 assert.equal((await send(busy,request({premium:true}))).attentionReason,'model_unavailable','Server errors still use the safe fallback');
});
test('the choice is a strict boolean and never reaches the model',async()=>{
 for(const bad of ['true',1,{},null])assert.throws(()=>validateInput(request({premium:bad})),/reply model/);
 assert.equal(validateInput(request({premium:false})).premium,undefined);
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response(reply);}});
 await send(relay,request({premium:true}));assert.equal(JSON.parse(sent.input).premium,undefined);
});
