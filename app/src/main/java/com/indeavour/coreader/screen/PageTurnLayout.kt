package com.indeavour.coreader.screen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hosts the reader and turns its pages like paper.
 *
 * The reader itself can only slide between pages, so the effect is drawn on top of it: a
 * snapshot of the page being left is taken, the reader is moved to the new page underneath
 * without animating, and the snapshot is then peeled away by its corner to reveal it. Only the
 * page being left needs a snapshot, because the page being arrived at is the live reader below.
 *
 * A turn is started either by [turn] (a tap) or by dragging horizontally, in which case the
 * corner of the page follows the finger and can be dropped back.
 */
@SuppressLint("ViewConstructor")
class PageTurnLayout(context: Context) : FrameLayout(context) {

    /** The reader goes in here. */
    val content = FrameLayout(context)

    private val curl = CurlView(context)

    /** When false this is a plain container and the reader's own sliding is left alone. */
    var curlEnabled = true
        set(value) {
            field = value
            if (!value) reset()
        }

    /** Colour of the back of the turning page; normally the reader's background. */
    var paperColor: Int = Color.WHITE

    /** Needed to take the snapshot. */
    var window: Window? = null

    /**
     * Moves the reader one page forward or backward without animating. Returns false when there
     * is no page in that direction.
     */
    var onMovePage: ((forward: Boolean) -> Boolean)? = null

    private enum class Phase { IDLE, CAPTURING, DRAGGING, SETTLING }

    private var phase = Phase.IDLE
    private var forward = true

    /** Bumped whenever a turn ends or is abandoned, so late callbacks from it are ignored. */
    private var generation = 0

    /** Whether the turn should complete by itself once the snapshot is ready. */
    private var commitWhenReady = false

    /** How far the finger has dragged, as a fraction of the page width. */
    private var dragProgress = 0f
    private var animator: ValueAnimator? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var tracking = false

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(curl, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Turns one page with the full animation, as for a tap on the edge of the screen. */
    fun turn(forward: Boolean) {
        if (phase != Phase.IDLE) finishNow()
        // A tap lifts the bottom corner.
        begin(forward, fromTop = false, autoCommit = true)
    }

    // ------------------------------------------------------------------ turn lifecycle

    private fun begin(forward: Boolean, fromTop: Boolean, autoCommit: Boolean) {
        this.forward = forward
        phase = Phase.CAPTURING
        commitWhenReady = autoCommit
        dragProgress = 0f
        val turn = ++generation

        capture { snapshot ->
            if (turn != generation) {
                snapshot?.recycle()
                return@capture
            }
            if (snapshot == null) {
                // No snapshot, no effect: still turn the page.
                onMovePage?.invoke(forward)
                reset()
                return@capture
            }

            curl.show(snapshot, forward, fromTop, paperColor)
            if (onMovePage?.invoke(forward) != true) {
                reset() // First or last page of the book.
                return@capture
            }

            if (commitWhenReady) {
                phase = Phase.SETTLING
                // Give the new page a moment to draw before uncovering it. A turn that was
                // never dragged gets a gentle arc so the corner visibly lifts.
                val arc = dragProgress == 0f
                postDelayed({ if (turn == generation) settle(commit = true, arc = arc) }, RENDER_DELAY_MS)
            } else {
                phase = Phase.DRAGGING
                followFinger()
            }
        }
    }

    /** Places the page corner according to where the finger currently is. */
    private fun followFinger() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val travelled = (if (forward) downX - lastX else lastX - downX).coerceAtLeast(0f)

        // The corner moves twice as far as the finger, so a drag across half the screen is
        // enough to turn the page right over.
        val x = w - 2f * travelled
        // The corner is drawn towards the finger's height as the drag gets going, which is what
        // tilts the fold.
        val pull = smoothStep(travelled / (w * 0.25f))
        val y = curl.cornerY + (lastY - curl.cornerY) * pull * FINGER_PULL
        curl.setCorner(x, y)
    }

    /** Animates the corner to the fully turned position, or back to where it started. */
    private fun settle(commit: Boolean, arc: Boolean) {
        val turn = generation
        val w = width.toFloat()
        val h = height.toFloat()
        val startX = curl.cornerX
        val startY = curl.cornerPullY
        val endX = if (commit) -w else w
        val endY = curl.cornerY
        val remaining = abs(endX - startX) / (2f * w).coerceAtLeast(1f)
        // Lifting from the bottom corner arcs upwards, from the top corner downwards.
        val arcHeight = if (!arc) 0f else if (curl.fromTop) h * TAP_ARC else -h * TAP_ARC

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (TURN_DURATION_MS * remaining).toLong().coerceAtLeast(MIN_DURATION_MS)
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                curl.setCorner(
                    startX + (endX - startX) * t,
                    startY + (endY - startY) * t + arcHeight * sin(PI * t).toFloat()
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (turn != generation) return
                    if (commit) {
                        reset()
                    } else {
                        // Dropped back: return the reader to the page still shown on top.
                        onMovePage?.invoke(!forward)
                        generation++
                        val settled = generation
                        postDelayed({ if (settled == generation) reset() }, RENDER_DELAY_MS)
                    }
                }
            })
            start()
        }
    }

    /** Jumps a turn in progress to its end state. */
    private fun finishNow() {
        if (phase == Phase.SETTLING) animator?.end()
        reset()
    }

    private fun reset() {
        generation++
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
        curl.clear()
        phase = Phase.IDLE
    }

    private fun capture(onResult: (Bitmap?) -> Unit) {
        val window = window
        val width = content.width
        val height = content.height
        if (window == null || width <= 0 || height <= 0) {
            onResult(null)
            return
        }
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val location = IntArray(2)
            content.getLocationInWindow(location)
            val source = Rect(location[0], location[1], location[0] + width, location[1] + height)
            PixelCopy.request(window, source, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    onResult(bitmap)
                } else {
                    Log.w(TAG, "Page snapshot failed: $result")
                    bitmap.recycle()
                    onResult(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.w(TAG, "Page snapshot failed", e)
            onResult(null)
        }
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ dragging

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!curlEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                downTime = event.eventTime
                tracking = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false
            MotionEvent.ACTION_MOVE -> {
                if (!tracking || phase != Phase.IDLE) return false
                val dx = event.x - downX
                val dy = event.y - downY
                // A press held before moving is a text selection, which the reader handles.
                val isSelecting = event.eventTime - downTime >= longPressTimeout
                if (isSelecting) {
                    tracking = false
                } else if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    lastX = event.x
                    lastY = event.y
                    // The page is picked up by whichever corner is nearer the finger.
                    begin(forward = dx < 0, fromTop = downY < height / 2f, autoCommit = false)
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (phase == Phase.IDLE || width <= 0) return false
        val travelled = if (forward) downX - event.x else event.x - downX

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastX = event.x
                lastY = event.y
                dragProgress = (travelled / width).coerceIn(0f, 1f)
                if (phase == Phase.DRAGGING) followFinger()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val isFlick = event.eventTime - downTime < FLICK_MS && travelled > touchSlop * 2
                val commit = event.actionMasked == MotionEvent.ACTION_UP &&
                    (dragProgress > COMMIT_FRACTION || isFlick)
                when (phase) {
                    // Snapshot not ready yet: a flick completes when it is, anything less is dropped.
                    Phase.CAPTURING -> if (commit) commitWhenReady = true else reset()
                    Phase.DRAGGING -> {
                        phase = Phase.SETTLING
                        settle(commit, arc = false)
                    }
                    else -> Unit
                }
            }
        }
        return true
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        const val TAG = "PageTurnLayout"
        const val TURN_DURATION_MS = 480f
        const val MIN_DURATION_MS = 120L
        const val RENDER_DELAY_MS = 60L
        const val FLICK_MS = 300L
        const val COMMIT_FRACTION = 0.2f

        /** How strongly the corner is drawn towards the finger's height (0 = not at all). */
        const val FINGER_PULL = 0.8f

        /** How far a tapped turn lifts the corner, as a fraction of the page height. */
        const val TAP_ARC = 0.28f
    }

    /**
     * Draws the page being left with one of its right-hand corners peeled over to a given
     * point, uncovering whatever is underneath. A backward turn is the same picture mirrored.
     *
     * The geometry is that of folding a sheet of paper: the fold is the line halfway between
     * where the corner started and where it is now, everything on the far side of that line has
     * left the page, and its back shows as a mirror image across the fold.
     */
    private class CurlView(context: Context) : View(context) {

        private var page: Bitmap? = null
        private var forward = true
        private var paper = Color.WHITE

        /** Whether the page is being lifted by its top corner rather than its bottom one. */
        var fromTop = false
            private set

        /** Resting height of the corner being lifted. */
        val cornerY: Float get() = if (fromTop) 0f else height.toFloat()

        /** Where the lifted corner has been pulled to, before limiting it to what paper can do. */
        var cornerX = 0f
            private set
        var cornerPullY = 0f
            private set

        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val bleedPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = 28 }
        private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12000000 }

        private val keptSide = Path()
        private val liftedSide = Path()
        private val mirror = Matrix()
        private val mirrorValues = FloatArray(9)

        fun show(snapshot: Bitmap, forward: Boolean, fromTop: Boolean, paperColor: Int) {
            page?.recycle()
            this.forward = forward
            this.fromTop = fromTop
            paper = paperColor or 0xFF000000.toInt()
            page = if (forward) {
                snapshot
            } else {
                // The canvas is mirrored for a backward turn, so the picture is mirrored first
                // to come out the right way round.
                val flip = Matrix().apply { preScale(-1f, 1f) }
                Bitmap.createBitmap(snapshot, 0, 0, snapshot.width, snapshot.height, flip, false)
                    .also { if (it !== snapshot) snapshot.recycle() }
            }
            setCorner(width.toFloat(), cornerY)
        }

        /**
         * Moves the lifted corner. Coordinates are as for a forward turn (the corner starts at
         * the right edge and travels left); a backward turn is mirrored when drawn.
         */
        fun setCorner(x: Float, y: Float) {
            cornerX = x
            cornerPullY = y
            invalidate()
        }

        fun clear() {
            page?.recycle()
            page = null
            invalidate()
        }

        /** A half-plane: everything on the [nx],[ny] side of the line through ([mx],[my]). */
        private fun halfPlane(path: Path, mx: Float, my: Float, nx: Float, ny: Float, reach: Float) {
            val px = -ny * reach
            val py = nx * reach
            path.rewind()
            path.moveTo(mx + px, my + py)
            path.lineTo(mx - px, my - py)
            path.lineTo(mx - px + nx * reach, my - py + ny * reach)
            path.lineTo(mx + px + nx * reach, my + py + ny * reach)
            path.close()
        }

        override fun onDraw(canvas: Canvas) {
            val bitmap = page ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.save()
            if (!forward) canvas.scale(-1f, 1f, w / 2f, 0f)

            // Paper does not stretch: the corner can never be further from either end of the
            // spine (the left edge) than it was when the page lay flat.
            val startY = cornerY
            var tx = cornerX
            var ty = cornerPullY
            val nearSpineY = startY
            val farSpineY = h - startY
            hypot(tx, ty - nearSpineY).let { distance ->
                if (distance > w) {
                    tx *= w / distance
                    ty = nearSpineY + (ty - nearSpineY) * w / distance
                }
            }
            val diagonal = hypot(w, h)
            hypot(tx, ty - farSpineY).let { distance ->
                if (distance > diagonal) {
                    tx *= diagonal / distance
                    ty = farSpineY + (ty - farSpineY) * diagonal / distance
                }
            }

            val pulled = hypot(tx - w, ty - startY)
            if (pulled < 1f) {
                canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
                canvas.restore()
                return
            }

            // The fold: through the midpoint between the corner's two positions, at right
            // angles to the line joining them. (dx, dy) points from the old position to the new.
            val dx = (tx - w) / pulled
            val dy = (ty - startY) / pulled
            val mx = (tx + w) / 2f
            val my = (ty + startY) / 2f
            val reach = (w + h) * 4f
            halfPlane(keptSide, mx, my, dx, dy, reach)
            halfPlane(liftedSide, mx, my, -dx, -dy, reach)

            // Mirror image across the fold, used to draw the back of the lifted part.
            val ux = -dy
            val uy = dx
            val a = 2f * ux * ux - 1f
            val b = 2f * ux * uy
            val d = 2f * uy * uy - 1f
            mirrorValues[0] = a; mirrorValues[1] = b; mirrorValues[2] = mx - (a * mx + b * my)
            mirrorValues[3] = b; mirrorValues[4] = d; mirrorValues[5] = my - (b * mx + d * my)
            mirrorValues[6] = 0f; mirrorValues[7] = 0f; mirrorValues[8] = 1f
            mirror.setValues(mirrorValues)

            // Shadows are deepest mid-turn, when the sheet stands furthest off the page.
            val lift = sin(PI * (pulled / (2f * w)).coerceIn(0f, 1f)).toFloat()
            // They also thin out as the turn completes, so nothing is left to vanish abruptly
            // when the last sliver of the page leaves the screen.
            val ending = ((2f * w - pulled) / (w * 0.4f)).coerceIn(0f, 1f)
            val shadow = (w * 0.03f + w * 0.09f * lift) * ending

            // 1. The part of the page still lying flat.
            canvas.save()
            canvas.clipPath(keptSide)
            canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            canvas.restore()

            // 2. Shadow the fold casts on the page being uncovered.
            if (shadow >= 1f) {
                canvas.save()
                canvas.clipRect(0f, 0f, w, h)
                canvas.clipPath(liftedSide)
                shadePaint.shader = LinearGradient(
                    mx, my, mx - dx * shadow, my - dy * shadow,
                    0x66000000, 0x00000000, Shader.TileMode.CLAMP
                )
                canvas.drawPaint(shadePaint)
                canvas.restore()
            }

            // 3. The lifted part, seen from behind, lying over the flat part.
            canvas.save()
            canvas.clipRect(0f, 0f, w, h)
            canvas.clipPath(keptSide)
            canvas.concat(mirror)
            // A soft dark halo just outside its edges, as its shadow on the page beneath.
            canvas.drawRect(-shadow, -shadow, w + shadow, h + shadow, haloPaint)
            canvas.drawRect(-shadow / 2f, -shadow / 2f, w + shadow / 2f, h + shadow / 2f, haloPaint)
            // Paper, with the print faintly showing through.
            paperPaint.color = paper
            canvas.drawRect(0f, 0f, w, h, paperPaint)
            canvas.drawBitmap(bitmap, 0f, 0f, bleedPaint)
            // Rounded look: dark in the fold, lighter across the sheet, darker again at the corner.
            val backDepth = pulled / 2f
            shadePaint.shader = LinearGradient(
                mx, my, mx - dx * backDepth, my - dy * backDepth,
                intArrayOf(0x55000000, 0x12000000, 0x00000000, 0x30000000),
                floatArrayOf(0f, 0.3f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, shadePaint)
            canvas.restore()

            canvas.restore()
        }
    }
}
