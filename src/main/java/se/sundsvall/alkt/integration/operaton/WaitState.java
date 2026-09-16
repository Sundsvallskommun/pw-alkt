package se.sundsvall.alkt.integration.operaton;

import java.util.List;
import se.sundsvall.alkt.service.model.AwaitingSignal;

/**
 * Where a process instance stands still: the phase rather than the catch event, and the signals a case worker can send
 * to move it on. The list is empty for an automatic wait state.
 */
public record WaitState(String activityId, String activityName, List<AwaitingSignal> awaitingSignals) {}
