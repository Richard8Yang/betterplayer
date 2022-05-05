package com.jhomlala.better_player

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES32.*
import android.os.Handler
import android.os.HandlerThread
//import com.futouapp.threeegl.ThreeEgl
import java.util.concurrent.Semaphore
import io.flutter.view.TextureRegistry.SurfaceTextureEntry
import android.util.Log

class CustomRender : SurfaceTexture.OnFrameAvailableListener{

    var disposed = false;

    lateinit var worker: RenderWorker
    var options: Map<String, Any>;
    var width: Int;
    var height: Int;

    var glWidth: Int = 1;
    var glHeight: Int = 1;

    var dstSurfaceTexture: SurfaceTexture;
    var dstTextureId: Int;
    var screenScale: Double = 1.0;
    //var context: Context;

    lateinit var eglEnv: EglEnv;
    lateinit var dartEglEnv: EglEnv;
    lateinit var shareEglEnv: EglEnv;

    var maxTextureSize = 4096;

    var renderThread: HandlerThread = HandlerThread("VideoCustomRender");
    var renderHandler : Handler

    lateinit var srcSurfaceTex: SurfaceTexture
    var oesTextureMatrix: FloatArray = FloatArray(4 * 4)

    constructor(options: Map<String, Any>, destTexture: SurfaceTextureEntry) {
        this.options = options;
        this.width = options["width"] as Int;
        this.height = options["height"] as Int;
        screenScale = options["dpr"] as Double;

        this.dstSurfaceTexture = destTexture.surfaceTexture();
        this.dstTextureId = destTexture.id().toInt();
        //this.srcTextureId = sourceTexture.id().toInt();
        //this.context = FlutterGlPlugin.context;

        renderThread.start()
        renderHandler = Handler(renderThread.looper)

        this.executeSync {
            setup();
        }
    }

    fun setup() {
        glWidth = (width * screenScale).toInt()
        glHeight = (height * screenScale).toInt()

        this.initEGL();

        this.worker = RenderWorker();
        this.worker.setup();

        srcSurfaceTex = SurfaceTexture(this.worker!!.srcTextureId)
        srcSurfaceTex.setOnFrameAvailableListener(this, renderHandler)
    }

    override fun onFrameAvailable(videoTexture: SurfaceTexture): Unit {
        eglEnv.makeCurrent();
        glActiveTexture(GL_TEXTURE1)
        videoTexture.updateTexImage()
        videoTexture.getTransformMatrix(oesTextureMatrix)

        // Important: set viewport first
        glViewport(0, 0, 1280, 720)
        this.worker.renderTexture(this.worker!!.srcTextureId, oesTextureMatrix);

        glFinish();

        checkGlError("update texture 01");
        eglEnv.swapBuffers();
        //Log.d("Thread %d".format(Thread.currentThread().getId()), "onFrameAvailable")
    }

    fun updateTexture(sourceTexture: Int): Boolean {
        this.execute {
            // Enable alpha channel
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            this.worker.renderTexture(sourceTexture, null);

            //Log.d("Thread %d".format(Thread.currentThread().getId()), "Frame render")

            glFinish();

            checkGlError("update texture 01");
            eglEnv.swapBuffers();
        }
/*
        renderHandler.postDelayed({
            this.updateTexture(sourceTexture)
        }, 20)
*/
        return true;
    }

    fun initEGL() {
        //shareEglEnv = EglEnv();
        //shareEglEnv.setupRender();

        //ThreeEgl.setContext("shareContext", shareEglEnv.eglContext);

        eglEnv = EglEnv();
        //dartEglEnv = EglEnv();

        //eglEnv.setupRender(shareEglEnv.eglContext);
        //dartEglEnv.setupRender(shareEglEnv.eglContext);
        eglEnv.setupRender();
        //dartEglEnv.setupRender();

        // TODO DEBUG
        //dstSurfaceTexture.setDefaultBufferSize(glWidth, glHeight)
        eglEnv.buildWindowSurface(dstSurfaceTexture);

        //dartEglEnv.buildOffScreenSurface(glWidth, glHeight);
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
        _res.addAll(this.dartEglEnv.getEgl());
        return _res;
    }

    fun dispose() {
        disposed = true;
        this.shareEglEnv.dispose();
        this.eglEnv.dispose();
        this.dartEglEnv.dispose();
        this.worker.dispose();
    }


    //检查每一步操作是否有错误的方法
    fun checkGlError(op: String) {
        val error: Int = glGetError();
        if (error != GL_NO_ERROR) {
            println("ES20_ERROR ${op}: glError ${error}")
            throw RuntimeException("$op: glError $error")
        }
    }
}
