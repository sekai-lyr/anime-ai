with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "rb") as f:
    raw = f.read()
print("Length:", len(raw))
print("First 200 bytes:", raw[:200])
print("Contains welcome:", b"welcome" in raw)
print("Contains AnimeAI:", b"AnimeAI" in raw)
