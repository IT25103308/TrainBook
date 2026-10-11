(function (root) {
  'use strict';

  function presentation(state) {
    const descriptions = {
      PENDING: { title: 'Temporary Seat Hold Active', message: 'Complete payment before the remaining time expires. Refreshing does not extend your hold.' },
      EXPIRED: { label: 'EXPIRED', title: 'Seat Hold Expired', message: 'Your seat hold has expired. Payment is unavailable. Expired holds are released automatically; select seats again to check current availability.' },
      EXPIRED_RELEASED: { label: 'EXPIRED', title: 'Seat Hold Expired', message: 'Your seat hold expired and its seats were released. Select available seats again or search another train.' },
      CANCELLED_BY_PASSENGER: { label: 'CANCELLED', title: 'Reservation Cancelled by You', message: 'You cancelled this reservation and its held seats were released.' },
      CANCELLED: { label: 'CANCELLED', title: 'Reservation Cancelled', message: 'This reservation is cancelled and its seats are no longer held. Select available seats again or search another train.' },
      SERVICE_CANCELLED: { label: 'SERVICE CANCELLED', title: 'Train Service Cancelled', message: 'This train service has been cancelled. Payment is unavailable. Please search for another train; your reservation record has been kept.' },
      CONFIRMED: { label: 'PAID', title: 'Payment Completed', message: 'This reservation is paid. View its ticket in My Bookings.' },
      UNAVAILABLE: { label: 'UNAVAILABLE', title: 'Reservation Unavailable', message: 'Payment is unavailable for this reservation. Please search another train.' }
    };
    return descriptions[state] || descriptions.UNAVAILABLE;
  }

  function snapshot(state, initialRemainingMillis, elapsedMillis) {
    const remaining = Number.isFinite(initialRemainingMillis) && Number.isFinite(elapsedMillis)
      ? Math.max(0, initialRemainingMillis - Math.max(0, elapsedMillis)) : 0;
    const currentState = state === 'PENDING' && remaining <= 0 ? 'EXPIRED' : state;
    const canPay = currentState === 'PENDING' && remaining > 0;
    const seconds = Math.ceil(remaining / 1000);
    const label = canPay
      ? String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0')
      : presentation(currentState).label;
    return { state: currentState, canPay: canPay, label: label };
  }

  function mount(config) {
    const form = document.getElementById('paymentForm');
    if (!form) return;
    const fields = document.getElementById('paymentFields');
    const payButton = document.getElementById('payButton');
    const timer = document.getElementById('timerDisplay');
    const title = document.getElementById('paymentHoldTitle');
    const message = document.getElementById('paymentHoldMessage');
    const badge = document.getElementById('paymentStatusBadge');
    const paymentCard = document.getElementById('paymentMethodCard');
    const cancelForm = document.getElementById('cancelReservationForm');
    const recoveryActions = document.getElementById('checkoutRecoveryActions');
    const retryLink = document.getElementById('selectSeatsAgain');
    const searchButton = document.getElementById('searchAnotherTrain');
    const recoveryMessage = document.getElementById('journeyRecoveryMessage');
    const searchLink = document.getElementById('checkoutSearchLink');
    const editDetailsLink = document.getElementById('editPassengerDetails');
    // The server computes this from the saved expiry on each request. Elapsed time,
    // rather than a decremented counter, catches up after an inactive browser tab.
    const startedAt = Date.now();
    let expired = false;
    let submitting = false;
    let interval;

    function render() {
      const result = snapshot(expired ? 'EXPIRED' : config.state,
        Number(config.remainingMillis), Date.now() - startedAt);
      if (result.state === 'EXPIRED') expired = true;
      fields.disabled = !result.canPay;
      // Keep named fields enabled for native form serialization. Only lock actions.
      payButton.disabled = !result.canPay || submitting;
      timer.textContent = result.label;
      badge.textContent = result.canPay ? 'Awaiting Payment' : result.label;
      const description = presentation(result.state);
      title.textContent = description.title;
      message.textContent = description.message;
      if (paymentCard) paymentCard.hidden = !result.canPay;
      if (cancelForm) cancelForm.hidden = !result.canPay || submitting;
      if (editDetailsLink) editDetailsLink.hidden = !result.canPay || submitting;
      const recoverable = !result.canPay && result.state !== 'CONFIRMED';
      const canSelectAgain = recoverable && config.recoveryEligible === true
        && Number.isFinite(config.departureRemainingMillis)
        && config.departureRemainingMillis > Math.max(0, Date.now() - startedAt);
      if (recoveryActions) recoveryActions.hidden = !recoverable;
      if (retryLink) retryLink.hidden = !canSelectAgain;
      if (searchButton) searchButton.className = canSelectAgain ? 'btn btn-secondary' : 'btn btn-primary';
      if (searchLink) searchLink.hidden = recoverable;
      if (recoveryMessage) recoveryMessage.textContent = canSelectAgain
        ? 'Seat availability will be checked again. Choose from the seats available now.'
        : config.recoveryEligible ? 'This departure has already passed. Please search another train.'
        : config.recoveryMessage || 'Please search another train.';
      if (!result.canPay && interval !== undefined) clearInterval(interval);
      return result;
    }

    form.addEventListener('submit', function (event) {
      if (event.defaultPrevented) return;
      if (submitting || !render().canPay) { event.preventDefault(); return; }
      if (!form.checkValidity()) { event.preventDefault(); form.reportValidity(); return; }
      submitting = true;
      payButton.textContent = 'Processing payment…';
      render();
    });
    document.addEventListener('visibilitychange', render);
    // A history-restored page must obtain a fresh server snapshot, not reuse old time.
    root.addEventListener('pageshow', function (event) {
      if (event.persisted) root.location.reload();
    });
    if (render().canPay) interval = setInterval(render, 250);
  }

  const api = { snapshot: snapshot, mount: mount };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PaymentHold = api;
}(typeof window !== 'undefined' ? window : globalThis));
