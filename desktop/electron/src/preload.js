/**
 * RupeeCRM Desktop - Secure Preload Bridge
 *
 * Enforces strict context isolation and provides an allowlisted,
 * validated API bridge between the Renderer and Main process.
 */

const { contextBridge, ipcRenderer } = require('electron');

// Disallowed URL schemes for external opening to prevent protocol hijacking
const ALLOWED_EXTERNAL_PROTOCOLS = new Set(['http:', 'https:', 'mailto:']);

contextBridge.exposeInMainWorld('rupeeDesktop', {
  isElectron: true,

  // App & Version Info
  getVersion: () => ipcRenderer.invoke('app:get-version'),

  // Backend Health & Status
  getBackendStatus: () => ipcRenderer.invoke('backend:get-status'),
  getDiagnostics: () => ipcRenderer.invoke('diagnostics:get-info'),

  // Directory & System Actions
  openLogsFolder: () => ipcRenderer.invoke('system:open-logs'),
  openDataFolder: () => ipcRenderer.invoke('system:open-data'),
  restartBackend: () => ipcRenderer.invoke('backend:restart'),

  // Window Controls
  minimizeWindow: () => ipcRenderer.send('window:minimize'),
  maximizeWindow: () => ipcRenderer.send('window:maximize'),
  closeWindow: () => ipcRenderer.send('window:close'),

  // Safe External Navigation
  openExternal: (urlStr) => {
    if (typeof urlStr !== 'string') return;
    try {
      const parsed = new URL(urlStr);
      if (ALLOWED_EXTERNAL_PROTOCOLS.has(parsed.protocol)) {
        ipcRenderer.send('system:open-external', urlStr);
      } else {
        console.warn('Blocked opening untrusted external protocol:', parsed.protocol);
      }
    } catch (e) {
      console.error('Invalid URL provided to openExternal:', e);
    }
  },

  // Event Listeners for State Updates
  onBackendStateChange: (callback) => {
    if (typeof callback !== 'function') return () => {};
    const subscription = (event, data) => callback(data);
    ipcRenderer.on('backend:state-change', subscription);
    return () => ipcRenderer.removeListener('backend:state-change', subscription);
  }
});
