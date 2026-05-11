package com.aiassistant.ui.chat.components

import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

private const val MAX_CACHED_VIEWS = 20

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 15f,
    fontColor: Color = Color.Unspecified,
    messageId: Long = 0L
) {
    val textColorHex = remember(fontColor) {
        if (fontColor != Color.Unspecified) {
            val r = (fontColor.red * 255).toInt()
            val g = (fontColor.green * 255).toInt()
            val b = (fontColor.blue * 255).toInt()
            String.format("#%02X%02X%02X", r, g, b)
        } else "#333333"
    }

    val escapedContent = remember(content) { escapeForJs(content) }
    val fullHtml = remember(escapedContent, fontSize, textColorHex) {
        buildHtmlPage(escapedContent, fontSize, textColorHex)
    }

    key(messageId) {
        AndroidView(
            factory = { ctx ->
                MarkdownWebViewPool.acquire(messageId, ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    loadDataWithBaseURL(
                        "https://cdn.jsdelivr.net/",
                        fullHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                val currentTag = webView.tag as? Long ?: 0L
                if (currentTag != messageId) {
                    webView.tag = messageId
                    webView.loadDataWithBaseURL(
                        "https://cdn.jsdelivr.net/",
                        fullHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                MarkdownWebViewPool.release(messageId, webView)
            },
            modifier = modifier.fillMaxWidth()
        )
    }
}

private object MarkdownWebViewPool {
    private val pool = mutableMapOf<Long, WebView>()

    fun acquire(messageId: Long, ctx: android.content.Context): WebView {
        pool.remove(messageId)?.let { cached ->
            cached.removeFromParent()
            return cached
        }
        return createWebView(ctx).apply {
            tag = messageId
        }
    }

    fun release(messageId: Long, view: WebView) {
        if (pool.size >= MAX_CACHED_VIEWS) {
            val oldest = pool.keys.firstOrNull() ?: return
            pool.remove(oldest)?.destroy()
        }
        pool[messageId] = view
    }

    fun destroyAll() {
        pool.values.forEach { it.destroy() }
        pool.clear()
    }

    private fun createWebView(ctx: android.content.Context): WebView {
        return WebView(ctx).apply {
            setBackgroundColor(0)
            isNestedScrollingEnabled = false
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {}
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.defaultTextEncodingName = "UTF-8"
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
    }
}

private fun android.view.View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}

private fun escapeForJs(text: String): String {
    return text
        .replace("\\", "\\\\")
        .replace("<", "\\u003C")
        .replace(">", "\\u003E")
        .replace("&", "\\u0026")
        .replace("`", "\\`")
        .replace("\$", "\\\$")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\t", "\\t")
}

private fun buildHtmlPage(escapedContent: String, fontSize: Float, textColor: String): String {
    val fs = fontSize
    return """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:transparent;color:${textColor};font-size:${fs}px;line-height:1.7;padding:6px 0;word-wrap:break-word;overflow-wrap:break-word;-webkit-text-size-adjust:100%}
h1{font-size:${fs * 1.6}px;font-weight:700;margin:16px 0 8px;line-height:1.3}
h2{font-size:${fs * 1.4}px;font-weight:700;margin:14px 0 6px;line-height:1.3}
h3{font-size:${fs * 1.25}px;font-weight:600;margin:12px 0 6px;line-height:1.3}
h4,h5,h6{font-size:${fs * 1.1}px;font-weight:600;margin:10px 0 4px;line-height:1.3}
p{margin:6px 0}
strong,b{font-weight:700}
em,i{font-style:italic}
del,s{text-decoration:line-through;color:#999}
a{color:#2E7D32;text-decoration:underline}
ul{list-style-type:disc;padding-left:24px;margin:8px 0}
ol{list-style-type:decimal;padding-left:24px;margin:8px 0}
li{margin:4px 0;line-height:1.7}
li>p{margin:2px 0}
hr{border:none;border-top:1px solid rgba(0,0,0,0.15);margin:16px 0}
pre{background:rgba(0,0,0,0.06);border-radius:8px;padding:12px;margin:8px 0;overflow-x:auto;-webkit-overflow-scrolling:touch}
pre code{background:none;padding:0;font-size:${fs * 0.85}px;line-height:1.5;display:block;white-space:pre-wrap;word-wrap:break-word}
code{font-family:monospace;background:rgba(0,0,0,0.06);padding:1px 5px;border-radius:3px;font-size:${fs * 0.9}px}
blockquote{border-left:3px solid rgba(0,0,0,0.15);padding:4px 12px;margin:8px 0;color:#666;background:rgba(0,0,0,0.03);border-radius:0 4px 4px 0}
table{border-collapse:collapse;width:100%;margin:8px 0;font-size:${fs * 0.9}px}
th,td{border:1px solid rgba(0,0,0,0.12);padding:6px 10px;text-align:left}
th{background:rgba(0,0,0,0.04);font-weight:600}
.katex-display{overflow-x:auto;-webkit-overflow-scrolling:touch;padding:8px 0;margin:0}
.katex{font-size:1em}
img{max-width:100%;height:auto}
.math-block{margin:8px 0;overflow-x:auto}
.math-inline{display:inline}
.math-fallback code{background:rgba(0,0,0,0.06);padding:4px 8px;border-radius:4px;display:block;white-space:pre-wrap;color:#c7254e;font-size:0.9em}
</style>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css"
      onerror="document.body.classList.add('katex-failed')">
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"
        onerror="window.katexLoadFailed=true"></script>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
</head>
<body>
<div id="md-content"></div>
<script>
var rawMd="$escapedContent";

var mathBlock={
  name:'mathBlock',
  level:'block',
  start:function(src){var m=src.match(/\$\$|\\\[/);return m?m.index:undefined;},
  tokenizer:function(src){
    var m=src.match(/^(\$\$)\n?([\s\S]+?)\n?\s*\1(?:\n|$)/);
    if(m)return{type:'mathBlock',raw:m[0],text:m[2].trim()};
    m=src.match(/^\\\[\n?([\s\S]+?)\n?\s*\\\](?:\n|$)/);
    if(m)return{type:'mathBlock',raw:m[0],text:m[1].trim()};
  },
  renderer:function(token){
    try{
      if(typeof katex!=='undefined'&&!window.katexLoadFailed){
        return'<div class="math-block">'+katex.renderToString(token.text,{displayMode:true,throwOnError:false})+'</div>';
      }
    }catch(e){}
    return'<div class="math-block math-fallback"><code>$$'+token.text+'$$</code></div>';
  }
};

var mathInline={
  name:'mathInline',
  level:'inline',
  start:function(src){var m=src.match(/\$|\\\(/);return m?m.index:undefined;},
  tokenizer:function(src){
    var m=src.match(/^(\$)([^\$\n]+?)\1(?!\$)/);
    if(m)return{type:'mathInline',raw:m[0],text:m[2].trim()};
    m=src.match(/^\\\((.+?)\\\)/);
    if(m)return{type:'mathInline',raw:m[0],text:m[1].trim()};
  },
  renderer:function(token){
    try{
      if(typeof katex!=='undefined'&&!window.katexLoadFailed){
        return'<span class="math-inline">'+katex.renderToString(token.text,{displayMode:false,throwOnError:false})+'</span>';
      }
    }catch(e){}
    return'<span class="math-inline math-fallback"><code>$'+token.text+'$</code></span>';
  }
};

function preprocessFootnotes(md){
  var defs={};
  md=md.replace(/^\[\^(\w+)\]:\s*(.+)$/gm,function(m,id,content){
    defs[id]=content;return '';
  });
  md=md.replace(/\[\^(\w+)\]/g,function(m,id){
    if(defs[id])return '<sup title="'+defs[id]+'">['+id+']</sup>';
    return m;
  });
  return md;
}

function preprocessCJK(md){
  var segments=[],codeBlockRegex=/(```[\s\S]*?```|`[^`\n]+`|```[\s\S]*$)/g,lastIndex=0,m;
  while((m=codeBlockRegex.exec(md))!==null){
    if(m.index>lastIndex)segments.push({t:md.slice(lastIndex,m.index),p:false});
    segments.push({t:m[0],p:true});
    lastIndex=m.index+m[0].length;
  }
  if(lastIndex<md.length)segments.push({t:md.slice(lastIndex),p:false});
  var CJK='[\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]';
  var result='';
  for(var i=0;i<segments.length;i++){
    if(segments[i].p){result+=segments[i].t;continue;}
    var s=segments[i].t;
    s=s.replace(new RegExp('('+CJK+'|[\\w\\.\\)\\]\\}!?,;])(#{1,6})([^#\\n])','g'),'$1\n$2 $3');
    s=s.replace(new RegExp('('+CJK+'|[\\w\\.\\)\\]\\}!?,;])(#{1,6})$','gm'),'$1\n$2');
    s=s.replace(/^(#{1,6})([^ #\n\d])/gm,'$1 $2');
    s=s.replace(new RegExp('('+CJK+')(\\s*[-*+])\\s*('+CJK+')','g'),'$1\n$2 $3');
    s=s.replace(new RegExp('('+CJK+')(\\s*\\d+\\.)\\s*('+CJK+')','g'),'$1\n$2 $3');
    s=s.replace(new RegExp('('+CJK+')>(>)?('+CJK+')','g'),function(m,a,b,c){return a+'\n>'+(b||'')+c;});
    s=s.replace(new RegExp('('+CJK+'|[\\w\\.\\)\\]\\}!?,])(\\|)','g'),'$1\n$2');
    s=s.replace(new RegExp('('+CJK+'|[\\w\\.\\)\\]\\}!?,])(```)','g'),'$1\n$2');
    s=s.replace(new RegExp('('+CJK+')(---)','g'),'$1\n---');
    s=s.replace(/\n{3,}/g,'\n\n');
    result+=s;
  }
  return result;
}

marked.use({extensions:[mathBlock,mathInline],gfm:true,breaks:false,pedantic:false});

var processed=preprocessCJK(rawMd);
processed=preprocessFootnotes(processed);
document.getElementById('md-content').innerHTML=marked.parse(processed);

setTimeout(function(){
  if(typeof marked==='undefined'){
    document.getElementById('md-content').textContent=rawMd;
  }
},10000);
</script>
</body>
</html>"""
}
