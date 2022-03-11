package org.simpleyaml.configuration.comments;

import org.simpleyaml.configuration.ConfigurationOptions;
import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.utils.StringUtils;
import org.simpleyaml.utils.Validate;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;

public class KeyTree implements Iterable<KeyTree.Node> {

    private final KeyTree.Node root = new KeyTree.Node(null, 0, "");

    private final ConfigurationOptions options;

    public KeyTree(final ConfigurationOptions options) {
        Validate.notNull(options);
        this.options = options;
    }

    /**
     * Get the last node that can be a parent of a child with the indent provided.
     *
     * @param indent the indent to look for
     * @return the last most inner child that has less indent than the indent provided, or parent otherwise
     */
    public KeyTree.Node findParent(final int indent) {
        return this.findParent(this.root, indent);
    }

    /**
     * Get a child from its path.
     *
     * @param path the path of names to look for separated by {@link #options()} {@link ConfigurationOptions#pathSeparator()}
     * @return the child that has the provided path or null if not found
     */
    public KeyTree.Node get(final String path) {
        return this.root.get(path, false);
    }

    /**
     * Get a child from its path. It is created if it does not exist.
     *
     * @param path the path of names to look for separated by {@link #options()} {@link ConfigurationOptions#pathSeparator()}
     * @return the child that has the provided path
     */
    public KeyTree.Node getOrAdd(final String path) {
        return this.root.get(path, true);
    }

    public KeyTree.Node add(final String path) {
        return this.getOrAdd(path);
    }

    public Set<String> keys() {
        return this.root.keys();
    }

    public List<KeyTree.Node> children() {
        return this.root.children();
    }

    public Set<Map.Entry<String, KeyTree.Node>> entries() {
        return this.root.entries();
    }

    public ConfigurationOptions options() {
        return this.options;
    }

    @Override
    public String toString() {
        return this.root.toString();
    }

    @Override
    public Iterator<Node> iterator() {
        return this.root.iterator();
    }

    private KeyTree.Node findParent(final KeyTree.Node parent, final int indent) {
        final KeyTree.Node last = parent.getLast();
        if (last != null && last.indent < indent) {
            return this.findParent(last, indent);
        }
        return parent;
    }

    public class Node implements Iterable<KeyTree.Node> {

        private final String name;

        private final KeyTree.Node parent;

        private List<KeyTree.Node> children;
        private Map<String, KeyTree.Node> indexByName;
        private Map<Integer, KeyTree.Node> indexByElementIndex; // parent list

        private final int indent;

        private String comment;
        private String sideComment;

        private boolean isList; // parent
        private Integer listSize; // parent
        private Integer elementIndex; // children

        Node(final KeyTree.Node parent, final int indent, final String name, final boolean isList) {
            this.parent = parent;
            this.indent = indent;
            this.name = name;
            this.isList = isList;
        }

        Node(final KeyTree.Node parent, final int indent, final String name) {
            this(parent, indent, name, false);
        }

        public String getName() {
            return this.name;
        }

        public String getComment() {
            return this.comment;
        }

        public void setComment(final String comment) {
            this.comment = comment;
        }

        public String getSideComment() {
            return this.sideComment;
        }

        public void setSideComment(final String sideComment) {
            this.sideComment = sideComment;
        }

        public KeyTree.Node getParent() {
            return this.parent;
        }

        public boolean isRootNode() {
            return this.parent == null;
        }

        public boolean isFirstNode() {
            if (!this.isRootNode() && this.parent.isRootNode()) {
                KeyTree.Node first = this.parent.getFirst();
                if (first.getName() == null && this.parent.children.size() > 1) { // footer
                    first = this.parent.children.get(1);
                }
                if (first == this) {
                    final Iterator<String> keys = KeyTree.this.options.configuration().getKeys(false).iterator();
                    return !keys.hasNext() || keys.next().equals(first.getName());
                }
            }
            return false;
        }

        public int getIndentation() {
            return this.indent;
        }

        /**
         * Get a child from its path, or optionally add a new one if it is not created.
         *
         * @param path the path of children names to look for separated by {@link #options()} {@link ConfigurationOptions#pathSeparator()}
         * @param add if a new node must be added if it does not exist
         * @return the child that has the provided path or null if not found
         */
        protected KeyTree.Node get(final String path, boolean add) {
            KeyTree.Node node = null;
            if (path != null && (this.indexByName == null || !this.indexByName.containsKey(path))) {
                final int i = StringUtils.firstSeparatorIndex(path, KeyTree.this.options.pathSeparator());
                if (i >= 0) {
                    final String childPath = path.substring(0, i);
                    KeyTree.Node child = this.get(childPath, add);
                    if (child == null) {
                        return null;
                    }
                    return child.get(path.substring(i + 1), add);
                }
                Matcher listIndex = StringUtils.LIST_INDEX.matcher(path);
                if (listIndex.matches()) {
                    final String child = listIndex.group(1);
                    if (child != null && !child.isEmpty()) {
                        node = this.get(child, add);
                        if (node == null) {
                            return null;
                        }
                    } else {
                        node = this;
                    }
                    return node.getElement(Integer.parseInt(listIndex.group(2)), add);
                }
            }
            if (this.indexByName != null) {
                node = this.indexByName.get(path);
            }
            if (node == null && add) {
                node = this.add(path);
            }
            return node;
        }

        /**
         * Get a child from its path.
         *
         * @param path the path of children names to look for separated by {@link #options()} {@link ConfigurationOptions#pathSeparator()}
         * @return the child that has the provided path or null if not found
         */
        public KeyTree.Node get(final String path) {
            return this.get(path, false);
        }

        /**
         * Get a child list element from its index, or optionally add a new one if it is not created.
         * <p>
         * <br>If <code>i</code> is negative then gets the child at index <code>{@link #size()} + i</code>
         * <br>Example: <code>node.get(-1)</code> gets the last child of <code>node</code>
         * </p>
         * @param i the index of the child
         * @param add if a new node must be added if it does not exist
         * @return the child with index i or null if not found and not created
         */
        protected KeyTree.Node getElement(int i, boolean add) {
            KeyTree.Node child = null;
            if (this.isList && this.indexByElementIndex != null) {
                child = this.indexByElementIndex.get(i);
                if (child == null && !add) {
                    if (i < 0) {
                        child = this.indexByElementIndex.get(this.listSize + i);
                    } else {
                        child = this.indexByElementIndex.get(i - this.listSize);
                    }
                }
            } else if (!add) {
                child = this.get(i);
            }
            if (child == null && add) {
                child = this.addIndexed(i);
            }
            return child;
        }

        /**
         * Get a child list element from its index.
         * <p>
         * <br>If <code>i</code> is negative then gets the child element indexed by <code>{@link #size()} + i</code>
         * <br>Example: <code>node.get(-1)</code> gets the last child element of the <code>node</code> list.
         * </p>
         * @param i the index of the child element
         * @return the child with index i or null if not found
         */
        public KeyTree.Node getElement(int i) {
            return this.getElement(i, false);
        }

        /**
         * Get a child from its index.
         * <p>
         * <br>If <code>i</code> is negative then gets the child at index <code>{@link #size()} + i</code>
         * <br>Example: <code>node.get(-1)</code> gets the last child of <code>node</code>
         * </p>
         * @param i the index of the child
         * @return the child with index i or null if not found
         */
        public KeyTree.Node get(int i) {
            KeyTree.Node child = null;
            if (this.hasChildren()) {
                i = this.asListIndex(i, this.children.size());
                if (i >= 0 && i < this.children.size()) {
                    child = this.children.get(i);
                }
            }
            return child;
        }

        public KeyTree.Node getFirst() {
            if (!this.hasChildren()) {
                return null;
            }
            return this.children.get(0);
        }

        public KeyTree.Node getLast() {
            if (!this.hasChildren()) {
                return null;
            }
            return this.children.get(this.children.size() - 1);
        }

        public KeyTree.Node add(final String key) {
            return this.add(key, false, false);
        }

        public KeyTree.Node add(final int indent, final String key) {
            return this.add(indent, key, false, false);
        }

        protected KeyTree.Node add(final String key, final boolean isList, final boolean indexed) {
            return this.add(this == KeyTree.this.root ? 0 : this.indent + KeyTree.this.options.indent(), key, isList, indexed);
        }

        protected KeyTree.Node add(final int indent, final String key, final boolean isList, final boolean indexed) {
            final KeyTree.Node child = new KeyTree.Node(this, indent, key, isList);
            if (this.children == null) {
                this.children = new ArrayList<>();
            }
            this.children.add(child);
            if (!indexed) { // not already indexed
                if (this.indexByName == null) {
                    this.indexByName = new LinkedHashMap<>();
                }
                this.indexByName.put(key, child); // allow repetitions
            }
            return child;
        }

        public boolean hasChildren() {
            return this.children != null && !this.children.isEmpty();
        }

        public List<KeyTree.Node> children() {
            return this.hasChildren() ? Collections.unmodifiableList(this.children) : Collections.emptyList();
        }

        public Set<String> keys() {
            return this.indexByName != null ? Collections.unmodifiableSet(this.indexByName.keySet()) : Collections.emptySet();
        }

        public Set<Map.Entry<String, KeyTree.Node>> entries() {
            return this.indexByName != null ? Collections.unmodifiableSet(this.indexByName.entrySet()) : Collections.emptySet();
        }

        public int size() {
            return this.hasChildren() ? this.children.size() : 0;
        }

        public boolean isList() {
            return this.isList;
        }

        public void isList(int listSize) {
            this.isList = true;
            this.listSize = listSize;
        }

        public int isListNewElement() {
            this.isList(this.listSize == null ? 1 : this.listSize + 1);
            return this.listSize - 1; // list index
        }

        public void setElementIndex(int elementIndex) {
            if (this.parent != null) {
                if (this.parent.indexByElementIndex == null) {
                    this.parent.indexByElementIndex = new HashMap<>();
                } else if (this.elementIndex != null) {
                    this.parent.indexByElementIndex.remove(this.elementIndex);
                }

                this.elementIndex = elementIndex;

                this.parent.indexByElementIndex.put(this.elementIndex, this);
            }
        }

        public Integer getElementIndex() {
            return this.elementIndex;
        }

        protected Integer getElementOrChildIndex() {
            if (this.elementIndex == null && this.parent != null) {
                this.elementIndex = this.parent.children.lastIndexOf(this);
            }
            return this.elementIndex;
        }

        public String getPath() {
            if (this.parent == null || this.parent == KeyTree.this.root) {
                return this.name;
            } else if (this.parent.isList) {
                return indexedName(this.parent.getPath(), this.getElementOrChildIndex());
            }
            return this.parent.getPath() + KeyTree.this.options.pathSeparator() + this.name;
        }

        public String getPathWithName() { // name may be repeated in lists
            if (this.parent == null || this.parent == KeyTree.this.root) {
                return this.name;
            }
            return this.parent.getPath() + KeyTree.this.options.pathSeparator() + this.name;
        }

        private String indexedName(String name, int listIndex) {
            return name + "[" + listIndex + "]";
        }

        private KeyTree.Node addIndexed(final int i) {
            KeyTree.Node child = null;
            Object value = KeyTree.this.options.configuration().get(this.getPath());
            if (value != null) {
                if (value instanceof Collection) {
                    final int size = ((Collection<?>) value).size();
                    this.isList(size);

                    if (value instanceof List) {
                        final int index = this.asListIndex(i, size);
                        if (index >= 0 && index < size) {
                            Object item = ((List<?>) value).get(index);
                            child = this.add((item instanceof String || item instanceof Number) ? String.valueOf(item) : null, false, true);
                        }
                    }
                } else {
                    if (value instanceof ConfigurationSection) {
                        value = ((ConfigurationSection) value).getValues(false);
                    }
                    if (value instanceof Map) {
                        final int mapSize = ((Map<?,?>) value).size();
                        final int index = this.asListIndex(i, mapSize);
                        if (index >= 0 && index < mapSize) {
                            Object key = null;
                            Iterator<?> it = ((Map<?,?>) value).keySet().iterator();
                            int j = -1;
                            while (it.hasNext() && ++j <= index) {
                                key = it.next();
                            }
                            if (key != null) {
                                child = this.add(String.valueOf(key), false, true);
                            }
                        }
                    }
                }
            }
            if (child == null) {
                child = this.add(null, false, true);
            }
            child.setElementIndex(i);
            return child;
        }

        private int asListIndex(int i, int size) {
            if (i < 0) {
                return size + i; // convert negative to positive indexing
            }
            return i;
        }

        protected void clearNode() {
            if (this.children != null) {
                this.children.clear();
                this.children = null;
            }
            if (this.indexByName != null) {
                this.indexByName.clear();
                this.indexByName = null;
            }
            if (this.indexByElementIndex != null) {
                this.indexByElementIndex.clear();
                this.indexByElementIndex = null;
            }
            if (this.parent != null) {
                if (this.parent.indexByName != null && this.parent.indexByName.get(this.name) == this) {
                    this.parent.indexByName.remove(this.name);

                    if (this.parent.indexByElementIndex != null && this.elementIndex != null) {
                        this.parent.indexByElementIndex.remove(this.elementIndex);
                    }
                }
            }
        }

        protected boolean clearIf(final Predicate<Node> condition, final boolean removeFromParent) {
            if (this.children != null) {
                this.children.removeIf(child -> child.clearIf(condition, false));
            }
            if (!this.hasChildren() && condition.test(this)) {
                this.clearNode();
                if (removeFromParent && this.parent != null) {
                    this.parent.children.remove(this);

                }
                return true;
            }
            return false;
        }

        public boolean clearIf(final Predicate<Node> condition) {
            return this.clearIf(condition, true);
        }

        public void clear() {
            this.clearNode();

            if (this.parent != null) {
                this.parent.children.remove(this);
            }
        }

        @Override
        public Iterator<Node> iterator() {
            return this.hasChildren() ? this.children.iterator() : Collections.emptyIterator();
        }

        @Override
        public String toString() {
            return "{" +
                "indent=" + this.indent +
                ", path='" + this.getPath() + '\'' +
                ", name='" + this.name + '\'' +
                ", comment='" + this.comment + '\'' +
                ", side='" + this.sideComment + '\'' +
                ", children=" + this.children +
                '}';
        }

    }
}
