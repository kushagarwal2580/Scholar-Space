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

    
    let delayCounter = 0;
    let delayTimer = null;

    const observer = new IntersectionObserver((entries, obs) => {
        const intersecting = entries.filter(entry => entry.isIntersecting && !entry.target.classList.contains('is-revealed'));
        
        // Sort intersecting elements by their physical position on the screen
        // Top-to-bottom, then Left-to-right for elements on the same row
        intersecting.sort((a, b) => {
            const topDiff = a.boundingClientRect.top - b.boundingClientRect.top;
            if (Math.abs(topDiff) < 10) {
                return a.boundingClientRect.left - b.boundingClientRect.left;
            }
            return topDiff;
        });
        
        intersecting.forEach((entry) => {
            entry.target.style.transitionDelay = (delayCounter * 0.1) + 's';
            entry.target.classList.add('is-revealed');
            obs.unobserve(entry.target); // Stop observing once revealed
            delayCounter++;
            
            if (delayTimer) clearTimeout(delayTimer);
            delayTimer = setTimeout(() => {
                delayCounter = 0;
            }, 100); 
        });
    }, observerOptions);


    // Identify elements to animate
    const selectorsToAnimate = [
        '.features-container > *:not(.feature-grid)',
        '.container > h1',
        '.container > h2',
        '.container > p',
        '.container > ul',
        '.container > div',
        '#tab-dashboard .grid > div',
        '#tab-dashboard .mb-8 > div',
        '#library-grid > div',
        '#recent-notes-container > div',
        '#notes-list > div',
        '.feature-item',
        
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
