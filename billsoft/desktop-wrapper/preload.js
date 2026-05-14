const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electron', {
  showUpdateProgress: () => ipcRenderer.send('show-update-progress'),
  hideUpdateProgress: () => ipcRenderer.send('hide-update-progress'),
  // Signal the main process that an update is starting so it can swap WAR on shutdown
  setUpdating: () => ipcRenderer.send('set-updating')
});