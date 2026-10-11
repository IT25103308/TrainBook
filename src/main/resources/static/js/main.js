/**
 * TrainBook Main JavaScript
 * SLIIT Software Engineering Project | 2026-Y2-S1-MLB-B9G2-10
 */
document.addEventListener('DOMContentLoaded', () => {
  initNavHighlighting();
  initAlertDismissal();
  initFormLoadingStates();
  initMobileNavigation();
  initSidebarToggle();
  initSidebarDropdown();
  initSeatCalculator();
});

/**
 * Highlights current active navigation item based on window.location
 */
function initNavHighlighting() {
  const currentPath = window.location.pathname;
  const navLinks = document.querySelectorAll('.nav-link, .sidebar-link, .sidebar-sublink');

  // A nested operations page should highlight its own link, not Dashboard too.
  const matches = Array.from(navLinks).filter(link => {
    const href = link.getAttribute('href');
    if (!href || !href.startsWith('/') || href.startsWith('//')) return false;
    const parts = href.split('#');
    if (parts.length > 1 && window.location.hash !== '#' + parts[1]) return false;
    return parts[0] === currentPath || (parts[0] !== '/' && currentPath.startsWith(parts[0] + '/'));
  });
  const longest = matches.reduce((length, link) => Math.max(length, link.getAttribute('href').length), 0);
  navLinks.forEach(link => {
    link.classList.remove('active');
    if (matches.includes(link) && link.getAttribute('href').length === longest) {
      link.classList.add('active');
      link.setAttribute('aria-current', 'page');
    } else {
      link.removeAttribute('aria-current');
    }
  });

  // Auto-expand Manage Trains dropdown if viewing any train or schedule page
  const manageTrainsDropdown = document.getElementById('manageTrainsNavDropdown');
  if (manageTrainsDropdown) {
    if (currentPath.startsWith('/trains') || currentPath.startsWith('/schedules')) {
      if (manageTrainsDropdown.tagName === 'DETAILS') {
        manageTrainsDropdown.open = true;
      } else {
        manageTrainsDropdown.classList.add('open');
      }
    }
  }
}

/**
 * Initializes sidebar dropdown listeners for non-details elements
 */
function initSidebarDropdown() {
  const dropdownBtns = document.querySelectorAll('.sidebar-dropdown-btn');
  dropdownBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const parent = btn.closest('.sidebar-dropdown');
      if (parent && parent.tagName !== 'DETAILS') {
        parent.classList.toggle('open');
      }
    });
  });
}

/**
 * Automatically dismisses flash alerts with smooth fade-out
 */
function initAlertDismissal() {
  const alerts = document.querySelectorAll('.alert');
  
  alerts.forEach(alert => {
    if (alert.hasAttribute('data-persistent')) return;
    // Dismiss on close button click
    const closeBtn = alert.querySelector('.alert-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => dismissAlert(alert));
    }

    // Auto dismiss after 4.5 seconds
    setTimeout(() => {
      dismissAlert(alert);
    }, 4500);
  });
}

function dismissAlert(alertElement) {
  if (!alertElement) return;
  alertElement.style.transition = 'opacity 0.4s ease, transform 0.4s ease';
  alertElement.style.opacity = '0';
  alertElement.style.transform = 'translateY(-8px)';
  setTimeout(() => {
    if (alertElement.parentNode) {
      alertElement.parentNode.removeChild(alertElement);
    }
  }, 400);
}

/**
 * Adds loading state / spinner to form buttons on submit
 */
function initFormLoadingStates() {
  const forms = document.querySelectorAll('form:not(.no-loading)');

  forms.forEach(form => {
    form.addEventListener('submit', (e) => {
      if (form.checkValidity && !form.checkValidity()) {
        return;
      }
      const submitBtn = form.querySelector('button[type="submit"]');
      if (submitBtn && !submitBtn.disabled) {
        submitBtn.dataset.originalText = submitBtn.innerHTML;
        submitBtn.disabled = true;
        submitBtn.innerHTML = `
          <span style="display:inline-block; width:16px; height:16px; border:2px solid rgba(255,255,255,0.3); border-top-color:#fff; border-radius:50%; animation:spin 0.8s linear infinite; margin-right:8px; vertical-align:middle;"></span>
          Processing...
        `;
        // Allow form submission to proceed
        setTimeout(() => {
          // Re-enable after 8s fallback in case of no navigation
          submitBtn.disabled = false;
          if (submitBtn.dataset.originalText) {
            submitBtn.innerHTML = submitBtn.dataset.originalText;
          }
        }, 8000);
      }
    });
  });

  // Inject spinner animation if not present
  if (!document.getElementById('spin-keyframes')) {
    const style = document.createElement('style');
    style.id = 'spin-keyframes';
    style.innerHTML = `@keyframes spin { to { transform: rotate(360deg); } }`;
    document.head.appendChild(style);
  }
}

/**
 * Handles mobile navbar toggle
 */
function initMobileNavigation() {
  const toggle = document.querySelector('.nav-mobile-toggle');
  const navMenu = document.querySelector('.nav-menu');

  if (toggle && navMenu) {
    toggle.addEventListener('click', () => {
      navMenu.classList.toggle('open');
      const isExpanded = navMenu.classList.contains('open');
      toggle.setAttribute('aria-expanded', isExpanded);
    });
  }
}

/**
 * Admin Sidebar toggle for responsive screens
 */
function initSidebarToggle() {
  const sidebarToggle = document.querySelector('.sidebar-toggle-btn');
  const sidebar = document.querySelector('.admin-sidebar');

  if (sidebarToggle && sidebar) {
    sidebarToggle.addEventListener('click', () => {
      sidebar.classList.toggle('open');
    });
  }
}

/**
 * Seat Selection dynamic price calculation
 */
function initSeatCalculator() {
  const seatClassInputs = document.querySelectorAll('input[name="seatClass"]');
  const seatCountInput = document.getElementById('seatCount');
  const totalFareDisplay = document.getElementById('totalFareDisplay');
  const farePerSeatDisplay = document.getElementById('farePerSeatDisplay');

  if (seatClassInputs.length > 0 && seatCountInput && totalFareDisplay) {
    function updateCalculations() {
      const selectedClass = document.querySelector('input[name="seatClass"]:checked');
      if (!selectedClass) return;

      const price = parseFloat(selectedClass.dataset.price || 0);
      const count = parseInt(seatCountInput.value || 1, 10);
      const total = price * count;

      if (farePerSeatDisplay) {
        farePerSeatDisplay.textContent = `LKR ${price.toLocaleString('en-US', { minimumFractionDigits: 2 })}`;
      }
      totalFareDisplay.textContent = `LKR ${total.toLocaleString('en-US', { minimumFractionDigits: 2 })}`;
    }

    seatClassInputs.forEach(input => input.addEventListener('change', updateCalculations));
    seatCountInput.addEventListener('input', updateCalculations);
    seatCountInput.addEventListener('change', updateCalculations);
    updateCalculations();
  }
}
