const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const data=JSON.parse(fs.readFileSync('app/src/main/assets/locales.json','utf8'));
assert.deepEqual(Object.keys(data.zh).sort(),Object.keys(data.en).sort());
for(const key of Object.keys(data.en)){
 assert.ok(data.en[key].length,key);
 assert.deepEqual((data.zh[key].match(/\{\d+\}/g)||[]).sort(),(data.en[key].match(/\{\d+\}/g)||[]).sort(),'placeholders: '+key);
 assert.equal(/[\u3400-\u9fff]/.test(data.en[key]),false,'English translation contains Chinese: '+key);
}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||undefined,headless:true,args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:390,height:844},locale:'en-US'});
 const page=await context.newPage();const errors=[];page.on('console',m=>{if(m.type()==='log')console.log(m.text())});page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  if(!sessionStorage.getItem('seeded')){const d=new Date(),date=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-15';sessionStorage.setItem('testdb',JSON.stringify([{id:'legacy-record',date,start:'18:00',end:'20:30',nextDay:false,break:30,type:'工作日',note:'Legacy note'}]));sessionStorage.setItem('seeded','1');}
  window.LocalApp={getLanguage(){return JSON.stringify({ok:true,language:sessionStorage.getItem('language')||'en'})},setLanguage(language){if(window.failLanguage)return JSON.stringify({ok:false,message:'语言选择保存失败，请重试'});sessionStorage.setItem('language',language);return JSON.stringify({ok:true,language})},list(){return JSON.stringify({ok:true,records:JSON.parse(sessionStorage.getItem('testdb')||'[]')})},save(raw){let a=JSON.parse(sessionStorage.getItem('testdb')||'[]');const r=JSON.parse(raw);if(!r.id)r.id='record-'+Date.now();a=a.filter(x=>x.id!==r.id);a.push(r);sessionStorage.setItem('testdb',JSON.stringify(a));return JSON.stringify({ok:true,message:'已保存在本机'})},delete(id){sessionStorage.setItem('testdb',JSON.stringify(JSON.parse(sessionStorage.getItem('testdb')).filter(x=>x.id!==id)));return JSON.stringify({ok:true,message:'记录已删除'})},exportFile(kind){window.testExport={kind,language:sessionStorage.getItem('language')||'en'}},importFile(){window.testImport=true}};
 });
 await page.goto('file://'+path.resolve('app/src/main/assets/index.html'));
 assert.equal(await page.locator('html').getAttribute('lang'),'en');assert.equal(await page.locator('h1').innerText(),'Overtime Notes');assert.equal(await page.locator('#totalHours').innerText(),'2');assert.equal(await page.locator('.badge').innerText(),'Workday');assert.match(await page.locator('.record').getAttribute('aria-label'),/^Edit entry/);
 const before=await page.evaluate(()=>sessionStorage.getItem('testdb'));
 async function noChineseVisible(){const cjk=await page.evaluate(()=>{const out=[],walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let node;while(node=walker.nextNode()){const el=node.parentElement;if(el&&el.getClientRects().length&&!['SCRIPT','STYLE','OPTION'].includes(el.tagName)&&/[\u3400-\u9fff]/.test(node.textContent)&&!el.closest('#languageToggle,#languageSettingTarget'))out.push(node.textContent.trim());}return out;});assert.deepEqual(cjk,[],'untranslated visible text');}
 await page.evaluate(()=>document.getElementById('toast').classList.remove('show'));await noChineseVisible();
 await page.locator('#languageToggle').click();assert.equal(await page.locator('html').getAttribute('lang'),'zh-CN');assert.equal(await page.locator('.badge').innerText(),'工作日');assert.equal(await page.evaluate(()=>sessionStorage.getItem('testdb')),before);
 await page.reload();assert.equal(await page.locator('html').getAttribute('lang'),'zh-CN');
 await page.locator('#navData').click();await page.locator('#languageSetting').click();assert.equal(await page.locator('html').getAttribute('lang'),'en');await noChineseVisible();assert.match(await page.locator('#allCountText').innerText(),/1 entry/);
 await page.locator('#backup').click();assert.equal(await page.locator('#confirmTitle').innerText(),'Export full backup');assert.match(await page.locator('#confirmText').innerText(),/Downloads/);await noChineseVisible();await page.locator('#acceptConfirm').click();assert.deepEqual(await page.evaluate(()=>window.testExport),{kind:'json',language:'en'});
 await page.locator('#csv').click();await page.locator('#acceptConfirm').click();assert.equal((await page.evaluate(()=>window.testExport)).kind,'csv');
 await page.locator('#restore').click();assert.match(await page.locator('#confirmText').innerText(),/existing IDs|Existing IDs/);await page.locator('#acceptConfirm').click();assert.equal(await page.evaluate(()=>window.testImport),true);
 await page.evaluate(()=>window.nativeEvent(false,'文件保存失败，请重试'));assert.match(await page.locator('#toast').innerText(),/Could not save the file/);
 await page.locator('#navHome').click();await page.locator('.record').click();assert.equal(await page.locator('#editorTitle').innerText(),'Edit overtime');assert.equal(await page.locator('#type option:checked').innerText(),'Workday');assert.equal(await page.locator('#type').inputValue(),'工作日');assert.equal(await page.locator('#duration').innerText(),'2 h 0 min');await noChineseVisible();
 await page.locator('#break').fill('999');await page.locator('#save').click();assert.match(await page.locator('#formError').innerText(),/Break time must be shorter/);await noChineseVisible();
 await page.locator('#break').fill('15');await page.locator('#save').click();assert.equal(await page.locator('#totalExact').innerText(),'2 h 15 min');assert.equal(JSON.parse(await page.evaluate(()=>sessionStorage.getItem('testdb')))[0].type,'工作日');
 await page.locator('.record').click();await page.locator('#delete').click();assert.equal(await page.locator('#confirmTitle').innerText(),'Delete this entry?');await noChineseVisible();await page.locator('#cancelConfirm').click();await page.locator('#closeEditor').click();
 await page.locator('#add').click();await page.locator('#start').fill('23:00');await page.locator('#end').fill('02:00');await page.locator('#nextDay').check();await page.locator('#break').fill('20');assert.equal(await page.locator('#duration').innerText(),'2 h 40 min');
 await page.locator('#closeEditor').click();assert.equal(await page.locator('#confirmTitle').innerText(),'Discard changes?');await page.locator('#acceptConfirm').click();
 for(const lang of ['zh','en']){
  const current=await page.locator('html').getAttribute('lang');if(!current.startsWith(lang))await page.locator('#languageToggle').click();
  for(const width of [320,360,390,768]){
   await page.setViewportSize({width,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,lang+' home '+width);
   await page.locator('#navData').click();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,lang+' data '+width);
   await page.locator('#navHome').click();await page.locator('#add').click();assert.equal(await page.evaluate(()=>{const sheet=document.querySelector('.sheet');const ok=sheet.scrollWidth<=sheet.clientWidth;if(!ok)console.log(JSON.stringify([...sheet.querySelectorAll('*')].map(el=>({tag:el.tagName,id:el.id,cls:el.className,left:el.getBoundingClientRect().left,right:el.getBoundingClientRect().right})).filter(r=>r.right>innerWidth)));return ok}),true,lang+' form '+width);await page.locator('#closeEditor').click();
  }
 }
 await page.setViewportSize({width:390,height:844});await page.evaluate(()=>document.getElementById('toast').classList.remove('show'));await page.screenshot({path:'build/ui-en-home.png',fullPage:true});await page.locator('#navData').click();await page.screenshot({path:'build/ui-en-data.png',fullPage:true});await page.locator('#add').click();await page.screenshot({path:'build/ui-en-form.png',fullPage:false});await page.locator('#closeEditor').click();
 await page.evaluate(()=>window.failLanguage=true);await page.locator('#languageToggle').click();assert.equal(await page.locator('html').getAttribute('lang'),'en');assert.match(await page.locator('#toast').innerText(),/Could not save your language/);
 await page.evaluate(()=>window.failLanguage=false);await page.reload();assert.equal(await page.locator('html').getAttribute('lang'),'en');
 assert.equal(await page.locator('#navHome>span[data-i18n]').evaluate(el=>getComputedStyle(el).fontSize),'10px');assert.equal(await page.locator('#add>span[data-i18n]').evaluate(el=>getComputedStyle(el).fontSize),'14px');assert.deepEqual(errors,[]);await context.close();
 for(const locale of ['zh-CN','en-GB','fr-FR']){const c=await browser.newContext({locale});const p=await c.newPage();await p.addInitScript(()=>{window.LocalApp={list(){return '{"ok":true,"records":[]}'}}});await p.goto('file://'+path.resolve('app/src/main/assets/index.html'));assert.equal(await p.locator('html').getAttribute('lang'),locale.startsWith('zh')?'zh-CN':'en');await c.close();}
 await browser.close();console.log('PASS: dictionary completeness, live toggle, saved choice reload, legacy record stability, English UI/errors/confirmations, export controls, 8 language/width combinations, system-language defaults');
})().catch(e=>{console.error(e);process.exit(1)});
