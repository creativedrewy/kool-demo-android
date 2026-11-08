package de.fabmax.kool.demo

import android.app.Activity
import android.os.Bundle
import de.fabmax.kool.createDefaultKoolContext
import de.fabmax.kool.platform.KoolContextAndroid

/**
 * Android launcher for the full kool demo app (the same demo suite that runs on
 * desktop / in the browser at https://kool-engine.github.io/demos.html).
 *
 * The demo scenes themselves live in this package (ported 1:1 from the
 * kool-demo commonMain sources of kool 0.19.0). [demo] creates the DemoLoader,
 * which adds the demo menu, loading screen and the selected demo scene to the
 * context. Demo assets (models, textures, HDRIs) are streamed from the kool
 * demo asset server, so the app needs internet access on first use of a demo.
 */
class MainActivity : Activity() {

    lateinit var koolCtx: KoolContextAndroid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        koolCtx = createKoolContext()
        setContentView(koolCtx.surfaceView)
    }

    override fun onResume() {
        super.onResume()
        koolCtx.onResume()
    }

    override fun onPause() {
        super.onPause()
        koolCtx.onPause()
    }

    override fun onDestroy() {
        koolCtx.onDestroy()
        super.onDestroy()
    }

    private fun createKoolContext(): KoolContextAndroid {
        val ctx = createDefaultKoolContext()

        // launch the full demo suite (menu + all demo scenes)
        demo(null, ctx)

        ctx.run()
        return ctx
    }
}
