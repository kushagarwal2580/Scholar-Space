for f_path in ['docs/privacy.html', 'docs/feedback.html']:
    with open(f_path, 'r') as f:
        text = f.read()
    
    text = text.replace('<div style="text-align: left;">\n            <a href="download.html" class="back-link">', '<div style="text-align: center;">\n            <a href="download.html" class="back-link">')
    
    with open(f_path, 'w') as f:
        f.write(text)
