const assert=require('node:assert/strict'),fs=require('node:fs');
const C=require('../app/src/main/assets/core.js'),S=require('../app/src/main/assets/settlement.js');
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++;};
const r={id:'night',date:'2026-10-31',start:'22:00',end:'02:00',nextDay:true,break:30,type:'工作日',note:'=HYPERLINK("bad")'};
eq(S.summarize([r],'2026-10',{}).total,210);eq(S.summarize([r],'2026-11',{}).total,0);
eq(S.detail(r,{split:true}).parts.map(p=>p.minutes),[105,105]);
eq(S.summarize([r],'2026-11',{split:true,cycleDay:26}).total,210);
eq(S.bounds('2024-03',31),{from:'2024-02-29',to:'2024-03-30'});
eq(S.bounds('2027-01',26),{from:'2026-12-26',to:'2027-01-25'});
eq(S.series([r],'month','2026-11-01',C.TYPES,{cycleDay:26,split:true}).bins.length,31);
eq(S.series([r],'month','2026-11-01',C.TYPES,{cycleDay:26,split:true}).total,210);
eq(S.series([r],'year','2026-11-01',[],{split:true}).total,0);
eq(S.csv([r],'2026-11',{cycleDay:26,split:true},'en').includes("'=HYPERLINK"),true);
for(const tz of ['UTC','America/New_York','Asia/Shanghai']){process.env.TZ=tz;eq(S.detail({...r,date:'2026-11-01',start:'00:30',end:'02:30',nextDay:false,break:0},{}).net,120);}
let seed=1057;function random(n){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed%n;}
const vectors=[];
for(let i=0;i<10000;i++){
 const start=random(1440),gross=1+random(1440),rest=random(gross),end=(start+gross)%1440,step=[1,5,15,30][random(4)],split=random(2)===1,clock=n=>String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0');
 const rec={...r,start:clock(start),end:clock(end),nextDay:start+gross>=1440,break:rest},d=S.detail(rec,{split,rounding:step});
 eq(d.parts.reduce((a,x)=>a+x.minutes,0),Math.floor((gross-rest)/step)*step);eq(d.parts.reduce((a,x)=>a+x.rest,0),rest);eq(d.parts.reduce((a,x)=>a+x.raw,0),gross-rest);assert.ok(d.parts.every(x=>x.minutes>=0&&x.raw>=0));checks++;
 const a=C.calculate(rec);let parts=d.parts.concat([{gross:0,rest:0,raw:0,minutes:0}]);vectors.push([a.start,a.end,rest,step,split?1:0,...[parts[0].gross,parts[1].gross,parts[0].rest,parts[1].rest,parts[0].raw,parts[1].raw,parts[0].minutes,parts[1].minutes,d.net,d.credited]].join('\t'));
}
fs.mkdirSync('build',{recursive:true});fs.writeFileSync('build/settlement-vectors.tsv',vectors.join('\n')+'\n');
console.log('PASS: '+checks+' settlement, midnight allocation, rounding conservation, calendar, CSV safety and time-zone assertions');
