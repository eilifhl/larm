extern crate clap;
extern crate image;
extern crate noise;
extern crate rayon;

use clap::Parser;
use image::{Rgb, RgbImage};
use noise::{NoiseFn, Worley};
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
}

struct ArtisticGrainConfig {
    size: f64,
    intensity: f64,
    crystal_sharpness: f64,
    mix_shadows: f64,
    mix_highlights: f64,
}

fn get_luma(p: &Rgb<u8>) -> f64 {
    (0.2126 * p[0] as f64 + 0.7152 * p[1] as f64 + 0.0722 * p[2] as f64) / 255.0
}

fn apply_massive_grain(img: &RgbImage, config: &ArtisticGrainConfig) -> RgbImage {
    let width = img.width();
    let height = img.height();

    let blur_radius = (config.size * 0.5) as f32;
    let base_image = if blur_radius > 0.5 {
        image::imageops::blur(img, blur_radius)
    } else {
        img.clone()
    };

    let mut buffer = RgbImage::new(width, height);

    buffer.enumerate_pixels_mut().par_bridge().for_each_init(
        || Worley::new(42),
        |noise_gen, (x, y, pixel)| {
            let original_pixel = base_image.get_pixel(x, y);
            let luma = get_luma(original_pixel);

            let scale_factor = 1.0 / config.size.max(0.1);
            let raw_noise = noise_gen.get([x as f64 * scale_factor, y as f64 * scale_factor, 0.0]);

            let n_norm = (raw_noise + 1.0) / 2.0;
            let grain_shape = n_norm.powf(config.crystal_sharpness);

            let mask = if luma > 0.5 {
                (1.0 - luma) * 2.0 * config.mix_highlights
            } else {
                1.0 * config.mix_shadows
            };

            let apply_channel = |c_in: u8| -> u8 {
                let c_float = c_in as f64 / 255.0;
                let grain_delta = (grain_shape - 0.5) * 2.0;
                let result = c_float + (grain_delta * config.intensity * mask);
                (result.clamp(0.0, 1.0) * 255.0) as u8
            };

            *pixel = Rgb([
                apply_channel(original_pixel[0]),
                apply_channel(original_pixel[1]),
                apply_channel(original_pixel[2]),
            ]);
        },
    );

    buffer
}

fn main() {
    let args = Args::parse();

    println!("Processing: {}", args.input);

    let img = image::open(&args.input)
        .expect("Failed to open input image")
        .to_rgb8();

    let config = ArtisticGrainConfig {
        size: args.size,
        intensity: args.intensity,
        crystal_sharpness: args.sharpness,
        mix_shadows: 1.0,
        mix_highlights: 0.5,
    };

    let result = apply_massive_grain(&img, &config);

    result
        .save(&args.output)
        .expect("Failed to save output image");
    println!("Saved to: {}", args.output);
}
