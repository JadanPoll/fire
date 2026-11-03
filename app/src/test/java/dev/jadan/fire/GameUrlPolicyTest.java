package dev.jadan.fire;

import org.junit.Test;
import static org.junit.Assert.*;

/** Representative good and malicious/share URLs for Fire Playroom pinning. */
public class GameUrlPolicyTest {
    @Test public void acceptsOnlyActualHttpsHostnames(){
        assertEquals("https://games.example.com/run",
            GameUrlPolicy.sanitize("https://games.example.com/run"));
        assertEquals("https://games.example.com/",
            GameUrlPolicy.sanitize(" HTTPS://games.example.com"));
        assertNull(GameUrlPolicy.sanitize("http://games.example.com"));
        assertNull(GameUrlPolicy.sanitize("javascript:alert(1)"));
        assertNull(GameUrlPolicy.sanitize("file:///etc/passwd"));
        assertNull(GameUrlPolicy.sanitize("/relative"));
        assertNull(GameUrlPolicy.sanitize("https://"));
    }

    @Test public void neverSavesTokensUserInfoOrTransientQuery(){
        assertEquals("https://games.example.com/play/runner",
            GameUrlPolicy.sanitize("https://games.example.com/play/runner?code=private-token&x=3#secret"));
        assertNull(GameUrlPolicy.sanitize("https://name:password@games.example.com/play"));
        assertEquals("https://games.example.com/",
            GameUrlPolicy.sanitize("https://games.example.com/#opaque"));
    }

    @Test public void keepsMeaningfulEncodedPathsWithoutDoubleEncoding(){
        assertEquals("https://games.example.com/a%20b",
            GameUrlPolicy.sanitize("https://games.example.com/a%20b?q=1"));
        assertEquals("https://games.example.com:8443/play",
            GameUrlPolicy.sanitize("https://games.example.com:8443/play"));
    }

    @Test public void cannotStoreUnboundedUrls(){
        assertNull(GameUrlPolicy.sanitize("https://games.example.com/"+"x".repeat(2100)));
        assertNull(GameUrlPolicy.sanitize(null));
    }
}
