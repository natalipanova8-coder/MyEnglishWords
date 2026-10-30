package com.myenglishwords.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.webkit.*;
import android.widget.Toast;
import android.util.Base64;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private ValueCallback<Uri[]> uploadCallback;
    private static final int FILE_REQUEST = 101;
    private static final int BACKUP_REQUEST = 102;
    private String pendingBackup;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        tts = new TextToSpeech(this, status -> { if(status == TextToSpeech.SUCCESS) {tts.setLanguage(Locale.UK); tts.setSpeechRate(0.85f);} });
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                uploadCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                startActivityForResult(i, FILE_REQUEST);
                return true;
            }
        });
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void speak(String text) { runOnUiThread(() -> { if(tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word"); }); }
            @JavascriptInterface public void saveBackup(String json) { runOnUiThread(() -> {
                pendingBackup=json;
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE,"my-english-words-backup.json");
                startActivityForResult(i,BACKUP_REQUEST);
            }); }
        }, "AndroidApp");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req,result,data);
        if(req==FILE_REQUEST) {
            if(uploadCallback!=null) { uploadCallback.onReceiveValue(result==RESULT_OK && data!=null && data.getData()!=null ? new Uri[]{data.getData()} : null); uploadCallback=null; }
        } else if(req==BACKUP_REQUEST && result==RESULT_OK && data!=null && data.getData()!=null && pendingBackup!=null) {
            try(java.io.OutputStream os=getContentResolver().openOutputStream(data.getData())) {
                os.write(pendingBackup.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(this,"Словник збережено",Toast.LENGTH_SHORT).show();
            } catch(Exception e) { Toast.makeText(this,"Помилка збереження",Toast.LENGTH_LONG).show(); }
            pendingBackup=null;
        }
    }
    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { if(tts!=null) {tts.stop();tts.shutdown();} web.destroy(); super.onDestroy(); }
}
