(function (root) {
  'use strict';
  const documents = {
    NIC: { pattern: /^(?:[0-9]{9}[VX]|[0-9]{12})$/, htmlPattern: '(?:[0-9]{9}[VvXx]|[0-9]{12})', max: 12,
      hint: '12 digits, or 9 digits followed by V or X.', example: 'e.g. 200300901022 or 855420159V' },
    SRI_LANKAN_PASSPORT: { pattern: /^[A-Z]{1,2}[0-9]{7}$/, htmlPattern: '[A-Za-z]{1,2}[0-9]{7}', max: 9,
      hint: '1 or 2 letters followed by 7 digits, as printed on your passport.', example: 'e.g. N1234567' },
    FOREIGN_PASSPORT: { pattern: /^[A-Z0-9]{6,20}$/, htmlPattern: '[A-Za-z0-9]{6,20}', max: 20,
      hint: '6–20 letters or digits, exactly as printed. Do not include spaces or punctuation.', example: 'e.g. AB1234567' }
  };
  function documentError(type, value) {
    const rule = documents[type];
    if (!rule) return 'Choose a document type.';
    return rule.pattern.test(String(value || '').trim().toUpperCase()) ? '' : rule.hint;
  }
  function nameError(value) {
    const name = String(value || '').trim();
    return name.length >= 2 && name.length <= 100 && /^[\p{L}\p{M} .'’-]+$/u.test(name) && /\p{L}/u.test(name)
      ? '' : 'Use 2–100 characters: letters, spaces, initials, apostrophes or hyphens.';
  }
  function normalizePhone(value) {
    const raw = String(value || '').trim();
    if (raw.length > 20 || !/^[+0-9 -]+$/.test(raw)) return null;
    let number = raw.replace(/[ -]/g, '');
    if (number.startsWith('+94')) number = '0' + number.slice(3);
    else if (number.startsWith('0094')) number = '0' + number.slice(4);
    return /^0(?:7[0-9]|11|21|23|24|25|26|27|31|32|33|34|35|36|37|38|41|45|47|51|52|54|55|57|63|65|66|67|81|91)[0-9]{7}$/.test(number) ? number : null;
  }
  function mount() {
    const form = document.getElementById('seatBookingForm');
    if (!form) return;
    const type = document.getElementById('documentType');
    const fields = ['passengerName', 'passengerNic', 'contactPhone'].map(id => document.getElementById(id));
    const touched = new Set();
    function message(field, text, show) {
      field.setCustomValidity(text);
      const element = document.getElementById(field.id + 'Error');
      element.textContent = show ? text : '';
      element.hidden = !show || !text;
      if (show && text) field.setAttribute('aria-invalid', 'true');
      else field.removeAttribute('aria-invalid');
    }
    function check(field, show) {
      const text = field.id === 'passengerName' ? nameError(field.value)
        : field.id === 'passengerNic' ? documentError(type.value, field.value)
        : normalizePhone(field.value) ? '' : 'Use a 10-digit Sri Lankan number or its +94 / 0094 equivalent. Spaces and hyphens are allowed.';
      message(field, text, show);
      return !text;
    }
    function setDocumentRule() {
      const rule = documents[type.value];
      if (!rule) return;
      const field = fields[1];
      field.maxLength = rule.max;
      field.pattern = rule.htmlPattern;
      field.placeholder = rule.example;
      document.getElementById('documentNumberLabel').textContent = type.value === 'NIC' ? 'NIC Number' : 'Passport Number';
      document.getElementById('documentNumberHint').textContent = rule.hint;
      if (field.value.trim()) check(field, touched.has(field.id));
      else message(field, '', false);
    }
    fields.forEach(field => {
      field.addEventListener('input', function () {
        if (field.id === 'passengerNic') {
          const start = field.selectionStart, end = field.selectionEnd;
          field.value = field.value.toUpperCase();
          field.setSelectionRange(start, end);
        }
        check(field, touched.has(field.id));
      });
      field.addEventListener('blur', function () { touched.add(field.id); check(field, true); });
      field.addEventListener('paste', function (event) {
        if (!event.clipboardData) return;
        const candidate = field.value.slice(0, field.selectionStart) + event.clipboardData.getData('text/plain').trim()
          + field.value.slice(field.selectionEnd);
        if (candidate.length > field.maxLength) {
          event.preventDefault(); touched.add(field.id);
          // Rejected paste cannot leave an invisible invalid value in the field's validity.
          check(field, true);
          const notice = document.getElementById(field.id + 'Error');
          if (field.validationMessage === '') {
            notice.textContent = 'That paste was too long and was not added. The current value is unchanged.';
            notice.hidden = false;
          }
        }
      });
    });
    type.addEventListener('change', setDocumentRule);
    // Autofilled values are checked as well; the server repeats every format check before saving.
    form.addEventListener('submit', function (event) {
      let firstInvalid = null;
      fields.forEach(field => { if (!check(field, true) && !firstInvalid) firstInvalid = field; });
      if (firstInvalid) { event.preventDefault(); firstInvalid.focus(); firstInvalid.reportValidity(); }
    });
    setDocumentRule();
  }
  const api = { documentError, nameError, normalizePhone, mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PassengerDetails = api;
}(typeof window !== 'undefined' ? window : globalThis));
