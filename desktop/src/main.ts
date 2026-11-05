import { app, BrowserWindow } from "electron";
import path from "node:path";

app.setAppUserModelId("io.github.selimerenkaya.phoneviewer"); // required for Windows toasts

app.whenReady().then(() => {
  const win = new BrowserWindow({
    width: 480,
    height: 900,
    title: "Phone Viewer",
    webPreferences: {
      // The whole app runs in the preload: it needs Node (tls) and the DOM (WebCodecs, canvas).
      // Safe because the window only ever loads our local index.html.
      preload: path.join(__dirname, "renderer", "app.js"),
      sandbox: false,
    },
  });
  win.loadFile(path.join(__dirname, "..", "src", "renderer", "index.html"));
});

app.on("window-all-closed", () => app.quit());
