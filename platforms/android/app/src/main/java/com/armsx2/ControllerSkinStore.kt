package com.armsx2

import com.armsx2.runtime.MainActivityRuntime

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Custom on-screen controller skins (v1: visuals only). A skin is a folder (or
 * .zip) of `ic_controller_<button>.png` images using the SAME filename scheme as
 * the iOS build, so iOS skin packs drop in. Imported skins live app-private under
 * `filesDir/controllerskins/<id>/`; one is selected at a time (or none = the
 * built-in look). [TouchControlsOverlay] asks [bitmapForKey] per button and falls
 * back to the bundled drawables when a skin is unset or missing an image — so the
 * default experience is unchanged.
 */
object ControllerSkinStore {

    private const val KEY_ACTIVE = "skin.active"
    private const val KEY_BUTTON_IMAGE_PREFIX = "skin.button.image."

    /** Changes whenever a custom button image is selected or reset. */
    val imageSelectionRevision = mutableStateOf(0)

    /** Per-game skin override: `skin.active.game.<serial>`. Mirrors how the touch
     *  LAYOUT already goes per-serial (see TouchControls.applyForSerial) — the global
     *  key stays the baseline and a game with an override shadows it for that serial
     *  only.
     *
     *  A per-game entry is present-or-absent, and "present" includes the explicit
     *  "no skin" choice — which is why the value can be [NONE] rather than the key
     *  just being removed. Without that, a game could not opt OUT of a global skin
     *  back to the built-in look. */
    private const val KEY_ACTIVE_GAME_PREFIX = "skin.active.game."
    private const val NONE = "__none__"

    /** RESOLVED skin id for what's on screen now, or null = built-in. Backed state so
     *  the overlay recomposes when it changes.
     *
     *  Resolved, not raw: it holds the per-game override while a game with one is
     *  running, and the global otherwise. [applyForSerial] is what re-resolves it, on
     *  the same hook the per-game touch layout uses. Read on the draw path via
     *  [bitmapForKey], so it must never itself touch prefs. */
    val activeSkinId = mutableStateOf<String?>(null)
    @Volatile private var loaded = false

    /** Serial whose override [activeSkinId] currently reflects, or null for global.
     *  Kept so the Skins tab can tell "this game uses the global skin" from "this game
     *  is pinned to the same skin the global happens to be". */
    val activeSerial = mutableStateOf<String?>(null)

    private fun idOrNull(raw: String?): String? = if (raw == null || raw == NONE) null else raw

    /** Re-resolve [activeSkinId] for [serial] (null = library/global). Called from the
     *  touch overlay on the same LaunchedEffect that applies the per-game layout, so a
     *  game's skin is up from the first frame rather than after a visit to Settings. */
    fun applyForSerial(ctx: Context, serial: String?) {
        ensureLoaded(ctx)
        val eff = serial?.takeIf { it.isNotEmpty() }
        val raw = if (eff != null)
            MainActivityRuntime.prefs.getString(KEY_ACTIVE_GAME_PREFIX + eff, null)
                ?: MainActivityRuntime.prefs.getString(KEY_ACTIVE, null)
        else MainActivityRuntime.prefs.getString(KEY_ACTIVE, null)
        val serialChanged = activeSerial.value != eff
        activeSerial.value = eff
        if (serialChanged) {
            clearCache()
            imageSelectionRevision.value++
        }
        val resolved = idOrNull(raw)?.takeIf { builtinFor(it) != null || File(root(ctx), it).isDirectory }
        if (activeSkinId.value != resolved) {
            activeSkinId.value = resolved
            clearCache()
        }
    }

    /** True if [serial] pins its own skin (including an explicit "none"). */
    fun hasGameOverride(serial: String?): Boolean {
        val eff = serial?.takeIf { it.isNotEmpty() } ?: return false
        return MainActivityRuntime.prefs.contains(KEY_ACTIVE_GAME_PREFIX + eff)
    }

    /** The skin [serial]'s tier selects, falling back to the global baseline so a fresh
     *  per-game row shows what it inherits rather than blank. serial=null reads global. */
    fun activeForScope(ctx: Context, serial: String?): String? {
        ensureLoaded(ctx)
        val eff = serial?.takeIf { it.isNotEmpty() }
        val raw = if (eff != null && hasGameOverride(eff))
            MainActivityRuntime.prefs.getString(KEY_ACTIVE_GAME_PREFIX + eff, null)
        else MainActivityRuntime.prefs.getString(KEY_ACTIVE, null)
        return idOrNull(raw)
    }

    /** Drop [serial]'s override so it follows the global skin again. */
    fun clearGameOverride(ctx: Context, serial: String) {
        if (serial.isEmpty()) return
        MainActivityRuntime.prefs.edit().remove(KEY_ACTIVE_GAME_PREFIX + serial).apply()
        applyForSerial(ctx, serial)
    }

    /** Logical button key -> filename inside a skin folder (iOS scheme). The face /
     *  shoulder / system / d-pad buttons use `ic_controller_<key>_button.png`; the
     *  analog ring + thumb drop the `_button` suffix. */
    private val FILE: Map<String, String> = buildMap {
        for (k in listOf(
            "cross", "circle", "square", "triangle",
            "l1", "l2", "l3", "r1", "r2", "r3",
            "start", "select", "up", "down", "left", "right",
        )) put(k, "ic_controller_${k}_button.png")
        put("analog_base", "ic_controller_analog_base.png")
        put("analog_stick", "ic_controller_analog_stick.png")
        // Per-side stick art. Packs that draw the two sticks differently (an "L"/"R"
        // marking is the common case) ship these, and they must stay SEPARATE: folding
        // both onto the single analog_stick slot made the two imports overwrite each
        // other, so whichever landed last was drawn under BOTH sticks — the reported
        // "left stick looks the same as the right one". A pack shipping only the old
        // single image still works: the overlay falls back to analog_base/analog_stick.
        put("analog_base_left", "ic_controller_analog_base_left.png")
        put("analog_base_right", "ic_controller_analog_base_right.png")
        put("analog_stick_left", "ic_controller_analog_stick_left.png")
        put("analog_stick_right", "ic_controller_analog_stick_right.png")
        // The four macro buttons. A macro can already fire any pad input -- including L-Stick
        // Left/Right -- so a racing layout of steer/steer/brake/accelerate was buildable, but the
        // buttons were stuck with the generic M1-M4 artwork because there was no skin slot for
        // them. Reported by David, who reasonably tried "ic_controller_m1_button.png" and got
        // silence. Both spellings are accepted below.
        for (i in 1..4) put("macro$i", "ic_controller_macro${i}_button.png")
    }

    /** Spellings people actually ship, mapped to the canonical key. */
    private val FILE_ALIASES: Map<String, String> = buildMap {
        for (i in 1..4) {
            put("m$i", "macro$i")
            put("macro_$i", "macro$i")
        }

        // Nomes do pacote personalizado de ícones.
        put("x", "cross")
        put("o", "circle")
        put("touchpad", "select")
        put("arrow_up", "up")
        put("arrow_down", "down")
        put("arrow_left", "left")
        put("arrow_right", "right")
    }
    /** Canonical logical key for an incoming image filename, accepting BOTH the
     *  bundled iOS scheme (`ic_controller_<key>_button.png`) AND the bare names
     *  community / Delta-style packs actually ship (`cross.png`, `L1.png`,
     *  `analog_base.png`). Strips an optional `ic_controller_` prefix + `_button`
     *  suffix, lower-cases, takes the basename, and rejects macOS `._` resource
     *  forks + non-png. Returns null for anything not a known button image. */
    private fun keyForFilename(name: String): String? {
        val n = name.substringAfterLast('/').substringAfterLast('\\').lowercase()
        if (n.startsWith("._") || !n.endsWith(".png")) return null
        // Note the d-pad's own "left"/"right" keys are distinct from the sticks'
        // "analog_stick_left"/"analog_stick_right" — the prefix keeps them apart.
        val core = n.removeSuffix(".png").removePrefix("ic_controller_").removeSuffix("_button")
        val canonical = FILE_ALIASES[core] ?: core
        return if (FILE.containsKey(canonical)) canonical else null
    }

    // Import caps (generous but bounded — phone storage).
    //
    // Must stay ABOVE the number of keys in FILE, with headroom. There are 28 (16 buttons, 8
    // analog, 4 macro) and this used to sit at 24, so a complete pack would have had its last
    // few images dropped on import with no error — the images simply would not appear. Adding a
    // skin slot means checking this number.
    private const val MAX_IMAGES = 60
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    private fun root(ctx: Context): File = File(ctx.filesDir, "controllerskins").apply { mkdirs() }

    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        val saved = MainActivityRuntime.prefs.getString(KEY_ACTIVE, null)
        activeSkinId.value = saved?.takeIf {
            builtinFor(it) != null || File(root(ctx), it).isDirectory
        }
        loaded = true
    }

    /** Eagerly hydrate the saved selection at app start (MainActivityRuntime.onCreate) so the in-game
     *  touch overlay applies the active skin on FIRST render — without it, activeSkinId
     *  stays null until the Skins tab is opened, so the skin only "took" after a
     *  re-select even though it showed as selected. */
    fun load(context: Context) = ensureLoaded(context)

    data class Skin(val id: String, val name: String, val imageCount: Int)

    /** Skins bundled in app assets (under `assets/<assetDir>/`), selectable as
     *  secondary defaults without importing. id is prefixed "builtin:" so it can
     *  never collide with an imported skin's folder name. */
    data class BuiltinSkin(val id: String, val name: String, val assetDir: String)
    val BUILTIN: List<BuiltinSkin> = listOf(
        BuiltinSkin("builtin:nethersx2", "NetherSX2", "controller_skins/nethersx2"),
        BuiltinSkin("builtin:nethersx2_old", "NetherSX2 Old", "controller_skins/nethersx2_old"),
    )
    private fun builtinFor(id: String?): BuiltinSkin? = BUILTIN.firstOrNull { it.id == id }

    /** Imported skins that have at least one recognized image. */
    fun list(ctx: Context): List<Skin> {
        ensureLoaded(ctx)
        val dirs = root(ctx).listFiles { f -> f.isDirectory && !f.name.endsWith(".tmp") } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val originals = File(dir, "originals")
            val n = if (originals.isDirectory) {
                originals.listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }?.size ?: 0
            } else {
                dir.listFiles { f -> f.isFile && keyForFilename(f.name) != null }?.size ?: 0
            }
            if (n == 0) null else Skin(dir.name, prettyName(dir.name), n)
        }.sortedBy { it.name.lowercase() }
    }

    /** Select [id] (null = built-in look) at [serial]'s tier — null serial writes the
     *  global skin, a serial pins that game only.
     *
     *  The per-game tier stores [NONE] rather than removing the key for a null id: a
     *  removed key means "follow the global", which is a different answer from "this
     *  game shows no skin". Global keeps the original remove-on-null so an untouched
     *  install writes nothing. */
    fun setActive(ctx: Context, id: String?, serial: String? = null) {
        ensureLoaded(ctx)
        val eff = serial?.takeIf { it.isNotEmpty() }
        MainActivityRuntime.prefs.edit().apply {
            if (eff != null) putString(KEY_ACTIVE_GAME_PREFIX + eff, id ?: NONE)
            else if (id == null) remove(KEY_ACTIVE)
            else putString(KEY_ACTIVE, id)
        }.apply()
        // Re-resolve rather than assigning id outright: editing the GLOBAL skin while a
        // game with its own override is running must not change what that game shows.
        applyForSerial(ctx, activeSerial.value)
    }

    fun delete(ctx: Context, id: String) {
        File(root(ctx), id).deleteRecursively()
        // A deleted skin may be pinned by any number of games; drop every reference so
        // none is left pointing at a folder that no longer exists.
        MainActivityRuntime.prefs.edit().apply {
            if (MainActivityRuntime.prefs.getString(KEY_ACTIVE, null) == id) remove(KEY_ACTIVE)
            MainActivityRuntime.prefs.all.keys
                .filter { it.startsWith(KEY_ACTIVE_GAME_PREFIX) }
                .filter { MainActivityRuntime.prefs.getString(it, null) == id }
                .forEach { remove(it) }
            MainActivityRuntime.prefs.all.keys
                .filter { key ->
                    val globalPrefix = KEY_BUTTON_IMAGE_PREFIX + id + "."
                    val gamePrefix = KEY_BUTTON_IMAGE_PREFIX + "game."
                    key.startsWith(globalPrefix) ||
                        (
                            key.startsWith(gamePrefix) &&
                            key.removePrefix(gamePrefix)
                                .substringAfter('.', "")
                                .substringBefore('.') == id
                        )
                }
                .forEach { remove(it) }
        }.apply()
        clearCache()
        imageSelectionRevision.value++
        applyForSerial(ctx, activeSerial.value)
    }

    // ---- Runtime image cache (active skin) ----------------------------------
    // Decode-once per (skin,key); null is cached too ("this skin has no image for
    // this button" -> use the built-in). Guarded: read on the UI/draw thread,
    // cleared from the IO import thread.
    private val cache = HashMap<String, ImageBitmap?>()

    private fun clearCache() = synchronized(cache) { cache.clear() }

    /** Longest edge a decoded skin image is kept at. An on-screen button never draws
     *  larger than a few hundred px even on a high-DPI tablet, so decoding a pack's
     *  full-resolution art (packs ship 1024–2048px PNGs) wastes memory AND frame time:
     *  every button's bitmap is uploaded and sampled each frame, so ~15 full-res images
     *  turn the menu and pad into a slideshow the moment a heavy skin is picked. Sampling
     *  down to this on decode is the fix — the on-screen size is unchanged, the per-frame
     *  cost drops by the square of the ratio. */
    private const val MAX_DECODE_PX = 640

    /** inSampleSize (a power of two) that brings the larger of [w]/[h] at or under
     *  [MAX_DECODE_PX]. BitmapFactory only honours powers of two, so this rounds down to
     *  one — a 2048px source decodes at 512 (sample 4), never above the cap. */
    private fun sampleSizeFor(w: Int, h: Int): Int {
        var sample = 1
        var longest = maxOf(w, h)
        while (longest / 2 >= MAX_DECODE_PX) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    /** Decode [key]'s image for the active skin, downsampled to [MAX_DECODE_PX]. Two-pass:
     *  read the bounds (inJustDecodeBounds), then decode with the computed sample size.
     *  [openBounds]/[openFull] return a fresh stream each — a decode consumes its stream. */
    private fun decodeDownsampled(openBounds: () -> java.io.InputStream?, openFull: () -> java.io.InputStream?): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { openBounds()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        return runCatching {
            openFull()?.use { BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap() }
        }.getOrNull()
    }

    /** Returns true only for a valid, reasonably sized PNG image. */
    private fun isValidPng(file: File): Boolean {
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
        )

        val signatureValid = runCatching {
            file.inputStream().use { input ->
                val signature = ByteArray(pngSignature.size)
                var offset = 0
                while (offset < signature.size) {
                    val read = input.read(signature, offset, signature.size - offset)
                    if (read <= 0) return@use false
                    offset += read
                }
                signature.contentEquals(pngSignature)
            }
        }.getOrDefault(false)

        if (!signatureValid) return false

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        val dimensionsValid = runCatching {
            file.inputStream().use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096
        }.getOrDefault(false)

        if (!dimensionsValid) return false

        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
        }

        return runCatching {
            file.inputStream().use {
                BitmapFactory.decodeStream(it, null, options) != null
            }
        }.getOrDefault(false)
    }

    private fun buttonImagePrefKey(skinId: String, key: String, serial: String? = null): String {
        val game = serial?.takeIf { it.isNotEmpty() }
        return if (game != null)
            KEY_BUTTON_IMAGE_PREFIX + "game." + game + "." + skinId + "." + key
        else
            KEY_BUTTON_IMAGE_PREFIX + skinId + "." + key
    }

    /** All PNG files preserved by the importer, including numbered variants. */
    fun imageOptions(ctx: Context, skinId: String): List<String> {
        val dir = File(root(ctx), skinId)
        if (!dir.isDirectory || builtinFor(skinId) != null) return emptyList()
        val originals = File(dir, "originals")
        val source = if (originals.isDirectory) originals else dir
        return source.listFiles { f ->
            f.isFile && f.extension.equals("png", ignoreCase = true)
        }?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()
    }

    /** Decode one imported PNG for the customization gallery preview. */
    fun bitmapForOption(ctx: Context, skinId: String, fileName: String): ImageBitmap? {
        if (fileName != File(fileName).name || !fileName.endsWith(".png", ignoreCase = true)) {
            return null
        }
        val dir = File(root(ctx), skinId)
        if (!dir.isDirectory || builtinFor(skinId) != null) return null
        val originals = File(dir, "originals")
        val source = if (originals.isDirectory) originals else dir
        val file = File(source, fileName).takeIf { it.isFile } ?: return null
        return decodeDownsampled(
            { runCatching { file.inputStream() }.getOrNull() },
            { runCatching { file.inputStream() }.getOrNull() },
        )
    }

    /** Currently selected custom image filename for one button. */
    fun selectedButtonImage(skinId: String, key: String, serial: String? = null): String? {
        if (!FILE.containsKey(key)) return null
        val prefs = MainActivityRuntime.prefs
        val game = serial?.takeIf { it.isNotEmpty() }
        if (game != null) {
            val gameKey = buttonImagePrefKey(skinId, key, game)
            if (prefs.contains(gameKey)) return prefs.getString(gameKey, null)
        }
        return prefs.getString(buttonImagePrefKey(skinId, key), null)
    }

    /** Save a PNG choice for one button; null restores that button's default image. */
    fun setButtonImage(
        ctx: Context,
        skinId: String,
        key: String,
        fileName: String?,
        serial: String? = null,
    ): Boolean {
        if (!FILE.containsKey(key) || !File(root(ctx), skinId).isDirectory) return false
        if (fileName != null && (
            fileName != File(fileName).name ||
            !imageOptions(ctx, skinId).contains(fileName)
        )) return false

        val prefKey = buttonImagePrefKey(skinId, key, serial)
        if (fileName == null) {
            MainActivityRuntime.prefs.edit().remove(prefKey).apply()
        } else {
            MainActivityRuntime.prefs.edit().putString(prefKey, fileName).apply()
        }
        clearCache()
        imageSelectionRevision.value++
        return true
    }

    /** ImageBitmap for [key] from the active skin, or null to use the built-in. */
    fun bitmapForKey(ctx: Context, key: String): ImageBitmap? {
        ensureLoaded(ctx)
        val id = activeSkinId.value ?: return null
        val fname = FILE[key] ?: return null
        val builtin = builtinFor(id)
        val selectedName = if (builtin == null) {
            selectedButtonImage(id, key, activeSerial.value)
        } else {
            null
        }

        val cacheKey = "$id/$key/${selectedName ?: fname}"
        synchronized(cache) {
            if (cache.containsKey(cacheKey)) return cache[cacheKey]
        }

        val bmp = if (builtin != null) {
            decodeDownsampled(
                {
                    runCatching {
                        ctx.assets.open("${builtin.assetDir}/$fname")
                    }.getOrNull()
                },
                {
                    runCatching {
                        ctx.assets.open("${builtin.assetDir}/$fname")
                    }.getOrNull()
                },
            )
        } else {
            val dir = File(root(ctx), id)
            val originals = File(dir, "originals")
            val selected = selectedName?.let { name ->
                File(if (originals.isDirectory) originals else dir, name)
                    .takeIf { it.isFile && it.name == name }
            }
            val file = selected ?: File(dir, fname)

            if (file.isFile) {
                decodeDownsampled(
                    { file.inputStream() },
                    { file.inputStream() },
                )
            } else {
                null
            }
        }

        synchronized(cache) {
            cache[cacheKey] = bmp
        }
        return bmp
    }

    // ---- Import -------------------------------------------------------------

    /** Import a skin from a picked FOLDER (SAF tree) of `ic_controller_*.png`.
     *  Returns the new skin id, or null if nothing usable was found. IO — call off
     *  the main thread. */
    fun importFromTree(ctx: Context, treeUri: Uri): String? {
        val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return null
        val files = tree.listFiles().filter { df ->
            val name = df.name ?: ""
            df.isFile && name.lowercase().endsWith(".png") &&
                !name.substringAfterLast('/').startsWith("._")
        }
        if (files.isEmpty()) return null

        val id = newId(ctx, tree.name ?: "skin")
        val tmp = File(root(ctx), "$id.tmp").apply { deleteRecursively(); mkdirs() }
        val originals = File(tmp, "originals").apply { mkdirs() }
        var count = 0

        for (df in files) {
            if (count >= MAX_IMAGES) break
            if (df.length() > MAX_IMAGE_BYTES) continue

            val originalName = (df.name ?: "icon.png")
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "icon.png" }

            var original = File(originals, originalName)
            var suffix = 1
            while (original.exists()) {
                val base = originalName.removeSuffix(".png")
                original = File(originals, "${base}_$suffix.png")
                suffix++
            }

            val ok = runCatching {
                ctx.contentResolver.openInputStream(df.uri)?.use { input ->
                    original.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) {
                                throw IllegalStateException("PNG exceeds size limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                    true
                } ?: false
            }.getOrDefault(false)

            if (!ok || !isValidPng(original)) {
                original.delete()
                continue
            }

            val key = keyForFilename(df.name ?: "")
            if (key != null) {
                val canonical = File(tmp, FILE.getValue(key))
                if (!canonical.exists()) {
                    runCatching {
                        original.inputStream().use { input ->
                            canonical.outputStream().use { output -> input.copyTo(output) }
                        }
                    }.onFailure { canonical.delete() }
                }
            }
            count++
        }
        return commit(ctx, tmp, id, count)
    }

    /** Import a skin from a picked .zip of `ic_controller_*.png` (flattened to root). */
    fun importFromZip(ctx: Context, zipUri: Uri): String? {
        val raw = DocumentFile.fromSingleUri(ctx, zipUri)?.name
            ?.removeSuffix(".zip")?.removeSuffix(".ZIP") ?: "skin"
        return importZip(ctx, raw) { ctx.contentResolver.openInputStream(zipUri) }
    }

    /** Import a .zip already on local disk under an explicit [displayName] — the
     *  download path ([com.armsx2.SkinRepo]).
     *
     *  Separate from [importFromZip] because that one resolves its name through
     *  DocumentFile.fromSingleUri, which expects a `content://` URI and yields null
     *  for the `file://` of a downloaded temp — every downloaded skin would land as
     *  "skin", "skin_1", "skin_2". The repo already knows the real name, so pass it. */
    fun importFromZipFile(ctx: Context, zip: File, displayName: String): String? =
        importZip(ctx, displayName) { zip.inputStream() }

    private fun importZip(ctx: Context, rawName: String, open: () -> java.io.InputStream?): String? {
        val id = newId(ctx, rawName)
        val tmp = File(root(ctx), "$id.tmp").apply { deleteRecursively(); mkdirs() }
        val originals = File(tmp, "originals").apply { mkdirs() }
        var count = 0

        runCatching {
            open()?.use { stream ->
                ZipInputStream(stream).use { zin ->
                    while (count < MAX_IMAGES) {
                        val entry = zin.nextEntry ?: break
                        val rawEntryName = entry.name
                        val baseName = rawEntryName.substringAfterLast('/').substringAfterLast('\\')
                        val validPng = !entry.isDirectory &&
                            baseName.lowercase().endsWith(".png") &&
                            !baseName.startsWith("._")

                        if (validPng) {
                            val safeName = baseName
                                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                .ifBlank { "icon.png" }

                            var original = File(originals, safeName)
                            var suffix = 1
                            while (original.exists()) {
                                val base = safeName.removeSuffix(".png")
                                original = File(originals, "${base}_$suffix.png")
                                suffix++
                            }

                            var total = 0L
                            val copied = runCatching {
                                original.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val read = zin.read(buffer)
                                        if (read == -1) break
                                        total += read
                                        if (total > MAX_IMAGE_BYTES) {
                                            throw IllegalStateException("PNG exceeds size limit")
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }.isSuccess

                            if (copied && total > 0L && isValidPng(original)) {
                                val key = keyForFilename(baseName)
                                if (key != null) {
                                    val canonical = File(tmp, FILE.getValue(key))
                                    if (!canonical.exists()) {
                                        runCatching {
                                            original.inputStream().use { input ->
                                                canonical.outputStream().use { output -> input.copyTo(output) }
                                            }
                                        }.onFailure { canonical.delete() }
                                    }
                                }
                                count++
                            } else {
                                original.delete()
                            }
                        }
                        zin.closeEntry()
                    }
                }
            }
        }

        return commit(ctx, tmp, id, count)
    }

    private fun commit(ctx: Context, tmp: File, id: String, count: Int): String? {
        if (count == 0) { tmp.deleteRecursively(); return null }
        val target = File(root(ctx), id)
        target.deleteRecursively()
        if (!tmp.renameTo(target)) { tmp.deleteRecursively(); return null }
        clearCache()
        return id
    }

    private fun newId(ctx: Context, name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
            .trim('_').ifEmpty { "skin" }
        var id = base
        var i = 1
        while (File(root(ctx), id).exists()) id = "${base}_${i++}"
        return id
    }

    private fun prettyName(id: String) = id.replace('_', ' ').trim()
}
