const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electron', {
  showUpdateProgress: () => ipcRenderer.send('show-update-progress'),
  hideUpdateProgress: () => ipcRenderer.send('hide-update-progress')
});
