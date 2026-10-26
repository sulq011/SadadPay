# سداد Pay — SadadPay (Flossx83 Lab)

تطبيق فواتير **سداد** للمعمل: استعلام فاتورة من دليل مفوترين (خدمة sadad_mock على الخادم)، ثم سدادها ببطاقات المعمل عبر رسالة **ISO 8583 0200** حقيقية إلى سويتش Flossx83 (TCP 5000)، وعند الموافقة (F39=00/01) تُسجَّل التسوية لدى المفوتر ويصدر إيصال برقم مرجعي `SD-...`.

## المكونات
- `src/` — تطبيق أندرويد (بدون Gradle): `MainActivity` واجهة السداد، `Iso8583` عميل السويتش، `SadadApi` عميل خدمة الفواتير.
- `server/sadad_mock.py` — خدمة المفوترين والفواتير (Python stdlib، منفذ 8095).
- `build.sh` — بناء APK (aapt2 + javac + d8 + apksigner).
- `.github/workflows/android.yml` — بناء تلقائي + إصدار release عند كل push لـ main.

## الإعدادات الافتراضية
- خادم سداد: `sull.world:8095`
- سويتش الدفع: `sull.world:5000`

## تشغيل الخدمة الخادمية
```bash
SADAD_PORT=8095 nohup python3 server/sadad_mock.py > sadad.log 2>&1 &
```

## بطاقات المعمل
نفس بطاقات FlossPay — مثال: `4000001234567899` / CVV `123`.
