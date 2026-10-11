const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const hold=require('../../main/resources/static/js/payment-hold.js');
function page(remaining) {
  let now=100000,tick;const events={},elements={};
  for(const id of ['seatBookingForm','detailsFields','saveDetailsButton','detailsHoldTimer','detailsHoldMessage']) elements[id]={events:{},addEventListener(n,f){this.events[n]=f;}};
  const window={PaymentHold:hold,events:{},addEventListener(n,f){this.events[n]=f;},location:{reload(){this.reloaded=true;}}};
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/booking-details-hold.js'),'utf8'),{
    window,document:{getElementById:id=>elements[id],addEventListener:(n,f)=>events[n]=f},Date:{now:()=>now},setInterval:f=>(tick=f,1),clearInterval(){tick=null;}});
  window.BookingDetailsHold.mount(remaining);
  return {elements,window,advance(ms){now+=ms;if(tick)tick();},visibility(){events.visibilitychange();},
    submit(){let prevented=false;elements.seatBookingForm.events.submit({preventDefault(){prevented=true;}});return !prevented;}};
}
test('detail editing countdown uses the remaining hold rather than starting ten minutes',()=>{
  const p=page(180000);assert.equal(p.elements.detailsHoldTimer.textContent,'03:00');assert.equal(p.submit(),true);
  p.advance(180001);assert.equal(p.elements.detailsFields.disabled,true);assert.equal(p.elements.saveDetailsButton.disabled,true);assert.equal(p.submit(),false);
});
test('invalid initial expiry fails closed and history restoration requests a fresh snapshot',()=>{
  const p=page(NaN);assert.equal(p.elements.detailsHoldTimer.textContent,'EXPIRED');assert.equal(p.submit(),false);
  p.window.events.pageshow({persisted:true});assert.equal(p.window.location.reloaded,true);
});
