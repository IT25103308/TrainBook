(function (root) {
  'use strict';
  function mismatch(password, confirmation) {
    return confirmation && password !== confirmation ? 'Passwords do not match.' : '';
  }
  function mount(doc) {
    doc = doc || root.document;
    if (!doc) return;
    const buttons = Array.from(doc.querySelectorAll('[data-password-toggle]'));
    const controls = [];
    buttons.forEach(button => {
      if (button.dataset.passwordMounted) return;
      const input = doc.getElementById(button.dataset.passwordToggle);
      if (!input) return;
      button.dataset.passwordMounted = 'true';
      const slash = button.querySelector('[data-eye-slash]');
      function setVisible(visible) {
        input.type = visible ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(visible));
        button.setAttribute('aria-label', (visible ? 'Hide ' : 'Show ') + (input.id === 'confirmPassword' ? 'confirm password' : 'password'));
        if (slash) slash.hidden = !visible;
      }
      button.hidden = false;
      setVisible(false);
      button.addEventListener('click', () => setVisible(input.type === 'password'));
      controls.push(() => setVisible(false));
    });
    if (root.addEventListener) root.addEventListener('pageshow', () => controls.forEach(mask => mask()));
    const password = doc.getElementById('password'), confirmation = doc.getElementById('confirmPassword');
    if (!password || !confirmation || confirmation.dataset.confirmationMounted) return;
    confirmation.dataset.confirmationMounted = 'true';
    const error = doc.getElementById('confirmPasswordError');
    let showError = Boolean(error && !error.hidden);
    function validate(reveal) {
      showError = showError || Boolean(reveal);
      const message = mismatch(password.value, confirmation.value);
      confirmation.setCustomValidity(message);
      if (showError && message) confirmation.setAttribute('aria-invalid', 'true'); else confirmation.removeAttribute('aria-invalid');
      if (error) { error.textContent = showError ? message : ''; error.hidden = !(showError && message); }
      return !message;
    }
    [password, confirmation].forEach(input => {
      input.addEventListener('input', () => validate(false));
      input.addEventListener('change', () => validate(false));
    });
    confirmation.addEventListener('blur', () => validate(true));
    confirmation.addEventListener('invalid', () => validate(true));
    const form = confirmation.form;
    if (form) {
      const submit = form.querySelector('[type="submit"]');
      if (submit) submit.addEventListener('click', () => validate(false), true);
      form.addEventListener('keydown', event => { if (event.key === 'Enter' && !event.isComposing) validate(false); }, true);
      form.addEventListener('submit', event => { if (!validate(true)) { event.preventDefault(); confirmation.reportValidity(); } });
    }
  }
  const api = { mismatch, mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else { root.PasswordFields = api; mount(); }
})(typeof window !== 'undefined' ? window : globalThis);
