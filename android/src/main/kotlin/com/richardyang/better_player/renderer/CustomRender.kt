package com.richardyang.better_player

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES32.*
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.Semaphore
import io.flutter.view.TextureRegistry.SurfaceTextureEntry
import android.util.Log

class CustomRender : SurfaceTexture.OnFrameAvailableListener{

    var disposed = false;

    lateinit var worker: RenderWorker

    var dstSurfaceTexture: SurfaceTexture;
    var dstTextureId: Int;

    lateinit var eglEnv: EglEnv;

    var maxTextureSize = 4096;

    var renderThread: HandlerThread = HandlerThread("VideoCustomRender");
    var renderHandler : Handler

    lateinit var srcSurfaceTex: SurfaceTexture
    var oesTextureMatrix: FloatArray = FloatArray(4 * 4)
    var sharedEglCtx: EGLContext = EGL14.EGL_NO_CONTEXT

    constructor(options: Map<String, Any>, 
        destTexture: SurfaceTextureEntry, 
        shareContext: EGLContext = EGL14.EGL_NO_CONTEXT) {

        this.dstSurfaceTexture = destTexture.surfaceTexture();
        this.dstTextureId = destTexture.id().toInt();

        this.sharedEglCtx = shareContext

        renderThread.start()
        renderHandler = Handler(renderThread.looper)

        this.executeSync {
            setup();
        }
    }

    fun setup() {
        this.initEGL();

        this.worker = RenderWorker();
        this.worker.setup();

        srcSurfaceTex = SurfaceTexture(this.worker!!.srcTextureId)
        srcSurfaceTex.setOnFrameAvailableListener(this, renderHandler)
    }

    fun updateTextureSize(w: Int, h: Int) {
        this.executeSync {
            this.worker.updateTextureSize(w, h)
        }
    }

    override fun onFrameAvailable(videoTexture: SurfaceTexture): Unit {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glActiveTexture(GL_TEXTURE1)
        videoTexture.updateTexImage()
        videoTexture.getTransformMatrix(oesTextureMatrix)

        this.worker.renderTexture(oesTextureMatrix);
        //this.worker.renderTexture(oesTextureMatrix, true);

        glFinish();

        checkGlError("update texture 01");
        eglEnv.swapBuffers();

        // TODO: callback to flutter notifying texture updated
        // flutter side can do texture copy immediately to its local context
        // then use the local texture copy for further rendering
    }

    fun initEGL() {
        eglEnv = EglEnv();
        eglEnv.setupRender(this.sharedEglCtx);
        eglEnv.buildWindowSurface(dstSurfaceTexture);
        eglEnv.makeCurrent();
    }

    fun executeSync(task: () -> Unit) {
        val semaphore = Semaphore(0)
        renderHandler.post {
            task.invoke()
            semaphore.release()
        }
        semaphore.acquire()
    }

    fun execute(task: () -> Unit) {
        renderHandler.post {
            task.invoke()
        }
    }

    fun getEgl() : List<Long> {
        var _res = mutableListOf<Long>();
        _res.addAll(this.eglEnv.getEgl());
        return _res;
    }

    fun dispose() {
        disposed = true;
        this.eglEnv.dispose();
        this.worker.dispose();
    }

    fun checkGlError(op: String) {
        val error: Int = glGetError();
        if (error != GL_NO_ERROR) {
            println("ES20_ERROR ${op}: glError ${error}")
            throw RuntimeException("$op: glError $error")
        }
    }
}
