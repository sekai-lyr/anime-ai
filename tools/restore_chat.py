# Build and write the original chat.html
css = """:root{--p:#6c5ce7;--pd:#5a4bd1;--a:#fd79a8;--bg:#0f0f23;--bg2:#1a1a2e;--bc:#1e1e32;--t:#e0e0e0;--tm:#8888aa;--b:#2a2a4a}
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:"Microsoft YaHei","PingFang SC",sans-serif;background:var(--bg);color:var(--t);height:100vh;display:flex;flex-direction:column}
.top{display:flex;justify-content:space-between;align-items:center;padding:0 20px;height:50px;background:var(--bg2);border-bottom:1px solid var(--b);flex-shrink:0}
.top span{font-weight:600;color:var(--p)}
.top button{background:none;border:1px solid var(--b);color:var(--tm);padding:4px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.top button:hover{color:#fff;border-color:var(--p)}
.main{flex:1;display:flex;flex-direction:column;overflow:hidden}
.msgs{flex:1;overflow-y:auto;padding:16px}
.msgs::-webkit-scrollbar{width:4px}.msgs::-webkit-scrollbar-thumb{background:var(--b);border-radius:2px}
.bot{display:flex;align-items:flex-start;gap:8px;margin-bottom:14px}
.bot .avatar{width:32px;height:32px;border-radius:50%;background:var(--p);display:flex;align-items:center;justify-content:center;font-size:14px;flex-shrink:0;color:#fff}
.bot .bubble{background:var(--bc);border:1px solid var(--b);border-radius:4px 12px 12px 12px;padding:10px 14px;max-width:75%;line-height:1.6;font-size:14px;word-break:break-word}
.user{display:flex;justify-content:flex-end;margin-bottom:14px}
.user .bubble{background:var(--p);color:#fff;border-radius:12px 4px 12px 12px;padding:10px 14px;max-width:75%;line-height:1.6;font-size:14px;word-break:break-word}
.user .sent-image{display:block;max-width:min(360px,70vw);max-height:360px;object-fit:contain;border-radius:12px 4px 12px 12px;border:1px solid var(--b);background:var(--bc)}
.typing{display:flex;gap:4px;padding:12px 16px}.typing span{width:6px;height:6px;background:var(--tm);border-radius:50%;animation:b 1.4s infinite}
.typing span:nth-child(2){animation-delay:.2s}.typing span:nth-child(3){animation-delay:.4s}
@keyframes b{0%,60%,100%{transform:translateY(0);opacity:.4}30%{transform:translateY(-6px);opacity:1}}
.input-area{display:flex;gap:10px;padding:12px 16px;background:var(--bg2);border-top:1px solid var(--b);flex-shrink:0}
.composer{background:var(--bg2);border-top:1px solid var(--b);flex-shrink:0}.image-preview{display:none;align-items:center;gap:10px;padding:10px 16px 0}.image-preview img{width:64px;height:64px;object-fit:cover;border-radius:8px;border:1px solid var(--b)}.image-preview button,.upload-btn{background:none;border:1px solid var(--b);color:var(--tm);padding:8px 12px;border-radius:8px;cursor:pointer}.upload-btn:hover,.image-preview button:hover{color:#fff;border-color:var(--p)}
.input-area{border-top:none}.input-area textarea{flex:1;padding:10px 14px;border:1px solid var(--b);border-radius:8px;background:var(--bg);color:var(--t);font-size:14px;resize:none;outline:none;min-height:42px;max-height:120px;font-family:inherit}
.input-area textarea:focus{border-color:var(--p)}
.input-area button{background:var(--p);color:#fff;border:none;padding:10px 18px;border-radius:8px;cursor:pointer;font-size:14px;font-weight:600;white-space:nowrap}
.input-area button:hover{background:var(--pd)}
.input-area button:disabled{opacity:.5}
.toast{position:fixed;top:16px;left:50%;transform:translateX(-50%);background:#e74c3c;color:#fff;padding:8px 20px;border-radius:6px;font-size:13px;z-index:999;display:none}
"""

html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AnimeAI Chat</title>
<style>
{css}</style>
</head>
<body>
<div class="top"><span>AnimeAI</span><div><span id="greeting"></span><button onclick="logout()">Exit</button></div></div>
<div class="main">
<div class="msgs" id="msgs"><div style="text-align:center;color:var(--tm);margin-top:60px;font-size:14px">Welcome to AnimeAI! Ask me about anime.</div></div>
<div class="composer"><div class="image-preview" id="imagePreview"><img id="previewImg" alt="待分析图片"><span>图片已选择</span><button id="removeImage" type="button">移除</button></div><div class="input-area"><input id="imageInput" type="file" accept="image/*" hidden><button class="upload-btn" id="uploadBtn" type="button">上传图片</button><textarea id="msgInput" placeholder="Ask about anime..." rows="1"></textarea><button id="sendBtn">Send</button></div></div>
</div>
<div class="toast" id="toast"></div>
<script>
var msgs=document.getElementById("msgs"),input=document.getElementById("msgInput"),sendBtn=document.getElementById("sendBtn"),toast=document.getElementById("toast"),imageInput=document.getElementById("imageInput"),uploadBtn=document.getElementById("uploadBtn"),imagePreview=document.getElementById("imagePreview"),previewImg=document.getElementById("previewImg"),removeImage=document.getElementById("removeImage");
var loading=false,selectedImage="";
var cid=sessionStorage.getItem("cid")||("c"+Date.now()+Math.random().toString(36).substr(2,6));
sessionStorage.setItem("cid",cid);
function toastMsg(m,d){toast.textContent=m;toast.style.display="block";clearTimeout(toast._t);toast._t=setTimeout(function(){toast.style.display="none"},d||3000)}
function addMsg(role,text){var d=document.createElement("div");d.className=role;if(role==="user"){d.innerHTML='<div class="bubble">'+text.replace(/\\n/g,"<br>")+"</div>"}else{d.innerHTML='<div class="avatar">AI</div><div class="bubble">'+text.replace(/\\n/g,"<br>")+"</div>"}msgs.appendChild(d);msgs.scrollTop=msgs.scrollHeight}
function addImageMsg(src,text){var d=document.createElement("div");d.className="user";var box=document.createElement("div");var img=document.createElement("img");img.className="sent-image";img.src=src;img.alt="上传的图片";box.appendChild(img);if(text){var bubble=document.createElement("div");bubble.className="bubble";bubble.textContent=text;box.appendChild(bubble)}d.appendChild(box);msgs.appendChild(d);msgs.scrollTop=msgs.scrollHeight}
function addTyping(){var d=document.createElement("div");d.className="typing";d.id="typing";d.innerHTML="<span></span><span></span><span></span>";msgs.appendChild(d);msgs.scrollTop=msgs.scrollHeight}
function removeTyping(){var d=document.getElementById("typing");if(d)d.remove()}
uploadBtn.onclick=function(){imageInput.click()};
imageInput.onchange=function(){var file=this.files[0];if(!file)return;if(file.size>10*1024*1024){toastMsg("图片不能超过10MB");this.value="";return}var reader=new FileReader();reader.onload=function(e){selectedImage=e.target.result;previewImg.src=selectedImage;imagePreview.style.display="flex"};reader.readAsDataURL(file)};
removeImage.onclick=function(){selectedImage="";imageInput.value="";previewImg.removeAttribute("src");imagePreview.style.display="none"};
sendBtn.onclick=function(){var t=input.value.trim();if((!t&&!selectedImage)||loading)return;loading=true;sendBtn.disabled=true;var image=selectedImage;if(image)addImageMsg(image,t);else addMsg("user",t);input.value="";input.style.height="auto";removeImage.onclick();var w=msgs.querySelector('div:first-child');if(w)w.style.display="none";addTyping();var url=image?"/api/ai/analyze-image":"/api/ai/chat-with-tools";var body=image?{image:image,prompt:t||"请分析图片中的动漫角色和对应番剧"}:{message:t,conversationId:cid};fetch(url,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)}).then(function(r){removeTyping();if(!r.ok)throw new Error("HTTP "+r.status);return r.json()}).then(function(d){if(d.success&&d.content)addMsg("bot",d.content);else toastMsg(d.error||"No response")}).catch(function(e){removeTyping();toastMsg("Error: "+e.message)}).finally(function(){loading=false;sendBtn.disabled=false;input.focus()})};
input.onkeydown=function(e){if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();sendBtn.onclick()}};
input.addEventListener("input",function(){this.style.height="auto";this.style.height=Math.min(this.scrollHeight,120)+"px"});
fetch("/api/auth/me").then(function(r){return r.json()}).then(function(d){if(d.code===200)document.getElementById("greeting").textContent="Hi, "+d.data.userName;else location.href="/login"}).catch(function(){location.href="/login"});
async function logout(){await fetch("/api/auth/logout",{method:"POST"});location.href="/login"}
input.focus();
// Reminder polling
function pollReminders(){
    fetch('/api/reminders/pending?conversationId='+cid).then(function(r){return r.json()}).then(function(d){
        if(d.success && d.messages && d.messages.length>0){
            d.messages.forEach(function(msg){addMsg('bot',msg);if('Notification' in window&&Notification.permission==='granted')new Notification('AnimeAI 提醒',{body:msg.replace(/[*#]/g,'')})})
        }
    }).catch(function(){})
}
if('Notification' in window&&Notification.permission==='default')Notification.requestPermission().catch(function(){});
setInterval(pollReminders, 5000);
pollReminders();
</script>
</body>
</html>"""

with open(r"D:\Sekai_two\memory-14\src\main\resources\static\chat.html", "w", encoding="utf-8") as f:
    f.write(html.format(css=css))
print("Restored:", len(html), "bytes")
