const test = require('node:test');
const assert = require('node:assert/strict');
const details = require('../../main/resources/static/js/passenger-details.js');
for (const [type, number] of [['NIC','200300901022'],['NIC','855420159v'],['NIC','855420159x'],
  ['SRI_LANKAN_PASSPORT','n1234567'],['SRI_LANKAN_PASSPORT','OL1234567'],['FOREIGN_PASSPORT','AB1234567'],['FOREIGN_PASSPORT','012345678']]) {
  test(`${type} accepts ${number}`,()=>assert.equal(details.documentError(type,number),''));
}
for (const [type, number] of [['NIC','20000000000000000000'],['NIC','123456789'],['NIC','1234567890'],['NIC','123456789A'],['NIC','2003 00901022'],
  ['SRI_LANKAN_PASSPORT','N123456'],['SRI_LANKAN_PASSPORT','N12345678'],['SRI_LANKAN_PASSPORT','12345678'],
  ['FOREIGN_PASSPORT','AB123'],['FOREIGN_PASSPORT','AB-123456'],['FOREIGN_PASSPORT','123456789012345678901'],['UNKNOWN','123456789']]) {
  test(`${type} rejects ${number}`,()=>assert.notEqual(details.documentError(type,number),''));
}
for (const [input, expected] of [['0771234567','0771234567'],['+94771234567','0771234567'],['0094771234567','0771234567'],
  ['+94 77 123 4567','0771234567'],['077-123-4567','0771234567'],['0112345678','0112345678'],['+94112345678','0112345678'],['0741943329','0741943329']]) {
  test(`normalizes ${input}`,()=>assert.equal(details.normalizePhone(input),expected));
}
for (const input of ['', '077123456', '07712345678', '+940771234567', '+44771234567', '0991234567', 'abc0771234567', '0771234567 ext 1', '++94771234567', '1234567890']) {
  test(`rejects malformed phone ${input}`,()=>assert.equal(details.normalizePhone(input),null));
}
for (const name of ['AnfasMS', 'A. B. Perera', "O'Connor", 'Anne-Marie', 'අනුර පෙරේරා', 'அன்ஃபாஸ்']) {
  test(`accepts name ${name}`,()=>assert.equal(details.nameError(name),''));
}
for (const name of ['', ' ', 'A', '12345', '---', 'Anfas123', '<script>']) {
  test(`rejects name ${name}`,()=>assert.notEqual(details.nameError(name),''));
}

function page() {
  const fs=require('node:fs'), vm=require('node:vm'), path=require('node:path');
  const elements={};
  for(const id of ['seatBookingForm','documentType','passengerName','passengerNic','contactPhone',
    'documentNumberLabel','documentNumberHint','passengerNameError','passengerNicError','contactPhoneError']) {
    elements[id]={ id,value:'',textContent:'',hidden:true,maxLength:id==='passengerName'?100:20,events:{},attrs:{},
      selectionStart:0,selectionEnd:0,validity:'',
      get validationMessage(){return this.validity;},
      addEventListener(name,callback){this.events[name]=callback;},setCustomValidity(message){this.validity=message;},
      setAttribute(name,value){this.attrs[name]=value;},removeAttribute(name){delete this.attrs[name];},
      setSelectionRange(start,end){this.selectionStart=start;this.selectionEnd=end;},focus(){this.focused=true;},reportValidity(){this.reported=true;}
    };
  }
  elements.documentType.value='NIC';
  const window={}; vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/passenger-details.js'),'utf8'),
    {window,document:{getElementById(id){return elements[id];}}}); window.PassengerDetails.mount();
  return {elements, type(value){elements.documentType.value=value;elements.documentType.events.change();},
    enter(id,value){elements[id].value=value;elements[id].selectionStart=elements[id].selectionEnd=value.length;elements[id].events.input();},
    submit(){let prevented=false;elements.seatBookingForm.events.submit({preventDefault(){prevented=true;}});return !prevented;}
  };
}
test('mounting and switching document type updates the actual label, limit, pattern and hint',()=>{
  const p=page(); assert.equal(p.elements.passengerNic.maxLength,12); assert.equal(p.elements.passengerNic.validity,'');
  p.type('SRI_LANKAN_PASSPORT'); assert.equal(p.elements.documentNumberLabel.textContent,'Passport Number'); assert.equal(p.elements.passengerNic.maxLength,9);
  p.type('FOREIGN_PASSPORT'); assert.equal(p.elements.passengerNic.maxLength,20); assert.equal(p.elements.passengerNic.pattern,'[A-Za-z0-9]{6,20}');
});
test('valid foreign numeric passport can be submitted and correction clears a prior phone error',()=>{
  const p=page(); p.type('FOREIGN_PASSPORT'); p.enter('passengerName','AnfasMS'); p.enter('passengerNic','012345678'); p.enter('contactPhone','abc');
  assert.equal(p.submit(),false); assert.equal(p.elements.contactPhoneError.hidden,false);
  p.enter('contactPhone','+94771234567'); assert.equal(p.elements.contactPhone.validity,''); assert.equal(p.submit(),true);
});
test('lowercase document letters are capitalized without losing the cursor',()=>{
  const p=page(); p.enter('passengerNic','855420159v'); assert.equal(p.elements.passengerNic.value,'855420159V'); assert.equal(p.elements.passengerNic.selectionStart,10);
});
test('an oversized pasted document is rejected as a whole rather than truncated to a valid NIC',()=>{
  const p=page(); let prevented=false;
  p.elements.passengerNic.events.paste({clipboardData:{getData(){return '20000000000000000000';}},preventDefault(){prevented=true;}});
  assert.equal(prevented,true); assert.equal(p.elements.passengerNic.value,''); assert.notEqual(p.elements.passengerNic.validity,'');
  p.enter('passengerNic','200300901022'); assert.equal(p.elements.passengerNic.validity,''); assert.equal(p.elements.passengerNicError.hidden,true);
});
test('rejected oversized paste cannot block an already valid visible NIC or phone',()=>{
  const p=page();p.enter('passengerName','Test Passenger');p.enter('passengerNic','200300901022');p.enter('contactPhone','0771234567');
  for(const id of ['passengerNic','contactPhone']) {
    let rejected=false;p.elements[id].events.paste({clipboardData:{getData(){return '9'.repeat(40);}},preventDefault(){rejected=true;}});
    assert.equal(rejected,true);assert.equal(p.elements[id].validity,'');assert.equal(p.submit(),true);
  }
  assert.equal(p.elements.passengerNic.value,'200300901022');assert.equal(p.elements.contactPhone.value,'0771234567');
});
