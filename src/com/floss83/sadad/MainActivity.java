package com.floss83.sadad;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * سداد Pay — تطبيق فواتير سداد المعمل:
 * 1) اختر المفوتر + أدخل رقم الفاتورة → استعلام من خدمة سداد على الخادم.
 * 2) ادفع الفاتورة ببطاقات المعمل → رسالة ISO 8583 0200 حقيقية عبر السويتش.
 * 3) عند الموافقة (F39=00/01) تُسجَّل التسوية عند المفوتر → إيصال برقم مرجعي SD-...
 */
public class MainActivity extends Activity {

    private CardStore store;
    private List<Card> cards;

    private EditText apiEdit;
    private EditText switchEdit;
    private EditText billNoEdit;
    private Spinner billerSpinner;
    private Spinner cardSpinner;
    private Button lookupBtn;
    private Button payBtn;
    private LinearLayout billBox;
    private LinearLayout resultBox;
    private TextView msgView;

    /** قائمة المفوترين {id, name} — من الخادم أو احتياطيًا مدمجة. */
    private List<String[]> billers = new ArrayList<String[]>();
    private JSONObject currentBill = null;

    private static final String[][] FALLBACK_BILLERS = {
            {"001", "شركة الكهرباء السعودية (SEC)"},
            {"002", "شركة المياه الوطنية (NWC)"},
            {"003", "STC — اتصالات"},
            {"004", "موبايلي"},
            {"005", "زين السعودية"},
            {"006", "شركة الغاز"},
            {"007", "المخالفات المرورية (مرور)"},
            {"008", "الأحوال المدنية — خدمات"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        store = new CardStore(this);
        cards = store.load();
        if (store.getSelectedPan() == null && !cards.isEmpty()) {
            store.setSelectedPan(cards.get(0).pan);
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(box);
        setContentView(scroll);
        setTitle("سداد Pay — معمل Flossx83");

        TextView title = new TextView(this);
        title.setText("🧾 سداد Pay");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(4));
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("سداد فواتير المفوترين ببطاقات المعمل — دفع ISO 8583 حقيقي عبر السويتش");
        sub.setTextSize(13);
        sub.setTextColor(Color.GRAY);
        sub.setPadding(0, 0, 0, dp(12));
        box.addView(sub);

        // ---------- إعدادات الخوادم ----------
        TextView srvTitle = new TextView(this);
        srvTitle.setText("الخوادم");
        srvTitle.setTextSize(16);
        srvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        srvTitle.setPadding(0, dp(8), 0, dp(6));
        box.addView(srvTitle);

        apiEdit = new EditText(this);
        apiEdit.setHint("خادم سداد (host:port)");
        apiEdit.setText(store.getApi());
        apiEdit.setTextSize(13);
        box.addView(apiEdit);

        switchEdit = new EditText(this);
        switchEdit.setHint("خادم السويتش (host:port)");
        switchEdit.setText(store.getHost());
        switchEdit.setTextSize(13);
        box.addView(switchEdit);

        // ---------- الاستعلام ----------
        TextView lookupTitle = new TextView(this);
        lookupTitle.setText("الاستعلام عن فاتورة");
        lookupTitle.setTextSize(18);
        lookupTitle.setTypeface(Typeface.DEFAULT_BOLD);
        lookupTitle.setPadding(0, dp(20), 0, dp(8));
        box.addView(lookupTitle);

        billerSpinner = new Spinner(this);
        box.addView(billerSpinner);

        Button refreshBillers = new Button(this);
        refreshBillers.setText("🔄 تحديث قائمة المفوترين");
        refreshBillers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadBillers(true);
            }
        });
        box.addView(refreshBillers);

        billNoEdit = new EditText(this);
        billNoEdit.setHint("رقم الفاتورة / رقم الحساب (6-15 رقمًا)");
        billNoEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(billNoEdit);

        lookupBtn = new Button(this);
        lookupBtn.setText("🔍 استعلام");
        lookupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lookupBill();
            }
        });
        box.addView(lookupBtn);

        // ---------- تفاصيل الفاتورة ----------
        billBox = new LinearLayout(this);
        billBox.setOrientation(LinearLayout.VERTICAL);
        billBox.setVisibility(View.GONE);
        box.addView(billBox);

        // ---------- الدفع ----------
        TextView payTitle = new TextView(this);
        payTitle.setText("سداد الفاتورة");
        payTitle.setTextSize(18);
        payTitle.setTypeface(Typeface.DEFAULT_BOLD);
        payTitle.setPadding(0, dp(20), 0, dp(8));
        payTitle.setVisibility(View.GONE);
        box.addView(payTitle);

        cardSpinner = new Spinner(this);
        cardSpinner.setVisibility(View.GONE);
        box.addView(cardSpinner);

        payBtn = new Button(this);
        payBtn.setText("✅ سداد الآن");
        payBtn.setTextSize(16);
        payBtn.setVisibility(View.GONE);
        payBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payBill();
            }
        });
        box.addView(payBtn);

        // ---------- النتيجة ----------
        msgView = new TextView(this);
        msgView.setTextSize(13);
        msgView.setPadding(0, dp(12), 0, 0);
        box.addView(msgView);

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(resultBox);

        loadCards();
        loadBillers(false);
    }

    // ================================================================ بطاقات

    private void loadCards() {
        List<String> labels = new ArrayList<String>();
        String sel = store.getSelectedPan();
        int selIdx = 0;
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            labels.add(Util.mask(c.pan) + "  —  رصيد " + Util.formatAmount(c.balanceMinor) + " ريال");
            if (c.pan.equals(sel)) selIdx = i;
        }
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cardSpinner.setAdapter(ad);
        cardSpinner.setSelection(Math.min(selIdx, labels.size() - 1));
    }

    private Card selectedCard() {
        int pos = cardSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= cards.size()) return null;
        return cards.get(pos);
    }

    // ================================================================ مفطورون

    private void loadBillers(final boolean force) {
        billers.clear();
        for (String[] b : FALLBACK_BILLERS) billers.add(b);
        renderBillers();
        if (!force) return;
        msg("⏳ جاري تحديث قائمة المفوترين من الخادم…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject r = SadadApi.json("GET", baseUrl() + "/api/billers", null);
                    final JSONArray arr = r.optJSONArray("billers");
                    if (r.optInt("_http") == 200 && arr != null && arr.length() > 0) {
                        final List<String[]> fresh = new ArrayList<String[]>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject b = arr.getJSONObject(i);
                            fresh.add(new String[]{b.optString("id"), b.optString("name")});
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                billers.clear();
                                billers.addAll(fresh);
                                renderBillers();
                                msg("✅ تم تحديث " + fresh.size() + " مفوتر من الخادم");
                            }
                        });
                    } else {
                        msgUi("⚠️ تعذر جلب القائمة من الخادم — استُخدمت القائمة المدمجة");
                    }
                } catch (Exception e) {
                    msgUi("⚠️ خادم سداد غير متاح: " + e.getMessage() + " — القائمة المدمجة فعّالة");
                }
            }
        }).start();
    }

    private void renderBillers() {
        List<String> names = new ArrayList<String>();
        for (String[] b : billers) names.add(b[1]);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        billerSpinner.setAdapter(ad);
    }

    // ================================================================ استعلام

    private void lookupBill() {
        final String[] biller = billers.get(Math.max(0, billerSpinner.getSelectedItemPosition()));
        final String no = billNoEdit.getText().toString().trim();
        if (no.length() < 6) {
            msg("⚠️ أدخل رقم فاتورة صحيحًا (6-15 رقمًا)");
            return;
        }
        lookupBtn.setEnabled(false);
        payBtn.setVisibility(View.GONE);
        cardSpinner.setVisibility(View.GONE);
        resultBox.removeAllViews();
        billBox.setVisibility(View.GONE);
        msg("⏳ جاري الاستعلام عن الفاتورة…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject r = SadadApi.json("GET",
                            baseUrl() + "/api/bill?biller=" + biller[0] + "&no=" + no, null);
                    final int http = r.optInt("_http");
                    if (http == 200) {
                        currentBill = r;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                renderBill(r);
                                msg("✅ وصلت بيانات الفاتورة من " + r.optString("name"));
                            }
                        });
                    } else {
                        currentBill = null;
                        msgUi("❌ " + r.optString("error", "تعذر الاستعلام (" + http + ")"));
                    }
                } catch (Exception e) {
                    currentBill = null;
                    msgUi("❌ خادم سداد غير متاح: " + e.getMessage());
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            lookupBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void renderBill(JSONObject b) {
        billBox.removeAllViews();
        billBox.setVisibility(View.VISIBLE);

        if ("paid".equals(b.optString("status"))) {
            TextView paid = row("✔️ هذه الفاتورة مسددة مسبقًا");
            paid.setTextColor(Color.rgb(13, 128, 23));
            billBox.addView(paid);
        }

        billBox.addView(row("المفوتر:  " + b.optString("name")));
        billBox.addView(row("رقم الفاتورة:  " + b.optString("bill_no")));
        billBox.addView(row("مبلغ الفاتورة:  " + Util.formatAmount(b.optLong("amount_minor")) + " ريال"));
        billBox.addView(row("رسوم السداد:  " + Util.formatAmount(b.optLong("fee_minor")) + " ريال"));
        TextView total = row("الإجمالي المطلوب:  " + Util.formatAmount(b.optLong("total_minor")) + " ريال");
        total.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD);
        total.setTextColor(Color.rgb(13, 128, 23));
        billBox.addView(total);
        billBox.addView(row("تاريخ الاستحقاق:  " + b.optString("due_date")));

        cardSpinner.setVisibility(View.VISIBLE);
        payBtn.setVisibility("paid".equals(b.optString("status")) ? View.GONE : View.VISIBLE);
    }

    private TextView row(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    // ================================================================ سداد

    private void payBill() {
        final Card card = selectedCard();
        if (card == null || currentBill == null) {
            msg("⚠️ لا توجد فاتورة أو بطاقة");
            return;
        }
        final long totalMinor = currentBill.optLong("total_minor");
        payBtn.setEnabled(false);
        resultBox.removeAllViews();
        msg("⏳ جاري السداد: " + Util.formatAmount(totalMinor) + " ريال من " + Util.mask(card.pan) + " …");

        new Thread(new Runnable() {
            @Override
            public void run() {
                String rc = null, ac = null, stan = null;
                try {
                    String[] hp = switchEdit.getText().toString().trim().split(":");
                    stan = String.format(Locale.US, "%06d",
                            (System.currentTimeMillis() / 1000) % 1000000);
                    String msg0200 = Iso8583.build0200(card.pan, card.cvv, totalMinor, stan);
                    Iso8583.PayResult r = Iso8583.send(hp[0], Integer.parseInt(hp[1].trim()), msg0200);
                    rc = r.responseCode;
                    ac = r.authCode;

                    if (r.approved) {
                        store.adjustBalance(cards, card.pan, -totalMinor);
                        store.markKnown(card.pan);
                        // تسجيل السداد عند المفوتر
                        JSONObject settleReq = new JSONObject();
                        settleReq.put("biller", currentBill.optString("biller"));
                        settleReq.put("bill_no", currentBill.optString("bill_no"));
                        settleReq.put("total_minor", totalMinor);
                        settleReq.put("auth_code", ac == null ? "" : ac);
                        settleReq.put("pan_suffix", card.pan.substring(card.pan.length() - 4));
                        JSONObject s = SadadApi.json("POST", baseUrl() + "/api/settle", settleReq);

                        final JSONObject settleResp = s;
                        final String fRc = rc, fAc = (ac == null || ac.isEmpty()) ? "000000" : ac;
                        final String fStan = stan;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (settleResp.optBoolean("ok")) {
                                    renderReceipt(settleResp.optString("ref"), fRc, fAc, fStan, card, totalMinor);
                                } else {
                                    msg("⚠️ الدفع مُوافَق عليه (" + fRc + "/" + fAc + ") لكن تسجيل السداد فشل: "
                                            + settleResp.optString("error", "خطأ غير معروف"));
                                }
                                loadCards();
                            }
                        });
                    } else {
                        final String fRc = rc == null ? "91" : rc;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                renderDecline(fRc);
                            }
                        });
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            renderDecline("91");
                            msg("❌ تعذر الوصول للسويتش: " + e.getMessage());
                        }
                    });
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            payBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void renderReceipt(String ref, String rc, String ac, String stan, Card card, long totalMinor) {
        resultBox.removeAllViews();
        resultBox.setPadding(0, dp(16), 0, 0);

        TextView ok = new TextView(this);
        ok.setText("✅ تم سداد الفاتورة بنجاح");
        ok.setTextSize(20);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setTextColor(Color.rgb(13, 128, 23));
        resultBox.addView(ok);

        resultBox.addView(row("الرقم المرجعي (سداد):  " + ref));
        resultBox.addView(row("كود التفويض:  " + ac + "   (F39=" + rc + ")"));
        resultBox.addView(row("رقم العملية STAN:  " + stan));
        resultBox.addView(row("المبلغ المسدد:  " + Util.formatAmount(totalMinor) + " ريال"));
        resultBox.addView(row("البطاقة:  " + Util.mask(card.pan) + " — الرصيد الجديد: "
                + Util.formatAmount(card.balanceMinor) + " ريال"));
        resultBox.addView(row("التاريخ:  " + new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm",
                java.util.Locale.US).format(new java.util.Date())));

        msg("🎉 سداد مكتمل — الفاتورة أصبحت مسددة لدى المفوتر");
    }

    private void renderDecline(String rc) {
        resultBox.removeAllViews();
        resultBox.setPadding(0, dp(16), 0, 0);
        TextView no = new TextView(this);
        no.setText("❌ لم تكتمل عملية السداد");
        no.setTextSize(18);
        no.setTypeface(Typeface.DEFAULT_BOLD);
        no.setTextColor(Color.rgb(183, 28, 28));
        resultBox.addView(no);
        resultBox.addView(row("كود الاستجابة:  " + rc + " — " + Iso8583.describe(rc)));
    }

    // ================================================================ أدوات

    private String baseUrl() {
        String a = apiEdit.getText().toString().trim();
        if (a.isEmpty()) a = store.getApi();
        if (!a.startsWith("http")) a = "http://" + a;
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        store.setApi(a);
        String h = switchEdit.getText().toString().trim();
        if (!h.isEmpty()) store.setHost(h);
        return a;
    }

    private void msg(final String text) {
        msgView.setText(text);
    }

    private void msgUi(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msg(text);
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
