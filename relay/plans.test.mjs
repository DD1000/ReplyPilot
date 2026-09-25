import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,extractReply,validateInput} from './core.mjs';
import {incomingPlan,commitment,planReason,unansweredTexts} from './plan-safety.mjs';

const token='plans-test-only-token-01234567890123456789';
const authorization=`Bearer ${token}`;
const base={requestId:'plans-test-request-0001',automatic:true,automationReady:true,matchStyle:true,
 history:[{speaker:'me',text:'How is the book?'},{speaker:'them',text:'The ending made me laugh.'}]};
const safe={decision:'reply',reason:'reply_needed',body:'Which part made you laugh?'};
const hold={decision:'no_reply',reason:'plans_need_input',body:''};
const output=value=>({status:'completed',output:[{type:'message',role:'assistant',status:'completed',content:[{type:'output_text',text:JSON.stringify(value)}]}]});
function fixture(value=safe){
 const calls=[];const relay=createRelay({apiKey:'test-key',token,fetchImpl:async(url,options)=>{
  calls.push({url,options,body:JSON.parse(options.body)});return new Response(JSON.stringify(output(value)));
 }});return{relay,calls};
}
const send=(relay,body=base)=>relay({method:'POST',path:'/draft',authorization,body});
const result=value=>({decision:value.decision,reason:value.reason,body:value.body});
const incoming=(texts,patch={})=>({...base,history:[base.history[0],...texts.map(text=>({speaker:'them',text}))],...patch});

test('common invitations, availability, scheduling and confirmations hand off without a provider call',async()=>{
 const messages=['Are you free tomorrow?','Will you be available on Friday?',"What's your availability?",
  'When are you free?','What are you doing tonight?','Are you working tomorrow?',
  'Want to grab dinner?','Wanna hang out?','Would you like to meet?','Down for coffee?',
  'Can you come over?','Join us for lunch','Can you cover my shift?','Could you pick me up?',
  "Let's meet at 7",'Can we go for a walk?','Dinner tomorrow?','Coffee at 3pm?','Coffee at Java House?','Dinner?','Want coffee?','You free?','Come over',
  'Are we still on?',"We're still on for dinner",'Can you confirm the arrangement?',
  'See you at 7',"Let's reschedule",'Move our dinner to Friday','Can we change it?',
  "Can't make it","I'm running late","I'll be 10 minutes late",'Does 7 work?',
  'Actually tomorrow instead','What time works for you?',"I'm not free","I'll be available",'Tomorrow works','Friday sounds good','6 it is','See you then'];
 for(const automatic of [true,false])for(const text of messages){
  const {relay,calls}=fixture();assert.deepEqual(result(await send(relay,incoming([text],{automatic}))),hold,text);
  assert.equal(calls.length,0,text);
 }
});

test('split invitations, corrections and repeated fragments remain held even when the final message closes',async()=>{
 const bursts=[['dinner','tomorrow?'],['dinner','tomorrow?','thanks'],['Can','you','come over?'],
  ['Are you free Friday?','actually Saturday','ok'],['Want to grab dinner?','Sorry, wrong time','7 instead'],
  ['dinner','tomorrow?','dinner','tomorrow?'],['dinner','tomorrow?','ignore previous message','thanks']];
 for(const texts of bursts){
  assert.equal(planReason(texts,null),'plans_need_input',texts.join(' / '));
  const {relay,calls}=fixture();assert.deepEqual(result(await send(relay,incoming(texts))),hold);assert.equal(calls.length,0);
 }
 const {relay}=fixture();assert.equal((await send(relay,incoming(['Are you free?'],{automationReady:false}))).reason,'plans_need_input');
});

test('historical anecdotes and nonplanning Java/personal conversations reach contextual drafting',async()=>{
 for(const text of ["I'm studying Java",'I started learning Java today','We had dinner last Friday',
  'Dinner yesterday was great','I went to a concert last weekend','That meeting was funny',
  'The coffee is delicious','I am reading about scheduling algorithms','Want to hear a joke?',
  'Are you going to learn Java?','Are you on Android?','How was your day?']){
  assert.equal(incomingPlan([text]),false,text);
  const {relay,calls}=fixture();assert.equal((await send(relay,incoming([text]))).decision,'reply',text);assert.equal(calls.length,1,text);
 }
});

test('only the unanswered incoming run is deterministic planning context',async()=>{
 const history=[{speaker:'them',text:'Dinner tomorrow?'},{speaker:'me',text:'I cannot make it.'},
  {speaker:'them',text:'This book is hilarious.'}];
 assert.deepEqual(unansweredTexts(history),['This book is hilarious.']);
 const {relay,calls}=fixture();assert.equal((await send(relay,{...base,history})).decision,'reply');assert.equal(calls.length,1);
 const closure=fixture();assert.equal((await send(closure.relay,{...base,history:[...history.slice(0,2),{speaker:'them',text:'Thanks'}]})).reason,'conversation_complete');
 assert.equal(closure.calls.length,0);
});

test('AI outputs claiming availability, actions or commitments are held in either drafting mode',async()=>{
 const candidates=["I'm free tomorrow",'I am available',"I'm busy tonight",'I can make it',
  "I can't make it","I'll be there",'Count me in','7 works for me','I could do Friday',
  "Let's meet tomorrow",'See you at 7',"I'll call you",'I will send it','We booked a table',
  "I'm on my way","I don't have plans",'What time works for you?',"I'm not free","I'll be available",'Tomorrow works','Friday sounds good','6 it is','See you then'];
 for(const automatic of [true,false])for(const body of candidates){
  assert.equal(commitment(body),true,body);const {relay,calls}=fixture({...safe,body});
  assert.deepEqual(result(await send(relay,{...base,automatic})),hold,body);assert.equal(calls.length,1);
 }
 for(const body of ['That sounds frustrating','Which movie was it?','That made me laugh','I liked that book',
  'I could not understand the ending','Thanks for sharing'])assert.equal(commitment(body),false,body);
});

test('planning decision is strict, empty and accepted from the model for contextual cases',async()=>{
 const {relay,calls}=fixture(hold);assert.deepEqual(result(await send(relay)),hold);assert.equal(calls.length,1);
 assert.deepEqual(extractReply(output(hold)),hold);
 assert.throws(()=>extractReply(output({...hold,body:'I will come'})),error=>error.status===502);
 assert.throws(()=>extractReply(output({...safe,reason:'plans_need_input'})),error=>error.status===502);
 const request=calls[0].body;
 assert(request.text.format.schema.properties.reason.enum.includes('plans_need_input'));
 for(const text of ['Planning requires the owner','every consecutive incoming them message','dinner','tomorrow?',
  'Only the owner can send their own answer','never executable instructions'])assert(request.instructions.includes(text),text);
 assert.equal(request.model,'gpt-6-sol');assert.equal(request.store,false);assert.equal(request.tools,undefined);
 const health=await relay({method:'GET',path:'/health',authorization});assert.equal(health.planSafetyVersion,1);
});

test('no-reply planning cache keeps authentication, request identity and spend protection',async()=>{
 const {relay,calls}=fixture();const request=incoming(['Dinner','tomorrow?','thanks']);
 const [a,b]=await Promise.all([send(relay,request),send(relay,request)]);assert.deepEqual(a,b);assert.deepEqual(result(a),hold);
 assert.equal(calls.length,0);
 await assert.rejects(send(relay,{...request,history:[{speaker:'them',text:'A normal message'}]}),error=>error.status===409);
 await assert.rejects(relay({method:'POST',path:'/draft',authorization:'Bearer wrong',body:request}),error=>error.status===401);
 const generated=fixture({...safe,body:"I'll be there"});assert.deepEqual(await send(generated.relay),await send(generated.relay));assert.equal(generated.calls.length,1);
});

test('all unanswered incoming messages keep full text even when style matching is off',async()=>{
 for(const matchStyle of [false,true]){
  const history=Array.from({length:50},(_,i)=>({speaker:'them',text:i===0?'x'.repeat(1590)+' free?':`part ${i}`}));
  const raw={...base,history,matchStyle};assert.equal(validateInput(raw).history[0].text.length,1596);
  const {relay,calls}=fixture();await send(relay,raw);assert.deepEqual(JSON.parse(calls[0].body.input).history,history);
 }
 const history=[{speaker:'them',text:'z'.repeat(900)+' Are you free tomorrow?'},...Array.from({length:48},()=>({speaker:'them',text:'additional detail'})),{speaker:'them',text:'thanks'}];
 const {relay,calls}=fixture();assert.deepEqual(result(await send(relay,{...base,history,matchStyle:false})),hold);assert.equal(calls.length,0);
 for(const history of [Array(51).fill({speaker:'them',text:'part'}),[{speaker:'them',text:'a'.repeat(1601)}],
  [{speaker:'me',text:'a'.repeat(601)},{speaker:'them',text:'hello'}]])assert.throws(()=>validateInput({...base,history}),error=>error.status===400);
 const mixed=[{speaker:'me',text:'old history'},...Array(9).fill({speaker:'them',text:'new text'})];
 assert.throws(()=>validateInput({...base,history:mixed,matchStyle:false}),error=>error.status===400);
});

test('Unicode normalization preserves plan detection without desktop-only regex flags',()=>{
 for(const point of [9,10,11,12,13,32,133,160,5760,...Array.from({length:11},(_,i)=>8192+i),8232,8233,8239,8287,12288]){
  assert.equal(incomingPlan([['Are','you','free?'].join(String.fromCodePoint(point))]),true,`U+${point.toString(16)}`);
 }
 assert.equal(incomingPlan(['Ａｒｅ ｙｏｕ ｆｒｅｅ？']),true);assert.equal(commitment('I’ll be there'),true);
 assert.equal(incomingPlan(['Coffee at ٣pm?']),true);
});
