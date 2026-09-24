<div align="center">

# Canon

**Plato, Shakespeare, Hume, Mill, William James and Nietzsche, read like a blog.**

</div>

Canon is an Android app built from [Marginal Reader](https://github.com/CalvinHoang/MarginalReader). It keeps Marginal Reader's reading experience (the feed, native post typography, swipe between posts, explore, saved, dark mode), but the posts are passages from six authors' works rather than a website's feed.

Every author "blogs" through their works in order. A few posts come out each day, interleaved across the six authors in proportion to how much each wrote, so the whole cast stays on the blog until the end. At the default six posts a day, the full run is about 14 months.

## What's in it

| Author | Works | Posts | Words |
|---|---|---|---|
| Plato | 28 dialogues (Jowett) | 506 | 693k |
| William Shakespeare | 39 plays, the Sonnets and 5 other poems | 1,131 | 970k |
| David Hume | *A Treatise of Human Nature*, *An Enquiry Concerning Human Understanding* | 222 | 263k |
| John Stuart Mill | *On Liberty*, *The Subjection of Women*, *Autobiography* | 131 | 168k |
| William James | *The Varieties of Religious Experience*, *Pragmatism*, *A Pluralistic Universe* | 221 | 274k |
| Friedrich Nietzsche | *Thus Spake Zarathustra* (Common), *Beyond Good and Evil* (Zimmern), *The Genealogy of Morals* (Samuel) | 405 | 206k |
| **Total** | | **2,498** | **2.57M** |

All texts are the [Standard Ebooks](https://standardebooks.org) editions, which are in the public domain.

**Not yet included.** Standard Ebooks hasn't produced these yet, so they aren't in the app: Hume's *Essays*, *Enquiry Concerning the Principles of Morals*, *Dialogues Concerning Natural Religion* and *History of England*; Mill's *Utilitarianism*, *Considerations on Representative Government*, *System of Logic* and *Principles of Political Economy*; James's *Principles of Psychology*, *The Will to Believe* and later essays; Nietzsche's *Birth of Tragedy*, *Untimely Meditations*, *Human, All Too Human*, *Daybreak*, *The Gay Science*, *Twilight of the Idols*, *The Antichrist* and *Ecce Homo*. Project Gutenberg has public-domain texts of most of these and is the natural next source.

## How the text becomes posts

The pipeline (`pipeline/`) never edits the authors' words. It only decides where to cut and adds packaging around the text.

- **Cuts come at the author's own boundaries**: a section, an aphorism, a scene, a sonnet, or a Zarathustra discourse. Chapters longer than about 2,000 words are split between whole paragraphs or speeches into parts of roughly 1,400 words (scenes are allowed up to about 2,600). Runs of very short aphorisms, such as *Beyond Good and Evil* Part IV, are grouped into one post.
- **Headlines** are the author's own section headings where they exist ("Of the Idea of Necessary Connection"). Otherwise they're the passage's own opening words ("He who fights with monsters should be careful lest he thereby become…"). Scenes are titled "Hamlet, Act III, Scene I", and sonnets "Sonnet 18: Shall I compare thee to a summer's day?".
- **A source line** under each headline says where the passage sits, e.g. "II: Of the Liberty of Thought and Discussion · Part 3 of 8".
- **Dialogue and drama** keep their speaker labels. The author's (and translators') notes appear at the foot of the post that references them.
- **Left out**: editors' introductions (for example W. L. Courtney's introduction to *On Liberty*), Jowett's long introductions and analyses to each dialogue, dedications, epigraph pages and cast lists.

`pipeline/verify.py` re-reads every source independently and checks, word for word and in order, that each work's posts laid end to end reproduce the author's text exactly. All 84 works pass.

### Rebuilding the corpus

```bash
cd pipeline
pip install -r requirements.txt
./fetch_sources.sh      # clones the Standard Ebooks repos into pipeline/se
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

Canon is an independent project. It is not affiliated with Standard Ebooks, Marginal Revolution or the translators' publishers.
