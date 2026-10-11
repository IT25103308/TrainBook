const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const sourcePath = process.env.PAYMENT_CARD_SOURCE || path.resolve(__dirname, '../../main/resources/static/js/payment-card.js');
const api = require(sourcePath);

test('cardholder name uses meaningful current-value validation and allows supported punctuation',()=>{
  for(const name of ['1234','<script>','---']) assert.notEqual(api.nameError(name),'');
  for(const name of ["O'Connor",'A. B. Perera','Anne-Marie','අනුර පෙරේරා','அன்ஃபாஸ்']) assert.equal(api.nameError(name),'');
});

function element(value = '', maxLength = -1) {
  return { value, maxLength, selectionStart: value.length, selectionEnd: value.length,
    validityMessage: '', hidden: true, textContent: '', dataset: {}, attributes: {}, handlers: {},
    addEventListener(name, handler) { (this.handlers[name] ||= []).push(handler); },
    dispatch(name, event = {}) { (this.handlers[name] || []).forEach(handler => handler(event)); },
    setCustomValidity(message) { this.validityMessage = message; },
    setAttribute(name, value) { this.attributes[name] = value; },
    removeAttribute(name) { delete this.attributes[name]; },
    setSelectionRange(start, end) { this.selectionStart = start; this.selectionEnd = end; }
  };
}
function page(initial = '') {
  const elements = { paymentForm: element(), payButton: element(), cardNumber: element(initial, 19),
    cvv: element('123', 3), cardHolderName: element('Test Passenger', 100), expMonth: element('10'), expYear: element('2026') };
  for (const id of ['cardNumber', 'cvv', 'cardHolderName']) {
    elements[id + 'Error'] = element(); elements[id + 'Notice'] = element();
  }
  elements.expiryError = element();
  const form = elements.paymentForm;
  form.checkValidity = () => ['cardNumber', 'cvv', 'cardHolderName', 'expMonth', 'expYear'].every(id => !elements[id].validityMessage);
  form.reportValidity = () => { for (const id of ['cardNumber', 'cvv', 'cardHolderName', 'expMonth', 'expYear'])
    if (elements[id].validityMessage) elements[id].dispatch('invalid'); return form.checkValidity(); };
  const document = { getElementById(id) { return elements[id] || null; } };
  const window = { document, handlers: {}, addEventListener(name, handler) { this.handlers[name] = handler; } };
  vm.runInNewContext(fs.readFileSync(sourcePath, 'utf8'), { window, document });
  window.PaymentCard.mount({ currentYear: 2026, currentMonth: 10 });
  function edit(inputType, data = null, id = 'cardNumber', cancelable = true) {
    const input = elements[id]; let prevented = false;
    input.dispatch('beforeinput', { inputType, data, cancelable, isComposing: false,
      preventDefault() { prevented = true; } });
    if (prevented) return false;
    let start = input.selectionStart, end = input.selectionEnd;
    if (inputType === 'deleteContentBackward' && start === end) start = Math.max(0, start - 1);
    if (inputType === 'deleteContentForward' && start === end) end = Math.min(input.value.length, end + 1);
    const replacement = inputType.startsWith('insert') ? data : '';
    const next = input.value.slice(0, start) + replacement + input.value.slice(end);
    if (cancelable && next.length > input.maxLength) return false;
    input.value = next; input.setSelectionRange(start + replacement.length, start + replacement.length);
    input.dispatch('input', {}); return true;
  }
  function submit(mode = 'click') {
    if (mode === 'click') elements.payButton.dispatch('click');
    if (mode === 'enter') form.dispatch('keydown', { key: 'Enter', isComposing: false });
    // Browsers validate before dispatching submit. Capture handlers must clear stale errors first.
    if (!form.checkValidity()) { form.reportValidity(); return null; }
    const event = { defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
    form.dispatch('submit', event);
    return event.defaultPrevented ? null : Object.fromEntries(['cardNumber', 'cvv', 'cardHolderName', 'expMonth', 'expYear'].map(id => [id, elements[id].value]));
  }
  return { elements, window, input: elements.cardNumber, error: elements.cardNumberError,
    select(start, end = start, id = 'cardNumber') { elements[id].setSelectionRange(start, end); }, edit, submit,
    paste(text, id = 'cardNumber') { let prevented = false;
      elements[id].dispatch('paste', { clipboardData: { getData() { return text; } }, preventDefault() { prevented = true; } });
      assert.equal(prevented, true); },
    autofill(value, id = 'cardNumber', event = 'input') { const input = elements[id]; input.value = value;
      input.setSelectionRange(value.length, value.length); input.dispatch(event, {}); },
    change(id, value) { elements[id].value = value; elements[id].dispatch('change'); }
  };
}
const plain = '4111222233334444', grouped = '4111 2222 3333 4444';
function assertPayable(p, card = grouped) {
  assert.equal(p.input.value, card); assert.equal(p.input.validityMessage, '');
  assert.equal(p.error.hidden, true); assert.equal(p.input.attributes['aria-invalid'], undefined);
  const payload = p.submit(); assert.ok(payload, 'Accepted visible fields must allow submission'); assert.equal(payload.cardNumber, card);
}

test('letters after accepted 16 digits never block valid visible payment details', () => {
  const p = page(plain);
  for (const letter of 'xyz!') { p.edit('insertText', letter); assertPayable(p); }
});
test('oversized mixed paste uses the first 16 accepted digits and permits payment', () => {
  const p = page(); p.paste('x4111-2222a3333 4444 999'); assertPayable(p);
});
test('typing adds spaces automatically with the caret at the end', () => {
  const p = page(); for (const digit of plain) { p.edit('insertText', digit); assert.equal(p.input.selectionStart, p.input.value.length); }
  assertPayable(p);
});
test('group separators have no trailing space', () => {
  const p = page(); for (const digit of '4111') p.edit('insertText', digit);
  assert.equal(p.input.value, '4111'); p.edit('insertText', '2'); assert.equal(p.input.value, '4111 2');
});
for (const text of ['9', '99999', 'letters', '-']) test('extra typing is ignored after 16 digits: ' + text, () => {
  const p = page(plain); p.edit('insertText', text); assertPayable(p);
});
test('full-number insert in the middle retains accepted digits and caret', () => {
  const p = page(plain); p.select(6); p.edit('insertText', '9'); assertPayable(p); assert.equal(p.input.selectionStart, 6);
});
test('replacing a separator cannot add a seventeenth digit', () => {
  const p = page(plain); p.select(4, 5); p.edit('insertText', '9'); assertPayable(p); assert.equal(p.input.selectionEnd, 5);
});
for (const text of [plain, grouped, ' 4111-2222-3333-4444\n', plain + '56789', grouped + '56', 'a4111b2222c3333d4444z'])
  test('paste normalizes only accepted visible digits: ' + text.trim(), () => { const p = page(); p.paste(text); assertPayable(p); });
test('all-letter replacement paste does not erase an already valid number', () => {
  const p = page(plain); p.select(0, 19); p.paste('abc'); assertPayable(p); assert.equal(p.input.selectionEnd, 19);
});
test('oversized replacement paste replaces all selected digits with its first 16', () => {
  const p = page(plain); p.select(0, 19); p.paste('2344555556778888999'); assertPayable(p, '2344 5555 5677 8888');
});
test('partial paste preserves accepted digits to the right of the selection', () => {
  const p = page(plain); p.select(5, 9); p.paste('987654321'); assertPayable(p, '4111 9876 3333 4444');
});
test('replacing one digit preserves order and caret', () => {
  const p = page(plain); p.select(6, 7); p.edit('insertText', '9'); assertPayable(p, '4111 2922 3333 4444'); assert.equal(p.input.selectionStart, 7);
});
test('backspace after a separator removes the previous digit', () => {
  const p = page(plain); p.select(5); p.edit('deleteContentBackward'); assert.equal(p.input.value, '4112 2223 3334 444'); assert.equal(p.input.selectionStart, 3); assert.equal(p.submit(), null);
});
test('delete before a separator removes the next digit', () => {
  const p = page(plain); p.select(4); p.edit('deleteContentForward'); assert.equal(p.input.value, '4111 2223 3334 444'); assert.equal(p.input.selectionStart, 4);
});
test('short visible card blocks payment, then correcting it clears the error', () => {
  const p = page(plain); p.edit('insertText', 'x'); p.edit('deleteContentBackward'); assert.equal(p.submit(), null);
  assert.match(p.input.validityMessage, /16/); assert.equal(p.error.hidden, false); p.edit('insertText', '4'); assertPayable(p);
});
for (const event of ['input', 'change', 'compositionend']) test('fallback ' + event + ' normalizes oversized mixed input without stale errors', () => {
  const p = page(); p.autofill('x' + plain + '999', 'cardNumber', event); assertPayable(p);
});
test('noncancelable insertion still preserves the accepted full value', () => {
  const p = page(plain); p.select(6); p.edit('insertText', '9x', 'cardNumber', false); assertPayable(p);
});
test('composition completion validates only the accepted digits', () => {
  const p = page(plain); p.input.value += '字'; p.input.dispatch('input', { isComposing: true });
  p.input.dispatch('compositionend'); assertPayable(p);
});
test('a script-provided value is synchronized before clicking pay', () => {
  const p = page(); p.input.value = plain + 'extra'; assertPayableAfterSubmit(p);
});
function assertPayableAfterSubmit(p) { const payload = p.submit(); assert.ok(payload); assert.equal(payload.cardNumber, grouped); }
test('Enter clears a stale custom error before native form validation', () => {
  const p = page(plain); p.input.setCustomValidity('An earlier rejected edit'); assert.ok(p.submit('enter')); assert.equal(p.input.validityMessage, '');
});
test('the submit handler validates current visible values before continuing', () => {
  const p = page(plain); const event = { defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
  p.elements.paymentForm.dispatch('submit', event); assert.equal(event.defaultPrevented, false);
});
for (const text of ['4', 'abc', '0000', '1234extra']) test('full CVV rejects extra input without blocking the accepted three digits: ' + text, () => {
  const p = page(plain); p.edit('insertText', text, 'cvv'); assert.equal(p.elements.cvv.value, '123'); assertPayable(p);
});
test('CVV paste takes three digits consistently, including the decline trigger', () => {
  const p = page(plain); p.select(0, 3, 'cvv'); p.paste('x0000', 'cvv'); assert.equal(p.submit().cvv, '000');
  p.select(0, 3, 'cvv'); p.paste('1234extra', 'cvv'); assert.equal(p.submit().cvv, '123');
});
test('short visible CVV blocks payment and clears its error after correction', () => {
  const p = page(plain); p.autofill('12', 'cvv'); assert.equal(p.submit(), null);
  assert.equal(p.elements.cvvError.hidden, false); p.edit('insertText', '3', 'cvv'); assertPayable(p);
});
test('CVV autofill and composition completion keep only accepted digits', () => {
  const p = page(plain); p.autofill('x1234', 'cvv', 'compositionend'); assert.equal(p.submit().cvv, '123');
});
test('rejected name overflow cannot block the accepted 100 characters', () => {
  const p = page(plain); p.autofill('A'.repeat(100), 'cardHolderName'); p.edit('insertText', 'extra', 'cardHolderName');
  assert.equal(p.submit().cardHolderName.length, 100);
});
test('name paste bounds the visible name and handles multiline clipboard text', () => {
  const p = page(plain); p.select(0, p.elements.cardHolderName.value.length, 'cardHolderName'); p.paste('Anne\nMarie ' + 'A'.repeat(120), 'cardHolderName');
  assert.equal(p.submit().cardHolderName.length, 100); assert.ok(p.elements.cardHolderName.value.startsWith('Anne Marie '));
});
test('blank and whitespace-only visible names cannot submit, Unicode names can', () => {
  const p = page(plain); p.autofill('   ', 'cardHolderName'); assert.equal(p.submit(), null);
  p.autofill('李', 'cardHolderName'); assert.equal(p.submit().cardHolderName, '李');
  p.autofill("Anne-Marie O’Neill", 'cardHolderName'); assertPayable(p);
});
test('past expiry blocks, and correcting the year clears the month error', () => {
  const p = page(plain); p.change('expMonth', '01'); assert.equal(p.submit(), null); assert.match(p.elements.expMonth.validityMessage, /past/);
  p.change('expYear', '2027'); assertPayable(p); assert.equal(p.elements.expiryError.hidden, true);
});
test('correcting the month clears past-expiry validity on both controls', () => {
  const p = page(plain); p.change('expMonth', '09'); assert.equal(p.submit(), null); p.change('expMonth', '10'); assertPayable(p);
});
test('both expiry selections are required and recover after correction', () => {
  const p = page(plain); p.change('expMonth', ''); p.change('expYear', ''); assert.equal(p.submit(), null);
  p.change('expYear', '2026'); p.change('expMonth', '12'); assertPayable(p);
});
test('mounting twice cannot register duplicate submission handlers', () => {
  const p = page(plain); p.window.PaymentCard.mount(); assert.equal(p.elements.paymentForm.handlers.submit.length, 1);
});
test('accepted decline card suffix remains unchanged by ignored characters', () => {
  const p = page(); p.paste('4111222233330000letters999'); assert.equal(p.submit().cardNumber, '4111 2222 3333 0000');
});
test('formatting incomplete input does not invent missing digits', () => {
  assert.equal(api.format('41112').value, '4111 2'); assert.equal(api.format('').value, '');
  assert.equal(api.format('x' + plain + '999').value, grouped); assert.equal(api.formatCvv('x1234').value, '123');
});
