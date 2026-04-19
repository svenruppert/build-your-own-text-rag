package com.svenruppert.flow.views.module03;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts plain text from a Markdown document while preserving the
 * heading hierarchy as structured metadata. This is the bridge between
 * the Markdown source format and the three chunkers downstream, which
 * all operate on plain text.
 *
 * <h2>Strategy</h2>
 * <ul>
 *   <li>Markdown syntax (emphasis markers, link URLs, heading {@code #}
 *       characters) is stripped: only the visible text survives.</li>
 *   <li>Heading titles remain in the plain text so that downstream
 *       consumers still see the document's table of contents; their
 *       offsets are recorded as {@link HeadingInfo} so that a
 *       structure-aware chunker can align chunks with section
 *       boundaries.</li>
 *   <li>Block elements are separated by a blank line so the plain text
 *       stays readable and {@code BreakIterator} still recognises
 *       sentence boundaries correctly inside paragraphs.</li>
 *   <li>Code blocks (fenced and indented) keep their literal content,
 *       line breaks included -- truncating them would damage the
 *       semantics ingestion is supposed to preserve.</li>
 * </ul>
 *
 * <p>Instances are cheap to construct and thread-safe. A fresh commonmark
 * {@link Parser} is built per extraction: participants will read this
 * and see how little commonmark actually requires.
 */
public final class MarkdownTextExtractor {

    /**
     * The result of an extraction pass: the plain text and the
     * recovered heading hierarchy, with offsets pointing into
     * {@link #plainText()}.
     */
    public record ExtractionResult(String plainText, List<HeadingInfo> headings) {

        public ExtractionResult {
            plainText = plainText == null ? "" : plainText;
            headings = headings == null ? List.of() : List.copyOf(headings);
        }
    }

    public ExtractionResult extract(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new ExtractionResult("", List.of());
        }
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        Visitor visitor = new Visitor();
        document.accept(visitor);
        return new ExtractionResult(visitor.plainText(), visitor.headings());
    }

    /**
     * AST visitor that flattens the Markdown tree into plain text and
     * collects heading positions. Block elements push a blank-line
     * separator so paragraphs, lists and code blocks remain readable
     * after extraction.
     */
    private static final class Visitor extends AbstractVisitor {

        private final StringBuilder out = new StringBuilder();
        private final List<HeadingInfo> headings = new ArrayList<>();

        String plainText() {
            return out.toString();
        }

        List<HeadingInfo> headings() {
            return headings;
        }

        // ---------- block elements --------------------------------

        @Override
        public void visit(Heading heading) {
            beginBlock();
            int offset = out.length();
            InlineTextCollector inline = new InlineTextCollector();
            heading.accept(inline);
            String title = inline.text().trim();
            out.append(title);
            headings.add(new HeadingInfo(heading.getLevel(), title, offset));
        }

        @Override
        public void visit(Paragraph paragraph) {
            beginBlock();
            InlineTextCollector inline = new InlineTextCollector();
            paragraph.accept(inline);
            out.append(inline.text().stripTrailing());
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            beginBlock();
            // Keep the literal content verbatim -- removing trailing
            // newlines matches how IDEs and viewers display fenced blocks.
            out.append(codeBlock.getLiteral().stripTrailing());
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            beginBlock();
            out.append(codeBlock.getLiteral().stripTrailing());
        }

        @Override
        public void visit(BulletList bulletList) {
            beginBlock();
            visitListItems(bulletList);
        }

        @Override
        public void visit(OrderedList orderedList) {
            beginBlock();
            visitListItems(orderedList);
        }

        private void visitListItems(Node list) {
            // Each ListItem becomes its own line; the bullet character is
            // deliberately dropped -- the resulting plain text is meant
            // for the embedding model, not for a human viewer.
            Node child = list.getFirstChild();
            boolean first = true;
            while (child != null) {
                if (child instanceof ListItem item) {
                    if (!first) out.append('\n');
                    InlineTextCollector inline = new InlineTextCollector();
                    item.accept(inline);
                    out.append(inline.text().stripTrailing());
                    first = false;
                }
                child = child.getNext();
            }
        }

        /**
         * Emits a separator before a block element so paragraphs, lists
         * and code blocks are clearly delimited in the plain-text output.
         */
        private void beginBlock() {
            if (out.isEmpty()) return;
            // Ensure a blank line between blocks; collapse if already there.
            while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                out.deleteCharAt(out.length() - 1);
            }
            if (out.length() < 2
                    || out.charAt(out.length() - 1) != '\n'
                    || out.charAt(out.length() - 2) != '\n') {
                if (out.charAt(out.length() - 1) != '\n') out.append('\n');
                out.append('\n');
            }
        }
    }

    /**
     * A secondary visitor that collects the plain-text content of an
     * inline subtree (emphasis markers dropped, link URLs dropped,
     * link/emphasis text preserved).
     */
    private static final class InlineTextCollector extends AbstractVisitor {

        private final StringBuilder buffer = new StringBuilder();

        String text() {
            return buffer.toString();
        }

        @Override
        public void visit(Text text) {
            buffer.append(text.getLiteral());
        }

        @Override
        public void visit(Emphasis emphasis) {
            visitChildren(emphasis);
        }

        @Override
        public void visit(StrongEmphasis strong) {
            visitChildren(strong);
        }

        @Override
        public void visit(Link link) {
            // Drop the URL; keep the visible link text.
            visitChildren(link);
        }

        @Override
        public void visit(SoftLineBreak soft) {
            buffer.append(' ');
        }

        @Override
        public void visit(HardLineBreak hard) {
            buffer.append('\n');
        }
    }
}
