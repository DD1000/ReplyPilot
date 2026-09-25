import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,validateInput} from './core.mjs';
import {bedtime} from './girlfriend-mode.mjs';

const token='girlfriend-test-only-token-01234567890123456789';
const authorization=`Bearer ${token}`;
const base={requestId:'girlfriend-test-request-0001',automatic:true,automationReady:true,matchStyle:true,engagement:'girlfriend',
 history:[{speaker:'me',text:'That book made me laugh.'},{speaker:'them',text:'The ending was so unexpected.'}]};
const safe={decision:'reply',reason:'reply_needed',body:'Which part surprised you?'};
const noReply=reason=>({decision:'no_reply',reason,body:''});
const result=value=>({decision:value.decision,reason:value.reason,body:value.body});
function fixture(value=safe){
 const calls=[];const relay=createRelay({apiKey:'test-key',token,fetchImpl:async(url,options)=>{
  calls.push({url,...JSON.parse(options.body)});
  return new Response(JSON.stringify({status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:JSON.stringify(value)}]}]}));
 }});return{relay,calls};
}
const input=(messages,extra={})=>({...base,history:[base.history[0],...messages.map(text=>({speaker:'them',text}))],...extra});
const send=(relay,body=base)=>relay({method:'POST',path:'/draft',authorization,body});

test('Girlfriend Autopilot uses brief owner-grounded guidance without changing legacy modes or model settings',async()=>{
 assert.equal(validateInput(base).engagement,'girlfriend');
 assert.equal(validateInput({...base,engagement:undefined}).engagement,'natural');
 const personality={about:'I enjoy books.',humor:'Dry humor, no insults.',examples:'Me: a plot twist for my coffee'};
 const {relay,calls}=fixture();assert.equal((await send(relay,{...base,personality})).decision,'reply');
 const request=calls[0];assert.equal(request.model,'gpt-6-sol');assert.equal(request.store,false);assert.equal(request.tools,undefined);
 assert.equal(JSON.parse(request.input).engagement,'girlfriend');
 for(const phrase of ['Engagement: girlfriend','at most one relevant follow-up question','real owner-supplied details',
  'Do not invent first-person experiences','relationship conflict','needs_review','do not send a goodnight back'])assert(request.instructions.includes(phrase),phrase);
 assert(!request.instructions.includes(personality.examples));
 const health=await relay({method:'GET',path:'/health',authorization});assert.equal(health.girlfriendModeVersion,1);
});

test('current bedtime and stop requests end girlfriend replies before any model call in manual or automatic mode',async()=>{
 const examples=['Goodnight','Good night!','Night','night-night','gn','Goodnight babe ❤️','Goodnight love you','Goodnight I love you','Goodnight see you tomorrow','Goodnight talk tomorrow',
  "I'm going to bed",'I am heading to bed','Headed to bed','Off to bed','Going to sleep',
  'I need to sleep','I should sleep',"I'm about to go to sleep",'gonna go to bed',"I'm going to sleep goodnight",'Okay, heading to bed','Calling it a night','Let me sleep','Stop texting me','Please stop messaging me',
  'Leave me alone','stop','please stop',"Don't text me anymore",'Goodnight, see you tomorrow',
  "I'm going to bed. Dinner tomorrow?",'Goodnight. Where are you?'];
 for(const automatic of [true,false])for(const text of examples){
  const {relay,calls}=fixture();assert.deepEqual(result(await send(relay,input([text],{automatic}))),noReply('conversation_complete'),text);assert.equal(calls.length,0,text);
 }
});

test('negations, past anecdotes, quoted speech and ordinary night topics are not bedtime commands',async()=>{
 const examples=["I'm not going to bed",'I am not heading to bed',"I'm not tired and not going to sleep",'I was going to bed when the dog barked',
  'She said goodnight yesterday','Goodnight is a strange word','I watched a movie last night',
  'The night sky looked amazing','What does goodnight mean?',"Do not say goodnight yet",'"Goodnight" is what the character said',
  'She wrote "I am going to bed" in the story'];
 for(const text of examples){const {relay,calls}=fixture();const response=await send(relay,input([text]));assert.equal(response.decision,'reply',text);assert.equal(calls.length,1,text);}
});

test('goodnight followed by a reaction stays stopped while a newer substantive message resumes',async()=>{
 for(const reaction of ['❤️','👍','okay','okay ❤️','Thanks 💕','lol','love you']){
  const {relay,calls}=fixture();assert.deepEqual(result(await send(relay,input(['Goodnight',reaction]))),noReply('conversation_complete'));assert.equal(calls.length,0);
 }
 for(const text of ['Morning','Morning! How did that movie end?','I found the book you mentioned.',"Actually, what was that actor's name?"]){
  const {relay,calls}=fixture();assert.equal((await send(relay,input(['Goodnight',text]))).decision,'reply',text);assert.equal(calls.length,1);
 }
 const historical=fixture();assert.equal((await send(historical.relay,{...base,history:[{speaker:'them',text:'Goodnight'},{speaker:'me',text:'Sleep well'},{speaker:'them',text:'What was that book called?'}]})).decision,'reply');
});

test('girlfriend mode keeps acknowledgment cutoff, eligibility, planning, location and unsuitable-request holds',async()=>{
 const repeated=fixture();assert.deepEqual(result(await send(repeated.relay,input(['Okay'],{history:[
  {speaker:'them',text:'Thanks'},{speaker:'me',text:'Of course'},{speaker:'them',text:'Okay'}]}))),noReply('conversation_complete'));assert.equal(repeated.calls.length,0);
 for(const [text,extra,reason] of [['Dinner tomorrow?',{},'plans_need_input'],['Where are you?',{},'needs_review'],
  ['Write a Java program',{},'needs_review'],['How was the movie?',{automationReady:false},'insufficient_history']]){
  const {relay,calls}=fixture();assert.deepEqual(result(await send(relay,input([text],extra))),noReply(reason));assert.equal(calls.length,0);
 }
 for(const [body,reason] of [["I'll be there",'plans_need_input'],['x'.repeat(361),'needs_review'],['That book made me laugh.','repeated_reply']]){
  const {relay}=fixture({...safe,body});assert.deepEqual(result(await send(relay)),noReply(reason));
 }
 const uncertain=fixture(noReply('needs_review'));assert.deepEqual(result(await send(uncertain.relay,input(['Do you really love me or are you pretending?']))),noReply('needs_review'));
});

test('bedtime decisions retain authentication and request-id caching',async()=>{
 const {relay,calls}=fixture();const request=input(['Goodnight']);const first=await send(relay,request);
 assert.deepEqual(await send(relay,request),first);assert.equal(calls.length,0);
 await assert.rejects(send(relay,{...request,engagement:'keep_going'}),error=>error.status===409);
 await assert.rejects(relay({method:'POST',path:'/draft',body:request}),error=>error.status===401);
});


test('bedtime scope, Unicode and cached mixed-location decisions remain consistent',async()=>{
 for(const text of ['Ｇｏｏｄｎｉｇｈｔ',"I’m going to bed",'Going\u00a0to\u2003sleep','Good\u200bnight'])assert.equal(bedtime(text),true,text);
 for(const text of ['> Goodnight',"```Goodnight```",'```Goodnight','“Goodnight”','‘I am heading to bed’',"Please don't stop texting me"])
  assert.equal(bedtime(text),false,text);
 const legacy=fixture();assert.equal((await send(legacy.relay,input(['Going to bed'],{engagement:'keep_going'}))).decision,'reply');assert.equal(legacy.calls.length,1);
 const firstAck=fixture();assert.equal((await send(firstAck.relay,input(['Okay']))).decision,'reply');assert.equal(firstAck.calls.length,1);
 const paused=fixture();const request=input(['Goodnight. Where are you?'],{locationContext:{label:'at home',capturedAt:1,expiresAt:2},automationReady:false});
 const first=await send(paused.relay,request);assert.equal(first.reason,'conversation_complete');assert.deepEqual(await send(paused.relay,request),first);assert.equal(paused.calls.length,0);
});
