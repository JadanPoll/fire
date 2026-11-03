package dev.jadan.fire;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Explicitly paired HTTPS client; credentials stay in UI memory, never the APK. */
final class CodexExecutorClient {
    private CodexExecutorClient(){}
    static String serverOrigin(String input) throws Exception {
        URI uri = new URI(input.trim()).parseServerAuthority();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
            uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null ||
            (uri.getRawPath() != null && !uri.getRawPath().isEmpty() &&
             !"/".equals(uri.getRawPath())))
            throw new IllegalArgumentException("Enter only a trusted https://host[:port] address (no path, query or credentials).");
        if (uri.getPort() < -1 || uri.getPort() > 65535)
            throw new IllegalArgumentException("Invalid port");
        return uri.getScheme().toLowerCase() + "://" +
            (uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost()) +
            (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }
    static String requireToken(String input) {
        String value=input.trim();
        if (!value.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalArgumentException("Enter your executor pairing token (32–256 URL-safe characters).");
        return value;
    }
    static JSONObject submit(String origin, String token, String prompt) throws Exception {
        if (prompt.trim().isEmpty() || prompt.length() > 8000)
            throw new IllegalArgumentException("Prompt must contain 1–8000 characters.");
        JSONObject body=new JSONObject();
        body.put("prompt",prompt);
        return request(origin,token,"POST","/v1/jobs",body);
    }
    static JSONObject probe(String origin,String token) throws Exception {
        return request(origin,token,"GET","/v1/health",null);
    }
    static JSONObject recover(String origin,String token) throws Exception {
        return request(origin,token,"GET","/v1/jobs/latest",null);
    }
    static JSONObject poll(String origin,String token,String id) throws Exception {
        return request(origin,token,"GET","/v1/jobs/"+UUID.fromString(id).toString(),null);
    }
    static JSONObject cancel(String origin,String token,String id) throws Exception {
        return request(origin,token,"DELETE","/v1/jobs/"+UUID.fromString(id).toString(),null);
    }
    private static JSONObject request(String origin,String token,String method,String path,JSONObject body) throws Exception {
        String host=serverOrigin(origin);
        String credential=requireToken(token);
        HttpURLConnection connection=(HttpURLConnection)new URL(host+path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization","Bearer "+credential);
        connection.setRequestProperty("Accept","application/json");
        if(body!=null){
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream stream=connection.getOutputStream()){stream.write(bytes);}
        }
        try{
            int code=connection.getResponseCode();
            InputStream stream=code>=200&&code<300 ? connection.getInputStream():connection.getErrorStream();
            if(stream==null)throw new Exception("Executor HTTP "+code+" (no response)");
            byte[] data;
            try(InputStream src=stream;ByteArrayOutputStream dst=new ByteArrayOutputStream()){
                byte[] buf=new byte[2048];int n;
                while((n=src.read(buf))!=-1){
                    if(dst.size()+n>96*1024)throw new Exception("Executor response exceeds limit");
                    dst.write(buf,0,n);
                }
                data=dst.toByteArray();
            }
            JSONObject json=new JSONObject(new String(data,StandardCharsets.UTF_8));
            if(code<200||code>=300)throw new Exception("Executor HTTP "+code+": "+json.optString("error","Request rejected"));
            return json;
        }finally{connection.disconnect();}
    }
}
