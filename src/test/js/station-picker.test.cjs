const test = require('node:test');
const assert = require('node:assert/strict');
const { matching, canonical, enhance } = require('../../main/resources/static/js/station-picker.js');

class Element {
  constructor(tag, doc) { this.tagName=tag; this.ownerDocument=doc; this.children=[]; this.listeners={}; this.attrs={}; this.dataset={}; this.value=''; this.hidden=false; this.validationMessage=''; }
  setAttribute(name,value) { this.attrs[name]=String(value); }
  removeAttribute(name) { delete this.attrs[name]; }
  getAttribute(name) { return this.attrs[name]; }
  append(...nodes) { nodes.forEach(node=>{node.parentElement=this; this.children.push(node);}); }
  replaceChildren() { this.children=[]; }
  addEventListener(name,fn) { (this.listeners[name] ||= []).push(fn); }
  fire(name,options={}) { const event={prevented:false, preventDefault(){this.prevented=true;},...options}; (this.listeners[name]||[]).forEach(fn=>fn(event)); return event; }
  focus() { if(this.ownerDocument.activeElement!==this){ this.ownerDocument.activeElement=this; this.fire('focus'); } }
  setCustomValidity(message) { this.validationMessage=message; }
  reportValidity() { return !this.validationMessage; }
  scrollIntoView() {}
}
function fixture(value='') {
  const doc={ createElement(tag){return new Element(tag,this);} };
  const form=new Element('form',doc); const wrapper=new Element('div',doc); const select=new Element('select',doc);
  select.form=form; select.id='originStation'; select.value=value;
  select.options=['','Colombo Fort','Kandy','New Admin Station','Galle'].map(value=>({value})); wrapper.append(select);
  return { ...enhance(select), form, doc };
}
test('filters without changing the saved station names',()=>assert.deepEqual(matching(['Colombo Fort','Kandy','Galle'],'OL'),['Colombo Fort']));
test('only an exact station name is accepted, with case and surrounding whitespace normalized',()=>{
  assert.equal(canonical(['Colombo Fort','Kandy'],' kANDY '),'Kandy'); assert.equal(canonical(['Colombo Fort'],'Colombo'),'');
});
test('enhancement preserves the native select and its submitted value',()=>{
  const f=fixture('Kandy'); assert.equal(f.select.hidden,true); assert.equal(f.input.id,'originStation');
  assert.equal(f.select.id,'originStationSource'); assert.equal(f.input.value,'Kandy'); assert.equal(f.input.validationMessage,'');
});
test('focus opens choices including stations supplied by the admin route catalogue',()=>{
  const f=fixture(); f.input.focus(); assert.equal(f.list.hidden,false); assert.equal(f.list.children.length,4);
  assert.equal(f.list.children[2].textContent,'New Admin Station'); assert.equal(f.input.getAttribute('aria-expanded'),'true');
});
test('typing filters the dropdown and unknown text cannot become a submitted station',()=>{
  const f=fixture('Kandy'); f.input.value='New'; f.input.fire('input');
  assert.equal(f.list.children.length,1); assert.equal(f.select.value,''); assert.notEqual(f.input.validationMessage,'');
});
test('clicking a match selects the stored canonical station',()=>{
  const f=fixture(); f.input.focus(); f.list.children[1].fire('click');
  assert.equal(f.select.value,'Kandy'); assert.equal(f.input.value,'Kandy'); assert.equal(f.list.hidden,true); assert.equal(f.input.validationMessage,'');
});
test('down arrow and Enter choose a station without submitting the form',()=>{
  const f=fixture(); f.input.focus(); f.input.fire('keydown',{key:'ArrowDown'}); f.input.fire('keydown',{key:'ArrowDown'});
  assert.equal(f.input.getAttribute('aria-activedescendant'),'originStationOption1');
  assert.equal(f.input.fire('keydown',{key:'Enter'}).prevented,true); assert.equal(f.select.value,'Kandy');
});
test('Escape closes choices and preserves an existing selection',()=>{
  const f=fixture('Kandy'); f.input.focus(); f.input.fire('keydown',{key:'ArrowDown'}); f.input.fire('keydown',{key:'Escape'});
  assert.equal(f.list.hidden,true); assert.equal(f.select.value,'Kandy'); assert.equal(f.input.getAttribute('aria-activedescendant'),undefined);
});
test('empty result has useful feedback and cannot be chosen by Enter',()=>{
  const f=fixture(); f.input.value='Unknown Station'; f.input.fire('input'); f.input.fire('keydown',{key:'ArrowDown'});
  assert.equal(f.list.children[0].textContent,'No matching stations'); assert.equal(f.input.fire('keydown',{key:'Enter'}).prevented,false);
  assert.equal(f.form.fire('submit').prevented,true); assert.equal(f.select.value,'');
});
test('an exact typed name is accepted and canonicalized on submit',()=>{
  const f=fixture(); f.input.value=' colombo fort '; f.input.fire('input');
  assert.equal(f.form.fire('submit').prevented,false); assert.equal(f.select.value,'Colombo Fort'); assert.equal(f.input.value,'Colombo Fort');
});
test('a browser change event synchronizes an autofilled exact name and clears stale validity',()=>{
  const f=fixture(); f.input.value='Kandy'; f.input.fire('change');
  assert.equal(f.select.value,'Kandy'); assert.equal(f.input.validationMessage,'');
});
test('correcting an invalid selection clears its error rather than trapping submission',()=>{
  const f=fixture(); f.input.value='Bogus'; f.input.fire('input'); f.input.fire('blur'); assert.equal(f.error.hidden,false);
  f.input.value='Galle'; f.input.fire('input'); assert.equal(f.error.hidden,true); assert.equal(f.input.validationMessage,''); assert.equal(f.form.fire('submit').prevented,false);
});
test('arrow toggle shows all stations even after a narrowed search',()=>{
  const f=fixture(); f.input.value='Kan'; f.input.fire('input'); f.toggle.fire('click'); f.toggle.fire('click');
  assert.equal(f.list.children.length,4); assert.equal(f.list.hidden,false);
});
test('Tab keeps normal focus navigation and does not accept a highlighted suggestion',()=>{
  const f=fixture(); f.input.focus(); f.input.fire('keydown',{key:'ArrowDown'});
  assert.equal(f.input.fire('keydown',{key:'Tab'}).prevented,false); assert.equal(f.list.hidden,true); assert.equal(f.select.value,'');
});
