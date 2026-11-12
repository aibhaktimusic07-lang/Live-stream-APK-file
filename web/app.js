// 24/7 Live Studio - PWA Installation & Studio Logic

// --- Global State ---
let deferredPrompt = null;
let isLive = false;
let streamInterval = null;
let streamElapsedSeconds = 0;
let currentLanguage = localStorage.getItem('live_studio_lang') || 'hi';

// --- DOM Elements ---
const btnInstallHeader = document.getElementById('btn-install-header');
const btnInstallHeaderSubtext = document.getElementById('btn-install-header-text');
const pwaInstallBanner = document.getElementById('pwa-install-banner');
const btnInstallBanner = document.getElementById('btn-install-banner');
const installModal = document.getElementById('install-modal');
const btnCloseModal = document.getElementById('btn-close-modal');
const btnModalGotIt = document.getElementById('btn-modal-got-it');
const offlineBanner = document.getElementById('offline-banner');

const btnStartStream = document.getElementById('btn-start-stream');
const btnStopStream = document.getElementById('btn-stop-stream');
const streamTimer = document.getElementById('stream-timer');
const liveBadge = document.getElementById('live-badge');
const liveBadgeText = document.getElementById('live-badge-text');
const valSpeed = document.getElementById('val-speed');
const valNetwork = document.getElementById('val-network');
const trackProgress = document.getElementById('track-progress');
const btnLangToggle = document.getElementById('btn-lang-toggle');

const inputYtKey = document.getElementById('input-yt-key');
const inputFbKey = document.getElementById('input-fb-key');
const btnShowYt = document.getElementById('btn-show-yt');
const btnShowFb = document.getElementById('btn-show-fb');
const btnSaveKeys = document.getElementById('btn-save-keys');

// --- 1. Service Worker Registration ---
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then((registration) => {
        console.log('[PWA] Service Worker registered successfully:', registration.scope);
      })
      .catch((error) => {
        console.warn('[PWA] Service Worker registration failed:', error);
      });
  });
}

// --- 2. Check If App Is Already Installed (Standalone Mode) ---
function isAppInstalled() {
  return window.matchMedia('(display-mode: standalone)').matches ||
         window.navigator.standalone === true ||
         document.referrer.includes('android-app://');
}

function updateInstallUiState() {
  if (isAppInstalled()) {
    console.log('[PWA] Running in standalone mode. Hiding install affordances.');
    if (btnInstallHeader) btnInstallHeader.style.display = 'none';
    if (pwaInstallBanner) pwaInstallBanner.style.display = 'none';
  } else {
    // In browser mode: show install button
    if (btnInstallHeader) btnInstallHeader.style.display = 'inline-flex';
    if (pwaInstallBanner) pwaInstallBanner.style.display = 'flex';
  }
}

// Initial check on load
updateInstallUiState();

// --- 3. PWA Installation Event Handlers ---
window.addEventListener('beforeinstallprompt', (e) => {
  // Prevent mini-infobar or default browser banner
  e.preventDefault();
  // Stash the event so it can be triggered later.
  deferredPrompt = e;
  console.log('[PWA] beforeinstallprompt event captured');

  if (!isAppInstalled()) {
    if (btnInstallHeader) btnInstallHeader.style.display = 'inline-flex';
    if (pwaInstallBanner) pwaInstallBanner.style.display = 'flex';
  }
});

// User clicked Install Button (Header or Banner)
function handleInstallClick() {
  if (deferredPrompt) {
    // Show the native browser install prompt
    deferredPrompt.prompt();
    deferredPrompt.userChoice.then((choiceResult) => {
      console.log('[PWA] User response to install prompt:', choiceResult.outcome);
      if (choiceResult.outcome === 'accepted') {
        if (btnInstallHeader) btnInstallHeader.style.display = 'none';
        if (pwaInstallBanner) pwaInstallBanner.style.display = 'none';
      }
      deferredPrompt = null;
    });
  } else {
    // If browser does not support prompt or prompt is not yet ready, show manual instructions modal!
    openInstallModal();
  }
}

if (btnInstallHeader) {
  btnInstallHeader.addEventListener('click', handleInstallClick);
}

if (btnInstallBanner) {
  btnInstallBanner.addEventListener('click', handleInstallClick);
}

// App was successfully installed
window.addEventListener('appinstalled', (evt) => {
  console.log('[PWA] 24/7 Live Studio was successfully installed!');
  deferredPrompt = null;
  if (btnInstallHeader) btnInstallHeader.style.display = 'none';
  if (pwaInstallBanner) pwaInstallBanner.style.display = 'none';
  alert(currentLanguage === 'hi' ? '24/7 Live Studio आपके फोन पर सफलतापूर्वक इंस्टॉल हो गया!' : '24/7 Live Studio installed successfully!');
});

// Modal open/close
function openInstallModal() {
  if (installModal) installModal.classList.add('open');
}

function closeInstallModal() {
  if (installModal) installModal.classList.remove('open');
}

if (btnCloseModal) btnCloseModal.addEventListener('click', closeInstallModal);
if (btnModalGotIt) btnModalGotIt.addEventListener('click', closeInstallModal);
if (installModal) {
  installModal.addEventListener('click', (e) => {
    if (e.target === installModal) closeInstallModal();
  });
}

// --- 4. Offline / Online Event Listeners ---
function updateNetworkStatus() {
  if (!navigator.onLine) {
    if (offlineBanner) offlineBanner.style.display = 'block';
    if (valNetwork) {
      valNetwork.textContent = currentLanguage === 'hi' ? 'ऑफलाइन (Offline)' : 'Offline';
      valNetwork.style.color = 'var(--live-red)';
    }
  } else {
    if (offlineBanner) offlineBanner.style.display = 'none';
    if (valNetwork) {
      valNetwork.textContent = currentLanguage === 'hi' ? 'ऑनलाइन (Online)' : 'Online (Connected)';
      valNetwork.style.color = 'var(--success-green)';
    }
  }
}

window.addEventListener('online', updateNetworkStatus);
window.addEventListener('offline', updateNetworkStatus);
updateNetworkStatus();

// --- 5. Studio Broadcasting Controls ---
function formatTime(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

btnStartStream.addEventListener('click', () => {
  isLive = true;
  streamElapsedSeconds = 0;
  liveBadge.className = 'live-badge live';
  liveBadgeText.textContent = 'LIVE 24/7';
  btnStartStream.style.display = 'none';
  btnStopStream.style.display = 'block';

  let progress = 0;
  streamInterval = setInterval(() => {
    streamElapsedSeconds++;
    streamTimer.textContent = formatTime(streamElapsedSeconds);

    // Fluctuate bitrate slightly for realistic telemetry
    const speed = 2400 + Math.floor(Math.random() * 250);
    valSpeed.textContent = `${speed} kbps`;

    progress = (progress + 2) % 100;
    trackProgress.style.width = `${progress}%`;
  }, 1000);
});

btnStopStream.addEventListener('click', () => {
  isLive = false;
  clearInterval(streamInterval);
  liveBadge.className = 'live-badge standby';
  liveBadgeText.textContent = 'STANDBY';
  btnStartStream.style.display = 'block';
  btnStopStream.style.display = 'none';
  streamTimer.textContent = '00:00:00';
  valSpeed.textContent = '0 kbps';
});

// Credentials toggle and save
btnShowYt.addEventListener('click', () => {
  inputYtKey.type = inputYtKey.type === 'password' ? 'text' : 'password';
});

btnShowFb.addEventListener('click', () => {
  inputFbKey.type = inputFbKey.type === 'password' ? 'text' : 'password';
});

btnSaveKeys.addEventListener('click', () => {
  localStorage.setItem('live_studio_yt_key', inputYtKey.value);
  localStorage.setItem('live_studio_fb_key', inputFbKey.value);
  alert(currentLanguage === 'hi' ? 'क्रेडेंशियल्स सुरक्षित रूप से सहेज लिए गए हैं!' : 'Credentials saved securely!');
});

// Load saved keys
if (localStorage.getItem('live_studio_yt_key')) {
  inputYtKey.value = localStorage.getItem('live_studio_yt_key');
}
if (localStorage.getItem('live_studio_fb_key')) {
  inputFbKey.value = localStorage.getItem('live_studio_fb_key');
}

// --- 6. Bilingual Translations (Hindi & English) ---
const translations = {
  hi: {
    langButton: 'EN',
    subHeader: 'YouTube & Facebook Multistreaming',
    installBtn: 'ऐप इंस्टॉल करें',
    bannerTitle: 'होम स्क्रीन पर इंस्टॉल करें',
    bannerDesc: 'बिना ब्राउज़र के फुल स्क्रीन में 24/7 लाइव स्टूडियो चलाएं।',
    bannerBtn: 'इंस्टॉल करें',
    nowPlaying: 'वर्तमान वीडियो (NOW PLAYING)',
    speed: 'अपलोड स्पीड',
    network: 'इंटरनेट स्थिति',
    startLive: 'लाइव प्रसारण शुरू करें',
    stopLive: 'प्रसारण रोकें',
    configTitle: 'स्ट्रीम क्रेडेंशियल्स (Stream Credentials)',
    saveBtn: '💾 क्रेडेंशियल्स सहेजें',
    apkTitle: 'Android Native APK डाउनलोड',
    apkDesc: 'फोन में 24/7 बैकग्राउंड सर्विस व वेक-लॉक के साथ सीधा इंस्टॉल करें।',
    modalTitle: 'ऐप को Android में कैसे इंस्टॉल करें?',
    modalDesc: 'यदि ऑटोमैटिक प्रॉम्प्ट नहीं दिख रहा है, तो इन आसान चरणों का पालन करें:',
    step1: 'ब्राउज़र के ऊपर दाईं ओर <strong>तीन बिंदुओं (⋮) मेन्यू</strong> पर टैप करें।',
    step2: 'मेन्यू में से <strong>"Install app"</strong> या <strong>"Add to Home screen" (होम स्क्रीन पर जोड़ें)</strong> चुनें।',
    step3: '<strong>"Install"</strong> पर क्लिक करें। ऐप आपके फोन की होम स्क्रीन पर बिना ब्राउज़र बार के फुल स्क्रीन में आ जाएगा।',
    modalGotIt: 'समझ गया (Got it)'
  },
  en: {
    langButton: 'हिं',
    subHeader: 'YouTube & Facebook Multistreaming',
    installBtn: 'Install App',
    bannerTitle: 'Install on Home Screen',
    bannerDesc: 'Run 24/7 Live Studio full-screen without browser bars.',
    bannerBtn: 'Install Now',
    nowPlaying: 'CURRENT VIDEO',
    speed: 'UPLOAD SPEED',
    network: 'NETWORK STATUS',
    startLive: 'START LIVE BROADCAST',
    stopLive: 'STOP BROADCAST',
    configTitle: 'Stream Credentials',
    saveBtn: '💾 Save Credentials',
    apkTitle: 'Android Native APK Download',
    apkDesc: 'Direct install on Android phone with 24/7 background foreground service & wakelock.',
    modalTitle: 'How to Install on Android?',
    modalDesc: 'If the automatic prompt does not appear, follow these steps:',
    step1: 'Tap the <strong>three dots (⋮) menu</strong> in top right of browser.',
    step2: 'Select <strong>"Install app"</strong> or <strong>"Add to Home screen"</strong>.',
    step3: 'Tap <strong>"Install"</strong>. The app will open standalone without browser address bar.',
    modalGotIt: 'Got It'
  }
};

function applyLanguage(lang) {
  currentLanguage = lang;
  localStorage.setItem('live_studio_lang', lang);
  const t = translations[lang];

  btnLangToggle.textContent = t.langButton;
  document.getElementById('sub-header-text').textContent = t.subHeader;
  btnInstallHeaderSubtext.textContent = t.installBtn;
  document.getElementById('banner-title').textContent = t.bannerTitle;
  document.getElementById('banner-desc').textContent = t.bannerDesc;
  document.getElementById('btn-install-banner').textContent = t.bannerBtn;
  document.getElementById('label-now-playing').textContent = t.nowPlaying;
  document.getElementById('label-speed').textContent = t.speed;
  document.getElementById('label-network').textContent = t.network;
  document.getElementById('btn-start-text').textContent = t.startLive;
  document.getElementById('btn-stop-text').textContent = t.stopLive;
  document.getElementById('title-config').textContent = t.configTitle;
  document.getElementById('btn-save-keys').textContent = t.saveBtn;
  document.getElementById('apk-card-title').textContent = t.apkTitle;
  document.getElementById('apk-card-desc').textContent = t.apkDesc;
  document.getElementById('modal-title').textContent = t.modalTitle;
  document.getElementById('modal-desc').textContent = t.modalDesc;
  document.getElementById('step-1').innerHTML = t.step1;
  document.getElementById('step-2').innerHTML = t.step2;
  document.getElementById('step-3').innerHTML = t.step3;
  document.getElementById('btn-modal-got-it').textContent = t.modalGotIt;
}

btnLangToggle.addEventListener('click', () => {
  const next = currentLanguage === 'hi' ? 'en' : 'hi';
  applyLanguage(next);
});

// Initialize with saved language
applyLanguage(currentLanguage);
