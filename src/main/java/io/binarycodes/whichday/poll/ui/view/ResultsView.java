package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.time.Duration;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.LiveBadge;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.ui.AvatarStack;
import io.binarycodes.whichday.people.ui.NameChips;
import io.binarycodes.whichday.people.ui.WaitingChip;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.component.AwaitingDayList;
import io.binarycodes.whichday.poll.ui.component.TallyList;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;
import io.binarycodes.whichday.poll.ui.share.VotingLink;

/**
 * The organizer watches the counts. Two moments, one screen: before anybody has
 * answered the rows are still there with nothing in them, and after, they carry the
 * paint. Same layout either way, so the poll does not appear to change shape when
 * the first answer lands.
 */
@PermitAll
@Route("poll/:id")
public class ResultsView extends PollScreen {

    private static final int VISIBLE_WAITING = 4;

    /**
     * How many answers the header names before the rest become a count. Fewer than the
     * ballot's six: this row sits beside the headline figure and has less to give.
     */
    private static final int ANSWERED_NAMES = 4;

    public ResultsView(PollPresenter presenter) {
        super(presenter);
    }

    /** Once a date is locked there are no standings to watch, only the date. */
    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (poll.lockedDay() == null) {
            return false;
        }
        forwardToPoll(event, LockedView.class);
        return true;
    }

    @Override
    protected void build(Poll poll) {
        // Before the branch, so the note lands above whatever the footer ends with — the
        // same order the share screen draws, where the hint sits over the action.
        closingNote(poll).ifPresent(this::footer);
        if (poll.isUnanswered()) {
            buildUnanswered(poll);
        } else {
            buildStandings(poll);
        }
    }

    /**
     * When voting closes, and the way to move it — for the organizer, on the screen they
     * actually come back to.
     *
     * <p>The promise is made on the share screen ("You can extend it later") and the
     * control has always lived there too, which left it unreachable: an organizer coming
     * back later follows the poll's own link and lands here, and nothing on this screen
     * went to the share screen. So the sentence was true of the application and false of
     * everybody's experience of it.
     *
     * <p>The same sentence rather than a second copy of it, because it is the same promise
     * and two copies would drift. The action navigates rather than revealing a calendar
     * here: the picker belongs to one screen, and its label says where it goes.
     */
    private Optional<HintBar> closingNote(Poll poll) {
        if (!presenter.isOrganizer(poll) || !poll.isEditable() || poll.closesOn() == null) {
            return Optional.empty();
        }
        return Optional.of(new HintBar(VaadinIcon.CLOCK,
                getTranslation("share.closes", DateText.closing(this, poll.closesOn())))
                .withAction(Actions.inline(getTranslation("results.closing.change"),
                        ignored -> goTo(ShareView.class))));
    }

    private void buildUnanswered(Poll poll) {
        body(new TopBar(poll.title()).leadingTitle()
                .withLeading(homeButton())
                .withTrailing(Typography.meta(getTranslation("results.sentAgo", sentAgo(poll)))));

        var count = Typography.stat(Counts.progress(this, 0, poll.inviteCount(), presenter.anonymous()));
        count.addClassName("stat-empty");
        var caption = Typography.meta(getTranslation("results.haveVoted"));
        var block = new Div(count, new Div(caption));
        block.addClassNames("stack-s", "push-2xl");
        body(block);

        var days = new AwaitingDayList(poll.candidateDays());
        days.addClassName("push-xl");
        body(days);

        if (!presenter.anonymous()) {
            body(waitingSection(poll));
        }
        // A poll nobody has answered includes the organizer, and this is where they
        // most need the way in.
        body(ownAnswer(poll));

        footer(shareLink(poll));
    }

    private void buildStandings(Poll poll) {
        body(new TopBar(poll.title()).leadingTitle()
                .withLeading(homeButton())
                .withTrailing(poll.isClosed()
                        ? Typography.meta(getTranslation("results.closed",
                                DateText.closing(this, poll.closesOn())))
                        : new LiveBadge(getTranslation("results.live"))));

        var count = Typography.stat(Counts.progress(this, poll.answerCount(), poll.inviteCount(), presenter.anonymous()));
        var caption = Typography.meta(getTranslation("results.haveVoted"));
        var text = new Div(count, new Div(caption));
        text.addClassName("stack-s");
        // Names rather than faces where an initial identifies nobody, and there is no
        // "awaiting" half to draw either: membership is having answered (REQUIREMENTS §1c).
        var faces = presenter.anonymous()
                ? NameChips.of(this, poll.answered(), ANSWERED_NAMES)
                : new AvatarStack().show(poll.answered(), poll.awaiting());
        var header = new Div(text, faces);
        header.addClassNames("row-between", "row-end", "push-2xl");
        body(header);

        var tallies = new TallyList(poll.tallies(), tally -> captionFor(poll, tally));
        tallies.addClassName("push-2xl");
        body(tallies);

        proposalSection(poll).ifPresent(this::body);
        // Once voting is over there is nothing to answer and nothing left to settle:
        // a closed poll is final, and the standings are the answer it ended on.
        if (poll.isOpen()) {
            body(ownAnswer(poll));
            // Settling is the organizer's, so nobody else is shown the button. The
            // service refuses it either way; this is so an invitee is not offered a
            // decision that is not theirs.
            if (presenter.isOrganizer(poll)) {
                settleSection(poll).ifPresent(this::footer);
            }
        }
    }

    /**
     * The leading bar is the only one dark enough to carry text, so it says who is
     * in rather than repeating the number above it.
     *
     * <p>It says nothing in anonymous mode. "Everyone" and "everyone but Ada" are both
     * claims about who was asked, and nobody was asked — the people on the poll are the
     * people who answered it, so "everyone" would mean "everyone who already said yes".
     */
    private Optional<String> captionFor(Poll poll, DayTally tally) {
        if (presenter.anonymous()) {
            return Optional.empty();
        }
        if (tally.voteCount() == poll.inviteCount()) {
            return Optional.of(getTranslation("results.everyone"));
        }
        var missing = poll.invited().stream().filter(person -> !tally.voters().contains(person)).toList();
        return missing.size() == 1
                ? Optional.of(getTranslation("results.everyoneBut", missing.getFirst().firstName()))
                : Optional.empty();
    }

    /**
     * Only ever called in login mode, where the invitee list says who was asked. Nobody
     * is waited on in anonymous mode because nobody could say who is missing.
     */
    private Div waitingSection(Poll poll) {
        var chips = new Div();
        chips.addClassNames("chip-row", "push-m");
        poll.awaiting().stream().limit(VISIBLE_WAITING).forEach(person -> chips.add(new WaitingChip(person)));
        var remaining = poll.awaiting().size() - Math.min(VISIBLE_WAITING, poll.awaiting().size());
        if (remaining > 0) {
            var more = new Span(getTranslation("count.more", remaining));
            more.addClassNames("chip", "chip-outline");
            chips.add(more);
        }
        var section = new Div(Typography.sectionLabel(getTranslation("results.waitingOn")), chips);
        section.addClassName("push-2xl");
        return section;
    }

    /**
     * A day somebody put forward instead. It is not a column until it is accepted
     * here, which is the promise the voting screen makes.
     */
    private Optional<Div> proposalSection(Poll poll) {
        var proposals = poll.declined().stream().filter(ballot -> !ballot.proposedDays().isEmpty()).toList();
        if (proposals.isEmpty()) {
            return Optional.empty();
        }
        var section = new Div(Typography.sectionLabel(getTranslation("results.proposals")));
        section.addClassNames("stack-m", "push-2xl");
        proposals.forEach(ballot -> section.add(proposalRow(poll, ballot)));
        return Optional.of(section);
    }

    /**
     * Everybody sees what was put forward, for as long as the poll exists. Only the
     * organizer is offered the button that puts it on the table, because accepting one
     * adds a column to everybody's ballot — and only while the poll can still change.
     */
    private HintBar proposalRow(Poll poll, Ballot ballot) {
        var days = ballot.proposedDays().stream().map(day -> DateText.compact(this, day)).toList();
        var row = new HintBar(VaadinIcon.CALENDAR, getTranslation("results.proposal",
                ballot.voter().firstName(), String.join(", ", days))).outlined();
        if (presenter.isOrganizer(poll) && poll.isEditable()) {
            row.withAction(Actions.inline(getTranslation("results.acceptProposal"), ignored -> accept(ballot)));
        }
        return row;
    }

    /**
     * The organizer is invited like everybody else and counted in every denominator,
     * so they need a way in to the ballot — and a way back to it once they have used
     * it. Without this they are the one person on the poll who cannot answer it.
     */
    private HintBar ownAnswer(Poll poll) {
        var answered = poll.ballotOf(presenter.viewer());
        var bar = new HintBar(VaadinIcon.USER, answered.map(this::describeOwnAnswer)
                .orElseGet(() -> getTranslation("results.yourTurn")))
                .outlined()
                .withAction(Actions.inline(getTranslation(answered.isPresent()
                        ? "results.yourAnswer.action"
                        : "results.yourTurn.action"), ignored -> goTo(BallotView.class)));
        bar.addClassName("push-xl");
        return bar;
    }

    private String describeOwnAnswer(Ballot ballot) {
        if (ballot.isDeclined()) {
            return getTranslation("results.yourAnswer.declined");
        }
        return ballot.chosenDays().size() == 1
                ? getTranslation("results.yourAnswer.one")
                : getTranslation("results.yourAnswer.many", ballot.chosenDays().size());
    }

    /** "Sent 4 min ago" — coarse on purpose; a live seconds counter would be noise. */
    private String sentAgo(Poll poll) {
        if (poll.openedAt() == null) {
            return getTranslation("time.justNow");
        }
        var elapsed = Duration.between(poll.openedAt(), presenter.instant());
        var minutes = elapsed.toMinutes();
        if (minutes < 1) {
            return getTranslation("time.justNow");
        }
        if (minutes < Duration.ofHours(1).toMinutes()) {
            return minutes == 1 ? getTranslation("time.minutes.one") : getTranslation("time.minutes.many", minutes);
        }
        var hours = elapsed.toHours();
        if (hours < Duration.ofDays(1).toHours()) {
            return hours == 1 ? getTranslation("time.hours.one") : getTranslation("time.hours.many", hours);
        }
        var days = elapsed.toDays();
        return days == 1 ? getTranslation("time.days.one") : getTranslation("time.days.many", days);
    }

    /**
     * The same link the share screen hands out, for an organizer who is back here
     * because somebody never got it.
     */
    private Button shareLink(Poll poll) {
        var button = Actions.outline(getTranslation("results.shareLink"));
        VotingLink.shareFrom(button, poll);
        return button;
    }

    private void accept(Ballot ballot) {
        ballot.proposedDays().forEach(day -> presenter.acceptProposal(id(), day));
        render();
    }

    /**
     * The way to a decision, not the decision. Locking is final, so it happens on
     * {@link SettleView} where the screen can say so and be cancelled — never under the
     * organizer's thumb on a screen they came to read.
     *
     * <p>One day in front names it on the button. A shared top is a decision the group
     * did not make, so this says so and hands the choice on rather than picking the
     * earliest of the tied days and calling it the winner.
     */
    private Optional<Component> settleSection(Poll poll) {
        var single = poll.leader();
        if (single.isPresent()) {
            return Optional.of(Actions.commit(
                    getTranslation("results.lock", DateText.compact(this, single.get().day())),
                    ignored -> goTo(SettleView.class)));
        }
        var tied = poll.tiedAtTheTop();
        if (tied.isEmpty()) {
            return Optional.empty();
        }
        var section = new Div(
                new HintBar(VaadinIcon.SCALE, getTranslation("results.tied", tied.size())),
                Actions.commit(getTranslation("results.tied.action"),
                        ignored -> goTo(SettleView.class)));
        section.addClassName("stack-s");
        return Optional.of(section);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("results.title");
    }
}
