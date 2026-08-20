package io.binarycodes.whichday.poll.ui.view;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.LiveBadge;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.ui.AvatarStack;
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
@Route("poll/:slug")
public class ResultsView extends PollScreen {

    private static final int VISIBLE_WAITING = 4;

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
        if (poll.isUnanswered()) {
            buildUnanswered(poll);
        } else {
            buildStandings(poll);
        }
    }

    private void buildUnanswered(Poll poll) {
        body(new TopBar(poll.title()).leadingTitle()
                .withLeading(homeButton())
                .withTrailing(Typography.meta(getTranslation("results.sentAgo", sentAgo(poll)))));

        var count = Typography.stat(Counts.progress(this, 0, poll.inviteCount()));
        count.addClassName("stat-empty");
        var caption = Typography.meta(getTranslation("results.haveVoted"));
        var block = new Div(count, new Div(caption));
        block.addClassNames("stack-s", "push-2xl");
        body(block);

        var days = new AwaitingDayList(poll.candidateDays());
        days.addClassName("push-xl");
        body(days);

        body(waitingSection(poll));
        // A poll nobody has answered includes the organizer, and this is where they
        // most need the way in.
        body(ownAnswer(poll));

        var reminder = new HintBar(VaadinIcon.CLOCK, getTranslation("results.reminder"));
        footer(reminder, Actions.outline(getTranslation("results.copyLink"), ignored -> copyLink(poll)));
    }

    private void buildStandings(Poll poll) {
        body(new TopBar(poll.title()).leadingTitle()
                .withLeading(homeButton())
                .withTrailing(new LiveBadge(getTranslation("results.live"))));

        var count = Typography.stat(Counts.progress(this, poll.answerCount(), poll.inviteCount()));
        var caption = Typography.meta(getTranslation("results.haveVoted"));
        var text = new Div(count, new Div(caption));
        text.addClassName("stack-s");
        var faces = new AvatarStack().show(poll.answered(), poll.awaiting());
        var header = new Div(text, faces);
        header.addClassNames("row-between", "row-end", "push-2xl");
        body(header);

        var tallies = new TallyList(poll.tallies(), tally -> captionFor(poll, tally));
        tallies.addClassName("push-2xl");
        body(tallies);

        proposalSection(poll).ifPresent(this::body);
        body(ownAnswer(poll));
        var others = poll.awaitingOthers(presenter.viewer());
        if (others.size() == 1) {
            body(nudge(others.getFirst()));
        }

        poll.leader().ifPresent(leader -> {
            footer(Actions.commit(getTranslation("results.lock", DateText.compact(this, leader.day())),
                    ignored -> lock(leader)));
        });
    }

    /**
     * The leading bar is the only one dark enough to carry text, so it says who is
     * in rather than repeating the number above it.
     */
    private Optional<String> captionFor(Poll poll, DayTally tally) {
        if (tally.voteCount() == poll.inviteCount()) {
            return Optional.of(getTranslation("results.everyone"));
        }
        var missing = poll.invited().stream().filter(person -> !tally.voters().contains(person)).toList();
        return missing.size() == 1
                ? Optional.of(getTranslation("results.everyoneBut", missing.getFirst().firstName()))
                : Optional.empty();
    }

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
        proposals.forEach(ballot -> section.add(proposalRow(ballot)));
        return Optional.of(section);
    }

    private HintBar proposalRow(Ballot ballot) {
        var days = ballot.proposedDays().stream().map(day -> DateText.compact(this, day)).toList();
        var accept = Actions.inline(getTranslation("results.acceptProposal"), ignored -> accept(ballot));
        return new HintBar(VaadinIcon.CALENDAR, getTranslation("results.proposal",
                ballot.voter().firstName(), String.join(", ", days))).outlined().withAction(accept);
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

    private HintBar nudge(Person holdout) {
        var send = Actions.inline(getTranslation("results.nudge.action"),
                ignored -> Notification.show(getTranslation("results.nudged", holdout.firstName())));
        var bar = new HintBar(VaadinIcon.BELL, getTranslation("results.nudge", holdout.firstName()))
                .outlined().withAction(send);
        bar.addClassName("push-m");
        return bar;
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

    private void copyLink(Poll poll) {
        VotingLink.copyToClipboard(this, poll.slug());
        Notification.show(getTranslation("share.copied"));
    }

    private void accept(Ballot ballot) {
        ballot.proposedDays().forEach(day -> presenter.acceptProposal(slug(), day));
        render();
    }

    private void lock(DayTally leader) {
        presenter.lock(slug(), leader.day());
        goTo(LockedView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("results.title");
    }
}
