(() => {
  const ALL_THEME_CLASSES = ['light-1', 'dark-1', 'classic', 'classic-dark'];

  function parseTime(str) {
    if (!str || typeof str !== 'string') return 0;
    const parts = str.split(':');
    const h = parseInt(parts[0], 10) || 0;
    const m = parseInt(parts[1], 10) || 0;
    return h * 60 + m;
  }

  function isWithinTimeRange(startStr, endStr) {
    const now = new Date();
    const currentMin = now.getHours() * 60 + now.getMinutes();
    const startMin = parseTime(startStr);
    const endMin = parseTime(endStr);

    if (startMin <= endMin) {
      return currentMin >= startMin && currentMin < endMin;
    } else {
      // Overnight range, e.g. 18:00 (1080) to 06:00 (360)
      return currentMin >= startMin || currentMin < endMin;
    }
  }

  function getStoredState() {
    let family = localStorage.getItem('themeFamily');
    let isDark = localStorage.getItem('themeDarkMode') === 'true';
    let schedule = localStorage.getItem('themeDarkSchedule') || 'manual';
    let customStart = localStorage.getItem('themeCustomStart') || '19:00';
    let customEnd = localStorage.getItem('themeCustomEnd') || '07:00';

    // Backward compatibility migration from legacy 'themeBase'
    const legacyBase = localStorage.getItem('themeBase');
    if (!family && legacyBase) {
      if (legacyBase === 'classic' || legacyBase === 'classic-dark') {
        family = 'classic';
        isDark = (legacyBase === 'classic-dark');
      } else {
        family = 'modern';
        isDark = (legacyBase === 'dark-1');
      }
      localStorage.setItem('themeFamily', family);
      localStorage.setItem('themeDarkMode', String(isDark));
    }

    if (family !== 'classic' && family !== 'modern') {
      family = 'modern';
    }

    return { family, isDark, schedule, customStart, customEnd };
  }

  function computeIsDark(state) {
    if (state.schedule === 'sunset') {
      return isWithinTimeRange('18:00', '06:00');
    }
    if (state.schedule === 'system') {
      return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    }
    if (state.schedule === 'custom') {
      return isWithinTimeRange(state.customStart, state.customEnd);
    }
    return state.isDark;
  }

  function computeThemeClass(family, isDark) {
    if (family === 'classic') {
      return isDark ? 'classic-dark' : 'classic';
    }
    return isDark ? 'dark-1' : 'light-1';
  }

  function applyThemeState(notify = true) {
    const state = getStoredState();
    const activeIsDark = computeIsDark(state);
    const activeClass = computeThemeClass(state.family, activeIsDark);

    const root = document.documentElement;
    ALL_THEME_CLASSES.forEach(c => root.classList.remove(c));
    root.classList.add(activeClass);
    localStorage.setItem('themeBase', activeClass);

    if (notify) {
      const detail = {
        theme: activeClass,
        family: state.family,
        isDark: activeIsDark,
        manualDark: state.isDark,
        schedule: state.schedule,
        customStart: state.customStart,
        customEnd: state.customEnd
      };
      window.dispatchEvent(new CustomEvent('themeChanged', { detail }));
    }
    return activeClass;
  }

  // Initialize immediately
  applyThemeState(false);

  // Periodic schedule check (every 30 seconds)
  setInterval(() => {
    const state = getStoredState();
    if (state.schedule !== 'manual') {
      applyThemeState(true);
    }
  }, 30000);

  // Check on tab focus / wake from sleep
  if (typeof window !== 'undefined') {
    window.addEventListener('focus', () => applyThemeState(true));
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        applyThemeState(true);
      }
    });

    // System OS preference change listener
    if (window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      if (mediaQuery.addEventListener) {
        mediaQuery.addEventListener('change', () => {
          const state = getStoredState();
          if (state.schedule === 'system') {
            applyThemeState(true);
          }
        });
      }
    }
  }

  // Global APIs
  window.getThemeState = function() {
    const state = getStoredState();
    const activeIsDark = computeIsDark(state);
    return {
      ...state,
      activeIsDark,
      activeClass: computeThemeClass(state.family, activeIsDark)
    };
  };

  window.setThemeFamily = function(family) {
    if (family !== 'modern' && family !== 'classic') return;
    localStorage.setItem('themeFamily', family);
    applyThemeState(true);
  };

  window.setDarkMode = function(isDark) {
    localStorage.setItem('themeDarkMode', String(!!isDark));
    // If setting manually, set schedule to manual so user's explicit action is respected
    localStorage.setItem('themeDarkSchedule', 'manual');
    applyThemeState(true);
  };

  window.setDarkSchedule = function(schedule, customStart, customEnd) {
    const validSchedules = ['manual', 'sunset', 'system', 'custom'];
    if (!validSchedules.includes(schedule)) schedule = 'manual';
    localStorage.setItem('themeDarkSchedule', schedule);
    if (customStart) localStorage.setItem('themeCustomStart', customStart);
    if (customEnd) localStorage.setItem('themeCustomEnd', customEnd);
    applyThemeState(true);
  };

  window.toggleTheme = function() {
    const state = getStoredState();
    const currentlyDark = computeIsDark(state);
    window.setDarkMode(!currentlyDark);
  };

  // Backward-compatible helpers
  window.getAppTheme = function() {
    const state = getStoredState();
    const isDark = computeIsDark(state);
    return computeThemeClass(state.family, isDark);
  };

  window.setAppTheme = function(themeId) {
    if (themeId === 'classic' || themeId === 'classic-dark') {
      localStorage.setItem('themeFamily', 'classic');
      localStorage.setItem('themeDarkMode', String(themeId === 'classic-dark'));
    } else {
      localStorage.setItem('themeFamily', 'modern');
      localStorage.setItem('themeDarkMode', String(themeId === 'dark-1'));
    }
    localStorage.setItem('themeDarkSchedule', 'manual');
    applyThemeState(true);
  };
})();
