const {test}=require('node:test');
const assert=require('node:assert/strict');
const {signInTarget}=require('../../main/resources/static/js/seat-selection.js');
test('guest sign-in keeps the journey and the currently chosen class',()=>{
  const target=new URL(signInTarget('/booking/sign-in?scheduleId=2&date=2026-10-06&seatClass=FIRST','SECOND'),'http://trainbook.local');
  assert.equal(target.pathname,'/booking/sign-in');assert.equal(target.searchParams.get('scheduleId'),'2');
  assert.equal(target.searchParams.get('date'),'2026-10-06');assert.equal(target.searchParams.get('seatClass'),'SECOND');
});
test('sign-in helper refuses unrelated paths and unsupported classes',()=>{
  assert.equal(signInTarget('/elsewhere','FIRST'),null);
  assert.equal(signInTarget('/booking/sign-in?scheduleId=2','THIRD'),null);
});
