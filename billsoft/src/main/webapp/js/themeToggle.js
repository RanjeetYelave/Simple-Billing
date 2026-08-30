(() => {
  // Mapping between light and dark variants
  const themeMap = {
    'light-1': 'dark-1',
    'dark-1': 'light-1',
    'light-2': 'dark-2',
    'dark-2': 'light-2'
  };

  // Retrieve stored theme or default to light-1
  function getStoredTheme() {
    return localStorage.getItem('themeBase') || 'light-1';
  }

  // Initialize theme on page load
  const initialTheme = getStoredTheme();
  document.documentElement.classList.add(initialTheme);

  // Expose a global toggle function for the button
  window.toggleTheme = function() {
    const current = getStoredTheme();
    const next = themeMap[current] || 'dark-1';
    document.documentElement.classList.replace(current, next);
    localStorage.setItem('themeBase', next);
    // Notify any listeners that the theme changed
    window.dispatchEvent(new Event('themeChanged'));
  };
})();
