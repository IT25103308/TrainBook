const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const sourcePath = path.resolve(__dirname, '../../main/resources/static/js/payment-hold.js');
const { snapshot } = require(sourcePath);

test('uses actual remaining hold, including part of a second', () => {
  assert.deepEqual(snapshot('PENDING', 303500, 0), { state: 'PENDING', canPay: true, label: '05:04' });
  assert.equal(snapshot('PENDING', 303500, 1500).label, '05:02');
});
test('catches up after an inactive tab instead of subtracting one tick', () => {
  assert.equal(snapshot('PENDING', 180000, 90000).label, '01:30');
});
test('reaches expired at the exact deadline', () => {
  assert.deepEqual(snapshot('PENDING', 1000, 1000), { state: 'EXPIRED', canPay: false, label: 'EXPIRED' });
});
test('paid and cancelled states never allow payment even with time remaining', () => {
  assert.equal(snapshot('CONFIRMED', 500000, 0).label, 'PAID');
  assert.equal(snapshot('CANCELLED', 500000, 0).canPay, false);
});
test('invalid or missing remaining time cannot enable payment', () => {
  for (const remaining of [NaN, Infinity, undefined, -1, 0]) assert.equal(snapshot('PENDING', remaining, 0).canPay, false);
});

function page(config) {
  let now = 100000;
  let tick;
  let cleared = false;
  let reloads = 0;
  const events = {};
  const elements = Object.fromEntries(['paymentForm', 'paymentFields', 'payButton', 'timerDisplay',
    'paymentHoldTitle', 'paymentHoldMessage', 'paymentStatusBadge', 'paymentMethodCard',
    'cancelReservationForm', 'checkoutRecoveryActions', 'selectSeatsAgain', 'searchAnotherTrain',
    'journeyRecoveryMessage', 'checkoutSearchLink'].map(id => [id, {
      disabled: true, hidden: true, className: '', textContent: '', addEventListener(name, handler) { this[name] = handler; }
    }]));
  const document = {
    getElementById(id) { return elements[id]; },
    addEventListener(name, handler) { events[name] = handler; }
  };
  elements.paymentForm.checkValidity = () => true;
  elements.paymentForm.reportValidity = () => false;
  const window = { addEventListener(name, handler) { events[name] = handler; }, location: { reload() { reloads++; } } };
  vm.runInNewContext(fs.readFileSync(sourcePath, 'utf8'), {
    document, window, Date: { now: () => now },
    setInterval(handler) { tick = handler; return 1; },
    clearInterval() { cleared = true; }
  });
  window.PaymentHold.mount(config);
  return { elements, events, advance(ms) { now += ms; if (tick && !cleared) tick(); },
    setNow(value) { now = value; }, get cleared() { return cleared; }, get reloads() { return reloads; } };
}

test('rendered page starts from server value and visibly counts down', () => {
  const p = page({ state: 'PENDING', remainingMillis: 180000 });
  assert.equal(p.elements.timerDisplay.textContent, '03:00');
  assert.equal(p.elements.payButton.disabled, false);
  p.advance(1000);
  assert.equal(p.elements.timerDisplay.textContent, '02:59');
});
test('expiry disables fields and blocks a direct form submission', () => {
  const p = page({ state: 'PENDING', remainingMillis: 1000 });
  p.advance(1000);
  assert.equal(p.elements.timerDisplay.textContent, 'EXPIRED');
  assert.equal(p.elements.paymentFields.disabled, true);
  assert.equal(p.elements.payButton.disabled, true);
  let blocked = false;
  p.elements.paymentForm.submit({ preventDefault() { blocked = true; } });
  assert.equal(blocked, true);
  assert.equal(p.cleared, true);
});
test('expired state cannot become payable after clock moves backwards', () => {
  const p = page({ state: 'PENDING', remainingMillis: 1000 });
  p.advance(1000);
  p.setNow(100000);
  p.events.visibilitychange();
  assert.equal(p.elements.payButton.disabled, true);
});
test('paid and cancelled pages show terminal state with disabled payment', () => {
  for (const state of ['CONFIRMED', 'CANCELLED', 'EXPIRED', 'UNAVAILABLE']) {
    const p = page({ state, remainingMillis: 0 });
    assert.equal(p.elements.payButton.disabled, true);
    assert.equal(p.elements.timerDisplay.textContent, state === 'CONFIRMED' ? 'PAID' : state);
  }
});
test('browser history restores request a fresh snapshot', () => {
  const p = page({ state: 'PENDING', remainingMillis: 180000 });
  p.events.pageshow({ persisted: false });
  assert.equal(p.reloads, 0);
  p.events.pageshow({ persisted: true });
  assert.equal(p.reloads, 1);
});
test('a refreshed page uses the new smaller server value', () => {
  const first = page({ state: 'PENDING', remainingMillis: 180000 });
  const refreshed = page({ state: 'PENDING', remainingMillis: 120000 });
  assert.equal(first.elements.timerDisplay.textContent, '03:00');
  assert.equal(refreshed.elements.timerDisplay.textContent, '02:00');
});

test('live expiry hides payment and cancellation controls and shows fresh-seat actions', () => {
  const p = page({ state: 'PENDING', remainingMillis: 1000, recoveryEligible: true, departureRemainingMillis: 60000 });
  assert.equal(p.elements.paymentMethodCard.hidden, false);
  assert.equal(p.elements.cancelReservationForm.hidden, false);
  assert.equal(p.elements.checkoutRecoveryActions.hidden, true);
  p.advance(1000);
  assert.equal(p.elements.paymentMethodCard.hidden, true);
  assert.equal(p.elements.cancelReservationForm.hidden, true);
  assert.equal(p.elements.checkoutRecoveryActions.hidden, false);
  assert.equal(p.elements.selectSeatsAgain.hidden, false);
  assert.equal(p.elements.searchAnotherTrain.className, 'btn btn-secondary');
  assert.equal(p.elements.checkoutSearchLink.hidden, true);
});

test('released expiry stays labelled expired after a refreshed server snapshot', () => {
  const p = page({ state: 'EXPIRED_RELEASED', remainingMillis: 0, recoveryEligible: true, departureRemainingMillis: 60000 });
  assert.equal(p.elements.timerDisplay.textContent, 'EXPIRED');
  assert.equal(p.elements.paymentHoldTitle.textContent, 'Seat Hold Expired');
  assert.match(p.elements.paymentHoldMessage.textContent, /seats were released/);
  assert.equal(p.elements.payButton.disabled, true);
});

test('passenger cancellation is distinct from a legacy unknown cancellation', () => {
  const manual = page({ state: 'CANCELLED_BY_PASSENGER', remainingMillis: 0 });
  assert.equal(manual.elements.timerDisplay.textContent, 'CANCELLED');
  assert.equal(manual.elements.paymentHoldTitle.textContent, 'Reservation Cancelled by You');
  const legacy = page({ state: 'CANCELLED', remainingMillis: 0 });
  assert.equal(legacy.elements.paymentHoldTitle.textContent, 'Reservation Cancelled');
  assert.doesNotMatch(legacy.elements.paymentHoldMessage.textContent, /You cancelled|expired/);
});

test('a departure passing while the page is open changes recovery to search another train', () => {
  const p = page({ state: 'PENDING', remainingMillis: 2000, recoveryEligible: true, departureRemainingMillis: 1000 });
  p.advance(2000);
  assert.equal(p.elements.selectSeatsAgain.hidden, true);
  assert.equal(p.elements.searchAnotherTrain.className, 'btn btn-primary');
  assert.match(p.elements.journeyRecoveryMessage.textContent, /already passed/);
});

test('an already expired page catches a later departure on returning to the tab', () => {
  const p = page({ state: 'EXPIRED', remainingMillis: 0, recoveryEligible: true, departureRemainingMillis: 1000 });
  assert.equal(p.elements.selectSeatsAgain.hidden, false);
  p.advance(1000);
  p.events.visibilitychange();
  assert.equal(p.elements.selectSeatsAgain.hidden, true);
});

test('unavailable journey offers search and its server-provided explanation', () => {
  const p = page({ state: 'CANCELLED_BY_PASSENGER', remainingMillis: 0,
    recoveryEligible: false, departureRemainingMillis: 0, recoveryMessage: 'This train is unavailable.' });
  assert.equal(p.elements.selectSeatsAgain.hidden, true);
  assert.equal(p.elements.searchAnotherTrain.className, 'btn btn-primary');
  assert.equal(p.elements.journeyRecoveryMessage.textContent, 'This train is unavailable.');
});

test('paid checkout hides payment and recovery without treating it as a cancelled hold', () => {
  const p = page({ state: 'CONFIRMED', remainingMillis: 0, recoveryEligible: false });
  assert.equal(p.elements.paymentMethodCard.hidden, true);
  assert.equal(p.elements.cancelReservationForm.hidden, true);
  assert.equal(p.elements.checkoutRecoveryActions.hidden, true);
  assert.equal(p.elements.paymentHoldTitle.textContent, 'Payment Completed');
});

test('valid submission locks the payment action without disabling submitted fields', () => {
  const p = page({ state: 'PENDING', remainingMillis: 180000 });
  let blocked = false; p.elements.paymentForm.submit({ preventDefault() { blocked = true; } });
  assert.equal(blocked, false); assert.equal(p.elements.paymentFields.disabled, false);
  assert.equal(p.elements.payButton.disabled, true); assert.equal(p.elements.cancelReservationForm.hidden, true);
  p.advance(1000); assert.equal(p.elements.payButton.disabled, true);
  assert.equal(p.elements.timerDisplay.textContent, '02:59');
  p.elements.paymentForm.submit({ preventDefault() { blocked = true; } });
  assert.equal(blocked, true, 'A second submit must not produce a duplicate payment');
});
test('invalid visible fields do not lock the action while the passenger corrects them', () => {
  const p = page({ state: 'PENDING', remainingMillis: 180000 });
  p.elements.paymentForm.checkValidity = () => false;
  let blocked = false; p.elements.paymentForm.submit({ preventDefault() { blocked = true; } });
  assert.equal(blocked, true); assert.equal(p.elements.payButton.disabled, false);
  p.elements.paymentForm.checkValidity = () => true;
  blocked = false; p.elements.paymentForm.submit({ preventDefault() { blocked = true; } });
  assert.equal(blocked, false); assert.equal(p.elements.payButton.disabled, true);
});
test('an earlier input-validation cancellation does not enter processing state', () => {
  const p = page({ state: 'PENDING', remainingMillis: 180000 });
  p.elements.paymentForm.submit({ defaultPrevented: true });
  assert.equal(p.elements.payButton.disabled, false); assert.equal(p.elements.cancelReservationForm.hidden, false);
});

test('cancelled train services cannot pay even when the saved hold has time remaining', () => {
  const p = page({ state: 'SERVICE_CANCELLED', remainingMillis: 180000, recoveryEligible: false });
  assert.equal(p.elements.timerDisplay.textContent, 'SERVICE CANCELLED');
  assert.equal(p.elements.paymentHoldTitle.textContent, 'Train Service Cancelled');
  assert.equal(p.elements.paymentMethodCard.hidden, true); assert.equal(p.elements.selectSeatsAgain.hidden, true);
  let blocked = false; p.elements.paymentForm.submit({ preventDefault() { blocked = true; } });
  assert.equal(blocked, true);
});
