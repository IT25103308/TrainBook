const {test}=require('node:test');
const assert=require('node:assert/strict');
const {error,mount}=require('../../main/resources/static/js/account-fields.js');
test('international names and initials remain supported',()=>{
  for(const name of ['Anfas','O’Neil','Jean-Luc','José','සිල්වා','A. Silva']) assert.equal(error('name',name),'');
});
test('blank numeric markup and excessive names fail',()=>{
  for(const name of ['','  ','1234','<script>','---','A'.repeat(101)]) assert.notEqual(error('name',name),'');
});
test('local and international Sri Lankan phone forms agree',()=>{
  for(const phone of ['0771234567','+94771234567','0094771234567','077 123 4567','0112345678']) assert.equal(error('phone',phone),'');
});
test('invalid visible phone values fail',()=>{
  for(const phone of ['077123456','07712345678','0771234567abc','+44771234567','0121234567']) assert.notEqual(error('phone',phone),'');
});
test('password byte limit prevents invisible bcrypt truncation',()=>{
  assert.equal(error('password',' Pass123 '),''); assert.equal(error('password','A'.repeat(72)),'');
  for(const value of ['short','        ','A'.repeat(73),'සි'.repeat(20)]) assert.notEqual(error('password',value),'');
});
function field(kind,value) {
  const listeners={}, attrs={};
  const input={dataset:{accountField:kind},id:'phone',value,form:null,
    addEventListener(name,fn){listeners[name]=fn;},setCustomValidity(message){this.message=message;},
    setAttribute(k,v){attrs[k]=v;},removeAttribute(k){delete attrs[k];}};
  const hint={hidden:true,textContent:''};
  mount({querySelectorAll(){return [input];},getElementById(){return hint;}});
  return {input,listeners,hint,attrs};
}
test('correcting the visible value clears previous custom validity and inline error',()=>{
  const {input,listeners,hint}=field('phone','0771234567abc'); listeners.blur(); assert.ok(input.message); assert.equal(hint.hidden,false);
  input.value='0771234567'; listeners.input(); assert.equal(input.message,''); assert.equal(hint.hidden,true);
});
test('later invalid visible input is validated without keeping an earlier accepted state',()=>{
  const {input,listeners}=field('phone','0771234567');listeners.input();assert.equal(input.message,'');
  input.value='0771234567x';listeners.change();assert.ok(input.message);
});
