#!/usr/bin/env python3
"""
Canon pipeline: Standard Ebooks sources -> blog-sized posts -> SQLite asset.

The author's words are never edited. The pipeline only:
  * chooses where to cut (at the author's own section / speech / paragraph boundaries),
  * converts Standard Ebooks XHTML into the small HTML subset the app renders,
  * adds packaging: a title (the original heading, or the passage's own opening words),
    a source line (work / chapter / part), speaker labels for dialogue, and notes.

Run:  ./fetch_sources.sh          (clones the Standard Ebooks sources into pipeline/se)
      python3 build.py            (writes out/canon.db, out/report.txt and the app asset)
      python3 verify.py           (checks every work word for word against the source)
"""
import gzip
import html
import json
import shutil
import math
import os
import re
import sqlite3
import sys
from dataclasses import dataclass, field

from lxml import etree

HERE = os.path.dirname(os.path.abspath(__file__))
SE_DIR = os.path.join(HERE, "se")
APP_ASSET = os.path.join(HERE, "..", "app", "src", "main", "assets", "canon.db.gz")
OUT_DIR = os.path.join(HERE, "out")

X = "{http://www.w3.org/1999/xhtml}"
EPUB_TYPE = "{http://www.idpf.org/2007/ops}type"
OPF = {"o": "http://www.idpf.org/2007/opf"}

SPLIT_OVER = 2000          # prose units longer than this get cut into parts
SPLIT_OVER_DRAMA = 2600    # scenes are allowed to run longer before cutting
TARGET_PART = 1400         # aim for parts around this long
TINY = 80                  # aphorisms shorter than this are grouped
GROUP_TO = 320             # ...until a group reaches roughly this many words

# ---------------------------------------------------------------------------
# Catalogue: authors, works, reading order
# ---------------------------------------------------------------------------

AUTHORS = [
    # id, display name, lifespan, one-line note (shown on the author page)
    ("plato", "Plato", "c. 428–348 BC", "Dialogues, in Benjamin Jowett’s translation."),
    ("shakespeare", "William Shakespeare", "1564–1616", "The plays, sonnets and poems."),
    ("hume", "David Hume", "1711–1776", "The Treatise and the first Enquiry."),
    ("mill", "John Stuart Mill", "1806–1873", "On Liberty, The Subjection of Women and the Autobiography."),
    ("james", "William James", "1842–1910", "Varieties, Pragmatism and A Pluralistic Universe."),
    ("nietzsche", "Friedrich Nietzsche", "1844–1900", "Zarathustra, Beyond Good and Evil and the Genealogy."),
]

# Plato: a sensible reading order (trial and death first, then early, middle, late).
PLATO_ORDER = [
    "euthyphro", "apology", "crito", "phaedo",
    "charmides", "laches", "lysis", "ion", "euthydemus", "protagoras", "gorgias", "meno",
    "symposium", "phaedrus", "cratylus", "republic",
    "theaetetus", "parmenides", "sophist", "statesman", "philebus", "timaeus", "critias", "laws",
    "lesser-hippias", "first-alcibiades", "menexenus", "eryxias",
]

# Shakespeare: approximate order of composition.
SHAKESPEARE_ORDER = [
    ("the-two-gentlemen-of-verona", 1590), ("the-taming-of-the-shrew", 1591),
    ("henry-vi-part-ii", 1591), ("henry-vi-part-iii", 1591), ("henry-vi-part-i", 1592),
    ("titus-andronicus", 1592), ("richard-iii", 1593), ("edward-iii", 1593),
    ("venus-and-adonis", 1593), ("the-rape-of-lucrece", 1594), ("the-comedy-of-errors", 1594),
    ("loves-labours-lost", 1595), ("richard-ii", 1595), ("romeo-and-juliet", 1595),
    ("a-midsummer-nights-dream", 1595), ("king-john", 1596), ("the-merchant-of-venice", 1596),
    ("henry-iv-part-i", 1596), ("the-merry-wives-of-windsor", 1597), ("henry-iv-part-ii", 1597),
    ("much-ado-about-nothing", 1598), ("henry-v", 1599), ("julius-caesar", 1599),
    ("the-passionate-pilgrim", 1599), ("as-you-like-it", 1599), ("hamlet", 1600),
    ("twelfth-night", 1601), ("the-pheonix-and-the-turtle", 1601), ("troilus-and-cressida", 1602),
    ("measure-for-measure", 1603), ("othello", 1604), ("alls-well-that-ends-well", 1605),
    ("timon-of-athens", 1605), ("king-lear", 1606), ("macbeth", 1606),
    ("antony-and-cleopatra", 1606), ("pericles", 1607), ("coriolanus", 1608),
    ("the-winters-tale", 1609), ("sonnets", 1609), ("a-lovers-complaint", 1609),
    ("cymbeline", 1610), ("the-tempest", 1611), ("henry-viii", 1613), ("the-two-noble-kinsmen", 1613),
]

SINGLE_WORK_REPOS = [
    # repo, author, work id, year, translator
    ("david-hume_a-treatise-of-human-nature", "hume", "a-treatise-of-human-nature", 1739, None),
    ("david-hume_an-enquiry-concerning-human-understanding", "hume", "an-enquiry-concerning-human-understanding", 1748, None),
    ("john-stuart-mill_on-liberty", "mill", "on-liberty", 1859, None),
    ("john-stuart-mill_the-subjection-of-women", "mill", "the-subjection-of-women", 1869, None),
    ("john-stuart-mill_the-autobiography-of-john-stuart-mill", "mill", "autobiography", 1873, None),
    ("william-james_the-varieties-of-religious-experience", "james", "the-varieties-of-religious-experience", 1902, None),
    ("william-james_pragmatism", "james", "pragmatism", 1907, None),
    ("william-james_a-pluralistic-universe", "james", "a-pluralistic-universe", 1909, None),
    ("friedrich-nietzsche_thus-spake-zarathustra_thomas-common", "nietzsche", "thus-spake-zarathustra", 1883, "Thomas Common"),
    ("friedrich-nietzsche_beyond-good-and-evil_helen-zimmern", "nietzsche", "beyond-good-and-evil", 1886, "Helen Zimmern"),
    ("friedrich-nietzsche_the-genealogy-of-morals_horace-b-samuel", "nietzsche", "the-genealogy-of-morals", 1887, "Horace B. Samuel"),
]

INCLUDE_TYPES = {
    "chapter", "part", "division", "volume", "z3998:subchapter", "z3998:scene", "z3998:poem",
    "preface", "introduction", "prologue", "epilogue", "afterword", "appendix", "conclusion",
    "foreword", "z3998:fiction", "z3998:non-fiction", "z3998:drama",
}
SKIP_TYPES = {
    "titlepage", "imprint", "colophon", "copyright-page", "halftitlepage", "endnotes",
    "dedication", "epigraph", "toc", "loi", "z3998:dramatis-personae", "z3998:editorial-note",
    "acknowledgments",
}
SKIP_IDS = {"editors-note"}
# Sections written by editors rather than the author, per repo
SKIP_REPO_IDS = {
    "john-stuart-mill_on-liberty": {"introduction"},   # W. L. Courtney's 1901 introduction
}

BLOCK_TAGS = {"p", "blockquote", "table", "ul", "ol", "hr", "div", "figure", "pre", "dl"}
HEAD_TAGS = {"h1", "h2", "h3", "h4", "h5", "h6", "hgroup", "header"}

# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------


def local(el):
    return etree.QName(el).localname if isinstance(el.tag, str) else ""


def types(el):
    return set((el.get(EPUB_TYPE) or "").split())


def text_of(el):
    return " ".join("".join(el.itertext()).split())


WORD_RE = re.compile(r"[\w’'-]+", re.UNICODE)


def count_words(s):
    return len(WORD_RE.findall(s))


ROMAN = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}


def roman_to_int(s):
    s = s.strip().upper()
    if not s or any(c not in ROMAN for c in s):
        return None
    total, prev = 0, 0
    for c in reversed(s):
        v = ROMAN[c]
        total = total - v if v < prev else total + v
        prev = max(prev, v)
    return total


def esc(s):
    return html.escape(re.sub(r"[ \t\r\n]+", " ", s), quote=False)


# ---------------------------------------------------------------------------
# Headings
# ---------------------------------------------------------------------------


@dataclass
class Heading:
    label: str = ""      # "Act", "Section", "Lecture"
    ordinal: str = ""    # "III", "14", "65A"
    title: str = ""      # "Of the Idea of Necessary Connection"
    subtitle: str = ""

    def name(self):
        """How this heading reads in a source line, e.g. 'Section XIV: Of the Idea…' or 'Act III'."""
        num = " ".join(x for x in (self.label, self.ordinal) if x)
        title = f"{self.title}: {self.subtitle}" if self.title and self.subtitle else self.title
        if title and num:
            return f"{num}: {title}"
        if title:
            return title
        if num:
            if not self.label and re.fullmatch(r"\d+[A-Za-z]?", self.ordinal):
                return f"§{self.ordinal}"
            return num
        return ""

    def descriptive(self):
        """True when the heading carries real words, not just a number."""
        t = self.title
        if not t:
            return False
        bare = re.sub(r"[^A-Za-z]", "", t)
        if not bare or roman_to_int(bare) is not None:
            return False
        if re.fullmatch(r"(?i)(book|part|chapter|section|lecture|essay|act|scene|volume|appendix)\s+[\w.]+", t):
            return False
        return True


def parse_heading(section):
    """Read the heading of a section/article from its first heading-like child."""
    h = Heading()
    head = None
    for c in section:
        if local(c) in HEAD_TAGS:
            head = c
            break
        if local(c) in BLOCK_TAGS or local(c) in ("section", "article"):
            break
    if head is None:
        return h, None
    for el in head.iter():
        if not isinstance(el.tag, str):
            continue
        t = types(el)
        txt = text_of(el)
        if "se:label" in t and not h.label:
            h.label = txt
        elif ("z3998:ordinal" in t or "z3998:roman" in t) and not h.ordinal and el is not head:
            h.ordinal = txt
        elif "subtitle" in t and not h.subtitle:
            h.subtitle = txt
        elif "title" in t and not h.title:
            h.title = txt
    ht = types(head)
    if not (h.label or h.ordinal or h.title):
        # A bare heading: <h3>63</h3>, <h2>Crito</h2>, <h3 epub:type="z3998:ordinal">1</h3>
        txt = text_of(head)
        if "z3998:ordinal" in ht or re.fullmatch(r"[0-9]+[A-Za-z]?|[IVXLCDM]+", txt):
            h.ordinal = txt
        else:
            h.title = txt
    elif local(head) != "hgroup" and not h.title and "z3998:ordinal" in ht and not h.ordinal:
        h.ordinal = text_of(head)
    return h, head


# ---------------------------------------------------------------------------
# Units: a run of content blocks under one heading path
# ---------------------------------------------------------------------------


@dataclass
class Unit:
    path: list                 # list of (node_id, Heading) from work root down to this node
    blocks: list = field(default_factory=list)   # rendered HTML strings
    words: list = field(default_factory=list)    # words per block
    plain: list = field(default_factory=list)    # plain text per block (for titles/excerpts)
    notes: list = field(default_factory=list)    # note numbers referenced, in order
    kind: str = "prose"        # prose | drama | dialogue | poem

    @property
    def total(self):
        return sum(self.words)


class Renderer:
    """Converts SE XHTML blocks into the app's small HTML subset, faithfully."""

    def __init__(self, notes):
        self.notes = notes      # note id -> note html
        self.used = []

    # --- inline -----------------------------------------------------------
    def inline(self, el, top=True):
        out = []
        if top and el.text:
            out.append(esc(el.text))
        for c in el:
            if not isinstance(c.tag, str):
                if c.tail:
                    out.append(esc(c.tail))
                continue
            name = local(c)
            t = types(c)
            if name == "a" and "backlink" in t:
                pass
            elif name == "a" and "noteref" in t:
                ref = (c.get("href") or "").split("#")[-1]
                num = text_of(c)
                if ref in self.notes:
                    self.used.append((num, ref))
                out.append(f"[{esc(num)}]")
            elif name == "br":
                out.append("<br/>")
            elif name in ("i", "em", "cite", "dfn", "var"):
                out.append("<i>" + self.inline(c) + "</i>")
            elif name in ("b", "strong"):
                out.append("<b>" + self.inline(c) + "</b>")
            elif name == "span" and any(k.startswith("i") and k[1:].isdigit() for k in (c.get("class") or "").split()):
                # verse indentation (class="i1", "i2" …): keep the indent as spaces
                depth = max(int(k[1:]) for k in c.get("class").split() if k.startswith("i") and k[1:].isdigit())
                out.append(" " * depth + self.inline(c))
            elif name in ("img", "hr"):
                pass
            else:
                out.append(self.inline(c))
            if c.tail:
                out.append(esc(c.tail))
        return "".join(out)

    def para_html(self, p):
        inner = self.inline(p).strip()
        return f"<p>{inner}</p>" if inner else ""

    # --- blocks -------------------------------------------------------------
    def block(self, el):
        """Return a list of (html, plain_text) for one source block."""
        name = local(el)
        if name == "p":
            h = self.para_html(el)
            return [(h, text_of(el))] if h else []
        if name == "hr":
            return [("<hr/>", "")]
        if name in ("blockquote", "div", "figure"):
            inner = []
            plain = []
            for c in el:
                for h, pl in self.block(c):
                    inner.append(h)
                    plain.append(pl)
            if not inner and text_of(el):
                inner = [f"<p>{self.inline(el).strip()}</p>"]
                plain = [text_of(el)]
            if not inner:
                return []
            if name == "blockquote":
                if sum(count_words(x) for x in plain) > 900 and len(inner) > 1:
                    return [("<blockquote>" + h + "</blockquote>", pl) for h, pl in zip(inner, plain)]
                return [("<blockquote>" + "".join(inner) + "</blockquote>", " ".join(plain))]
            return list(zip(inner, plain))
        if name in ("ul", "ol"):
            items = []
            for li in el:
                if local(li) != "li":
                    continue
                if any(local(c) in ("p", "blockquote") for c in li):
                    items.append(" ".join(self.inline(c).strip() for c in li if local(c) in ("p", "blockquote")))
                else:
                    items.append(self.inline(li).strip())
            if not items:
                return []
            if count_words(text_of(el)) > 900:
                start = int(el.get("start") or 1)
                lis = [li for li in el if local(li) == "li"]
                return [(f"<p>{start + k}.\u00a0{it}</p>" if name == "ol" else f"<p>•\u00a0{it}</p>", text_of(li))
                        for k, (it, li) in enumerate(zip(items, lis))]
            return [(f"<{name}>" + "".join(f"<li>{i}</li>" for i in items) + f"</{name}>", text_of(el))]
        if name == "table":
            return self.table(el)
        if name in HEAD_TAGS:
            h = Heading()
            txt = text_of(el)
            return [(f"<h4>{esc(txt)}</h4>", txt)] if txt else []
        if name == "pre":
            return [(f"<p>{esc(text_of(el))}</p>", text_of(el))]
        txt = text_of(el)
        return [(f"<p>{self.inline(el).strip()}</p>", txt)] if txt else []

    def table(self, table):
        """Drama and dialogue tables: one row per speech or stage direction."""
        rows = []
        own_rows = [tr for tr in table.iter(X + "tr")
                    if next(a for a in tr.iterancestors() if local(a) == "table") is table]
        for tr in own_rows:
            cells = [c for c in tr if local(c) in ("td", "th")]
            nonempty = [c for c in cells if text_of(c)]
            if (len(nonempty) > 1 and not any("z3998:persona" in types(c) for c in cells)
                    and not any(local(x) in BLOCK_TAGS for c in cells for x in c)):
                # an ordinary data table (e.g. James's two columns): one line per row
                row = " — ".join(self.inline(c).strip() for c in nonempty)
                rows.append((f"<p>{row}</p>", " ".join(text_of(c) for c in nonempty)))
                continue
            if not cells:
                continue
            persona = ""
            if len(cells) >= 2 and "z3998:persona" in types(cells[0]):
                persona = text_of(cells[0])
                speech = cells[1:]
            elif len(cells) >= 2 and not text_of(cells[0]):
                speech = cells[1:]
            else:
                speech = cells
            parts, plain = [], []
            for cell in speech:
                subs = [c for c in cell if local(c) in BLOCK_TAGS]
                if subs:
                    for c in cell:
                        if local(c) == "p":
                            parts.append(self.inline(c).strip())
                        elif local(c) in BLOCK_TAGS:
                            for h, _ in self.block(c):
                                parts.append(h[3:-4] if h.startswith("<p>") and h.endswith("</p>") else h)
                    plain.append(text_of(cell))
                else:
                    s = self.inline(cell).strip()
                    if s:
                        parts.append(s)
                        plain.append(text_of(cell))
            parts = [p for p in parts if p]
            if not parts and not persona:
                continue
            verse = any("z3998:verse" in types(c) or "z3998:song" in types(c) for c in speech)
            said = " ".join(plain).strip()
            has_block = any(is_block_html(p) for p in parts)
            if persona and verse and not has_block:
                body = "<br/>".join(parts)
                rows.append((f"<p><b>{esc(persona)}</b><br/>{body}</p>", said))
            elif persona:
                # Long prose speeches become one block per paragraph so they can be cut between
                # paragraphs; the speaker label stays on the first.
                if is_block_html(parts[0]):
                    rows.append((f"<p><b>{esc(persona)}:</b></p>", persona))
                    rest = parts
                else:
                    rows.append((f"<p><b>{esc(persona)}:</b> {parts[0]}</p>", plain_first(cell_plains(speech)) or said))
                    rest = parts[1:]
                for r in rest:
                    rows.append((wrap_p(r), re.sub(r"<[^>]+>", "", r)))
            else:
                for r in parts:
                    rows.append((wrap_p(r), re.sub(r"<[^>]+>", "", r)))
        return rows


def is_block_html(h):
    return h.startswith(("<ol", "<ul", "<blockquote", "<hr", "<h4", "<p>"))


def wrap_p(h):
    return h if is_block_html(h) else f"<p>{h}</p>"


def cell_plains(cells):
    out = []
    for cell in cells:
        subs = [c for c in cell if local(c) in ("p", "blockquote", "div")]
        out.extend(text_of(c) for c in subs) if subs else out.append(text_of(cell))
    return out


def plain_first(plains):
    return plains[0] if plains else ""


# ---------------------------------------------------------------------------
# Reading a repo
# ---------------------------------------------------------------------------


def spine_files(repo):
    base = os.path.join(SE_DIR, repo, "src", "epub")
    opf = etree.parse(os.path.join(base, "content.opf"))
    man = {i.get("id"): i.get("href") for i in opf.findall(".//o:manifest/o:item", OPF)}
    return [os.path.join(base, man[r.get("idref")]) for r in opf.findall(".//o:spine/o:itemref", OPF)
            if man[r.get("idref")].startswith("text/")]


def load_notes(repo):
    path = os.path.join(SE_DIR, repo, "src", "epub", "text", "endnotes.xhtml")
    notes = {}
    if not os.path.exists(path):
        return notes
    doc = etree.parse(path)
    for li in doc.iter(X + "li"):
        nid = li.get("id")
        if not nid:
            continue
        notes[nid] = li
    return notes


class RepoReader:
    """Walks one repo in spine order and yields Units, tracking headings across files."""

    def __init__(self, repo, include_section=None):
        self.repo = repo
        self.notes_xml = load_notes(repo)
        self.renderer = Renderer(self.notes_xml)
        self.headings = {}     # node id -> Heading
        self.parent = {}       # node id -> parent id (from nesting or data-parent)
        self.include_section = include_section

    def ancestry(self, node_id):
        chain = []
        seen = set()
        while node_id and node_id not in seen:
            seen.add(node_id)
            chain.append(node_id)
            node_id = self.parent.get(node_id)
        return list(reversed(chain))

    def units(self):
        for path in spine_files(self.repo):
            doc = etree.parse(path)
            body = doc.getroot().find(X + "body")
            for top in body:
                if local(top) not in ("section", "article"):
                    continue
                yield from self.walk(top, None)

    def keep(self, sec):
        if sec.get("id") in SKIP_IDS or sec.get("id") in SKIP_REPO_IDS.get(self.repo, set()):
            return False
        t = types(sec)
        if t & SKIP_TYPES:
            return False
        if self.include_section is not None:
            return self.include_section(sec)
        return True

    def walk(self, sec, parent_id):
        if not self.keep(sec):
            return
        sid = sec.get("id")
        heading, head_el = parse_heading(sec)
        self.headings[sid] = heading
        self.parent[sid] = sec.get("data-parent") or parent_id
        unit = None

        def flush():
            nonlocal unit
            if unit is not None and unit.blocks:
                yield unit
            unit = None

        for c in sec:
            name = local(c)
            if c is head_el or not name:
                continue
            if name in ("section", "article"):
                yield from flush()
                yield from self.walk(c, sid)
                continue
            if unit is None:
                unit = Unit(path=[(n, self.headings.get(n, Heading())) for n in self.ancestry(sid)])
                if "z3998:scene" in types(sec):
                    unit.kind = "drama"
            self.renderer.used = []
            for h, plain in self.renderer.block(c):
                unit.blocks.append(h)
                unit.plain.append(plain)
                unit.words.append(count_words(re.sub(r"<[^>]+>", " ", h)))
            unit.notes.extend(self.renderer.used)
        yield from flush()

    def note_html(self, ref):
        li = self.notes_xml.get(ref)
        if li is None:
            return ""
        parts = []
        r = Renderer({})
        for c in li:
            if local(c) == "p":
                parts.append(r.inline(c).strip())
            elif local(c) in ("blockquote", "ul", "ol", "table"):
                parts.extend(re.sub(r"^<p>|</p>$", "", h) for h, _ in r.block(c))
        return " ".join(p for p in parts if p)


# ---------------------------------------------------------------------------
# Posts
# ---------------------------------------------------------------------------


@dataclass
class Post:
    author: str
    work: str
    work_title: str
    title: str
    section: str
    html: str
    excerpt: str
    words: int
    source_words: int          # words of the author's text (for verification)
    work_seq: int = 0


SENT_END = re.compile(r"(?<=[.!?;:])[’”)]?\s+(?=[“‘(]?[A-Z])")


def opening_words(plains, skip_persona=None):
    """A title from the passage's own first words: the first substantial sentence."""
    for plain in plains[:6]:
        s = plain.strip()
        if skip_persona and s.startswith(skip_persona):
            pass
        if not s:
            continue
        s = re.sub(r"^\s*(\[\d+\]\s*)", "", s)
        sentence = SENT_END.split(s, maxsplit=1)[0].strip()
        words = sentence.split()
        if len(words) < 5 and len(plains) > 1:
            continue
        if len(words) > 16:
            sentence = " ".join(words[:13]).rstrip(",;:—–-") + "…"
        return sentence.rstrip(" ,;:—–-")
    return (plains[0].split(".")[0] if plains else "").strip()[:90]


def strip_persona(plain):
    """Dialogue rows start with the speaker's name; drop it for title purposes."""
    return plain


def section_line(work_title, path):
    names = []
    for nid, h in path:
        n = h.name()
        if n and n != work_title and n not in names:
            names.append(n)
    return " · ".join(names)


def split_blocks(unit, over, target):
    """Cut a long unit between blocks into parts of roughly equal length."""
    total = unit.total
    if total <= over:
        return [list(range(len(unit.blocks)))]
    n = max(2, round(total / target))
    per = total / n
    parts, cur, acc = [], [], 0
    for i, w in enumerate(unit.words):
        starts_heading = unit.blocks[i].startswith("<h4")
        if cur and acc >= per * 0.6 and (acc + w > per * 1.15 or (starts_heading and acc >= per * 0.8)):
            parts.append(cur)
            cur, acc = [], 0
        cur.append(i)
        acc += w
    if cur:
        parts.append(cur)
    if len(parts) > 1 and sum(unit.words[i] for i in parts[-1]) < per * 0.35:
        parts[-2].extend(parts.pop())
    return parts


def build_work(reader, author, work_id, work_title, units, is_sonnets=False, is_plato=False):
    posts = []

    def notes_block(note_refs):
        if not note_refs:
            return ""
        seen, items = set(), []
        for num, ref in note_refs:
            if ref in seen:
                continue
            seen.add(ref)
            items.append(f"<p>[{esc(num)}] {reader.note_html(ref)}</p>")
        return '<hr/><h4>Notes</h4>' + "".join(items)

    # Fold stray fragments (an epigraph under a book heading, a signature line) into the next unit.
    folded = []
    carry = None
    for u in units:
        if carry is not None:
            u.blocks[:0], u.plain[:0], u.words[:0], u.notes[:0] = carry.blocks, carry.plain, carry.words, carry.notes
            carry = None
        h = u.path[-1][1]
        numbered = bool(h.ordinal and not h.title)
        if u.total < 25 and u.kind != "drama" and not numbered and not is_sonnets:
            carry = u
            continue
        folded.append(u)
    if carry is not None:
        if folded:
            f = folded[-1]
            f.blocks += carry.blocks; f.plain += carry.plain; f.words += carry.words; f.notes += carry.notes
        else:
            folded.append(carry)
    units = folded

    # Group runs of tiny numbered units (aphorisms, apophthegms) into one post.
    grouped = []
    i = 0
    while i < len(units):
        u = units[i]
        h = u.path[-1][1]
        numbered = h.ordinal and not h.title
        if numbered and u.total < TINY and not is_sonnets:
            group = [u]
            j = i + 1
            while j < len(units):
                v = units[j]
                vh = v.path[-1][1]
                same_parent = v.path[:-1] == u.path[:-1] or [p[0] for p in v.path[:-1]] == [p[0] for p in u.path[:-1]]
                if not (vh.ordinal and not vh.title and v.total < TINY * 2.5 and same_parent):
                    break
                if sum(x.total for x in group) >= GROUP_TO:
                    break
                group.append(v)
                j += 1
            grouped.append(group)
            i = j
        else:
            grouped.append([u])
            i += 1

    for group in grouped:
        first = group[0]
        head = first.path[-1][1]
        is_scene = first.kind == "drama" or head.label == "Scene"

        if len(group) > 1:
            blocks, plains, words, notes = [], [], [], []
            for u in group:
                hh = u.path[-1][1]
                marker = hh.name()
                blocks.append(f"<h4>{esc(marker)}</h4>")
                plains.append("")
                words.append(0)
                blocks.extend(u.blocks)
                plains.extend(u.plain)
                words.extend(u.words)
                notes.extend(u.notes)
            first_name = group[0].path[-1][1].name()
            last_name = group[-1].path[-1][1].name().lstrip("§")
            parent_line = section_line(work_title, first.path[:-1])
            sec = " · ".join(x for x in (parent_line, f"{first_name}–{last_name}") if x)
            title = opening_words([p for p in plains if p])
            html_body = "".join(blocks) + notes_block(notes)
            posts.append(Post(author, work_id, work_title, title, sec, html_body,
                              make_excerpt(plains), sum(words), sum(words)))
            continue

        u = first
        over = SPLIT_OVER_DRAMA if is_scene else SPLIT_OVER
        pieces = split_blocks(u, over, TARGET_PART)
        base_line = section_line(work_title, u.path)
        for k, idxs in enumerate(pieces):
            blocks = [u.blocks[i] for i in idxs]
            plains = [u.plain[i] for i in idxs]
            words = sum(u.words[i] for i in idxs)
            # notes referenced inside this piece
            piece_text = "".join(blocks)
            note_refs = [(n, r) for n, r in u.notes if f"[{n}]" in piece_text]
            part_tag = f"Part {k + 1} of {len(pieces)}" if len(pieces) > 1 else ""
            sec = " · ".join(x for x in (base_line, part_tag) if x)
            title = make_title(work_title, u, plains, k, len(pieces), is_scene, is_sonnets, is_plato)
            posts.append(Post(author, work_id, work_title, title, sec,
                              "".join(blocks) + notes_block(note_refs),
                              make_excerpt(plains), words, words))
    for n, p in enumerate(posts):
        p.work_seq = n
    return posts


def make_title(work_title, u, plains, k, n, is_scene, is_sonnets, is_plato):
    head = u.path[-1][1]
    if is_sonnets and head.ordinal:
        num = roman_to_int(head.ordinal) or head.ordinal
        return f"Sonnet {num}: {first_line(u.blocks)}"
    if is_scene:
        names = [h.name() for _, h in u.path if h.name() and h.name() != work_title]
        base = f"{work_title}, " + ", ".join(names) if names else work_title
        return base if n == 1 else f"{base}, part {k + 1}"
    if k == 0 and head.descriptive():
        return head.title
    if k == 0 and not head.title and head.label and head.ordinal and len(u.path) >= 2:
        # e.g. Enquiry 'Part I' under a titled section: use the titled parent + part
        parent = u.path[-2][1]
        if parent.descriptive():
            return f"{parent.title}, {head.label} {head.ordinal}"
    return opening_words([p for p in plains if p])


def first_line(blocks):
    """First verse line of a poem: the text before the first line break."""
    for b in blocks:
        line = html.unescape(re.sub(r"<[^>]+>", "", b.split("<br/>")[0])).strip()
        if line:
            return line.strip("\u2003 ").rstrip(",;:")
    return ""


def make_excerpt(plains, limit=240):
    text = " ".join(p for p in plains if p).strip()
    text = re.sub(r"\s+", " ", text)
    if len(text) <= limit:
        return text
    return text[:limit].rsplit(" ", 1)[0] + "…"


# ---------------------------------------------------------------------------
# Corpus assembly
# ---------------------------------------------------------------------------


def repo_title(repo):
    opf = etree.parse(os.path.join(SE_DIR, repo, "src", "epub", "content.opf"))
    t = opf.find(".//{http://purl.org/dc/elements/1.1/}title")
    return t.text.strip()


def collect():
    works = []   # dicts: id, author, title, year, translator, posts

    # --- single-work repos -----------------------------------------------------------------
    for repo, author, wid, year, translator in SINGLE_WORK_REPOS:
        title = repo_title(repo)
        reader = RepoReader(repo)
        units = [u for u in reader.units()]
        posts = build_work(reader, author, wid, title, units)
        works.append(dict(id=wid, author=author, title=title, year=year, translator=translator,
                          posts=posts, repo=repo))

    # --- Plato: one work per dialogue, dialogue text only (no Jowett introductions) ---------
    repo = "plato_dialogues_benjamin-jowett"
    reader = RepoReader(repo)
    by_dialogue = {}
    titles = {}
    for path in spine_files(repo):
        doc = etree.parse(path)
        for art in doc.getroot().iter(X + "article", X + "section"):
            sid = art.get("id") or ""
            if not sid.endswith("-text"):
                continue
            did = sid[: -len("-text")]
            h, _ = parse_heading(art)
            titles[did] = h.title or did.replace("-", " ").title()
            units = list(reader.walk(art, None))
            # Persons of the dialogue + scene, from the dramatis personae section
            dp = [c for c in art if local(c) == "section" and "z3998:dramatis-personae" in types(c)]
            if dp and units:
                persons = [text_of(li) for li in dp[0].iter(X + "li")]
                scene_p = [c for c in dp[0] if local(c) == "p" and "Scene" in text_of(c)]
                intro = ""
                if persons:
                    intro += "<p><i>Persons of the dialogue:</i> " + esc(", ".join(persons)) + "</p>"
                if scene_p:
                    intro += reader.renderer.para_html(scene_p[0])
                if intro:
                    units[0].blocks.insert(0, intro)
                    units[0].plain.insert(0, "")
                    units[0].words.insert(0, 0)
            by_dialogue[did] = units
    for order, did in enumerate(PLATO_ORDER):
        if did not in by_dialogue:
            print("warning: Plato dialogue missing:", did, file=sys.stderr)
            continue
        title = titles[did]
        posts = build_work(reader, "plato", did, title, by_dialogue[did], is_plato=True)
        works.append(dict(id=did, author="plato", title=title, year=order, translator="Benjamin Jowett",
                          posts=posts, repo=repo))
    missing = set(by_dialogue) - set(PLATO_ORDER)
    if missing:
        print("warning: Plato dialogues not in order list:", missing, file=sys.stderr)

    # --- Shakespeare: plays are repos; poetry repo holds several works --------------------
    sh = {}
    for repo in sorted(os.listdir(SE_DIR)):
        if not repo.startswith("william-shakespeare"):
            continue
        if repo == "william-shakespeare_poetry":
            continue
        wid = repo.split("_")[-1]
        title = repo_title(repo)
        reader = RepoReader(repo)
        units = list(reader.units())
        sh[wid] = (title, build_work(reader, "shakespeare", wid, title, units), repo)
    repo = "william-shakespeare_poetry"
    reader = RepoReader(repo)
    for path in spine_files(repo):
        doc = etree.parse(path)
        body = doc.getroot().find(X + "body")
        if "bodymatter" not in (body.get(EPUB_TYPE) or ""):
            continue
        for top in body:
            if local(top) not in ("section", "article"):
                continue
            wid = top.get("id")
            h, _ = parse_heading(top)
            title = h.title or wid.replace("-", " ").title()
            units = list(reader.walk(top, None))
            # dedications to patrons are skipped (SKIP_TYPES); keep the poem text
            sh[wid] = (title, build_work(reader, "shakespeare", wid, title, units,
                                         is_sonnets=(wid == "sonnets")), repo)
    for order, (wid, year) in enumerate(SHAKESPEARE_ORDER):
        if wid not in sh:
            print("warning: Shakespeare work missing:", wid, file=sys.stderr)
            continue
        title, posts, repo = sh[wid]
        works.append(dict(id=wid, author="shakespeare", title=title, year=year, translator=None,
                          posts=posts, repo=repo))
    extra = set(sh) - {w for w, _ in SHAKESPEARE_ORDER}
    if extra:
        print("warning: Shakespeare works not in order list:", extra, file=sys.stderr)
    return works


# ---------------------------------------------------------------------------
# Feed order: every author blogs through their works in order; authors interleave in
# proportion to how much they wrote, so the whole cast stays on the blog until the end.
# ---------------------------------------------------------------------------


def interleave(works):
    queues = {}
    for a, *_ in AUTHORS:
        ws = [w for w in works if w["author"] == a]
        ws.sort(key=lambda w: w["year"])
        queues[a] = [p for w in ws for p in w["posts"]]
    totals = {a: sum(p.words for p in q) or 1 for a, q in queues.items()}
    done = {a: 0 for a in queues}
    pos = {a: 0 for a in queues}
    feed = []
    last = None
    while any(pos[a] < len(queues[a]) for a in queues):
        live = [a for a in queues if pos[a] < len(queues[a])]
        # pick the author furthest behind their share; avoid the same author twice in a row
        live.sort(key=lambda a: (done[a] / totals[a], a))
        pick = live[0]
        if pick == last and len(live) > 1:
            pick = live[1]
        p = queues[pick][pos[pick]]
        pos[pick] += 1
        done[pick] += p.words
        feed.append(p)
        last = pick
    return feed


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------


def write_db(works, feed, path):
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.executescript(
        """
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE authors (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, years TEXT NOT NULL, note TEXT NOT NULL,
            sort INTEGER NOT NULL, post_count INTEGER NOT NULL, words INTEGER NOT NULL);
        CREATE TABLE works (
            id TEXT PRIMARY KEY, author_id TEXT NOT NULL, title TEXT NOT NULL, year INTEGER,
            translator TEXT, sort INTEGER NOT NULL, post_count INTEGER NOT NULL, words INTEGER NOT NULL);
        CREATE TABLE posts (
            id INTEGER PRIMARY KEY,           -- position in the feed (0 = first post ever)
            author_id TEXT NOT NULL, work_id TEXT NOT NULL, work_seq INTEGER NOT NULL,
            title TEXT NOT NULL, section TEXT NOT NULL, excerpt TEXT NOT NULL,
            html TEXT NOT NULL, words INTEGER NOT NULL);
        CREATE INDEX posts_work ON posts(work_id, work_seq);
        CREATE INDEX posts_author ON posts(author_id, id);
        CREATE VIRTUAL TABLE posts_fts USING fts4(title, body, tokenize=unicode61);
        """
    )
    for sort, (aid, name, years, note) in enumerate(AUTHORS):
        aposts = [p for p in feed if p.author == aid]
        db.execute("INSERT INTO authors VALUES (?,?,?,?,?,?,?)",
                   (aid, name, years, note, sort, len(aposts), sum(p.words for p in aposts)))
    for sort, w in enumerate(works):
        db.execute("INSERT INTO works VALUES (?,?,?,?,?,?,?,?)",
                   (w["id"], w["author"], w["title"], w["year"] if w["author"] != "plato" else None,
                    w["translator"], sort, len(w["posts"]), sum(p.words for p in w["posts"])))
    for p in feed:
        p.html = re.sub(r"\s*<br/>\s*", "<br/>", p.html)
        p.html = re.sub(r"<p>\s+", "<p>", p.html)
        p.html = re.sub(r"\s+</p>", "</p>", p.html)
    for i, p in enumerate(feed):
        db.execute("INSERT INTO posts VALUES (?,?,?,?,?,?,?,?,?)",
                   (i, p.author, p.work, p.work_seq, p.title, p.section, p.excerpt, p.html, p.words))
        body = re.sub(r"<[^>]+>", " ", p.html)
        db.execute("INSERT INTO posts_fts(docid, title, body) VALUES (?,?,?)", (i, p.title, html.unescape(body)))
    db.execute("INSERT INTO meta VALUES ('version', ?)", (str(len(feed)) + "-" + str(sum(p.words for p in feed)),))
    db.execute("INSERT INTO meta VALUES ('post_count', ?)", (str(len(feed)),))
    db.commit()
    db.execute("INSERT INTO posts_fts(posts_fts) VALUES ('optimize')")
    db.commit()
    db.execute("VACUUM")
    db.close()


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    works = collect()
    # dedupe work ids (Plato and Shakespeare ids are distinct already)
    ids = [w["id"] for w in works]
    assert len(ids) == len(set(ids)), "duplicate work ids"
    feed = interleave(works)
    write_db(works, feed, os.path.join(OUT_DIR, "canon.db"))

    lines = []
    for a, name, *_ in AUTHORS:
        ap = [p for p in feed if p.author == a]
        lines.append(f"{name}: {len(ap)} posts, {sum(p.words for p in ap):,} words")
        for w in [w for w in works if w["author"] == a]:
            ws = [p.words for p in w["posts"]]
            lines.append(f"   {w['title']}: {len(ws)} posts, median {sorted(ws)[len(ws)//2] if ws else 0} words, max {max(ws) if ws else 0}")
    lines.append(f"TOTAL: {len(feed)} posts, {sum(p.words for p in feed):,} words")
    report = "\n".join(lines)
    print(report)
    with open(os.path.join(OUT_DIR, "report.txt"), "w") as f:
        f.write(report + "\n")
    # Install into the app. Remember to bump versionCode in app/build.gradle.kts so
    # existing installs pick up the new database.
    with open(os.path.join(OUT_DIR, "canon.db"), "rb") as src, gzip.open(APP_ASSET, "wb", compresslevel=9) as dst:
        shutil.copyfileobj(src, dst)
    print("wrote", os.path.relpath(APP_ASSET, HERE))
    with open(os.path.join(OUT_DIR, "sample.json"), "w") as f:
        json.dump([dict(id=i, author=p.author, work=p.work_title, title=p.title, section=p.section,
                        words=p.words) for i, p in enumerate(feed)], f, indent=0, ensure_ascii=False)


if __name__ == "__main__":
    main()
