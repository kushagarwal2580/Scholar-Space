const fs = require('fs');
const content = fs.readFileSync('docs/index.html', 'utf8');

const startTag = '<style id="premium-scholar-space">';
const endTag = '</style>';

let startIndex = content.indexOf(startTag);
// Find the first </style> *after* the start tag
let endIndex = content.indexOf(endTag, startIndex);

if (startIndex === -1 || endIndex === -1) {
  console.error("Could not find style block boundaries.");
  process.exit(1);
}

// Ensure we include the end tag itself
endIndex += endTag.length;

const newStyle = `<style id="premium-scholar-space">
/* ─────────────────────────────────────────────────────────────
   Premium Product UI — Sophisticated Minimal Dark Theme
   ───────────────────────────────────────────────────────────── */
:root {
  --bg-base: #050505;
  --bg-surface: #0a0a0a;
  --bg-surface-elevated: #111111;
  --border-subtle: rgba(255, 255, 255, 0.05);
  --border-strong: rgba(255, 255, 255, 0.1);
  --border-highlight: rgba(255, 255, 255, 0.2);
  --text-primary: #f4f4f5;
  --text-secondary: #85858a;
  --text-muted: #555555;
  --accent: #ffffff;
  --accent-fg: #000000;
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;
  --shadow-subtle: 0 8px 30px rgba(0, 0, 0, 0.4);
}

/* Base Document */
html, body {
  background: var(--bg-base) !important;
  color: var(--text-primary) !important;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
  letter-spacing: -0.015em;
  -webkit-font-smoothing: antialiased;
}

/* Faint background grid & radial light */
body::before {
  content: "";
  position: fixed;
  inset: 0;
  z-index: -2;
  pointer-events: none;
  background-image: 
    linear-gradient(rgba(255,255,255,0.015) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.015) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at center, black 30%, transparent 80%);
}
body::after {
  content: "";
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: radial-gradient(circle at 50% 0%, rgba(255,255,255,0.03) 0%, transparent 50%);
}

/* Hide colorful blobs */
.blob { display: none !important; }

/* Typography Overrides */
h1, h2, h3, h4, h5, h6, .font-bold, .font-semibold {
  color: var(--text-primary) !important;
  font-weight: 500 !important;
  letter-spacing: -0.03em !important;
}
.text-gray-400, .text-gray-500, .text-indigo-200, .text-indigo-300 {
  color: var(--text-secondary) !important;
  font-weight: 400 !important;
}
.text-indigo-400, .text-purple-400, .text-emerald-400, .text-white {
  color: var(--text-primary) !important;
}
.text-red-400 {
  color: #ff6b6b !important;
}

/* Layout Structural Elements */
header {
  background: var(--bg-base) !important;
  border-bottom: 1px solid var(--border-subtle) !important;
  backdrop-filter: none !important;
  box-shadow: none !important;
}
.lg\\:flex.w-64 { /* Sidebar */
  background: var(--bg-base) !important;
  border-right: 1px solid var(--border-subtle) !important;
  backdrop-filter: none !important;
}

/* Surfaces & Cards */
.bg-white\\/5, .bg-white, .bg-gray-50, .bg-gray-100, .bg-indigo-50, 
#note-editor, .bg-white\\/10 {
  background: var(--bg-surface) !important;
  border: 1px solid var(--border-subtle) !important;
  box-shadow: var(--shadow-subtle) !important;
  backdrop-filter: none !important;
  transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1) !important;
}
.bg-white\\/5:hover, .bg-white:hover {
  background: var(--bg-surface-elevated) !important;
  border-color: var(--border-strong) !important;
}

/* Radii overrides */
.rounded-2xl, .rounded-3xl, .rounded-xl, .rounded-lg {
  border-radius: var(--radius-lg) !important;
}
.rounded-full {
  border-radius: 9999px !important;
}

/* Primary Buttons */
.bg-indigo-600, .bg-indigo-500, button[onclick="connectGoogleDrive()"], a[href="download.html"] {
  background: var(--accent) !important;
  color: var(--accent-fg) !important;
  border: none !important;
  border-radius: var(--radius-md) !important;
  font-weight: 500 !important;
  letter-spacing: 0em !important;
  box-shadow: 0 4px 14px rgba(255,255,255,0.1) !important;
  transition: all 0.2s cubic-bezier(0.2, 0.8, 0.2, 1) !important;
  text-shadow: none !important;
}
.bg-indigo-600:hover, .bg-indigo-500:hover, button[onclick="connectGoogleDrive()"]:hover, a[href="download.html"]:hover {
  background: #e0e0e0 !important;
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(255,255,255,0.15) !important;
}
.bg-indigo-600 *, .bg-indigo-500 *, button[onclick="connectGoogleDrive()"] *, a[href="download.html"] * {
  color: var(--accent-fg) !important;
}
button[onclick="connectGoogleDrive()"] svg {
  filter: grayscale(100%) contrast(120%);
}

/* Secondary Buttons */
.bg-indigo-500\\/20, .bg-purple-500\\/20, .bg-emerald-500\\/20, .bg-red-500\\/10,
.bg-gray-100\\/50, .hover\\:bg-white\\/10, .border-white\\/10, button[onclick="createNewNote()"], button[onclick="openVoicePopup()"] {
  background: transparent !important;
  border: 1px solid var(--border-strong) !important;
  color: var(--text-primary) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: none !important;
  transition: all 0.2s ease !important;
}
button:hover, .cursor-pointer:hover {
  border-color: var(--border-highlight) !important;
  background: rgba(255,255,255,0.03) !important;
}

/* Navigation Items */
.nav-item {
  border-radius: var(--radius-md) !important;
  color: var(--text-secondary) !important;
  margin: 0 8px !important;
  transition: all 0.2s ease !important;
  border: 1px solid transparent !important;
}
.nav-item:hover {
  color: var(--text-primary) !important;
  background: rgba(255,255,255,0.02) !important;
}
.nav-item.active {
  background: rgba(255,255,255,0.04) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-subtle) !important;
}
.nav-item.active::before { display: none !important; }

/* Note Editor overrides */
#note-editor {
  background: var(--bg-base) !important;
}
#note-editor > div:first-child {
  background: var(--bg-surface) !important;
  border-bottom: 1px solid var(--border-subtle) !important;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
}
#note-title {
  font-size: 1.5rem !important;
  letter-spacing: -0.03em !important;
  font-weight: 500 !important;
}

/* Inputs & Textareas */
input, textarea {
  background: transparent !important;
  border: 1px solid transparent !important;
  color: var(--text-primary) !important;
  transition: all 0.2s ease !important;
}
input:not(#note-title):not(#note-body) {
  background: var(--bg-surface) !important;
  border: 1px solid var(--border-strong) !important;
  border-radius: var(--radius-md) !important;
  padding-left: 1rem !important;
  padding-right: 1rem !important;
}
input:focus, textarea:focus {
  border-color: var(--border-highlight) !important;
  outline: none !important;
  box-shadow: none !important;
}

/* Icons */
.material-icons-outlined {
  font-size: 18px !important;
  opacity: 0.9;
}

/* Login Screen */
#login-overlay {
  background: var(--bg-base) !important;
}
#login-overlay > div {
  background: var(--bg-surface) !important;
  border: 1px solid var(--border-strong) !important;
  box-shadow: 0 32px 100px rgba(0,0,0,0.8) !important;
  border-radius: var(--radius-lg) !important;
}
#login-overlay .w-16.h-16 { /* Logo box */
  background: var(--bg-base) !important;
  border: 1px solid var(--border-strong) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: none !important;
  color: var(--text-primary) !important;
}

/* Live Indicator */
.ss-live-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--text-primary);
  box-shadow: 0 0 0 0 rgba(255,255,255,0.2);
  animation: ssPulse 2s infinite;
}
@keyframes ssPulse {
  0% { box-shadow: 0 0 0 0 rgba(255,255,255,0.2); }
  70% { box-shadow: 0 0 0 6px rgba(255,255,255,0); }
  100% { box-shadow: 0 0 0 0 rgba(255,255,255,0); }
}

/* Staggered Animations */
.tab-content.active {
  animation: slideFadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
@keyframes slideFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Card Entrance */
.grid > div, #library-grid > div, #notes-list > div {
  animation: cardIn 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.grid > div:nth-child(1) { animation-delay: 0.05s; }
.grid > div:nth-child(2) { animation-delay: 0.1s; }
.grid > div:nth-child(3) { animation-delay: 0.15s; }
.grid > div:nth-child(4) { animation-delay: 0.2s; }
@keyframes cardIn {
  from { opacity: 0; transform: translateY(12px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* Scrollbar */
::-webkit-scrollbar { width: 4px; height: 4px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { 
  background: var(--border-strong); 
  border-radius: 4px; 
}
::-webkit-scrollbar-thumb:hover { background: var(--border-highlight); }

/* Accessibility */
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>`;

const newContent = content.substring(0, startIndex) + newStyle + content.substring(endIndex);
fs.writeFileSync('docs/index.html', newContent);
console.log("Style block replaced successfully.");
