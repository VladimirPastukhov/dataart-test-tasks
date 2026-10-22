import kotlin.test.*

/**
 * A `Hierarchy` stores an arbitrary _forest_ (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * Parent-child relationships are identified by the position in the array and the associated depth.
 * Tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * Example:
 * ```
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * ```
 *
 * the forest can be visualized as follows:
 * ```
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 *```
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * Invariants on the depths array:
 *  * Depth of the first element is 0.
 *  * If the depth of a node is `D`, the depth of the next node in the array can be:
 *      * `D + 1` if the next node is a child of this node;
 *      * `D` if the next node is a sibling of this node;
 *      * `d < D` - in this case the next node is not related to this node.
 */
interface Hierarchy {
    /** The number of nodes in the hierarchy. */
    val size: Int

    /**
     * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be `depth(index)`.
     * @param index must be non-negative and less than [size]
     * */
    fun nodeId(index: Int): Int

    /**
     * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be `nodeId(index)`.
     * @param index must be non-negative and less than [size]
     * */
    fun depth(index: Int): Int

    fun formatString(): String {
        return (0 until size).joinToString(
            separator = ", ",
            prefix = "[",
            postfix = "]"
        ) { i -> "${nodeId(i)}:${depth(i)}" }
    }
}

/**
 * COMMENT FROM CANDIDATE:
 * I've added "inline" keyword to function signature
 * Theoretically it improves performance a little by removing lambda boxing and unboxing
 */
/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
inline fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
    val ids = mutableListOf<Int>() //the other option is to use arrays here to improve performance, but I've decided it is overkill
    val depths = mutableListOf<Int>()
    var removeDepth = Int.MAX_VALUE
    for(i in 0 until size){
        val depth = depth(i);
        if(depth > removeDepth)
            continue;
        removeDepth = Int.MAX_VALUE

        val nodeId = nodeId(i)
        if(nodeIdPredicate(nodeId)){
            ids.add(nodeId)
            depths.add(depth)
        } else {
            removeDepth = depth
        }
    }
    return ArrayBasedHierarchy(ids.toIntArray(), depths.toIntArray())
}

class ArrayBasedHierarchy(
    private val myNodeIds: IntArray,
    private val myDepths: IntArray,
) : Hierarchy {
    override val size: Int = myDepths.size

    override fun nodeId(index: Int): Int = myNodeIds[index]

    override fun depth(index: Int): Int = myDepths[index]
}


/*
* ArrayBasedHierarchy can be unsafe in case if we don't trust the code that creates it
* - arrays can be of different sizes
* - arrays can be changed after class creation
* - arrays content can breach DFS forest convention
* Candidate made this class to demonstrate a little safer approach
*/
class SaferArrayBasedHierarchy(
    myNodeIds: IntArray,
    myDepths: IntArray,
) : Hierarchy {

    private val nodeIds = myNodeIds.copyOf()
    private val depths = myDepths.copyOf()

    init{
        require(myNodeIds.size == myDepths.size) {
            "nodeIds and depths must have the same length: ${myNodeIds.size} != ${myDepths.size}"
        }
        require(depths.all{it >= 0}){
            "depths must be 0 or more"
        }
        //possibly add tree validation here, but I've decided that it is overkill for demonstration
    }

    override val size: Int = depths.size

    override fun nodeId(index: Int): Int = nodeIds[index]

    override fun depth(index: Int): Int = depths[index]
}

class FilterTest {
    @Test
    fun testFilter() {
        val unfiltered: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
            intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2))
        val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 3 != 0 }
        val filteredExpected: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(1, 2, 5, 8, 10, 11),
            intArrayOf(0, 1, 1, 0, 1, 2))
        assertEquals(filteredExpected.formatString(), filteredActual.formatString())
    }

    /**
     * Catches potentially wrong realisation of filter method that original test doesn't catch.
     * A node must be removed when ANY ancestor fails, not only its parent.
     * Original test checks only 1-depth parent-child deletion
     */
    @Test
    fun testRemovedNodeRemovesAllDescendantsNotOnlyChildren() {
        val unfiltered: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(1, 2, 3, 4, 5),
            intArrayOf(0, 1, 2, 3, 1))
        val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId != 2 }
        val filteredExpected: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(1, 5),
            intArrayOf(0, 1))
        assertEquals(filteredExpected.formatString(), filteredActual.formatString())
    }

    /**
     * Catches potentially wrong realisation of filter method that original and second test don't catch.
     * Bug to catch: decides whether it's still inside a removed subtree by comparing with the
     * PREVIOUS node's depth, instead of the depth of the removed node itself.
     */
    @Test
    fun testRemovedSubtreeReturnsToIntermediateDepth() {
        val unfiltered: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(10, 20, 30, 40, 50, 60, 70),
            intArrayOf(0, 1, 2, 3, 2, 3, 1))
        val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId != 20 }
        val filteredExpected: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(10, 70),
            intArrayOf(0, 1))
        assertEquals(filteredExpected.formatString(), filteredActual.formatString())
    }

    /*
    * Wrong implementation can potentially contain iteration errors: ignoring firs, last, or sibling filtering
    * I've decided to mix this checks in one test to reduce tests amount
     */
    @Test
    fun testFirstLastAndConsecutiveSiblingsRemoved() {
        val unfiltered: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(10, 11, 1, 20, 21, 30, 31, 2, 3, 40),
            intArrayOf( 0,  1, 0,  1,  2,  1,  2, 1, 0,  1))
        val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 10 != 0 }
        val filteredExpected: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(1, 2, 3),
            intArrayOf(0, 1, 0))
        assertEquals(filteredExpected.formatString(), filteredActual.formatString())
    }

    /* Test that algorithm works efficiently
    * The predicate must be called exactly once for each node whose ancestors are all kept:
    * 10, 1, 20, 30, 2, 3, 4, 5, 40, 6, 50 -> 11 calls out of 18 nodes.
    */
    @Test
    fun testPredicateCallsCount() {
        val unfiltered: Hierarchy = ArrayBasedHierarchy(
            intArrayOf(10, 11, 12, 1, 20, 21, 22, 23, 24, 30, 31, 2, 3, 4, 5, 40, 6, 50),
            intArrayOf( 0,  1,  2, 0,  1,  2,  3,  2,  3,  1,  2, 1, 2, 3, 0,  1, 1,  1))

        val calledIds = mutableListOf<Int>()
        unfiltered.filter { nodeId ->
            calledIds += nodeId
            nodeId % 10 != 0
        }
        assertEquals(listOf(10, 1, 20, 30, 2, 3, 4, 5, 40, 6, 50), calledIds)
    }

}