let wasmExports;
let wasmMemory;

function getMemory() {
  if (wasmMemory) {
    return wasmMemory;
  }

  for (const value of Object.values(wasmExports)) {
    if (value instanceof WebAssembly.Memory) {
      wasmMemory = value;
      return wasmMemory;
    }
  }

  throw new Error("WASM memory export not found");
}

async function initWasm(wasmUrl) {
  if (wasmExports) {
    return;
  }

  let result;
  if ("instantiateStreaming" in WebAssembly) {
    try {
      result = await WebAssembly.instantiateStreaming(fetch(wasmUrl), {});
    } catch (_error) {
      const source = await fetch(wasmUrl);
      result = await WebAssembly.instantiate(await source.arrayBuffer(), {});
    }
  } else {
    const source = await fetch(wasmUrl);
    result = await WebAssembly.instantiate(await source.arrayBuffer(), {});
  }

  wasmExports = result.instance.exports;
  getMemory();
}

function processPixels(width, height, pixels, params) {
  const input = new Uint8Array(pixels);
  const len = input.byteLength;
  const memory = getMemory();
  const inputPtr = wasmExports.alloc_buffer(len);
  const outputPtr = wasmExports.alloc_buffer(len);

  try {
    new Uint8Array(memory.buffer, inputPtr, len).set(input);

    const status = wasmExports.apply_grain_buffer(
      inputPtr,
      len,
      outputPtr,
      len,
      width,
      height,
      params.size,
      params.intensity,
      params.crystalSharpness,
      params.saturation,
      params.exposure,
      params.shadowGrain,
      params.midtoneGrain,
      params.highlightGrain,
      params.tonalSmoothness,
      params.depth,
      params.chromatic,
      params.relief,
      params.layers,
    );

    if (status !== 0) {
      throw new Error(`WASM render failed with code ${status}`);
    }

    const output = new Uint8Array(memory.buffer, outputPtr, len).slice();
    return output.buffer;
  } finally {
    wasmExports.free_buffer(inputPtr, len);
    wasmExports.free_buffer(outputPtr, len);
  }
}

self.onmessage = async (event) => {
  const message = event.data;

  try {
    if (message.type === "init") {
      await initWasm(message.wasmUrl);
      self.postMessage({ type: "ready" });
      return;
    }

    if (message.type === "render") {
      await initWasm(message.wasmUrl);
      const buffer = processPixels(
        message.width,
        message.height,
        message.pixels,
        message.params,
      );

      self.postMessage(
        {
          type: "rendered",
          requestId: message.requestId,
          width: message.width,
          height: message.height,
          pixels: buffer,
        },
        [buffer],
      );
    }
  } catch (error) {
    self.postMessage({
      type: "error",
      requestId: message.requestId ?? null,
      error: error instanceof Error ? error.message : String(error),
    });
  }
};
