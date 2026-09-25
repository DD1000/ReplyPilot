// Conservative handoff for common planning language. The model handles subtler
// context; these rules never authorize a commitment or claim perfect detection.
const normalize=text=>String(text??'').normalize('NFKC').toLowerCase()
 .replace(/[\u200b-\u200d\ufeff]/gu,'').replace(/[’‘]/gu,"'").replace(/[\p{Z}\x09-\x0d\u0085]+/gu,' ').trim();
const time=String.raw`(?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \p{Nd}{1,2}(?::\p{Nd}{2})?(?: ?[ap]m)?|\p{Nd}{1,2}(?::\p{Nd}{2})? ?[ap]m|noon|midnight|in \p{Nd}+ (?:minutes?|hours?|days?))`;
const activity=String.raw`(?:dinner|lunch|breakfast|brunch|coffee|drinks|a drink|movie|movies|concert|party|meeting|appointment|date|trip|hike|walk|hangout|game|gym|call|catch up)`;
const patterns=[
 String.raw`\b(?:are|r|will|would) (?:you|u|ya)(?: be| still| happen to be)? (?:free|available|busy|off)\b`,
 String.raw`\b(?:you|u) (?:free|available)\b|\b(?:come over|come join (?:me|us)|swing by|stop by)\b`,
 String.raw`\b(?:i(?:'m| am)|im|we(?:'re| are))(?: not)? (?:free|available)\b|\b(?:i|we)'ll be (?:free|available)\b`,
 String.raw`\b(?:when|what time|which days?|what days?) (?:are|will|would) (?:you|u)(?: be)? (?:free|available|off)\b|\b(?:your|ur) (?:availability|schedule)\b`,
 String.raw`\b(?:what (?:are|r) (?:you|u) (?:doing|up to)|are you working)\b.*\b${time}\b`,
 String.raw`\b(?:(?:do|would) you (?:want|like|care) to|want to|wanna|up for|down for|let's|lets|shall we|can we|could we|we should) (?:grab|get|go|meet|hang|catch up|do|have|come|join|visit|plan|book|schedule|watch|see|play|eat|take a walk)\b`,
 String.raw`\b(?:up for|down for|join (?:me|us) for|how about|what about) (?:a |some |the )?${activity}\b`,
 String.raw`\b(?:can|could|would|will) (?:you|u)(?: please)? (?:come|make it|join|meet|call|hop on|cover (?:my|a|the) shift|pick (?:me|us) up|give (?:me|us) a ride|babysit|watch (?:the|my|our) kids)\b`,
 String.raw`\b(?:are (?:you|we)|r u)(?: still)? (?:coming|joining|meeting)\b|\bare we(?: still)? on\b|\bare you still (?:on|going)\b|\b(?:we(?:'re| are)|it(?:'s| is)) still on\b`,
 String.raw`\b(?:reschedule|re-schedule|rearrange)\b|\b(?:cancel|move|change|postpone) (?:our |the |that )?(?:plans?|meeting|appointment|dinner|lunch|date|reservation|it)\b`,
 String.raw`\b(?:can't|cannot|can not|won't|will not) make it\b|\b(?:running|going to be|will be|i'll be|im|i'm|i am) (?:\p{Nd}+ (?:minutes?|hours?) )?late\b`,
 String.raw`\b(?:what time|when|where) (?:should|shall|can|could|do|are) we (?:meet|go|start|leave|get together)\b|\b(?:what time|when) (?:works|is good|is best) (?:for you|for u)\b`,
 String.raw`\b(?:see|meet) you (?:at|on|tomorrow|tonight|this|next|then)\b|\b(?:confirm|confirming|confirmed)(?: our| the| your)? (?:plans?|meeting|appointment|reservation|dinner|time|arrangement)\b`,
 String.raw`\b(?:how about|what about|does|would|can we do|make it|actually) ${time}\b`,
 String.raw`\b(?:does|would) (?:\p{Nd}{1,2}(?::\p{Nd}{2})?(?: ?[ap]m)?|${time})(?: still)? (?:work|suit)\b|\b\p{Nd}{1,2}(?::\p{Nd}{2})?(?: ?[ap]m)? instead\b`,
 String.raw`\b${time} (?:works|sounds good|is (?:good|perfect|fine))\b|\b\p{Nd}{1,2}(?::\p{Nd}{2})?(?: ?[ap]m)? it is\b`
].map(pattern=>new RegExp(pattern,'u'));
const activityPattern=new RegExp(String.raw`\b${activity}\b`,'u');
const timePattern=new RegExp(String.raw`\b${time}\b`,'u');
const shortInvite=new RegExp(String.raw`^(?:(?:want|fancy) (?:some |a )?)?${activity}(?: (?:at|with|near|after|before) .{1,100})?\?+$`,'u');
const past=/\b(?:yesterday|last (?:night|week|weekend|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)|used to|had|went|was|were|did)\b/u;
const commitmentPatterns=[
 String.raw`\b(?:i(?:'m| am)|im|we(?:'re| are))(?: not)? (?:free|available|busy|off work|working|on my way|on our way|coming)\b`,
 String.raw`\b(?:i|we) (?:can|could|will|would|can't|cannot|won't)(?: definitely| probably| happily)? (?:make it|come|join|meet|attend|cover|pick|drop|bring|call|text|send|book|reserve|check|get back|be there|do (?:it|that)|handle|take care)\b`,
 String.raw`\b(?:i|we)'(?:ll|d) (?:come|join|meet|attend|cover|pick|drop|bring|call|text|send|book|reserve|check|get back|be (?:there|free|available|busy)|do (?:it|that)|handle|take care)\b`,
 String.raw`\b(?:count me in|i'm in|im in|i am in|i can do|i could do|works for me|works for us|my schedule is|i have no plans|i don't have plans|i do not have plans)\b`,
 String.raw`\b(?:i|we)(?:'ve| have)? (?:booked|reserved|scheduled|confirmed|cancelled|canceled)\b`,
 String.raw`\b${time} (?:works|sounds good|is (?:good|perfect|fine))\b|\b\p{Nd}{1,2}(?::\p{Nd}{2})?(?: ?[ap]m)? it is\b`
].map(pattern=>new RegExp(pattern,'u'));

export function unansweredTexts(history){
 let first=history.length;while(first>0&&history[first-1].speaker==='them')first--;
 return history.slice(first).map(message=>message.text);
}
export function incomingPlan(messages){
 const parts=messages.map(normalize).filter(Boolean),joined=parts.join(' ');
 if(!joined)return false;
 if(patterns.some(pattern=>parts.some(text=>pattern.test(text))||pattern.test(joined)))return true;
 if(parts.some(text=>shortInvite.test(text)))return true;
 // Short split invitations often omit a verb: "dinner" / "tomorrow?".
 // Historical anecdotes alone, such as "we had dinner last Friday", stay contextual.
 if(activityPattern.test(joined)&&timePattern.test(joined)&&(!past.test(joined)||joined.includes('?')))return true;
 return parts.some(text=>text.includes('?')&&timePattern.test(text)&&text.split(' ').length<=8&&!past.test(text));
}
export function commitment(text){
 const value=normalize(text);return !!value&&(incomingPlan([value])||commitmentPatterns.some(pattern=>pattern.test(value)));
}
export function planReason(messages,candidate){
 return incomingPlan(messages)||commitment(candidate)?'plans_need_input':null;
}
