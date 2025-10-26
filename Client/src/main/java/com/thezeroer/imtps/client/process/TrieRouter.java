package com.thezeroer.imtps.client.process;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TrieRouter: 三层 Trie 路由器
 */
public final class TrieRouter<H> {
    // 阈值：当 SET 的元素数量 <= EXPAND_SET_THRESHOLD 时，注册时直接展开到 exact 分支
    private final int EXPAND_SET_THRESHOLD;
    private final AtomicLong orderGen = new AtomicLong(0);

    /**
     * IntMatcher: 单字段匹配器（ANY / EXACT / SET）
     */
    public interface IntMatcher {
        boolean matches(int v);
        Kind kind();
        enum Kind { ANY, SET, EXACT }
        static IntMatcher any() { return AnyMatcher.INSTANCE; }
        static IntMatcher eq(int v) { return new ExactMatcher(v); }
        static IntMatcher oneOf(int... vs) { return new SetMatcher(vs); }
        static IntMatcher oneOf(Collection<? extends Integer> vs) {
            int[] arr = new int[vs.size()];
            int i=0; for (Integer x: vs) arr[i++] = x;
            return new SetMatcher(arr);
        }

        final class AnyMatcher implements IntMatcher {
            static final AnyMatcher INSTANCE = new AnyMatcher();
            private AnyMatcher() {}
            public boolean matches(int v) { return true; }
            public Kind kind() { return Kind.ANY; }
            public String toString(){ return "*"; }
        }

        final class SetMatcher implements IntMatcher {
            final int[] vals; // sorted unique
            SetMatcher(int[] vs){
                int[] copy = Arrays.copyOf(vs, vs.length);
                Arrays.sort(copy);
                int n=0;
                for (int i=0;i<copy.length;i++){
                    if (i==0 || copy[i]!=copy[i-1]) copy[n++]=copy[i];
                }
                this.vals = Arrays.copyOf(copy, n);
            }
            public boolean matches(int x){ return Arrays.binarySearch(vals, x) >= 0; }
            public Kind kind(){ return Kind.SET; }
            public int size(){ return vals.length; }
            public String toString(){ return "oneOf"+Arrays.toString(vals); }
        }

        final class ExactMatcher implements IntMatcher {
            private final int v;
            ExactMatcher(int v){ this.v = v; }
            public boolean matches(int x){ return x == v; }
            public Kind kind(){ return Kind.EXACT; }
            public int value(){ return v; }
            public String toString(){ return Integer.toString(v); }
        }
    }
    /**
     * Rule: 一条三字段匹配规则
     */
    static final class Rule<H> {
        final IntMatcher way, type, extra;
        final H handler;
        final int exactCount;
        final int setCount;
        final long order;

        Rule(IntMatcher way, IntMatcher type, IntMatcher extra, H handler, long order){
            this.way = way; this.type = type; this.extra = extra;
            this.handler = handler; this.order = order;
            this.exactCount = (way.kind()== IntMatcher.Kind.EXACT?1:0)
                    + (type.kind()== IntMatcher.Kind.EXACT?1:0)
                    + (extra.kind()== IntMatcher.Kind.EXACT?1:0);
            this.setCount = (way.kind()== IntMatcher.Kind.SET?1:0)
                    + (type.kind()== IntMatcher.Kind.SET?1:0)
                    + (extra.kind()== IntMatcher.Kind.SET?1:0);
        }

        boolean matchesAll(int w, int t, int e){
            return way.matches(w) && type.matches(t) && extra.matches(e);
        }

        // 检查从指定 index (0=way,1=type,2=extra) 开始的剩余字段能否匹配
        boolean matchesRemainingFrom(int[] values, int startIndex){
            if (startIndex <= 0 && !way.matches(values[0])) return false;
            if (startIndex <= 1 && !type.matches(values[1])) return false;
            if (startIndex <= 2 && !extra.matches(values[2])) return false;
            return true;
        }

        // 返回该层的 matcher（0=way,1=type,2=extra）
        IntMatcher matcherAt(int idx){
            return switch (idx) {
                case 0 -> way;
                case 1 -> type;
                case 2 -> extra;
                default -> throw new IllegalArgumentException();
            };
        }

        // 优先级比较： more exact first, then more set, then earlier registered (smaller order)
        int comparePriorityTo(Rule<H> o){
            if (this.exactCount != o.exactCount) return Integer.compare(o.exactCount, this.exactCount);
            if (this.setCount != o.setCount) return Integer.compare(o.setCount, this.setCount);
            return Long.compare(this.order, o.order);
        }

        public String toString(){
            return String.format("Rule[e=%d,s=%d,ord=%d, %s,%s,%s]",
                    exactCount, setCount, order, way, type, extra);
        }
    }
    // Node 定义
    static final class Node<H> {
        final Map<Integer, Node<H>> exactChildren = new HashMap<>();
        Node<H> anyChild = null;              // 对应 ANY 分支
        final List<Rule<H>> setRules = new ArrayList<>(); // 存放未展开的大集合规则（在此层判断）
        final List<Rule<H>> handlers = new ArrayList<>(); // 到达此节点可终结的规则
        boolean frozen = false;
    }
    // 帮助类：持有当前最优规则
    static final class BestHolder<H> { Rule<H> bestRule = null; }

    private final Node<H> root = new Node<>();

    public TrieRouter(){ this(8); } // 默认阈值 8
    public TrieRouter(int expandSetThreshold){
        this.EXPAND_SET_THRESHOLD = expandSetThreshold;
    }

    public static IntMatcher buildMatcher(Object field) {
        return switch (field) {
            case null -> IntMatcher.any();
            case Integer i -> IntMatcher.eq(i);
            case int[] intArray -> IntMatcher.oneOf(intArray);
            case Collection<?> c -> IntMatcher.oneOf((Collection<? extends Integer>) c);
            default -> throw new IllegalArgumentException("Unsupported matcher type: " + field);
        };
    }

    // 注册一条规则
    public void register(IntMatcher way, IntMatcher type, IntMatcher extra, H handler){
        Rule<H> rule = new Rule<>(way, type, extra, handler, orderGen.getAndIncrement());
        insertRecursive(root, 0, rule);
    }

    // 递归插入
    private void insertRecursive(Node<H> node, int idx, Rule<H> rule){
        if (idx == 3) {
            node.handlers.add(rule);
            return;
        }
        IntMatcher m = rule.matcherAt(idx);
        if (m.kind() == IntMatcher.Kind.EXACT){
            int v = ((IntMatcher.ExactMatcher)m).value();
            Node<H> child = node.exactChildren.computeIfAbsent(v, k -> new Node<>());
            insertRecursive(child, idx+1, rule);
        } else if (m.kind() == IntMatcher.Kind.ANY){
            if (node.anyChild == null) node.anyChild = new Node<>();
            insertRecursive(node.anyChild, idx+1, rule);
        } else { // SET
            IntMatcher.SetMatcher sm = (IntMatcher.SetMatcher) m;
            int size = sm.size();
            if (size <= EXPAND_SET_THRESHOLD){
                // 展开：把规则插到每个具体值的 exact 分支（注册期扩展，查找期更快）
                for (int v : sm.vals){
                    Node<H> child = node.exactChildren.computeIfAbsent(v, k -> new Node<>());
                    insertRecursive(child, idx+1, rule);
                }
            } else {
                // 不展开，保存到当前节点的 setRules；查找时在此层判断
                node.setRules.add(rule);
            }
        }
    }

    // freeze: 排序并标记为只读（提高查找性能 / 多线程安全）
    public void freeze(){
        freezeRecursive(root);
    }
    private void freezeRecursive(Node<H> node){
        if (node.frozen) return;
        Comparator<Rule<H>> cmp = Rule::comparePriorityTo;
        node.handlers.sort(cmp);
        node.setRules.sort(cmp);
        node.frozen = true;
        for (Node<H> c : node.exactChildren.values()) freezeRecursive(c);
        if (node.anyChild != null) freezeRecursive(node.anyChild);
    }

    // 查找最优单个 handler（null 表示未命中）
    public H find(int way, int type, int extra){
        int[] vals = new int[]{way, type, extra};
        BestHolder<H> best = new BestHolder<>();
        searchRecursive(root, 0, vals, best);
        return best.bestRule == null ? null : best.bestRule.handler;
    }

    // 查找所有命中（按优先级排序）
    public List<H> findAll(int way, int type, int extra){
        int[] vals = new int[]{way, type, extra};
        List<Rule<H>> matched = new ArrayList<>();
        collectAll(root, 0, vals, matched);
        matched.sort(Rule::comparePriorityTo);
        List<H> out = new ArrayList<>(matched.size());
        for (Rule<H> r : matched) out.add(r.handler);
        return out;
    }

    // 递归搜索并更新 best（尝试剪枝：若出现 exactCount == 3 的规则，则可提前终止）
    private boolean searchRecursive(Node<H> node, int idx, int[] vals, BestHolder<H> best){
        // idx == 3: 处理 handlers 列表
        if (idx == 3){
            for (Rule<H> r : node.handlers){
                if (r.matchesAll(vals[0], vals[1], vals[2])){
                    updateBest(best, r);
                    if (best.bestRule != null && best.bestRule.exactCount == 3) return true; // 完美匹配，无法更好，早停
                }
            }
            return false;
        }
        int curVal = vals[idx];
        // 1) 先走 exact 分支（更具体）
        Node<H> exactChild = node.exactChildren.get(curVal);
        if (exactChild != null){
            boolean stop = searchRecursive(exactChild, idx+1, vals, best);
            if (stop) return true;
        }
        // 2) 然后检查当前节点的 setRules（未展开的大集合规则）
        if (!node.setRules.isEmpty()){
            for (Rule<H> r : node.setRules){
                // 先判断当前层是否命中集合（避免进入下一层）
                if (!r.matcherAt(idx).matches(curVal)) continue;
                // 若当前层命中，再检查剩余层（直接一次性检查）
                if (r.matchesRemainingFrom(vals, idx+1)){
                    updateBest(best, r);
                    if (best.bestRule != null && best.bestRule.exactCount == 3) return true;
                }
            }
        }
        // 3) 再走 any 分支（最模糊）
        if (node.anyChild != null){
            boolean stop = searchRecursive(node.anyChild, idx+1, vals, best);
            if (stop) return true;
        }
        return false;
    }

    private void updateBest(BestHolder<H> best, Rule<H> r){
        if (best.bestRule == null) best.bestRule = r;
        else {
            int cmp = r.comparePriorityTo(best.bestRule);
            if (cmp < 0) best.bestRule = r; // comparePriorityTo 用的是 this-vs-other; adapt accordingly
        }
    }

    // 收集所有匹配
    private void collectAll(Node<H> node, int idx, int[] vals, List<Rule<H>> out){
        if (idx==3){
            for (Rule<H> r : node.handlers) if (r.matchesAll(vals[0], vals[1], vals[2])) out.add(r);
            return;
        }
        int curVal = vals[idx];
        Node<H> exactChild = node.exactChildren.get(curVal);
        if (exactChild != null) collectAll(exactChild, idx+1, vals, out);
        for (Rule<H> r : node.setRules){
            if (r.matcherAt(idx).matches(curVal) && r.matchesRemainingFrom(vals, idx+1)) out.add(r);
        }
        if (node.anyChild != null) collectAll(node.anyChild, idx+1, vals, out);
    }
}
