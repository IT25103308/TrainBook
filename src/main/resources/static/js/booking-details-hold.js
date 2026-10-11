(function (root) {
  'use strict';
  function mount(remainingMillis) {
    const form = document.getElementById('seatBookingForm');
    const fields = document.getElementById('detailsFields');
    const button = document.getElementById('saveDetailsButton');
    const timer = document.getElementById('detailsHoldTimer');
    const message = document.getElementById('detailsHoldMessage');
    if (!form || !fields || !button || !timer || !message) return;
    const startedAt = Date.now();
    let interval;
    function render() {
      const state = root.PaymentHold.snapshot('PENDING', Number(remainingMillis), Date.now() - startedAt);
      fields.disabled = !state.canPay;
      button.disabled = !state.canPay;
      timer.textContent = state.label;
      if (!state.canPay) {
        message.textContent = 'Your hold expired. Return to payment to select available seats again.';
        if (interval !== undefined) clearInterval(interval);
      }
      return state.canPay;
    }
    form.addEventListener('submit', function (event) { if (!render()) event.preventDefault(); });
    document.addEventListener('visibilitychange', render);
    root.addEventListener('pageshow', function (event) { if (event.persisted) root.location.reload(); });
    if (render()) interval = setInterval(render, 250);
  }
  const api = { mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.BookingDetailsHold = api;
}(typeof window !== 'undefined' ? window : globalThis));
