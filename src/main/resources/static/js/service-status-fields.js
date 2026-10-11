(function(root){
  'use strict';
  function mount(){
    const form=document.getElementById('serviceStatusForm'),status=document.getElementById('status'),delay=document.getElementById('delayMinutes');
    if(!form || !status || !delay)return;
    function check(){
      const active=status.value==='DELAYED';delay.disabled=!active;delay.required=active;delay.min=active?'1':'0';
      const n=Number(delay.value);delay.setCustomValidity(active && (!delay.value || !Number.isInteger(n) || n<1 || n>1440)?'Enter a whole delay between 1 and 1440 minutes.':'');
    }
    status.addEventListener('change',check);delay.addEventListener('input',check);form.addEventListener('submit',check);check();
  }
  if(typeof module!=='undefined' && module.exports)module.exports={mount};else root.ServiceStatusFields={mount};
}(typeof window!=='undefined'?window:globalThis));
