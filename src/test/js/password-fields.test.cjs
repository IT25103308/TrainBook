const test=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path'), vm=require('node:vm');
const source=path.resolve(__dirname,'../../main/resources/static/js/password-fields.js');
const {mismatch}=require(source);
function element(id,value='') {return {id,value,type:'password',hidden:true,dataset:{},attributes:{},events:{},
 addEventListener(name,fn){(this.events[name]||=[]).push(fn);}, dispatch(name,event={}){(this.events[name]||[]).forEach(fn=>fn(event));},
 setAttribute(n,v){this.attributes[n]=v;},removeAttribute(n){delete this.attributes[n];},setCustomValidity(v){this.error=v;},reportValidity(){this.reported=true;}};}
function page(confirm=true) {
 const password=element('password','SecurePass123'), confirmation=confirm?element('confirmPassword',''):null;
 const error=element('confirmPasswordError'), form=element('form'), submit=element('submit');
 form.querySelector=()=>submit; if(confirmation) confirmation.form=form;
 const buttons=[password,confirmation].filter(Boolean).map(input=>{const b=element(input.id+'Toggle');b.dataset.passwordToggle=input.id;b.slash={hidden:true};b.querySelector=()=>b.slash;return b;});
 const doc={querySelectorAll:()=>buttons,getElementById(id){return {password,confirmPassword:confirmation,confirmPasswordError:error}[id];}};
 const window={document:doc,events:{},addEventListener(n,f){this.events[n]=f;}};
 vm.runInNewContext(fs.readFileSync(source,'utf8'),{window});
 return {password,confirmation,error,form,submit,buttons,window,change(input,value){input.value=value;input.dispatch('input');}};
}
test('visibility toggles never change the accepted password value',()=>{const p=page(false), b=p.buttons[0];assert.equal(b.hidden,false);b.dispatch('click');assert.equal(p.password.type,'text');assert.equal(p.password.value,'SecurePass123');assert.equal(b.attributes['aria-label'],'Hide password');assert.equal(b.attributes['aria-pressed'],'true');b.dispatch('click');assert.equal(p.password.type,'password');assert.equal(p.password.value,'SecurePass123');});
test('password and confirmation have independent eye controls',()=>{const p=page();p.buttons[1].dispatch('click');assert.equal(p.password.type,'password');assert.equal(p.confirmation.type,'text');assert.equal(p.buttons[1].attributes['aria-label'],'Hide confirm password');});
test('returning through history masks visible passwords without erasing them',()=>{const p=page();p.buttons.forEach(b=>b.dispatch('click'));p.window.events.pageshow();assert.equal(p.password.type,'password');assert.equal(p.confirmation.type,'password');assert.equal(p.password.value,'SecurePass123');});
test('an actual mismatch blocks, then correcting the accepted value clears the error',()=>{const p=page();p.change(p.confirmation,'different');p.confirmation.dispatch('blur');assert.equal(p.confirmation.error,'Passwords do not match.');assert.equal(p.error.hidden,false);assert.equal(p.confirmation.attributes['aria-invalid'],'true');p.change(p.confirmation,'SecurePass123');assert.equal(p.confirmation.error,'');assert.equal(p.error.hidden,true);assert.equal(p.confirmation.attributes['aria-invalid'],undefined);});
test('editing the original password rechecks confirmation',()=>{const p=page();p.change(p.confirmation,'SecurePass123');p.change(p.password,'ChangedPass123');assert.match(p.confirmation.error,/do not match/);p.change(p.confirmation,'ChangedPass123');assert.equal(p.confirmation.error,'');});
test('spaces and case are significant; passwords are never reformatted',()=>{assert.equal(mismatch(' SecurePass123 ','SecurePass123'),'Passwords do not match.');assert.equal(mismatch('SecurePass123','securePass123'),'Passwords do not match.');assert.equal(mismatch(' SecurePass123 ',' SecurePass123 '),'');});
test('blank confirmation leaves native required validation active',()=>{assert.equal(mismatch('SecurePass123',''),'');});
test('click and Enter synchronize autofilled visible values',()=>{const p=page();p.confirmation.value='SecurePass123';p.submit.dispatch('click');assert.equal(p.confirmation.error,'');p.password.value='newPassword123';p.form.dispatch('keydown',{key:'Enter'});assert.match(p.confirmation.error,/do not match/);});
test('a submitted mismatch is cancelled and reported',()=>{const p=page();p.confirmation.value='wrong';let blocked=false;p.form.dispatch('submit',{preventDefault(){blocked=true;}});assert.equal(blocked,true);assert.equal(p.confirmation.reported,true);});
test('mounting twice never doubles a visibility toggle',()=>{const p=page();p.window.PasswordFields.mount();p.buttons[0].dispatch('click');assert.equal(p.password.type,'text');assert.equal(p.buttons[0].events.click.length,1);});
