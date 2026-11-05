import { app, BrowserWindow } from "electron";
import path from "node:path";
import { clipboard, ipcMain } from "electron"; // notify

app.setAppUserModelId("io.github.selimerenkaya.phoneviewer"); // required for Windows toasts
// notify: Electron 45 exports `clipboard` only in the main process, so the preload reaches it here.
ipcMain.handle("notify:clipboard-read", () => clipboard.readText());
ipcMain.handle("notify:clipboard-write", (_e, text: unknown) => clipboard.writeText(String(text)));

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
      additionalArguments: [`--user-data=${app.getPath("userData")}`], // link: where the preload keeps its cert and pins
    },
  });
  win.loadFile(path.join(__dirname, "..", "src", "renderer", "index.html"));
});

app.on("window-all-closed", () => app.quit());
