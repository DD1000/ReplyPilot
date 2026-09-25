import http from 'node:http';
import https from 'node:https';
import {readFileSync} from 'node:fs';
import {createRelay,PublicError,authorized} from './core.mjs';

const secret=process.env.OPENAI_API_KEY|| (process.env.OPENAI_KEY_FILE?readFileSync(process.env.OPENAI_KEY_FILE,'utf8').trim():'');
const token=process.env.REPLY_PILOT_TOKEN|| (process.env.REPLY_PILOT_TOKEN_FILE?readFileSync(process.env.REPLY_PILOT_TOKEN_FILE,'utf8').trim():'');
if(!/^[a-zA-Z0-9_-]{32,128}$/.test(token)||token.startsWith('sk-'))throw new Error('Configure a private REPLY_PILOT_TOKEN with at least 32 characters.');
const relay=createRelay({apiKey:secret,token,model:process.env.OPENAI_MODEL||'gpt-6-sol',trainingModel:process.env.OPENAI_TRAINING_MODEL||'gpt-6-astra',maxDaily:200});
async function handler(req,res){
 res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
 try{
  // Native clients have no browser Origin. Reject cross-origin web requests, including local setup pages.
  if(req.headers.origin)throw new PublicError(403,'Browser requests are not enabled.');
  if(req.method==='GET'&&req.url==='/status'){res.writeHead(200);res.end(JSON.stringify({status:'ok'}));return;}
  if(!authorized(req.headers.authorization,token))throw new PublicError(401,'Phone connection not recognized. Pair again.');
  if(!['/health','/draft','/media-analysis','/train','/history-analysis','/persona-train'].includes(req.url))throw new PublicError(404,'Not found.');
  if(req.method==='POST'&&!String(req.headers['content-type']).startsWith('application/json'))throw new PublicError(415,'Use JSON.');
  const limit=['/media-analysis','/persona-train'].includes(req.url)?1048576:262144;
  const length=Number(req.headers['content-length']||0);if(length>limit)throw new PublicError(413,'Request is too large.');
  let total=0,chunks=[];
  for await(const chunk of req){total+=chunk.length;if(total>limit)throw new PublicError(413,'Request is too large.');chunks.push(chunk);}
  let body;try{body=total?JSON.parse(Buffer.concat(chunks).toString('utf8')):undefined;}catch{throw new PublicError(400,'Invalid JSON.');}
  const value=await relay({method:req.method,path:req.url,authorization:req.headers.authorization,body});
  res.writeHead(200);res.end(JSON.stringify(value));
 }catch(error){res.writeHead(error instanceof PublicError?error.status:500);res.end(JSON.stringify({error:error instanceof PublicError?error.message:'The draft service could not finish.'}));}
}
const key=process.env.TLS_KEY_FILE,cert=process.env.TLS_CERT_FILE;
if(!!key!==!!cert)throw new Error('Set both TLS_KEY_FILE and TLS_CERT_FILE.');
const server=key?https.createServer({key:readFileSync(key),cert:readFileSync(cert),minVersion:'TLSv1.2'},handler):http.createServer(handler);
server.requestTimeout=30000;server.headersTimeout=10000;server.maxHeadersCount=30;
const host=process.env.HOST||(key?'0.0.0.0':'127.0.0.1');
if(!key&&host!=='127.0.0.1'&&process.env.TRUST_TLS_PROXY!=='yes')throw new Error('Plain HTTP requires a trusted TLS reverse proxy.');
server.listen(Number(process.env.PORT||8781),host,()=>console.log(`Reply Pilot relay ready on port ${server.address().port}. API key configured: ${!!secret}. Request content is not logged.`));
