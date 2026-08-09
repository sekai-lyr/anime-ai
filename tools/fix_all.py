import re

# Read the corrupted file and extract non-corrupted English parts
with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "r", encoding="utf-8") as f:
    content = f.read()

# Replace ALL PUA chars (U+E000-U+F8FF) in string contexts with safe alternatives
def fix_pua(match):
    text = match.group(0)
    result = []
    for c in text:
        cp = ord(c)
        if 0xE000 <= cp <= 0xF8FF:
            result.append('?')
        else:
            result.append(c)
    return ''.join(result)

# Fix Chinese in string literals - replace garbled chars with proper Unicode escapes
# Pattern: find Java string literals containing PUA chars
lines = content.split('\n')
new_lines = []
for line in lines:
    has_pua = any(0xE000 <= ord(c) <= 0xF8FF for c in line)
    if has_pua:
        # Convert line to safe form - escape all non-ASCII chars
        new_line = []
        for c in line:
            cp = ord(c)
            if cp > 127 and cp < 0xE000:
                new_line.append(f'\\u{cp:04X}')
            elif 0xE000 <= cp <= 0xF8FF:
                # PUA char - skip it
                pass
            else:
                new_line.append(c)
        new_lines.append(''.join(new_line))
    else:
        new_lines.append(line)

content = '\n'.join(new_lines)

with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "w", encoding="utf-8") as f:
    f.write(content)
print("OK")
