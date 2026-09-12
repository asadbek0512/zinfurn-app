import sharp from 'sharp';

const SRC = '../zinfurn-next/public/img/logo/001.3.png';
const BG = '#ffffff';

// Logo ships with heavy transparent padding — trim it, then re-pad to a known ratio.
async function logo(size, coverage) {
	const inner = Math.round(size * coverage);
	const buf = await sharp(SRC)
		.trim({ threshold: 10 })
		.resize(inner, inner, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
		.png()
		.toBuffer();
	return sharp({ create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
		.composite([{ input: buf, gravity: 'centre' }])
		.png()
		.toBuffer();
}

async function onWhite(size, coverage, out) {
	await sharp({ create: { width: size, height: size, channels: 4, background: BG } })
		.composite([{ input: await logo(size, coverage), gravity: 'centre' }])
		.png()
		.toFile(out);
}

// Adaptive icon foreground: logo must stay inside the 66% safe zone.
await sharp(await logo(1024, 0.5)).toFile('assets/icon-foreground.png');
await sharp({ create: { width: 1024, height: 1024, channels: 4, background: BG } })
	.png()
	.toFile('assets/icon-background.png');

// Legacy launcher + Play Store icon.
await onWhite(1024, 0.68, 'assets/icon.png');

// Splash is CENTER_CROP, so keep the logo small.
await onWhite(2732, 0.22, 'assets/splash.png');
await onWhite(2732, 0.22, 'assets/splash-dark.png');
