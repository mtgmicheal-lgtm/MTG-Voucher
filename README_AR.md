# MTG Voucher v2

نسخة جديدة بواجهة أنظف + Workflow يتحقق من الـAPK قبل رفعه.

بعد رفع الملفات إلى GitHub:
1. Actions
2. Build + Verify MTG Voucher APK
3. Run workflow
4. لازم الـRun يطلع أخضر
5. نزّل Artifact اسمه MTG-Voucher-v2-VERIFIED
6. جوّاه MTG-Voucher-v2.apk

الـWorkflow لن يعتبر البناء ناجحاً إلا لو:
- classes.dex موجود
- AndroidManifest.xml موجود
- توقيع APK صالح
- aapt قادر يقرأ بيانات الحزمة
