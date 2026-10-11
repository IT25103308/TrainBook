const {test}=require('node:test');const assert=require('node:assert/strict');
const {textError,durationError}=require('../../main/resources/static/js/operational-fields.js');
test('correcting the current visible operational name clears its earlier error',()=>{
 assert.ok(textError('<script>','name',true));assert.equal(textError('Udarata Menike','name',true),'');
 assert.ok(textError('123456','name',true));assert.equal(textError('කොළඹ','name',true),'');
});
test('train numbers require a digit and meaningful bounded characters',()=>{
 for(const value of ['ABC','<script>','T 101','A'.repeat(21)+'1'])assert.ok(textError(value,'trainNumber',true));
 assert.equal(textError('TR-1015','trainNumber',true),'');
});
test('optional blank platforms remain valid and rejected characters cannot leave a sticky error',()=>{
 assert.ok(textError('Platform 21','platform',false));assert.equal(textError('Platform 20','platform',false),'');assert.equal(textError('','platform',false),'');
});
test('same-day and overnight journeys share the same order and duration bounds',()=>{
 assert.ok(durationError('23:00','02:00','0'));assert.equal(durationError('23:00','02:00','1'),'');
 assert.ok(durationError('10:00','10:00','0'));assert.equal(durationError('10:00','10:00','1'),'');assert.ok(durationError('10:00','11:00','1'));
});
test('description validation uses the current plain text rather than input history',()=>{
 assert.ok(textError('<b>Maintenance</b>','note',true));assert.equal(textError('Brake inspection','note',true),'');
 assert.ok(textError('x'.repeat(301),'note',true));
});
