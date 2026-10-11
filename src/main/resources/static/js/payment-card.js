(function (root) {
  'use strict';
  const digits = value => String(value == null ? '' : value).replace(/[^0-9]/g, '');
  const position = (value, offset) => digits(String(value).slice(0, Number.isInteger(offset) ? Math.max(0, offset) : String(value).length)).length;
  function numeric(value, start, end, limit, grouped) {
    const raw = String(value == null ? '' : value), accepted = digits(raw).slice(0, limit);
    const formatted = grouped ? (accepted.match(/.{1,4}/g) || []).join(' ') : accepted;
    function caret(offset) {
      if (raw === formatted && Number.isInteger(offset)) return Math.max(0, Math.min(offset, formatted.length));
      const count = Math.min(position(raw, offset), accepted.length);
      return Math.min(formatted.length, count + (grouped && count ? Math.floor((count - 1) / 4) : 0));
    }
    return { value: formatted, start: caret(start), end: caret(end == null ? start : end),
      notice: digits(raw).length > limit ? 'Only the first ' + limit + ' digits were kept. Check the value shown.'
        : /[^0-9\s-]/.test(raw) ? 'Non-digit characters were ignored. Check the value shown.' : '' };
  }
  const format = (value, start, end) => numeric(value, start, end, 16, true);
  const formatCvv = (value, start, end) => numeric(value, start, end, 3, false);
  function numericInsertion(value, start, end, incoming, limit, grouped) {
    const current = digits(value).slice(0, limit);
    const from = Math.min(position(value, start), current.length), to = Math.min(position(value, end), current.length);
    const left = current.slice(0, from), right = current.slice(to), typed = digits(incoming);
    const accepted = typed.slice(0, Math.max(0, limit - left.length - right.length));
    // Rejected input must never delete a selected part of an accepted value.
    if (!accepted) {
      const result = numeric(value, start, end, limit, grouped);
      result.start = start; result.end = end;
      result.notice = typed.length || /[^0-9\s-]/.test(incoming) ? 'Extra or non-digit characters were ignored.' : '';
      return result;
    }
    const result = numeric(left + accepted + right, left.length + accepted.length, left.length + accepted.length, limit, grouped);
    result.notice = typed.length > accepted.length ? 'Extra digits were ignored. Check the value shown.'
      : /[^0-9\s-]/.test(incoming) ? 'Non-digit characters were ignored. Check the value shown.' : '';
    return result;
  }
  function boundedText(raw, limit) {
    let accepted = '';
    for (const character of String(raw)) { if (accepted.length + character.length > limit) break; accepted += character; }
    return accepted;
  }
  function nameError(value) {
    const text = String(value || '').trim();
    // Names vary internationally. Match the server's nonblank, 100-character limit.
    return !text || String(value).length > 100 || !/^[\p{L}\p{M} .'’-]+$/u.test(text) || !/\p{L}/u.test(text)
      ? 'Enter a cardholder name of 1–100 characters using letters, spaces, initials, apostrophes or hyphens.' : '';
  }
  function expiryErrors(month, year, now) {
    const errors = { month: '', year: '' };
    if (!/^(0[1-9]|1[0-2])$/.test(month)) errors.month = 'Choose a valid expiry month.';
    if (!/^[0-9]{4}$/.test(year)) errors.year = 'Choose a valid expiry year.';
    if (!errors.month && !errors.year && (Number(year) < now.year || Number(year) === now.year && Number(month) < now.month))
      errors.month = 'The card expiry date is in the past. Choose a current or future month.';
    return errors;
  }
  function mount(config) {
    const doc = root.document || (typeof document !== 'undefined' ? document : null);
    if (!doc) return;
    const form = doc.getElementById('paymentForm');
    if (!form || form.dataset.paymentInputsMounted) return;
    form.dataset.paymentInputsMounted = 'true';
    const validators = [], year = config && config.currentYear, month = config && config.currentMonth;
    function now() {
      if (Number.isInteger(year) && Number.isInteger(month)) return { year, month };
      const date = new Date(); return { year: date.getFullYear(), month: date.getMonth() + 1 };
    }
    function field(id, settings) {
      const input = doc.getElementById(id); if (!input) return;
      const error = doc.getElementById(id + 'Error'), notice = doc.getElementById(id + 'Notice');
      let showError = false, pending = null;
      function validate(show) {
        if (show) showError = true;
        const message = settings.error(input.value);
        // Blocking validity is based exclusively on the currently visible accepted value.
        input.setCustomValidity(message);
        if (message && showError) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
        if (error) { error.textContent = showError ? message : ''; error.hidden = !(showError && message); }
        return !message;
      }
      function apply(result) {
        input.value = result.value;
        input.setSelectionRange(Math.min(result.start, input.value.length), Math.min(result.end, input.value.length));
        if (notice) { notice.textContent = result.notice || ''; notice.hidden = !result.notice; }
        validate(false);
      }
      function normalize(show) { apply(settings.format(input.value, input.selectionStart, input.selectionEnd)); return validate(show); }
      validators.push(normalize);
      input.addEventListener('beforeinput', function (event) {
        pending = null; if (event.isComposing) return;
        const start = input.selectionStart, end = input.selectionEnd, value = input.value;
        if (event.inputType.startsWith('insert') && event.data != null) {
          const result = settings.insert(value, start, end, event.data); pending = result;
          const candidate = value.slice(0, start) + event.data + value.slice(end);
          if (event.cancelable && (result.notice || candidate.length > input.maxLength)) {
            event.preventDefault(); pending = null; apply(result);
          }
        } else if (settings.grouped && start === end && event.cancelable && event.inputType === 'deleteContentBackward' && start > 1 && value[start - 1] === ' ') {
          event.preventDefault(); apply(settings.format(value.slice(0, start - 2) + value.slice(start), start - 2));
        } else if (settings.grouped && start === end && event.cancelable && event.inputType === 'deleteContentForward' && value[start] === ' ' && start + 1 < value.length) {
          event.preventDefault(); apply(settings.format(value.slice(0, start) + value.slice(start + 2), start));
        }
      });
      input.addEventListener('input', function (event) {
        if (event && event.isComposing) return;
        if (pending) { const accepted = pending; pending = null; apply(accepted); } else normalize(false);
      });
      input.addEventListener('paste', function (event) {
        if (!event.clipboardData) return;
        event.preventDefault(); pending = null;
        apply(settings.insert(input.value, input.selectionStart, input.selectionEnd, event.clipboardData.getData('text/plain')));
      });
      input.addEventListener('compositionend', () => { pending = null; normalize(false); });
      input.addEventListener('change', () => { pending = null; normalize(false); });
      input.addEventListener('blur', () => { pending = null; normalize(true); });
      input.addEventListener('invalid', () => validate(true)); normalize(false);
    }
    field('cardNumber', { grouped: true, format,
      insert: (value, start, end, text) => numericInsertion(value, start, end, text, 16, true),
      error: value => /^[0-9]{4}( ?[0-9]{4}){3}$/.test(value) ? '' : 'Enter exactly 16 card digits.' });
    field('cvv', { format: formatCvv,
      insert: (value, start, end, text) => numericInsertion(value, start, end, text, 3, false),
      error: value => /^[0-9]{3}$/.test(value) ? '' : 'Enter exactly 3 CVV digits.' });
    field('cardHolderName', { error: nameError,
      format: (value, start, end) => {
        const text = boundedText(String(value).replace(/[\t\r\n]/g, ' '), 100);
        return { value: text, start: start == null ? text.length : Math.min(start, text.length), end: end == null ? start == null ? text.length : start : Math.min(end, text.length),
          notice: String(value).length > 100 ? 'Extra characters were ignored. Check the name shown.' : '' };
      },
      insert: (value, start, end, text) => {
        const left = value.slice(0, start), right = value.slice(end);
        const accepted = boundedText(String(text).replace(/[\t\r\n]/g, ' '), Math.max(0, 100 - left.length - right.length));
        if (!accepted) return { value, start, end, notice: 'Extra characters were ignored.' };
        return { value: left + accepted + right, start: left.length + accepted.length, end: left.length + accepted.length,
          notice: accepted.length < text.length ? 'Extra characters were ignored. Check the name shown.' : '' };
      }
    });
    const expiryMonth = doc.getElementById('expMonth'), expiryYear = doc.getElementById('expYear');
    if (expiryMonth && expiryYear) {
      const error = doc.getElementById('expiryError'); let show = false;
      function validateExpiry(reveal) {
        show = show || Boolean(reveal); const errors = expiryErrors(expiryMonth.value, expiryYear.value, now());
        [[expiryMonth, errors.month], [expiryYear, errors.year]].forEach(([input, message]) => {
          input.setCustomValidity(message);
          if (show && message) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
        });
        if (error) { error.textContent = show ? errors.month || errors.year : ''; error.hidden = !(show && (errors.month || errors.year)); }
        return !errors.month && !errors.year;
      }
      [expiryMonth, expiryYear].forEach(input => {
        input.addEventListener('input', () => validateExpiry(false)); input.addEventListener('change', () => validateExpiry(false));
        input.addEventListener('blur', () => validateExpiry(true)); input.addEventListener('invalid', () => validateExpiry(true));
      });
      validators.push(validateExpiry); validateExpiry(false);
    }
    function synchronize(show) { let valid = true; validators.forEach(validate => { if (!validate(show)) valid = false; }); return valid; }
    const button = doc.getElementById('payButton');
    if (button) button.addEventListener('click', () => synchronize(false), true);
    form.addEventListener('keydown', event => { if (event.key === 'Enter' && !event.isComposing) synchronize(false); }, true);
    form.addEventListener('submit', function (event) { if (!synchronize(true)) { event.preventDefault(); form.reportValidity(); } });
    if (root.addEventListener) root.addEventListener('pageshow', () => synchronize(false));
    return { synchronize };
  }
  const api = { format, formatCvv, numericInsertion, nameError, expiryErrors, mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.PaymentCard = api;
})(typeof window !== 'undefined' ? window : globalThis);
