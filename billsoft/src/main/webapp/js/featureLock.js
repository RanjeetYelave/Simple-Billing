/**
 * App-Based Feature Lock Module (UI Privacy Lock)
 * Completely isolated client-side privacy locking for selected sidebar features.
 * 
 * Storage Key: billsoft_feature_lock_v1
 * Public Interface: window.FeatureLock
 */
(function (root) {
  'use strict';

  const STORAGE_KEY = 'billsoft_feature_lock_v1';
  const MASTER_RECOVERY_HASH = '3680e811ded1a1831a688d594243e727a23d8bc801a9e2fa31279dba46b635ce';

  const LOCKABLE_FEATURES = [
    { id: 'hr', label: 'HR Management & Payroll', icon: '👥', desc: 'Employee master, salary slips, attendance & advances' },
    { id: 'planner', label: 'Planner & Personal View', icon: '📅', desc: 'Kanban tasks, reminders, personal notes, expenses & goals' },
    { id: 'inbox', label: 'Firm Inbox', icon: '📥', desc: 'System alerts, business notices & firm messages' },
    { id: 'invoices', label: 'Invoices & Quotations', icon: '📄', desc: 'Customer billing records, estimates & sales returns' },
    { id: 'firm', label: 'Firm Profile & Inventory', icon: '🏢', desc: 'Stock movements, price books, purchase orders & statements' },
    { id: 'circuit_connect', label: 'Take a Break', icon: '🎮', desc: 'In-app recreation and progression experience' }
  ];

  const VALID_FEATURE_IDS = ['hr', 'planner', 'inbox', 'invoices', 'firm', 'circuit_connect'];

  // Runtime-only in-memory state (NEVER persisted)
  let inMemoryUnlocked = {}; // { [normFeatureId]: timestamp }
  let lastActivityTime = Date.now();
  let isSettingsSessionAuthorized = false;
  let inactivityTimerId = null;

  function scheduleInactivityCheck() {
    if (typeof window === 'undefined') return;
    if (inactivityTimerId) {
      clearTimeout(inactivityTimerId);
      inactivityTimerId = null;
    }
    const cfg = getRawConfig();
    if (!cfg.enabled || !cfg.pin || cfg.timeoutMinutes <= 0) return;
    if (Object.keys(inMemoryUnlocked).length === 0) return;

    const timeoutMs = cfg.timeoutMinutes * 60 * 1000;
    inactivityTimerId = setTimeout(() => {
      const now = Date.now();
      if (now - lastActivityTime >= timeoutMs) {
        inMemoryUnlocked = {};
        window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: { timedOut: true } }));
      } else {
        scheduleInactivityCheck();
      }
    }, timeoutMs + 100);
    if (inactivityTimerId && typeof inactivityTimerId.unref === 'function') {
      inactivityTimerId.unref();
    }
  }

  // ─── PURE JAVASCRIPT SHA-256 FALLBACK (FOR NON-SECURE HTTP / LAN ENVIRONMENTS) ───
  function jsSha256(ascii) {
    function rightRotate(value, amount) {
      return (value >>> amount) | (value << (32 - amount));
    }
    const mathPow = Math.pow;
    const maxWord = mathPow(2, 32);
    let lengthProperty = 'length';
    let i, j;
    let result = '';
    const words = [];
    const asciiBitLength = (ascii ? ascii[lengthProperty] : 0) * 8;
    let hash = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    const k = [
      0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
      0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
      0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
      0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
      0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
      0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
      0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
      0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    ];
    let composite = (ascii || '') + '\x80';
    while (composite[lengthProperty] % 64 - 56) composite += '\x00';
    for (i = 0; i < composite[lengthProperty]; i++) {
      j = composite.charCodeAt(i);
      words[i >> 2] |= j << ((3 - i) % 4) * 8;
    }
    words[words[lengthProperty]] = ((asciiBitLength / maxWord) | 0);
    words[words[lengthProperty]] = (asciiBitLength | 0);
    for (j = 0; j < words[lengthProperty];) {
      const w = words.slice(j, j += 16);
      const oldHash = hash;
      hash = hash.slice(0, 8);
      for (i = 0; i < 64; i++) {
        const w15 = w[i - 15], w2 = w[i - 2];
        const a = hash[0], e = hash[4];
        const temp1 = hash[7]
          + (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25))
          + ((e & hash[5]) ^ ((~e) & hash[6]))
          + k[i]
          + (w[i] = (i < 16) ? w[i] : (
            w[i - 16]
            + (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3))
            + w[i - 7]
            + (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))
          ) | 0);
        const temp2 = (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22))
          + ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]));
        hash = [(temp1 + temp2) | 0].concat(hash);
        hash[4] = (hash[4] + temp1) | 0;
      }
      for (i = 0; i < 8; i++) {
        hash[i] = (hash[i] + oldHash[i]) | 0;
      }
    }
    for (i = 0; i < 8; i++) {
      for (j = 3; j >= 0; j--) {
        const b = (hash[i] >> (8 * j)) & 255;
        result += (b < 16 ? '0' : '') + b.toString(16);
      }
    }
    return result;
  }

  // Global user activity tracking & inactivity expiration check
  function checkInactivityExpiration(now = Date.now()) {
    const cfg = getRawConfig();
    if (cfg.enabled && cfg.timeoutMinutes > 0 && Object.keys(inMemoryUnlocked).length > 0) {
      const timeoutMs = cfg.timeoutMinutes * 60 * 1000;
      if (now - lastActivityTime >= timeoutMs) {
        inMemoryUnlocked = {};
        if (inactivityTimerId) {
          clearTimeout(inactivityTimerId);
          inactivityTimerId = null;
        }
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: { timedOut: true } }));
        }
        return true;
      }
    }
    return false;
  }

  if (typeof window !== 'undefined') {
    let lastThrottledTime = 0;
    const onUserActivity = () => {
      const now = Date.now();
      if (now - lastThrottledTime > 1000) {
        if (checkInactivityExpiration(now)) {
          lastActivityTime = now;
          lastThrottledTime = now;
          return;
        }
        lastActivityTime = now;
        lastThrottledTime = now;
        if (Object.keys(inMemoryUnlocked).length > 0) {
          scheduleInactivityCheck();
        }
      }
    };
    window.addEventListener('mousemove', onUserActivity, { passive: true });
    window.addEventListener('mousedown', onUserActivity, { passive: true });
    window.addEventListener('keydown', onUserActivity, { passive: true });
    window.addEventListener('touchstart', onUserActivity, { passive: true });
    window.addEventListener('scroll', onUserActivity, { passive: true });
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
          checkInactivityExpiration(Date.now());
        }
      });
    }
    window.addEventListener('focus', () => {
      checkInactivityExpiration(Date.now());
    });
    window.addEventListener('storage', (e) => {
      if (e.key === STORAGE_KEY) {
        window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: { storageSynced: true } }));
      }
    });
    window.addEventListener('popstate', () => {
      isSettingsSessionAuthorized = false;
    });
    window.addEventListener('pagehide', () => {
      isSettingsSessionAuthorized = false;
    });
  }

  // ─── STORAGE HANDLING ───
  function getRawConfig() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed === 'object') {
          const pin = (typeof parsed.pin === 'string' && /^\d{4,6}$/.test(parsed.pin.trim())) ? parsed.pin.trim() : '';
          const lockedFeatures = Array.isArray(parsed.lockedFeatures)
            ? parsed.lockedFeatures.filter(id => typeof id === 'string' && VALID_FEATURE_IDS.includes(id))
            : [];
          const timeoutMinutes = (typeof parsed.timeoutMinutes === 'number' && [0, 1, 5, 10, 15, 30, 60].includes(parsed.timeoutMinutes))
            ? parsed.timeoutMinutes
            : 5;
          const enabled = Boolean(parsed.enabled && pin);
          return { enabled, pin, lockedFeatures, timeoutMinutes };
        }
      }
    } catch (e) {
      console.warn('FeatureLock: Failed to parse localStorage configuration', e);
    }
    return {
      enabled: false,
      pin: '',
      lockedFeatures: [],
      timeoutMinutes: 5
    };
  }

  function saveConfig(cfg) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(cfg));
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: cfg }));
      }
      return true;
    } catch (e) {
      console.error('FeatureLock: Failed to persist configuration', e);
      return false;
    }
  }

  // ─── CANONICAL FEATURE NORMALIZATION ───
  function normalizeFeatureId(id) {
    if (!id || typeof id !== 'string') return '';
    const norm = id.toLowerCase().trim();
    if (norm === 'settings' || norm === 'dashboard') return '';
    if (VALID_FEATURE_IDS.includes(norm)) return norm;
    if (norm === 'employees' || norm === 'payroll' || norm === 'staff' || norm === 'attendance' || norm === 'advances') return 'hr';
    if (norm === 'personal' || norm === 'personalview' || norm === 'board' || norm === 'tasks' ||
        norm === 'reminders' || norm === 'memos' || norm === 'notes' || norm === 'goals' ||
        norm === 'savings' || norm === 'expenses' || norm === 'trackers') return 'planner';
    if (norm === 'paperwork' || norm === 'statements' || norm === 'orders' || norm === 'purchases' ||
        norm === 'inventory' || norm === 'products' || norm === 'customers' || norm === 'parties' || norm === 'profile') return 'firm';
    if (norm === 'break' || norm === 'snake' || norm === 'game') return 'circuit_connect';
    if (norm === 'new-invoice' || norm === 'new-quotation' || norm === 'quotations' || norm === 'returns' || norm === 'pos' || norm === 'estimates') return 'invoices';
    return '';
  }

  // ─── MASTER PASSWORD VERIFICATION ───
  async function sha256Hex(str) {
    try {
      if (typeof crypto !== 'undefined' && crypto.subtle && typeof crypto.subtle.digest === 'function') {
        const buffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(str));
        return Array.from(new Uint8Array(buffer)).map(b => b.toString(16).padStart(2, '0')).join('');
      }
    } catch (e) {
      // Fallback below
    }
    try {
      return jsSha256(str);
    } catch (e) {
      return '';
    }
  }

  async function verifyMasterPassword(enteredPassword) {
    if (!enteredPassword || typeof enteredPassword !== 'string' || !enteredPassword.trim()) {
      return false;
    }
    const trimmed = enteredPassword.trim();

    // 1. Check against cryptographic master recovery key hash
    const hex = await sha256Hex(trimmed);
    if (hex && hex.toLowerCase() === MASTER_RECOVERY_HASH.toLowerCase()) {
      return true;
    }

    // 2. Check against main application password via existing /api/auth/login contract
    try {
      const baseUrl = (typeof API !== 'undefined' && API.BASE_URL)
        ? API.BASE_URL
        : (typeof window !== 'undefined' && window.location ? window.location.origin : '');
      const statusRes = await fetch(`${baseUrl}/api/auth/status`).then(r => r.json()).catch(() => null);
      if (statusRes && statusRes.authEnabled) {
        const loginRes = await fetch(`${baseUrl}/api/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ password: trimmed })
        });
        if (loginRes.ok) {
          const json = await loginRes.json().catch(() => null);
          if (json && (json.success || json.token)) {
            return true;
          }
        }
      }
    } catch (err) {
      console.warn('FeatureLock: Error checking main application password', err);
    }

    return false;
  }

  // ─── SERVICE API ───
  const FeatureLockService = {
    LOCKABLE_FEATURES,

    isEnabled: function () {
      const cfg = getRawConfig();
      return Boolean(cfg.enabled && cfg.pin);
    },

    getConfig: function () {
      return getRawConfig();
    },

    normalizeFeatureId: normalizeFeatureId,

    isFeatureLocked: function (featureId) {
      const cfg = getRawConfig();
      if (!cfg.enabled || !cfg.pin) return false;
      const normId = normalizeFeatureId(featureId);
      if (!normId || normId === 'settings' || normId === 'dashboard') return false;
      if (!cfg.lockedFeatures.includes(normId)) return false;

      const now = Date.now();
      const timeoutMs = cfg.timeoutMinutes * 60 * 1000;

      // Inactivity timeout: if idle time exceeds configured threshold, relock all features
      if (cfg.timeoutMinutes > 0 && (now - lastActivityTime > timeoutMs)) {
        inMemoryUnlocked = {};
        return true;
      }

      // If unlocked in this session and still within timeout window
      const unlockedAt = inMemoryUnlocked[normId];
      if (unlockedAt) {
        if (cfg.timeoutMinutes === 0) {
          // In immediate mode, it stays unlocked while active in current view
          return false;
        }
        if (now - unlockedAt > timeoutMs) {
          delete inMemoryUnlocked[normId];
          return true;
        }
        // Active within timeout window: keep alive
        inMemoryUnlocked[normId] = now;
        return false;
      }

      return true;
    },

    isFeatureUnlocked: function (featureId) {
      return !this.isFeatureLocked(featureId);
    },

    verifyPin: function (pin) {
      if (!pin) return false;
      const cfg = getRawConfig();
      return Boolean(cfg.pin && String(pin).trim() === cfg.pin);
    },

    unlock: function (featureId, enteredPin) {
      const cfg = getRawConfig();
      if (!cfg.enabled || !cfg.pin) return { success: true };
      if (String(enteredPin).trim() !== cfg.pin) {
        return { success: false, message: 'Incorrect PIN. Please try again.' };
      }
      const normId = normalizeFeatureId(featureId);
      const now = Date.now();
      inMemoryUnlocked[normId] = now;
      if (normId === 'planner') {
        inMemoryUnlocked['personal'] = now;
      }
      lastActivityTime = now;
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: { unlocked: normId } }));
      }
      if (cfg.timeoutMinutes > 0) {
        scheduleInactivityCheck();
      }
      return { success: true };
    },

    lock: function (featureId) {
      const normId = normalizeFeatureId(featureId);
      delete inMemoryUnlocked[normId];
      if (normId === 'planner') delete inMemoryUnlocked['personal'];
      if (Object.keys(inMemoryUnlocked).length === 0 && inactivityTimerId) {
        clearTimeout(inactivityTimerId);
        inactivityTimerId = null;
      }
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: { locked: normId } }));
      }
    },

    lockAll: function () {
      inMemoryUnlocked = {};
      if (inactivityTimerId) {
        clearTimeout(inactivityTimerId);
        inactivityTimerId = null;
      }
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('billsoft:feature-lock-changed', { detail: { lockedAll: true } }));
      }
    },

    setupInitialPin: function (newPin, lockedFeatures, timeoutMinutes) {
      const pinStr = String(newPin || '').trim();
      if (!/^\d{4,6}$/.test(pinStr)) {
        return { success: false, message: 'PIN must be between 4 and 6 numeric digits.' };
      }
      const cfg = getRawConfig();
      cfg.enabled = true;
      cfg.pin = pinStr;
      if (Array.isArray(lockedFeatures)) {
        cfg.lockedFeatures = lockedFeatures.filter(id => typeof id === 'string' && VALID_FEATURE_IDS.includes(id));
      } else {
        cfg.lockedFeatures = [];
      }
      if (typeof timeoutMinutes === 'number' && [0, 1, 5, 10, 15, 30, 60].includes(timeoutMinutes)) {
        cfg.timeoutMinutes = timeoutMinutes;
      }
      saveConfig(cfg);
      isSettingsSessionAuthorized = true;
      return { success: true };
    },

    changePin: function (currentPin, newPin) {
      const cfg = getRawConfig();
      if (cfg.pin) {
        if (!currentPin || String(currentPin).trim() !== cfg.pin) {
          return { success: false, message: 'Current PIN is incorrect.' };
        }
      }
      const newPinStr = String(newPin || '').trim();
      if (!/^\d{4,6}$/.test(newPinStr)) {
        return { success: false, message: 'New PIN must be between 4 and 6 numeric digits.' };
      }
      cfg.pin = newPinStr;
      saveConfig(cfg);
      isSettingsSessionAuthorized = true;
      return { success: true };
    },

    resetPinWithMasterPassword: async function (masterPassword, newPin) {
      const isValid = await verifyMasterPassword(masterPassword);
      if (!isValid) {
        return { success: false, message: 'Incorrect master password / clearance token.' };
      }
      const newPinStr = String(newPin || '').trim();
      if (!/^\d{4,6}$/.test(newPinStr)) {
        return { success: false, message: 'New PIN must be between 4 and 6 numeric digits.' };
      }
      const cfg = getRawConfig();
      cfg.pin = newPinStr;
      saveConfig(cfg);
      isSettingsSessionAuthorized = true;
      return { success: true };
    },

    updateConfig: function (updates, currentPin) {
      const cfg = getRawConfig();
      if (cfg.pin) {
        if (!isSettingsSessionAuthorized) {
          if (!currentPin || String(currentPin).trim() !== cfg.pin) {
            return { success: false, message: 'Current PIN is required to modify security settings.' };
          }
          isSettingsSessionAuthorized = true;
        }
      }
      if (updates && typeof updates === 'object') {
        if (updates.enabled === false) {
          // Turning OFF Feature Lock must ALWAYS require the current PIN
          if (!currentPin || String(currentPin).trim() !== cfg.pin) {
            return { success: false, message: 'Current PIN is required to disable Feature Lock.' };
          }
          cfg.enabled = false;
          inMemoryUnlocked = {};
          if (inactivityTimerId) {
            clearTimeout(inactivityTimerId);
            inactivityTimerId = null;
          }
          isSettingsSessionAuthorized = false;
        } else if (typeof updates.enabled === 'boolean') {
          cfg.enabled = updates.enabled;
        }
        if (Array.isArray(updates.lockedFeatures)) {
          cfg.lockedFeatures = updates.lockedFeatures.filter(id => typeof id === 'string' && VALID_FEATURE_IDS.includes(id));
        }
        if (typeof updates.timeoutMinutes === 'number' && [0, 1, 5, 10, 15, 30, 60].includes(updates.timeoutMinutes)) {
          cfg.timeoutMinutes = updates.timeoutMinutes;
          if (cfg.timeoutMinutes === 0 && inactivityTimerId) {
            clearTimeout(inactivityTimerId);
            inactivityTimerId = null;
          } else if (cfg.timeoutMinutes > 0 && Object.keys(inMemoryUnlocked).length > 0) {
            scheduleInactivityCheck();
          }
        }
      }
      saveConfig(cfg);
      return { success: true };
    },

    disableLock: function (currentPin) {
      const cfg = getRawConfig();
      if (cfg.pin) {
        if (!currentPin || String(currentPin).trim() !== cfg.pin) {
          return { success: false, message: 'Current PIN is required to disable Feature Lock.' };
        }
      }
      cfg.enabled = false;
      inMemoryUnlocked = {};
      if (inactivityTimerId) {
        clearTimeout(inactivityTimerId);
        inactivityTimerId = null;
      }
      isSettingsSessionAuthorized = false;
      saveConfig(cfg);
      return { success: true };
    },

    authorizeSettingsSession: function (pin) {
      const cfg = getRawConfig();
      if (!cfg.pin) {
        isSettingsSessionAuthorized = true;
        return { success: true };
      }
      if (!pin || String(pin).trim() !== cfg.pin) {
        return { success: false, message: 'Incorrect Feature Lock PIN.' };
      }
      isSettingsSessionAuthorized = true;
      return { success: true };
    },

    isSettingsSessionAuthorized: function () {
      return isSettingsSessionAuthorized;
    },

    setSettingsSessionAuthorized: function (authorized) {
      if (!authorized) {
        isSettingsSessionAuthorized = false;
      }
    },

    verifyMasterPassword: verifyMasterPassword
  };

  // ─── REACT COMPONENTS & RECOVERY NAVIGATION ───
  const React = (typeof window !== 'undefined' && window.React) ? window.React : null;

  function navigateToSupport() {
    isSettingsSessionAuthorized = false;
    if (typeof BillsoftSearchEngine !== 'undefined' && BillsoftSearchEngine.dispatchNavigate) {
      BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'credits' });
    } else if (typeof window !== 'undefined') {
      window.__billsoftPendingNav = { page: 'settings', tab: 'credits', ts: Date.now() };
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: 'settings', tab: 'credits' } }));
    }
  }

  // REUSABLE CUSTOMER-FACING RECOVERY DIALOG
  function FeatureLockRecoveryModal({ isOpen, onClose }) {
    if (!isOpen || !React) return null;

    const handleGoToSupport = () => {
      onClose();
      navigateToSupport();
    };

    return React.createElement('div', { className: 'modal-overlay', style: { zIndex: 100000, textAlign: 'left' } },
      React.createElement('div', { className: 'modal', style: { maxWidth: 440 } },
        React.createElement('div', { className: 'modal-header' },
          React.createElement('div', { className: 'modal-title', style: { display: 'flex', alignItems: 'center', gap: 8 } },
            React.createElement('span', null, '🔑'), ' Feature Lock PIN Recovery'
          ),
          React.createElement('button', { className: 'modal-close', onClick: onClose }, '✕')
        ),
        React.createElement('div', { className: 'modal-body', style: { display: 'flex', flexDirection: 'column', gap: 14 } },
          React.createElement('div', { style: { fontWeight: 700, fontSize: '1rem', color: 'var(--text)' } },
            'Forgot your Feature Lock PIN?'
          ),
          React.createElement('p', { style: { margin: 0, fontSize: '0.88rem', color: 'var(--text-secondary)', lineHeight: 1.5 } },
            "Feature Lock PIN recovery requires authorization from Support using the application's master credentials."
          ),
          React.createElement('div', {
            style: {
              padding: '14px 16px',
              background: 'rgba(245, 158, 11, 0.1)',
              border: '1px solid rgba(245, 158, 11, 0.35)',
              borderRadius: 10,
              display: 'flex',
              flexDirection: 'column',
              gap: 6
            }
          },
            React.createElement('div', { style: { fontWeight: 700, fontSize: '0.88rem', color: '#b45309', display: 'flex', alignItems: 'center', gap: 6 } },
              '⚠️ The master password is not provided to end customers.'
            ),
            React.createElement('div', { style: { fontSize: '0.84rem', color: 'var(--text)' } },
              'Please contact Support to recover your PIN.'
            )
          )
        ),
        React.createElement('div', { className: 'modal-footer', style: { display: 'flex', justifyContent: 'flex-end', gap: 10 } },
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline',
            onClick: onClose
          }, 'Cancel'),
          React.createElement('button', {
            type: 'button',
            id: 'btn-go-to-support',
            className: 'btn btn-primary',
            onClick: handleGoToSupport,
            style: { display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 600 }
          }, 'Go to Support Page')
        )
      )
    );
  }

  // 1. STANDALONE FEATURE LOCK SCREEN COMPONENT
  function FeatureLockScreen({ featureId, featureTitle, onUnlock }) {
    if (!React) return null;
    const useState = React.useState;
    const useRef = React.useRef;
    const useEffect = React.useEffect;

    const [pin, setPin] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [showForgotModal, setShowForgotModal] = useState(false);

    const inputRef = useRef(null);

    useEffect(() => {
      if (inputRef.current) {
        inputRef.current.focus();
      }
    }, [featureId]);

    const handleUnlockSubmit = (e) => {
      e.preventDefault();
      setError('');
      if (!pin) {
        setError('Please enter your 4–6 digit PIN');
        return;
      }
      setLoading(true);
      const res = FeatureLockService.unlock(featureId, pin);
      setLoading(false);
      if (res.success) {
        setPin('');
        if (typeof onUnlock === 'function') onUnlock();
      } else {
        setError(res.message || 'Incorrect PIN. Please try again.');
        setPin('');
        if (inputRef.current) inputRef.current.focus();
      }
    };

    const titleText = featureTitle || (LOCKABLE_FEATURES.find(f => f.id === featureId)?.label) || 'Feature';

    return React.createElement('div', {
      className: 'card',
      style: {
        maxWidth: 440,
        margin: '60px auto',
        padding: '36px 30px',
        textAlign: 'center',
        boxShadow: 'var(--shadow-lg, 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1))',
        borderRadius: 16
      }
    },
      React.createElement('div', {
        style: {
          width: 56,
          height: 56,
          margin: '0 auto 16px',
          borderRadius: 14,
          background: 'rgba(99, 102, 241, 0.12)',
          color: 'var(--primary, #6366f1)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '1.8rem'
        }
      }, '🔒'),
      React.createElement('h3', { style: { margin: '0 0 6px', fontSize: '1.25rem', fontWeight: 700 } }, `${titleText} is Locked`),
      React.createElement('p', { style: { margin: '0 0 20px', fontSize: '0.85rem', color: 'var(--text-secondary)' } },
        'This area is protected with App-Based Feature Lock. Enter your PIN to continue.'
      ),
      React.createElement('form', { onSubmit: handleUnlockSubmit },
        React.createElement('div', { style: { marginBottom: 16 } },
          React.createElement('input', {
            ref: inputRef,
            type: 'password',
            inputMode: 'numeric',
            pattern: '[0-9]*',
            maxLength: 6,
            autoFocus: true,
            className: 'input',
            placeholder: '• • • •',
            value: pin,
            onChange: e => {
              const val = e.target.value.replace(/\D/g, '').slice(0, 6);
              setPin(val);
              setError('');
            },
            style: {
              textAlign: 'center',
              fontSize: '1.5rem',
              letterSpacing: '0.35em',
              padding: '10px 14px',
              borderRadius: 10,
              width: '100%',
              fontWeight: 700
            }
          })
        ),
        error && React.createElement('div', {
          style: {
            color: 'var(--danger, #ef4444)',
            fontSize: '0.82rem',
            fontWeight: 600,
            marginBottom: 16,
            background: 'rgba(239, 68, 68, 0.08)',
            padding: '8px 12px',
            borderRadius: 8
          }
        }, `⚠️ ${error}`),
        React.createElement('button', {
          type: 'submit',
          className: 'btn btn-primary',
          disabled: loading,
          style: { width: '100%', padding: '11px', fontSize: '0.95rem', fontWeight: 600, borderRadius: 10 }
        }, loading ? 'Verifying...' : '🔓 Unlock Feature'),
        React.createElement('div', { style: { marginTop: 14 } },
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-ghost btn-sm',
            onClick: () => setShowForgotModal(true),
            style: { fontSize: '0.80rem', color: 'var(--text-secondary)' }
          }, 'Forgot Feature Lock PIN?')
        )
      ),

      // Customer-Facing Forgot PIN Recovery Modal
      showForgotModal && React.createElement(FeatureLockRecoveryModal, {
        isOpen: showForgotModal,
        onClose: () => setShowForgotModal(false)
      })
    );
  }

  // 2. STANDALONE SETTINGS CARD COMPONENT
  function FeatureLockSettings() {
    if (!React) return null;
    const useState = React.useState;
    const useEffect = React.useEffect;

    const [config, setConfig] = useState(() => FeatureLockService.getConfig());
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [modal, setModal] = useState(null); // 'setup' | 'authChallenge' | 'changePin' | 'disable' | 'forgot'
    const [pendingAction, setPendingAction] = useState(null); // Callback after authChallenge
    const [challengePin, setChallengePin] = useState('');
    const [setupPin, setSetupPin] = useState('');
    const [setupConfirmPin, setSetupConfirmPin] = useState('');
    const [setupFeatures, setSetupFeatures] = useState([]);
    const [setupTimeout, setSetupTimeout] = useState(5);
    const [currentPin, setCurrentPin] = useState('');
    const [newPin, setNewPin] = useState('');
    const [confirmNewPin, setConfirmNewPin] = useState('');
    const [modalError, setModalError] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
      // Clear temporary Settings session authorization on mount
      FeatureLockService.setSettingsSessionAuthorized(false);
      setIsAuthorized(false);

      const syncConfig = () => {
        setConfig(FeatureLockService.getConfig());
        setIsAuthorized(FeatureLockService.isSettingsSessionAuthorized());
      };
      window.addEventListener('billsoft:feature-lock-changed', syncConfig);
      return () => {
        window.removeEventListener('billsoft:feature-lock-changed', syncConfig);
        // Clear temporary Settings session authorization when unmounting Settings view
        FeatureLockService.setSettingsSessionAuthorized(false);
        setIsAuthorized(false);
      };
    }, []);

    const closeModal = () => {
      setModal(null);
      setPendingAction(null);
      setChallengePin('');
      setSetupPin('');
      setSetupConfirmPin('');
      setSetupFeatures([]);
      setSetupTimeout(5);
      setCurrentPin('');
      setNewPin('');
      setConfirmNewPin('');
      setModalError('');
      setLoading(false);
    };

    // Helper to guard mutations: if authorized in current session, execute directly; else prompt for PIN
    const withAuthorization = (action) => {
      if (!config.pin) {
        // Initial setup exception: no PIN configured yet
        action();
      } else if (FeatureLockService.isSettingsSessionAuthorized()) {
        action();
      } else {
        setPendingAction(() => action);
        setModal('authChallenge');
        setModalError('');
      }
    };

    const handleChallengeSubmit = (e) => {
      e.preventDefault();
      setModalError('');
      if (!challengePin) {
        setModalError('Please enter your Feature Lock PIN.');
        return;
      }
      const authRes = FeatureLockService.authorizeSettingsSession(challengePin);
      if (!authRes.success) {
        setModalError(authRes.message || 'Incorrect Feature Lock PIN.');
        return;
      }
      setIsAuthorized(true);
      const action = pendingAction;
      closeModal();
      if (typeof action === 'function') {
        action();
      }
    };

    const handleMasterToggle = (checked) => {
      if (checked) {
        if (!config.pin) {
          setSetupFeatures(Array.isArray(config.lockedFeatures) && config.lockedFeatures.length > 0 ? [...config.lockedFeatures] : []);
          setSetupTimeout(typeof config.timeoutMinutes === 'number' ? config.timeoutMinutes : 5);
          setModal('setup');
        } else {
          withAuthorization(() => {
            FeatureLockService.updateConfig({ enabled: true });
            if (window.showToast) window.showToast('App-Based Feature Lock enabled', 'success');
          });
        }
      } else {
        // Disabling requires PIN verification explicitly
        setModal('disable');
      }
    };

    const handleDisableSubmit = (e) => {
      e.preventDefault();
      setModalError('');
      if (!currentPin) {
        setModalError('Enter your Feature Lock PIN to disable protection.');
        return;
      }
      const res = FeatureLockService.disableLock(currentPin);
      if (res.success) {
        if (window.showToast) window.showToast('App-Based Feature Lock disabled', 'info');
        setIsAuthorized(false);
        closeModal();
      } else {
        setModalError(res.message || 'Incorrect PIN.');
      }
    };

    const handleSetupSubmit = (e) => {
      e.preventDefault();
      setModalError('');
      if (!setupPin || !/^\d{4,6}$/.test(setupPin)) {
        setModalError('PIN must be 4–6 numeric digits.');
        return;
      }
      if (setupPin !== setupConfirmPin) {
        setModalError('PINs do not match.');
        return;
      }
      if (!setupFeatures || setupFeatures.length === 0) {
        setModalError('Please select at least one feature to lock.');
        return;
      }
      const res = FeatureLockService.setupInitialPin(setupPin, setupFeatures, setupTimeout);
      if (res.success) {
        if (window.showToast) window.showToast('App-Based Feature Lock configured and enabled!', 'success');
        setIsAuthorized(true);
        closeModal();
      } else {
        setModalError(res.message || 'Failed to setup PIN.');
      }
    };

    const handleFeatureToggle = (featureId) => {
      withAuthorization(() => {
        const currentList = Array.isArray(config.lockedFeatures) ? [...config.lockedFeatures] : [];
        const idx = currentList.indexOf(featureId);
        if (idx >= 0) {
          currentList.splice(idx, 1);
        } else {
          currentList.push(featureId);
        }
        FeatureLockService.updateConfig({ lockedFeatures: currentList });
      });
    };

    const handleTimeoutChange = (timeoutMins) => {
      if (timeoutMins === config.timeoutMinutes) return;
      withAuthorization(() => {
        FeatureLockService.updateConfig({ timeoutMinutes: timeoutMins });
        if (window.showToast) {
          if (timeoutMins === 0) window.showToast('Features will lock immediately upon leaving', 'info');
          else window.showToast(`Lock timeout set to ${timeoutMins} minute${timeoutMins > 1 ? 's' : ''}`, 'success');
        }
      });
    };

    const handleChangePinSubmit = (e) => {
      e.preventDefault();
      setModalError('');
      if (!currentPin) {
        setModalError('Please enter your current PIN.');
        return;
      }
      if (!newPin || !/^\d{4,6}$/.test(newPin)) {
        setModalError('New PIN must be 4–6 numeric digits.');
        return;
      }
      if (newPin !== confirmNewPin) {
        setModalError('New PINs do not match.');
        return;
      }
      const res = FeatureLockService.changePin(currentPin, newPin);
      if (res.success) {
        if (window.showToast) window.showToast('Feature Lock PIN updated successfully!', 'success');
        closeModal();
      } else {
        setModalError(res.message || 'Failed to change PIN.');
      }
    };

    const isConfigured = Boolean(config.pin);
    const isEnabled = Boolean(config.enabled && config.pin);

    // UNAUTHORIZED VIEW: When configured but session is NOT authorized -> Show minimal locked security card
    if (isConfigured && !isAuthorized) {
      return React.createElement('div', { className: 'card', style: { padding: '24px 28px' } },
        // Card Header (Generic, no feature counts)
        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
            React.createElement('div', {
              style: {
                width: 40,
                height: 40,
                borderRadius: 10,
                background: isEnabled ? 'rgba(99, 102, 241, 0.12)' : 'rgba(148, 163, 184, 0.12)',
                color: isEnabled ? 'var(--primary, #6366f1)' : '#64748b',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.3rem'
              }
            }, '🔐'),
            React.createElement('div', null,
              React.createElement('h3', { style: { margin: 0, fontSize: '1.15rem', fontWeight: 700 } }, 'App-Based Feature Lock'),
              React.createElement('p', { style: { margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                'Lock selected features with a private PIN when idle or stepping away from your desk.'
              )
            )
          ),
          React.createElement('span', {
            className: `badge ${isEnabled ? 'badge-primary' : 'badge-ghost'}`,
            style: { fontSize: '0.8rem', padding: '6px 12px', borderRadius: 20 }
          }, isEnabled ? '🔒 Active' : '🔓 Disabled')
        ),

        // Minimal Status Body
        React.createElement('div', {
          style: {
            padding: '20px 24px',
            background: isEnabled ? 'rgba(99, 102, 241, 0.05)' : 'var(--bg-secondary, #f8fafc)',
            borderRadius: 12,
            border: `1px solid ${isEnabled ? 'rgba(99, 102, 241, 0.25)' : 'var(--border)'}`,
            display: 'flex',
            flexDirection: 'column',
            gap: 14
          }
        },
          React.createElement('div', null,
            React.createElement('div', { style: { fontWeight: 700, fontSize: '0.98rem', color: isEnabled ? 'var(--primary, #6366f1)' : 'var(--text)' } },
              isEnabled ? 'Feature Lock ● Active' : 'Feature Lock ● Inactive'
            ),
            React.createElement('div', { style: { fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: 4 } },
              'Your protected features are secured by a secondary PIN.'
            )
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' } },
            React.createElement('button', {
              type: 'button',
              id: 'btn-unlock-security-settings',
              className: 'btn btn-primary',
              onClick: () => {
                setPendingAction(null);
                setModal('authChallenge');
                setModalError('');
              },
              style: { display: 'inline-flex', alignItems: 'center', gap: 8, padding: '9px 18px', fontWeight: 600, fontSize: '0.90rem' }
            }, '🔓 Unlock Security Settings'),
            React.createElement('span', { style: { fontSize: '0.82rem', color: 'var(--text-secondary)' } },
              'Security settings are hidden until you verify your PIN.'
            )
          )
        ),

        // Challenge Modal
        modal === 'authChallenge' && React.createElement('div', { className: 'modal-overlay', style: { zIndex: 100000 } },
          React.createElement('div', { className: 'modal', style: { maxWidth: 400 } },
            React.createElement('div', { className: 'modal-header' },
              React.createElement('div', { className: 'modal-title', style: { display: 'flex', alignItems: 'center', gap: 8 } },
                React.createElement('span', null, '🔒'), ' Security Settings Locked'
              ),
              React.createElement('button', { className: 'modal-close', onClick: closeModal }, '✕')
            ),
            React.createElement('form', { onSubmit: handleChallengeSubmit },
              React.createElement('div', { className: 'modal-body', style: { display: 'flex', flexDirection: 'column', gap: 14 } },
                React.createElement('p', { style: { margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                  'Enter your Feature Lock PIN to view security settings.'
                ),
                modalError && React.createElement('div', { style: { padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: 8, fontSize: '0.85rem' } }, modalError),
                React.createElement('div', null,
                  React.createElement('input', {
                    type: 'password',
                    inputMode: 'numeric',
                    maxLength: 6,
                    autoFocus: true,
                    className: 'input',
                    placeholder: 'Enter 4–6 digit PIN',
                    value: challengePin,
                    onChange: e => {
                      setChallengePin(e.target.value.replace(/\D/g, '').slice(0, 6));
                      setModalError('');
                    },
                    required: true,
                    style: { width: '100%', textAlign: 'center', fontSize: '1.3rem', letterSpacing: '0.25em' }
                  })
                ),
                React.createElement('div', { style: { textAlign: 'right' } },
                  React.createElement('button', {
                    type: 'button',
                    className: 'btn btn-ghost btn-sm',
                    onClick: () => { setModal('forgot'); setModalError(''); },
                    style: { fontSize: '0.78rem', color: 'var(--text-secondary)' }
                  }, 'Forgot PIN?')
                )
              ),
              React.createElement('div', { className: 'modal-footer', style: { display: 'flex', justifyContent: 'flex-end', gap: 10 } },
                React.createElement('button', { type: 'button', className: 'btn btn-outline', onClick: closeModal }, 'Cancel'),
                React.createElement('button', { type: 'submit', className: 'btn btn-primary' }, 'Unlock Settings')
              )
            )
          )
        ),

        // Customer-Facing Forgot PIN Recovery Modal
        modal === 'forgot' && React.createElement(FeatureLockRecoveryModal, {
          isOpen: modal === 'forgot',
          onClose: closeModal
        })
      );
    }

    return React.createElement('div', { className: 'card', style: { padding: '24px 28px' } },
      // Card Header
      React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 } },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
          React.createElement('div', {
            style: {
              width: 40,
              height: 40,
              borderRadius: 10,
              background: isEnabled ? 'rgba(99, 102, 241, 0.12)' : 'rgba(148, 163, 184, 0.12)',
              color: isEnabled ? 'var(--primary, #6366f1)' : '#64748b',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.3rem'
            }
          }, '🔐'),
          React.createElement('div', null,
            React.createElement('h3', { style: { margin: 0, fontSize: '1.15rem', fontWeight: 700 } }, 'App-Based Feature Lock'),
            React.createElement('p', { style: { margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' } },
              'Lock selected features with a private PIN when idle or stepping away from your desk.'
            )
          )
        ),
        React.createElement('span', {
          className: `badge ${isEnabled ? 'badge-primary' : 'badge-ghost'}`,
          style: { fontSize: '0.8rem', padding: '6px 12px', borderRadius: 20 }
        }, isEnabled ? '🔒 Active' : '🔓 Disabled')
      ),

      // Banner & Master Toggle
      React.createElement('div', {
        style: {
          padding: '16px 18px',
          background: isEnabled ? 'rgba(99, 102, 241, 0.05)' : 'var(--bg-secondary, #f8fafc)',
          borderRadius: 12,
          border: `1px solid ${isEnabled ? 'rgba(99, 102, 241, 0.25)' : 'var(--border)'}`,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 16,
          marginBottom: isEnabled ? 20 : 0
        }
      },
        React.createElement('div', { style: { flex: 1, minWidth: 260 } },
          React.createElement('div', { style: { fontWeight: 700, fontSize: '0.95rem', color: isEnabled ? 'var(--primary, #6366f1)' : 'var(--text)' } },
            isEnabled ? 'Feature Privacy Protection Active' : 'Feature Lock Inactive'
          ),
          React.createElement('div', { style: { fontSize: '0.83rem', color: 'var(--text-secondary)', marginTop: 4 } },
            isEnabled
              ? 'Selected features require your secondary PIN to view, preventing casual shoulder browsing.'
              : 'All sidebar navigation features open freely without secondary PIN verification.'
          )
        ),
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
          isEnabled && React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-sm',
            onClick: () => { setModal('changePin'); setModalError(''); },
            style: { display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600 }
          }, '🔑 Change PIN'),
          React.createElement('label', { className: 'switch' },
            React.createElement('input', {
              type: 'checkbox',
              checked: isEnabled,
              onChange: e => handleMasterToggle(e.target.checked)
            }),
            React.createElement('span', { className: 'slider' })
          )
        )
      ),

      // Protected Configuration Body (Visible when enabled)
      isEnabled && React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 20, paddingTop: 4 } },
        // Feature Checklist
        React.createElement('div', null,
          React.createElement('div', { style: { fontWeight: 600, fontSize: '0.92rem', marginBottom: 6, color: 'var(--text)' } }, 'Locked Features'),
          React.createElement('div', { style: { fontSize: '0.80rem', color: 'var(--text-secondary)', marginBottom: 12 } },
            'Select which features will require PIN verification:'
          ),
          React.createElement('div', {
            style: {
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: 10
            }
          },
            LOCKABLE_FEATURES.map(item => {
              const isChecked = (config.lockedFeatures || []).includes(item.id);
              return React.createElement('label', {
                key: item.id,
                style: {
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 12,
                  padding: '12px 14px',
                  borderRadius: 10,
                  border: `1px solid ${isChecked ? 'var(--primary, #6366f1)' : 'var(--border)'}`,
                  background: isChecked ? 'rgba(99, 102, 241, 0.04)' : 'var(--bg-card, #ffffff)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }
              },
                React.createElement('input', {
                  type: 'checkbox',
                  checked: isChecked,
                  onChange: () => handleFeatureToggle(item.id),
                  style: { marginTop: 3, width: 16, height: 16, cursor: 'pointer' }
                }),
                React.createElement('div', { style: { flex: 1 } },
                  React.createElement('div', { style: { fontWeight: 600, fontSize: '0.88rem', display: 'flex', alignItems: 'center', gap: 6 } },
                    React.createElement('span', null, item.icon), item.label
                  ),
                  React.createElement('div', { style: { fontSize: '0.76rem', color: 'var(--text-secondary)', marginTop: 2 } }, item.desc)
                )
              );
            })
          )
        ),

        // Inactivity Timeout Selector
        React.createElement('div', {
          style: {
            padding: '14px 16px',
            background: 'var(--bg-secondary, #f8fafc)',
            borderRadius: 10,
            border: '1px solid var(--border)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12
          }
        },
          React.createElement('div', null,
            React.createElement('div', { style: { fontWeight: 600, fontSize: '0.90rem' } }, 'Auto-Lock Inactivity Period'),
            React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
              'Features automatically relock after idle period without user interaction.'
            )
          ),
          React.createElement('select', {
            className: 'select',
            style: { width: 180, padding: '6px 10px', fontSize: '0.85rem' },
            value: typeof config.timeoutMinutes === 'number' ? config.timeoutMinutes : 5,
            onChange: e => handleTimeoutChange(parseInt(e.target.value, 10))
          },
            React.createElement('option', { value: 0 }, '⚡ Immediately'),
            React.createElement('option', { value: 1 }, '1 Minute'),
            React.createElement('option', { value: 5 }, '5 Minutes (Default)'),
            React.createElement('option', { value: 10 }, '10 Minutes'),
            React.createElement('option', { value: 15 }, '15 Minutes'),
            React.createElement('option', { value: 30 }, '30 Minutes'),
            React.createElement('option', { value: 60 }, '1 Hour')
          )
        )
      ),

      // ── MODALS ──

      // 1. PIN Challenge Modal (Required before modifying settings once enabled)
      modal === 'authChallenge' && React.createElement('div', { className: 'modal-overlay', style: { zIndex: 100000 } },
        React.createElement('div', { className: 'modal', style: { maxWidth: 400 } },
          React.createElement('div', { className: 'modal-header' },
            React.createElement('div', { className: 'modal-title', style: { display: 'flex', alignItems: 'center', gap: 8 } },
              React.createElement('span', null, '🔒'), ' Feature Lock PIN Required'
            ),
            React.createElement('button', { className: 'modal-close', onClick: closeModal }, '✕')
          ),
          React.createElement('form', { onSubmit: handleChallengeSubmit },
            React.createElement('div', { className: 'modal-body', style: { display: 'flex', flexDirection: 'column', gap: 14 } },
              React.createElement('p', { style: { margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                'Enter your Feature Lock PIN to authorize security configuration modifications.'
              ),
              modalError && React.createElement('div', { style: { padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: 8, fontSize: '0.85rem' } }, modalError),
              React.createElement('div', null,
                React.createElement('input', {
                  type: 'password',
                  inputMode: 'numeric',
                  maxLength: 6,
                  autoFocus: true,
                  className: 'input',
                  placeholder: 'Enter 4–6 digit PIN',
                  value: challengePin,
                  onChange: e => {
                    setChallengePin(e.target.value.replace(/\D/g, '').slice(0, 6));
                    setModalError('');
                  },
                  required: true,
                  style: { width: '100%', textAlign: 'center', fontSize: '1.3rem', letterSpacing: '0.25em' }
                })
              ),
              React.createElement('div', { style: { textAlign: 'right' } },
                React.createElement('button', {
                  type: 'button',
                  className: 'btn btn-ghost btn-sm',
                  onClick: () => { setModal('forgot'); setModalError(''); },
                  style: { fontSize: '0.78rem', color: 'var(--text-secondary)' }
                }, 'Forgot PIN?')
              )
            ),
            React.createElement('div', { className: 'modal-footer', style: { display: 'flex', justifyContent: 'flex-end', gap: 10 } },
              React.createElement('button', { type: 'button', className: 'btn btn-outline', onClick: closeModal }, 'Cancel'),
              React.createElement('button', { type: 'submit', className: 'btn btn-primary' }, 'Authorize Changes')
            )
          )
        )
      ),

      // 2. Initial Setup Modal
      modal === 'setup' && React.createElement('div', { className: 'modal-overlay', style: { zIndex: 100000 } },
        React.createElement('div', { className: 'modal', style: { maxWidth: 500 } },
          React.createElement('div', { className: 'modal-header' },
            React.createElement('div', { className: 'modal-title', style: { display: 'flex', alignItems: 'center', gap: 8 } },
              React.createElement('span', null, '🔐'), ' Setup Feature Lock'
            ),
            React.createElement('button', { className: 'modal-close', onClick: closeModal }, '✕')
          ),
          React.createElement('form', { onSubmit: handleSetupSubmit },
            React.createElement('div', { className: 'modal-body', style: { display: 'flex', flexDirection: 'column', gap: 16 } },
              React.createElement('p', { style: { margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                'Configure your private PIN, select which features to lock, and choose an inactivity timeout.'
              ),
              modalError && React.createElement('div', { style: { padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: 8, fontSize: '0.85rem' } }, modalError),
              React.createElement('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 } },
                React.createElement('div', null,
                  React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 4 } }, 'Feature Lock PIN'),
                  React.createElement('input', {
                    type: 'password',
                    inputMode: 'numeric',
                    maxLength: 6,
                    autoFocus: true,
                    className: 'input',
                    placeholder: '4–6 digits',
                    value: setupPin,
                    onChange: e => setSetupPin(e.target.value.replace(/\D/g, '').slice(0, 6)),
                    required: true,
                    style: { width: '100%', textAlign: 'center', fontSize: '1.1rem', letterSpacing: '0.2em' }
                  })
                ),
                React.createElement('div', null,
                  React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 4 } }, 'Confirm PIN'),
                  React.createElement('input', {
                    type: 'password',
                    inputMode: 'numeric',
                    maxLength: 6,
                    className: 'input',
                    placeholder: 'Re-enter PIN',
                    value: setupConfirmPin,
                    onChange: e => setSetupConfirmPin(e.target.value.replace(/\D/g, '').slice(0, 6)),
                    required: true,
                    style: { width: '100%', textAlign: 'center', fontSize: '1.1rem', letterSpacing: '0.2em' }
                  })
                )
              ),
              // Feature selection
              React.createElement('div', null,
                React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 6 } }, 'Select Features to Lock'),
                React.createElement('div', {
                  style: {
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: 8,
                    maxHeight: 180,
                    overflowY: 'auto',
                    padding: '8px',
                    border: '1px solid var(--border)',
                    borderRadius: 8
                  }
                },
                  LOCKABLE_FEATURES.map(f => {
                    const checked = setupFeatures.includes(f.id);
                    return React.createElement('label', {
                      key: f.id,
                      style: {
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        fontSize: '0.82rem',
                        cursor: 'pointer',
                        padding: '6px 8px',
                        borderRadius: 6,
                        background: checked ? 'rgba(99, 102, 241, 0.08)' : 'transparent',
                        border: `1px solid ${checked ? 'rgba(99, 102, 241, 0.3)' : 'transparent'}`
                      }
                    },
                      React.createElement('input', {
                        type: 'checkbox',
                        checked: checked,
                        onChange: () => {
                          setSetupFeatures(prev => prev.includes(f.id) ? prev.filter(x => x !== f.id) : [...prev, f.id]);
                        }
                      }),
                      React.createElement('span', null, `${f.icon} ${f.label}`)
                    );
                  })
                )
              ),
              // Timeout selection
              React.createElement('div', null,
                React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 4 } }, 'Inactivity Timeout'),
                React.createElement('select', {
                  className: 'select',
                  value: setupTimeout,
                  onChange: e => setSetupTimeout(parseInt(e.target.value, 10)),
                  style: { width: '100%', padding: '8px 10px', fontSize: '0.88rem' }
                },
                  React.createElement('option', { value: 0 }, '⚡ Immediately'),
                  React.createElement('option', { value: 1 }, '1 Minute'),
                  React.createElement('option', { value: 5 }, '5 Minutes (Default)'),
                  React.createElement('option', { value: 10 }, '10 Minutes'),
                  React.createElement('option', { value: 15 }, '15 Minutes'),
                  React.createElement('option', { value: 30 }, '30 Minutes'),
                  React.createElement('option', { value: 60 }, '1 Hour')
                )
              )
            ),
            React.createElement('div', { className: 'modal-footer', style: { display: 'flex', justifyContent: 'flex-end', gap: 10 } },
              React.createElement('button', { type: 'button', className: 'btn btn-outline', onClick: closeModal }, 'Cancel'),
              React.createElement('button', { type: 'submit', className: 'btn btn-primary' }, '🔒 Save & Enable Feature Lock')
            )
          )
        )
      ),

      // 3. Disable Lock Modal
      modal === 'disable' && React.createElement('div', { className: 'modal-overlay', style: { zIndex: 100000 } },
        React.createElement('div', { className: 'modal', style: { maxWidth: 400 } },
          React.createElement('div', { className: 'modal-header' },
            React.createElement('div', { className: 'modal-title', style: { display: 'flex', alignItems: 'center', gap: 8, color: 'var(--danger)' } },
              React.createElement('span', null, '⚠️'), ' Disable Feature Lock'
            ),
            React.createElement('button', { className: 'modal-close', onClick: closeModal }, '✕')
          ),
          React.createElement('form', { onSubmit: handleDisableSubmit },
            React.createElement('div', { className: 'modal-body', style: { display: 'flex', flexDirection: 'column', gap: 14 } },
              React.createElement('p', { style: { margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                'Enter your current Feature Lock PIN to turn off feature privacy protection.'
              ),
              modalError && React.createElement('div', { style: { padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: 8, fontSize: '0.85rem' } }, modalError),
              React.createElement('div', null,
                React.createElement('input', {
                  type: 'password',
                  inputMode: 'numeric',
                  maxLength: 6,
                  autoFocus: true,
                  className: 'input',
                  placeholder: 'Current PIN',
                  value: currentPin,
                  onChange: e => setCurrentPin(e.target.value.replace(/\D/g, '').slice(0, 6)),
                  required: true,
                  style: { width: '100%', textAlign: 'center', fontSize: '1.2rem', letterSpacing: '0.2em' }
                })
              ),
              React.createElement('div', { style: { textAlign: 'right' } },
                React.createElement('button', {
                  type: 'button',
                  className: 'btn btn-ghost btn-sm',
                  onClick: () => { setModal('forgot'); setModalError(''); },
                  style: { fontSize: '0.78rem', color: 'var(--text-secondary)' }
                }, 'Forgot PIN?')
              )
            ),
            React.createElement('div', { className: 'modal-footer', style: { display: 'flex', justifyContent: 'flex-end', gap: 10 } },
              React.createElement('button', { type: 'button', className: 'btn btn-outline', onClick: closeModal }, 'Cancel'),
              React.createElement('button', { type: 'submit', className: 'btn btn-danger', style: { backgroundColor: 'var(--danger)', color: 'white' } }, 'Disable Protection')
            )
          )
        )
      ),

      // 4. Change PIN Modal
      modal === 'changePin' && React.createElement('div', { className: 'modal-overlay', style: { zIndex: 100000 } },
        React.createElement('div', { className: 'modal', style: { maxWidth: 420 } },
          React.createElement('div', { className: 'modal-header' },
            React.createElement('div', { className: 'modal-title', style: { display: 'flex', alignItems: 'center', gap: 8 } },
              React.createElement('span', null, '🔑'), ' Change Feature Lock PIN'
            ),
            React.createElement('button', { className: 'modal-close', onClick: closeModal }, '✕')
          ),
          React.createElement('form', { onSubmit: handleChangePinSubmit },
            React.createElement('div', { className: 'modal-body', style: { display: 'flex', flexDirection: 'column', gap: 14 } },
              modalError && React.createElement('div', { style: { padding: '8px 12px', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: 8, fontSize: '0.85rem' } }, modalError),
              React.createElement('div', null,
                React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 4 } }, 'Current PIN'),
                React.createElement('input', {
                  type: 'password',
                  inputMode: 'numeric',
                  maxLength: 6,
                  autoFocus: true,
                  className: 'input',
                  placeholder: 'Current PIN',
                  value: currentPin,
                  onChange: e => setCurrentPin(e.target.value.replace(/\D/g, '').slice(0, 6)),
                  required: true,
                  style: { width: '100%', textAlign: 'center', fontSize: '1.1rem', letterSpacing: '0.2em' }
                })
              ),
              React.createElement('div', null,
                React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 4 } }, 'New PIN'),
                React.createElement('input', {
                  type: 'password',
                  inputMode: 'numeric',
                  maxLength: 6,
                  className: 'input',
                  placeholder: '4–6 numeric digits',
                  value: newPin,
                  onChange: e => setNewPin(e.target.value.replace(/\D/g, '').slice(0, 6)),
                  required: true,
                  style: { width: '100%', textAlign: 'center', fontSize: '1.1rem', letterSpacing: '0.2em' }
                })
              ),
              React.createElement('div', null,
                React.createElement('label', { className: 'label', style: { fontWeight: 600, marginBottom: 4 } }, 'Confirm New PIN'),
                React.createElement('input', {
                  type: 'password',
                  inputMode: 'numeric',
                  maxLength: 6,
                  className: 'input',
                  placeholder: 'Re-enter new PIN',
                  value: confirmNewPin,
                  onChange: e => setConfirmNewPin(e.target.value.replace(/\D/g, '').slice(0, 6)),
                  required: true,
                  style: { width: '100%', textAlign: 'center', fontSize: '1.1rem', letterSpacing: '0.2em' }
                })
              ),
              React.createElement('div', { style: { textAlign: 'right' } },
                React.createElement('button', {
                  type: 'button',
                  className: 'btn btn-ghost btn-sm',
                  onClick: () => { setModal('forgot'); setModalError(''); },
                  style: { fontSize: '0.78rem', color: 'var(--text-secondary)' }
                }, 'Forgot Current PIN?')
              )
            ),
            React.createElement('div', { className: 'modal-footer', style: { display: 'flex', justifyContent: 'flex-end', gap: 10 } },
              React.createElement('button', { type: 'button', className: 'btn btn-outline', onClick: closeModal }, 'Cancel'),
              React.createElement('button', { type: 'submit', className: 'btn btn-primary' }, 'Update PIN')
            )
          )
        )
      ),

      // 5. Customer-Facing Forgot PIN Recovery Modal (Inside Settings)
      modal === 'forgot' && React.createElement(FeatureLockRecoveryModal, {
        isOpen: modal === 'forgot',
        onClose: closeModal
      })
    );
  }

  // Attach components to service
  FeatureLockService.LockScreen = FeatureLockScreen;
  FeatureLockService.SettingsCard = FeatureLockSettings;
  FeatureLockService.RecoveryModal = FeatureLockRecoveryModal;
  FeatureLockService.navigateToSupport = navigateToSupport;

  // Expose public API on root window
  root.FeatureLock = FeatureLockService;

})(typeof window !== 'undefined' ? window : this);
