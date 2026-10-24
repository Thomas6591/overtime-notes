// Browser-only bridge simulation. Native SQLite is tested separately on Android.
module.exports=function mockBridge(){
const read=(key,fallback)=>JSON.parse(sessionStorage.getItem(key)||JSON.stringify(fallback)),write=(key,value)=>sessionStorage.setItem(key,JSON.stringify(value)),ok=value=>JSON.stringify(Object.assign({ok:true},value)),no=message=>JSON.stringify({ok:false,message});
let sequence=0;
window.LocalApp={
 getLanguage(){return ok({language:sessionStorage.getItem('language')||window.testLanguage||'zh'});},
 setLanguage(language){if(window.failLanguage)return no('语言选择保存失败，请重试');sessionStorage.setItem('language',language);return ok({language});},
 list(){return ok({records:read('testdb',[])});},
 getState(){return ok({records:read('testdb',[]),trash:read('trash',[]),templates:read('templates',[]),preferences:read('preferences',{}),lastBackup:sessionStorage.getItem('lastBackup')||''});},
 save(raw){const r=JSON.parse(raw),a=read('testdb',[]);if(!r.id)r.id='test-'+Date.now()+'-'+(++sequence);write('testdb',a.filter(x=>x.id!==r.id).concat(r));return ok({record:r,message:'已保存在本机'});},
 saveBatch(raw){const a=read('testdb',[]),results=[];for(const r of JSON.parse(raw)){if(a.some(x=>window.Overtime.overlap(x,r)))results.push({date:r.date,ok:false,message:'error.overlap'});else{r.id='batch-'+Date.now()+'-'+(++sequence);a.push(r);results.push({date:r.date,ok:true});}}write('testdb',a);return ok({results});},
 delete(id){const a=read('testdb',[]),r=a.find(x=>x.id===id);write('trash',read('trash',[]).concat({record:r,deletedAt:Date.now(),expiresAt:Date.now()+30*86400000}));write('testdb',a.filter(x=>x.id!==id));return ok({message:'trash.moved'});},
 restoreRecord(id){const a=read('testdb',[]),tr=read('trash',[]),entry=tr.find(x=>x.record.id===id);if(a.some(x=>window.Overtime.overlap(x,entry.record)))return no('trash.conflict');write('testdb',a.concat(entry.record));write('trash',tr.filter(x=>x.record.id!==id));return ok({message:'trash.restored'});},
 removeTrash(id){write('trash',id==='*'?[]:read('trash',[]).filter(x=>x.record.id!==id));return ok({});},
 saveTemplate(raw){const x=JSON.parse(raw),a=read('templates',[]);if(!x.name.trim())return no('template.nameError');if(!x.id)x.id='template-'+(++sequence);write('templates',a.filter(t=>t.id!==x.id).concat(x));return ok({});},
 deleteTemplate(id){write('templates',read('templates',[]).filter(x=>x.id!==id));return ok({});},
 setPreferences(raw){if(window.failPreferences)return no('error.preferences');write('preferences',JSON.parse(raw));return ok({});},
 exportFile(kind){window.testExport={kind,language:sessionStorage.getItem('language')||window.testLanguage||'zh'};},
 importFile(){window.testImport=true;},
 confirmImport(){window.importApplied=true;return ok({added:2,duplicates:1,conflicts:1,templatesAdded:0,trashAdded:0});},
 cancelImport(){window.importCancelled=true;}
};
};
