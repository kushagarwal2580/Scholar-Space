const fs = require('fs');

const scrollCss = `
<style id="scroll-animations-css">
/* Smooth scroll reveal animations */
.reveal-on-scroll {
  opacity: 0;
  transform: translateY(20px);
  transition: opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1), transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  will-change: opacity, transform;
}
.reveal-on-scroll.is-revealed {
  opacity: 1;
  transform: translateY(0);
}

/* Add some staggered delays based on nth-child for lists */
.grid > div:nth-child(1) { transition-delay: 0.05s; }
.grid > div:nth-child(2) { transition-delay: 0.10s; }
.grid > div:nth-child(3) { transition-delay: 0.15s; }
.grid > div:nth-child(4) { transition-delay: 0.20s; }
.grid > div:nth-child(5) { transition-delay: 0.25s; }

.feature-grid .feature-item:nth-child(1) { transition-delay: 0.1s; }
.feature-grid .feature-item:nth-child(2) { transition-delay: 0.2s; }
.feature-grid .feature-item:nth-child(3) { transition-delay: 0.3s; }
.feature-grid .feature-item:nth-child(4) { transition-delay: 0.4s; }
</style>
`;

const scrollJs = `
<script id="scroll-animations-js">
document.addEventListener("DOMContentLoaded", () => {
    const observerOptions = {
        root: null,
        rootMargin: "0px 0px -30px 0px",
        threshold: 0.1
    };

    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('is-revealed');
                // Optional: Stop observing once revealed if you want it to stay
                // obs.unobserve(entry.target); 
            } else {
                // If we want it to animate OUT when scrolling up/down, remove the class
                entry.target.classList.remove('is-revealed');
            }
        });
    }, observerOptions);

    // Identify elements to animate
    const selectorsToAnimate = [
        '#tab-dashboard .grid > div',
        '#tab-dashboard .mb-8 > div',
        '#library-grid > div',
        '#recent-notes-container > div',
        '#notes-list > div',
        '.feature-item',
        '.container > h1',
        '.container > p',
        '.logo-container',
        '.privacy-container section',
        '.privacy-container h1',
        '.feedback-container h1',
        '.feedback-container form > div',
        '.settings-section'
    ];

    // Helper to periodically re-scan for dynamic elements (like loaded library files)
    const observeElements = () => {
        selectorsToAnimate.forEach(selector => {
            document.querySelectorAll(selector).forEach(el => {
                if (!el.classList.contains('reveal-on-scroll')) {
                    el.classList.add('reveal-on-scroll');
                    observer.observe(el);
                }
            });
        });
    };

    observeElements();
    
    // Watch for DOM changes to catch dynamically added items (like new notes or library items)
    const mutationObserver = new MutationObserver(() => {
        observeElements();
    });
    
    mutationObserver.observe(document.body, { childList: true, subtree: true });
});
</script>
`;

const files = [
  'docs/index.html',
  'docs/download.html',
  'docs/feedback.html',
  'docs/privacy.html'
];

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');
  
  // 1. Remove the strict vibe coder monochrome styles
  const regex = /<style id="vibe-coder-space">[\s\S]*?<\/style>/g;
  content = content.replace(regex, '');
  
  // 2. Remove any previously injected scroll animations so we don't duplicate
  const regexScrollCss = /<style id="scroll-animations-css">[\s\S]*?<\/style>/g;
  content = content.replace(regexScrollCss, '');
  const regexScrollJs = /<script id="scroll-animations-js">[\s\S]*?<\/script>/g;
  content = content.replace(regexScrollJs, '');

  // 3. Inject Scroll CSS into head
  content = content.replace('</head>', scrollCss + '\n</head>');
  
  // 4. Inject Scroll JS into body
  content = content.replace('</body>', scrollJs + '\n</body>');
  
  fs.writeFileSync(file, content);
  console.log('Restored glass and applied scroll animations to ' + file);
}
