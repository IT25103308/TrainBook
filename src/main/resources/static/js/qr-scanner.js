/**
 * TrainBook QR Ticket Scanner & Validation Script
 * Uses jsQR library for live browser video feed decoding
 * SLIIT Software Engineering Project | 2026-Y2-S1-MLB-B9G2-10
 */
let videoStream = null;
let scanning = false;
let validationBusy=false;
let animationFrameId = null;

document.addEventListener('DOMContentLoaded', () => {
  const startCameraBtn = document.getElementById('startCameraBtn');
  const stopCameraBtn = document.getElementById('stopCameraBtn');
  const manualForm = document.getElementById('manualValidateForm');
  const resetValidationBtn = document.getElementById('resetValidationBtn');

  if (startCameraBtn) {
    startCameraBtn.addEventListener('click', startScanner);
  }

  if (stopCameraBtn) {
    stopCameraBtn.addEventListener('click', stopScanner);
  }

  if (manualForm) {
    manualForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const ticketNumber = document.getElementById('ticketNumberInput').value.trim();
      if (ticketNumber) {
        validateTicket(ticketNumber);
      }
    });
  }

  if (resetValidationBtn) {
    resetValidationBtn.addEventListener('click', resetScannerUI);
  }

  const select=document.getElementById('boardingSchedule'),manifest=document.getElementById('loadManifest');
  if(select && manifest){select.addEventListener('change',()=>{const date=document.getElementById('boardingDate').value;manifest.href='/dashboard/validate?date='+encodeURIComponent(date)+(select.value?'&scheduleId='+encodeURIComponent(select.value):'');});}
  // Network connectivity drop detection
  const offlineBanner = document.getElementById('offlineBanner');
  window.addEventListener('offline', () => {
    if (offlineBanner) offlineBanner.style.display = 'flex';
  });
  window.addEventListener('online', () => {
    if (offlineBanner) offlineBanner.style.display = 'none';
  });
});

async function startScanner() {
  const videoElement = document.getElementById('qrVideo');
  const startBtn = document.getElementById('startCameraBtn');
  const stopBtn = document.getElementById('stopCameraBtn');
  const scannerStatus = document.getElementById('scannerStatus');

  if(typeof jsQR!=='function'){showInvalidResult('Camera QR reader unavailable. Use manual ticket entry.');return;}
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    alert('Camera access is not supported on this browser/device.');
    return;
  }

  try {
    videoStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' }
    });
    
    videoElement.srcObject = videoStream;
    videoElement.setAttribute('playsinline', true);
    await videoElement.play();

    scanning = true;
    startBtn.style.display = 'none';
    stopBtn.style.display = 'inline-flex';
    if (scannerStatus) scannerStatus.textContent = 'Scanning for QR Code...';

    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });

    function tick() {
      if (!scanning) return;

      if (videoElement.readyState === videoElement.HAVE_ENOUGH_DATA) {
        canvas.height = videoElement.videoHeight;
        canvas.width = videoElement.videoWidth;
        ctx.drawImage(videoElement, 0, 0, canvas.width, canvas.height);

        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        if (typeof jsQR !== 'undefined') {
          const code = jsQR(imageData.data, imageData.width, imageData.height, {
            inversionAttempts: 'dontInvert'
          });

          if (code && code.data) {
            handleScanSuccess(code.data);
            return;
          }
        }
      }
      animationFrameId = requestAnimationFrame(tick);
    }

    animationFrameId = requestAnimationFrame(tick);
  } catch (err) {
    console.error('Error accessing camera:', err);
    alert('Unable to access camera. Please check camera permissions or use manual input.');
  }
}

function stopScanner() {
  scanning = false;
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }

  if (videoStream) {
    videoStream.getTracks().forEach(track => track.stop());
    videoStream = null;
  }

  const videoElement = document.getElementById('qrVideo');
  if (videoElement) {
    videoElement.srcObject = null;
  }

  const startBtn = document.getElementById('startCameraBtn');
  const stopBtn = document.getElementById('stopCameraBtn');
  const scannerStatus = document.getElementById('scannerStatus');

  if (startBtn) startBtn.style.display = 'inline-flex';
  if (stopBtn) stopBtn.style.display = 'none';
  if (scannerStatus) scannerStatus.textContent = 'Camera stopped. Click Start Camera or enter ticket ID manually.';
}

function handleScanSuccess(decodedData) {
  stopScanner();

  const ticketInput = document.getElementById('ticketNumberInput');
  if (ticketInput) {
    ticketInput.value = decodedData;
  }

  validateTicket(decodedData);
}

async function validateTicket(ticketData) {
  if(validationBusy)return;
  const departure=document.getElementById('boardingSchedule'),date=document.getElementById('boardingDate');
  if(!departure?.value || !date?.value){showInvalidResult('Choose the departure and date before boarding.');return;}
  validationBusy=true;

  const resultCardValid = document.getElementById('validationResultValid');
  const resultCardInvalid = document.getElementById('validationResultInvalid');
  const validateSpinner = document.getElementById('validateSpinner');
  const scannerSection = document.getElementById('scannerSection');

  // Hide existing results
  if (resultCardValid) resultCardValid.style.display = 'none';
  if (resultCardInvalid) resultCardInvalid.style.display = 'none';
  if (validateSpinner) validateSpinner.style.display = 'block';

  try {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };
    if (csrfToken && csrfHeader) {
      headers[csrfHeader] = csrfToken;
    }

    const response = await fetch('/api/tickets/validate', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({ ticketNumber: ticketData,scheduleId:departure.value,serviceDate:date.value })
    });

    if (validateSpinner) validateSpinner.style.display = 'none';

    let data = null;
    try {
      data = await response.json();
    } catch (e) {
      console.warn('Failed to parse JSON response', e);
    }

    if (response.ok && data && typeof data.valid === 'boolean') {
      if (data.valid === true) {
        playSuccessSound();
        showValidResult(data);
      } else {
        showInvalidResult(data.message || 'Invalid or Expired Ticket.');
      }
    } else {
      showInvalidResult('Boarding was not confirmed. Refresh your session and check the ticket before trying again.');
    }
  } catch (err) {
    console.error('Validation fetch error:', err);
    if (validateSpinner) validateSpinner.style.display = 'none';
    showInvalidResult('Boarding result unavailable. Check the ticket status after reconnecting; do not admit it without confirmation.');
  } finally {validationBusy=false;} 
}

function showValidResult(ticket) {
  const resultCardValid = document.getElementById('validationResultValid');
  if (!resultCardValid) return;

  document.getElementById('resTicketNo').textContent = ticket.ticketNumber || 'Not recorded';
  document.getElementById('resPassengerName').textContent = ticket.passengerName || 'Not recorded';
  document.getElementById('resTrainName').textContent = ticket.trainName || 'Not recorded';
  document.getElementById('resRoute').textContent = `${ticket.origin || 'Not recorded'} ➔ ${ticket.destination || 'Not recorded'}`;
  document.getElementById('resSeatInfo').textContent = `${ticket.seatClass || 'Not recorded'} (Seat ${ticket.seatNumbers || 'Not recorded'})`;
  document.getElementById('resTravelDate').textContent = ticket.travelDate || 'Not recorded';

  resultCardValid.style.display = 'block';
  resultCardValid.scrollIntoView({ behavior: 'smooth' });
}

function showInvalidResult(reason) {
  const resultCardInvalid = document.getElementById('validationResultInvalid');
  if (!resultCardInvalid) return;

  const reasonElement = document.getElementById('invalidReasonText');
  if (reasonElement) reasonElement.textContent = reason;

  resultCardInvalid.style.display = 'block';
  resultCardInvalid.scrollIntoView({ behavior: 'smooth' });
}

function resetScannerUI() {
  const resultCardValid = document.getElementById('validationResultValid');
  const resultCardInvalid = document.getElementById('validationResultInvalid');
  const ticketInput = document.getElementById('ticketNumberInput');

  if (resultCardValid) resultCardValid.style.display = 'none';
  if (resultCardInvalid) resultCardInvalid.style.display = 'none';
  if (ticketInput) {
    ticketInput.value = '';
    ticketInput.focus();
  }
}

// Expose functions globally for table click-to-validate buttons and UI reset
window.quickValidateTicket = function(code) {
  const ticketInput = document.getElementById('ticketNumberInput');
  if (ticketInput) {
    ticketInput.value = code;
  }
  ticketInput?.focus();
};
window.resetScannerUI = resetScannerUI;
window.validateTicket = validateTicket;

function playSuccessSound() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.type = 'sine';
    osc.frequency.setValueAtTime(800, ctx.currentTime);
    osc.frequency.setValueAtTime(1200, ctx.currentTime + 0.1);
    gain.gain.setValueAtTime(0.3, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
    osc.start();
    osc.stop(ctx.currentTime + 0.25);
  } catch (e) {
    // Audio context may be restricted
  }
}

window.addEventListener("pagehide",stopScanner);
