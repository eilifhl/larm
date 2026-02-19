extern crate clap;
extern crate image;
extern crate indicatif;
extern crate noise;
extern crate rayon;

use clap::Parser;
use image::{Rgb, RgbImage};
use indicatif::{ProgressBar, ProgressStyle};
use noise::{Fbm, NoiseFn, SuperSimplex};
use rayon::prelude::*;

#[derive(Parser, Debug)]
#[command(
    author,
    version,
    about = "A film grain simulation tool based on Dehancer logic."
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
}

struct ArtisticGrainConfig {
    size: f64,
    intensity: f64,
    crystal_sharpness: f64,
    saturation: f64,
    exposure: f64,
}

fn get_luma(p: &Rgb<u8>) -> f64 {
    (0.2126 * p[0] as f64 + 0.7152 * p[1] as f64 + 0.0722 * p[2] as f64) / 255.0
}

fn apply_massive_grain(img: &RgbImage, config: &ArtisticGrainConfig) -> RgbImage {
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
        || {
            let mut fbm = Fbm::<SuperSimplex>::new(42);
            fbm.octaves = 3;
            fbm.frequency = 1.0;
            fbm.persistence = 0.5;
            fbm
        },
        |fbm, (x, y, pixel)| {
            let original_pixel = base_image.get_pixel(x, y);
            let luma = get_luma(original_pixel);

            let x_f = x as f64;
            let y_f = y as f64;

            let clump_scale = 1.0 / (config.size * 2.5);
            let rot_x = (x_f * 0.866) - (y_f * 0.5);
            let rot_y = (x_f * 0.5) + (y_f * 0.866);
            let clump_noise = fbm.get([rot_x * clump_scale, rot_y * clump_scale]);

            let grit_scale = 2.0;
            let grit_noise = fbm.get([rot_x * grit_scale, rot_y * grit_scale]);

            let combined_noise = (clump_noise * 0.7) + (grit_noise * 0.3);

            let centered = combined_noise * config.crystal_sharpness;
            let grain_raw = 1.0 / (1.0 + (-centered).exp());

            let luma_mask = (-(luma - 0.5).powi(2) / 0.20).exp();
            let grain_swing = (grain_raw - 0.5) * config.intensity * luma_mask;
            let grain_shape = 0.5 + grain_swing;

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

    pb.finish_with_message("Grain complete.");
    buffer
}

fn main() {
    let args = Args::parse();

    println!("Opening: {}", args.input);

    let img = image::open(&args.input)
        .expect("Failed to open input image")
        .to_rgb8();

    let config = ArtisticGrainConfig {
        size: args.size,
        intensity: args.intensity,
        crystal_sharpness: args.sharpness,
        saturation: args.saturation,
        exposure: args.exposure,
    };

    let result = apply_massive_grain(&img, &config);

    println!("Saving to: {}", args.output);
    result
        .save(&args.output)
        .expect("Failed to save output image");
}
