package io.binarycodes.whichday.people.service;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An account, which is an address somebody has signed in with and the name the
 * provider gave for it. Package-private and never returned: callers see the immutable
 * {@code Person} the directory builds from one.
 *
 * <p>This is the only place a name is stored. A poll names people by address, so a
 * name corrected here is corrected on every screen at once — see
 * {@code docs/REQUIREMENTS.md} §10.
 */
@Entity
@Table(name = "account")
class StoredAccount {

    @Id
    @Column(name = "email", length = 320, nullable = false)
    private String email;

    /** Empty rather than null for somebody who gave the provider no name. */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * First seen, and never rewritten — {@code remember} runs on every read of who is
     * looking, and a column that moved with it would keep an anonymous session's row
     * alive for as long as somebody left the tab open. The retention sweep reads this.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoredAccount() {
    }

    StoredAccount(String email, String name, Instant createdAt) {
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
    }

    String email() {
        return email;
    }

    String name() {
        return name;
    }

    Instant createdAt() {
        return createdAt;
    }

    void rename(String newName) {
        this.name = newName;
    }
}
