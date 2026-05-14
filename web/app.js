const MAX_PROXY_WIDTH = 1200;
const LOUPE_SIZE = 800;
const WASM_URL = "./larm.wasm";

const state = {
  workerReady: false,
  requestId: 0,
  pending: new Map(),
  previewTimeout: null,
  viewMode: "proxy",
  loupeX: 0.5,
  loupeY: 0.5,
  originalCanvas: null,
  proxyCanvas: null,
  fileName: "larm_export.png",
  previewRenderInFlight: false,
  queuedPreviewJob: null,
};

const uploadArea = document.getElementById("uploadArea");
const uploadMessage = document.getElementById("uploadMessage");
const fileInput = document.getElementById("fileInput");
const preview = document.getElementById("preview");
const placeholder = document.getElementById("placeholder");
const loadingOverlay = document.getElementById("loadingOverlay");
const controls = document.getElementById("controls");
const exportBtn = document.getElementById("exportBtn");
const status = document.getElementById("status");
const btnProxy = document.getElementById("btnProxy");
const btnLoupe = document.getElementById("btnLoupe");
const viewControls = document.getElementById("viewControls");
const viewHint = document.getElementById("viewHint");

const worker = new Worker("./worker.js", { type: "module" });

const sliders = [
  "previewQuality",
  "size",
  "intensity",
  "crystalSharpness",
  "layers",
  "shadowGrain",
  "midtoneGrain",
  "highlightGrain",
  "tonalSmoothness",
  "saturation",
  "exposure",
  "depth",
  "chromatic",
  "relief",
];

worker.addEventListener("message", (event) => {
  const message = event.data;

  if (message.type === "ready") {
    state.workerReady = true;
    setStatus("Awaiting upload.");
    return;
  }

  if (message.type === "rendered") {
    const pending = state.pending.get(message.requestId);
    if (!pending) {
      return;
    }

    state.pending.delete(message.requestId);
    pending.resolve(new Uint8Array(message.pixels));
    return;
  }

  if (message.type === "error") {
    const pending = message.requestId ? state.pending.get(message.requestId) : null;
    if (pending) {
      state.pending.delete(message.requestId);
      pending.reject(new Error(message.error));
    } else {
      setStatus(`System error: ${message.error}`);
    }
  }
});

worker.postMessage({ type: "init", wasmUrl: WASM_URL });

function createCanvas(width, height) {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  return canvas;
}

function setStatus(text) {
  status.textContent = text;
  uploadMessage.textContent = text;
}

function setLoadingOverlay(visible, text = "Loading preview…") {
  loadingOverlay.textContent = text;
  loadingOverlay.classList.toggle("visible", visible);
}

function setViewMode(mode) {
  state.viewMode = mode;
  if (mode === "proxy") {
    btnProxy.classList.add("active");
    btnLoupe.classList.remove("active");
    viewHint.textContent = "Click image to inspect 100% details";
    preview.style.cursor = "crosshair";
  } else {
    btnLoupe.classList.add("active");
    btnProxy.classList.remove("active");
    viewHint.textContent = "Click to return to Fit view";
    preview.style.cursor = "zoom-out";
  }

  void renderPreview();
}

function getVal(id) {
  const el = document.getElementById(id);
  return el.dataset.override !== undefined
    ? Number.parseFloat(el.dataset.override)
    : Number.parseFloat(el.value);
}

function getParams() {
  return {
    size: getVal("size"),
    intensity: getVal("intensity"),
    crystalSharpness: getVal("crystalSharpness"),
    saturation: getVal("saturation"),
    exposure: getVal("exposure"),
    shadowGrain: getVal("shadowGrain"),
    midtoneGrain: getVal("midtoneGrain"),
    highlightGrain: getVal("highlightGrain"),
    tonalSmoothness: getVal("tonalSmoothness"),
    depth: getVal("depth"),
    chromatic: getVal("chromatic"),
    relief: getVal("relief"),
    layers: Number.parseInt(document.getElementById("layers").value, 10),
  };
}

function getPreviewQuality() {
  return getVal("previewQuality");
}

function rgbaToRgb(rgba) {
  const rgb = new Uint8Array((rgba.length / 4) * 3);
  let rgbIndex = 0;
  for (let i = 0; i < rgba.length; i += 4) {
    rgb[rgbIndex++] = rgba[i];
    rgb[rgbIndex++] = rgba[i + 1];
    rgb[rgbIndex++] = rgba[i + 2];
  }
  return rgb;
}

function rgbToImageData(rgb, width, height) {
  const rgba = new Uint8ClampedArray(width * height * 4);
  let rgbIndex = 0;
  for (let i = 0; i < rgba.length; i += 4) {
    rgba[i] = rgb[rgbIndex++];
    rgba[i + 1] = rgb[rgbIndex++];
    rgba[i + 2] = rgb[rgbIndex++];
    rgba[i + 3] = 255;
  }
  return new ImageData(rgba, width, height);
}

function drawProcessedToCanvas(canvas, rgb, width, height, displayWidth, displayHeight) {
  canvas.width = width;
  canvas.height = height;
  canvas.style.width = `${displayWidth}px`;
  canvas.style.height = `${displayHeight}px`;
  canvas.getContext("2d").putImageData(rgbToImageData(rgb, width, height), 0, 0);
}

function makeProxyCanvas(sourceCanvas) {
  if (sourceCanvas.width <= MAX_PROXY_WIDTH) {
    const copy = createCanvas(sourceCanvas.width, sourceCanvas.height);
    copy.getContext("2d").drawImage(sourceCanvas, 0, 0);
    return copy;
  }

  const scale = MAX_PROXY_WIDTH / sourceCanvas.width;
  const width = MAX_PROXY_WIDTH;
  const height = Math.max(1, Math.round(sourceCanvas.height * scale));
  const proxy = createCanvas(width, height);
  proxy.getContext("2d").drawImage(sourceCanvas, 0, 0, width, height);
  return proxy;
}

function makeLoupeCanvas() {
  const source = state.originalCanvas;
  const maxLoupeDimension = Math.min(LOUPE_SIZE, 640);
  const cropWidth = Math.min(maxLoupeDimension, source.width);
  const cropHeight = Math.min(maxLoupeDimension, source.height);
  const centerX = Math.round(source.width * state.loupeX);
  const centerY = Math.round(source.height * state.loupeY);
  const startX = Math.min(
    Math.max(0, centerX - Math.floor(cropWidth / 2)),
    Math.max(0, source.width - cropWidth),
  );
  const startY = Math.min(
    Math.max(0, centerY - Math.floor(cropHeight / 2)),
    Math.max(0, source.height - cropHeight),
  );

  const loupe = createCanvas(cropWidth, cropHeight);
  loupe
    .getContext("2d")
    .drawImage(source, startX, startY, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight);
  return loupe;
}

function currentSourceCanvas() {
  return state.viewMode === "loupe" ? makeLoupeCanvas() : state.proxyCanvas;
}

function getDisplaySize(sourceCanvas) {
  const viewportWidth = Math.max(320, Math.floor(window.innerWidth - 420));
  const viewportHeight = Math.max(240, Math.floor(window.innerHeight - 220));
  const maxWidth = Math.min(sourceCanvas.width, viewportWidth);
  const maxHeight = Math.min(sourceCanvas.height, viewportHeight);
  const scale = Math.min(maxWidth / sourceCanvas.width, maxHeight / sourceCanvas.height, 1);

  return {
    width: Math.max(1, Math.round(sourceCanvas.width * scale)),
    height: Math.max(1, Math.round(sourceCanvas.height * scale)),
  };
}

function scaleCanvasForPreview(sourceCanvas) {
  const quality = getPreviewQuality();
  const displaySize = getDisplaySize(sourceCanvas);
  const maxWidth = Math.min(sourceCanvas.width, Math.round(displaySize.width * quality));
  const maxHeight = Math.min(sourceCanvas.height, Math.round(displaySize.height * quality));
  const scale = Math.min(maxWidth / sourceCanvas.width, maxHeight / sourceCanvas.height, 1);

  if (scale >= 0.98) {
    return {
      canvas: sourceCanvas,
      displayWidth: displaySize.width,
      displayHeight: displaySize.height,
    };
  }

  const width = Math.max(1, Math.round(sourceCanvas.width * scale));
  const height = Math.max(1, Math.round(sourceCanvas.height * scale));
  const scaled = createCanvas(width, height);
  scaled.getContext("2d").drawImage(sourceCanvas, 0, 0, width, height);
  return {
    canvas: scaled,
    displayWidth: displaySize.width,
    displayHeight: displaySize.height,
  };
}

function renderWithWorker(width, height, pixels, params) {
  return new Promise((resolve, reject) => {
    const requestId = ++state.requestId;
    state.pending.set(requestId, { resolve, reject });
    worker.postMessage(
      {
        type: "render",
        requestId,
        wasmUrl: WASM_URL,
        width,
        height,
        pixels: pixels.buffer,
        params,
      },
      [pixels.buffer],
    );
  });
}

async function renderPreview() {
  if (!state.workerReady || !state.proxyCanvas) {
    return;
  }

  const scaledPreview = scaleCanvasForPreview(currentSourceCanvas());
  const source = scaledPreview.canvas;
  const ctx = source.getContext("2d");
  const imageData = ctx.getImageData(0, 0, source.width, source.height);
  const rgb = rgbaToRgb(imageData.data);
  const params = getParams();

  state.queuedPreviewJob = {
    width: source.width,
    height: source.height,
    displayWidth: scaledPreview.displayWidth,
    displayHeight: scaledPreview.displayHeight,
    rgb,
    params,
    mode: state.viewMode,
  };

  if (state.previewRenderInFlight) {
    return;
  }

  state.previewRenderInFlight = true;
  setLoadingOverlay(true, state.viewMode === "loupe" ? "Rendering loupe…" : "Rendering preview…");

  while (state.queuedPreviewJob) {
    const job = state.queuedPreviewJob;
    state.queuedPreviewJob = null;

    setStatus(
      job.mode === "loupe"
        ? `Rendering loupe ${job.width}×${job.height}…`
        : `Rendering preview ${job.width}×${job.height} at ${Math.round(getPreviewQuality() * 100)}%…`,
    );

    try {
      const rendered = await renderWithWorker(job.width, job.height, job.rgb, job.params);
      drawProcessedToCanvas(
        preview,
        rendered,
        job.width,
        job.height,
        job.displayWidth,
        job.displayHeight,
      );
      preview.style.display = "block";
      placeholder.style.display = "none";

      const target = job.mode === "loupe" ? state.originalCanvas : state.proxyCanvas;
      setStatus(`Render complete. Target: ${target.width}×${target.height}`);
    } catch (error) {
      setStatus(`Render failed: ${error.message}`);
    }
  }

  state.previewRenderInFlight = false;
  setLoadingOverlay(false);
}

function debouncePreview() {
  if (state.previewTimeout) {
    clearTimeout(state.previewTimeout);
  }

  setStatus("Rendering…");
  state.previewTimeout = setTimeout(() => {
    void renderPreview();
  }, 90);
}

async function decodeFile(file) {
  if ("createImageBitmap" in window) {
    try {
      const bitmap = await createImageBitmap(file);
      const canvas = createCanvas(bitmap.width, bitmap.height);
      canvas.getContext("2d").drawImage(bitmap, 0, 0);
      bitmap.close();
      return canvas;
    } catch (_error) {
      // Fall through to img-based decode for files createImageBitmap rejects.
    }
  }

  const imageUrl = URL.createObjectURL(file);

  try {
    const image = await new Promise((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error("Could not decode image file"));
      el.src = imageUrl;
    });

    const canvas = createCanvas(image.width, image.height);
    canvas.getContext("2d").drawImage(image, 0, 0);
    return canvas;
  } finally {
    URL.revokeObjectURL(imageUrl);
  }
}

async function handleFile(file) {
  if (!file.type.startsWith("image/")) {
    alert("Please upload an image file");
    return;
  }

  if (!state.workerReady) {
    setStatus("WASM engine is still loading.");
    return;
  }

  setStatus("Decoding image in browser…");
  setLoadingOverlay(true, "Loading image…");

  try {
    state.originalCanvas = await decodeFile(file);
    state.proxyCanvas = makeProxyCanvas(state.originalCanvas);
    state.fileName = `${file.name.replace(/\.[^.]+$/, "") || "larm"}_export.png`;
    state.loupeX = 0.5;
    state.loupeY = 0.5;
    state.viewMode = "proxy";

    controls.style.display = "block";
    viewControls.style.display = "flex";
    placeholder.style.display = "none";
    preview.style.display = "block";
    uploadArea.style.display = "none";

    btnProxy.classList.add("active");
    btnLoupe.classList.remove("active");
    viewHint.textContent = "Click image to inspect 100% details";

    await renderPreview();
  } catch (error) {
    setStatus(`System error: ${error.message}`);
    setLoadingOverlay(false);
    alert(`Upload failed: ${error.message}`);
  }
}

uploadArea.addEventListener("click", () => fileInput.click());

uploadArea.addEventListener("dragover", (event) => {
  event.preventDefault();
  uploadArea.classList.add("dragover");
});

uploadArea.addEventListener("dragleave", () => {
  uploadArea.classList.remove("dragover");
});

uploadArea.addEventListener("drop", (event) => {
  event.preventDefault();
  uploadArea.classList.remove("dragover");
  if (event.dataTransfer.files.length > 0) {
    void handleFile(event.dataTransfer.files[0]);
  }
});

fileInput.addEventListener("change", (event) => {
  if (event.target.files.length > 0) {
    void handleFile(event.target.files[0]);
    event.target.value = "";
  }
});

btnProxy.addEventListener("click", () => setViewMode("proxy"));
btnLoupe.addEventListener("click", () => setViewMode("loupe"));

preview.addEventListener("click", (event) => {
  if (!state.proxyCanvas) {
    return;
  }

  if (state.viewMode === "proxy") {
    const rect = preview.getBoundingClientRect();
    state.loupeX = (event.clientX - rect.left) / rect.width;
    state.loupeY = (event.clientY - rect.top) / rect.height;
    setViewMode("loupe");
    return;
  }

  setViewMode("proxy");
});

sliders.forEach((id) => {
  const slider = document.getElementById(id);
  const valueDisplay = document.getElementById(`${id}Value`);

  slider.addEventListener("input", () => {
    delete slider.dataset.override;
    valueDisplay.textContent = slider.value;
    debouncePreview();
  });

  valueDisplay.addEventListener("click", () => {
    const input = document.createElement("input");
    input.type = "text";
    input.className = "slider-value-input";
    input.value = slider.value;
    valueDisplay.replaceWith(input);
    input.focus();
    input.select();

    function commit() {
      let val = Number.parseFloat(input.value);
      if (Number.isNaN(val)) {
        val = Number.parseFloat(slider.value);
      }

      const step = Number.parseFloat(slider.step) || 1;
      const decimals = (step.toString().split(".")[1] || "").length;
      val = Number.parseFloat(val.toFixed(decimals));
      slider.value = `${val}`;
      slider.dataset.override = `${val}`;
      valueDisplay.textContent = `${val}`;
      input.replaceWith(valueDisplay);
      debouncePreview();
    }

    input.addEventListener("blur", commit);
    input.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        input.blur();
      }

      if (event.key === "Escape") {
        input.value = slider.value;
        input.blur();
      }
    });
  });
});

exportBtn.addEventListener("click", async () => {
  if (!state.originalCanvas || !state.workerReady) {
    return;
  }

  exportBtn.disabled = true;
  setStatus(`Processing full resolution ${state.originalCanvas.width}×${state.originalCanvas.height}…`);

  try {
    const ctx = state.originalCanvas.getContext("2d");
    const imageData = ctx.getImageData(
      0,
      0,
      state.originalCanvas.width,
      state.originalCanvas.height,
    );
    const rgb = rgbaToRgb(imageData.data);
    const rendered = await renderWithWorker(
      state.originalCanvas.width,
      state.originalCanvas.height,
      rgb,
      getParams(),
    );

    const exportCanvas = createCanvas(state.originalCanvas.width, state.originalCanvas.height);
    drawProcessedToCanvas(
      exportCanvas,
      rendered,
      state.originalCanvas.width,
      state.originalCanvas.height,
    );

    const blob = await new Promise((resolve) => exportCanvas.toBlob(resolve, "image/png"));
    if (!blob) {
      throw new Error("Failed to encode PNG");
    }

    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = state.fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    setStatus("Export complete.");
  } catch (error) {
    setStatus(`System error: ${error.message}`);
    alert(`Export failed: ${error.message}`);
  } finally {
    exportBtn.disabled = false;
  }
});

window.addEventListener("resize", () => {
  if (state.proxyCanvas) {
    debouncePreview();
  }
});
