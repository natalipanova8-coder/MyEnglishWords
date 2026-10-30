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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.cyrillic.CyrillicTextRecognizerOptions;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.common.model.DownloadConditions;
import org.json.JSONArray;
import org.json.JSONObject;


public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private ValueCallback<Uri[]> uploadCallback;
    private static final int FILE_REQUEST = 101;
    private static final int BACKUP_REQUEST = 102;
    private static final int IMAGE_REQUEST = 103;
    private Translator translator;
    private String pendingBackup;
    private String pendingName = "my-english-words-backup.json";
    private String pendingMime = "application/json";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        tts = new TextToSpeech(this, status -> { if(status == TextToSpeech.SUCCESS) {tts.setLanguage(Locale.UK); tts.setSpeechRate(0.85f);} });
        TranslatorOptions opts = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.UKRAINIAN).build();
        translator = Translation.getClient(opts);
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
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"});
                startActivityForResult(i, FILE_REQUEST);
                return true;
            }
        });
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void chooseImage() { runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, IMAGE_REQUEST);
            }); }
            @JavascriptInterface public void translateWords(String json) { runOnUiThread(() -> translateBatch(json)); }
            @JavascriptInterface public void speak(String text) { runOnUiThread(() -> { if(tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word"); }); }
            @JavascriptInterface public void saveFile(String filename, String mime, String content) { runOnUiThread(() -> {
                pendingBackup=content; pendingName=filename; pendingMime=mime;
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE,filename);
                startActivityForResult(i,BACKUP_REQUEST);
            }); }
            @JavascriptInterface public void saveBackup(String json) { runOnUiThread(() -> {
                pendingBackup=json; pendingName="my-english-words-backup.json"; pendingMime="application/json";
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(pendingMime);
                i.putExtra(Intent.EXTRA_TITLE,pendingName);
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
        } else if(req==IMAGE_REQUEST && result==RESULT_OK && data!=null && data.getData()!=null) {
            scanImage(data.getData());
        } else if(req==BACKUP_REQUEST && result==RESULT_OK && data!=null && data.getData()!=null && pendingBackup!=null) {
            try(java.io.OutputStream os=getContentResolver().openOutputStream(data.getData())) {
                os.write(pendingBackup.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(this,"Словник збережено",Toast.LENGTH_SHORT).show();
            } catch(Exception e) { Toast.makeText(this,"Помилка збереження",Toast.LENGTH_LONG).show(); }
            pendingBackup=null;
        }
    }
    private void jsCall(String name, String arg) {
        runOnUiThread(() -> web.evaluateJavascript(name + "(" + JSONObject.quote(arg) + ")", null));
    }
    
private void scanImage(Uri uri) {
    jsCall("ocrStatus", "Розпізнаю текст на зображенні...");
    try {
        InputImage image = InputImage.fromFilePath(this, uri);

        com.google.mlkit.vision.text.TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
            .addOnSuccessListener(result -> {
                jsCall("ocrResult", result.getText());
            })
            .addOnFailureListener(e -> {
                jsCall("ocrError", "Не вдалося прочитати зображення: " + e.getMessage());
            })
            .addOnCompleteListener(task -> recognizer.close());

    } catch (Exception e) {
        jsCall("ocrError", "Не вдалося прочитати зображення: " + e.getMessage());
    }
}
    private void translateBatch(String json) {
        try {
            JSONArray words = new JSONArray(json);
            if(words.length()>100) {jsCall("ocrError", "За один раз можна перекласти до 100 рядків");return;}
            jsCall("ocrStatus", "Завантажую модель перекладу (перший раз потрібен інтернет)…");
            translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    JSONArray output = new JSONArray();
                    translateNext(words, 0, output);
                }).addOnFailureListener(e -> jsCall("ocrError", "Модель перекладу недоступна. Перевір інтернет і спробуй ще раз."));
        } catch(Exception e) {jsCall("ocrError", "Помилка списку слів");}
    }
    private void translateNext(JSONArray input, int i, JSONArray output) {
        if(i>=input.length()) {jsCall("ocrTranslations", output.toString());return;}
        String word = input.optString(i, "").trim();
        if(word.isEmpty()) {output.put("");translateNext(input,i+1,output);return;}
        jsCall("ocrStatus", "Переклад: " + (i+1) + " / " + input.length());
        translator.translate(word)
            .addOnSuccessListener(t -> {output.put(t);translateNext(input,i+1,output);})
            .addOnFailureListener(e -> {output.put("");translateNext(input,i+1,output);});
    }
    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { if(tts!=null) {tts.stop();tts.shutdown();} if(translator!=null)translator.close(); web.destroy(); super.onDestroy(); }
}
