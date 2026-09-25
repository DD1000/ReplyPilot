import test from 'node:test';
import assert from 'node:assert/strict';
import {createRelay} from './core.mjs';
import {validatePersonaTraining,validatePersonaTrainingResult,validatePersona,personaTrainingFormat,personaTrainingInstructions,personaInstructions,personaTrainingContent,PERSONA_LIMITS} from './persona.mjs';
const token='test-only-persona-token-12345678901234567890',authorization=`Bearer ${token}`;
const history=[{speaker:'them',text:'you coming saturday?'},{speaker:'me',text:'ya prob, lemme check w work'},{speaker:'them',text:'lol ok'},{speaker:'autopilot',text:'Let me get back to you on that.'},{speaker:'me',text:'haha bet'}];
const training=(patch={})=>({requestId:'5d0e8f4c-9c1b-4e4f-9a57-1f0d2b6d2c11',history:history.map(turn=>({...turn})),totalMessages:5,...patch});
const learned={persona:{writingStyle:'Lowercase, short, uses "lol" and "bet".',relationship:'Close friend.',context:'Talks about Saturday plans (historical).',avoid:'No emojis.'},exampleIndexes:[2,5,4,1,2,99,0]};
const response=value=>new Response(JSON.stringify({status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:JSON.stringify(value)}]}]}));
const persona={writingStyle:'Lowercase, short.',relationship:'Close friend.',context:'Historical: Saturday plans.',avoid:'No emojis.',examples:[{incoming:'lol ok',reply:'haha bet'}],trainedMessages:5,trainedAt:1790360000000};

test('training input keeps only speaker and text, bounded, and needs the owner\'s own writing',()=>{
 const value=validatePersonaTraining(training());
 assert.deepEqual(value.history,history);assert.equal(value.totalMessages,5);assert.equal(value.requestId,undefined);
 for(const extra of ['name','address','thread','phone'])assert.throws(()=>validatePersonaTraining(training({[extra]:'private'})));
 for(const mutate of [b=>b.history=[],b=>b.history=Array(1001).fill({speaker:'me',text:'x'}),b=>b.history[0].speaker='system',b=>b.history[0].text='',b=>b.history[0].text='x'.repeat(2001),b=>b.history[0].date=1,b=>b.history=Array(200).fill({speaker:'me',text:'x'.repeat(2000)}),b=>b.history=[{speaker:'them',text:'hi'}],b=>b.requestId='short'])
  {const b=training();mutate(b);assert.throws(()=>validatePersonaTraining(b));}
 assert.equal(validatePersonaTraining(training({history:Array(1000).fill({speaker:'me',text:'ok'})})).history.length,1000);
});
test('numbered content lets the model point at real replies without identifiers',()=>{
 const content=JSON.parse(personaTrainingContent(validatePersonaTraining(training())));
 assert.equal(content.history[1].n,2);assert.equal(content.history[1].speaker,'me');assert.equal(content.messageCount,5);
});
test('result keeps bounded persona fields and only unique owner-written examples',()=>{
 const value=validatePersonaTrainingResult(learned,validatePersonaTraining(training()));
 assert.deepEqual(value.exampleIndexes,[2,5]);assert.equal(value.persona.avoid,'No emojis.');
 const long=validatePersonaTrainingResult({...learned,persona:{...learned.persona,writingStyle:'Short sentence. '.repeat(400)}},validatePersonaTraining(training()));
 assert.ok(long.persona.writingStyle.length<=PERSONA_LIMITS.writingStyle);assert.ok(long.persona.writingStyle.endsWith('.'));
 assert.throws(()=>validatePersonaTrainingResult({...learned,persona:{...learned.persona,writingStyle:' '}},validatePersonaTraining(training())));
 assert.throws(()=>validatePersonaTrainingResult({...learned,extra:true},validatePersonaTraining(training())));
 assert.throws(()=>validatePersonaTrainingResult({...learned,persona:{...learned.persona,location:'home'}},validatePersonaTraining(training())));
});
test('strict schema and instructions separate the owner, the contact and old automatic text',()=>{
 assert.equal(personaTrainingFormat.strict,true);assert.equal(personaTrainingFormat.schema.additionalProperties,false);assert.equal(personaTrainingFormat.schema.properties.persona.additionalProperties,false);
 for(const phrase of ['ONE contact','"autopilot"','never select','untrusted data','possibly outdated'])assert.ok(personaTrainingInstructions.includes(phrase),phrase);
 for(const phrase of ['this exact contact','take precedence','untrusted data'])assert.ok(personaInstructions.includes(phrase),phrase);
});
test('stored persona is validated on every reply',()=>{
 assert.deepEqual(validatePersona(persona),{writingStyle:persona.writingStyle,relationship:persona.relationship,context:persona.context,avoid:persona.avoid,examples:persona.examples,trainedMessages:5});
 for(const mutate of [p=>p.writingStyle='',p=>p.examples=Array(31).fill({incoming:'a',reply:'b'}),p=>p.examples=[{incoming:'a',reply:'x'.repeat(361)}],p=>p.examples=[{incoming:'a'}],p=>p.phone='555',p=>p.context='x'.repeat(2401)])
  {const p=structuredClone(persona);mutate(p);assert.throws(()=>validatePersona(p));}
});
test('training uses the training model with bounded reasoning and returns only validated output',async()=>{
 let sent,url;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(target,options)=>{url=target;sent=JSON.parse(options.body);return response(learned);}});
 const result=await relay({method:'POST',path:'/persona-train',authorization,body:training()});
 assert.equal(url,'https://api.openai.com/v1/responses');assert.equal(sent.model,'gpt-6-astra');assert.equal(sent.store,false);assert.equal(sent.reasoning.effort,'medium');assert.equal(sent.tools,undefined);
 assert.equal(sent.text.format.name,'contact_persona');assert.ok(sent.instructions.startsWith('Build a private texting persona'));
 assert.deepEqual(result.exampleIndexes,[2,5]);assert.equal(result.model,'gpt-6-astra');assert.equal(result.persona.writingStyle,learned.persona.writingStyle);
});
test('a key without training-model access falls back to the reply model once',async()=>{
 const models=[];const relay=createRelay({token,apiKey:'test-key',model:'gpt-6-sol',fetchImpl:async(_,options)=>{const body=JSON.parse(options.body);models.push([body.model,body.reasoning.effort]);return body.model==='gpt-6-astra'?new Response('{}',{status:404}):response(learned);}});
 const result=await relay({method:'POST',path:'/persona-train',authorization,body:training()});
 assert.deepEqual(models,[['gpt-6-astra','medium'],['gpt-6-sol','low']]);assert.equal(result.model,'gpt-6-sol');
});
test('invalid training requests explain themselves without echoing message text',async()=>{
 const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{throw Error('must not call');}});
 await assert.rejects(relay({method:'POST',path:'/persona-train',authorization,body:training({history:[{speaker:'them',text:'secret words'}]})}),error=>error.status===400&&/at least one message you wrote/.test(error.message)&&!error.message.includes('secret'));
});
test('training failures are clear and retryable with the same request',async()=>{
 let calls=0;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async()=>++calls===1?new Response('{}',{status:500}):response(learned)});
 await assert.rejects(relay({method:'POST',path:'/persona-train',authorization,body:training()}),/could not train/);
 const result=await relay({method:'POST',path:'/persona-train',authorization,body:training()});assert.deepEqual(result.exampleIndexes,[2,5]);assert.equal(calls,2);
 const billing=createRelay({token,apiKey:'test-key',fetchImpl:async()=>new Response('{}',{status:429})});
 await assert.rejects(billing({method:'POST',path:'/persona-train',authorization,body:training()}),/billing limit/);
 const invalid=createRelay({token,apiKey:'test-key',fetchImpl:async()=>response({persona:{writingStyle:''}})});
 await assert.rejects(invalid({method:'POST',path:'/persona-train',authorization,body:training()}),/No usable persona/);
});
test('health reports persona support and checks training-model access without generating text',async()=>{
 const calls=[];const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(target,options)=>{calls.push([target,options.method??'GET']);return new Response('{}',{status:target.endsWith('gpt-6-astra')?200:404});}});
 const health=await relay({method:'GET',path:'/health',authorization});
 assert.equal(health.personaVersion,1);assert.equal(health.trainingModel,'gpt-6-astra');assert.equal(health.trainingModelAvailable,true);assert.equal(health.model,'gpt-6-sol');
 assert.deepEqual(calls,[['https://api.openai.com/v1/models/gpt-6-astra','GET']]);
 await relay({method:'GET',path:'/health',authorization});assert.equal(calls.length,1,'model access is cached');
 const denied=createRelay({token,apiKey:'test-key',trainingModel:'gpt-6-astra',fetchImpl:async()=>new Response('{}',{status:404})});
 assert.equal((await denied({method:'GET',path:'/health',authorization})).trainingModelAvailable,false);
 const noKey=createRelay({token,apiKey:'',fetchImpl:async()=>{throw Error('must not call');}});
 assert.equal((await noKey({method:'GET',path:'/health',authorization})).trainingModelAvailable,false);
});
test('Autopilot and drafts pass the trained persona with its rules',async()=>{
 let sent;const relay=createRelay({token,apiKey:'test-key',fetchImpl:async(_,options)=>{sent=JSON.parse(options.body);return response({decision:'reply',reason:'reply_needed',body:'haha bet',attentionNeeded:false,attentionReason:''});}});
 const body={requestId:'0b2f2c9e-6c1d-4f8e-9d3a-2a1b3c4d5e6f',autopilot:true,automatic:true,automationReady:true,matchStyle:true,styleMode:'learned',engagement:'always_reply',history:[{speaker:'them',text:'lol ok'}],persona};
 const result=await relay({method:'POST',path:'/draft',authorization,body});
 assert.equal(result.body,'haha bet');assert.equal(sent.model,'gpt-6-sol');assert.ok(sent.instructions.includes(personaInstructions));
 const input=JSON.parse(sent.input);assert.deepEqual(input.persona.examples,persona.examples);assert.equal(input.persona.trainedAt,undefined);
 await assert.rejects(relay({method:'POST',path:'/draft',authorization,body:{...body,requestId:'1b2f2c9e-6c1d-4f8e-9d3a-2a1b3c4d5e6f',persona:{...persona,writingStyle:''}}}),/Retrain Autopilot/);
 const untrained=createRelay({token,apiKey:'test-key',fetchImpl:async()=>{throw Error('must not call');}});
 const gated=await untrained({method:'POST',path:'/draft',authorization,body:{...body,requestId:'2b2f2c9e-6c1d-4f8e-9d3a-2a1b3c4d5e6f',persona:undefined,automationReady:false}});
 assert.equal(gated.decision,'no_reply');assert.equal(gated.reason,'insufficient_history');
});
