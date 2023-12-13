package com.alibaba.lindorm.contest.compress;
import com.alibaba.lindorm.contest.util.Pair;

import java.util.*;

public class DependencyFinder {
    private Map<Integer, Set<Integer>> graph = new HashMap<>();

    public DependencyFinder(List<Pair<Integer, Integer>> subSet) {
        // 构建图的邻接表表示
        for (Pair<Integer, Integer> pair : subSet) {
            graph.computeIfAbsent(pair.getLeft(), k -> new HashSet<>()).add(pair.getRight());
        }
    }

    public Set<Integer> findDependencies(int start) {
        // 使用递归的深度优先搜索来查找所有依赖
        Set<Integer> dependencies = new HashSet<>();
        dfs(start, dependencies);
        dependencies.add(start);
        return dependencies;
    }

    private void dfs(int node, Set<Integer> dependencies) {
        // 遍历所有依赖项
        if (graph.containsKey(node)) {
            for (int dep : graph.get(node)) {
                if (dependencies.add(dep)) {
                    dfs(dep, dependencies);
                }
            }
        }
    }

    public static void main(String[] args) {
        List<Pair<Integer, Integer>> subSet = new ArrayList<>();
        subSet.add(new Pair<>(3, 4));
        subSet.add(new Pair<>(4, 6));
        subSet.add(new Pair<>(6, 19));
        subSet.add(new Pair<>(15, 19));
        subSet.add(new Pair<>(19, 20));
        subSet.add(new Pair<>(12, 39));
        subSet.add(new Pair<>(34, 39));
        subSet.add(new Pair<>(11, 39));

        DependencyFinder finder = new DependencyFinder(subSet);
        Set<Integer> dependencies = finder.findDependencies(3);

        // 将依赖项转换为排序列表并打印
        List<Integer> sortedDependencies = new ArrayList<>(dependencies);
        Collections.sort(sortedDependencies);
        System.out.println(sortedDependencies);
    }
}
