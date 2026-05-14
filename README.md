# larm
larm is a film grain/noise generator written in Rust and delivered as a browser-only WebAssembly app.

<img width="2706" height="1548" alt="image" src="https://github.com/user-attachments/assets/a198a67b-b0ea-4177-8ed5-3ac65953f538" />

# Building from source
larm is now a browser-only app. The grain engine is compiled from Rust to WebAssembly and the UI is served as static files.

Requirements:

- Rust with the `wasm32-unknown-unknown` target available
- Node.js
- Python 3 for the local static file server

If you use `rustup`, install the target with:

```bash
rustup target add wasm32-unknown-unknown
```

Build and run:

```bash
git clone git@github.com:eilifhl/larm.git
cd larm
npm run build
npm run dev
```

The UI will then be available at `http://localhost:8080`.

You can also use:

```bash
make run
```

# Examples
![extreme_max_grain.png](./examples/extreme_max_grain.png)

### Cropped
![extreme_max_grain_crop.png](./examples/extreme_max_grain_crop.png)

--- 

![artistic_grungy.png](./examples/artistic_grungy.png)

### Cropped
![artistic_grungy_crop.png](./examples/artistic_grungy_crop.png)
