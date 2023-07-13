package Database;

class Node {
    int key;
    Node parent, left, right;

    public Node(int key) {
        this.key = key;
        parent = left = right = null;
    }
}

public class SplayTree {
    private Node root;

    // Constructor
    public SplayTree() {
        root = null;
    }

    // Zig rotation
    private void zig(Node x) {
        Node p = x.parent;
        if (p != null) {
            Node g = p.parent;
            if (x == p.left) {
                p.left = x.right;
                if (x.right != null) {
                    x.right.parent = p;
                }
                x.right = p;
                p.parent = x;
            } else {
                p.right = x.left;
                if (x.left != null) {
                    x.left.parent = p;
                }
                x.left = p;
                p.parent = x;
            }
            x.parent = g;
            if (g != null) {
                if (p == g.left) {
                    g.left = x;
                } else {
                    g.right = x;
                }
            }
        }
        root = x;
    }

    // Zig-zig rotation
    private void zigZig(Node x) {
        Node p = x.parent;
        Node g = p.parent;
        if (x == p.left) {
            Node c = x.right;
            p.left = c;
            if (c != null) {
                c.parent = p;
            }
            x.right = p;
            p.parent = x;
        } else {
            Node c = x.left;
            p.right = c;
            if (c != null) {
                c.parent = p;
            }
            x.left = p;
            p.parent = x;
        }
        g.left = x.right;
        if (x.right != null) {
            x.right.parent = g;
        }
        x.right = g;
        g.parent = x;
        x.parent = null;
        root = x;
    }

    // Splay operation
    private void splay(Node x) {
        while (x.parent != null) {
            Node p = x.parent;
            Node g = p.parent;
            if (g == null) {
                zig(x);
            } else if ((x == p.left && p == g.left) || (x == p.right && p == g.right)) {
                zigZig(x);
            } else {
                zig(x);
                zig(x);
            }
        }
    }

    // Insert a key into the tree
    public void insert(int key) {
        Node newNode = new Node(key);
        Node x = root;
        Node parent = null;

        while (x != null) {
            parent = x;
            if (key < x.key) {
                x = x.left;
            } else if (key > x.key) {
                x = x.right;
            } else {
                // Key already exists, move it to the root
                splay(x);
                return;
            }
        }

        newNode.parent = parent;
        if (parent == null) {
            root = newNode;
        } else if (key < parent.key) {
            parent.left = newNode;
        } else {
            parent.right = newNode;
        }

        splay(newNode);
    }

    // Search for a key in the tree and splay it to the root
    public boolean search(int key) {
        Node x = root;

        while (x != null) {
            if (key < x.key) {
                x = x.left;
            } else if (key > x.key) {
                x = x.right;
            } else {
                // Key found, splay it to the root
                splay(x);
                return true;
            }
        }

        return false; // Key not found
    }

    // Remove a key from the tree
    public void remove(int key) {
        Node node = searchNode(key);
        if (node == null) {
            return; // Key not found, nothing to remove
        }

        splay(node);

        if (node.left == null) {
            replaceWith(node, node.right);
        } else if (node.right == null) {
            replaceWith(node, node.left);
        } else {
            Node minRight = minimumNode(node.right);
            if (minRight.parent != node) {
                replaceWith(minRight, minRight.right);
                minRight.right = node.right;
                minRight.right.parent = minRight;
            }
            replaceWith(node, minRight);
            minRight.left = node.left;
            minRight.left.parent = minRight;
        }
    }

    // Helper method to replace one node with another
    private void replaceWith(Node oldNode, Node newNode) {
        if (oldNode.parent == null) {
            root = newNode;
        } else if (oldNode == oldNode.parent.left) {
            oldNode.parent.left = newNode;
        } else {
            oldNode.parent.right = newNode;
        }
        if (newNode != null) {
            newNode.parent = oldNode.parent;
        }
    }

    // Helper method to find the node with the minimum key in a subtree
    private Node minimumNode(Node node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    // Helper method to search for a node with a given key in the tree
    private Node searchNode(int key) {
        Node x = root;

        while (x != null) {
            if (key < x.key) {
                x = x.left;
            } else if (key > x.key) {
                x = x.right;
            } else {
                // Key found
                return x;
            }
        }

        return null; // Key not found
    }
}