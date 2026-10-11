(function(root){
  'use strict';
  function textError(value,rule,required){
    const text=String(value||'').trim();if(!text)return required?'Complete this field.':'';
    if(rule==='trainNumber' && (!/^[A-Za-z0-9][A-Za-z0-9-]{0,19}$/.test(text)||!/[0-9]/.test(text)))return 'Use 1–20 letters/digits/hyphens, including at least one digit.';
    if(rule==='name' && (text.length>100||!/[\p{L}]/u.test(text)||!/^[\p{L}\p{M}\p{N} .’'()&-]+$/u.test(text)))return 'Use letters, spaces or ordinary name punctuation, up to 100 characters.';
    if(rule==='platform'&&!/^(?:Platform\s+)?(?:[1-9]|1[0-9]|20)$/i.test(text))return 'Choose a platform number from 1 to 20.';
    if(rule==='note'&&(text.length>300||/[<>\p{Cc}]/u.test(text)))return 'Use a short plain-text description, up to 300 characters.';
    return '';
  }
  function minutes(value){if(!/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/.test(value||''))return null;const parts=value.split(':');return +parts[0]*60 + +parts[1];}
  function durationError(departure,arrival,offset){const a=minutes(departure),b=minutes(arrival);if(a===null||b===null)return '';const duration=b+Number(offset)*1440-a;return ![0,1].includes(Number(offset))||duration<=0||duration>1440?'Arrival must be after departure, within 24 hours. Choose next day for an overnight service.':'';}
  function mount(doc){
    doc.querySelectorAll('form[data-confirm]').forEach(form=>form.addEventListener('submit',event=>{if(!root.confirm(form.dataset.confirm))event.preventDefault();}));
    doc.querySelectorAll('[data-fare-preview]').forEach(form=>form.addEventListener('submit',async event=>{
      event.preventDefault();if(!form.reportValidity())return;const button=form.querySelector('[type="submit"]'),output=form.querySelector('[data-fare-result]');button.disabled=true;output.textContent='Calculating…';
      try{const target=new URL(form.dataset.farePreviewUrl,root.location.href);target.searchParams.set('date',form.elements.namedItem('date').value);target.searchParams.set('seatClass',form.elements.namedItem('seatClass').value);
        const response=await root.fetch(target,{headers:{Accept:'application/json'}});if(!response.headers.get('content-type')?.includes('application/json'))throw new Error('Sign in again to view the saved fare.');const data=await response.json();if(!response.ok)throw new Error(data.error||'Check the selected date and class.');
        output.textContent='LKR '+Number(data.perSeatFare).toLocaleString('en-LK',{minimumFractionDigits:2,maximumFractionDigits:2})+' per seat · '+data.departure+' → '+data.arrival+(data.runs?' · Service currently open':' · Service unavailable on this date/time');
      }catch(error){output.textContent=error.message==='Unexpected token'?'Sign in again to view the saved fare.':error.message||'Could not read the saved fare. Please try again.';}finally{button.disabled=false;}
    }));
    doc.querySelectorAll('[data-ops-form]').forEach(form=>{
      const field=name=>form.elements.namedItem(name);
      function refresh(){
        const maintenance=field('kind')&&field('kind').value==='MAINTENANCE';const timetable=form.querySelector('[data-timetable-fields]');
        if(field('label')){field('label').maxLength=maintenance?300:100;field('label').dataset.rule='note';}
        if(timetable){timetable.disabled=maintenance;timetable.hidden=maintenance;}
        const demand=(field('farePolicy')?field('farePolicy').value:form.dataset.farePolicy)==='DEMAND';
        form.querySelectorAll('[data-fixed-fare]').forEach(input=>{input.disabled=maintenance||demand;input.setCustomValidity('');});
        form.querySelectorAll('input,select,textarea').forEach(input=>input.setCustomValidity(''));
        form.querySelectorAll('[data-rule]').forEach(input=>{if(!input.disabled)input.setCustomValidity(textError(input.value,input.dataset.rule,input.required));});
        const first=field('firstClassSeats'),second=field('secondClassSeats');if(first&&second){
          const total=Number(first.value)+Number(second.value);const display=form.querySelector('[data-total-seats]');if(display)display.textContent=Number.isFinite(total)?String(total):'—';
          if(first.value!==''&&second.value!==''&&total===0)first.setCustomValidity('Provide seats in at least one supported class.');
        }
        if(form.dataset.opsForm==='route'&&field('origin').value.trim()&&field('origin').value.trim().toLowerCase()===field('destination').value.trim().toLowerCase())field('destination').setCustomValidity('Choose different origin and destination stations.');
        if(!maintenance&&field('arrivalTime')&&field('departureTime')&&field('arrivalDayOffset'))field('arrivalTime').setCustomValidity(durationError(field('departureTime').value,field('arrivalTime').value,field('arrivalDayOffset').value));
        let previousStop=0;const baseDeparture=field('departureTime')?minutes(field('departureTime').value):null;
        const journeyArrival=field('arrivalTime')?minutes(field('arrivalTime').value):null;
        const journeyLength=baseDeparture!==null&&journeyArrival!==null?journeyArrival+Number(field('arrivalDayOffset').value)*1440-baseDeparture:null;
        const stopStations=new Set();const routeChoice=field('routeId');if(routeChoice&&routeChoice.selectedOptions[0])routeChoice.selectedOptions[0].textContent.split(' → ').forEach(name=>stopStations.add(name.trim().toLowerCase()));
        form.querySelectorAll('[data-stop-row]').forEach(row=>{
          const inputs=Array.from(row.querySelectorAll('input'));const used=inputs.some(i=>i.name.endsWith('.station')?i.value.trim():i.name.endsWith('Time')&&i.value);
          inputs.forEach(i=>{i.required=used;if(!used)i.setCustomValidity('');});
          if(!used)return;const station=inputs.find(i=>i.name.endsWith('.station')),arrival=inputs.find(i=>i.name.endsWith('.arrivalTime')),departure=inputs.find(i=>i.name.endsWith('.departureTime'));
          const normalizedStation=station.value.trim().toLowerCase();if(normalizedStation&&stopStations.has(normalizedStation))station.setCustomValidity('Each stop must be distinct from the route endpoints and other stops.');stopStations.add(normalizedStation);
          const arr=minutes(arrival.value),dep=minutes(departure.value),day=Number(row.querySelector('select').value);
          if(arr!==null&&dep!==null&&baseDeparture!==null&&journeyLength!==null){const offsetArrival=arr+day*1440-baseDeparture,offsetDeparture=dep+day*1440-baseDeparture+(dep<arr?1440:0);
            if(offsetArrival<=previousStop||offsetDeparture<offsetArrival||offsetDeparture>=journeyLength)arrival.setCustomValidity('Put stops in journey order, with departure at or after arrival and before the final destination time.');previousStop=offsetDeparture;
          }
        });
      }
      form.addEventListener('input',refresh);form.addEventListener('change',refresh);
      form.addEventListener('click',event=>{if(event.target.closest('[type="submit"]'))refresh();});
      form.addEventListener('keydown',event=>{if(event.key==='Enter')refresh();});form.addEventListener('submit',event=>{refresh();if(!form.checkValidity()){event.preventDefault();form.reportValidity();}});
      const stops=form.querySelector('[data-stop-list]');const add=form.querySelector('[data-add-stop]');
      function renumber(){if(!stops)return;stops.querySelectorAll('[data-stop-row]').forEach((row,index)=>{
        row.querySelectorAll('[name]').forEach(input=>{const suffix=input.name.slice(input.name.indexOf(']')+1);input.name='stops['+index+']'+suffix;input.id='stops'+index+suffix;});
        row.querySelectorAll('label').forEach(label=>{label.htmlFor=label.htmlFor.replace(/^stops\d+/,'stops'+index);});
      });if(add)add.disabled=stops.children.length>=20;refresh();}
      if(add)add.addEventListener('click',()=>{
        const original=stops.querySelector('[data-stop-row]');if(!original)return;const clone=original.cloneNode(true);
        clone.querySelectorAll('input').forEach(i=>{i.value='';i.required=false;i.setCustomValidity('');});clone.querySelectorAll('select').forEach(i=>{i.value='0';});stops.append(clone);renumber();
      });
      if(stops)stops.addEventListener('click',event=>{const remove=event.target.closest('[data-remove-stop]');if(!remove)return;
        if(stops.children.length===1){remove.closest('[data-stop-row]').querySelectorAll('input').forEach(i=>{i.value='';i.required=false;});}else remove.closest('[data-stop-row]').remove();renumber();
      });refresh();
    });
  }
  const api={textError,durationError,mount};if(typeof module!=='undefined'&&module.exports)module.exports=api;
  root.OperationalFields=api;if(root.document)mount(root.document);
})(typeof window!=='undefined'?window:globalThis);
