package dev.jadan.fire;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodexExecutorClientTest {
    @Test public void acceptsOnlyHttpsOrigin() throws Exception {
        assertEquals("https://example.com",CodexExecutorClient.serverOrigin("https://example.com/"));
        assertEquals("https://example.com:8443",CodexExecutorClient.serverOrigin("https://example.com:8443"));
        for(String bad:new String[]{"http://example.com","https://user:pass@example.com",
            "https://example.com/path","https://example.com/?token=x","https://example.com/#frag",
            "file:///tmp/test","https://","https://example.com/redirect/../foo"}){
            try { CodexExecutorClient.serverOrigin(bad); fail("accepted "+bad); }
            catch(IllegalArgumentException|java.net.URISyntaxException expected){}
        }
    }
    @Test public void rejectsHeaderInjectionAndWeakTokens() {
        for(String bad:new String[]{"short","abc\\r\\nX-Sneaky","spaces are not allowed",
            "x".repeat(260)}){
            try {CodexExecutorClient.requireToken(bad);fail("accepted weak token");}
            catch(IllegalArgumentException expected){}
        }
        assertEquals("a".repeat(32),CodexExecutorClient.requireToken("a".repeat(32)));
    }
}
