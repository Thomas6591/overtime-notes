(function(){
'use strict';
const data=window.TRANSLATIONS;
let language=(navigator.language||'en').toLowerCase().startsWith('zh')?'zh':'en';
try{if(window.LocalApp){const r=JSON.parse(window.LocalApp.getLanguage());if(r.ok&&['zh','en'].includes(r.language))language=r.language;}}catch(e){}
function t(key,...args){if(key==='version.records'&&Number(args[0])===1)key='version.oneRecord';let value=data[language][key]||data.en[key]||key;args.forEach((a,i)=>{value=value.split('{'+i+'}').join(String(a));});return value;}
function message(value){const key=data.messages[value];if(key)return t(key);return language==='en'&&/[\u3400-\u9fff]/.test(value)?t('error.generic'):value;}
function apply(){document.documentElement.lang=language==='zh'?'zh-CN':'en';document.title=t('app.name');document.querySelectorAll('[data-i18n]').forEach(el=>el.textContent=t(el.dataset.i18n));document.querySelectorAll('[data-i18n-aria]').forEach(el=>el.setAttribute('aria-label',t(el.dataset.i18nAria)));document.querySelectorAll('[data-i18n-placeholder]').forEach(el=>el.placeholder=t(el.dataset.i18nPlaceholder));document.getElementById('languageToggle').textContent=language==='zh'?'EN':'中文';document.getElementById('languageSettingTarget').textContent=language==='zh'?'English':'中文';}
function set(next){if(!['zh','en'].includes(next))throw Error(t('error.language'));if(window.LocalApp){const r=JSON.parse(window.LocalApp.setLanguage(next));if(!r.ok)throw Error(message(r.message||t('error.language')));}language=next;apply();}
function exact(minutes){return t('unit.exact',Math.floor(minutes/60),minutes%60);}
function type(value){return t({'工作日':'type.work','休息日':'type.rest','节假日':'type.holiday'}[value]||value);}
function month(value){const [y,m]=value.split('-').map(Number);return new Intl.DateTimeFormat(language==='zh'?'zh-CN':'en-US',{year:'numeric',month:'short'}).format(new Date(y,m-1,1));}
function weekday(date){return new Intl.DateTimeFormat(language==='zh'?'zh-CN':'en-US',{weekday:'short'}).format(new Date(date+'T12:00:00'));}
window.I18n={t,message,apply,set,exact,type,month,weekday,get language(){return language;}};
})();
