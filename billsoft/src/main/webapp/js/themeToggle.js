(() => {
  const ALL_THEME_CLASSES = ['light-1', 'dark-1', 'classic', 'classic-dark', 'minimal-light', 'minimal-dark'];

  // ══════════════════════════════════════════════════════════════════════════
  // ASTRONOMICAL SOLAR CALCULATION ENGINE (NOAA STANDARD, 100% OFFLINE)
  // Calculates exact sunrise and sunset times based on local coordinates & date.
  // Zero network requests, local caching, deterministic calculations.
  // ══════════════════════════════════════════════════════════════════════════

  function calculateSolarTimes(date, latitude, longitude) {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = d.getMonth() + 1;
    const day = d.getDate();

    // Day of the year
    const N1 = Math.floor(275 * month / 9);
    const N2 = Math.floor((month + 9) / 12);
    const N3 = (1 + Math.floor((year - 4 * Math.floor(year / 4) + 2) / 3));
    const N = N1 - (N2 * N3) + day - 30;

    const lngHour = longitude / 15;

    function calcTime(isSunrise) {
      const t = isSunrise ? (N + ((6 - lngHour) / 24)) : (N + ((18 - lngHour) / 24));
      const M = (0.9856 * t) - 3.289;
      let L = M + (1.916 * Math.sin(M * Math.PI / 180)) + (0.020 * Math.sin(2 * M * Math.PI / 180)) + 282.634;
      L = (L + 360) % 360;

      let RA = Math.atan(0.91764 * Math.tan(L * Math.PI / 180)) * 180 / Math.PI;
      RA = (RA + 360) % 360;

      const Lquadrant = Math.floor(L / 90) * 90;
      const RAquadrant = Math.floor(RA / 90) * 90;
      RA = (RA + (Lquadrant - RAquadrant)) / 15;

      const sinDec = 0.39782 * Math.sin(L * Math.PI / 180);
      const cosDec = Math.cos(Math.asin(sinDec));

      // Zenith for official civil sunrise/sunset = 90° 50' = 90.8333°
      const zenith = 90.83333;
      const cosH = (Math.cos(zenith * Math.PI / 180) - (sinDec * Math.sin(latitude * Math.PI / 180))) / (cosDec * Math.cos(latitude * Math.PI / 180));

      if (cosH > 1 || cosH < -1) return null; // Polar day or night

      const H = isSunrise ? ((360 - Math.acos(cosH) * 180 / Math.PI) / 15) : ((Math.acos(cosH) * 180 / Math.PI) / 15);
      let T = H + RA - (0.06571 * t) - 6.622;
      T = ((T % 24) + 24) % 24; // NOAA Step 8: normalize local solar time to [0, 24)
      const rawUT = T - lngHour;
      const dayOffset = Math.floor(rawUT / 24);
      const UT = ((rawUT % 24) + 24) % 24;

      const hours = Math.floor(UT);
      const minutes = Math.floor((UT % 1) * 60);
      const seconds = Math.round(((UT % 1) * 60 % 1) * 60);
      return new Date(Date.UTC(year, month - 1, day + dayOffset, hours, minutes, seconds));
    }

    return {
      sunrise: calcTime(true),
      sunset: calcTime(false)
    };
  }

  // Pre-configured popular hubs for immediate selection
  const CITIES_CATALOG = [
    { id: 'auto', name: 'Auto-detect from Timezone / System', lat: null, lng: null },
    { id: 'mumbai', name: 'Mumbai, India', lat: 19.0760, lng: 72.8777, tz: 'Asia/Kolkata' },
    { id: 'pune', name: 'Pune, India', lat: 18.5204, lng: 73.8567, tz: 'Asia/Kolkata' },
    { id: 'delhi', name: 'Delhi / NCR, India', lat: 28.6139, lng: 77.2090, tz: 'Asia/Kolkata' },
    { id: 'bengaluru', name: 'Bengaluru, India', lat: 12.9716, lng: 77.5946, tz: 'Asia/Kolkata' },
    { id: 'hyderabad', name: 'Hyderabad, India', lat: 17.3850, lng: 78.4867, tz: 'Asia/Kolkata' },
    { id: 'chennai', name: 'Chennai, India', lat: 13.0827, lng: 80.2707, tz: 'Asia/Kolkata' },
    { id: 'kolkata', name: 'Kolkata, India', lat: 22.5726, lng: 88.3639, tz: 'Asia/Kolkata' },
    { id: 'ahmedabad', name: 'Ahmedabad, India', lat: 23.0225, lng: 72.5714, tz: 'Asia/Kolkata' },
    { id: 'surat', name: 'Surat, India', lat: 21.1702, lng: 72.8311, tz: 'Asia/Kolkata' },
    { id: 'jaipur', name: 'Jaipur, India', lat: 26.9124, lng: 75.7873, tz: 'Asia/Kolkata' },
    { id: 'lucknow', name: 'Lucknow, India', lat: 26.8467, lng: 80.9462, tz: 'Asia/Kolkata' },
    { id: 'dubai', name: 'Dubai, UAE', lat: 25.2048, lng: 55.2708, tz: 'Asia/Dubai' },
    { id: 'singapore', name: 'Singapore', lat: 1.3521, lng: 103.8198, tz: 'Asia/Singapore' },
    { id: 'london', name: 'London, UK', lat: 51.5074, lng: -0.1278, tz: 'Europe/London' },
    { id: 'newyork', name: 'New York, USA', lat: 40.7128, lng: -74.0060, tz: 'America/New_York' },
    { id: 'tokyo', name: 'Tokyo, Japan', lat: 35.6762, lng: 139.6503, tz: 'Asia/Tokyo' },
    { id: 'sydney', name: 'Sydney, Australia', lat: -33.8688, lng: 151.2093, tz: 'Australia/Sydney' }
  ];

  const TIMEZONE_COORDINATE_MAP = {
    'Asia/Kolkata': { lat: 19.0760, lng: 72.8777, name: 'India Standard Time (IST)' },
    'Asia/Calcutta': { lat: 19.0760, lng: 72.8777, name: 'India Standard Time (IST)' },
    'Asia/Dubai': { lat: 25.2048, lng: 55.2708, name: 'Gulf Standard Time (GST)' },
    'Asia/Singapore': { lat: 1.3521, lng: 103.8198, name: 'Singapore Standard Time (SGT)' },
    'Europe/London': { lat: 51.5074, lng: -0.1278, name: 'Greenwich / British Time' },
    'America/New_York': { lat: 40.7128, lng: -74.0060, name: 'Eastern Time (ET)' },
    'America/Chicago': { lat: 41.8781, lng: -87.6298, name: 'Central Time (CT)' },
    'America/Denver': { lat: 39.7392, lng: -104.9903, name: 'Mountain Time (MT)' },
    'America/Los_Angeles': { lat: 34.0522, lng: -118.2437, name: 'Pacific Time (PT)' },
    'Asia/Tokyo': { lat: 35.6762, lng: 139.6503, name: 'Japan Standard Time (JST)' },
    'Australia/Sydney': { lat: -33.8688, lng: 151.2093, name: 'Australian Eastern Time' },
    'UTC': { lat: 51.4769, lng: 0.0000, name: 'Coordinated Universal Time (UTC)' },
    'Etc/UTC': { lat: 51.4769, lng: 0.0000, name: 'Coordinated Universal Time (UTC)' },
    'Etc/GMT': { lat: 51.4769, lng: 0.0000, name: 'Greenwich Mean Time (GMT)' },
    'GMT': { lat: 51.4769, lng: 0.0000, name: 'Greenwich Mean Time (GMT)' },
    'Universal': { lat: 51.4769, lng: 0.0000, name: 'Universal Time' }
  };

  function resolveLocationCoordinates() {
    // 1. Explicit user-configured city in Settings
    const customCityId = localStorage.getItem('themeLocationCity');
    if (customCityId && customCityId !== 'auto') {
      const city = CITIES_CATALOG.find(c => c.id === customCityId);
      if (city && city.lat !== null) {
        return { lat: city.lat, lng: city.lng, name: city.name, source: 'city', cityId: city.id };
      }
    }

    // 2. Explicit device coordinates if permitted and stored
    const customLat = parseFloat(localStorage.getItem('themeLatitude'));
    const customLng = parseFloat(localStorage.getItem('themeLongitude'));
    const customName = localStorage.getItem('themeLocationName');
    if (!isNaN(customLat) && !isNaN(customLng)) {
      return { lat: customLat, lng: customLng, name: customName || 'Device Location', source: 'device' };
    }

    // 3. Timezone-based deterministic local fallback
    let tz = 'Asia/Kolkata';
    try {
      if (typeof Intl !== 'undefined' && Intl.DateTimeFormat) {
        tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata';
      }
    } catch (e) {}

    if (TIMEZONE_COORDINATE_MAP[tz]) {
      const info = TIMEZONE_COORDINATE_MAP[tz];
      return { lat: info.lat, lng: info.lng, name: `${info.name} (${tz})`, source: 'timezone', tz };
    }

    // Robust universal fallback: calculate longitude based on local system timezone offset
    // so solar day/night transitions deterministically align with the host machine's clock
    let offsetMinutes = 330; // default IST (+5:30)
    try {
      offsetMinutes = -new Date().getTimezoneOffset();
    } catch (e) {}
    const fallbackLng = (offsetMinutes / 60) * 15;
    const fallbackLat = 20.0;
    return { lat: fallbackLat, lng: fallbackLng, name: `System Timezone (${tz || 'Local'})`, source: 'fallback', tz };
  }

  let cachedSolarSchedule = null;
  let cachedSolarDateKey = null;

  function getSolarSchedule(now = new Date()) {
    const loc = resolveLocationCoordinates();
    const dateKey = `${now.getFullYear()}-${now.getMonth()+1}-${now.getDate()}_${loc.lat.toFixed(2)}_${loc.lng.toFixed(2)}`;

    if (cachedSolarSchedule && cachedSolarDateKey === dateKey) {
      const { sunrise, sunset, nextSunrise } = cachedSolarSchedule;
      const isDark = (now < sunrise) || (now >= sunset);
      let nextTransition = null;
      let nextTransitionType = '';
      if (now < sunrise) {
        nextTransition = sunrise;
        nextTransitionType = 'sunrise';
      } else if (now < sunset) {
        nextTransition = sunset;
        nextTransitionType = 'sunset';
      } else {
        nextTransition = nextSunrise;
        nextTransitionType = 'sunrise';
      }
      return {
        ...cachedSolarSchedule,
        isDark,
        nextTransition,
        nextTransitionType,
        currentTime: now
      };
    }

    const todayTimes = calculateSolarTimes(now, loc.lat, loc.lng);
    const tomorrow = new Date(now.getTime() + 24 * 3600 * 1000);
    const tomorrowTimes = calculateSolarTimes(tomorrow, loc.lat, loc.lng);

    let sunrise = todayTimes.sunrise;
    let sunset = todayTimes.sunset;
    let nextSunrise = tomorrowTimes.sunrise;

    // Safety fallback for extreme latitudes
    if (!sunrise || !sunset) {
      const sRise = new Date(now);
      sRise.setHours(6, 30, 0, 0);
      const sSet = new Date(now);
      sSet.setHours(18, 30, 0, 0);
      sunrise = sRise;
      sunset = sSet;
      const nRise = new Date(tomorrow);
      nRise.setHours(6, 30, 0, 0);
      nextSunrise = nRise;
    }

    const isDark = (now < sunrise) || (now >= sunset);
    let nextTransition = null;
    let nextTransitionType = '';
    if (now < sunrise) {
      nextTransition = sunrise;
      nextTransitionType = 'sunrise';
    } else if (now < sunset) {
      nextTransition = sunset;
      nextTransitionType = 'sunset';
    } else {
      nextTransition = nextSunrise;
      nextTransitionType = 'sunrise';
    }

    cachedSolarSchedule = {
      sunrise,
      sunset,
      nextSunrise,
      isDark,
      nextTransition,
      nextTransitionType,
      location: loc,
      calculatedAt: now
    };
    cachedSolarDateKey = dateKey;

    return {
      ...cachedSolarSchedule,
      currentTime: now
    };
  }

  // Central Theme Configuration & Compatibility Registry
  const THEME_REGISTRY = {
    families: {
      material: {
        id: 'material',
        name: 'Material',
        description: 'Modern Material-inspired design system with elevation and clean density',
        styles: {
          minimal: {
            id: 'minimal',
            name: 'Minimal SaaS',
            family: 'material',
            description: 'Premium, clean SaaS design inspired by modern commercial billing tools',
            defaultPalette: 'emerald',
            defaultFont: 'jakarta',
            compatiblePalettes: ['emerald', 'slate', 'indigo', 'sage', 'teal'],
            compatibleFonts: ['jakarta', 'inter', 'roboto', 'system']
          },
          modern: {
            id: 'modern',
            name: 'Modern Material',
            family: 'material',
            description: 'Established RupeeCRM interface with card grid and multi-tier metrics',
            defaultPalette: 'forest',
            defaultFont: 'jakarta',
            compatiblePalettes: ['forest', 'navy', 'teal', 'plum', 'burgundy', 'amber'],
            compatibleFonts: ['jakarta', 'inter', 'roboto', 'system']
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
      // Material Minimal SaaS
      emerald: { id: 'emerald', name: 'Emerald SaaS (Default)', primary: '#168B45', accent: '#E8F5E9', family: 'material', styles: ['minimal'] },
      slate: { id: 'slate', name: 'Slate Minimal', primary: '#0F172A', accent: '#F1F5F9', family: 'material', styles: ['minimal'] },
      indigo: { id: 'indigo', name: 'Indigo Modern', primary: '#4F46E5', accent: '#EEF2FF', family: 'material', styles: ['minimal'] },
      sage: { id: 'sage', name: 'Sage Nordic', primary: '#2D6A4F', accent: '#D8F3DC', family: 'material', styles: ['minimal'] },
      teal: { id: 'teal', name: 'Petrol Teal', primary: '#0F766E', accent: '#F0FDFA', family: 'material', styles: ['minimal', 'modern'] },
      // Material Modern
      forest: { id: 'forest', name: 'Forest Green', primary: '#168B45', accent: '#E8F5E9', family: 'material', styles: ['modern'] },
      navy: { id: 'navy', name: 'Navy Blue', primary: '#1E40AF', accent: '#EFF6FF', family: 'material', styles: ['modern'] },
      plum: { id: 'plum', name: 'Royal Plum', primary: '#581C87', accent: '#FAF5FF', family: 'material', styles: ['modern'] },
      burgundy: { id: 'burgundy', name: 'Burgundy', primary: '#831843', accent: '#FDF2F8', family: 'material', styles: ['modern'] },
      amber: { id: 'amber', name: 'Amber Gold', primary: '#B45309', accent: '#FFFBEB', family: 'material', styles: ['modern'] },
      // Classic Retro '98
      'win98-standard': { id: 'win98-standard', name: 'Win98 Standard', primary: '#000080', accent: '#008080', family: 'classic', styles: ['retro98'] },
      'win98-noir': { id: 'win98-noir', name: 'Win98 Noir', primary: '#000000', accent: '#00FFFF', family: 'classic', styles: ['retro98'] },
      'classic-slate': { id: 'classic-slate', name: 'Classic Slate', primary: '#3A6EA5', accent: '#D4D0C8', family: 'classic', styles: ['retro98'] },
      'desert-gold': { id: 'desert-gold', name: 'Desert Gold', primary: '#800000', accent: '#D2B48C', family: 'classic', styles: ['retro98'] },
      'spruce-green': { id: 'spruce-green', name: 'Spruce Green', primary: '#004040', accent: '#20B2AA', family: 'classic', styles: ['retro98'] },
      'retro-plum': { id: 'retro-plum', name: 'Retro Plum', primary: '#4A0E4E', accent: '#FF007F', family: 'classic', styles: ['retro98'] }
    },
    fonts: {
      jakarta: { id: 'jakarta', name: 'Plus Jakarta Sans (Default)', stack: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif", family: 'material' },
      inter: { id: 'inter', name: 'Inter', stack: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif", family: 'material' },
      roboto: { id: 'roboto', name: 'Roboto', stack: "'Roboto', -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif", family: 'material' },
      system: { id: 'system', name: 'System Default', stack: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif", family: 'material' },
      tahoma: { id: 'tahoma', name: 'Tahoma (Win98 SE)', stack: "'Tahoma', 'Segoe UI', Arial, sans-serif", family: 'classic' },
      mssansserif: { id: 'mssansserif', name: 'MS Sans Serif (Win95)', stack: "'MS Sans Serif', 'Tahoma', 'Segoe UI', sans-serif", family: 'classic' },
      fixedsys: { id: 'fixedsys', name: 'Fixedsys (Monospace)', stack: "'Fixedsys', 'Courier New', monospace", family: 'classic' },
      segoe: { id: 'segoe', name: 'Segoe UI (System)', stack: "'Segoe UI', Tahoma, Arial, sans-serif", family: 'classic' }
    }
  };

  const DEFAULT_ACCENT = 'emerald';
  const ACCENT_ALIASES = {
    emerald: 'emerald',
    green: 'emerald',
    forest: 'forest',
    blue: 'navy',
    violet: 'plum',
    purple: 'plum',
    red: 'burgundy',
    orange: 'amber',
    steel: 'navy'
  };

  function normalizeAccent(accent, style) {
    if (!accent || typeof accent !== 'string') {
      return style === 'minimal' ? 'emerald' : (style === 'retro98' ? 'win98-standard' : 'forest');
    }
    const lower = accent.trim().toLowerCase();
    if (THEME_REGISTRY.palettes[lower]) return lower;
    if (ACCENT_ALIASES[lower]) return ACCENT_ALIASES[lower];
    return style === 'minimal' ? 'emerald' : (style === 'retro98' ? 'win98-standard' : 'forest');
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

    // Detect genuinely fresh installation (zero prior theme keys)
    const hasExistingTheme = (
      localStorage.getItem('themeFamily') !== null ||
      localStorage.getItem('themeStyle') !== null ||
      localStorage.getItem('themeMode') !== null ||
      localStorage.getItem('themeBase') !== null ||
      localStorage.getItem('themeDarkMode') !== null ||
      localStorage.getItem('themeDarkSchedule') !== null
    );

    if (!hasExistingTheme) {
      // ═══════════════════════════════════════════════════════════════
      // FRESH INSTALLATION AUTHORITATIVE DEFAULTS:
      // 1. Theme Family: Minimal SaaS ('material')
      // 2. Style: Minimal ('minimal')
      // 3. Theme: Emerald SaaS ('emerald')
      // 4. Default Font: Plus Jakarta Sans ('jakarta')
      // 5. Appearance Mode: Automatic Sunset-to-Sunrise ('auto')
      // ═══════════════════════════════════════════════════════════════
      family = 'material';
      style = 'minimal';
      accent = 'emerald';
      font = 'jakarta';
      mode = 'auto';
      schedule = 'sunset';

      const initialSolar = getSolarSchedule();
      isDark = initialSolar.isDark;

      try {
        localStorage.setItem('themeFamily', 'material');
        localStorage.setItem('themeStyle', 'minimal');
        localStorage.setItem('themeMode', 'auto');
        localStorage.setItem('themeDarkMode', String(isDark));
        localStorage.setItem('themeAccentColor', 'emerald');
        localStorage.setItem('themeFontFamily', 'jakarta');
        localStorage.setItem('themeScheduleEnabled', 'true');
        localStorage.setItem('themeScheduleMode', 'sunset');
        localStorage.setItem('themeDarkSchedule', 'sunset');
      } catch (e) {}
    }

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

    // Translate legacy flat themeFamily values to two-tier
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
      font = targetStyleObj ? targetStyleObj.defaultFont : 'jakarta';
    }

    // Theme scheduling validation
    const validSchedules = ['sunset', 'manual', 'system', 'custom'];
    if (!schedule || !validSchedules.includes(schedule)) {
      schedule = (mode === 'auto') ? 'sunset' : 'manual';
    }

    const scheduleEnabled = (mode === 'auto' || schedule === 'sunset' || schedule === 'custom');

    return {
      family,
      style,
      mode,
      isDark,
      schedule,
      scheduleEnabled,
      scheduleMode: (mode === 'auto' || schedule === 'sunset') ? 'sunset' : 'custom',
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
    // 1. Manual Light override explicitly disables dark appearance
    if (state.mode === 'light') {
      return false;
    }
    // 2. Manual Dark override explicitly enables dark appearance
    if (state.mode === 'dark') {
      return true;
    }
    // 3. Automatic Sunset-to-Sunrise mode (NOAA Astronomical Solar Engine)
    if (state.mode === 'auto' || state.schedule === 'sunset' || (state.scheduleEnabled && state.scheduleMode === 'sunset')) {
      const solar = getSolarSchedule();
      return solar.isDark;
    }
    // 4. Custom Time Window
    if (state.scheduleEnabled && state.scheduleMode === 'custom') {
      return isWithinTimeRange(state.customDarkStart || state.customStart, state.customLightStart || state.customEnd);
    }
    // 5. System OS preference
    if (state.mode === 'system' || state.schedule === 'system') {
      return !!(typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    }
    return state.isDark === true;
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
  let transitionTimer = null;

  function getEffectiveState() {
    if (activeLiveConfig) {
      return activeLiveConfig;
    }
    return getStoredState();
  }

  function scheduleNextSolarTransition() {
    if (transitionTimer) {
      clearTimeout(transitionTimer);
      transitionTimer = null;
    }
    const state = getEffectiveState();
    if (state.mode === 'auto' || state.schedule === 'sunset' || (state.scheduleEnabled && state.scheduleMode === 'sunset')) {
      const solar = getSolarSchedule();
      if (solar && solar.nextTransition) {
        const ms = solar.nextTransition.getTime() - Date.now();
        if (ms > 0 && ms <= 86400000) {
          transitionTimer = setTimeout(() => {
            applyThemeState(true);
            scheduleNextSolarTransition();
          }, ms + 1000);
        }
      }
    }
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

    // Only persist themeBase when in steady state
    if (!explicitState && !activeLiveConfig) {
      localStorage.setItem('themeBase', activeClass);
    }

    // Schedule next automatic sunrise/sunset transition if in auto mode
    scheduleNextSolarTransition();

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

  // Periodic schedule guard check (every 30 seconds)
  setInterval(() => {
    const state = getStoredState();
    if (state.mode === 'auto' || state.scheduleEnabled || state.schedule === 'sunset' || state.schedule === 'custom') {
      applyThemeState(true);
    }
  }, 30000);

  // Check on tab focus / wake from system sleep
  if (typeof window !== 'undefined') {
    window.addEventListener('focus', () => applyThemeState(true));
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        applyThemeState(true);
      }
    });

    // Cross-tab synchronization listener
    window.addEventListener('storage', (e) => {
      if (e.key && (e.key.startsWith('theme') || e.key.startsWith('billsoft_'))) {
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

  // ══════════════════════════════════════════════════════════════════════════
  // GLOBAL APPLICATION PROGRAMMING INTERFACES (WINDOW)
  // ══════════════════════════════════════════════════════════════════════════

  window.getThemeRegistry = function() {
    return THEME_REGISTRY;
  };

  window.getSolarSchedule = function(now = new Date()) {
    return getSolarSchedule(now);
  };

  window.getCitiesCatalog = function() {
    return CITIES_CATALOG;
  };

  window.setLocationCity = function(cityId) {
    if (!cityId) return;
    localStorage.setItem('themeLocationCity', cityId);
    cachedSolarSchedule = null;
    cachedSolarDateKey = null;
    applyThemeState(true);
  };

  window.detectDeviceLocation = function(onSuccess, onError) {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      if (typeof onError === 'function') onError(new Error('Geolocation is not supported by your browser/device.'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        localStorage.setItem('themeLatitude', String(lat));
        localStorage.setItem('themeLongitude', String(lng));
        localStorage.setItem('themeLocationCity', 'auto');
        localStorage.setItem('themeLocationName', `Detected Location (${lat.toFixed(2)}°, ${lng.toFixed(2)}°)`);
        cachedSolarSchedule = null;
        cachedSolarDateKey = null;
        applyThemeState(true);
        if (typeof onSuccess === 'function') onSuccess({ lat, lng });
      },
      (err) => {
        if (typeof onError === 'function') onError(err);
      },
      { timeout: 10000, enableHighAccuracy: false }
    );
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
    const mode = (config.mode === 'dark' || config.mode === 'light' || config.mode === 'auto' || config.mode === 'system') ? config.mode : 'auto';
    const accent = normalizeAccent(config.palette || config.accent, style);
    const font = (config.font && THEME_REGISTRY.fonts[config.font]) ? config.font : (style === 'retro98' ? 'tahoma' : 'jakarta');
    const scheduleEnabled = (mode === 'auto') ? true : !!config.scheduleEnabled;
    const scheduleMode = (mode === 'auto') ? 'sunset' : (config.scheduleMode === 'sunset' ? 'sunset' : 'custom');
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

  window.setAutoMode = function() {
    localStorage.setItem('themeMode', 'auto');
    localStorage.setItem('themeDarkSchedule', 'sunset');
    localStorage.setItem('themeScheduleEnabled', 'true');
    localStorage.setItem('themeScheduleMode', 'sunset');
    applyThemeState(true);
  };

  window.setDarkSchedule = function(schedule, customStart, customEnd) {
    const validSchedules = ['sunset', 'manual', 'system', 'custom'];
    if (!validSchedules.includes(schedule)) schedule = 'sunset';
    localStorage.setItem('themeDarkSchedule', schedule);
    localStorage.setItem('themeScheduleEnabled', String(schedule === 'sunset' || schedule === 'custom'));
    localStorage.setItem('themeDarkScheduleCustomized', 'true');
    if (schedule === 'sunset') {
      localStorage.setItem('themeMode', 'auto');
    }
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
      const mode = (config.mode === 'dark' || config.mode === 'light' || config.mode === 'auto' || config.mode === 'system') ? config.mode : 'auto';
      const palette = normalizeAccent(config.palette, style);
      const font = (config.font && THEME_REGISTRY.fonts[config.font]) ? config.font : (style === 'retro98' ? 'tahoma' : 'jakarta');
      const scheduleEnabled = (mode === 'auto') ? true : !!config.scheduleEnabled;
      const scheduleMode = (mode === 'auto') ? 'sunset' : (config.scheduleMode === 'sunset' ? 'sunset' : 'custom');
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
      localStorage.setItem('themeDarkSchedule', (mode === 'auto' || scheduleEnabled) ? scheduleMode : 'manual');
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
