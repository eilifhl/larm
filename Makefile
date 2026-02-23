UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
	LIB_EXT := dylib
else
	LIB_EXT := so
endif

RUST_DIR := rust
LIBS_DIR := libs
LIB_NAME := liblarm.$(LIB_EXT)

.PHONY: all build-rust copy-lib run clean

all: run

build-rust:
	@echo "Building Rust engine..."
	@cd $(RUST_DIR) && cargo build --release

copy-lib: build-rust
	@echo "Copying library to $(LIBS_DIR)..."
	@mkdir -p $(LIBS_DIR)
	@cp $(RUST_DIR)/target/release/$(LIB_NAME) $(LIBS_DIR)/

run: copy-lib
	@echo "Starting Server..."
	@./gradlew run

clean:
	@echo "Cleaning up..."
	@cd $(RUST_DIR) && cargo clean
	@./gradlew clean
	@rm -rf $(LIBS_DIR)
