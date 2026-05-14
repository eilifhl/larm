.PHONY: build-wasm build-web build run clean

build-wasm:
	cargo build --manifest-path rust/Cargo.toml --target wasm32-unknown-unknown --release

build-web:
	node scripts/build.mjs

build: build-wasm build-web

run: build
	python3 -m http.server 8080 -d dist

clean:
	cargo clean --manifest-path rust/Cargo.toml
	rm -rf dist
