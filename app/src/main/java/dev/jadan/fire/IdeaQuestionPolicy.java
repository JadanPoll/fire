package dev.jadan.fire;

/**
 * Human-owned question formulation. The agent sees an explicit investigation
 * request, not a claim that generated suggestions are true or tested.
 */
final class IdeaQuestionPolicy {
    private IdeaQuestionPolicy() {}

    static String forAgent(String challengeTitle, String question) {
        if (question == null || question.trim().isEmpty())
            throw new IllegalArgumentException("Question must be non-empty");
        String q = question.trim();
        if (q.length() > 1500) throw new IllegalArgumentException("Question too long");
        return "Fire Console / Android Dojo — human question\n" +
            "Parent vision: What can't we do with this TV?\n" +
            "Originating challenge: " + (challengeTitle == null ? "Open exploration" : challengeTitle) + "\n" +
            "My question or idea: " + q + "\n\n" +
            "Please: (1) identify what mechanisms would make this possible, " +
            "(2) offer at least one surprising alternative, " +
            "(3) distinguish known results from hypotheses, " +
            "(4) propose the cheapest informative experiment or counterexample, " +
            "(5) describe what new possibilities I might be overlooking. " +
            "Do not treat my question as a command to install or execute anything.";
    }

    static String reflection(String question) {
        return "What did I think was possible before? What observation changed my model? " +
            "What new idea or sharper question does this suggest about: " + question;
    }
}
