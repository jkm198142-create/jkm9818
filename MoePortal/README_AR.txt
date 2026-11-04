تطبيق البوابة الإلكترونية الموحدة (أندرويد)
==========================================
1) ثبّت Android Studio (مجاني) من developer.android.com/studio
2) File > Open ثم اختر مجلد MoePortal هذا. انتظر حتى ينتهي Gradle من التحميل (يحتاج إنترنت أول مرة).
3) لبناء ملف التثبيت: Build > Build Bundle(s) / APK(s) > Build APK(s)
4) الملف الناتج:  app/build/outputs/apk/debug/app-debug.apk
5) انقله للهاتف وثبّته (فعّل "التثبيت من مصادر غير معروفة" عند الطلب).

- الحد الأدنى: أندرويد 10 (API 29).
- رابط البوابة محفوظ داخل MainActivity.kt (PORTAL_URL) وفي assets/offline.html؛ غيّره في المكانين إذا تغيّر الرابط.
- أي تحديث تنشره على Apps Script يظهر تلقائيًا في التطبيق.

بدون Android Studio (البناء السحابي عبر GitHub):
1) أنشئ حسابًا مجانيًا على github.com ثم مستودعًا جديدًا (New repository).
2) ارفع محتويات هذا المجلد كلها (Add file > Upload files) بما فيها مجلد .github
3) افتح تبويب Actions > Build APK > Run workflow.
4) بعد دقائق ادخل على التشغيل المكتمل ونزّل portal-apk من قسم Artifacts، ففيه app-debug.apk.
