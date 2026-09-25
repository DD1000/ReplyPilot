// Generates a restricted phone token locally. Never asks for or reads the OpenAI API key.
import {randomBytes} from 'node:crypto';
import {mkdirSync,writeFileSync,existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
const origin=process.argv[2];
let url;try{url=new URL(origin);}catch{throw new Error('Usage: node pair.mjs https://YOUR-SERVICE-DOMAIN');}
if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash||url.pathname!=='/')throw new Error('Use only the service’s HTTPS origin.');
const folder=resolve('../.local-secrets');mkdirSync(folder,{recursive:true,mode:0o700});
const tokenFile=resolve(folder,'phone-token.txt');
const token=existsSync(tokenFile)?readFileSync(tokenFile,'utf8').trim():randomBytes(32).toString('base64url');
if(!/^[a-zA-Z0-9_-]{32,128}$/.test(token))throw new Error('The saved phone token is invalid.');
writeFileSync(tokenFile,token+'\n',{mode:0o600});
const pairingFile=resolve(folder,'phone-pairing.txt');writeFileSync(pairingFile,JSON.stringify({url:url.origin,token})+'\n',{mode:0o600});
console.log(`Set REPLY_PILOT_TOKEN on your service using ${tokenFile}\nPaste ${pairingFile} into the phone app’s Settings → Phone pairing code.\nThese files grant access to paid drafts. Keep them private.`);
