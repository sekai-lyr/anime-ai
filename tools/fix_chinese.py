with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "r", encoding="utf-8") as f:
    content = f.read()

# Each fix: (search_marker_english_text_before, replacement_line_or_block)
# Use English parts as anchors to locate the corrupted Chinese

fixes = [
    # 1. getDomesticMovieTicketLinks return line (~231)
    # Find: animeService.getDomesticMovieTicketLink(movieTitle)\n                + "
    ('getDomesticMovieTicketLink(movieTitle)',
     'getDomesticMovieTicketLink(movieTitle)\n                + "\\n\\u5982\\u679c\\u4f60\\u544a\\u8bc9\\u6211\\u57ce\\u5e02/\\u533a\\u53bf\\u6216\\u5546\\u5708\\uff0c\\u6211\\u8fd8\\u80fd\\u8c03\\u7528\\u9ad8\\u5fb7\\u641c\\u7d22\\u9644\\u8fd1\\u5f71\\u9662\\u5e76\\u751f\\u6210\\u5bfc\\u822a\\u94fe\\u63a5\\u3002";'),

    # 2. getAnimeAiringSchedule description - starts with "Get the real-time anime"
    ('description = "Get the real-time anime episode airing schedule for a date. Use for',
     'description = "Get the real-time anime episode airing schedule for a date. Use for today\'s airing, update time, airing calendar."'),

    # 3. getAnimeAiringSchedule return - has "\n选定作品后"
    # After: String result = animeService.getAnimeAiringSchedule(...)
    # The return line has Chinese
    # Instead of exact match, I'll replace the whole return statement
    # Actually let me find a unique anchor

    # 4. getAnimeWatchLinks fallback return
    # 5. getAnimeRelations return + fallback
    # 6. getAnimeRecommendations fallback
]

# Apply simple text replacements for lines we can match
# For the complex lines, use regex patterns

import re

# Fix 1: getDomesticMovieTicketLinks return line
# Match from "getDomesticMovieTicketLink(movieTitle)" to the next ";"
pattern1 = r'(getDomesticMovieTicketLink\(movieTitle\))\s*\n\s*\+\s*\"[^"]*\"\s*;'
replacement1 = r'\1\n                + "\n\u5982\u679c\u4f60\u544a\u8bc9\u6211\u57ce\u5e02/\u533a\u53bf\u6216\u5546\u5708\uff0c\u6211\u8fd8\u80fd\u8c03\u7528\u9ad8\u5fb7\u641c\u7d22\u9644\u8fd1\u5f71\u9662\u5e76\u751f\u6210\u5bfc\u822a\u94fe\u63a5\u3002";'
content = re.sub(pattern1, replacement1, content)

# Fix 2-6: Find and replace corrupted Chinese strings using surrounding English context
# Pattern for corrupted Chinese in string literals
# Replace any string between quotes that contains PUA characters with a clean version

# Let me find all corrupted strings and fix them by context
corruptions = [
    # getAnimeAiringSchedule return - the + "\n... part
    (r'result \+ \"\\n[^\"]*\"\s*:', 
     'result + "\\n选定作品后可以让我生成更新提醒。" :'),
    
    # getAnimeAiringSchedule fallback
    (r': \"[^\"]*鎾[^\"]*\"\s*;',
     ': "播出日历暂时不可用，请稍后重试。";'),
    
    # getAnimeWatchLinks fallback
    (r': \"[^\"]*鎾[^\"]*\"\s*;',
     ': "没有找到播放入口。请告诉我国家/地区，我将联网搜索当地正版平台。";'),
    
    # getAnimeRelations result + 
    # getAnimeRelations fallback
    # getAnimeRecommendations fallback
]

for pattern, replacement in corruptions:
    content = re.sub(pattern, replacement, content)

with open(r"D:\Sekai_two\memory-14\src\main\java\com\example\demo\ai\SpringAiTools.java", "w", encoding="utf-8") as f:
    f.write(content)
print("Done")
