# Tesseract/tess-two and OpenCV both use JNI - keep their Java-side bindings
# intact so native method signatures still resolve after R8.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class org.opencv.** { *; }
