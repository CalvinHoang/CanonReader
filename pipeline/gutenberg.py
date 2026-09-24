"""
Project Gutenberg sources for the works Standard Ebooks hasn't produced.

The texts come from GITenberg (github.com/GITenberg), which mirrors each Project Gutenberg
book as a git repository; fetch_sources.sh clones the ones listed in pg_sources.txt into
pipeline/pg. Gutenberg's HTML is far less regular than Standard Ebooks' XHTML, so every
book gets a small entry in BOOKS saying where the author's text starts and stops, which
sections are editors' matter, and how its headings nest. From there the same rules apply
as for the Standard Ebooks works: the words are never edited, cuts come at the author's
own boundaries, and notes are moved to the foot of the post that references them.
"""
import html
import os
import re
from dataclasses import dataclass
from typing import Callable, Optional

from lxml import html as lhtml

HERE = os.path.dirname(os.path.abspath(__file__))
PG_DIR = os.path.join(HERE, "pg")

HEAD_TAGS = {"h1", "h2", "h3", "h4", "h5", "h6"}
BLOCK_TAGS = {"p", "blockquote", "pre", "table", "ul", "ol", "dl", "hr"}
CONTAINERS = {"div", "center", "body", "section", "article", "span", "font", "big", "small"}
WORD_RE = re.compile(r"[\w’'-]+", re.UNICODE)


def tag(el):
    return el.tag.lower() if isinstance(el.tag, str) else ""


def cls(el):
    return (el.get("class") or "").lower().split()


def norm(s):
    return " ".join((s or "").split())


def text_of(el):
    return norm(el.text_content())


# ---------------------------------------------------------------------------
# Book descriptions
# ---------------------------------------------------------------------------


def drop_common(el):
    """Page numbers, printer's sidenotes, illustrations: not part of the text."""
    c = cls(el)
    if tag(el) in ("script", "style", "img"):
        return True
    if any(k in c for k in ("pagenum", "pageno", "tei-pb", "xxpn", "side", "figcenter", "figleft", "figright",
                            "fig", "caption", "capt02", "capt2", "toc", "index")):
        return True
    if tag(el) == "a" and not el.get("href") and not norm(el.text_content()):
        return True
    return False


def note_common(el):
    """Footnote bodies in the usual Gutenberg layouts."""
    c = cls(el)
    t = tag(el)
    if t == "span" and "gnote" in c:
        return True
    if t == "div" and ("footnote" in c or "note" in c or "dftnt" in c):
        return True
    if t == "p" and ("foot" in c or "footnote" in c):
        return True
    if t == "dd" and "tei-notetext" in c:
        return True
    return False


@dataclass
class GBook:
    repo: object                    # directory under pg/ (a GITenberg repository), or a tuple of them
    author: str
    work_id: str
    title: str
    year: int
    translator: Optional[str] = None
    start: str = ""                 # regex on a heading: where the author's text begins (per file)
    stop: str = ""                  # regex on a heading: where it ends (not included)
    skip: tuple = ()                # regexes on headings whose sections are editors' matter
    level: Callable = None          # (element, text) -> nesting level, or None for a minor heading
    merge: str = ""                 # regex: a label heading ("Chapter II.") that takes the next heading as its title
    heading: Callable = None        # extra non-h* elements that are headings (e.g. aphorism numbers)
    drop: Callable = None           # extra elements to drop
    inline_note: Callable = None    # blocks that are footnotes printed inline, without a link
    note_drop: str = ""             # regex: footnotes written by an editor, left out
    bracket_notes: str = ""         # regex opening an inline '[Footnote: ...]'

    def paths(self):
        out = []
        for repo in (self.repo if isinstance(self.repo, tuple) else (self.repo,)):
            base = os.path.join(PG_DIR, repo)
            found = [os.path.join(base, d, f) for d in sorted(os.listdir(base)) if d.endswith("-h")
                     for f in sorted(os.listdir(os.path.join(base, d))) if re.search(r"\.html?$", f)]
            if not found:
                raise FileNotFoundError(repo)
            out.append(found[0])
        return out

    def is_dropped(self, el):
        return drop_common(el) or bool(self.drop and self.drop(el))

    def is_heading(self, el):
        if tag(el) in HEAD_TAGS:
            return True
        return bool(self.heading and self.heading(el))

    def hit(self, pattern, el, txt):
        """Does a start/stop/skip pattern match this heading? Patterns may test the text
        alone ('^PREFACE$') or the tag too ('^h3[^|]*\\|THE ANTICHRIST$')."""
        return bool(pattern) and bool(re.search(pattern, txt) or re.search(pattern, f"{tag(el)}.{'.'.join(cls(el))}|{txt}"))

    def level_of(self, el):
        """Nesting level of a heading, or None if it is only a minor heading inside the text."""
        txt = heading_text(el)
        if self.level is None:
            return int(tag(el)[1]) if tag(el) in HEAD_TAGS else None
        return self.level(el, txt)


def heading_text(el):
    """A heading's words, without page numbers or note markers."""
    parts = []

    def walk(e):
        if drop_common(e):
            if e.tail:
                parts.append(e.tail)
            return
        if tag(e) == "a" and re.fullmatch(r"\[?\s*[\w*]{1,4}\s*\]?", norm(e.text_content()) or "x") \
                and (e.get("href") or "").startswith("#"):
            if e.tail:
                parts.append(e.tail)
            return
        if tag(e) == "br":
            parts.append(" ")
        if e.text:
            parts.append(e.text)
        for c in e:
            walk(c)
        if e is not el and e.tail:
            parts.append(e.tail)

    walk(el)
    t = norm("".join(parts).replace("\u200b", ""))
    t = re.sub(r"\[Pg \d+\]", "", t)
    t = re.sub(r"\s*\[\s*\d*\s*\]$", "", t)
    return norm(t)


def lv(*rules, default=None):
    """Build a level function from (regex, level) rules. Each regex is tested against
    'tag.classes|heading text', e.g. 'h2.c004|BOOK FIRST', so a rule can test either."""
    compiled = [(re.compile(r, re.I), n) for r, n in rules]

    def f(el, txt):
        key = f"{tag(el)}.{'.'.join(cls(el))}|{txt}"
        for r, n in compiled:
            if r.search(key):
                return n
        return default(el, txt) if callable(default) else default
    return f


def parnum(el):
    return tag(el) == "p" and "parnum" in cls(el)


H = r"^h\d[^|]*\|"          # prefix matching any h* tag in a level key

MILL_REPGOV = GBook(
    repo="Considerations-on-Representative-Government_5669", author="mill",
    work_id="considerations-on-representative-government",
    title="Considerations on Representative Government", year=1861,
    start=r"^Preface$", stop=r"^Footnotes", level=lv((H, 1)))

MILL_UTIL = GBook(
    repo="Utilitarianism_11224", author="mill", work_id="utilitarianism", title="Utilitarianism",
    year=1863, start=r"^CHAPTER I\.$", level=lv((r"^h2", 1)), merge=r"^CHAPTER [IVX]+\.$",
    heading=lambda el: tag(el) == "center" or (tag(el) == "p" and text_of(el) == "FOOTNOTES:"),
    skip=(r"^FOOTNOTES:$",), drop=lambda el: (el.get("href") or "").startswith("#FNanchor"))

HUME_EPM = GBook(
    repo="An-Enquiry-Concerning-the-Principles-of-Morals_4320", author="hume",
    work_id="an-enquiry-concerning-the-principles-of-morals",
    title="An Enquiry Concerning the Principles of Morals", year=1751,
    start=r"^AUTHOR'S ADVERTISEMENT", merge=r"^SECTION [IVX]+\.?$",
    heading=lambda el: tag(el) == "center" and "ADVERTISEMENT" in text_of(el),
    level=lv((r"^center|^h2[^|]*\|(SECTION|APPENDIX [IVX])", 1), (r"^h2[^|]*\|PART", 2), (H, 1)),
    skip=(r"^CONTENTS", r"^APPENDIX\.$", r"^AN ENQUIRY CONCERNING THE PRINCIPLES OF MORALS$"),
    bracket_notes=r"\[\s*(?:Footnote|FOOTNOTE)\s*:?",
    note_drop=r"posthumous edition of Hume")      # the 1912 editor's note on the Advertisement

HUME_DIALOGUES = GBook(
    repo="Dialogues-Concerning-Natural-Religion_4583", author="hume",
    work_id="dialogues-concerning-natural-religion", title="Dialogues Concerning Natural Religion",
    year=1779, start=r"^PAMPHILUS TO HERMIPPUS", level=lv((r"^h3", 1)))

HUME_ESSAYS = GBook(
    repo="Essays_36120", author="hume", work_id="essays-moral-and-political",
    title="Essays, Moral and Political", year=1742,      # a selection of 13 of the 1741–42 essays
    start=r"^OF THE DELICACY OF TASTE", level=lv((r"^h3", 1)))

HUME_LIFE = GBook(
    repo="Hume-s-Political-Discourses_59792", author="hume", work_id="my-own-life",
    title="My Own Life", year=1776, start=r"^MY OWN LIFE", stop=r"^ADAM SMITH", level=lv((H, 1)))

HUME_DISCOURSES = GBook(
    repo="Hume-s-Political-Discourses_59792", author="hume", work_id="political-discourses",
    title="Political Discourses", year=1752, start=r"^OF COMMERCE\.$", stop=r"^ALPHABETICAL ARRANGEMENT",
    level=lv((r"^h2", 1)),
    # the edition closes with three essays printed elsewhere in Canon: two from the Essays and
    # Section IV of the Enquiry Concerning the Principles of Morals
    skip=(r"^NOTES?, ", r"^THAT POLITICS MAY BE REDUCED", r"^OF THE FIRST PRINCIPLES OF GOVERNMENT",
          r"^OF POLITICAL SOCIETY"))

HUME_HISTORY = GBook(
    repo=("The-History-of-England-in-Three-Volumes-Vol.I.-Part-A.From-the-Britons-of-Early-Times-to-King__19211",
          "The-History-of-England-in-Three-Volumes-Vol.I.-Part-B.From-Henry-III.-to-Richard-III._19212",
          "The-History-of-England-in-Three-Volumes-Vol.I.-Part-C.From-Henry-VII.-to-Mary_19213",
          "The-History-of-England-in-Three-Volumes-Vol.I.-Part-D.From-Elizabeth-to-James-I._19214",
          "The-History-of-England-in-Three-Volumes-Vol.I.-Part-E.From-Charles-I.-to-Cromwell_19215",
          "The-History-of-England-in-Three-Volumes-Vol.I.-Part-F.From-Charles-II.-to-James-II._19216"),
    author="hume", work_id="the-history-of-england", title="The History of England", year=1754,
    start=r"^CHAPTER [IVXL]+\.?$", stop=r"^NOTES\.?$",
    level=lv((r"^h2[^|]*\|(CHAPTER|APPENDIX)", 1), (r"^h2", 2)),
    # the 1860 edition's tables of 'Contemporary Monarchs' are the publisher's, not Hume's
    drop=lambda el: (tag(el) == "h3" and "contemporary" in text_of(el).lower()) or (
        tag(el) == "pre" and re.search(r"CONTEMPORARY MONARCHS|EMP\. O[FP] GERM|K\. OF FRANCE", text_of(el))),
    inline_note=lambda el: tag(el) == "pre" and re.match(r"\[?\s*\*", text_of(el)))

JAMES_PSYCHOLOGY = GBook(
    repo=("The-Principles-of-Psychology-Volume-1-of-2_57628", "The-Principles-of-Psychology-Volume-2-of-2_57634"),
    author="james", work_id="the-principles-of-psychology", title="The Principles of Psychology", year=1890,
    start=r"^(PREFACE\.?|CHAPTER XVII\.?)$", stop=r"^(END OF VOL|INDEX|THE END)",
    level=lv((r"^h\d[^|]*\|(PREFACE|CONTENTS)", 1), (r"^h5[^|]*\|CHAPTER", 1), (r"^h4", 2)),
    merge=r"^CHAPTER [IVXL]+\.?$", skip=(r"^CONTENTS",))

JAMES_WILL = GBook(
    repo="The-Will-to-Believe-and-Other-Essays-in-Popular-Philosophy_26659", author="james",
    work_id="the-will-to-believe", title="The Will to Believe and Other Essays in Popular Philosophy", year=1897,
    start=r"^PREFACE\.?$", stop=r"^INDEX\.$",
    level=lv((r"^h3[^|]*\|[IVX]+\.?$", 2), (r"^h3", 1), (r"^h4[^|]*\|MEMBERS", None), (r"^h4", 2)),
    skip=((r"^CONTENTS", r"^THE WILL TO BELIEVE\.$"),),
    drop=lambda el: tag(el) in HEAD_TAGS and text_of(el) in ("ESSAYS", "IN", "POPULAR PHILOSOPHY."))

MILL_LOGIC = GBook(
    repo="A-System-Of-Logic-Ratiocinative-And-Inductive_27942", author="mill",
    work_id="a-system-of-logic", title="A System of Logic, Ratiocinative and Inductive", year=1843,
    start=r"^Preface To The First Edition", stop=r"^Footnotes$",
    level=lv((r"^h1", 1), (r"^h2", 2), (r"^h3", 3)),
    merge=r"^(Book|Chapter) [IVXL]+\.$")

TOS1 = "Thoughts-out-of-Season-Part-I-David-Strauss-the-Confessor-and-the-Writer-Richard-Wagner-in-Ba__51710"
TOS2 = "Thoughts-Out-of-Season--Part-II-_38226"
ROMAN_OR_NUM = r"\|([IVXL]+|\d+)\.?$"


def title_only(*texts):
    """Drop headings that only repeat the title page's wording."""
    return lambda el: tag(el) in HEAD_TAGS and text_of(el) in texts


N_BIRTH = GBook(
    repo="The-Birth-of-Tragedy-or-Hellenism-and-Pessimism_51356", author="nietzsche",
    work_id="the-birth-of-tragedy", title="The Birth of Tragedy", year=1872, translator="William A. Haussmann",
    start=r"^AN ATTEMPT AT SELF-CRITICISM", stop=r"^TRANSLATOR'S NOTE",
    level=lv((ROMAN_OR_NUM, 2), (r"^h3", 1), (r"^h4", 1)),
    drop=title_only("THE BIRTH OF TRAGEDY", "FROM THE SPIRIT OF MUSIC"))

N_STRAUSS = GBook(
    repo=TOS1, author="nietzsche", work_id="david-strauss", title="David Strauss, the Confessor and the Writer",
    year=1873, translator="Anthony M. Ludovici", start=r"^DAVID STRAUSS,$", stop=r"^RICHARD WAGNER IN BAYREUTH\.$",
    level=lv((ROMAN_OR_NUM, 1), (r"^h3", 0)), drop=title_only("THE CONFESSOR AND THE WRITER."))

N_HISTORY = GBook(
    repo=TOS2, author="nietzsche", work_id="the-use-and-abuse-of-history", title="The Use and Abuse of History",
    year=1874, translator="Adrian Collins", start=r"^THE USE AND ABUSE OF HISTORY\.$",
    stop=r"^SCHOPENHAUER AS EDUCATOR\.$",
    level=lv((r"^h3", 0), (r"^h4", 1)))

N_SCHOPENHAUER = GBook(
    repo=TOS2, author="nietzsche", work_id="schopenhauer-as-educator", title="Schopenhauer as Educator",
    year=1874, translator="Adrian Collins", start=r"^SCHOPENHAUER AS EDUCATOR\.$", level=lv((r"^h3", 0), (r"^h4", 1)))

N_WAGNER = GBook(
    repo=TOS1, author="nietzsche", work_id="richard-wagner-in-bayreuth", title="Richard Wagner in Bayreuth",
    year=1876, translator="Anthony M. Ludovici", start=r"^RICHARD WAGNER IN BAYREUTH\.$",
    level=lv((r"^h3", 0), (r"^h4", 1)))

N_HAH1 = GBook(
    repo="Human-All-Too-Human-A-Book-for-Free-Spirits-Part-1-Complete-Works-Volume-Six_51935",
    author="nietzsche", work_id="human-all-too-human", title="Human, All Too Human", year=1878,
    translator="Helen Zimmern", start=r"^PREFACE$", heading=parnum,
    level=lv((r"^p\.parnum", 2), (r"^h3", 0), (r"^h4", 1)), merge=r"DIVISION\.$")

N_HAH2 = GBook(
    repo="Human-All-Too-Human--A-Book-For-Free-Spirits--Part-II_37841", author="nietzsche",
    work_id="human-all-too-human-part-ii", title="Human, All Too Human, Part II", year=1879,
    translator="Paul V. Cohn", start=r"^Preface\.$", stop=r"^Footnotes$",
    level=lv((r"^h1", 1), (r"^h2", 2)))

N_DAWN = GBook(
    repo="The-Dawn-of-Day_39955", author="nietzsche", work_id="the-dawn-of-day", title="The Dawn of Day",
    year=1881, translator="J. M. Kennedy", start=r"^Author's Preface\.$", stop=r"^Footnotes$",
    level=lv((r"^h1", 1), (r"^h2", 2)))

N_TWILIGHT = GBook(
    repo="The-Twilight-of-the-Idols-or-How-to-Philosophize-with-the-Hammer-The-Antichrist-Complete-Work__52263",
    author="nietzsche", work_id="the-twilight-of-the-idols", title="The Twilight of the Idols", year=1888,
    translator="Anthony M. Ludovici", start=r"^PREFACE$", stop=r"^h3[^|]*\|THE ANTICHRIST$", heading=parnum,
    level=lv((r"^p\.parnum", 2), (r"^h5[^|]*\|THE END", 0), (r"^h4", 1)))

N_ANTICHRIST = GBook(
    repo="The-Twilight-of-the-Idols-or-How-to-Philosophize-with-the-Hammer-The-Antichrist-Complete-Work__52263",
    author="nietzsche", work_id="the-antichrist", title="The Antichrist", year=1888,
    translator="Anthony M. Ludovici", start=r"^h3[^|]*\|THE ANTICHRIST$", stop=r"^THE ETERNAL RECURRENCE", heading=parnum,
    level=lv((r"^p\.parnum", 1), (r"^h3", 0), (r"^h4", 1)))

N_ECCE = GBook(
    repo="Ecce-Homo-Complete-Works-Volume-Seventeen_52190", author="nietzsche", work_id="ecce-homo",
    title="Ecce Homo", year=1888, translator="Anthony M. Ludovici", start=r"^PREFACE$",
    stop=r"^EDITORIAL NOTE TO POETRY", heading=parnum,
    level=lv((r"^p\.parnum", 2), (r"^h3", 0), (r"^h4[^|]*\|HOW ONE BECOMES", 0), (r"^h4", 1), (r"^h5", 0)))

N_JOYFUL = GBook(
    repo="The-Joyful-Wisdom-La-Gaya-Scienza_52881", author="nietzsche", work_id="the-joyful-wisdom",
    title="The Joyful Wisdom", year=1882, translator="Thomas Common",
    start=r"^PREFACE TO THE SECOND", stop=r"^FOOTNOTES",
    level=lv((r"^h2\.c013", None), (r"^h2", 1), (r"^h3", 2)))

# Each author's works are published in order of year (build.interleave); equal years keep this order.
BOOKS = [
    HUME_ESSAYS, HUME_EPM, HUME_DISCOURSES, HUME_HISTORY, HUME_LIFE, HUME_DIALOGUES,
    MILL_LOGIC, MILL_REPGOV, MILL_UTIL,
    JAMES_PSYCHOLOGY, JAMES_WILL,
    N_BIRTH, N_STRAUSS, N_HISTORY, N_SCHOPENHAUER, N_WAGNER, N_HAH1, N_HAH2, N_DAWN, N_JOYFUL,
    N_TWILIGHT, N_ANTICHRIST, N_ECCE,
]


# ---------------------------------------------------------------------------
# Headings -> build.Heading
# ---------------------------------------------------------------------------

SMALL = {"a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "into", "nor", "of", "on", "or",
         "the", "to", "upon", "with"}
LABELS = r"(?:Chapter|Section|Book|Part|Lecture|Essay|Appendix|Division|Dialogue|Note|Volume)"


def smart_case(s):
    """ALL-CAPS headings become title case; 'Of The Things Denoted By Names' loses its
    capitalised small words; anything else is left alone."""
    if re.search(r"[a-zß-ÿ]", s):
        words = s.split(" ")
        if len(words) > 2 and all(w[:1].isupper() or not w[:1].isalpha() for w in words):
            return " ".join(w if i == 0 or w.lower() not in SMALL or re.search(r"[:—.]$", words[i - 1])
                            else w.lower() for i, w in enumerate(words))
        return s
    out = []
    for i, w in enumerate(s.split(" ")):
        lw = w.lower()
        core = re.sub(r"^\W+|\W+$", "", lw)
        if re.fullmatch(r"[ivxlcdm]+\.?", core) and len(core) > 0 and core not in ("i", "mix", "dim", "mild", "vim", "lid", "did", "civil", "mill"):
            out.append(w)                   # roman numeral
        elif i > 0 and core in SMALL and not re.search(r"[:—.]$", out[-1] if out else ""):
            out.append(lw)
        else:
            out.append(re.sub(r"[a-zß-ÿ]", lambda m: m.group(0).upper(), lw, count=1))
    s = " ".join(out)
    return re.sub(r"(?<=[’'])S\b", "s", s)


def make_heading(Heading, txt):
    t = norm(txt).strip()
    t = re.sub(r"[.:]$", "", t).strip()
    m = re.fullmatch(rf"(?i)({LABELS})\s+([IVXLCDM]+|\d+[A-Z]?|[A-Z])(?=$|[\s.:—–-])\.?(?:\s*[.:—–-]\s*|\s+)?(.*)", t)
    if m:
        label = m.group(1).capitalize()
        title = smart_case(m.group(3).strip(" .:—–-"))
        return Heading(label=label, ordinal=m.group(2).upper() if not m.group(2).isdigit() else m.group(2),
                       title=title)
    if re.fullmatch(r"(?:[IVXLCDM]+|\d+[A-Za-z]?)", t):
        return Heading(ordinal=t)
    if re.fullmatch(r"[\d]+\.?\s.*", t):
        num, rest = t.split(None, 1)
        return Heading(ordinal=num.rstrip("."), title=smart_case(rest))
    return Heading(title=smart_case(t))


# ---------------------------------------------------------------------------
# Footnotes
# ---------------------------------------------------------------------------


class Notes:
    """Finds a book's footnote bodies and resolves the links that point at them."""

    def __init__(self, book, root):
        self.book = book
        self.bodies = []            # footnote elements, in document order
        self.inside = {}            # anchor name/id inside (or just before) a body -> index
        self.back = {}              # anchor a body links back to -> index
        self.index = {}             # id(body element) -> index
        for el in root.iter():
            if not tag(el) or not self.is_body(el):
                continue
            if any(self.is_body(a) for a in el.iterancestors()):
                continue
            k = len(self.bodies)
            self.bodies.append(el)
            self.index[id(el)] = k
            for a in el.iter("a"):
                if any(drop_common(x) for x in a.iterancestors()):
                    continue                    # a page-number anchor inside the note
                for key in (a.get("name"), a.get("id")):
                    if key:
                        self.inside.setdefault(key, k)
                href = a.get("href") or ""
                if href.startswith("#"):
                    self.back.setdefault(href[1:], k)
            if el.get("id"):
                self.inside.setdefault(el.get("id"), k)
            dt = el.getprevious()
            if tag(el) == "dd" and dt is not None and tag(dt) == "dt":
                for a in dt.iter("a"):
                    for key in (a.get("name"), a.get("id")):
                        if key:
                            self.inside.setdefault(key, k)
                    if (a.get("href") or "").startswith("#"):
                        self.back.setdefault(a.get("href")[1:], k)
            prev, hops = el.getprevious(), 0
            while prev is not None and hops < 6 and (tag(prev) == "a" or (
                    tag(prev) == "p" and not norm(prev.text_content()))):
                for a in [prev] + list(prev.iter("a")):
                    for key in (a.get("name"), a.get("id")):
                        if key:
                            self.inside.setdefault(key, k)
                prev, hops = prev.getprevious(), hops + 1

    def is_body(self, el):
        return note_common(el) and not (self.book.inline_note and self.book.inline_note(el))

    def ref(self, a):
        """Index of the footnote an <a> points at, or None if it isn't a note reference."""
        href = a.get("href") or ""
        if not href.startswith("#"):
            return None
        target = href[1:]
        if any(b in self.bodies for b in a.iterancestors()):
            return None
        # the note that links back to this reference is the surest match
        names = {a.get("name"), a.get("id")}
        prev = a.getprevious()
        if prev is not None and tag(prev) == "a":
            names |= {prev.get("name"), prev.get("id")}
        for n in names:
            if n and n in self.back:
                return self.back[n]
        return self.inside.get(target)


LABEL_RE = re.compile(r"^\s*(?:\[\s*[\w*]{1,4}\s*\]|[\w*]{1,4}\s*\(\s*(?:return)?\s*\)|\(?\s*[\w*]{1,4}\s*\)|[\d*]{1,4}\.?)\s*(?:<br/>\s*)?")


# ---------------------------------------------------------------------------
# Rendering Gutenberg HTML into the app's small HTML subset
# ---------------------------------------------------------------------------


def esc(s, keep_lines=False):
    if keep_lines:
        return "<br/>".join(html.escape(re.sub(r"[ \t\r]+", " ", x), quote=False) for x in (s or "").split("\n"))
    return html.escape(re.sub(r"[ \t\r\n]+", " ", s or ""), quote=False)


def italic(el):
    st = (el.get("style") or "").replace(" ", "").lower()
    return "font-style:italic" in st or any(k in ("italic", "i", "emph") for k in cls(el))


def bold(el):
    st = (el.get("style") or "").replace(" ", "").lower()
    return "font-weight:bold" in st


class GRenderer:
    def __init__(self, book, notes):
        self.book = book
        self.notes = notes
        self.pre = False        # inside <pre>: keep line breaks

    def inline(self, el, in_note=False):
        out = [esc(el.text, self.pre)] if el.text else []
        for c in el:
            t = tag(c)
            if t == "span" and "gnote" in cls(c) and not in_note:
                out.append(f"\u0000{self.notes.index[id(c)]}\u0000")
            elif not t or self.book.is_dropped(c) or (not in_note and self.notes.is_body(c)):
                pass
            elif t == "a" and (k := self.notes.ref(c)) is not None:
                if not in_note:                         # (inside a note it is the back-link)
                    out.append(f"\u0000{k}\u0000")      # replaced by the post's note number later
            elif t == "br":
                out.append("<br/>")
            elif t in ("i", "em", "cite", "dfn", "var") or (t == "span" and italic(c)):
                inner = self.inline(c, in_note)
                out.append(f"<i>{inner}</i>" if inner.strip() else inner)
            elif t in ("b", "strong") or (t == "span" and bold(c)):
                inner = self.inline(c, in_note)
                out.append(f"<b>{inner}</b>" if inner.strip() else inner)
            elif t in HEAD_TAGS or t in BLOCK_TAGS:
                out.append(" " + self.inline(c, in_note) + " ")
            else:
                out.append(self.inline(c, in_note))
            if c.tail:
                out.append(esc(c.tail, self.pre))
        return "".join(out)

    def para(self, el):
        inner = re.sub(r"^(\s|<br/>)+|(\s|<br/>)+$", "", self.inline(el))
        return f"<p>{inner}</p>" if norm(re.sub(r"<[^>]+>", "", inner)) else ""

    def verse(self, el):
        """A poem: one paragraph per stanza, one line per line, indents kept."""
        stanzas = [g for g in el.iter("div") if "group" in cls(g)] or [el]
        out = []
        for g in stanzas:
            lines = []
            for line in g.iter("div"):
                if "line" not in cls(line):
                    continue
                depth = max([int(k[2:]) for k in cls(line) if re.fullmatch(r"in\d+", k)] or [0])
                txt = self.inline(line).strip()
                if txt:
                    lines.append("\u00a0" * depth + txt)
            if lines:
                out.append("<p>" + "<br/>".join(lines) + "</p>")
        return out

    def block(self, el):
        """Return a list of rendered HTML blocks for one source block."""
        t = tag(el)
        if "linegroup" in cls(el):
            return self.verse(el)
        if t == "hr":
            return ["<hr/>"] if "tb" in cls(el) else []
        if t == "pre":
            self.pre = True
            try:
                inner = self.inline(el)
            finally:
                self.pre = False
            lines = [x.strip() for x in inner.split("<br/>")]
            lines = [x for x in lines if x]
            return ["<p>" + "<br/>".join(lines) + "</p>"] if lines else []
        if t == "blockquote" or (t in CONTAINERS and self.has_blocks(el)):
            inner = []
            loose = norm(el.text or "")
            if loose:
                inner.append(f"<p>{esc(loose)}</p>")
            for c in el:
                if not tag(c) or self.book.is_dropped(c) or self.notes.is_body(c):
                    pass
                elif tag(c) in BLOCK_TAGS or tag(c) in CONTAINERS or tag(c) in HEAD_TAGS:
                    inner.extend(self.block(c))
                else:
                    h = self.para(c)
                    if h:
                        inner.append(h)
                if c.tail and norm(c.tail):
                    inner.append(f"<p>{esc(c.tail.strip())}</p>")
            if t == "blockquote" and inner:
                return ["<blockquote>" + "".join(inner) + "</blockquote>"] if len(inner) < 12 else \
                    ["<blockquote>" + h + "</blockquote>" for h in inner]
            return inner
        if t in ("ul", "ol"):
            items = [self.inline(li).strip() for li in el if tag(li) == "li"]
            items = [i for i in items if i]
            return [f"<{t}>" + "".join(f"<li>{i}</li>" for i in items) + f"</{t}>"] if items else []
        if t == "dl":
            return [h for c in el if tag(c) in ("dt", "dd") for h in [self.para(c)] if h]
        if t == "table":
            rows = []
            for tr in el.iter("tr"):
                cells = [self.inline(c).strip() for c in tr if tag(c) in ("td", "th")]
                cells = [c for c in cells if norm(re.sub(r"<[^>]+>", "", c))]
                if cells:
                    rows.append("<p>" + " — ".join(cells) + "</p>")
            return rows
        if t in HEAD_TAGS:
            txt = text_of(el)
            return [f"<h4>{esc(txt)}</h4>"] if txt else []
        h = self.para(el)
        return [h] if h else []

    def has_blocks(self, el):
        for d in el.iterdescendants():
            if tag(d) in BLOCK_TAGS or tag(d) in HEAD_TAGS:
                if not self.book.is_dropped(d):
                    return True
        return False

    def note_html(self, k):
        body = self.notes.bodies[k]
        if tag(body) == "dd":
            parts = [self.inline(body, in_note=True)]
        else:
            ps = [c for c in body if tag(c) in ("p", "blockquote", "div")] if tag(body) == "div" else []
            parts = [self.inline(p, in_note=True) for p in ps] if ps else [self.inline(body, in_note=True)]
        s = " ".join(norm(p) for p in parts if norm(p))
        if "gnote" in cls(body):
            s = re.sub(r"^\[\s*(?:footnote|note)\s*:?\s*", "", s, flags=re.I)
            s = re.sub(r"\]\s*$", "", s)
            if self.book.note_drop and re.search(self.book.note_drop, s):
                return None
            return s.strip()
        s = LABEL_RE.sub("", s, count=1)
        s = re.sub(r"^\[\s*(.*?)\s*\]$", r"\1", s)
        return s.strip()


# ---------------------------------------------------------------------------
# Walking a book: events -> units
# ---------------------------------------------------------------------------


PG_END = re.compile(r"(?i)end of (the |this )?project gutenberg|start: full license")


def mark_bracket_notes(text, opener):
    """Old Gutenberg texts print footnotes inline as '[Footnote: ...]'. Wrap each in a
    <span class="gnote"> so it can be moved to the foot of the post like any other note.
    A note ends at its closing bracket, or at the end of the paragraph it began in."""
    out, pos = [], 0
    for m in re.finditer(opener, text):
        if m.start() < pos:
            continue
        depth, i = 0, m.start()
        end = None
        while i < len(text):
            c = text[i]
            if c == "[":
                depth += 1
            elif c == "]":
                depth -= 1
                if depth == 0:
                    end, close = i, i + 1
                    break
            elif c == "<" and re.match(r"</(p|pre|blockquote|div)>", text[i:i + 14], re.I):
                end, close = i, i
                break
            i += 1
        if end is None:
            continue
        out.append(text[pos:m.start()])
        out.append('<span class="gnote">' + text[m.start():end] + "</span>")
        pos = close
    out.append(text[pos:])
    return "".join(out)


def load(path, book=None):
    """Parse a Gutenberg HTML file, cutting off Project Gutenberg's end matter and licence."""
    with open(path, "rb") as f:
        data = f.read()
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("cp1252", errors="replace")
    text = re.sub(r"^\s*<\?xml[^>]*>", "", text)
    if book is not None and book.bracket_notes:
        text = mark_bracket_notes(text, book.bracket_notes)
    root = lhtml.document_fromstring(text)
    body = root.find("body")
    for el in body.iter():
        own = (el.text or "") if isinstance(el.tag, str) else ""
        if tag(el) and PG_END.search(own) and el is not body:
            # drop this element and everything after it
            node = el
            while node is not body:
                parent = node.getparent()
                for sib in list(node.itersiblings()):
                    parent.remove(sib)
                node = parent
            el.getparent().remove(el)
            break
    return root


def flatten(book, body, notes):
    """The book's content as a flat list of ('h', el), ('b', el), ('fn', el), ('t', text)."""
    events = []

    def structured(el):
        for d in el.iterdescendants():
            td = tag(d)
            if td in BLOCK_TAGS or td in HEAD_TAGS or book.is_heading(d) or notes.is_body(d) or "linegroup" in cls(d):
                if not any(book.is_dropped(a) for a in d.iterancestors() if a is not el):
                    return True
        return False

    def visit(el):
        if el.text and norm(el.text) and el is not body:
            events.append(("t", el.text))
        for c in el:
            t = tag(c)
            if t and not book.is_dropped(c) and not notes.is_body(c):
                if book.is_heading(c):
                    events.append(("h", c))
                elif "linegroup" in cls(c):
                    events.append(("b", c))
                elif book.inline_note and book.inline_note(c):
                    events.append(("fn", c))
                elif t in BLOCK_TAGS:
                    events.append(("b", c))
                elif structured(c):
                    visit(c)
                elif norm(c.text_content()):
                    events.append(("b", c))
            if c.tail and norm(c.tail):
                events.append(("t", c.tail))

    visit(body)
    return events


class GReader:
    """Reads one GBook into build.Unit objects, like build.RepoReader does for Standard Ebooks."""

    def __init__(self, book, Heading, Unit):
        self.book, self.Heading, self.Unit = book, Heading, Unit
        self.note_texts = {}        # ref -> note html
        self.seq = 0

    def note_html(self, ref):
        return self.note_texts.get(ref, "")

    def units(self):
        book = self.book
        out = []
        stack = []                  # (level, node_id, Heading)
        state = dict(unit=None)
        nid = [0]

        def new_unit():
            path = [(n, h) for _, n, h in stack] or [(f"{book.work_id}-0", self.Heading())]
            state["unit"] = self.Unit(path=path)
            return state["unit"]

        def flush():
            u = state["unit"]
            if u is not None and u.blocks:
                out.append(u)
            state["unit"] = None

        def add(htmls, renderer):
            u = state["unit"] or new_unit()
            if state.get("heading_marks") and htmls and htmls[0].endswith("</p>"):
                htmls = [htmls[0][:-4] + " " + state.pop("heading_marks") + "</p>"] + list(htmls[1:])
            for h in htmls:
                # number the notes this block references
                def num(m):
                    k = int(m.group(1))
                    note = renderer.note_html(k)
                    if note is None:            # an editor's note: left out
                        return ""
                    ref = f"{renderer_id}:{k}"
                    known = [n for n, r in u.notes if r == ref]
                    if known:                   # the same note referenced again
                        return f"[{known[0]}]"
                    self.seq += 1
                    n = str(self.seq)
                    self.note_texts[ref] = note
                    u.notes.append((n, ref))
                    return f"[{n}]"
                h = re.sub(r"\u0000(\d+)\u0000", num, h)
                h = re.sub(r"\[\[(\d+)\]\]", r"[\1]", h)
                bare = norm(re.sub(r"<[^>]+>|\[\d+\]", " ", h))
                if not bare:
                    marks = "".join(re.findall(r"\[\d+\]", h))
                    if marks and u.blocks:
                        u.blocks[-1] = re.sub(r"(</p>|</blockquote>|</li></[uo]l>)$", f" {marks}\\1", u.blocks[-1], count=1)
                    continue
                u.blocks.append(h)
                plain = re.sub(r"</?(?:br|p|li|ul|ol|blockquote|h4)\b[^>]*>", " ", h)
                plain = html.unescape(re.sub(r"<[^>]+>", "", plain))
                u.plain.append(norm(re.sub(r"\[\d+\]", "", plain)))
                u.words.append(len(WORD_RE.findall(plain)))

        def attach_inline_note(el, renderer):
            """A footnote printed in the flow of text: move it to the foot of the post. Where
            the text marks it ('[*]', '[**]'), the mark becomes the note's number; otherwise
            the number goes at the end of the paragraph the note follows."""
            u = state["unit"] or new_unit()
            for stars, text in split_inline_note(el):
                if not text or (book.note_drop and re.search(book.note_drop, text)):
                    continue
                self.seq += 1
                n = str(self.seq)
                ref = f"inline:{n}"
                self.note_texts[ref] = esc(text)
                u.notes.append((n, ref))
                mark = f"[{stars}]" if stars else None
                for j in range(len(u.blocks) - 1, max(-1, len(u.blocks) - 4), -1):
                    if mark and mark in u.blocks[j]:
                        u.blocks[j] = u.blocks[j].replace(mark, f"[{n}]", 1)
                        break
                else:
                    if u.blocks:
                        u.blocks[-1] = re.sub(r"(</p>|</blockquote>)$", f" [{n}]\\1", u.blocks[-1], count=1)
                        u.words[-1] += 1

        for fi, path in enumerate(book.paths()):
            root = load(path, book)
            body = root.find("body")
            notes = Notes(book, root)
            renderer = GRenderer(book, notes)
            renderer_id = fi
            events = flatten(book, body, notes)
            active = not book.start
            skip_level = skip_until = None
            i = 0
            while i < len(events):
                kind, el = events[i]
                i += 1
                if kind == "h":
                    txt = heading_text(el)
                    if not active:
                        if book.hit(book.start, el, txt):
                            active = True
                        else:
                            continue
                    elif book.hit(book.stop, el, txt):
                        break
                    lvl = book.level_of(el)
                    if skip_level is not None:
                        if lvl is not None and lvl <= skip_level:
                            skip_level = None
                        else:
                            continue
                    if skip_until is not None:
                        if not book.hit(skip_until, el, txt):
                            continue
                        skip_until = None
                    hit = [s for s in book.skip if book.hit(s if isinstance(s, str) else s[0], el, txt)]
                    if hit:
                        if isinstance(hit[0], tuple):
                            skip_until = hit[0][1]
                        else:
                            skip_level = lvl if lvl is not None else 99
                        continue
                    if lvl == 0:                    # a heading that only repeats the work's title
                        continue
                    if lvl is None:
                        if txt:
                            add([f"<h4>{esc(txt)}</h4>"], renderer)
                        continue
                    flush()
                    while stack and stack[-1][0] >= lvl:
                        stack.pop()
                    h = make_heading(self.Heading, txt)
                    if book.merge and re.search(book.merge, txt) and i < len(events) and events[i][0] == "h":
                        nxt = heading_text(events[i][1])
                        h.title = smart_case(re.sub(r"[.:]$", "", nxt))
                        i += 1
                    nid[0] += 1
                    stack.append((lvl, f"{book.work_id}-{nid[0]}", h))
                    marks = "".join(re.findall(r"\u0000\d+\u0000", renderer.inline(el)))
                    if marks:
                        state["heading_marks"] = marks    # a note on the title: numbered on the first paragraph
                    continue
                if not active or skip_level is not None or skip_until is not None:
                    continue
                if kind == "fn":
                    attach_inline_note(el, renderer)
                elif kind == "t":
                    add([f"<p>{esc(el.strip())}</p>"], renderer)
                else:
                    add(renderer.block(el), renderer)
            flush()
        return out


def split_inline_note(el):
    """An inline footnote block may hold several notes: '[* Gildas.] [** Bede.]' or
    '* Gildas. ** Bede.'. Returns (stars, text) pairs; stars is '' for '[Footnote: …]'."""
    t = re.sub(r"\s+", " ", el.text_content()).strip()
    out = []
    for p in re.split(r"(?:^|\s)(?=\[?\*+)", t):
        p = p.strip()
        if not p:
            continue
        m = re.match(r"\[?(\*+)\s*", p)
        stars = m.group(1) if m else ""
        p = p[m.end():] if m else re.sub(r"^\[\s*footnote\s*:?\s*", "", p, flags=re.I)
        if t.startswith("["):
            p = re.sub(r"\]\s*$", "", p)
        out.append((stars, p.strip()))
    return out
