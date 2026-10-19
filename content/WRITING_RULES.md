# Rules for study content

These apply to everything in `content/written/` and to anything Claude writes inside the app.
Check new content against them before adding it.

## One right answer, like the real JLPT

Every multiple-choice question must have exactly one option that a native speaker would accept,
given the context shown. A "wrong" option must not be:

- a more or less polite form of the right one (あそこ / あちら, どこ / どちら, いる / いらっしゃる)
- another word that also fits (これ / ここ for a room you are in, に / へ for a direction)
- another grammar pattern or verb form that also works (～たら / ～ば / ～と, ～ので / ～から)
- a synonym, or another valid reading of the same word or kanji
- for reading, listening and stories: an option the text also supports

If two options could work, add short context to the question, e.g. （{部屋|へや}の{中|なか}で）,
or swap the extra one for an option that is clearly wrong.

The app follows the same rule when it builds word, kanji and phrase questions: it leaves out wrong
options whose meaning overlaps with the right answer, and other readings of the same word or kanji.

## Furigana markup

Every kanji sits in `{kanji|reading}` with a hiragana reading; okurigana stays outside.
`python3 tools/validate.py <file>` checks the markup, the answer index and duplicate options.
