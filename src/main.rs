extern crate clap;
extern crate image;
extern crate indicatif;
extern crate noise;
extern crate rayon;

use clap::Parser;
use image::{Rgb, RgbImage};
use indicatif::{ProgressBar, ProgressStyle};
use noise::{Fbm, NoiseFn, Perlin, SuperSimplex};
use rayon::prelude::*;

#[derive(Parser, Debug)]
#[command(
    author,
    version,
    about = "A 3D film grain simulation tool based on Dehancer logic."
)]
struct Args {
    input: String,

    #[arg(short, long, default_value = "output.png")]
    output: String,

    #[arg(short, long, default_value_t = 25.0)]
    size: f64,

    #[arg(short = 'i', long, default_value_t = 0.8)]
    intensity: f64,

    #[arg(long, default_value_t = 8.0)]
    sharpness: f64,

    #[arg(long, default_value_t = 1.0)]
    saturation: f64,

    #[arg(short, long, default_value_t = 0.0)]
    exposure: f64,

    #[arg(long, default_value_t = 1.2)]
    shadow_grain: f64,

    #[arg(long, default_value_t = 1.0)]
    midtone_grain: f64,

    #[arg(long, default_value_t = 0.6)]
    highlight_grain: f64,

    #[arg(long, default_value_t = 0.15)]
    tonal_smoothness: f64,

    #[arg(long, default_value_t = 0.4)]
    depth: f64,

    #[arg(long, default_value_t = 2.0)]
    chromatic: f64,

    #[arg(long, default_value_t = 0.3)]
    relief: f64,

    #[arg(long, default_value_t = 3)]
    layers: u32,
}

struct ArtisticGrainConfig {
    size: f64,
    intensity: f64,
    crystal_sharpness: f64,
    saturation: f64,
    exposure: f64,
    shadow_grain: f64,
    midtone_grain: f64,
    highlight_grain: f64,
    tonal_smoothness: f64,
    depth: f64,
    chromatic: f64,
    relief: f64,
    layers: u32,
}

fn get_luma(p: &Rgb<u8>) -> f64 {
    (0.2126 * p[0] as f64 + 0.7152 * p[1] as f64 + 0.0722 * p[2] as f64) / 255.0
}

fn get_tonal_weights(luma: f64, smoothness: f64) -> (f64, f64, f64) {
    let shadow_center = 0.15;
    let midtone_center = 0.5;
    let highlight_center = 0.85;

    let variance = smoothness * smoothness;

    let shadow_weight = (-(luma - shadow_center).powi(2) / (2.0 * variance)).exp();
    let midtone_weight = (-(luma - midtone_center).powi(2) / (2.0 * variance)).exp();
    let highlight_weight = (-(luma - highlight_center).powi(2) / (2.0 * variance)).exp();

    let total = shadow_weight + midtone_weight + highlight_weight;

    if total > 0.0 {
        (
            shadow_weight / total,
            midtone_weight / total,
            highlight_weight / total,
        )
    } else {
        (0.0, 1.0, 0.0)
    }
}

fn get_tonal_grain_intensity(luma: f64, config: &ArtisticGrainConfig) -> f64 {
    let (shadow_w, midtone_w, highlight_w) = get_tonal_weights(luma, config.tonal_smoothness);

    shadow_w * config.shadow_grain
        + midtone_w * config.midtone_grain
        + highlight_w * config.highlight_grain
}

struct Grain3D {
    layer_noises: Vec<Fbm<SuperSimplex>>,

    chromatic_noise: Perlin,

    relief_noise: Fbm<Perlin>,
}

impl Grain3D {
    fn new(layers: u32, seed: u32) -> Self {
        let mut layer_noises = Vec::new();
        for i in 0..layers {
            let mut fbm = Fbm::<SuperSimplex>::new(seed + i);
            fbm.octaves = 3;
            fbm.frequency = 1.0;
            fbm.persistence = 0.5;
            layer_noises.push(fbm);
        }

        let chromatic_noise = Perlin::new(seed + 100);

        let mut relief_noise = Fbm::<Perlin>::new(seed + 200);
        relief_noise.octaves = 2;
        relief_noise.frequency = 0.5;

        Grain3D {
            layer_noises,
            chromatic_noise,
            relief_noise,
        }
    }

    fn sample(&self, x: f64, y: f64, luma: f64, config: &ArtisticGrainConfig) -> [f64; 3] {
        let mut channel_grains = [0.0; 3];

        let relief_offset = self.relief_noise.get([x * 0.3, y * 0.3]) * config.relief;
        let surface_x = x + relief_offset * luma * 5.0;
        let surface_y = y + relief_offset * luma * 5.0;

        let chromatic_offsets: [(f64, f64); 3] = [
            (
                self.chromatic_noise.get([surface_x * 0.1, surface_y * 0.1]) * config.chromatic,
                self.chromatic_noise
                    .get([surface_x * 0.1 + 100.0, surface_y * 0.1])
                    * config.chromatic,
            ),
            (
                self.chromatic_noise
                    .get([surface_x * 0.1 + 50.0, surface_y * 0.1])
                    * config.chromatic
                    * 0.3,
                self.chromatic_noise
                    .get([surface_x * 0.1 + 150.0, surface_y * 0.1])
                    * config.chromatic
                    * 0.3,
            ),
            (
                self.chromatic_noise
                    .get([surface_x * 0.1 + 25.0, surface_y * 0.1])
                    * config.chromatic
                    * 0.7,
                self.chromatic_noise
                    .get([surface_x * 0.1 + 175.0, surface_y * 0.1])
                    * config.chromatic
                    * 0.7,
            ),
        ];

        let rot_x = (surface_x * 0.866) - (surface_y * 0.5);
        let rot_y = (surface_x * 0.5) + (surface_y * 0.866);

        let num_layers = self.layer_noises.len().max(1);
        for (layer_idx, fbm) in self.layer_noises.iter().enumerate() {
            let layer_depth_factor = (layer_idx as f64 + 1.0) / (num_layers as f64);
            let depth_scale = 1.0 + (1.0 - layer_depth_factor) * config.depth * 2.0;

            let layer_intensity = 0.5 + 0.5 * layer_depth_factor;

            let layer_sharpness = config.crystal_sharpness * (0.7 + 0.3 * layer_depth_factor);

            for channel in 0..3 {
                let (offset_x, offset_y) = chromatic_offsets[channel];

                let sample_x = (rot_x + offset_x) * depth_scale / (config.size * 2.5);
                let sample_y = (rot_y + offset_y) * depth_scale / (config.size * 2.5);

                let clump_noise = fbm.get([sample_x, sample_y]);

                let grit_noise = fbm.get([sample_x * 3.0, sample_y * 3.0]);

                let combined = clump_noise * 0.7 + grit_noise * 0.3;

                let centered = combined * layer_sharpness;
                let grain_raw = 1.0 / (1.0 + (-centered).exp());

                channel_grains[channel] += (grain_raw - 0.5) * layer_intensity;
            }
        }

        let norm_factor = 1.0 / (num_layers as f64);
        for grain in &mut channel_grains {
            *grain *= norm_factor;
        }

        channel_grains
    }
}

fn apply_3d_grain(img: &RgbImage, config: &ArtisticGrainConfig) -> RgbImage {
    let (width, height) = img.dimensions();

    let pb = ProgressBar::new(height as u64);
    pb.set_style(ProgressStyle::default_bar()
        .template("{spinner:.green} [{elapsed_precise}] [{bar:40.cyan/blue}] {pos}/{len} rows ({eta})")
        .unwrap());

    let blur_radius = (config.size * 0.3) as f32;
    let base_image = if blur_radius > 0.5 {
        image::imageops::blur(img, blur_radius)
    } else {
        img.clone()
    };

    let mut buffer = RgbImage::new(width, height);

    buffer.enumerate_pixels_mut().par_bridge().for_each_init(
        || Grain3D::new(config.layers, 42),
        |grain_3d, (x, y, pixel)| {
            let original_pixel = base_image.get_pixel(x, y);
            let luma = get_luma(original_pixel);

            let x_f = x as f64;
            let y_f = y as f64;

            let channel_grains = grain_3d.sample(x_f, y_f, luma, config);

            let tonal_intensity = get_tonal_grain_intensity(luma, config);
            let effective_intensity = config.intensity * tonal_intensity;

            let soft_light = |c: f64, g: f64| -> f64 {
                if g < 0.5 {
                    c - (1.0 - 2.0 * g) * c * (1.0 - c)
                } else {
                    c + (2.0 * g - 1.0)
                        * ((if c <= 0.25 {
                            ((16.0 * c - 12.0) * c + 4.0) * c
                        } else {
                            c.sqrt()
                        }) - c)
                }
            };

            let exposure_mult = 2.0_f64.powf(config.exposure);
            let mut rgb_f = [0.0; 3];

            for i in 0..3 {
                let c_orig = original_pixel[i] as f64 / 255.0;

                let grain_shape = 0.5 + channel_grains[i] * effective_intensity;
                let grained = soft_light(c_orig, grain_shape);

                rgb_f[i] = (grained * exposure_mult).clamp(0.0, 1.0);
            }

            let out_luma = 0.2126 * rgb_f[0] + 0.7152 * rgb_f[1] + 0.0722 * rgb_f[2];
            let r = (out_luma + config.saturation * (rgb_f[0] - out_luma)).clamp(0.0, 1.0);
            let g = (out_luma + config.saturation * (rgb_f[1] - out_luma)).clamp(0.0, 1.0);
            let b = (out_luma + config.saturation * (rgb_f[2] - out_luma)).clamp(0.0, 1.0);

            *pixel = Rgb([(r * 255.0) as u8, (g * 255.0) as u8, (b * 255.0) as u8]);

            if x == width - 1 {
                pb.inc(1);
            }
        },
    );

    pb.finish_with_message("3D Grain complete.");
    buffer
}

fn main() {
    let args = Args::parse();

    println!("Opening: {}", args.input);

    let img = image::open(&args.input)
        .expect("Failed to open input image")
        .to_rgb8();

    println!("Tonal grain settings:");
    println!("  Shadows:    {:.2}", args.shadow_grain);
    println!("  Midtones:   {:.2}", args.midtone_grain);
    println!("  Highlights: {:.2}", args.highlight_grain);
    println!("3D grain settings:");
    println!("  Depth:      {:.2}", args.depth);
    println!("  Chromatic:  {:.2}", args.chromatic);
    println!("  Relief:     {:.2}", args.relief);
    println!("  Layers:     {}", args.layers);

    let config = ArtisticGrainConfig {
        size: args.size,
        intensity: args.intensity,
        crystal_sharpness: args.sharpness,
        saturation: args.saturation,
        exposure: args.exposure,
        shadow_grain: args.shadow_grain,
        midtone_grain: args.midtone_grain,
        highlight_grain: args.highlight_grain,
        tonal_smoothness: args.tonal_smoothness,
        depth: args.depth,
        chromatic: args.chromatic,
        relief: args.relief,
        layers: args.layers,
    };

    let result = apply_3d_grain(&img, &config);

    println!("Saving to: {}", args.output);
    result
        .save(&args.output)
        .expect("Failed to save output image");
}
