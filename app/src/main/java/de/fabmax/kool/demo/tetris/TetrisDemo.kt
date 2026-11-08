package de.fabmax.kool.demo.tetris

import de.fabmax.kool.KoolContext
import de.fabmax.kool.demo.*
import de.fabmax.kool.demo.menu.DemoMenu
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.Pointer
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.scene.ColorMesh
import de.fabmax.kool.scene.Light
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.*
import kotlin.math.roundToInt

/*
 * Tetris Demo
 * @author dan_bat :>
 */
class TetrisDemo : DemoScene("Tetris") {

    private val game = TetrisGame()
    private val renderer = TetrisRenderer(game)
    private val keyListener = InputStack.KeyboardListener { keyEvents, _ -> keyEvents.forEach { handleInput(it) } }
    private val pointerListener = InputStack.PointerListener { pointerState, _ -> handlePointer(pointerState.primaryPointer) }

    // touch gesture state
    private val touchAnchor = MutableVec2f()
    private var touchStartTime = 0.0
    private var touchActive = false
    private var touchMoved = false
    private lateinit var gameMesh: ColorMesh
    private val overlay = Scene("tetris-overlay")

    init {
        scenes += overlay
    }

    override fun Scene.setupMainScene(ctx: KoolContext) {
        mainScene.clearColor = ClearColorFill(MdColor.GREY tone 900)

        camera.position.set(TetrisGame.WIDTH * game.blockSize / 2f, TetrisGame.HEIGHT * game.blockSize / 2f, 22f)
        camera.lookAt.set(TetrisGame.WIDTH * game.blockSize / 2f, TetrisGame.HEIGHT * game.blockSize / 2f, 0f)

        InputStack.defaultInputHandler.keyboardListeners += keyListener
        InputStack.defaultInputHandler.pointerListeners += pointerListener

        lighting.clear()
        lighting.addLight(Light.Directional().apply {
            setup(Vec3f(-0.4f, -0.8f, -1f)).setColor(Color.WHITE, 1.2f)
        })
        lighting.addLight(Light.Directional().apply {
            setup(Vec3f(0.8f, 0.4f, 0.5f)).setColor(MdColor.AMBER.toLinear(), 0.5f)
        })

        // Create and configure the mesh for rendering the game field
        gameMesh = ColorMesh(name = "tetris-mesh")
        gameMesh.shader = KslPbrShader {
            color { vertexColor() }
            lighting { maxNumberOfLights = 2 }
            roughness(0.2f)
            metallic(0f)
        }

        // Update game state and rebuild mesh each frame
        gameMesh.onUpdate {
            game.update(Time.deltaT)
            gameMesh.geometry.clear()
            gameMesh.generate {
                with(renderer) { render() }
            }
        }
        addNode(gameMesh)
    }

    override fun lateInit(ctx: KoolContext) {
        super.lateInit(ctx)
        overlay.setupUiScene()
        overlay.addPanelSurface {
            modifier
                .size(Grow.Std, Grow.Std)
                .background(null)
                .layout(CellLayout)
            if (game.isGameOver.use()) {
                // Game Over overlay
                modifier.backgroundColor(Color.BLACK.withAlpha(0.5f))
                Column(FitContent, FitContent) {
                    modifier.align(AlignmentX.Center, AlignmentY.Center)
                    Text("Game Over".l) {
                        modifier
                            .textColor(Color.WHITE)
                            .font(MsdfFont(sizePts = 36f))
                            .alignX(AlignmentX.Center)
                    }
                    Button("Restart".l) {
                        modifier
                            .textColor(Color.WHITE)
                            .font(MsdfFont(sizePts = 18f))
                            .alignX(AlignmentX.Center)
                            .margin(top = sizes.largeGap)
                            .onClick {
                                game.isGameOver.value = false
                            }
                    }
                }
            } else if (game.isPaused.use()) {
                // Pause overlay
                modifier.backgroundColor(Color.BLACK.withAlpha(0.5f))
                Text("Paused".l) {
                    modifier
                        .textColor(Color.WHITE)
                        .font(MsdfFont(sizePts = 36f))
                        .align(AlignmentX.Center, AlignmentY.Center)
                }
            }
        }
    }

    private fun handleInput(it: KeyEvent) {
        if (it.isConsumed) return

        var consumed = false
        if (!game.isGameOver.value && it.isPressed) {
            consumed = true
            when (it.keyCode) {
                KeyboardInput.KEY_CURSOR_LEFT -> game.move(-1)
                KeyboardInput.KEY_CURSOR_RIGHT -> game.move(1)
                KeyboardInput.KEY_CURSOR_DOWN -> game.softDrop()
                KeyboardInput.KEY_CURSOR_UP -> game.rotate()
                UniversalKeyCode(' ') -> game.hardDrop()
                UniversalKeyCode('p') -> game.togglePause()
                else -> {
                    consumed = false
                }
            }
        }
        if (game.isGameOver.value && it.isPressed) {
            game.reset()
            consumed = true
        }

        if (consumed) {
            it.isConsumed = true
        }
    }

    /**
     * Touch controls:
     * - swipe left / right: move the piece one cell per swipe step (longer swipe = more cells)
     * - swipe down: soft drop, one cell per swipe step
     * - tap (short touch without swiping): rotate the piece
     * - tap while the game-over overlay is shown: restart
     */
    private fun handlePointer(ptr: Pointer) {
        if (!ptr.isValid || ptr.isConsumed()) {
            return
        }

        if (game.isGameOver.value) {
            if (ptr.isLeftButtonPressed) {
                game.reset()
                ptr.consume()
            }
            return
        }
        if (game.isPaused.value) {
            return
        }

        // distance the finger has to travel for one move / drop step
        val stepPx = 36f * ptr.windowScale
        when {
            ptr.isLeftButtonPressed -> {
                touchAnchor.set(ptr.pos)
                touchStartTime = Time.gameTime
                touchActive = true
                touchMoved = false
            }
            ptr.isLeftButtonDown && touchActive -> {
                while (ptr.pos.x - touchAnchor.x > stepPx) {
                    game.move(1)
                    touchAnchor.x += stepPx
                    touchMoved = true
                }
                while (touchAnchor.x - ptr.pos.x > stepPx) {
                    game.move(-1)
                    touchAnchor.x -= stepPx
                    touchMoved = true
                }
                while (ptr.pos.y - touchAnchor.y > stepPx) {
                    game.softDrop()
                    touchAnchor.y += stepPx
                    touchMoved = true
                }
                if (touchMoved) {
                    ptr.consume()
                }
            }
            ptr.isLeftButtonReleased -> {
                if (touchActive && !touchMoved && Time.gameTime - touchStartTime < 0.35) {
                    game.rotate()
                    ptr.consume()
                }
                touchActive = false
            }
        }
    }

    override fun onRelease(ctx: KoolContext) {
        super.onRelease(ctx)
        InputStack.defaultInputHandler.keyboardListeners -= keyListener
        InputStack.defaultInputHandler.pointerListeners -= pointerListener
    }

    // Helper function for rendering control keys
    private fun UiScope.KeyButton(width: Dimension = 24.dp, block: UiScope.() -> Unit) {
        Box(width, 24.dp) {
            modifier
                .margin(horizontal = sizes.smallGap)
                .background(RoundRectBackground(colors.backgroundVariant, sizes.smallGap))
                .border(RoundRectBorder(colors.primaryVariant, sizes.smallGap, 1.dp))
            block()
        }
    }
    private fun UiScope.KeyButton(key: String, width: Dimension = 24.dp) {
        KeyButton(width) {
            Text(key) {
                modifier
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .textColor(colors.onBackground)
            }
        }
    }

    override fun createMenu(menu: DemoMenu, ctx: KoolContext) = menuSurface {
        MenuRow {
            Text("Score".l) { labelStyle(Grow.Std) }
            Text("${game.score.use()}") { labelStyle() }
        }
        MenuRow {
            Text("Lines".l) { labelStyle(Grow.Std) }
            Text("${game.lines.use()}") { labelStyle() }
        }
        MenuRow {
            Text("Level".l) { labelStyle(Grow.Std) }
            Text("${game.level.use()}") { labelStyle() }
        }
        MenuRow {
            Text("Block Style".l) { labelStyle() }
            ComboBox {
                val options = BlockStyle.entries
                modifier
                    .width(Grow.Std)
                    .margin(start = sizes.largeGap)
                    .items(options.map { it.displayName.l })
                    .selectedIndex(options.indexOf(renderer.blockStyle.use()))
                    .onItemSelected {
                        renderer.blockStyle.set(options[it])
                    }
            }
        }
        MenuSlider2("Preview Pieces".l, game.numPreviews.use().toFloat(), 0f, 5f, txtFormat = { "${it.toInt()}" }) {
            game.numPreviews.set(it.roundToInt())
        }

        Text("Controls".l) { sectionTitleStyle() }
        MenuRow {
            modifier.alignY(AlignmentY.Center)
            Text("Move".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            KeyButton("←")
            KeyButton("→")
        }
        MenuRow {
            Text("Rotate".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            KeyButton("↑")
        }
        MenuRow {
            Text("Soft Drop".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            KeyButton("↓")
        }
        MenuRow {
            Text("Hard Drop".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            KeyButton("Space".l, width = 100.dp)
        }
        MenuRow {
            Text("Pause".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            KeyButton("P")
        }
        MenuRow {
            Text("Move (touch)".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            Text("Swipe ◀ ▶".l) { labelStyle() }
        }
        MenuRow {
            Text("Rotate (touch)".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            Text("Tap".l) { labelStyle() }
        }
        MenuRow {
            Text("Drop (touch)".l) { modifier.width(120.dp).textAlignX(AlignmentX.End).margin(end = sizes.gap) }
            Text("Swipe ▼".l) { labelStyle() }
        }
    }
}
