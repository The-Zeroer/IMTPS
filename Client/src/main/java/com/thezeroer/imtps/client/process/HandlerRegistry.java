package com.thezeroer.imtps.client.process;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 处理器注册表
 *
 * @author NiZhanBo
 * @since 2025/11/30
 * @version 1.0.0
 */
public class HandlerRegistry<H> {
    /** 路径匹配器，支持多路径（单 handler 的路径规则） */
    public static class PathMatcher {
        private final List<List<Short>> path = new ArrayList<>();
        /** 深度节点匹配 */
        public PathMatcher(Short... path) {
            if (path != null && path.length > 0) {
                for (Short p : path) {
                    this.path.add(Collections.singletonList(p));
                }
            } else if (path == null) {
                this.path.add(Collections.singletonList(null));
            }
        }
        /** 支持链式增加路径, 广度节点匹配, 每次调用添加一层 */
        public PathMatcher addPath(Short... path) {
            if (path != null && path.length > 0) {
                List<Short> newPath = new ArrayList<>(Arrays.asList(path));
                if (newPath.contains(null)) {
                    this.path.add(Collections.singletonList(null));
                } else {
                    this.path.add(newPath);
                }
            } else {
                this.path.add(Collections.singletonList(null));
            }
            return this;
        }

        public String formatPath() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            if (!this.path.isEmpty()) {
                for (List<Short> paths : this.path) {
                    if (paths != null) {
                        sb.append("(").append(paths.stream().map(String::valueOf).collect(Collectors.joining("|"))).append(")");
                    } else {
                        sb.append("*");
                    }
                    sb.append('-');
                }
                sb.deleteCharAt(sb.length() - 1);
            } else {
                sb.append("*");
            }
            return sb.append("]").toString();
        }
    }
    private class TrieNode {
        private final HashMap<Short, TrieNode> children = new HashMap<>();
        private H handler;

        public TrieNode getOrCreateChild(Short key) {
            return children.computeIfAbsent(key, k -> new TrieNode());
        }
    }

    private final TrieNode root = new TrieNode();

    public void register(PathMatcher matcher, H handler) {
        if (matcher.path.isEmpty()) {
            root.handler = handler;
        } else {
            register(matcher, handler, root, 0);
        }
    }
    private void register(PathMatcher matcher, H handler, TrieNode node, int depth) {
        for (Short path : matcher.path.get(depth)) {
            TrieNode next = node.getOrCreateChild(path);
            if (depth == matcher.path.size() - 1) {
                next.handler = handler;
            } else {
                register(matcher, handler, next, depth + 1);
            }
        }
    }

    public H match(short[] paths) {
        return match(root, paths, 0);
    }
    private H match(TrieNode node, short[] paths, int depth) {
        if (node == null) {
            return null;
        }
        if (depth == paths.length) {
            return node.handler;
        }
        H handler = match(node.children.get(paths[depth]), paths, depth + 1);
        if (handler != null) {
            return handler;
        }
        return match(node.children.get(null), paths, depth + 1);
    }
}
