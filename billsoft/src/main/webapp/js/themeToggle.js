(() => {
  const ALL_THEME_CLASSES = ['light-1', 'dark-1', 'classic', 'classic-dark', 'minimal-light', 'minimal-dark'];

  // Central Theme Configuration & Compatibility Registry
  const THEME_REGISTRY = {
    families: {
      material: {
        id: 'material',
        name: 'Material',
        description: 'Modern Material-inspired design system with elevation and clean density',
        styles: {
          modern: {
            id: 'modern',
            name: 'Modern Material',
            family: 'material',
            description: 'Established RupeeCRM interface with card grid and multi-tier metrics',
            defaultPalette: 'forest',
            defaultFont: 'inter',
            compatiblePalettes: ['forest', 'navy', 'teal', 'plum', 'burgundy', 'amber'],
            compatibleFonts: ['inter', 'jakarta', 'roboto', 'system']
          },
          minimal: {
            id: 'minimal',
            name: 'Minimal SaaS',
            family: 'material',
            description: 'Premium, clean SaaS design inspired by modern commercial billing tools',
            defaultPalette: 'emerald',
            defaultFont: 'inter',
            compatiblePalettes: ['emerald', 'slate', 'indigo', 'sage', 'teal'],
            compatibleFonts: ['inter', 'jakarta', 'roboto', 'system']
          }
        }
      },
      classic: {
        id: 'classic',
        name: 'Classic',
        description: 'Authentic 90s retro desktop experience with beveled panels',
        styles: {
          retro98: {
            id: 'retro98',
            name: "Classic Retro '98",
            family: 'classic',
            description: 'Windows 95/98 desktop aesthetics with 3D outsets, insets and dense tables',
            defaultPalette: 'win98-standard',
            defaultFont: 'tahoma',
            compatiblePalettes: ['win98-standard', 'win98-noir', 'classic-slate', 'desert-gold', 'spruce-green', 'retro-plum'],
            compatibleFonts: ['tahoma', 'mssansserif', 'fixedsys', 'segoe']
          }
        }
      }
    },
    palettes: {
      // Material Modern
      forest: { id: 'forest', name: 'Forest Green', primary: '#168B45', accent: '#E8F5E9', family: 'material', styles: ['modern'] },
      navy: { id: 'navy', name: 'Navy Blue', primary: '#1E40AF', accent: '#EFF6FF', family: 'material', styles: ['modern'] },
      teal: { id: 'teal', name: 'Deep Teal', primary: '#0F766E', accent: '#F0FDFA', family: 'material', styles: ['modern', 'minimal'] },
      plum: { id: 'plum', name: 'Royal Plum', primary: '#581C87', accent: '#FAF5FF', family: 'material', styles: ['modern'] },
      burgundy: { id: 'burgundy', name: 'Burgundy', primary: '#831843', accent: '#FDF2F8', family: 'material', styles: ['modern'] },
      amber: { id: 'amber', name: 'Amber Gold', primary: '#B45309', accent: '#FFFBEB', family: 'material', styles: ['modern'] },
      // Material Minimal SaaS
      emerald: { id: 'emerald', name: 'Emerald SaaS', primary: '#168B45', accent: '#E8F5E9', family: 'material', styles: ['minimal'] },
      slate: { id: 'slate', name: 'Slate Minimal', primary: '#0F172A', accent: '#F1F5F9', family: 'material', styles: ['minimal'] },
      indigo: { id: 'indigo', name: 'Indigo Modern', primary: '#4F46E5', accent: '#EEF2FF', family: 'material', styles: ['minimal'] },
      sage: { id: 'sage', name: 'Sage Nordic', primary: '#2D6A4F', accent: '#D8F3DC', family: 'material', styles: ['minimal'] },
      // Classic Retro '98
      'win98-standard': { id: 'win98-standard', name: 'Win98 Standard', primary: '#000080', accent: '#008080', family: 'classic', styles: ['retro98'] },
      'win98-noir': { id: 'win98-noir', name: 'Win98 Noir', primary: '#000000', accent: '#00FFFF', family: 'classic', styles: ['retro98'] },
      'classic-slate': { id: 'classic-slate', name: 'Classic Slate', primary: '#3A6EA5', accent: '#D4D0C8', family: 'classic', styles: ['retro98'] },
      'desert-gold': { id: 'desert-gold', name: 'Desert Gold', primary: '#800000', accent: '#D2B48C', family: 'classic', styles: ['retro98'] },
      'spruce-green': { id: 'spruce-green', name: 'Spruce Green', primary: '#004040', accent: '#20B2AA', family: 'classic', styles: ['retro98'] },
      'retro-plum': { id: 'retro-plum', name: 'Retro Plum', primary: '#4A0E4E', accent: '#FF007F', family: 'classic', styles: ['retro98'] }
    },
    fonts: {
      inter: { id: 'inter', name: 'Inter (Default)', stack: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif", family: 'material' },
      jakarta: { id: 'jakarta', name: 'Plus Jakarta Sans', stack: "'Plus Jakarta Sans', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif", family: 'material' },
      roboto: { id: 'roboto', name: 'Roboto', stack: "'Roboto', -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif", family: 'material' },
      system: { id: 'system', name: 'System Default', stack: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif", family: 'material' },
      tahoma: { id: 'tahoma', name: 'Tahoma (Win98 SE)', stack: "'Tahoma', 'Segoe UI', Arial, sans-serif", family: 'classic' },
      mssansserif: { id: 'mssansserif', name: 'MS Sans Serif (Win95)', stack: "'MS Sans Serif', 'Tahoma', 'Segoe UI', sans-serif", family: 'classic' },
      fixedsys: { id: 'fixedsys', name: 'Fixedsys (Monospace)', stack: "'Fixedsys', 'Courier New', monospace", family: 'classic' },
      segoe: { id: 'segoe', name: 'Segoe UI (System)', stack: "'Segoe UI', Tahoma, Arial, sans-serif", family: 'classic' }
    }
  };

  const DEFAULT_ACCENT = 'forest';
  const ACCENT_ALIASES = {
    emerald: 'forest',
    green: 'forest',
    blue: 'navy',
    violet: 'plum',
    purple: 'plum',
    red: 'burgundy',
    orange: 'amber',
    steel: 'navy'
  };

  function normalizeAccent(accent, style) {
    if (!accent || typeof accent !== 'string') {
      return style === 'minimal' ? 'emerald' : (style === 'retro98' ? 'win98-standard' : DEFAULT_ACCENT);
    }
    const lower = accent.trim().toLowerCase();
    if (THEME_REGISTRY.palettes[lower]) return lower;
    if (ACCENT_ALIASES[lower]) return ACCENT_ALIASES[lower];
    return style === 'minimal' ? 'emerald' : (style === 'retro98' ? 'win98-standard' : DEFAULT_ACCENT);
  }

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
      // Overnight range across midnight, e.g. 19:00 (1140) to 07:00 (420)
      return currentMin >= startMin || currentMin < endMin;
    }
  }

  function getStoredState() {
    let family = localStorage.getItem('themeFamily');
    let style = localStorage.getItem('themeStyle');
    let isDark = localStorage.getItem('themeDarkMode') === 'true';
    let schedule = localStorage.getItem('themeDarkSchedule');
    let isCustomized = localStorage.getItem('themeDarkScheduleCustomized') === 'true';
    let customStart = localStorage.getItem('themeCustomDarkStart') || localStorage.getItem('themeCustomStart') || '19:00';
    let customEnd = localStorage.getItem('themeCustomLightStart') || localStorage.getItem('themeCustomEnd') || '07:00';
    let accent = localStorage.getItem('themeAccentColor');
    let font = localStorage.getItem('themeFontFamily');
    let mode = localStorage.getItem('themeMode');

    // Backward compatibility migration from legacy 'themeBase'
    const legacyBase = localStorage.getItem('themeBase');
    if (!family && legacyBase) {
      if (legacyBase === 'minimal' || legacyBase === 'minimal-light' || legacyBase === 'minimal-dark') {
        family = 'material';
        style = 'minimal';
        isDark = (legacyBase === 'minimal-dark');
      } else if (legacyBase === 'classic' || legacyBase === 'classic-dark') {
        family = 'classic';
        style = 'retro98';
        isDark = (legacyBase === 'classic-dark');
      } else {
        family = 'material';
        style = 'modern';
        isDark = (legacyBase === 'dark-1');
      }
      localStorage.setItem('themeFamily', family);
      localStorage.setItem('themeStyle', style);
      localStorage.setItem('themeDarkMode', String(isDark));
    }

    // Translate legacy flat themeFamily values ('modern', 'minimal', 'classic') to two-tier
    if (family === 'modern') {
      family = 'material';
      style = 'modern';
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'modern');
    } else if (family === 'minimal') {
      family = 'material';
      style = 'minimal';
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'minimal');
    } else if (family === 'classic') {
      family = 'classic';
      style = 'retro98';
      localStorage.setItem('themeFamily', 'classic');
      localStorage.setItem('themeStyle', 'retro98');
    }

    // Default fallbacks for family and style
    if (family !== 'classic' && family !== 'modern' && family !== 'minimal' && family !== 'material') {
      family = 'material';
      style = 'minimal';
    }

    if (family === 'material') {
      if (style !== 'modern' && style !== 'minimal') {
        style = 'minimal';
      }
    } else if (family === 'classic') {
      if (style !== 'retro98') {
        style = 'retro98';
      }
    }

    // Resolve explicit mode if not stored
    if (!mode) {
      mode = isDark ? 'dark' : 'light';
    }

    // Validate and resolve palette compatibility
    accent = normalizeAccent(accent, style);
    const targetStyleObj = THEME_REGISTRY.families[family].styles[style];
    if (targetStyleObj && !targetStyleObj.compatiblePalettes.includes(accent)) {
      accent = targetStyleObj.defaultPalette;
    }

    // Validate and resolve font compatibility
    if (!font || !THEME_REGISTRY.fonts[font] || (targetStyleObj && !targetStyleObj.compatibleFonts.includes(font))) {
      font = targetStyleObj ? targetStyleObj.defaultFont : 'inter';
    }

    // Default theme scheduling
    const validSchedules = ['sunset', 'manual', 'system', 'custom'];
    if (!schedule || !validSchedules.includes(schedule)) {
      schedule = 'manual';
    }

    const scheduleEnabled = (schedule === 'sunset' || schedule === 'custom');

    return {
      family,
      style,
      mode,
      isDark,
      schedule,
      scheduleEnabled,
      scheduleMode: schedule === 'sunset' ? 'sunset' : 'custom',
      isCustomized,
      customStart,
      customEnd,
      customDarkStart: customStart,
      customLightStart: customEnd,
      accent,
      font
    };
  }

  function computeIsDark(state) {
    // PRECEDENCE RULE: Enabled schedule strictly overrides manual mode
    if (state.scheduleEnabled) {
      if (state.scheduleMode === 'sunset' || state.schedule === 'sunset') {
        return isWithinTimeRange('18:00', '06:00');
      }
      return isWithinTimeRange(state.customDarkStart || state.customStart, state.customLightStart || state.customEnd);
    }
    // When scheduling is disabled, manual mode governs:
    if (state.mode === 'system' || state.schedule === 'system') {
      return !!(typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    }
    return state.mode === 'dark' || state.isDark === true;
  }

  function computeThemeClass(family, isDark, style) {
    if (family === 'minimal') {
      return isDark ? 'minimal-dark' : 'minimal-light';
    }
    if (family === 'classic' || style === 'retro98') {
      return isDark ? 'classic-dark' : 'classic';
    }
    if (style === 'minimal') {
      return isDark ? 'minimal-dark' : 'minimal-light';
    }
    return isDark ? 'dark-1' : 'light-1';
  }

  let activeLiveConfig = null;

  function getEffectiveState() {
    if (activeLiveConfig) {
      return activeLiveConfig;
    }
    return getStoredState();
  }

  function applyThemeState(notify = true, explicitState = null) {
    const state = explicitState || getEffectiveState();
    const activeIsDark = computeIsDark(state);
    const activeClass = computeThemeClass(state.family, activeIsDark, state.style);

    const root = document.documentElement;

    // Remove legacy and active theme classes cleanly
    ALL_THEME_CLASSES.forEach(c => root.classList.remove(c));
    root.classList.remove('classic-retro-light', 'classic-retro-dark');
    root.classList.add(activeClass);

    // Apply strict data attributes for CSS token cascade
    root.setAttribute('data-family', state.family);
    root.setAttribute('data-style', state.style);
    root.setAttribute('data-theme', activeIsDark ? 'dark' : 'light');
    root.setAttribute('data-accent', state.accent);
    root.setAttribute('data-palette', state.accent);
    root.setAttribute('data-font', state.font);

    root.classList.remove('minimal', 'classic', 'modern');
    if (state.family === 'minimal' || state.style === 'minimal') {
      root.classList.add('minimal', 'material');
    } else if (state.family === 'classic' || state.style === 'retro98') {
      root.classList.add('classic');
      if (activeIsDark) root.classList.add('classic-retro-dark');
      else root.classList.add('classic-retro-light');
    } else {
      root.classList.add('modern', 'material');
    }

    if (activeIsDark) {
      root.classList.add('dark', 'dark-mode');
      if (document.body) document.body.classList.add('dark', 'dark-mode');
    } else {
      root.classList.remove('dark', 'dark-mode');
      if (document.body) document.body.classList.remove('dark', 'dark-mode');
    }

    // Only persist themeBase when in steady state (not temporary live preview)
    if (!explicitState && !activeLiveConfig) {
      localStorage.setItem('themeBase', activeClass);
    }

    if (notify) {
      const detail = {
        theme: activeClass,
        family: state.family,
        style: state.style,
        mode: state.mode,
        isDark: activeIsDark,
        manualDark: state.isDark,
        schedule: state.schedule,
        scheduleEnabled: state.scheduleEnabled,
        scheduleMode: state.scheduleMode,
        customStart: state.customStart,
        customEnd: state.customEnd,
        accent: state.accent,
        font: state.font
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
    if (state.scheduleEnabled || state.schedule === 'sunset' || state.schedule === 'custom') {
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

    // Cross-tab synchronization listener
    window.addEventListener('storage', (e) => {
      if (e.key && e.key.startsWith('theme')) {
        applyThemeState(true);
      }
    });

    // System OS preference change listener
    if (window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      if (mediaQuery.addEventListener) {
        mediaQuery.addEventListener('change', () => {
          const state = getStoredState();
          if (state.mode === 'system' || state.schedule === 'system') {
            applyThemeState(true);
          }
        });
      }
    }
  }

  // Global APIs
  window.getThemeRegistry = function() {
    return THEME_REGISTRY;
  };

  window.getThemeState = function() {
    const state = getEffectiveState();
    const activeIsDark = computeIsDark(state);
    return {
      ...state,
      activeIsDark,
      activeClass: computeThemeClass(state.family, activeIsDark, state.style)
    };
  };

  window.getCurrentThemeState = window.getThemeState;

  window.getStoredThemeConfig = function() {
    const s = getStoredState();
    return {
      family: s.family === 'classic' ? 'classic' : 'material',
      style: s.style,
      mode: s.mode,
      palette: s.accent,
      accent: s.accent,
      font: s.font,
      scheduleEnabled: s.scheduleEnabled,
      scheduleMode: s.scheduleMode,
      customDarkStart: s.customDarkStart || s.customStart || '19:00',
      customLightStart: s.customLightStart || s.customEnd || '07:00'
    };
  };

  window.getActiveThemeConfig = function() {
    if (activeLiveConfig) {
      return {
        family: activeLiveConfig.family,
        style: activeLiveConfig.style,
        mode: activeLiveConfig.mode,
        palette: activeLiveConfig.accent,
        accent: activeLiveConfig.accent,
        font: activeLiveConfig.font,
        scheduleEnabled: activeLiveConfig.scheduleEnabled,
        scheduleMode: activeLiveConfig.scheduleMode,
        customDarkStart: activeLiveConfig.customDarkStart,
        customLightStart: activeLiveConfig.customLightStart
      };
    }
    return window.getStoredThemeConfig();
  };

  window.applyLiveTheme = function(config, notify = true) {
    if (!config || typeof config !== 'object') return;
    const family = (config.family === 'classic') ? 'classic' : 'material';
    const style = family === 'classic' ? 'retro98' : ((config.style === 'modern' || config.style === 'minimal') ? config.style : 'minimal');
    const mode = (config.mode === 'dark' || config.mode === 'system') ? config.mode : 'light';
    const accent = normalizeAccent(config.palette || config.accent, style);
    const font = (config.font && THEME_REGISTRY.fonts[config.font]) ? config.font : (style === 'retro98' ? 'tahoma' : 'inter');
    const scheduleEnabled = !!config.scheduleEnabled;
    const scheduleMode = config.scheduleMode === 'sunset' ? 'sunset' : 'custom';
    const customStart = config.customDarkStart || config.customStart || '19:00';
    const customEnd = config.customLightStart || config.customEnd || '07:00';

    activeLiveConfig = {
      family,
      style,
      mode,
      isDark: mode === 'dark',
      schedule: scheduleEnabled ? scheduleMode : 'manual',
      scheduleEnabled,
      scheduleMode,
      customStart,
      customEnd,
      customDarkStart: customStart,
      customLightStart: customEnd,
      accent,
      font
    };
    applyThemeState(notify, activeLiveConfig);
  };

  window.restoreSavedTheme = function() {
    activeLiveConfig = null;
    applyThemeState(true);
  };

  window.getThemeAccent = function() {
    const state = getStoredState();
    return state.accent || DEFAULT_ACCENT;
  };

  window.setThemeAccent = function(accent) {
    const state = getStoredState();
    const resolved = normalizeAccent(accent, state.style);
    localStorage.setItem('themeAccentColor', resolved);
    applyThemeState(true);
    window.dispatchEvent(new CustomEvent('themeAccentChanged', { detail: { accent: resolved } }));
  };
  window.setThemePalette = window.setThemeAccent;

  window.setThemeFont = function(fontId) {
    if (!THEME_REGISTRY.fonts[fontId]) return;
    localStorage.setItem('themeFontFamily', fontId);
    applyThemeState(true);
    window.dispatchEvent(new CustomEvent('themeFontChanged', { detail: { font: fontId } }));
  };

  window.setThemeFamily = function(family, optStyle) {
    if (family === 'material') {
      localStorage.setItem('themeFamily', 'material');
      const targetStyle = (optStyle === 'minimal' || optStyle === 'modern') ? optStyle : 'minimal';
      localStorage.setItem('themeStyle', targetStyle);
    } else if (family === 'classic') {
      localStorage.setItem('themeFamily', 'classic');
      localStorage.setItem('themeStyle', 'retro98');
    } else if (family === 'modern') {
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'modern');
    } else if (family === 'minimal') {
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'minimal');
    } else {
      return;
    }
    applyThemeState(true);
  };

  window.setThemeStyle = function(style) {
    if (style === 'minimal') {
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'minimal');
    } else if (style === 'modern') {
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'modern');
    } else if (style === 'retro98') {
      localStorage.setItem('themeFamily', 'classic');
      localStorage.setItem('themeStyle', 'retro98');
    } else {
      return;
    }
    applyThemeState(true);
  };

  window.setDarkMode = function(isDark) {
    localStorage.setItem('themeMode', isDark ? 'dark' : 'light');
    localStorage.setItem('themeDarkMode', String(!!isDark));
    localStorage.setItem('themeDarkSchedule', 'manual');
    localStorage.setItem('themeScheduleEnabled', 'false');
    localStorage.setItem('themeDarkScheduleCustomized', 'true');
    applyThemeState(true);
  };

  window.setDarkSchedule = function(schedule, customStart, customEnd) {
    const validSchedules = ['sunset', 'manual', 'system', 'custom'];
    if (!validSchedules.includes(schedule)) schedule = 'sunset';
    localStorage.setItem('themeDarkSchedule', schedule);
    localStorage.setItem('themeScheduleEnabled', String(schedule === 'sunset' || schedule === 'custom'));
    localStorage.setItem('themeDarkScheduleCustomized', 'true');
    if (customStart) {
      localStorage.setItem('themeCustomStart', customStart);
      localStorage.setItem('themeCustomDarkStart', customStart);
    }
    if (customEnd) {
      localStorage.setItem('themeCustomEnd', customEnd);
      localStorage.setItem('themeCustomLightStart', customEnd);
    }
    applyThemeState(true);
  };

  window.saveThemeConfig = function(config) {
    if (!config || typeof config !== 'object') return false;
    try {
      const family = (config.family === 'classic') ? 'classic' : 'material';
      const style = family === 'classic' ? 'retro98' : ((config.style === 'modern' || config.style === 'minimal') ? config.style : 'minimal');
      const mode = (config.mode === 'dark' || config.mode === 'system') ? config.mode : 'light';
      const palette = normalizeAccent(config.palette, style);
      const font = (config.font && THEME_REGISTRY.fonts[config.font]) ? config.font : (style === 'retro98' ? 'tahoma' : 'inter');
      const scheduleEnabled = !!config.scheduleEnabled;
      const scheduleMode = config.scheduleMode === 'sunset' ? 'sunset' : 'custom';
      const customDarkStart = config.customDarkStart || '19:00';
      const customLightStart = config.customLightStart || '07:00';

      localStorage.setItem('themeFamily', family);
      localStorage.setItem('themeStyle', style);
      localStorage.setItem('themeMode', mode);
      localStorage.setItem('themeDarkMode', String(mode === 'dark'));
      localStorage.setItem('themeAccentColor', palette);
      localStorage.setItem('themeFontFamily', font);
      localStorage.setItem('themeScheduleEnabled', String(scheduleEnabled));
      localStorage.setItem('themeScheduleMode', scheduleMode);
      localStorage.setItem('themeDarkSchedule', scheduleEnabled ? scheduleMode : 'manual');
      localStorage.setItem('themeCustomStart', customDarkStart);
      localStorage.setItem('themeCustomEnd', customLightStart);
      localStorage.setItem('themeCustomDarkStart', customDarkStart);
      localStorage.setItem('themeCustomLightStart', customLightStart);

      activeLiveConfig = null;
      applyThemeState(true);
      return true;
    } catch (err) {
      console.error('Failed to save theme configuration:', err);
      return false;
    }
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
    return computeThemeClass(state.family, isDark, state.style);
  };

  window.setAppTheme = function(themeId) {
    if (themeId === 'minimal' || themeId === 'minimal-light' || themeId === 'minimal-dark') {
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'minimal');
      localStorage.setItem('themeDarkMode', String(themeId === 'minimal-dark'));
    } else if (themeId === 'classic' || themeId === 'classic-dark') {
      localStorage.setItem('themeFamily', 'classic');
      localStorage.setItem('themeStyle', 'retro98');
      localStorage.setItem('themeDarkMode', String(themeId === 'classic-dark'));
    } else {
      localStorage.setItem('themeFamily', 'material');
      localStorage.setItem('themeStyle', 'modern');
      localStorage.setItem('themeDarkMode', String(themeId === 'dark-1'));
    }
    localStorage.setItem('themeDarkSchedule', 'manual');
    localStorage.setItem('themeScheduleEnabled', 'false');
    localStorage.setItem('themeDarkScheduleCustomized', 'true');
    applyThemeState(true);
  };
})();