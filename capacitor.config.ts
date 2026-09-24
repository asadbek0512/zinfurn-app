import type { CapacitorConfig } from '@capacitor/cli';

const SITE_URL = 'https://zinfurn.uz';
const APP_UA_TAG = 'ZinfurnApp/1.0';

const config: CapacitorConfig = {
	appId: 'uz.zinfurn.app',
	appName: 'Zinfurn',
	webDir: 'www',
	server: {
		url: SITE_URL,
		cleartext: false,
		androidScheme: 'https',
		allowNavigation: ['zinfurn.uz', '*.zinfurn.uz', 'accounts.google.com', '*.google.com', 'oauth.telegram.org', '*.telegram.org'],
	},
	android: {
		allowMixedContent: false,
		webContentsDebuggingEnabled: false,
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
