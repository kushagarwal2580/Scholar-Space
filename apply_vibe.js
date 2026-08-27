const fs = require('fs');

const vibeCss = `
<style id="vibe-coder-space">
/* ─────────────────────────────────────────────────────────────
   Energetic Vibe Coder UI — Stark, Fast, Flat
   ───────────────────────────────────────────────────────────── */
:root {
  --bg: #000000;
  --border: rgba(255, 255, 255, 0.15);
  --border-hover: rgba(255, 255, 255, 0.6);
  --text: #ffffff;
  --text-muted: #888888;
  --accent: #ffffff;
  --accent-bg: #ffffff;
  --accent-fg: #000000;
}

html, body {
  background: var(--bg) !important;
  color: var(--text) !important;
  font-family: 'Inter', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace !important; 
  -webkit-font-smoothing: antialiased;
}

/* Energetic Grid Background */
body::before {
  content: ''; position: fixed; inset: 0; z-index: -2;
  background: 
    linear-gradient(to right, rgba(255,255,255,0.04) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255,255,255,0.04) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(circle at 50% 30%, black 10%, transparent 80%);
}

/* No Blobs */
.blob { display: none !important; }

/* Global Sharpness & No Cards/Glass */
* { 
  backdrop-filter: none !important; 
  -webkit-backdrop-filter: none !important;
  box-shadow: none !important;
}

/* Flatten surfaces, remove card look */
.bg-white\\/5, .bg-white, .bg-gray-50, .bg-gray-100, .bg-indigo-50, 
#note-editor, .bg-white\\/10, .container, .privacy-container, .feedback-container,
.lg\\:flex.w-64, header, .feature-item {
  background: transparent !important;
  border: 1px solid var(--border) !important;
  border-radius: 0px !important;
}

/* Remove header/sidebar borders where they double up */
header {
  border-top: none !important; border-left: none !important; border-right: none !important;
  border-bottom: 1px solid var(--border) !important;
  position: relative;
}
.lg\\:flex.w-64 {
  border-top: none !important; border-bottom: none !important; border-left: none !important;
  border-right: 1px solid var(--border) !important;
}

/* Energetic Top Line */
header::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  background-size: 200% 100%;
  animation: pulseLine 2.5s linear infinite;
}
@keyframes pulseLine {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

/* Typography */
h1, h2, h3, h4, h5, h6, .font-bold, .font-semibold {
  color: var(--text) !important;
  font-weight: 600 !important;
  letter-spacing: -0.05em !important;
}
.text-gray-400, .text-gray-500, .text-indigo-200, .text-indigo-300 {
  color: var(--text-muted) !important;
}

/* Snappy Energetic Hover Interactions */
.bg-white\\/5:hover, .bg-white:hover, .feature-item:hover {
  border-color: var(--border-hover) !important;
  background: rgba(255,255,255,0.03) !important;
  transform: translateY(0) !important; /* Remove floaty card lifts */
}

/* Energetic Primary Buttons */
.bg-indigo-600, .bg-indigo-500, button[onclick="connectGoogleDrive()"], a[href="download.html"], .btn-download, .btn-primary, button[type="submit"] {
  background: var(--accent-bg) !important;
  color: var(--accent-fg) !important;
  border: none !important;
  border-radius: 0px !important;
  font-weight: 700 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.1em !important;
  font-size: 0.8rem !important;
  transition: all 0.1s cubic-bezier(0.175, 0.885, 0.32, 1.275) !important;
}
.bg-indigo-600 *, .bg-indigo-500 *, button[onclick="connectGoogleDrive()"] *, a[href="download.html"] *, .btn-download *, .btn-primary *, button[type="submit"] * {
  color: var(--accent-fg) !important;
}
.bg-indigo-600:hover, .bg-indigo-500:hover, button[onclick="connectGoogleDrive()"]:hover, a[href="download.html"]:hover, .btn-download:hover, .btn-primary:hover, button[type="submit"]:hover {
  background: #fff !important;
  transform: scale(1.03) !important;
  box-shadow: 0 0 20px rgba(255,255,255,0.4) !important;
}
.bg-indigo-600:active, .bg-indigo-500:active, button[onclick="connectGoogleDrive()"]:active, a[href="download.html"]:active {
  transform: scale(0.95) !important;
}

/* Secondary Buttons */
.bg-indigo-500\\/20, .bg-purple-500\\/20, .bg-emerald-500\\/20, .bg-red-500\\/10,
.bg-gray-100\\/50, .hover\\:bg-white\\/10, .border-white\\/10, button[onclick="createNewNote()"], button[onclick="openVoicePopup()"], .btn-secondary, .back-link {
  background: transparent !important;
  border: 1px solid var(--border) !important;
  color: var(--text) !important;
  border-radius: 0px !important;
  transition: all 0.1s ease-out !important;
  font-weight: 600 !important;
}
button:hover, .cursor-pointer:hover, .btn-secondary:hover, .back-link:hover {
  border-color: var(--accent) !important;
  background: rgba(255,255,255,0.08) !important;
}

/* Navigation Items */
.nav-item {
  border: 1px solid transparent !important;
  border-radius: 0px !important;
  margin: 4px 8px !important;
  transition: all 0.1s ease !important;
}
.nav-item.active {
  background: var(--text) !important;
  color: #000 !important;
  font-weight: 600 !important;
}
.nav-item.active * { color: #000 !important; }
.nav-item.active::before { display: none !important; }

/* Inputs & Textareas */
input, textarea {
  background: transparent !important;
  border: 1px solid var(--border) !important;
  border-radius: 0px !important;
  color: var(--text) !important;
  transition: border-color 0.1s, box-shadow 0.1s !important;
}
input:focus, textarea:focus {
  border-color: var(--accent) !important;
  box-shadow: inset 4px 0 0 var(--accent) !important;
  outline: none !important;
}
#note-title { border: none !important; }
#note-title:focus { box-shadow: none !important; border-color: transparent !important; }
#note-body { border: none !important; }

/* Note Editor Structural fixes */
#note-editor > div:first-child {
  background: transparent !important;
  border-bottom: 1px solid var(--border) !important;
}

/* Fast Energetic Animations */
.tab-content.active, .grid > div, #library-grid > div, #notes-list > div, .container, .privacy-container, .feedback-container {
  animation: snappyIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275) both !important;
}
.grid > div:nth-child(1) { animation-delay: 0.02s !important; }
.grid > div:nth-child(2) { animation-delay: 0.04s !important; }
.grid > div:nth-child(3) { animation-delay: 0.06s !important; }
.grid > div:nth-child(4) { animation-delay: 0.08s !important; }
.grid > div:nth-child(5) { animation-delay: 0.10s !important; }

@keyframes snappyIn {
  0% { opacity: 0; transform: scale(0.95) translateY(15px); }
  100% { opacity: 1; transform: scale(1) translateY(0); }
}

/* Login Overlay */
#login-overlay { 
  background: #000 !important; 
}
#login-overlay > div {
  background: transparent !important;
  border: 1px solid var(--border-hover) !important;
  border-radius: 0px !important;
  box-shadow: 0 0 50px rgba(255,255,255,0.05) !important;
  animation: snappyIn 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275) both !important;
}

/* Icons */
.material-icons-outlined { font-size: 18px !important; }

/* Rounded Full Exceptions (Avatars, live dots) */
.rounded-full { border-radius: 9999px !important; }
.logo-container { border-radius: 0 !important; }
.w-16.h-16 { border-radius: 0 !important; }

/* Scrollbar */
::-webkit-scrollbar { width: 4px; height: 4px; }
::-webkit-scrollbar-track { background: #000; }
::-webkit-scrollbar-thumb { background: #333; border-radius: 0; }
::-webkit-scrollbar-thumb:hover { background: #fff; }

@media (prefers-reduced-motion: reduce) {
  * { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}
</style>
`;

const files = [
  { path: 'docs/index.html', removeTags: ['premium-scholar-space', 'premium-scholar-space-js'] },
  { path: 'docs/download.html', removeTags: ['premium-scholar-space-pages'] },
  { path: 'docs/feedback.html', removeTags: ['premium-scholar-space-pages'] },
  { path: 'docs/privacy.html', removeTags: ['premium-scholar-space-pages'] }
];

for (const file of files) {
  let content = fs.readFileSync(file.path, 'utf8');
  
  for (const tag of file.removeTags) {
     const regex = new RegExp(\`<style id="\${tag}">[\\\\s\\\\S]*?<\\\\/style>\`, 'g');
     content = content.replace(regex, '');
     const regexJs = new RegExp(\`<script id="\${tag}">[\\\\s\\\\S]*?<\\\\/script>\`, 'g');
     content = content.replace(regexJs, '');
  }
  
  content = content.replace('</head>', vibeCss + '\\n</head>');
  
  fs.writeFileSync(file.path, content);
  console.log('Updated ' + file.path);
}
