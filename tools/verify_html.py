import re
with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "r", encoding="utf-8") as f:
    content = f.read()

parts = re.split(r"<script>.*?</script>", content, flags=re.DOTALL)
found_any = False
for part in parts:
    esc = re.findall(r"\\u[0-9a-fA-F]{4}", part)
    if esc:
        found_any = True
        print("Remaining escapes:", esc[:5])
if not found_any:
    print("No remaining \\uXXXX escapes in HTML")

idx = content.find('class="welcome"')
if idx >= 0:
    snippet = content[idx:idx+600]
    print(snippet[:600])
