import re

with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "r", encoding="utf-8") as f:
    content = f.read()

# Fix garbled Chinese by replacing known corrupted strings
fixes = {
    # Line 231: getDomesticMovieTicketLinks return
    '"\n濡傛灉浣犲憡璇夋垜鍩庡競/鍖哄幙鎴栧晢鍦堬紝鎴戣繕鑳借皟鐢ㄩ珮寰锋悳绱㈤檮杩戝奖闄㈠苟鐢熸垚瀵艰埅閾炬帴銆?': '"\n\u5982\u679c\u4f60\u544a\u8bc9\u6211\u57ce\u5e02/\u533a\u53bf\u6216\u5546\u5708\uff0c\u6211\u8fd8\u80fd\u8c03\u7528\u9ad8\u5fb7\u641c\u7d22\u9644\u8fd1\u5f71\u9662\u5e76\u751f\u6210\u5bfc\u822a\u94fe\u63a5\u3002"',

    # Line 258: getAnimeAiringSchedule description - contains garbled Chinese
    # I'll search by marker
}

# Apply fixes
for old, new in fixes.items():
    if old in content:
        content = content.replace(old, new)
        print(f"Fixed: {old[:50]}...")
    else:
        print(f"NOT FOUND: {old[:50]}...")

with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "w", encoding="utf-8") as f:
    f.write(content)
print("Done")
