package dev.jadan.fire;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ComputeInboxClientTest {
    @Test public void persistentSenderDoesNotRotate() {
        String name="device:fire-console-0123456789ab";
        assertEquals(name,ComputeInboxClient.senderId(name));
        assertTrue(ComputeInboxClient.senderId("").matches("device:fire-console-[a-f0-9]{12}"));
        assertNotEquals("device:other",
            ComputeInboxClient.senderId("device:other"));
    }

    @Test public void taskAddressAndReceiptAreIndependent() throws Exception {
        String sender="device:fire-console-0123456789ab";
        JSONObject task=ComputeInboxClient.makeRequest(sender,"Fire Console TV",
            "Investigate Snake movement",null);
        assertEquals(sender,task.getString("from"));
        assertEquals("agent:compute",task.getString("to"));
        assertEquals("request",task.getString("kind"));
        assertEquals("compute",task.getString("project"));
        assertTrue(task.getString("id").length()>=30);
        assertNotEquals(sender,task.getString("id"));
        assertEquals("Investigate Snake movement",task.getString("text"));
    }

    @Test public void rejectEmptyOrExcessivePublicTasks() throws Exception {
        for(String bad:new String[]{"","  ","x".repeat(6001)}) {
            try {
                ComputeInboxClient.makeRequest("device:fire-console-0123456789ab",
                    "Fire TV",bad,null);
                fail("Accepted invalid task");
            }catch(IllegalArgumentException expected){}
        }
    }

    @Test public void trackOnlyAnswersToThisTaskAndThisDevice() throws Exception {
        JSONObject source=new JSONObject();
        JSONArray entries=new JSONArray();
        entries.put(new JSONObject().put("id","my-request").put("from","device:fire-console-0123456789ab")
            .put("to","agent:compute").put("kind","request"));
        entries.put(new JSONObject().put("id","unrelated").put("from","agent:compute")
            .put("to","device:other").put("reply_to","my-request").put("created_at","2026-11-08T16:00:00Z"));
        entries.put(new JSONObject().put("id","valid-first").put("from","agent:compute")
            .put("to","device:fire-console-0123456789ab").put("reply_to","my-request")
            .put("kind","progress").put("text","Investigating")
            .put("created_at","2026-11-08T14:00:00Z"));
        entries.put(new JSONObject().put("id","valid-last").put("from","agent:compute")
            .put("to","device:fire-console-0123456789ab").put("reply_to","my-request")
            .put("kind","result").put("text","Commit ready")
            .put("created_at","2026-11-08T15:00:00Z"));
        source.put("messages",entries);
        assertTrue(ComputeInboxClient.savedRequest(source,
            "device:fire-console-0123456789ab","my-request"));
        assertFalse(ComputeInboxClient.savedRequest(source,"device:other","my-request"));
        assertEquals("valid-last",ComputeInboxClient.latestReply(source,
            "device:fire-console-0123456789ab","my-request").getString("id"));
        assertNull(ComputeInboxClient.latestReply(source,"device:other","my-request"));
    }
}
