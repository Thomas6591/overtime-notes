// API 28's original WebView 66 cannot parse nullish coalescing or optional chaining.
const acorn=require('acorn'),fs=require('node:fs'),path=require('node:path');
const directory=path.resolve(__dirname,'../app/src/main/assets');
for(const f of fs.readdirSync(directory).filter(f=>f.endsWith('.js'))){acorn.parse(fs.readFileSync(path.join(directory,f),'utf8'),{ecmaVersion:2018});}
console.log('PASS: all shipped JavaScript uses ES2018-compatible syntax');
