with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "rb") as f:
    raw = f.read()

# Fix remaining corrupted Chinese strings
# Each fix: (english_marker_bytes, replacement_chinese_str)

fixes = [
    # Fix 2: getAnimeAiringSchedule description - "Use for 今天播什么..."
    (b'Use for', None),  # We'll handle description differently
]

# Fix: find all corrupted Chinese in the file and replace them
# Approach: find " + "\n[corrupted]"; " patterns and replace

# Fix: getAnimeAiringSchedule return line
# Pattern: animeService.getAnimeAiringSchedule(target, ...) ... result + "\n[corrupted]" :
marker2 = 'animeService.getAnimeAiringSchedule(target,'.encode()
pos2 = raw.find(marker2)
if pos2 >= 0:
    # Find the corrupted Chinese after "result + \"\\n"
    after_str = raw.find(b'"\\n', pos2)
    if after_str >= 0:
        # Find the closing quote before " :"
        end_quote = raw.find(b'" :', after_str + 3)
        if end_quote >= 0:
            new_chinese = '\u9009\u5b9a\u4f5c\u54c1\u540e\u53ef\u4ee5\u8ba9\u6211\u751f\u6210\u66f4\u65b0\u63d0\u9192\u3002'.encode('utf-8')
            raw = raw[:after_str+3] + new_chinese + raw[end_quote+1:]
            print("Fix 2: getAnimeAiringSchedule return OK")
        else:
            # Try different pattern
            end_quote = raw.find(b'" ', after_str + 3)
            if end_quote >= 0 and raw[end_quote+2:end_quote+3] == b':':
                new_chinese = '\u9009\u5b9a\u4f5c\u54c1\u540e\u53ef\u4ee5\u8ba9\u6211\u751f\u6210\u66f4\u65b0\u63d0\u9192\u3002'.encode('utf-8')
                raw = raw[:after_str+3] + new_chinese + raw[end_quote+1:]
                print("Fix 2: getAnimeAiringSchedule return OK (alt)")
            else:
                print("Fix 2: end quote not found")

# Fix 3: getAnimeAiringSchedule fallback (after colon)
marker3 = 'animeService.getAnimeAiringSchedule'.encode()
pos3 = raw.rfind(marker3)  # Use rfind to get the last occurrence
if pos3 >= 0:
    # Find the fallback string after ": \""
    fallback_pos = raw.find(b': "', pos3)
    if fallback_pos >= 0:
        # Find the closing ";
        end_pos = raw.find(b'";', fallback_pos)
        if end_pos >= 0:
            new_chinese = '\u64ad\u51fa\u65e5\u5386\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002'.encode('utf-8')
            raw = raw[:fallback_pos+3] + new_chinese + raw[end_pos+1:]
            print("Fix 3: getAnimeAiringSchedule fallback OK")
        else:
            print("Fix 3: end quote not found")

# Fix 4: getAnimeWatchLinks fallback
marker4 = 'getAnimeWatchLinks'.encode()
pos4 = raw.find(marker4)
if pos4 >= 0:
    fallback_pos = raw.find(b': "', pos4)
    if fallback_pos >= 0:
        end_pos = raw.find(b'";', fallback_pos)
        if end_pos >= 0:
            new_chinese = '\u6ca1\u6709\u627e\u5230\u64ad\u653e\u5165\u53e3\u3002\u8bf7\u544a\u8bc9\u6211\u56fd\u5bb6/\u5730\u533a\uff0c\u6211\u5c06\u8054\u7f51\u641c\u7d22\u5f53\u5730\u6b63\u7248\u5e73\u53f0\u3002'.encode('utf-8')
            raw = raw[:fallback_pos+3] + new_chinese + raw[end_pos+1:]
            print("Fix 4: getAnimeWatchLinks OK")
        else:
            print("Fix 4: end quote not found")

# Fix 5: getAnimeRelations result + fallback
marker5 = 'getAnimeRelations'.encode()
pos5 = raw.find(marker5)
if pos5 >= 0:
    # First fix the result + "\n..." part
    result_plus = raw.find(b'"\\n', pos5)
    if result_plus >= 0:
        end_quote = raw.find(b'" :', result_plus + 3)
        if end_quote >= 0:
            new_chinese = '\u5982\u679c\u4f60\u8981\u5b8c\u6574\u89c2\u770b\u987a\u5e8f\uff0c\u6211\u53ef\u4ee5\u6309\u4e3b\u7ebf\u3001\u5916\u4f20\u548c\u5267\u573a\u7248\u7ee7\u7eed\u6574\u7406\u3002'.encode('utf-8')
            raw = raw[:result_plus+3] + new_chinese + raw[end_quote+1:]
            print("Fix 5a: getAnimeRelations result OK")
        else:
            print("Fix 5a: end quote not found")
    
    # Then fix fallback after ": "
    fallback_pos = raw.find(b': "', pos5)
    if fallback_pos >= 0:
        end_pos = raw.find(b'";', fallback_pos)
        if end_pos >= 0:
            new_chinese = '\u6ca1\u6709\u627e\u5230\u4f5c\u54c1\u5173\u7cfb\u3002'.encode('utf-8')
            raw = raw[:fallback_pos+3] + new_chinese + raw[end_pos+1:]
            print("Fix 5b: getAnimeRelations fallback OK")
        else:
            print("Fix 5b: end quote not found")

# Fix 6: getAnimeRecommendations fallback
marker6 = 'getAnimeRecommendations'.encode()
pos6 = raw.find(marker6)
if pos6 >= 0:
    fallback_pos = raw.find(b': "', pos6)
    if fallback_pos >= 0:
        end_pos = raw.find(b'";', fallback_pos)
        if end_pos >= 0:
            new_chinese = '\u6682\u65f6\u6ca1\u6709\u627e\u5230\u76f8\u5173\u63a8\u8350\u3002'.encode('utf-8')
            raw = raw[:fallback_pos+3] + new_chinese + raw[end_pos+1:]
            print("Fix 6: getAnimeRecommendations OK")
        else:
            print("Fix 6: end quote not found")

with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "wb") as f:
    f.write(raw)
print("All done")
