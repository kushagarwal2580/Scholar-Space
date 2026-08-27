const fs = require('fs');

const premiumAnimationsCss = `
<style id="premium-micro-animations">
/* Glass Shine Effect on Hover */
.bg-white\\/5, .feature-item, .logo-container {
    position: relative;
    overflow: hidden;
}
.bg-white\\/5::after, .feature-item::after, .logo-container::after {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 50%;
    height: 100%;
    background: linear-gradient(to right, rgba(255,255,255,0) 0%, rgba(255,255,255,0.05) 50%, rgba(255,255,255,0) 100%);
    transform: skewX(-25deg);
    transition: left 0.7s ease;
    z-index: 1;
    pointer-events: none;
}
.bg-white\\/5:hover::after, .feature-item:hover::after, .logo-container:hover::after {
    left: 200%;
}

/* Button Pulse/Glow */
.bg-indigo-600, .bg-indigo-500, button[onclick="connectGoogleDrive()"], .btn-download {
    position: relative;
    z-index: 1;
}
.bg-indigo-600::before, .bg-indigo-500::before, button[onclick="connectGoogleDrive()"]::before, .btn-download::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    border-radius: inherit;
    background: inherit;
    z-index: -1;
    transition: transform 0.3s ease, opacity 0.3s ease;
    opacity: 0;
}
.bg-indigo-600:hover::before, .bg-indigo-500:hover::before, button[onclick="connectGoogleDrive()"]:hover::before, .btn-download:hover::before {
    transform: scale(1.05, 1.15);
    opacity: 0.4;
    filter: blur(8px);
}

/* Smooth Icon Rotations */
.material-icons-outlined {
    transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.nav-item:hover .material-icons-outlined {
    transform: scale(1.15) rotate(5deg);
}

/* Float Animation for main logos/elements */
@keyframes softFloat {
    0% { transform: translateY(0px); }
    50% { transform: translateY(-5px); }
    100% { transform: translateY(0px); }
}
.logo-container svg {
    animation: softFloat 4s ease-in-out infinite;
}
</style>
`;

const parallaxJs = `
<script id="mouse-parallax-js">
document.addEventListener("DOMContentLoaded", () => {
    // Parallax effect for blobs based on mouse movement
    const blobs = document.querySelectorAll('.blob');
    if (blobs.length > 0) {
        document.addEventListener('mousemove', (e) => {
            const x = (e.clientX / window.innerWidth - 0.5) * 40; // max 20px movement
            const y = (e.clientY / window.innerHeight - 0.5) * 40;
            
            blobs.forEach((blob, index) => {
                const speed = (index + 1) * 0.5;
                blob.style.transform = \`translate(\${x * speed}px, \${y * speed}px)\`;
            });
        });
    }
});
</script>
`;

const files = ['docs/index.html', 'docs/download.html', 'docs/feedback.html', 'docs/privacy.html'];

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');
  
  // Clean old tags to prevent duplicates
  content = content.replace(/<style id="premium-micro-animations">[\s\S]*?<\/style>/g, '');
  content = content.replace(/<script id="mouse-parallax-js">[\s\S]*?<\/script>/g, '');

  // Inject
  content = content.replace('</head>', premiumAnimationsCss + '\n</head>');
  content = content.replace('</body>', parallaxJs + '\n</body>');
  
  fs.writeFileSync(file, content);
  console.log('Added micro-animations to ' + file);
}
