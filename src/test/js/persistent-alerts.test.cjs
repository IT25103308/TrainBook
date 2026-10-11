const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
test('hold and field-error notices marked persistent are not scheduled for flash dismissal',()=>{
  const scheduled=[];
  const persistent={hasAttribute:n=>n==='data-persistent',querySelector(){throw new Error('Persistent notice must not receive a dismissal handler');}};
  const flash={hasAttribute:()=>false,querySelector:()=>null};
  const context={document:{addEventListener(){},querySelectorAll:()=>[persistent,flash]},setTimeout:f=>scheduled.push(f)};
  vm.createContext(context);vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/main.js'),'utf8'),context);
  context.initAlertDismissal();assert.equal(scheduled.length,1);
});
