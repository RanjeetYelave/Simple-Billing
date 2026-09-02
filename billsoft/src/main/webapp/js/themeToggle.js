(() => {
  const THEME_CLASSES = ['light-1', 'light-2', 'dark-1', 'dark-2'];

  function getStoredTheme() {
    const saved = localStorage.getItem('themeBase');
    if (saved && THEME_CLASSES.includes(saved)) {
      return saved;
    }
    return 'light-1';
  }

  function applyTheme(themeId) {
    const root = document.documentElement;
    THEME_CLASSES.forEach(c => root.classList.remove(c));
    root.classList.add(themeId);
    localStorage.setItem('themeBase', themeId);
    window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme: themeId } }));
  }

  // Initialize theme on script load
  const initial = getStoredTheme();
  document.documentElement.classList.add(initial);

  window.getAppTheme = getStoredTheme;
  window.setAppTheme = applyTheme;
  window.toggleTheme = function() {
    const current = getStoredTheme();
    const isDark = current.startsWith('dark');
    const next = isDark ? (current === 'dark-2' ? 'light-2' : 'light-1') : (current === 'light-2' ? 'dark-2' : 'dark-1');
    applyTheme(next);
  };
})();
