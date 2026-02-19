#!/bin/bash

set -e

INPUT_FILE="${1:-input.png}"
OUTPUT_DIR="${2:-examples}"
GRAIN_BIN="${3:-./target/release/larm}"
CROP_SIZE="${4:-512x512}" 

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'


log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[DONE]${NC} $1"
}

log_crop() {
    echo -e "${CYAN}[CROP]${NC} $1"
}

log_section() {
    echo -e "\n${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  $1${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

extract_crop() {
    local input="$1"
    local output="$2"
    local crop_size="$3"
    
    if command -v convert &> /dev/null; then
        local dimensions=$(identify -format "%wx%h" "$input")
        local img_width=$(echo "$dimensions" | cut -d'x' -f1)
        local img_height=$(echo "$dimensions" | cut -d'x' -f2)
        
        local crop_width=$(echo "$crop_size" | cut -d'x' -f1)
        local crop_height=$(echo "$crop_size" | cut -d'x' -f2)
        
        local offset_x=$(( (img_width - crop_width) / 2 ))
        local offset_y=$(( (img_height - crop_height) / 2 ))
        
        convert "$input" -crop "${crop_width}x${crop_height}+${offset_x}+${offset_y}" +repage "$output" 2>/dev/null
        return 0
    elif command -v magick &> /dev/null; then
        local dimensions=$(magick identify -format "%wx%h" "$input")
        local img_width=$(echo "$dimensions" | cut -d'x' -f1)
        local img_height=$(echo "$dimensions" | cut -d'x' -f2)
        
        local crop_width=$(echo "$crop_size" | cut -d'x' -f1)
        local crop_height=$(echo "$crop_size" | cut -d'x' -f2)
        
        local offset_x=$(( (img_width - crop_width) / 2 ))
        local offset_y=$(( (img_height - crop_height) / 2 ))
        
        magick "$input" -crop "${crop_width}x${crop_height}+${offset_x}+${offset_y}" +repage "$output" 2>/dev/null
        return 0
    else
        return 1
    fi
}

create_comparison() {
    local original="$1"
    local grain="$2"
    local output="$3"
    
    if command -v convert &> /dev/null; then
        convert "$original" "$grain" +append "$output" 2>/dev/null
        return 0
    elif command -v magick &> /dev/null; then
        magick "$original" "$grain" +append "$output" 2>/dev/null
        return 0
    else
        return 1
    fi
}

generate_example() {
    local filename="$1"
    local desc="$2"
    shift 2
    local params=("$@")
    
    local output_path="${OUTPUT_DIR}/${filename}.png"
    local crop_path="${OUTPUT_DIR}/${filename}_crop.png"
    
    log_info "Generating: $desc"
    log_info "  Output: ${filename}.png"
    
    if $GRAIN_BIN "$INPUT_FILE" -o "$output_path" "${params[@]}" 2>/dev/null; then
        log_success "Created: ${filename}.png"
        
        if extract_crop "$output_path" "$crop_path" "$CROP_SIZE"; then
            log_crop "Created crop: ${filename}_crop.png"
        else
            log_info "  (Skipping crop - ImageMagick not available)"
        fi
    else
        echo -e "${RED}[ERROR]${NC} Failed to generate: ${filename}.png"
        return 1
    fi
}

generate_original_crop() {
    local crop_path="${OUTPUT_DIR}/original_crop.png"
    
    log_info "Generating original reference crop..."
    
    if extract_crop "$INPUT_FILE" "$crop_path" "$CROP_SIZE"; then
        log_crop "Created reference: original_crop.png"
    else
        log_info "Using Python fallback for crop..."
        
        if command -v python3 &> /dev/null; then
            python3 << EOF
from PIL import Image
import os

img = Image.open("$INPUT_FILE")
w, h = img.size
crop_w, crop_h = map(int, "$CROP_SIZE".split('x'))
offset_x = (w - crop_w) // 2
offset_y = (h - crop_h) // 2
crop = img.crop((offset_x, offset_y, offset_x + crop_w, offset_y + crop_h))
crop.save("$crop_path")
EOF
            if [ -f "$crop_path" ]; then
                log_crop "Created reference: original_crop.png (via Python)"
            fi
        fi
    fi
}


echo -e "\n${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        Film Grain Example Generator for README                   ║${NC}"
echo -e "${BLUE}║        With 100% Crop Extraction                                  ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}\n"

if [ ! -f "$GRAIN_BIN" ]; then
    log_info "Binary not found at $GRAIN_BIN"
    log_info "Building release binary..."
    cargo build --release
    GRAIN_BIN="./target/release/grain"
fi

if [ ! -f "$INPUT_FILE" ]; then
    echo -e "${RED}[ERROR]${NC} Input file not found: $INPUT_FILE"
    echo "Usage: $0 <input_image> [output_dir] [grain_binary] [crop_size]"
    exit 1
fi

if ! command -v convert &> /dev/null && ! command -v magick &> /dev/null; then
    echo -e "${YELLOW}[WARN]${NC} ImageMagick not found. Crops will be skipped or use Python fallback."
    echo "       Install with: apt install imagemagick OR brew install imagemagick"
fi

mkdir -p "$OUTPUT_DIR"

log_info "Input: $INPUT_FILE"
log_info "Output directory: $OUTPUT_DIR"
log_info "Binary: $GRAIN_BIN"
log_info "Crop size: $CROP_SIZE"

generate_original_crop

# ============================================================================
# SIZE VARIATIONS
# ============================================================================

log_section "SIZE VARIATIONS (Grain Size)"

generate_example "size_fine" "Fine grain (size 0.5)" --size 0.5
generate_example "size_small" "Small grain (size 1.0)" --size 1.0
generate_example "size_medium" "Medium grain (size 2.0)" --size 2.0
generate_example "size_large" "Large grain (size 4.0)" --size 4.0
generate_example "size_coarse" "Coarse grain (size 8.0)" --size 8.0

# ============================================================================
# INTENSITY VARIATIONS
# ============================================================================

log_section "INTENSITY VARIATIONS"

generate_example "intensity_subtle" "Subtle grain (0.3)" --intensity 0.3
generate_example "intensity_light" "Light grain (0.5)" --intensity 0.5
generate_example "intensity_moderate" "Moderate grain (0.8)" --intensity 0.8
generate_example "intensity_strong" "Strong grain (1.2)" --intensity 1.2
generate_example "intensity_heavy" "Heavy grain (1.5)" --intensity 1.5

# ============================================================================
# SHARPNESS VARIATIONS
# ============================================================================

log_section "SHARPNESS VARIATIONS (Crystal Definition)"

generate_example "sharpness_soft" "Soft grain (2.0)" --sharpness 2.0
generate_example "sharpness_medium" "Medium sharp (6.0)" --sharpness 6.0
generate_example "sharpness_sharp" "Sharp grain (10.0)" --sharpness 10.0
generate_example "sharpness_crystalline" "Crystalline (15.0)" --sharpness 15.0

# ============================================================================
# TONAL VARIATIONS
# ============================================================================

log_section "TONAL GRAIN DISTRIBUTION"

generate_example "tonal_even" "Even distribution" \
    --shadow-grain 1.0 --midtone-grain 1.0 --highlight-grain 1.0

generate_example "tonal_shadows" "Shadows emphasized" \
    --shadow-grain 1.5 --midtone-grain 0.8 --highlight-grain 0.3

generate_example "tonal_highlights" "Highlights emphasized" \
    --shadow-grain 0.3 --midtone-grain 0.8 --highlight-grain 1.5

generate_example "tonal_vintage" "Vintage film look" \
    --shadow-grain 1.4 --midtone-grain 1.0 --highlight-grain 0.4

# ============================================================================
# 3D DEPTH VARIATIONS
# ============================================================================

log_section "3D DEPTH EFFECTS"

generate_example "depth_flat" "Flat grain (depth 0.0)" --depth 0.0
generate_example "depth_subtle" "Subtle depth (0.2)" --depth 0.2
generate_example "depth_moderate" "Moderate depth (0.4)" --depth 0.4
generate_example "depth_strong" "Strong depth (0.7)" --depth 0.7

# ============================================================================
# CHROMATIC VARIATIONS
# ============================================================================

log_section "CHROMATIC SEPARATION (Film Emulation)"

generate_example "chromatic_none" "No chromatic separation" --chromatic 0.0
generate_example "chromatic_subtle" "Subtle separation (1.0)" --chromatic 1.0
generate_example "chromatic_moderate" "Moderate separation (2.0)" --chromatic 2.0
generate_example "chromatic_strong" "Strong separation (4.0)" --chromatic 4.0

# ============================================================================
# RELIEF VARIATIONS
# ============================================================================

log_section "SURFACE RELIEF"

generate_example "relief_none" "No relief" --relief 0.0
generate_example "relief_subtle" "Subtle relief (0.2)" --relief 0.2
generate_example "relief_moderate" "Moderate relief (0.4)" --relief 0.4
generate_example "relief_strong" "Strong relief (0.6)" --relief 0.6

# ============================================================================
# LAYER VARIATIONS
# ============================================================================

log_section "GRAIN LAYERS"

generate_example "layers_1" "Single layer" --layers 1
generate_example "layers_2" "Two layers" --layers 2
generate_example "layers_3" "Three layers (default)" --layers 3
generate_example "layers_4" "Four layers" --layers 4
generate_example "layers_5" "Five layers" --layers 5

# ============================================================================
# FILM STOCK PRESETS
# ============================================================================

log_section "FILM STOCK PRESETS"

generate_example "film_kodak_portra" "Kodak Portra 400 style" \
    --size 2.5 --intensity 0.5 --sharpness 6.0 \
    --shadow-grain 0.8 --midtone-grain 0.7 --highlight-grain 0.4 \
    --depth 0.3 --chromatic 1.5 --relief 0.2 --layers 3

generate_example "film_kodak_tri_x" "Kodak Tri-X style" \
    --size 3.0 --intensity 1.0 --sharpness 8.0 \
    --shadow-grain 1.3 --midtone-grain 1.0 --highlight-grain 0.5 \
    --depth 0.5 --chromatic 2.0 --relief 0.3 --layers 3

generate_example "film_ilford_hp5" "Ilford HP5 Plus style" \
    --size 2.8 --intensity 0.9 --sharpness 7.0 \
    --shadow-grain 1.2 --midtone-grain 0.9 --highlight-grain 0.5 \
    --depth 0.4 --chromatic 1.8 --relief 0.25 --layers 3

generate_example "film_fuji_400h" "Fuji 400H style" \
    --size 2.2 --intensity 0.6 --sharpness 5.0 \
    --shadow-grain 0.9 --midtone-grain 0.8 --highlight-grain 0.6 \
    --depth 0.35 --chromatic 2.5 --relief 0.2 --layers 4

generate_example "film_cinestill" "CineStill 800T style" \
    --size 2.0 --intensity 0.7 --sharpness 9.0 \
    --shadow-grain 1.0 --midtone-grain 0.8 --highlight-grain 0.7 \
    --depth 0.6 --chromatic 3.0 --relief 0.35 --layers 4

# ============================================================================
# ARTISTIC PRESETS
# ============================================================================

log_section "ARTISTIC PRESETS"

generate_example "artistic_dreamy" "Dreamy soft" \
    --size 6.0 --intensity 0.4 --sharpness 3.0 \
    --shadow-grain 0.5 --midtone-grain 0.6 --highlight-grain 0.8 \
    --depth 0.2 --chromatic 1.0 --relief 0.1 --layers 2

generate_example "artistic_grungy" "Grungy texture" \
    --size 4.0 --intensity 1.4 --sharpness 12.0 \
    --shadow-grain 1.6 --midtone-grain 1.2 --highlight-grain 0.6 \
    --depth 0.7 --chromatic 3.5 --relief 0.5 --layers 4

generate_example "artistic_vintage_polaroid" "Vintage Polaroid" \
    --size 5.0 --intensity 0.8 --sharpness 4.0 \
    --shadow-grain 1.0 --midtone-grain 0.9 --highlight-grain 0.7 \
    --depth 0.3 --chromatic 2.5 --relief 0.3 --layers 3

generate_example "artistic_dramatic" "Dramatic cinema" \
    --size 3.5 --intensity 1.0 --sharpness 10.0 \
    --shadow-grain 1.4 --midtone-grain 1.0 --highlight-grain 0.3 \
    --depth 0.5 --chromatic 2.0 --relief 0.4 --layers 4

# ============================================================================
# EXTREME TESTS
# ============================================================================

log_section "EXTREME COMBINATIONS"

generate_example "extreme_max_grain" "Maximum grain" \
    --size 8.0 --intensity 2.0 --sharpness 20.0 \
    --shadow-grain 2.0 --midtone-grain 2.0 --highlight-grain 2.0 \
    --depth 1.0 --chromatic 6.0 --relief 0.8 --layers 5

generate_example "extreme_minimal" "Barely visible" \
    --size 1.0 --intensity 0.15 --sharpness 3.0 \
    --shadow-grain 0.3 --midtone-grain 0.2 --highlight-grain 0.1 \
    --depth 0.05 --chromatic 0.3 --relief 0.0 --layers 1

generate_example "extreme_3d_showcase" "3D effect showcase" \
    --size 3.0 --intensity 0.9 --sharpness 8.0 \
    --shadow-grain 1.0 --midtone-grain 1.0 --highlight-grain 1.0 \
    --depth 0.9 --chromatic 5.0 --relief 0.7 --layers 5

# ============================================================================
# Summary
# ============================================================================

log_section "GENERATION COMPLETE"

total_full=$(find "$OUTPUT_DIR" -maxdepth 1 -name "*.png" ! -name "*_crop.png" | wc -l)
total_crops=$(find "$OUTPUT_DIR" -maxdepth 1 -name "*_crop.png" | wc -l)

echo -e "\n${GREEN}Generated:${NC}"
echo -e "  ${total_full} full images"
echo -e "  ${total_crops} crop images"
echo -e "Output directory: ${YELLOW}${OUTPUT_DIR}${NC}\n"

log_section "README MARKDOWN"

cat << 'MARKDOWN_HEADER'
## Examples

### 100% Crops (Pixel-Level Detail)

Below are 100% crops showing the grain structure at actual pixel resolution.

| Original | Grain Applied |
|----------|---------------|
MARKDOWN_HEADER

for f in "$OUTPUT_DIR"/*_crop.png; do
    if [ -f "$f" ]; then
        fname=$(basename "$f")
        base_name="${fname%_crop.png}"
        desc="${base_name//_/ }"
        
        full_img="${base_name}.png"
        
        echo "| ![](original_crop.png) | ![](${fname}) |"
        echo "| *Original* | *${desc}* |"
        break  
    fi
done

echo ""
echo "### All Examples"
echo ""
echo "| Example | Full Image | 100% Crop |"
echo "|---------|------------|-----------|"

for f in "$OUTPUT_DIR"/*.png; do
    if [ -f "$f" ] && [[ ! "$f" == *_crop.png ]]; then
        fname=$(basename "$f")
        base_name="${fname%.png}"
        crop_name="${base_name}_crop.png"
        desc="${base_name//_/ }"
        
        if [ -f "${OUTPUT_DIR}/${crop_name}" ]; then
            echo "| ${desc} | [${fname}](${fname}) | [${crop_name}](${crop_name}) |"
        else
            echo "| ${desc} | [${fname}](${fname}) | - |"
        fi
    fi
done

echo -e "\n${GREEN}Done!${NC}"
