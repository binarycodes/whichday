package io.binarycodes.findadate.poll.ui.view;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What the create form binds to. Mutable because {@code Binder} demands it. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class PollDraft {

    private String title;
}
