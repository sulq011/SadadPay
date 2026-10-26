package com.floss83.sadad;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** عميل HTTP بسيط لخدمة سداد المعمل (sadad_mock على الخادم). */
public final class SadadApi {

    private SadadApi() {}

    /** GET/POST JSON — يرجع الاستجابة كـ JSONObject مع حقل _http = كود الحالة. */
    public static JSONObject json(String method, String url, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(9000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        if (in != null) {
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
        }
        JSONObject o;
        String text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        try {
            o = new JSONObject(text);
        } catch (Exception e) {
            o = new JSONObject();
            o.put("error", text.length() > 0 ? text : "رد غير مفهوم من الخادم");
        }
        o.put("_http", code);
        return o;
    }
}
