package io.binarycodes.whichday.poll.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** One person's mutable answer. Package-private for the reason {@link StoredPoll} is. */
@Entity
@Table(name = "ballot")
@IdClass(StoredBallotKey.class)
class StoredBallot {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private StoredPoll poll;

    /** The voter is an address; the name comes from the account table on read. */
    @Id
    @Column(name = "voter_email", length = 320, nullable = false)
    private String voterEmail;

    @ElementCollection
    @CollectionTable(name = "ballot_day", joinColumns = {
            @JoinColumn(name = "poll_id", referencedColumnName = "poll_id"),
            @JoinColumn(name = "voter_email", referencedColumnName = "voter_email")})
    @Column(name = "chosen_day", nullable = false)
    @BatchSize(size = 50)
    private Set<LocalDate> chosenDays = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "ballot_proposal", joinColumns = {
            @JoinColumn(name = "poll_id", referencedColumnName = "poll_id"),
            @JoinColumn(name = "voter_email", referencedColumnName = "voter_email")})
    @OrderColumn(name = "ordinal")
    @Column(name = "proposed_day", nullable = false)
    @BatchSize(size = 50)
    private List<LocalDate> proposedDays = new ArrayList<>();

    protected StoredBallot() {
    }

    StoredBallot(StoredPoll poll, String voterEmail) {
        this.poll = poll;
        this.voterEmail = voterEmail;
    }

    String voterEmail() {
        return voterEmail;
    }

    /**
     * Answering again replaces the whole answer. Filled in place rather than by
     * building a new ballot: replacing the value in an orphan-removing map asks
     * Hibernate to delete and insert a row with the same key in one flush, and the
     * ordering of the two is not something to rely on.
     */
    void answer(Set<LocalDate> chosen, List<LocalDate> proposed) {
        chosenDays.clear();
        chosenDays.addAll(chosen);
        proposedDays.clear();
        proposedDays.addAll(proposed);
    }

    Set<LocalDate> chosenDays() {
        return chosenDays;
    }

    List<LocalDate> proposedDays() {
        return proposedDays;
    }
}
