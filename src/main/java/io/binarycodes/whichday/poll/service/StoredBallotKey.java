package io.binarycodes.whichday.poll.service;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The composite key behind {@link StoredBallot}: one answer per person per poll.
 *
 * <p>The one place in this package where §4's Lombok rule genuinely applies — JPA
 * demands a mutable bean with a no-arg constructor and with {@code equals}/
 * {@code hashCode}, and there is nothing else here to write. {@code poll} is typed as
 * the poll's own id, which is what the derived-identity rules ask for.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
class StoredBallotKey implements Serializable {

    private UUID poll;

    private String voterEmail;
}
