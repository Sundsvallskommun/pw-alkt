package se.sundsvall.alkt.integration.party;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class PartyIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private PartyClient partyClientMock;

	@InjectMocks
	private PartyIntegration partyIntegration;

	@Test
	void getLegalIdAnswersWithThePartysLegalId() {
		final var partyId = randomUUID().toString();
		when(partyClientMock.getLegalId(MUNICIPALITY_ID, partyId)).thenReturn(Optional.of("5566124144"));

		assertThat(partyIntegration.getLegalId(MUNICIPALITY_ID, partyId)).isEqualTo("5566124144");

		verify(partyClientMock).getLegalId(MUNICIPALITY_ID, partyId);
	}

	/** dismiss404 turns an unknown party into an empty answer, and there is nothing to carry on with. */
	@Test
	void getLegalIdFailsForAPartyThatIsNotKnown() {
		final var partyId = randomUUID().toString();
		when(partyClientMock.getLegalId(MUNICIPALITY_ID, partyId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> partyIntegration.getLegalId(MUNICIPALITY_ID, partyId))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(partyId)
			.hasMessageContaining("has no legal id");
	}

	@Test
	void getLegalIdLetsOtherFailuresThrough() {
		final var partyId = randomUUID().toString();
		when(partyClientMock.getLegalId(MUNICIPALITY_ID, partyId)).thenThrow(new ClientProblem(BAD_GATEWAY, "Party is down"));

		assertThatThrownBy(() -> partyIntegration.getLegalId(MUNICIPALITY_ID, partyId)).isInstanceOf(ClientProblem.class);
	}
}
