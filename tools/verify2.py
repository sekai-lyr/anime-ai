with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("welcome")
if idx >= 0:
    print(content[idx:idx+600])
else:
    print("not found")
print("---")
# Check if emoji renders
idx2 = content.find("AnimeAI")
if idx2 >= 0:
    print(content[idx2:idx2+200])
