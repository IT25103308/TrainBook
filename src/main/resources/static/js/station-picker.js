(function (root) {
  'use strict';

  function key(value) { return String(value || '').trim().toLowerCase(); }
  function matching(stations, query) {
    const term = key(query);
    return stations.filter(station => key(station).includes(term));
  }
  function canonical(stations, value) {
    return stations.find(station => key(station) === key(value)) || '';
  }

  // Keep the native select as a working fallback; only enhance it when JavaScript runs.
  function enhance(select) {
    if (select.dataset.enhanced) return;
    const doc = select.ownerDocument;
    const wrapper = select.parentElement;
    const stations = Array.from(select.options).map(option => option.value).filter(Boolean);
    const id = select.id;
    const input = doc.createElement('input');
    input.type = 'text'; input.id = id; input.className = 'form-control station-input';
    input.value = select.value; input.required = true; input.maxLength = 100;
    input.placeholder = 'Search or choose station'; input.autocomplete = 'off';
    input.setAttribute('role', 'combobox'); input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-expanded', 'false'); input.setAttribute('aria-required', 'true');
    input.setAttribute('aria-controls', id + 'Options'); input.setAttribute('aria-describedby', id + 'Error');
    const toggle = doc.createElement('button');
    toggle.type = 'button'; toggle.className = 'station-picker-toggle'; toggle.tabIndex = -1;
    toggle.textContent = '▾'; toggle.setAttribute('aria-label', 'Show station choices');
    const list = doc.createElement('ul'); list.id = id + 'Options'; list.className = 'station-options';
    list.setAttribute('role', 'listbox'); list.hidden = true;
    list.setAttribute('aria-label', 'Station choices');
    const error = doc.createElement('span'); error.id = id + 'Error';
    error.className = 'station-field-error'; error.hidden = true;
    const status = doc.createElement('span'); status.className = 'sr-only';
    status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
    select.id = id + 'Source'; select.hidden = true; select.required = false;
    select.dataset.enhanced = 'true';
    wrapper.append(input, toggle, list, error, status);
    let filtered = [], active = -1;

    function validity(show) {
      const choice = canonical(stations, input.value);
      select.value = choice;
      const message = choice ? '' : input.value.trim() ? 'Choose a station from the list.' : 'Choose a station.';
      input.setCustomValidity(message);
      if (show && message) input.setAttribute('aria-invalid', 'true');
      else input.removeAttribute('aria-invalid');
      error.textContent = show ? message : ''; error.hidden = !(show && message);
      return Boolean(choice);
    }
    function close() {
      list.hidden = true; active = -1;
      input.setAttribute('aria-expanded', 'false'); input.removeAttribute('aria-activedescendant');
    }
    function highlight(index) {
      active = index;
      Array.from(list.children).forEach((option, position) => option.setAttribute('aria-selected', String(position === active)));
      if (active >= 0 && list.children[active]) {
        input.setAttribute('aria-activedescendant', list.children[active].id);
        list.children[active].scrollIntoView({ block: 'nearest' });
      } else input.removeAttribute('aria-activedescendant');
    }
    function choose(station) {
      input.value = station; validity(false); close(); input.focus();
      // Focusing an already focused input does not reopen the popup.
      close();
    }
    function open(query) {
      filtered = matching(stations, query); active = -1;
      input.removeAttribute('aria-activedescendant'); list.replaceChildren();
      filtered.forEach((station, index) => {
        const option = doc.createElement('li'); option.id = id + 'Option' + index;
        option.setAttribute('role', 'option'); option.setAttribute('aria-selected', 'false'); option.textContent = station;
        option.addEventListener('pointerdown', event => event.preventDefault());
        option.addEventListener('mousedown', event => event.preventDefault());
        option.addEventListener('click', () => choose(station)); list.append(option);
      });
      if (!filtered.length) {
        const empty = doc.createElement('li'); empty.className = 'station-no-match'; empty.textContent = 'No matching stations'; list.append(empty);
      }
      list.hidden = false; input.setAttribute('aria-expanded', 'true');
      status.textContent = filtered.length ? filtered.length + ' station choices available.' : 'No matching stations.';
    }
    input.addEventListener('focus', () => open(canonical(stations, input.value) ? '' : input.value));
    input.addEventListener('input', () => { validity(false); open(input.value); });
    input.addEventListener('change', () => validity(false));
    input.addEventListener('blur', () => { validity(true); close(); });
    input.addEventListener('invalid', () => validity(true));
    input.addEventListener('keydown', event => {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        if (list.hidden) open(canonical(stations, input.value) ? '' : input.value);
        if (filtered.length) highlight(event.key === 'ArrowDown' ? Math.min(active + 1, filtered.length - 1) : active < 0 ? filtered.length - 1 : Math.max(0, active - 1));
      } else if (event.key === 'Enter' && !list.hidden && active >= 0) {
        event.preventDefault(); choose(filtered[active]);
      } else if (event.key === 'Escape') {
        if (!list.hidden) event.preventDefault(); close();
      } else if (event.key === 'Tab') close();
    });
    toggle.addEventListener('mousedown', event => event.preventDefault());
    toggle.addEventListener('click', () => {
      const wasOpen = !list.hidden; input.focus(); if (wasOpen) close(); else open('');
    });
    select.form.addEventListener('submit', event => {
      if (!validity(true)) { event.preventDefault(); input.focus(); input.reportValidity(); }
      else { input.value = select.value; close(); }
    });
    validity(false);
    return { input, list, toggle, error, select };
  }
  function mount() {
    root.document.querySelectorAll('[data-station-picker]').forEach(enhance);
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = { matching, canonical, enhance };
  if (root.document) {
    if (root.document.readyState === 'loading') root.document.addEventListener('DOMContentLoaded', mount);
    else mount();
  }
})(typeof window !== 'undefined' ? window : globalThis);
