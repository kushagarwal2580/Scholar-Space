for f_path in ['docs/privacy.html', 'docs/feedback.html']:
    with open(f_path, 'r') as f:
        text = f.read()
    
    text = text.replace('h1 {\n            color: var(--primary-color);\n            margin-top: 0;\n            font-size: 32px;\n            font-weight: 700;\n            letter-spacing: -0.5px;\n            text-align: left;\n        }', 'h1 {\n            color: var(--primary-color);\n            margin-top: 0;\n            font-size: 32px;\n            font-weight: 700;\n            letter-spacing: -0.5px;\n            text-align: center;\n        }')
    text = text.replace('h1 {\n            color: var(--primary-color);\n            margin: 0 0 12px;\n            font-size: 32px;\n            font-weight: 700;\n            letter-spacing: -0.5px;\n            text-align: left;\n        }', 'h1 {\n            color: var(--primary-color);\n            margin: 0 0 12px;\n            font-size: 32px;\n            font-weight: 700;\n            letter-spacing: -0.5px;\n            text-align: center;\n        }')
    
    with open(f_path, 'w') as f:
        f.write(text)
