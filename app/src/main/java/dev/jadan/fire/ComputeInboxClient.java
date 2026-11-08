package dev.jadan.fire;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Public Compute requests are durable Inbox messages, NOT execution authority.
 * A permitted agent/paired Codex host is a separate capability.
 * Every request keeps a stable per-installation sender address and
 * an independent unique message ID for reconciliation.
 */
final class ComputeInboxClient {
    static final String BASE = "https://fire-inbox.vercel.app";
    static final String RECIPIENT = "agent:compute";
    static final String DEFAULT_NAME = "Fire Console TV";

    private ComputeInboxClient() {}

    static String senderId(String persisted) {
        if (persisted != null && persisted.matches("device:fire-console-[a-f0-9]{12}"))
            return persisted;
        return "device:fire-console-" +
            UUID.randomUUID().toString().replace("-", "").substring(0,12);
    }

    static JSONObject makeRequest(String from, String label, String text, String id) throws Exception {
        if (from==null || !from.matches("device:fire-console-[a-f0-9]{12}"))
            throw new IllegalArgumentException("Unrecognized Compute sender");
        if (id==null) id=UUID.randomUUID().toString();
        UUID.fromString(id);
        if(text==null || text.trim().isEmpty() || text.length()>6000)
            throw new IllegalArgumentException("Enter a public task of 1–6000 characters");
        JSONObject request = new JSONObject();
        request.put("schema_version",1);
        request.put("id",id);
        request.put("from",from);
        request.put("from_label",label==null?DEFAULT_NAME:label);
        request.put("to",RECIPIENT);
        request.put("kind","request");
        request.put("project","compute");
        request.put("text",text.trim());
        return request;
    }

    private static JSONObject request(String method, String path, JSONObject payload) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(BASE+path).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Cache-Control","no-store");
        if(payload!=null) {
            byte[] data=payload.toString().getBytes(StandardCharsets.UTF_8);
            if(data.length>9000)throw new IllegalArgumentException("Public task is too large");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            conn.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=conn.getOutputStream()){out.write(data);}
        }
        try {
            int code=conn.getResponseCode();
            InputStream input=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
            if(input==null)throw new Exception("Inbox HTTP "+code+" without response");
            try(InputStream stream=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[2048];int n;
                while((n=stream.read(b))!=-1) {
                    if(out.size()+n>96*1024)throw new Exception("Inbox reply too large");
                    out.write(b,0,n);
                }
                JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                if(code<200||code>=300||!json.optBoolean("ok",false))
                    throw new Exception("Inbox HTTP "+code+": "+json.optString("error","request failed"));
                return json;
            }
        } finally {conn.disconnect();}
    }

    static JSONObject submit(JSONObject payload) throws Exception {
        JSONObject result=request("POST","/api/messages",payload);
        if(!payload.optString("id").equals(result.optString("id")))
            throw new Exception("Inbox receipt ID mismatch. Reconcile before retrying");
        return result;
    }

    static JSONObject fetchRecent() throws Exception {
        return request("GET","/api/messages?limit=100",null);
    }

    static JSONObject latestReply(JSONObject history,String sender,String taskId) {
        JSONArray messages=history.optJSONArray("messages");
        JSONObject latest=null;
        if(messages==null)return null;
        for(int i=0;i<messages.length();i++) {
            JSONObject msg=messages.optJSONObject(i);
            if(msg==null)continue;
            if(!taskId.equals(msg.optString("reply_to")) ||
                    !sender.equals(msg.optString("to")))continue;
            if(latest==null || msg.optString("created_at")
                    .compareTo(latest.optString("created_at"))>0) latest=msg;
        }
        return latest;
    }

    static boolean savedRequest(JSONObject history,String sender,String requestId) {
        JSONArray messages=history.optJSONArray("messages");
        if(messages==null)return false;
        for(int i=0;i<messages.length();i++) {
            JSONObject msg=messages.optJSONObject(i);
            if(msg!=null && requestId.equals(msg.optString("id")) &&
               sender.equals(msg.optString("from")) &&
               RECIPIENT.equals(msg.optString("to")))return true;
        }
        return false;
    }
}
