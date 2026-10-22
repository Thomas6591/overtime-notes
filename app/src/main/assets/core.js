(function(root){
'use strict';
const DAY=86400000;
function epoch(date,time){if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!/^\d{2}:\d{2}$/.test(time))throw Error('请填写日期和开始、结束时间');const [y,m,d]=date.split('-').map(Number),[h,n]=time.split(':').map(Number);const ms=Date.UTC(y,m-1,d,h,n),dt=new Date(ms);if(y<2000||y>2100||dt.getUTCFullYear()!==y||dt.getUTCMonth()!==m-1||dt.getUTCDate()!==d||h>23||n>59)throw Error('请选择2000—2100年的有效日期和时间');return ms;}
function calculate(r){const start=epoch(r.date,r.start),end=epoch(r.date,r.end)+(r.nextDay?DAY:0),gross=(end-start)/60000;const rest=Number(r.break);if(!Number.isInteger(rest)||rest<0)throw Error('休息时长须为不小于0的整数分钟');if(gross<=0)throw Error('结束时间需晚于开始时间；跨午夜请勾选“次日”');if(gross>1440)throw Error('单条记录不能超过24小时');if(rest>=gross)throw Error('休息时长必须小于本次总时长');return {start,end,minutes:gross-rest};}
function overlap(a,b){const x=calculate(a),y=calculate(b);return x.start<y.end&&y.start<x.end;}
function hours(min){return (min/60).toFixed(2).replace(/\.?0+$/,'');}
function exact(min){return Math.floor(min/60)+'小时'+min%60+'分';}
function summarize(records,month){const selected=records.filter(r=>r.date.slice(0,7)===month);let total=0;const days=new Set(),types={'工作日':0,'休息日':0,'节假日':0},daily={};selected.forEach(r=>{const n=calculate(r).minutes;total+=n;days.add(r.date);types[r.type]=(types[r.type]||0)+n;if(!daily[r.date])daily[r.date]={'工作日':0,'休息日':0,'节假日':0};daily[r.date][r.type]+=n;});return {selected,total,days:days.size,count:selected.length,types,daily};}
const core={epoch,calculate,overlap,hours,exact,summarize};if(typeof module!=='undefined'&&module.exports)module.exports=core;else root.Overtime=core;
})(typeof window!=='undefined'?window:globalThis);
