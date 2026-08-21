package io.binarycodes.whichday.people.service;

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

    protected StoredAccount() {
    }

    StoredAccount(String email, String name) {
        this.email = email;
        this.name = name;
    }

    String email() {
        return email;
    }

    String name() {
        return name;
    }

    void rename(String newName) {
        this.name = newName;
    }
}
