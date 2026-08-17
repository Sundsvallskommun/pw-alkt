package se.sundsvall.alkt.integration.operaton.deployment;

public class DeploymentException extends RuntimeException {
	private static final long serialVersionUID = -1616889424590166876L;

	public DeploymentException(Exception e) {
		super(e);
	}
}
