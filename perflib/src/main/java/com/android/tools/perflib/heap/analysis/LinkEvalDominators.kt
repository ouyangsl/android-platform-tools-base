/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.perflib.heap.analysis

import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.IdentityHashMap
import java.util.Stack
import java.util.stream.Stream
import kotlin.streams.toList

/**
 * Computes dominators based on the union-find data structure with path compression and linking by
 * size. Using description found in: http://adambuchsbaum.com/papers/dom-toplas.pdf which is based
 * on a copy of the paper available at:
 * http://www.cc.gatech.edu/~harrold/6340/cs6340_fall2009/Readings/lengauer91jul.pdf
 */
object LinkEvalDominators {

    /**
     * @return 2 parallel lists of each node and its immediate dominator,
     *         with `null` being the auxiliary root.
     */
    fun <T : Any> computeDominators(roots: Set<T>, next: (T) -> Stream<T>): Result<T> {
        // Step 1 of paper.
        // Number the instances by their DFS-traversal order and record each one's parent in the DFS
        // tree.
        // Also gather predecessors and initialize semi-dominators in the same pass.
        val (instances, parents, preds) = computeIndicesAndParents(roots, next)
        val semis = IntArray(instances.size) { it }
        val buckets = Array(instances.size) { IntArrayList() }
        val doms = IntArray(instances.size)
        val ancestors = IntArray(instances.size) { INVALID_ANCESTOR }
        val labels = IntArray(instances.size) { it }
        val immDom = MutableList<T?>(instances.size) { null }
        for (currentNode in instances.size - 1 downTo 1) {
            // Step 2 of paper.
            // Compute each instance's semi-dominator
            for (predecessor in preds[currentNode] ?: EMPTY_INT_ARRAY) {
                val evaledPredecessor = eval(ancestors, labels, semis, predecessor)
                if (semis[evaledPredecessor] < semis[currentNode]) {
                    semis[currentNode] = semis[evaledPredecessor]
                }
            }
            buckets[semis[currentNode]].add(currentNode)
            ancestors[currentNode] = parents[currentNode]

            // Step 3 of paper.
            // Implicitly define each node's immediate dominator by Corollary 1
            for (i in 0 until buckets[parents[currentNode]].size) {
                val node = buckets[parents[currentNode]].getInt(i)
                val nodeEvaled = eval(ancestors, labels, semis, node)
                doms[node] =
                    if (semis[nodeEvaled] < semis[node]) nodeEvaled else parents[currentNode]
                immDom[node] = instances[doms[node]]
            }
            buckets[parents[currentNode]].clear() // Bulk remove (slightly different from paper).
        }

        // Step 4 of paper.
        // Explicitly define each node's immediate dominator
        for (currentNode in 1 until instances.size) {
            if (doms[currentNode] != semis[currentNode]) {
                doms[currentNode] = doms[doms[currentNode]]
                immDom[currentNode] = instances[doms[currentNode]]
            }
        }

        return Result(instances, immDom)
    }

    /** Traverse the instances depth-first, marking their order and parents in the DFS-tree  */
    private fun <T : Any> computeIndicesAndParents(roots: Set<T>, next: (T) -> Stream<T>): DFSResult<T> {
        val instances = ArrayList<Node<T>?>()
        val nodeStack = Stack<Node<T>>()
        instances.add(null) // auxiliary root at 0
        val newNode = Node.newFactory(next)
        roots.forEach {
            val root = newNode(it).apply { parent = 0; predecessors.add(0) }
            nodeStack.push(root)
        }
        while (!nodeStack.empty()) {
            val node = nodeStack.pop()
            if (node.topoOrder < 0) {
                node.topoOrder = instances.size
                instances.add(node)

                for (succ in node.successors) {
                    succ.predecessors.add(node.topoOrder)
                    if (succ.topoOrder < 0) {
                        succ.parent = node.topoOrder
                        nodeStack.push(succ)
                    }
                }
            }
        }
        val parentIndices = IntArray(instances.size)
        val predIndices = arrayOfNulls<IntArray>(instances.size)
        for (i in 1 until instances.size) { // omit auxiliary root at [0]
            val instance = instances[i]!!
            parentIndices[i] = instance.parent
            predIndices[i] = instance.predecessors.toIntArray()
        }
        return DFSResult(instances.map { it?.content }, parentIndices, predIndices)
    }
    data class Result<T>(val topoOrder: List<T?>, val immediateDominator: List<T?>)
}

private data class DFSResult<T>(
    val instances: List<T?>,
    val parents: IntArray, // Predecessors not involved in DFS, but lumped in here for 1 pass. Paper did same.
    val predecessors: Array<IntArray?>
)

private fun eval(ancestors: IntArray, labels: IntArray, semis: IntArray, node: Int) =
    when (ancestors[node]) {
        INVALID_ANCESTOR -> node
        else -> compress(ancestors, labels, semis, node)
    }

/**
 *  @return a node's evaluation after compression
 */
private fun compress(ancestors: IntArray, labels: IntArray, semis: IntArray, node: Int): Int {
    val compressArray = IntArrayList()
    assert(ancestors[node] != INVALID_ANCESTOR)
    var n = node
    while (ancestors[ancestors[n]] != INVALID_ANCESTOR) {
        compressArray.add(n)
        n = ancestors[n]
    }
    for (i in compressArray.size - 1 downTo 0) {
        val toCompress = compressArray.getInt(i)
        val ancestor = ancestors[toCompress]
        assert(ancestor != INVALID_ANCESTOR)
        if (semis[labels[ancestor]] < semis[labels[toCompress]]) {
            labels[toCompress] = labels[ancestor]
        }
        ancestors[toCompress] = ancestors[ancestor]
    }
    return labels[node]
}

// 0 would coincide with valid parent. Paper uses 0 because they count from 1.
private const val INVALID_ANCESTOR = -1
private val EMPTY_INT_ARRAY = IntArray(0)

// Augment the original graph with additional information (e.g. topological order, predecessors'
// orders, etc.)
private class Node<T> private constructor(val content: T, next: (T) -> Stream<T>, wrap: (T) -> Node<T>) {
    val successors: List<Node<T>> by lazy { next(content).map(wrap).toList() }
    var topoOrder = -1 // topological order from our particular traversal, also used as id
    var parent = -1
    var predecessors = IntArrayList()

    companion object {
        fun<T> newFactory(next: (T) -> Stream<T>): (T) -> Node<T> =
            IdentityHashMap<T, Node<T>>().let { cache ->
                fun wrap(content: T): Node<T> = cache.getOrPut(content) { Node(content, next, ::wrap) }
                ::wrap
            }
    }
}
