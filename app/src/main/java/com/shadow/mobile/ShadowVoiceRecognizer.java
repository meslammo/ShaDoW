package com.shadow.mobile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/** MOD-29.19: Fix missing Intent import; keep voice recognition fully in-app. */
public final class ShadowVoiceRecognizer {
    public interface Listener { void onListening(); void onText(String text); void onError(String message); }
    private final Context context; private final Listener listener; private SpeechRecognizer recognizer; private boolean active; private String language="ar-EG";
    public ShadowVoiceRecognizer(Context context, Listener listener){this.context=context.getApplicationContext();this.listener=listener;}
    public boolean isAvailable(){return SpeechRecognizer.isRecognitionAvailable(context);}
    public boolean isOnDeviceAvailable(){return android.os.Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(context);}
    public void start(String language){this.language=language==null||language.isEmpty()?"ar-EG":language;if(!isAvailable()){listener.onError("خدمة التعرف على الصوت غير متاحة على الجهاز.");return;}stop();try{recognizer=(android.os.Build.VERSION.SDK_INT>=31&&isOnDeviceAvailable())?SpeechRecognizer.createOnDeviceSpeechRecognizer(context):SpeechRecognizer.createSpeechRecognizer(context);recognizer.setRecognitionListener(new RecognitionListener(){@Override public void onReadyForSpeech(Bundle params){active=true;listener.onListening();}@Override public void onBeginningOfSpeech(){}@Override public void onRmsChanged(float rmsdB){}@Override public void onBufferReceived(byte[] buffer){}@Override public void onEndOfSpeech(){active=false;}@Override public void onError(int error){active=false;listener.onError(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"مش سامع كلام واضح — قولها تاني.":"حصل خطأ في التعرف على الصوت ("+error+").");}@Override public void onResults(Bundle results){active=false;ArrayList<String> values=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(values!=null&&!values.isEmpty()&&values.get(0)!=null&&!values.get(0).trim().isEmpty())listener.onText(values.get(0).trim());else listener.onError("مش سامع كلام واضح — قولها تاني.");}@Override public void onPartialResults(Bundle partialResults){}@Override public void onEvent(int eventType,Bundle params){}});Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,this.language);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,this.language);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);recognizer.startListening(intent);}catch(Throwable t){active=false;stop();listener.onError("التعرف على الصوت غير متاح حالياً.");}}
    public void stop(){active=false;if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}try{recognizer.destroy();}catch(Exception ignored){}recognizer=null;}}
    public boolean isActive(){return active;}
}
