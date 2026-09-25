import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,extractReply,validateInput} from './core.mjs';

const token='decision-test-only-token-012345678901234567890';
const authorization=`Bearer ${token}`;
const base={requestId:'decision-test-request-0001',automationReady:true,history:[{speaker:'me',text:'I will check and get back to you.'},{speaker:'them',text:'Can you check the other file too?'}],matchStyle:true,style:[]};
const reply={decision:'reply',reason:'reply_needed',body:'Which file did you mean?'};
const noReply={decision:'no_reply',reason:'conversation_complete',body:''};
function output(value=reply){return{status:'completed',output:[{type:'message',role:'assistant',status:'completed',content:[{type:'output_text',text:JSON.stringify(value)}]}]};}
function send(relay,body=base){return relay({method:'POST',path:'/draft',authorization,body});}
function fixture(value=reply,options={}){
 const calls=[];
 const relay=createRelay({apiKey:'test-key',token,...options,fetchImpl:async(url,opts)=>{calls.push({url,...JSON.parse(opts.body)});return new Response(JSON.stringify(output(value)));}});
 return{relay,calls};
}
function ending(text,extra={}){return{...base,history:[base.history[0],{speaker:'them',text}],...extra};}

test('legacy omitted automatic defaults to protected mode, but manual/test stays explicit',()=>{
 assert.equal(validateInput(base).automatic,true);
 assert.equal(validateInput({...base,automatic:true}).automatic,true);
 assert.equal(validateInput({...base,automatic:false}).automatic,false);
 for(const automatic of [null,'false',0,1,{},[]])assert.throws(()=>validateInput({...base,automatic}),e=>e.status===400);
});

test('whole-message closing acknowledgments stop before any provider call for ready conversations',async()=>{
 for(const text of ['Ok','okay!','kk','Thanks.','Thank you!','Thanks again','Got it','sounds good','No problem','You’re welcome','👍','👍🏽','👌','🙏','🙌','Thanks! 👍','OK!!!','ＯＫ']){
  const {relay,calls}=fixture();
  const result=await send(relay,ending(text));
  assert.deepEqual({decision:result.decision,reason:result.reason,body:result.body},noReply,text);
  assert.equal(calls.length,0,text);
  assert.equal(typeof result.engine,'string');assert(result.elapsedMs>=0);
 }
});

test('questions, meaningful clauses, first messages, and explicit manual drafts reach the provider',async()=>{
 const cases=[
  ending('Ok but where?'),ending('Thanks, can you clarify that?'),ending('Ok?'),ending('Ok？'),
  ending('Thanks — the address changed'),ending('👍 Where should I go?'),ending('No problem with the file, but the date is wrong'),
  ending('I need help'),ending('okay',{automatic:false}),
  {...base,history:[{speaker:'them',text:'Thanks'}]},
  {...base,history:[{speaker:'them',text:'Hello'},{speaker:'them',text:'Ok'}]},
  {...base,history:[base.history[0],{speaker:'them',text:'Please send the address'},{speaker:'them',text:'Thanks'}]}
 ];
 for(const request of cases){const {relay,calls}=fixture();assert.equal((await send(relay,request)).decision,'reply');assert.equal(calls.length,1);}
 const {relay,calls}=fixture();await send(relay,ending('Thanks',{automatic:false}));assert.equal(calls[0].input&&JSON.parse(calls[0].input).automatic,false);
});

test('strict Responses schema and decision instructions are sent without changing model or security settings',async()=>{
 const {relay,calls}=fixture(reply,{model:'configured-model'});await send(relay);
 const request=calls[0],format=request.text.format;
 assert.equal(format.type,'json_schema');assert.equal(format.strict,true);assert.equal(format.name,'reply_decision');
 assert.equal(format.schema.type,'object');assert.equal(format.schema.additionalProperties,false);
 assert.deepEqual(format.schema.required,['decision','reason','body']);
 assert.deepEqual(Object.keys(format.schema.properties),['decision','reason','body']);
 assert.deepEqual(format.schema.properties.decision.enum,['reply','no_reply']);
 assert.deepEqual(format.schema.properties.reason.enum,['reply_needed','conversation_complete','repeated_reply','needs_review','insufficient_history','plans_need_input']);
 assert.equal(format.schema.properties.body.type,'string');
 assert.equal(request.model,'configured-model');assert.equal(request.store,false);assert.equal(request.tools,undefined);
 assert.equal(request.max_output_tokens,320);assert.deepEqual(request.reasoning,{effort:'none'});
 assert.equal(JSON.parse(request.input).automatic,true);
 for(const text of ['silence is a valid','repeat or rephrase','no new useful reply','Never invent', 'automatic is false'])assert(request.instructions.includes(text),text);
});

test('validated model no-reply stays empty and keeps reason metadata for the phone',async()=>{
 for(const reason of ['conversation_complete','repeated_reply']){
  const {relay,calls}=fixture({...noReply,reason});const result=await send(relay);
  assert.equal(calls.length,1);assert.equal(result.decision,'no_reply');assert.equal(result.reason,reason);assert.equal(result.body,'');
 }
});

test('automatic repeated owner replies are suppressed after normalization, not copied back into the loop',async()=>{
 for(const body of ['I will check and get back to you.','  I WILL check and get back to you!!!  ','I will check\nand get back to you']){
  const {relay}=fixture({...reply,body});const result=await send(relay);
  assert.deepEqual({decision:result.decision,reason:result.reason,body:result.body},{decision:'no_reply',reason:'repeated_reply',body:''});
 }
 const {relay}=fixture({...reply,body:"I'll check the other file later"});
 const result=await send(relay,{...base,history:[{speaker:'me',text:'I’ll check the other file later.'},...base.history]});assert.equal(result.reason,'repeated_reply');
});

test('manual repeated drafts are allowed, and other-person text or meaningful differences are not duplicate owners',async()=>{
 const repeated='The blue folder is the one I meant.';const {relay}=fixture({...reply,body:repeated});assert.equal((await send(relay,{...base,history:[{speaker:'me',text:repeated},base.history[1]],automatic:false})).decision,'reply');
 for(const history of [
  [{speaker:'them',text:reply.body}],
  [{speaker:'me',text:'Which other file did you mean?'},base.history[1]],
  [{speaker:'me',text:'15'},base.history[1]]
 ]){
  const body=history[0].text==='15'?'1.5':reply.body;
  const {relay}=fixture({...reply,body});assert.equal((await send(relay,{...base,history})).decision,'reply');
 }
 for(const body of ['Yes','No','Not really','Thanks so much']){
  const {relay}=fixture({...reply,body});
  const history=[{speaker:'me',text:body},{speaker:'them',text:'Did you like the ending?'}];
  assert.equal((await send(relay,{...base,history})).decision,'reply',body);
 }
});

test('no-reply cache preserves request identity, manual mode cannot reuse an automatic request ID',async()=>{
 const {relay,calls}=fixture();const request=ending('Thanks');
 const [a,b]=await Promise.all([send(relay,request),send(relay,request)]);assert.deepEqual(a,b);assert.equal(calls.length,0);
 await assert.rejects(send(relay,{...request,automatic:false}),e=>e.status===409);
 await assert.rejects(send(relay,ending('Thanks, where?')),e=>e.status===409);
 const manual=await send(relay,{...request,requestId:base.requestId+'manual',automatic:false});assert.equal(manual.decision,'reply');assert.equal(calls.length,1);
 const model=fixture(noReply);assert.deepEqual(await send(model.relay),await send(model.relay));assert.equal(model.calls.length,1);
});

test('closure shortcut retains authenticated routes, input validation and configured request limits',async()=>{
 let stamp=Date.parse('2026-09-23T12:00:00Z');
 const {relay,calls}=fixture(reply,{maxDaily:1,now:()=>stamp});
 await assert.rejects(relay({method:'POST',path:'/draft',authorization:'Bearer wrong',body:ending('Thanks')}),e=>e.status===401);
 await assert.rejects(send(relay,ending('Thanks',{requestId:'bad'})),e=>e.status===400);
 await send(relay,ending('Thanks'));
 await assert.rejects(send(relay,ending('Ok',{requestId:base.requestId+'another'})),e=>e.status===429);
 stamp+=86400000;await send(relay,ending('Ok',{requestId:base.requestId+'another'}));assert.equal(calls.length,0);
});

test('malformed and contradictory decision objects never become sendable bodies',()=>{
 const invalid=[null,[],{},'plain reply', {...reply,extra:'unexpected'}, {...reply,decision:'send'},
  {...reply,decision:true}, {...reply,reason:'conversation_complete'}, {...reply,reason:'repeated_reply'},
  {...reply,reason:undefined}, {...reply,body:''}, {...reply,body:' \n '}, {...reply,body:null}, {...reply,body:42},
  {...noReply,body:'do send this'}, {...noReply,body:' '}, {...noReply,reason:'reply_needed'},
  {...noReply,reason:'unknown'}, {...noReply,body:undefined}];
 for(const value of invalid)assert.throws(()=>extractReply(output(value)),e=>e.status===502,JSON.stringify(value));
 for(const text of ['not json','```json\n'+JSON.stringify(reply)+'\n```',JSON.stringify(reply)+'\n'+JSON.stringify(noReply),'x'.repeat(12001)]){
  const value=output();value.output[0].content[0].text=text;assert.throws(()=>extractReply(value),e=>e.status===502);
 }
});

test('refused, incomplete, missing or multiple assistant payloads fail closed',()=>{
 for(const status of ['incomplete','in_progress','failed','cancelled',undefined])assert.throws(()=>extractReply({...output(),status}),e=>e.status===502);
 const refused=output();refused.output[0].content.push({type:'refusal',refusal:'No.'});assert.throws(()=>extractReply(refused),e=>e.status===422);
 const partial=output();partial.output[0].status='incomplete';assert.throws(()=>extractReply(partial),e=>e.status===502);
 for(const value of [null,{}, {status:'completed',output:null},{status:'completed',output:[]},
  {...output(),output:[...output().output,...output().output]},
  {status:'completed',output:[{type:'message',role:'assistant',content:null}]},
  {status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:42}]}]}
 ])assert.throws(()=>extractReply(value),e=>e.status===502);
});

test('invalid model decisions and refusal errors remain cached so repeats cannot spend or send',async()=>{
 for(const response of [output({...noReply,body:'invalid'}),{...output(),status:'incomplete'},
  {status:'completed',output:[{type:'message',role:'assistant',content:[{type:'refusal',refusal:'No'}]}]}]){
  let calls=0;const relay=createRelay({apiKey:'key',token,fetchImpl:async()=>{calls++;return new Response(JSON.stringify(response));}});
  for(let i=0;i<2;i++)await assert.rejects(send(relay),e=>[422,502].includes(e.status));assert.equal(calls,1);
 }
});
