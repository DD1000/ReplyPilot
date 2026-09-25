// Mirrors the phone's pure GirlfriendPolicy; data never becomes instructions.
const normalize=value=>String(value??'').normalize('NFKC').toLowerCase()
 .replace(/[’‘]/gu,"'").replace(/\p{Cf}/gu,'').replace(/[\p{Z}\x09-\x0d\u0085]+/gu,' ').trim();
const quoted=/"[^"]*"|“[^”]*”|‘[^’]*’|(?<![\p{L}\p{N}])'[^']*'(?![\p{L}\p{N}])/gsu;
const end=String.raw`(?:[^\p{L}\p{N}]*|,.*)$`;
const pet=String.raw`(?: ?(?:babe|baby|love|hun|honey|sweetheart|darling|dear|my love|gorgeous|beautiful|xx|xoxo))*`;
const tail=String.raw`(?: (?:i love you|love you|love u|ily|good ?night|night[- ]?night|see you tomorrow|talk (?:to you )?tomorrow))*`;
const night=new RegExp(String.raw`^(?:good ?night|night[- ]?night|night|gn)`+pet+tail+end,'u');
const sleep=new RegExp(String.raw`^(?:(?:i(?:'m| am|m) )?(?:(?:going|heading|headed|off) to (?:bed|sleep)|(?:gonna|about to) (?:go to )?(?:bed|sleep)|calling it a night|turning in)|i (?:need to|have to|got to|should) (?:get some )?sleep|(?:it's |its )?bedtime(?: for me)?|time for bed)(?: (?:now|soon|for the night))*`+pet+tail+end,'u');
const stop=new RegExp(String.raw`^(?:(?:please|pls) )?(?:(?:can|could|would) you (?:please )?)?(?:(?:i said )?stop(?: (?:texting|messaging|contacting|calling)(?: me)?)?|leave me alone|let me sleep|(?:don't|do not) (?:text|message|contact|call) me|(?:let's|lets|let us) (?:stop talking|end (?:this|the) conversation)|i (?:don't|do not) want to talk|(?:give me|i need) (?:some )?space)(?: (?:now|please|anymore|again|for now|tonight|today))*`+end,'u');
const noncurrent=/\b(?:not|never|yesterday|earlier|said|saying|say|told|remember|was|were|used to|last night|if|when)\b|\b(?:don't|can't|cannot|won't|wasn't|weren't)\b/u;
const reactions=new Set(['yes','yeah','yep','yup','no','nope','nah','lol','lmao','haha','hahaha','hehe','aww','aw','love you','i love you','love u','ily','x','xx','xoxo','goodnight','good night','night','gn']);
export function bedtime(body){
 const unquoted=String(body??'').replace(/```.*?(?:```|$)/gsu,' ').replace(quoted,' ').replace(/^\s*>.*$/gmu,' ');
 for(const raw of unquoted.normalize('NFKC').split(/[.!;\r\n。！？]+/u)){
  const clause=normalize(raw).replace(/^(?:ok(?:ay)?|alright|all right)[,!]? +/u,'');
  if(stop.test(clause))return true;
  if(noncurrent.test(clause.split(',',1)[0]))continue;
  if(night.test(clause)||sleep.test(clause))return true;
 }
 return false;
}
export function shouldResume(body,isAcknowledgment){
 if(body==null||bedtime(body))return false;
 const text=normalize(body).replace(/[?!¿؟！？]+/gu,'').trim();
 if(!text||isAcknowledgment(text))return false;
 const words=text.replace(/[^\p{L}\p{N}' ]/gu,' ').replace(/ +/gu,' ').trim();
 if(!words||reactions.has(words)||isAcknowledgment(words))return false;
 const letters=(words.match(/[\p{L}\p{Nd}]/gu)||[]).length;
 return letters>=4||(letters>=2&&/[?¿؟？]/u.test(body));
}
export function girlfriendPaused(incoming,isAcknowledgment){
 let paused=false;
 for(const body of incoming){
  if(bedtime(body))paused=true;
  else if(paused&&shouldResume(body,isAcknowledgment))paused=false;
 }
 return paused;
}
