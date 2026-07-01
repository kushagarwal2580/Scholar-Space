with open('docs/privacy.html', 'r') as f:
    text = f.read()

text = text.replace('''        p, ul {
        }
        ul {
            padding-left: 20px;
            
            color: var(--text-secondary);
            line-height: 1.6;
            margin-bottom: 16px;
            font-size: 16px;
            text-align: left;
        }''', '''        p, ul {
            color: var(--text-secondary);
            line-height: 1.6;
            margin-bottom: 16px;
            font-size: 16px;
            text-align: left;
        }
        ul {
            padding-left: 20px;
        }''')

with open('docs/privacy.html', 'w') as f:
    f.write(text)
