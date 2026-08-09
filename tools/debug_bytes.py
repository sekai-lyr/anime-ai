with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "rb") as f:
    raw = f.read()

# Find the position of line 231 (the getDomesticMovieTicketLinks return)
idx = raw.find(b'"\\n')
if idx >= 0:
    start = max(0, idx - 10)
    end = min(len(raw), idx + 120)
    print("Found at byte", idx)
    print("Hex:", raw[start:end].hex())
else:
    print("NOT FOUND, searching for alternative patterns...")
    # Search for \n followed by Chinese-like bytes
    for i in range(len(raw) - 3):
        if raw[i:i+2] == b'\\n':
            print(f"Found \\n at byte {i}: hex={raw[i:i+60].hex()}")
            break
