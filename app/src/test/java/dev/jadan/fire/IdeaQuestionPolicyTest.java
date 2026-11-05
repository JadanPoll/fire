package dev.jadan.fire;

import org.junit.Test;
import static org.junit.Assert.*;

public class IdeaQuestionPolicyTest {
    @Test public void asksForPossibilityAndFalsifyingEvidence() {
        String q = IdeaQuestionPolicy.forAgent("Why does OK click twice?",
            "Could we normalize control intent?");
        assertTrue(q.contains("What can't we do with this TV?"));
        assertTrue(q.contains("normalize control intent"));
        assertTrue(q.contains("counterexample"));
        assertTrue(q.contains("not treat my question as a command to install"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void emptyIdeaIsRejected() {
        IdeaQuestionPolicy.forAgent("Experiment", "   ");
    }

    @Test public void reflectionIsNotACompletionScore() {
        assertTrue(IdeaQuestionPolicy.reflection("Snake input").contains("changed my model"));
    }
}
