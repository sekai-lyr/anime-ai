with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "rb") as f:
    raw = f.read()

# Fix 1: getDomesticMovieTicketLinks return line
# Corrupted bytes: getDomesticMovieTicketLink(movieTitle)\r\n                + "\n[corrupted Chinese]";
# After the + "\n part, replace corrupted Chinese with correct Chinese
old_pattern_1 = b'getDomesticMovieTicketLink(movieTitle)\r\n                + "\\n'
new_prefix_1 = b'getDomesticMovieTicketLink(movieTitle)\r\n                + "\\n'

pos = raw.find(old_pattern_1)
if pos >= 0:
    # Find the closing "; after this
    after_prefix = pos + len(old_pattern_1)
    semicolon = raw.find(b'";', after_prefix)
    if semicolon >= 0:
        new_chinese = '\u5982\u679c\u4f60\u544a\u8bc9\u6211\u57ce\u5e02/\u533a\u53bf\u6216\u5546\u5708\uff0c\u6211\u8fd8\u80fd\u8c03\u7528\u9ad8\u5fb7\u641c\u7d22\u9644\u8fd1\u5f71\u9662\u5e76\u751f\u6210\u5bfc\u822a\u94fe\u63a5\u3002'.encode('utf-8')
        raw = raw[:after_prefix] + new_chinese + raw[semicolon+2:]
        print("Fix 1: OK")
    else:
        print("Fix 1: semicolon not found")
else:
    print("Fix 1: pattern not found")

with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "wb") as f:
    f.write(raw)
print("Done")
