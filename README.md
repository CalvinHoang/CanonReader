<div align="center">

# Canon

**Plato, Shakespeare, Hume, Mill, William James and Nietzsche, read like a blog.**

</div>

Canon is an Android app built from [Marginal Reader](https://github.com/CalvinHoang/MarginalReader). It keeps Marginal Reader's reading experience (the feed, native post typography, swipe between posts, explore, saved, dark mode), but the posts are passages from six authors' works rather than a website's feed.

Every author "blogs" through their works in order. A few posts come out each day, interleaved across the six authors in proportion to how much each wrote, so the whole cast stays on the blog until the end. At the default six posts a day, the full run is about 32 months.

## What's in it

| Author | Works | Posts | Words |
|---|---|---|---|
| Plato | 28 dialogues (Jowett) | 466 | 693k |
| William Shakespeare | 39 plays, the Sonnets and 5 other poems | 1,096 | 971k |
| David Hume | *A Treatise of Human Nature*, 13 of the *Essays, Moral and Political*, *An Enquiry Concerning Human Understanding*, *An Enquiry Concerning the Principles of Morals*, the *Political Discourses*, *The History of England*, *My Own Life*, *Dialogues Concerning Natural Religion* | 1,138 | 1.55M |
| John Stuart Mill | *A System of Logic*, *On Liberty*, *Considerations on Representative Government*, *Utilitarianism*, *The Subjection of Women*, *Autobiography* | 480 | 658k |
| William James | *The Principles of Psychology*, *The Will to Believe*, *The Varieties of Religious Experience*, *Pragmatism*, *A Pluralistic Universe* | 661 | 831k |
| Friedrich Nietzsche | *The Birth of Tragedy*, the four *Thoughts out of Season*, *Human, All Too Human* (both parts), *The Dawn of Day*, *The Joyful Wisdom*, *Thus Spake Zarathustra*, *Beyond Good and Evil*, *The Genealogy of Morals*, *The Twilight of the Idols*, *The Antichrist*, *Ecce Homo* | 1,979 | 810k |
| **Total** | 107 works | **5,820** | **5.51M** |

The texts are public domain. Where [Standard Ebooks](https://standardebooks.org) has an edition, Canon uses it. The rest come from [Project Gutenberg](https://www.gutenberg.org), by way of [GITenberg](https://github.com/GITenberg)'s mirror of it on GitHub: Hume's *Essays*, second *Enquiry*, *Political Discourses*, *My Own Life*, *Dialogues* and *History*; Mill's *Logic*, *Representative Government* and *Utilitarianism*; James's *Principles of Psychology* and *The Will to Believe*; and every Nietzsche title except *Zarathustra*, *Beyond Good and Evil* and the *Genealogy*. The Nietzsche translations are those of Oscar Levy's *Complete Works* (Haussmann, Ludovici, Collins, Zimmern, Cohn, Kennedy, Common).

**Not yet included.** Gutenberg has no complete text of Hume's *Essays* (only the 13 above, plus the *Political Discourses*) or of Mill's *Principles of Political Economy* (only an abridgement). Also left for later: Hume's *Natural History of Religion*; James's *Meaning of Truth*, *Essays in Radical Empiricism* and *Some Problems of Philosophy*; Nietzsche's *Case of Wagner*, *Nietzsche contra Wagner* and poems.

## How the text becomes posts

The pipeline (`pipeline/`) never edits the authors' words. It only decides where to cut and adds packaging around the text.

- **Cuts come at the author's own boundaries**: a section, an aphorism, a scene, a sonnet, or a Zarathustra discourse. Chapters longer than about 2,000 words are split between whole paragraphs or speeches into parts of roughly 1,400 words (scenes are allowed up to about 2,600). Runs of very short aphorisms, such as *Beyond Good and Evil* Part IV, are grouped into one post.
- **Headlines** are the author's own section headings where they exist ("Of the Idea of Necessary Connection"). Otherwise they're the passage's own opening words ("He who fights with monsters should be careful lest he thereby become…"). Scenes are titled "Hamlet, Act III, Scene I", and sonnets "Sonnet 18: Shall I compare thee to a summer's day?".
- **A source line** under each headline says where the passage sits, e.g. "II: Of the Liberty of Thought and Discussion · Part 3 of 8".
- **Dialogue and drama** keep their speaker labels. The author's (and translators') notes appear at the foot of the post that references them.
- **Left out**: editors' introductions (for example W. L. Courtney's introduction to *On Liberty*, Oscar Levy's and the translators' introductions to Nietzsche), Jowett's long introductions and analyses to each dialogue, dedications, epigraph pages and cast lists. From the Gutenberg editions, also: page numbers, the publisher's marginal dates and "Contemporary Monarchs" tables in Hume's *History*, and the one editor's footnote in the second *Enquiry*.
- **Gutenberg editions** are less regular than Standard Ebooks', so `pipeline/gutenberg.py` has a short entry per book saying where the author's text starts and stops, which sections are editors' matter, and how its headings nest. Footnotes printed inline ("[Footnote: …]", or the `[*]` notes in Hume's *History*) are moved to the foot of the post like any other note. Typos in the Gutenberg transcriptions are left as they are.

`pipeline/verify.py` re-reads every source independently and checks, word for word and in order, that each work's posts laid end to end reproduce the author's text exactly. All 107 works pass.

### Rebuilding the corpus

```bash
cd pipeline
pip install -r requirements.txt
./fetch_sources.sh      # clones the Standard Ebooks repos into pipeline/se and the GITenberg ones into pipeline/pg
python3 build.py        # writes out/canon.db and app/src/main/assets/canon.db.gz
python3 verify.py       # word-for-word check against the sources
```

After rebuilding, bump `versionCode` in `app/build.gradle.kts`. The app reinstalls its copy of the database whenever the version code changes.

## The publishing calendar

- Posts come out between 6:30 am and 9:30 pm, spread evenly. The feed shows how many are out today and when the next one arrives.
- A new install starts with three days of backlog so the blog doesn't open empty.
- **Settings → Publishing** sets 3, 6, 10 or 20 posts a day. Changing it keeps everything already published.
- **Unlock everything** opens the whole archive now, dated as if the blog had just finished. Turn it off to return to the daily schedule. The schedule keeps running underneath.
- Inside a post, "Next in *work*" continues the work in order. If the next part isn't out yet, the post says when it arrives.
- Optional notifications announce new posts. They need no network, because the calendar is computed on the device.

## Building the app

Open the project in Android Studio and run the `app` configuration, or:

```bash
./gradlew :app:assembleDebug
```

The build matches Marginal Reader's toolchain (AGP 9, Kotlin 2.4, Compose, Hilt). It no longer needs networking, Room or Coil: the corpus is a bundled SQLite database (with FTS4 for search), and saved posts are stored as ids.

## Disclaimer

Canon is an independent project. It is not affiliated with Standard Ebooks, Project Gutenberg, Marginal Revolution or the translators' publishers.
