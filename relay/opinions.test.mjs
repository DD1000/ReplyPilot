import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay,validateInput} from './core.mjs';
import {autopilotInstructions} from './autopilot.mjs';
const token='test-only-opinions-token-1234567890123456789',authorization=`Bearer ${token}`;
const persona={writingStyle:'Casual, lowercase, says "lol".',relationship:'Friend.',context:'',avoid:'',examples:[],trainedMessages:300};
const question='How do you in-vision the 4 years to be with the rapid rate of A.I. growing.\n Give me a prediction and estimation of how that will affect the economy';
const views="Very pro AI. It will replace a lot of simple and repetitive tasks. People who don't use it will fall behind.";
const request=(patch={})=>({requestId:crypto.randomUUID(),autopilot:true,automatic:true,automationReady:true,matchStyle:true,styleMode:'learned',engagement:'always_reply',history:[{speaker:'me',text:"Practice chatting with me let's see what happens"},{speaker:'them',text:question}],persona,ownerViews:views,...patch});
const response=value=>new Response(JSON.stringify({status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:JSON.stringify(value)}]}]}));
const take={decision:'reply',reason:'reply_needed',body:"honestly i'm all in on AI lol. it's gonna take over a lot of the boring repetitive stuff, and people who don't use it are gonna fall behind",attentionNeeded:false,attentionReason:''};
const send=(relay,body)=>relay({method:'POST',path:'/draft',authorization,body});

test('an opinion question reaches the model with the owner views and comes back as an ordinary reply',async()=>{
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response(take);}});
 const result=await send(relay,request());
 assert.equal(result.body,take.body);assert.equal(result.attentionNeeded,false);
 assert.equal(JSON.parse(sent.input).ownerViews,views);
});
test('Autopilot instructions treat opinions as conversation and forbid canned lines',()=>{
 for(const phrase of ['Questions about the owner themselves','Use personal_info only for private or checkable facts','Opinion and big-picture questions','ownerViews','never contradict it','friendly non-answer','attentionNeeded false','never do the work','Got your message. Let me get back to you.'])assert.ok(autopilotInstructions.includes(phrase),phrase);
 for(const phrase of ['unsupported expertise','use a brief deferral and attention','such as "Let me get back to you on that."'])assert.ok(!autopilotInstructions.includes(phrase),phrase);
});
test('ownerViews is bounded text and only goes to Autopilot',async()=>{
 assert.equal(validateInput(request({ownerViews:'  x  '})).ownerViews,'x');
 assert.equal(validateInput(request({ownerViews:''})).ownerViews,undefined);
 for(const bad of ['x'.repeat(1201),42,{},null])assert.throws(()=>validateInput(request({ownerViews:bad})),JSON.stringify(bad).slice(0,20));
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response({decision:'reply',reason:'reply_needed',body:'haha yeah'});}});
 await send(relay,request({autopilot:false,automatic:false}));
 assert.equal(JSON.parse(sent.input).ownerViews,undefined);
});
