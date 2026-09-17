package se.sundsvall.alkt.integration.operaton;

import java.util.List;
import se.sundsvall.alkt.service.model.AwaitingSignal;

/**
 * Where a process instance stands still, named by its phase rather than by the catch event. The signals are empty for a
 * wait state no case worker can answer.
 */
public record WaitState(String activityId, String activityName, List<AwaitingSignal> awaitingSignals) {}
