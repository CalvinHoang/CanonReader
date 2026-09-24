#!/usr/bin/env python3
"""
Independent check that the posts reproduce the source text exactly.

For every work, the author's words are read straight from the Standard Ebooks XHTML
(a separate, simpler traversal than build.py uses) and compared, word for word and in
order, with the words of that work's posts laid end to end. Headings, the notes the
pipeline appends, and the few labels it inserts are excluded from both sides.
"""
import html
import os
import re
import sqlite3
import sys

from lxml import etree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402
import gutenberg  # noqa: E402

X, E = build.X, build.EPUB_TYPE
WORD = re.compile(r"[\w’'-]+", re.UNICODE)
HEADS = {"h1", "h2", "h3", "h4", "h5", "h6", "hgroup", "header"}


def words_of_source(el, repo, out):
    """Collect words under el, skipping headings, noterefs and excluded sections."""
    name = build.local(el)
    if not name:
        return
    t = build.types(el)
    if name in ("section", "article"):
        if el.get("id") in build.SKIP_IDS or el.get("id") in build.SKIP_REPO_IDS.get(repo, set()):
            return
        if t & build.SKIP_TYPES:
            return
    if name in HEADS:
        # only the section's own leading heading is packaging; later headings are content
        parent = el.getparent()
        first = next((c for c in parent if build.local(c)), None)
        if first is el or (first is not None and build.local(first) in HEADS and el is next(
                (c for c in parent if build.local(c) in HEADS), None)):
            if el.tail:
                out.extend(WORD.findall(el.tail))
            return
    if name == "a" and ("noteref" in t or "backlink" in t):
        if el.tail:
            out.extend(WORD.findall(el.tail))
        return
    if el.text:
        out.extend(WORD.findall(el.text))
    for c in el:
        words_of_source(c, repo, out)
        if not build.local(c) and c.tail:
            out.extend(WORD.findall(c.tail))
    if el.tail and el.getparent() is not None and name not in ("section", "article"):
        pass


def source_words(repo, root_filter=None):
    out = []
    for path in build.spine_files(repo):
        doc = etree.parse(path)
        body = doc.getroot().find(X + "body")
        for top in body:
            if build.local(top) not in ("section", "article"):
                continue
            if root_filter:
                for el in top.iter(X + "section", X + "article"):
                    if root_filter(el):
                        words_of_source_tail_safe(el, repo, out)
            else:
                words_of_source_tail_safe(top, repo, out)
    return out


def words_of_source_tail_safe(el, repo, out):
    # lxml keeps text after a child in child.tail; walk explicitly to keep order right
    def walk(e):
        name = build.local(e)
        if not name:
            return
        t = build.types(e)
        if name in ("section", "article"):
            if e.get("id") in build.SKIP_IDS or e.get("id") in build.SKIP_REPO_IDS.get(repo, set()):
                return
            if t & build.SKIP_TYPES:
                return
        skip_self = False
        if name in HEADS:
            parent = e.getparent()
            lead = None
            for c in parent:
                if build.local(c) in HEADS:
                    lead = c
                    break
                if build.local(c) in build.BLOCK_TAGS or build.local(c) in ("section", "article"):
                    break
            skip_self = lead is e
        if name == "a" and ("noteref" in t or "backlink" in t):
            skip_self = True
        if not skip_self:
            if e.text:
                out.extend(WORD.findall(e.text))
            for c in e:
                walk(c)
                if c.tail:
                    out.extend(WORD.findall(c.tail))
    walk(el)


def words(text):
    # a bracketed number is a note marker, on both sides of the comparison
    return WORD.findall(re.sub(r"\[\d+\]", " ", text))


def pg_source_words(book):
    """A Gutenberg book's text: every word between its start and stop headings, minus the
    sections it skips, headings, page numbers, footnotes and note markers."""
    out = []
    for path in book.paths():
        root = gutenberg.load(path, book)
        notes = gutenberg.Notes(book, root)
        st = {"active": not book.start, "skip": None, "done": False}

        def taking():
            return st["active"] and st["skip"] is None and not st.get("until")

        def heading(e):
            txt = gutenberg.heading_text(e)
            if not st["active"]:
                st["active"] = book.hit(book.start, e, txt)
                if not st["active"]:
                    return
            elif book.hit(book.stop, e, txt):
                st["done"] = True
                return
            lvl = book.level_of(e)
            if st["skip"] is not None:
                if lvl is not None and lvl <= st["skip"]:
                    st["skip"] = None
                else:
                    return
            if st.get("until"):
                if not book.hit(st["until"], e, txt):
                    return
                st["until"] = None
            for s in book.skip:
                if isinstance(s, tuple) and book.hit(s[0], e, txt):
                    st["until"] = s[1]
                    return
                if isinstance(s, str) and book.hit(s, e, txt):
                    st["skip"] = lvl if lvl is not None else 99
                    return

        def walk(e):
            t = gutenberg.tag(e)
            if not t or st["done"]:
                return
            if book.is_dropped(e) or notes.is_body(e):
                return
            if book.inline_note and book.inline_note(e):
                return
            if book.is_heading(e):
                heading(e)
                return
            if t == "a" and notes.ref(e) is not None:
                return
            if e.text and taking():
                out.extend(words(e.text))
            for c in e:
                walk(c)
                if st["done"]:
                    return
                if c.tail and taking():
                    out.extend(words(c.tail))

        walk(root.find("body"))
    return out


def post_words(db, work_id):
    out = []
    for (h,) in db.execute("SELECT html FROM posts WHERE work_id=? ORDER BY work_seq", (work_id,)):
        h = h.split("<hr/><h4>Notes</h4>")[0]
        h = re.sub(r"<h4>.*?</h4>", " ", h)
        h = re.sub(r"^<p><i>Persons of the dialogue:</i>.*?</p>(<p><b>Scene:</b>.*?</p>)?", " ", h)
        h = re.sub(r"\[\d+\]", " ", h)                     # note markers
        h = re.sub(r"<p>\d+\.\u00a0", "<p>", h)            # numbers put on long numbered lists
        h = re.sub(r"<p>•\u00a0", "<p>", h)
        h = re.sub(r"<b>([^<]*?):</b>", r"<b>\1</b>", h)    # the colon after a speaker
        out.extend(WORD.findall(html.unescape(re.sub(r"<[^>]+>", " ", h))))
    return out


def compare(label, src, got):
    if src == got:
        print(f"OK    {label}: {len(got):,} words identical")
        return True
    if "".join(src) == "".join(got):
        # identical characters; only word breaks differ (superscripts such as H<sub>2</sub>O, 4<sup>me</sup>)
        print(f"OK    {label}: identical text ({len(got):,} words; superscripts joined)")
        return True
    n = next((i for i, (a, b) in enumerate(zip(src, got)) if a != b), min(len(src), len(got)))
    print(f"DIFF  {label}: source {len(src):,} words, posts {len(got):,} words; first difference at word {n}")
    print("      source:", " ".join(src[max(0, n - 12):n + 12]))
    print("      posts: ", " ".join(got[max(0, n - 12):n + 12]))
    return False


def main():
    db = sqlite3.connect(os.path.join(build.OUT_DIR, "canon.db"))
    ok = True
    for repo, _, wid, _, _ in build.SINGLE_WORK_REPOS:
        ok &= compare(wid, source_words(repo), post_words(db, wid))
    # Plato: per dialogue, the '-text' section only
    repo = "plato_dialogues_benjamin-jowett"
    for did in build.PLATO_ORDER:
        src = []
        for path in build.spine_files(repo):
            doc = etree.parse(path)
            for el in doc.getroot().iter(X + "section"):
                if el.get("id") == f"{did}-text":
                    words_of_source_tail_safe(el, repo, src)
        ok &= compare(did, src, post_words(db, did))
    # Shakespeare
    for repo in sorted(os.listdir(build.SE_DIR)):
        if not repo.startswith("william-shakespeare") or repo.endswith("_poetry"):
            continue
        wid = repo.split("_")[-1]
        ok &= compare(wid, source_words(repo), post_words(db, wid))
    repo = "william-shakespeare_poetry"
    for path in build.spine_files(repo):
        doc = etree.parse(path)
        body = doc.getroot().find(X + "body")
        if "bodymatter" not in (body.get(E) or ""):
            continue
        for top in body:
            if build.local(top) in ("section", "article"):
                src = []
                words_of_source_tail_safe(top, repo, src)
                ok &= compare(top.get("id"), src, post_words(db, top.get("id")))
    # Project Gutenberg works
    for book in gutenberg.BOOKS:
        ok &= compare(book.work_id, pg_source_words(book), post_words(db, book.work_id))
    print("ALL IDENTICAL" if ok else "SOME DIFFERENCES")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
