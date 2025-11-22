// js/ui/ui.js
import { layout } from "./layout.js";
import { navigation } from "./navigation.js";

export const ui = {
  render() {
    // Render sidebar + main layout
    document.getElementById("app").innerHTML = layout.renderShell();

    // Start sidebar navigation
    navigation.init();
  }
};
