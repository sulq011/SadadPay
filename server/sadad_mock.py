#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
sadad_mock.py — خدمة "سداد المعمل": دليل مفوترين + استعلام فواتير + تسوية سداد.
stdlib فقط. يعمل على VPS المعمل (منفذ 8095 افتراضيًا).

السلوك:
- GET  /api/billers                 -> قائمة المفوترين
- GET  /api/bill?biller=B001&no=..  -> بيانات فاتورة (مبلغ حتمي deterministic من رقم الفاتورة)
- POST /api/settle                  -> تسجيل السداد بعد موافقة السويتش (يُرجع رقم مرجعي SD-...)
- GET  /health                      -> {"status":"ok"}

الحالة تُحفظ في paid.json بجانب السكربت (تبقى بعد إعادة التشغيل).
"""
import hashlib
import json
import os
import random
import re
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(os.environ.get("SADAD_PORT", "8095"))
STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "paid.json")

# دليل المفوترين (أرقام تشبه النظام الحقيقي — أسماء واقعية، معمل فقط)
BILLERS = [
    {"id": "001", "name": "شركة الكهرباء السعودية (SEC)"},
    {"id": "002", "name": "شركة المياه الوطنية (NWC)"},
    {"id": "003", "name": "STC — اتصالات"},
    {"id": "004", "name": "موبايلي"},
    {"id": "005", "name": "زين السعودية"},
    {"id": "006", "name": "شركة الغاز"},
    {"id": "007", "name": "المخالفات المرورية (مرور)"},
    {"id": "008", "name": "الأحوال المدنية — خدمات"},
]
BILLER_BY_ID = {b["id"]: b for b in BILLERS}
FEE_MINOR = 100  # 1.00 ريال رسوم سداد

# ---------------------------------------------------------------- state
def load_state():
    try:
        with open(STATE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}

def save_state(state):
    try:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False, indent=1)
    except Exception:
        pass

PAID = load_state()

# ---------------------------------------------------------------- bill math
def bill_amount_minor(biller_id, bill_no):
    """مبلغ حتمي (deterministic) من (المفوتر + رقم الفاتورة) — نفس الرقم يعطي نفس الفاتورة."""
    seed = int(hashlib.sha1((biller_id + "|" + bill_no).encode()).hexdigest()[:8], 16)
    rng = random.Random(seed)
    return 5000 + rng.randrange(0, 95000)  # بين 50.00 و 999.50 ريال

def due_date(biller_id, bill_no):
    seed = int(hashlib.sha1(("due" + biller_id + bill_no).encode()).hexdigest()[:8], 16)
    rng = random.Random(seed)
    return date.today() + timedelta(days=3 + rng.randrange(0, 18))

def bill_json(biller_id, bill_no):
    if biller_id not in BILLER_BY_ID:
        return None, "رقم المفوتر غير معروف"
    if not re.fullmatch(r"[0-9]{6,15}", bill_no or ""):
        return None, "رقم الفاتورة يجب أن يكون من 6 إلى 15 رقمًا"
    key = biller_id + "|" + bill_no
    amount = bill_amount_minor(biller_id, bill_no)
    return {
        "biller": biller_id,
        "name": BILLER_BY_ID[biller_id]["name"],
        "bill_no": bill_no,
        "amount_minor": amount,
        "fee_minor": FEE_MINOR,
        "total_minor": amount + FEE_MINOR,
        "due_date": due_date(biller_id, bill_no).isoformat(),
        "status": "paid" if key in PAID else "unpaid",
    }, None

def settle(biller_id, bill_no, total_minor, auth_code, pan_suffix):
    key = biller_id + "|" + bill_no
    if key in PAID:
        return {"ok": False, "error": "الفاتورة مسددة مسبقًا", "ref": PAID[key]["ref"]}, 409
    b, err = bill_json(biller_id, bill_no)
    if b is None:
        return {"ok": False, "error": err}, 400
    if int(total_minor) != b["total_minor"]:
        return {"ok": False, "error": "المبلغ المدفوع لا يطابق الفاتورة"}, 400
    ref = "SD-{}-{:06d}".format(date.today().strftime("%Y%m%d"),
                                int(hashlib.sha1((key + auth_code).encode()).hexdigest()[:6], 16) % 1000000)
    rec = {"ref": ref, "auth_code": auth_code, "pan_suffix": pan_suffix,
           "total_minor": int(total_minor), "paid_at": date.today().isoformat()}
    PAID[key] = rec
    save_state(PAID)
    return {"ok": True, "ref": ref, "paid_at": rec["paid_at"]}, 200

# ---------------------------------------------------------------- http
class Handler(BaseHTTPRequestHandler):
    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path == "/health":
            return self._json({"status": "ok", "billers": len(BILLERS), "paid_count": len(PAID)})
        if u.path == "/api/billers":
            return self._json({"billers": BILLERS})
        if u.path == "/api/bill":
            q = parse_qs(u.query)
            b, err = bill_json(q.get("biller", [""])[0], q.get("no", [""])[0])
            if b is None:
                return self._json({"error": err}, 400)
            return self._json(b)
        return self._json({"error": "غير موجود"}, 404)

    def do_POST(self):
        u = urlparse(self.path)
        if u.path != "/api/settle":
            return self._json({"error": "غير موجود"}, 404)
        try:
            n = int(self.headers.get("Content-Length", "0"))
            req = json.loads(self.rfile.read(n).decode("utf-8"))
        except Exception:
            return self._json({"error": "طلب غير صالح"}, 400)
        obj, code = settle(
            str(req.get("biller", "")), str(req.get("bill_no", "")),
            req.get("total_minor", 0), str(req.get("auth_code", "")), str(req.get("pan_suffix", "")))
        return self._json(obj, code)

    def log_message(self, fmt, *args):
        print("[SADAD] " + (fmt % args), flush=True)

if __name__ == "__main__":
    print("[SADAD] sadad_mock on 0.0.0.0:{} — دليل المفوترين: {}".format(PORT, len(BILLERS)), flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
