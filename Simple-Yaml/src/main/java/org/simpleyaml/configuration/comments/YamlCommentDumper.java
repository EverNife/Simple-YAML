package org.simpleyaml.configuration.comments;

import java.io.IOException;
import java.io.Reader;
import java.util.stream.Collectors;

public class YamlCommentDumper extends YamlCommentReader {

    protected final YamlCommentMapper yamlCommentMapper;

    protected StringBuilder builder;

    public YamlCommentDumper(final YamlCommentMapper yamlCommentMapper, final Reader reader) {
        super(yamlCommentMapper.options(), reader);
        this.yamlCommentMapper = yamlCommentMapper;
    }

    /**
     * Merge comments from the comment mapper with lines from the reader.
     *
     * @return the resulting String
     * @throws IOException if any problem while reading arise
     */
    public String dump() throws IOException {
        String result;

        if (this.yamlCommentMapper == null) {
            result = this.reader.lines().collect(Collectors.joining("\n"));
        } else {
            this.builder = new StringBuilder();

            while (this.nextLine()) {
                if (!this.isComment()) { // Skip comments from the reader (keep only comments from the comment mapper)
                    final KeyTree.Node readerNode = this.track();
                    KeyTree.Node commentNode = null;
                    if (readerNode != null) {
                        commentNode = this.getNode(readerNode.getPath());
                    }
                    this.appendBlockComment(commentNode);
                    this.builder.append(this.currentLine);
                    this.appendSideComment(commentNode);
                    this.builder.append('\n');
                }
            }

            // Append end of file (footer) comment (null path), if found
            this.appendBlockComment(this.getNode(null));

            result = this.builder.toString();
        }

        this.close();

        return result;
    }

    @Override
    protected KeyTree.Node getNode(final String path) {
        return this.yamlCommentMapper.getNode(path);
    }

    protected void appendBlockComment(final KeyTree.Node node) {
        final String comment = this.getRawComment(node, CommentType.BLOCK);
        if (comment != null) {
            this.builder.append(comment);
            if (!comment.endsWith("\n")) {
                this.builder.append('\n');
            }
        }
    }

    protected void appendSideComment(final KeyTree.Node node) throws IOException {
        final String comment = this.getRawComment(node, CommentType.SIDE);
        if (comment != null) {
            this.readValue();
            this.builder.append(comment);
        }
    }

    @Override
    protected void readValue() throws IOException {
        if (this.hasChar()) {
            this.readIndent();

            if (this.isInQuote()) {
                // Could be a multi line value
                this.appendMultiline();
            }
        }
    }

    protected void appendMultiline() throws IOException {
        boolean hasChar = this.hasChar() && this.nextChar();

        if (hasChar) {
            this.stage = ReaderStage.VALUE;
        }

        while (hasChar) {
            hasChar = this.nextChar();
        }

        if (this.isMultiline() && this.nextLine()) {
            this.builder.append('\n');
            this.builder.append(this.currentLine);
            this.appendMultiline();
        }
    }

}