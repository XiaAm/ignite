// @scala.file.header

/*
 * ___    _________________________ ________
 * __ |  / /____  _/__  ___/__  __ \___  __ \
 * __ | / /  __  /  _____ \ _  / / /__  /_/ /
 * __ |/ /  __/ /   ____/ / / /_/ / _  _, _/
 * _____/   /___/   /____/  \____/  /_/ |_|
 *
 */

package org.gridgain.visor.commands.gc

import org.gridgain.grid._
import org.gridgain.grid.kernal.GridEx
import resources.GridInstanceResource
import org.gridgain.scalar._
import scalar._
import org.gridgain.visor._
import org.gridgain.visor.commands.VisorConsoleCommand
import visor._
import collection.JavaConversions._
import scala.util.control.Breaks._
import scala.util.control.Exception._
import org.gridgain.grid.util.lang.GridAbsClosure

/**
 * ==Overview==
 * Contains Visor command `gc` implementation.
 *
 * ==Importing==
 * When using this command from Scala code (not from REPL) you need to make sure to
 * properly import all necessary typed and implicit conversions:
 * <ex>
 * import org.gridgain.visor._
 * import commands.gc.VisorGcCommand._
 * </ex>
 * Note that `VisorGcCommand` object contains necessary implicit conversions so that
 * this command would be available via `visor` keyword.
 *
 * ==Help==
 * {{{
 * +---------------------------------------------------------------------------+
 * | gc | Runs garbage collector on remote nodes.                              |
 * |    | If specific node is provided, garbage collector is run on that node. |
 * |    | Otherwise, it will be run on all nodes in topology.                  |
 * +---------------------------------------------------------------------------+
 * }}}
 *
 * ====Specification====
 * {{{
 *     gc
 *     gc "{-id8=<node-id8>|-id=<node-id>} {-c}"
 * }}}
 *
 * ====Arguments====
 * {{{
 *     -id8=<node-id8>
 *         ID8 of the node.
 *         Note that either '-id8' or '-id' can be specified.
 *     -id=<node-id>
 *         ID of the node.
 *         Note that either '-id8' or '-id' can be specified.
 *     -c
 *         Run DGC procedure on all caches.
 * }}}
 *
 * ====Examples====
 * {{{
 *     gc "-id8=12345678"
 *         Runs garbage collector on specified node.
 *     gc
 *         Runs garbage collector on all nodes in topology.
 *     gc "-id8=12345678 -c"
 *         Runs garbage collector and DGC procedure on all caches.
 * }}}
 *
 * @author @java.author
 * @version @java.version
 */
class VisorGcCommand {
    /**
     * Prints error message and advise.
     *
     * @param errMsgs Error messages.
     */
    private def scold(errMsgs: Any*) {
        assert(errMsgs != null)

        nl()

        warn(errMsgs: _*)
        warn("Type 'help gc' to see how to use this command.")
    }

    /**
     * ===Command===
     * Runs `System.gc()` on specified node or on all nodes in topology.
     *
     * ===Examples===
     * <ex>gc "-id8=12345678"</ex>
     * Runs `System.gc()` on specified node.
     *
     * <ex>gc "-id8=12345678 -c"</ex>
     * Runs garbage collector and DGC procedure on all caches.
     */
    def gc(args: String) = breakable {
        assert(args != null)

        if (!isConnected)
            adviseToConnect()
        else {
            val argLst = parseArgs(args)

            val id8 = argValue("id8", argLst)
            val id = argValue("id", argLst)
            val dgc = hasArgFlag("c", argLst)

            var node: GridNode = null

            if (id8.isDefined && id.isDefined)
                scold("Only one of '-id8' or '-id' is allowed.") ^^
            else if (id8.isDefined) {
                val ns = nodeById8(id8.get)

                if (ns.isEmpty)
                    scold("Unknown 'id8' value: " + id8.get) ^^
                else if (ns.size != 1) {
                    scold("'id8' resolves to more than one node (use full 'id' instead): " + id8.get) ^^
                }
                else
                    node = ns.head
            }
            else if (id.isDefined)
                try {
                    node = grid.node(java.util.UUID.fromString(id.get))

                    if (node == null)
                        scold("'id' does not match any node: " + id.get) ^^
                }
                catch {
                    case e: IllegalArgumentException => scold("Invalid node 'id': " + id.get) ^^
                }

            val f = new GridAbsClosure {
                @GridInstanceResource
                val g: Grid = null

                override def apply() {
                    System.gc()

                    if (dgc)
                        g.asInstanceOf[GridEx].cachesx().foreach(_.dgc())
                }
            }

            val hnd: Catcher[Unit] = {
                case e: GridEmptyProjectionException => scold("Topology is empty.")
                case e: GridException => scold(e.getMessage)
            }

            catching(hnd) {
                if (node != null)
                    grid.forNode(node)
                        .compute()
                        .withNoFailover()
                        .run(f)
                else
                    grid.compute()
                        .withNoFailover()
                        .run(f)
            }
        }
    }

    /**
     * ===Command===
     * Runs `System.gc()` on all nodes in topology.
     *
     * ===Examples===
     * <ex>gc</ex>
     * Runs `System.gc()` on all nodes in topology.
     */
    def gc() {
        gc("")
    }
}

/**
 * Companion object that does initialization of the command.
 *
 * @author @java.author
 * @version @java.version
 */
object VisorGcCommand {
    addHelp(
        name = "gc",
        shortInfo = "Runs GC on remote nodes.",
        longInfo = List(
            "Runs garbage collector on remote nodes.",
            "If specific node is provided, garbage collector is run on that node.",
            "Otherwise, it will be run on all nodes in topology."
        ),
        spec = List(
            "gc",
            "gc {-id8=<node-id8>|-id=<node-id>} {-c}"
        ),
        args = List(
            "-id8=<node-id8>" -> List(
                "ID8 of the node.",
                "Note that either '-id8' or '-id' can be specified and " +
                    "you can also use '@n0' ... '@nn' variables as shortcut to <node-id8>."
            ),
            "-id=<node-id>" -> List(
                "ID of the node.",
                "Note that either '-id8' or '-id' can be specified."
            ),
            "-c" -> List(
                "Run DGC procedure on all caches."
            )
        ),
        examples = List(
            "gc -id8=12345678" ->
                "Runs garbage collector on specified node.",
            "gc" ->
                "Runs garbage collector on all nodes in topology.",
            "gc -id8=@n0 -c" ->
                ("Runs garbage collector on specified node with id8 taken from 'n0' memory variable " +
                "and run DGC procedure on all caches.")
        ),
        ref = VisorConsoleCommand(cmd.gc, cmd.gc)
    )

    /** Singleton command. */
    private val cmd = new VisorGcCommand

    /**
     * Singleton.
     */
    def apply() = cmd

    /**
     * Implicit converter from visor to commands "pimp".
     *
     * @param vs Visor tagging trait.
     */
    implicit def fromGc2Visor(vs: VisorTag) = cmd
}
