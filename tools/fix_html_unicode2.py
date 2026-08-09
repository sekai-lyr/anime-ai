import re

with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "r", encoding="utf-8") as f:
    content = f.read()

# Replace \uXXXX escapes with actual Unicode characters in HTML context (not inside <script>)
parts = re.split(r"(<script>.*?</script>)", content, flags=re.DOTALL)

def replace_escapes(text):
    def replacer(m):
        return chr(int(m.group(1), 16))
    return re.sub(r"\\u([0-9a-fA-F]{4})", replacer, text)

result = []
for part in parts:
    if part.startswith("<script>"):
        result.append(part)
    else:
        result.append(replace_escapes(part))

content = "".join(result)

with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "w", encoding="utf-8") as f:
    f.write(content)
print("Done")
