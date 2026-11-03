package dev.jadan.fire;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * User-confirmed game bookmark policy, no Android dependencies so it can be
 * checked by JVM tests before publishing to Fire TV.
 *
 * We do NOT persist query parameters/fragment, which may contain session
 * tokens and OAuth state. A website that requires a query must be reopened
 * manually in Browser instead of silently storing private credentials.
 */
final class GameUrlPolicy {
    private GameUrlPolicy(){}

    static String sanitize(String raw){
        if(raw==null || raw.length()>2048)return null;
        try {
            URI u=new URI(raw.trim());
            if(!"https".equalsIgnoreCase(u.getScheme()) ||
                u.getHost()==null || u.getHost().isEmpty() ||
                u.getRawUserInfo()!=null || u.getRawAuthority()==null ||
                u.getPort()>65535)
                return null;
            String path=u.getRawPath();
            if(path==null || path.isEmpty())path="/";
            if(!path.startsWith("/"))return null;
            return "https://"+u.getRawAuthority()+path;
        }catch(URISyntaxException | IllegalArgumentException ex){
            return null;
        }
    }
}
