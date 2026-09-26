import type { CapacitorConfig } from '@capacitor/cli';

// Dev: CAP_DEV_URL=http://localhost:3006 bilan telefonda local next dev (adb reverse)
const SITE_URL = process.env.CAP_DEV_URL || 'https://zinfurn.uz';
const IS_DEV_URL = SITE_URL.startsWith('http://');
const APP_UA_TAG = 'ZinfurnApp/1.0';

const config: CapacitorConfig = {
	appId: 'uz.zinfurn.app',
	appName: 'Zinfurn',
	webDir: 'www',
	server: {
		url: SITE_URL,
		cleartext: IS_DEV_URL,
		androidScheme: 'https',
		// Internet yo'q bo'lsa WebView xatosi o'rniga www/error.html ko'rsatiladi
		errorPath: 'error.html',
		allowNavigation: [...(IS_DEV_URL ? ['localhost'] : []), 'zinfurn.uz', '*.zinfurn.uz', 'accounts.google.com', '*.google.com', 'oauth.telegram.org', '*.telegram.org'],
	},
	android: {
		allowMixedContent: IS_DEV_URL,
		webContentsDebuggingEnabled: IS_DEV_URL || process.env.CAP_WEBVIEW_DEBUG === '1',
		// Sayt app ichida ochilganini shu belgi bilan biladi (pastdagi navbar, yon menyu yo'q)
		appendUserAgent: APP_UA_TAG,
	},
	ios: {
		appendUserAgent: APP_UA_TAG,
	},
	plugins: {
		SplashScreen: {
			launchShowDuration: 1500,
			launchAutoHide: true,
			backgroundColor: '#ffffff',
			androidSplashResourceName: 'splash',
			androidScaleType: 'CENTER_CROP',
			showSpinner: false,
		},
		StatusBar: {
			style: 'LIGHT',
			backgroundColor: '#ffffff',
		},
	},
};

export default config;
