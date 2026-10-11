(function(root) {
  'use strict';
  function error(kind, value) {
    value = value == null ? '' : String(value);
    if (kind === 'name') {
      const name = value.trim();
      return name.length >= 1 && name.length <= 100 && /^[\p{L}\p{M} .'’\-]+$/u.test(name) && /\p{L}/u.test(name)
        ? '' : 'Use 1–100 characters: letters, spaces, initials, apostrophes or hyphens.';
    }
    if (kind === 'phone') {
      const raw = value.trim();
      let number = raw.replace(/[ -]/g, '');
      if (number.startsWith('+94')) number = '0' + number.slice(3);
      else if (number.startsWith('0094')) number = '0' + number.slice(4);
      return raw.length <= 20 && /^[+0-9 -]+$/.test(raw)
        && /^0(?:7[0-9]|11|21|23|24|25|26|27|31|32|33|34|35|36|37|38|41|45|47|51|52|54|55|57|63|65|66|67|81|91)[0-9]{7}$/.test(number)
        ? '' : 'Enter 10 Sri Lankan phone digits, such as 0771234567 or +94771234567. Spaces and hyphens are allowed.';
    }
    if (kind === 'password') {
      const bytes = new TextEncoder().encode(value).length;
      return value.trim() && value.length >= 8 && bytes <= 72 ? '' : 'Use at least 8 characters and no more than 72 UTF-8 bytes.';
    }
    return '';
  }
  function mount(doc) {
    doc = doc || root.document;
    if (!doc) return;
    const inputs = Array.from(doc.querySelectorAll('[data-account-field]'));
    inputs.forEach(input => {
      if (input.dataset.accountMounted) return;
      input.dataset.accountMounted = 'true';
      const hint = doc.getElementById(input.id + 'ClientError');
      let revealed = false;
      function validate(show) {
        revealed = revealed || show;
        const message = error(input.dataset.accountField, input.value);
        input.setCustomValidity(message);
        if (revealed && message) input.setAttribute('aria-invalid','true'); else input.removeAttribute('aria-invalid');
        if (hint) { hint.textContent = revealed ? message : ''; hint.hidden = !(revealed && message); }
        return !message;
      }
      input.addEventListener('input', () => validate(false));
      input.addEventListener('change', () => validate(false));
      input.addEventListener('blur', () => validate(true));
      input.addEventListener('invalid', () => validate(true));
      if (root.addEventListener) root.addEventListener('pageshow', () => validate(false));
      if (input.form) {
        input.form.addEventListener('click', event => {
          if (event.target.closest('[type="submit"]')) validate(false);
        }, true);
        input.form.addEventListener('keydown', event => { if (event.key === 'Enter' && !event.isComposing) validate(false); }, true);
        input.form.addEventListener('submit', event => {
          if (!validate(true)) { event.preventDefault(); input.reportValidity(); }
        });
      }
    });
  }
  const api = { error, mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else { root.AccountFields = api; mount(); }
})(typeof window !== 'undefined' ? window : globalThis);
