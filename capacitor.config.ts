import type { CapacitorConfig } from '@capacitor/cli';

const SITE_URL = 'https://zinfurn.uz';

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
