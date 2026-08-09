import re

with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "r", encoding="utf-8") as f:
    content = f.read()

parts = re.split(r"(<script>.*?</script>)", content, flags=re.DOTALL)

def replace_escapes(text):
    # First collect all \uXXXX sequences
    seqs = re.findall(r"\\u([0-9a-fA-F]{4})", text)
    if not seqs:
        return text
    
    # Process pairs
    i = 0
    chars = []
    while i < len(seqs):
        code = int(seqs[i], 16)
        if 0xD800 <= code <= 0xDBFF and i + 1 < len(seqs):
            next_code = int(seqs[i+1], 16)
            if 0xDC00 <= next_code <= 0xDFFF:
                # Surrogate pair
                hi = code - 0xD800
                lo = next_code - 0xDC00
                codepoint = 0x10000 + (hi << 10) + lo
                chars.append(chr(codepoint))
                i += 2
                continue
        chars.append(chr(code))
        i += 1
    
    # Replace in order
    result = re.sub(r"\\u[0-9a-fA-F]{4}", lambda m, it=iter(chars): next(it), text)
    return result

result_parts = []
for part in parts:
    if part.startswith("<script>"):
        result_parts.append(part)
    else:
        result_parts.append(replace_escapes(part))

content = "".join(result_parts)

with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "w", encoding="utf-8") as f:
    f.write(content)
print("Done")
