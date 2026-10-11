/* Seat labels are shared with the server: one class inventory per service and date. */
(function (root) {
  'use strict';

  function createSeatMap(seatClass, capacity, occupiedSeats) {
    const size = Number.isInteger(capacity) && capacity > 0 ? capacity : 0;
    const occupied = new Set(occupiedSeats || []);
    const carriage = seatClass === 'FIRST' ? '01' : '02';
    return Array.from({ length: size }, function (_, index) {
      const shortLabel = 'S' + String(index + 1).padStart(2, '0');
      const label = 'Car ' + carriage + ' / ' + shortLabel;
      return { label: label, shortLabel: shortLabel, occupied: occupied.has(label) };
    });
  }

  function selectionStatus(seats, selectedSeats, requestedCount, reportedAvailable, warning) {
    const free = new Set(seats.filter(function (seat) { return !seat.occupied; }).map(function (seat) { return seat.label; }));
    const available = Math.min(free.size, Math.max(0, Number(reportedAvailable) || 0));
    const unique = new Set(selectedSeats);
    const validSelection = unique.size === selectedSeats.length && selectedSeats.every(function (seat) { return free.has(seat); });
    return {
      available: available,
      canSubmit: !warning && requestedCount >= 1 && requestedCount <= 6 && Number.isInteger(requestedCount)
        && selectedSeats.length === requestedCount && requestedCount <= available && validSelection
    };
  }

  function signInTarget(target, seatClass) {
    const url = new URL(target, 'http://trainbook.local');
    if (url.pathname !== '/booking/sign-in' || !['FIRST','SECOND'].includes(seatClass)) return null;
    url.searchParams.set('seatClass',seatClass);
    return url.pathname + url.search;
  }

  function mount(config) {
    const form = document.getElementById('seatBookingForm');
    if (!form) return;
    const grid = document.getElementById('seatGrid');
    const countSelect = document.getElementById('seatCount');
    const numbersInput = document.getElementById('seatNumbersInput');
    const label = document.getElementById('selectedSeatsLabel');
    const notice = document.getElementById('seatSelectionNotice');
    const submitButton = document.getElementById('btnSubmitBooking');
    if (submitButton.dataset && submitButton.dataset.signInUrl) {
      submitButton.addEventListener('click', function () {
        const selected = form.querySelector('input[name="seatClass"]:checked');
        const target = signInTarget(submitButton.dataset.signInUrl, selected ? selected.value : config.initialClass);
        if (target) root.location.assign(target);
      });
    }
    const banner = document.getElementById('soldOutBanner');
    const bannerText = document.getElementById('availabilityMessage');
    const classRadios = Array.from(form.querySelectorAll('input[name="seatClass"]'));
    let selectedSeats = [];
    let seats = [];

    function currentRadio() { return classRadios.find(function (radio) { return radio.checked; }); }
    function currentInventory() { return config.classes[currentRadio().value]; }
    function requestedCount() { return Number(countSelect.value); }
    function status() {
      const inventory = currentInventory();
      return selectionStatus(seats, selectedSeats, requestedCount(), inventory.available, inventory.warning);
    }

    function updateDisplays(message) {
      const result = status();
      const inventory = currentInventory();
      const unitPrice = Number(currentRadio().dataset.price) || 0;
      const currency = function (value) { return 'LKR ' + value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); };
      label.textContent = selectedSeats.join(', ') || 'None selected';
      numbersInput.value = selectedSeats.join(', ');
      submitButton.disabled = !result.canSubmit;
      document.getElementById('farePerSeatDisplay').textContent = currency(unitPrice);
      document.getElementById('totalFareDisplay').textContent = currency(unitPrice * selectedSeats.length);
      banner.style.display = inventory.warning || result.available === 0 ? 'flex' : 'none';
      bannerText.textContent = inventory.warning || 'No seats remain in this class for this date. Please choose another class or date.';
      notice.textContent = message || (result.available === 0 ? 'No seats selected.'
        : requestedCount() > result.available ? 'Only ' + result.available + ' seats remain. Reduce the passenger count or choose another class.'
        : !result.canSubmit ? 'Select ' + requestedCount() + ' seat(s) to continue.'
        : selectedSeats.length + ' seat(s) selected. Availability is checked again when you continue.');
    }

    function renderSeats(focusLabel) {
      grid.replaceChildren();
      seats.forEach(function (seat) {
        const selected = selectedSeats.includes(seat.label);
        const unavailable = seat.occupied || Boolean(currentInventory().warning);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'reservation-seat ' + (unavailable ? 'is-occupied' : selected ? 'is-selected' : 'is-available');
        button.textContent = seat.shortLabel + (unavailable ? ' 🔒' : selected ? ' ✓' : '');
        button.disabled = unavailable;
        button.setAttribute('aria-label', seat.label + (seat.occupied ? ', booked or held' : unavailable ? ', unavailable' : ', available'));
        button.setAttribute('aria-pressed', String(selected));
        button.addEventListener('click', function () {
          const index = selectedSeats.indexOf(seat.label);
          if (index >= 0) {
            selectedSeats.splice(index, 1);
          } else {
            if (selectedSeats.length >= Math.min(6, status().available)) {
              updateDisplays('Deselect a seat before choosing another. Up to six available seats may be selected.');
              return;
            }
            selectedSeats.push(seat.label);
          }
          countSelect.value = String(Math.max(1, selectedSeats.length));
          renderSeats(seat.label);
        });
        grid.appendChild(button);
        if (seat.label === focusLabel) button.focus();
      });
      updateDisplays();
    }

    function selectDefaults() {
      const result = status();
      const freeLabels = seats.filter(function (seat) { return !seat.occupied; }).map(function (seat) { return seat.label; });
      const limit = Math.min(requestedCount(), result.available);
      if (currentInventory().warning) {
        selectedSeats = [];
        return;
      }
      // Changing quantity keeps the passenger's existing choices before adding more seats.
      selectedSeats = selectedSeats.filter(function (seat) { return freeLabels.includes(seat); }).slice(0, limit);
      freeLabels.forEach(function (seat) {
        if (selectedSeats.length < limit && !selectedSeats.includes(seat)) selectedSeats.push(seat);
      });
    }

    function changeClass() {
      const radio = currentRadio();
      const inventory = currentInventory();
      seats = createSeatMap(radio.value, Number(inventory.capacity), inventory.occupied);
      document.getElementById('coachLabel').textContent = radio.value === 'FIRST' ? 'First class · Car 01' : 'Second class · Car 02';
      selectedSeats = [];
      selectDefaults();
      renderSeats();
    }

    const initialRadio = classRadios.find(function (radio) { return radio.value === config.initialClass; });
    if (initialRadio) initialRadio.checked = true;
    const initialCount = Number(config.initialCount);
    if (Number.isInteger(initialCount) && initialCount >= 1 && initialCount <= 6) countSelect.value = String(initialCount);
    classRadios.forEach(function (radio) { radio.addEventListener('change', changeClass); });
    countSelect.addEventListener('change', function () { selectDefaults(); renderSeats(); });
    form.addEventListener('submit', function (event) {
      if (!status().canSubmit) {
        event.preventDefault();
        updateDisplays();
        notice.focus();
      }
    });
    changeClass();
    if (config.bookingError) {
      // After a conflict, let the passenger choose again from the refreshed inventory.
      selectedSeats = [];
      renderSeats();
    }
  }

  const api = { createSeatMap: createSeatMap, selectionStatus: selectionStatus, signInTarget: signInTarget, mount: mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TrainSeatSelection = api;
}(typeof window !== 'undefined' ? window : globalThis));
