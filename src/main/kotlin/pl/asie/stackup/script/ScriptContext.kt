package pl.asie.stackup.script

import com.google.common.base.Charsets
import gnu.trove.map.TObjectIntMap
import gnu.trove.map.hash.TObjectIntHashMap
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraftforge.registries.IForgeRegistry
import org.apache.commons.lang3.tuple.Pair
import pl.asie.stackup.StackUp
import pl.asie.stackup.StackUpHelpers
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.StringReader

class ScriptContext(
    private val registry: IForgeRegistry<Item>,
    stream: InputStream,
    provider: TokenProvider
) {
    private val streamReader: InputStreamReader = InputStreamReader(stream, Charsets.UTF_8)
    private val reader: BufferedReader = BufferedReader(streamReader)
    private val stackSizeMap: TObjectIntMap<Item> = TObjectIntHashMap()
    private val tokenProvider: TokenProvider = provider

    init {
        provider.addToken("size", java.util.function.Supplier { TokenNumeric<Item> { item -> getStackSize(item, false) } })
    }

    fun execute() {
        var parsed = 0
        try {
            val it = reader.lines().iterator()
            while (it.hasNext()) {
                parseLine(it.next())
                parsed++
            }
            reader.close()
            streamReader.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: TokenException) {
            e.printStackTrace()
        }

        requireNotNull(StackUp.logger).info("Parsed $parsed lines.")
        applyChanges()
    }

    @Throws(IOException::class, TokenException::class)
    protected fun parseLine(lineIn: String) {
        val line = lineIn.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            return
        }

        val reader = PushbackReader(StringReader(line), 2)
        val args = ArrayList<Token<Item>>()
        var newStackSize = 0
        var operator = '='.code

        var i = 256
        while ((i--) > 0) {
            if (i < 256) {
                ScriptHandler.cutWhitespace(reader)
                val c = reader.read()
                if (c != ','.code) {
                    reader.unread(c)
                } else {
                    ScriptHandler.cutWhitespace(reader)
                }
            }

            val token: Pair<String, Token<*>?> = tokenProvider.getToken(reader)
            if (token.right == null) {
                val c1 = reader.read()
                if (c1 == '-'.code) {
                    val c2 = reader.read()
                    if (c2 == '>'.code) {
                        newStackSize = TokenNumeric.parseInteger(reader)
                        break
                    } else if (c2 == '='.code) {
                        newStackSize = TokenNumeric.parseInteger(reader)
                        operator = c1
                        break
                    } else {
                        reader.unread(c2)
                    }
                } else if (c1 == '+'.code || c1 == '*'.code || c1 == '/'.code) {
                    val c2 = reader.read()
                    if (c2 == '='.code) {
                        newStackSize = TokenNumeric.parseInteger(reader)
                        operator = c1
                        break
                    } else {
                        reader.unread(c2)
                    }
                } else {
                    reader.unread(c1)
                }

                throw TokenException("Token not found: ${token.left}!")
            } else {
                @Suppress("UNCHECKED_CAST")
                val parsedToken = token.right as Token<Item>
                parsedToken.parse(reader)
                args.add(parsedToken)
            }
        }

        if (newStackSize > 0) {
            for (item in registry) {
                var ok = true
                for (t in args) {
                    if (t.isInvert()) {
                        if (t.apply(item)) {
                            ok = false
                            break
                        }
                    } else if (!t.apply(item)) {
                        ok = false
                        break
                    }
                }

                if (ok) {
                    when (operator) {
                        '='.code -> stackSizeMap.put(item, clamp(newStackSize))
                        '+'.code -> stackSizeMap.put(item, clamp(getStackSize(item, true) + newStackSize))
                        '-'.code -> stackSizeMap.put(item, clamp(getStackSize(item, true) - newStackSize))
                        '*'.code -> stackSizeMap.put(item, clamp(getStackSize(item, true) * newStackSize))
                        '/'.code -> stackSizeMap.put(item, clamp(getStackSize(item, true) / newStackSize))
                    }
                }
            }
        }
    }

    protected fun clamp(v: Int): Int {
        return when {
            v < 1 -> 1
            v > StackUpHelpers.getMaxStackSize() -> StackUpHelpers.getMaxStackSize()
            else -> v
        }
    }

    protected fun getStackSize(i: Item, withMap: Boolean): Int {
        return if (withMap && stackSizeMap.containsKey(i)) {
            stackSizeMap.get(i)
        } else {
            i.itemStackLimit
        }
    }

    protected fun applyChanges() {
        for (i in stackSizeMap.keySet()) {
            if (i == Items.AIR) {
                continue
            }

            val target = stackSizeMap.get(i)
            StackUp.backupStackSize(i)
            // 这里修改的是 Item 级别的堆叠上限，同一个 Item 的所有 metadata 变体会共享这个值。
            i.setMaxStackSize(target)
            val result = i.itemStackLimit
            if (target != result) {
                requireNotNull(StackUp.logger).warn("Could not change stack size on item " + i.registryName + "!")
            }
        }

        stackSizeMap.clear()
    }
}
